# Client ↔ Server Bridge

How a client mod talks to its server-side counterpart. Four
mechanisms, layered from "simplest, use this first" to "when you need
typed low-latency push":

| Mechanism | When to reach for it |
|---|---|
| **HTTP endpoints** (server hosts, client fetches) | Tiles, data dumps, static files, anything browseable |
| **`ServerInfoRegistry`** | "Where do I HTTP to?" — discover the server's base URI |
| **`ServerCapabilities`** | "Does the server have my mod? What version?" — feature gating |
| **ModComm** (typed packets) | Bidirectional server push, small/frequent messages |

If you're coming from Ago-era client mods, the big shifts are:

- **HTTP is first-class.** The server framework bundles an HTTP server
  for mod endpoints. LiveMap's tile server is an HTTP endpoint on the
  Wurm server process, not a separate daemon.
- **No hardcoded hosts.** `ServerInfoRegistry` advertises the base URI
  over ModComm during login; you never hardcode `localhost:8080`.
- **Capability checks are standard.** A short handshake tells every
  client which server mods are loaded and their versions. Gate
  features on it.
- **ModComm still exists**, same wire shape as Ago, with a cleaner
  API on both sides.

---

## 1. HTTP endpoints — the default path

**Use this when:** your client mod wants to fetch anything from the
server — tiles, JSON, files, live status. Works with any HTTP client
(`HttpURLConnection`, OkHttp, whatever).

### Server side — register a handler

The server framework owns an HTTP server (`ModHttpServer`, installed
during boot). Server mods register routes against it.

**Preferred (event-driven):** fire a `ModActionEvent` — works whether
or not the HTTP subsystem is present, so mods degrade gracefully:

```java
@SubscribeEvent
public void onServerStarted(ServerStartedEvent event) {
    ModActionEvent reg = new ModActionEvent("httpserver:register_endpoint");
    reg.set("modName", "mymod");
    reg.set("pattern", Pattern.compile("^/api/(?<path>.*)$"));
    reg.set("handler",
        (Function<String, InputStream>) this::handleApiRequest);
    EventBus.getInstance().post(reg);

    if (!reg.isHandled()) {
        logger.warning("httpserver not loaded; /mymod/api disabled");
    }
}

private InputStream handleApiRequest(String path) {
    // path = whatever matched the (?<path>.*) group
    return new ByteArrayInputStream(generateJson(path).getBytes(UTF_8));
}
```

This is the pattern LiveMap's server mod uses
(`LiveMapMod.registerEndpoints()` — it registers three endpoints this
way). The named `(?<path>.*)` group in the regex is what gets passed
to the handler.

**Direct API equivalent** (if you want to import the framework
directly):

```java
ModHttpServer.getInstance().serve(
    this,                                         // your WurmServerMod
    Pattern.compile("tile/(?<path>.*)"),
    (path) -> openTileStream(path));
```

Either way, the endpoint is exposed at
`http://<host>:<port>/mymod/...` — `modName` becomes the URL prefix.

### Client side — find the URI, then fetch

Discovery uses `ServerInfoRegistry` (next section). Once you have the
URI, use any HTTP client:

```java
public class MyClientMod {
    private String baseUri;

    @SubscribeEvent
    public void onServerInfo(ServerInfoAvailableEvent event) {
        baseUri = event.getHttpUri() + "/mymod";
        logger.info("Server mymod endpoint at: " + baseUri);
    }

    private byte[] fetchTile(int z, int x, int y) throws IOException {
        URL url = new URL(baseUri + "/tile/" + z + "/" + x + "/" + y + ".png");
        try (InputStream in = url.openStream()) {
            return in.readAllBytes();
        }
    }
}
```

**Gotchas:**

- Always fetch off the render thread. `url.openStream()` blocks;
  doing it in `ClientTickEvent` will stutter.
- Subsystem binds on `ServerStartedEvent` server-side. Registrations
  from `preInit()` or `init()` may race — register from a
  `@SubscribeEvent(ServerStartedEvent)` handler to be safe.
- The HTTP server's port is controlled by
  `wurmmodloader-http.properties` on the server. Don't hardcode it
  client-side — use `ServerInfoRegistry`.

---

## 2. `ServerInfoRegistry` — URI discovery

The framework pushes the server's HTTP base URI to every client during
login over a built-in channel (`wml.serverinfo`). Client mods read it
instead of hardcoding.

### Two ways to consume it

**Event (recommended for setup):**

```java
@SubscribeEvent
public void onServerInfo(ServerInfoAvailableEvent event) {
    String uri = event.getHttpUri();       // e.g. "http://192.168.1.5:9090"
    String ver = event.getModloaderVersion();
    wireClient(uri);
}
```

**Synchronous (anytime after login):**

```java
if (ServerInfoRegistry.isAvailable()) {
    String uri = ServerInfoRegistry.getHttpUri();
    // use uri
}
```

Use the event for setup (register HTTP clients once), the registry for
late reads (a button was clicked after login, go fetch fresh data).

**Gotchas:**

- Fires **only on WML-enabled servers.** Pure vanilla servers never
  send it — `getHttpUri()` returns null. Gate your HTTP code on
  availability rather than assuming.
- Fires during login handshake, before `ClientWorldLoadedEvent`. If
  you need the URI at world-load time, it's already there.
- Server-side: no action needed. The framework's
  `ServerInfoChannel` auto-advertises; you don't configure or
  register anything.

---

## 3. `ServerCapabilities` — feature gating

After `ServerInfoAvailableEvent`, the client receives the list of
server-side mods + versions. Use it to enable / disable features.

### Server side — declare a capability

```java
@SubscribeEvent
public void onServerStarted(ServerStartedEvent event) {
    WMLCapabilitiesChannel.registerServerMod(
        "sprint_system",
        "1.0.0",
        "Adds sprint stamina and UI");
}
```

(The channel auto-initializes; you just register.)

### Client side — check before you act

```java
@SubscribeEvent
public void onCapabilities(ServerCapabilitiesReceivedEvent event) {
    if (event.hasServerMod("sprint_system")) {
        sprintHud.enable();
        String ver = event.getModVersion("sprint_system");
        if (!isCompatible(ver)) {
            sprintHud.showWarning("Server sprint_system " + ver +
                                  " may not match client");
        }
    } else {
        sprintHud.disable();
    }
}
```

`ServerCapabilities.hasServerMod(...)` / `getModVersion(...)` give you
the same queries synchronously from anywhere after the event has
fired.

**Why bother?** Client mods often ship independently of their server
counterpart. Capability checks let your client mod cope with:

- Server doesn't have the mod → hide your UI
- Server has an older version → show a compat warning or degrade
- Server has a newer version → maybe your client is the one behind

---

## 4. ModComm — typed packets

**When to reach for it:** server wants to *push* state to the client
without the client polling — combat indicators, frequent numeric
updates, anything too chatty or too latency-sensitive for HTTP
polling.

Same wire protocol as Ago's `ModComm`, new API on both sides.

### Register a channel (both sides, before login)

**Client:**

```java
public class MyClientMod {
    @SubscribeEvent
    public void onClientInit(ClientInitEvent event) {
        ModComm.registerChannel("mymod.state", new IChannelListener() {
            @Override
            public void handleMessage(ByteBuffer message) {
                int hp = message.getInt();
                float stamina = message.getFloat();
                hud.update(hp, stamina);
            }

            @Override
            public void onServerConnected() {
                logger.info("mymod.state active");
            }
        });
    }
}
```

**Server:**

```java
public class MyServerMod implements WurmServerMod {
    private Channel channel;

    @SubscribeEvent
    public void onServerStarted(ServerStartedEvent event) {
        channel = ModComm.registerChannel("mymod.state",
            new IChannelListener() {
                @Override
                public void handleMessage(Player player, ByteBuffer message) {
                    // client → server (if applicable)
                }
                @Override
                public void onPlayerConnected(Player player) {
                    sendInitialState(player);
                }
            });
    }

    private void sendInitialState(Player p) {
        ByteBuffer buf = ByteBuffer.allocate(8);
        buf.putInt(p.getCreature().getHealth());
        buf.putFloat(p.getStamina());
        channel.sendMessage(p, (ByteBuffer) buf.flip());
    }
}
```

### Wire format helpers

For anything past a handful of fields, use the `PacketReader` /
`PacketWriter` wrappers around the `ByteBuffer` — they give you
`DataInputStream`-style reads (`readUTF`, `readShort`, etc.) without
the manual byte math.

### Gotchas

- **Register in `ClientInitEvent`** (client) and `ServerStartedEvent`
  (server). Channels registered *after* a player's connection
  handshake won't be visible to that player's session.
- **Channel names must match exactly** on both sides. `mymod.state`
  on server and `mymod.State` on client = silent drop.
- **Check `channel.isActiveForPlayer(player)` before
  `sendMessage`** (server-side). If the client doesn't have the
  matching channel, the send is a silent no-op — better to branch
  and log.
- **Single-threaded on client.** No synchronization needed for state
  touched only by `handleMessage`. Server-side is multi-threaded; use
  the same locking you'd use for any Wurm player state.

---

## Picking between HTTP and ModComm

| Question | If yes → |
|---|---|
| Does the data change rarely, or does the client only fetch on demand? | **HTTP** |
| Is the payload a file, image, or JSON blob? | **HTTP** |
| Do you want browsers / external tools to also consume it? | **HTTP** |
| Does the server need to *push* updates without the client polling? | **ModComm** |
| Is latency below ~100ms critical (combat, real-time indicators)? | **ModComm** |
| Is the payload small (< ~1KB) and frequent (> once per second)? | **ModComm** |

Many mods use both: HTTP for bulk initial data, ModComm for live
deltas.

---

## Full real-world walk-through

LiveMap exercises three of the four mechanisms. Read it when you're
past the snippets:

| Mechanism | Where in livemap |
|---|---|
| HTTP endpoints (server) | [`WurmModLoader-CommunityMods/mods/livemap/.../LiveMapMod.java`](../../../WurmModLoader-CommunityMods/mods/livemap/src/main/java/org/gotti/wurmunlimited/mods/livemap/LiveMapMod.java) — `registerEndpoints()` fires `ModActionEvent("httpserver:register_endpoint")` three times |
| HTTP fetch (client) | [`mods/livemap/.../MapHttpClient.java`](../../mods/livemap/src/main/java/com/garward/mods/livemap/) — async tile + data fetch |
| `ServerInfoRegistry` (client) | [`mods/livemap/.../LiveMapClientMod.java`](../../mods/livemap/src/main/java/com/garward/mods/livemap/LiveMapClientMod.java) — checks `ServerInfoRegistry.isAvailable()` early, subscribes to `ServerInfoAvailableEvent` as fallback |

serverpacks is the minimal ModComm reference if you want to see the
typed-packet pattern in isolation:
[`mods/serverpacks/`](../../mods/serverpacks/).

---

## Lifecycle order (for reference)

```
Client connects
    ↓
ModComm handshake (exchanges channel IDs)
    ↓
ServerInfoChannel pushes base URI
    → ServerInfoAvailableEvent fires on client
    ↓
WMLCapabilitiesChannel pushes server-mod list
    → ServerCapabilitiesReceivedEvent fires on client
    ↓
(your ModComm channel listeners start receiving; HTTP is reachable)
    ↓
World loads
    → ClientWorldLoadedEvent fires on client
```

If your mod needs the URI + capabilities before the world comes up,
both are already available by `ClientWorldLoadedEvent`.

---

## See also

- [`./lifecycle-events.md`](./lifecycle-events.md) — event timeline (what fires when)
- [`./troubleshooting.md`](./troubleshooting.md) — ModComm / HTTP debugging patterns
- [`../getting-started/index.md`](../getting-started/index.md) — client hub
- Server side:
  [`../../../WurmModLoader/docs/guides/event-bus.md`](../../../WurmModLoader/docs/guides/event-bus.md),
  `ModHttpServer` API in `wurmmodloader-api`, `WMLCapabilitiesChannel`
  in `wurmmodloader-core`
