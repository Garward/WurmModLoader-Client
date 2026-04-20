# Client Lifecycle Events

Every client mod subscribes to at least one lifecycle event — usually
`ClientInitEvent` or `ClientWorldLoadedEvent`. This page is the
reference for *when* each event fires, *what* it gives you, and *which
one to pick* for a given task. If you know the server side, this is the
client analogue of `ServerStartedEvent` & friends — same event-bus
semantics, different timeline.

> Event bus basics (`@SubscribeEvent`, priorities, cancellation) are
> identical to the server side. See the server
> [`event-bus.md`](../../../WurmModLoader/docs/guides/event-bus.md) for
> the handler-shape rules; everything there applies here, just with
> `com.garward.wurmmodloader.client.api.events.*` imports.

---

## The client timeline

```
┌─────────────────────────────────────────────────────────────┐
│  Launcher → patched client.jar boots                        │
│                                                             │
│  ClientInitEvent          ← framework + Wurm client ready   │
│      ↓                    (no world, no HUD)                │
│                                                             │
│  ClientHUDInitializedEvent ← HUD set up, register windows   │
│      ↓                                                      │
│                                                             │
│  ClientWorldLoadedEvent    ← world + entities present       │
│      ↓                                                      │
│                                                             │
│  ServerInfoAvailableEvent  ← ModComm reports server HTTP    │
│      ↓                     (only if server runs WML)        │
│                                                             │
│  ServerCapabilitiesReceivedEvent                            │
│      ↓                     ← list of server-side mods       │
│                                                             │
│  ClientTickEvent × N       ← once per frame, for the rest   │
│                              of the session                 │
└─────────────────────────────────────────────────────────────┘
```

Order is guaranteed top-to-bottom for the events above — if you need
the HUD to exist, subscribe to `ClientHUDInitializedEvent`, not
`ClientInitEvent`.

---

## The lifecycle events

All of these live in
`com.garward.wurmmodloader.client.api.events.lifecycle.*` (with one
exception — `ClientHUDInitializedEvent` lives under `.map.*` for
historical reasons; that's noted below).

### `ClientInitEvent`

Fires when the Wurm client finishes initialization, *before* the world
loads. Not cancellable, no payload.

**Use it for:** framework wiring, loading config, registering with
other WML systems, logging mod startup.

**Don't use it for:** anything that touches the world, player, HUD, or
render state — none of that exists yet.

```java
@SubscribeEvent
public void onClientInit(ClientInitEvent event) {
    config = loadConfig();
    logger.info("MyMod v" + VERSION + " ready");
}
```

---

### `ClientHUDInitializedEvent`

Fires when the HUD (`HeadsUpDisplay`) is fully constructed. This is the
ideal time to register custom windows, MainMenu entries, and HUD
components. Lives under `api.events.map.*` (historical — it's a
lifecycle event in every other respect).

Payload is exposed as `Object` to keep the public API free of
`com.wurmonline.*` types — cast at the call site if you actually need
them:

| Getter | Underlying type |
|---|---|
| `getHud()` | `com.wurmonline.client.renderer.gui.HeadsUpDisplay` |
| `getWorld()` | `com.wurmonline.client.game.World` |
| `getMainMenu()` | `com.wurmonline.client.renderer.gui.MainMenu` |
| `getScreenWidth()` / `getScreenHeight()` | `int` (pixels) |

**Use it for:** registering `ModHud` components, adding MainMenu
buttons, building sidebars / icon rows / map windows (see
[`ui-layout.md`](./ui-layout.md)).

**Real-world example:** `mods/livemap/.../LiveMapClientMod.java` — this
is where the live map registers its sidebar + minimap with the HUD.

---

### `ClientWorldLoadedEvent`

Fires once terrain and entities are loaded and the player is in-world.
Not cancellable, no payload.

**Use it for:** anything that needs `World` / player state to be real
— spawning visual markers tied to tiles, querying the player's
position, wiring up world-dependent systems.

**Don't use it for:** HUD setup (too late, use
`ClientHUDInitializedEvent`) or framework wiring (too late, use
`ClientInitEvent`).

```java
@SubscribeEvent
public void onWorldLoaded(ClientWorldLoadedEvent event) {
    logger.info("World loaded — starting tile polling");
    startTilePoller();
}
```

---

### `ServerInfoAvailableEvent`

Fires when the server (if it's running WurmModLoader) announces its
HTTP endpoint over the `wml.serverinfo` ModComm channel. Payload:

- `getHttpUri()` — base HTTP URI of the server's mod-exposed endpoints
- `getModloaderVersion()` — server's WML version

**Use it for:** discovering a server-side counterpart's HTTP endpoint
without hardcoding `localhost:8080`. Livemap uses this to find the map
tile server.

**Synchronous alternative:** if you need the URI outside of an event
handler, read it directly from
`ServerInfoRegistry.getHttpUri()` (returns null if not yet received).

```java
@SubscribeEvent
public void onServerInfo(ServerInfoAvailableEvent event) {
    tileClient.setBaseUrl(event.getHttpUri() + "/livemap/");
}
```

**Note:** fires only if the server is actually running WML and
advertising. Pure-vanilla servers never fire this.

---

### `ServerCapabilitiesReceivedEvent`

Fires after `ServerInfoAvailableEvent` with the list of server-side
mods the server is running. Use it to enable/disable client features
based on what the server actually has.

```java
@SubscribeEvent
public void onCapabilities(ServerCapabilitiesReceivedEvent event) {
    if (event.hasServerMod("sprint_system")) {
        enableSprintHUD();
    }
}
```

Built-in helpers on the event: `hasServerMod(modId)`,
`getModVersion(modId)`, `getServerMods()`. A static facade
(`com.garward.wurmmodloader.client.api.capabilities.ServerCapabilities`)
exposes the same queries synchronously once the event has fired.

---

### `ClientTickEvent`

Fires every frame. Payload: `getDeltaTime()` in seconds.

**Warning:** this fires 60+ times per second. Keep handlers tight — no
allocations in the hot path, no synchronous HTTP, no logging at `INFO`.

**Use it for:** animation interpolation, client-side prediction, HUD
refresh at sub-second cadence, polling any state you can't event-drive.

**Common pattern:** accumulate delta and only act every N seconds:

```java
private float accumulator = 0f;

@SubscribeEvent
public void onTick(ClientTickEvent event) {
    accumulator += event.getDeltaTime();
    if (accumulator >= 1.0f) {
        accumulator = 0f;
        refreshMinimap();
    }
}
```

---

## Picking the right event

| Your task | Event |
|---|---|
| Load config / register with other WML systems | `ClientInitEvent` |
| Add a HUD window, MainMenu entry, or sidebar | `ClientHUDInitializedEvent` |
| Query world, player position, or tile data | `ClientWorldLoadedEvent` |
| Find the server's HTTP endpoint | `ServerInfoAvailableEvent` (or `ServerInfoRegistry` directly) |
| Gate features on server mods | `ServerCapabilitiesReceivedEvent` |
| Animate, poll, or interpolate | `ClientTickEvent` (use an accumulator) |

---

## Beyond lifecycle — the rest of the event catalog

Lifecycle is where you'll start, but the client API has focused events
for specific subsystems. All under
`com.garward.wurmmodloader.client.api.events.*`:

| Subpackage | What's in it |
|---|---|
| `client.*` | `FOVChangedEvent` |
| `client.combat.*` | `ClientCombatAnimationStartEvent`, `ClientCombatAnimationEndEvent` |
| `client.movement.*` | `ClientMovementIntentEvent`, `ClientPrePlayerUpdateEvent`, `ClientPostPlayerUpdateEvent`, `AuthoritativePlayerPositionEvent` |
| `client.npc.*` | `ClientNpcUpdateEvent` |
| `gui.*` | `ComponentRenderEvent`, `MouseClickEvent`, `MouseDragEvent`, `MouseScrollEvent` |
| `map.*` | `MapDataReceivedEvent`, `MapTileReceivedEvent`, `WorldMapToggleRequestedEvent` (plus `ClientHUDInitializedEvent`, covered above) |
| `serverpacks.*` | `ServerPackReceivedEvent` |
| `sync.*` | `ServerCorrectionReceivedEvent` |

These are all plain `Event` subclasses — subscribe with
`@SubscribeEvent` just like the lifecycle ones. See each source file's
Javadoc for payload details; the ones with interesting payloads
(movement intent, mouse events) document their getters inline.

---

## Common pitfalls

- **Registering windows in `ClientInitEvent`.** The HUD doesn't exist
  yet — your window has nothing to attach to. Use
  `ClientHUDInitializedEvent`.
- **Reading `World` in `ClientInitEvent`.** Same problem. Use
  `ClientWorldLoadedEvent`.
- **Heavy work in `ClientTickEvent`.** Allocating a new object per
  frame, logging per frame, or making a blocking call per frame will
  tank client FPS.
- **Assuming `ServerInfoAvailableEvent` always fires.** It only fires
  on WML-enabled servers. Gate your HTTP client on it having fired
  (check `ServerInfoRegistry.getHttpUri() != null`) rather than
  assuming it.
- **Missing `@SubscribeEvent`.** Silent no-op — the event bus only
  calls annotated methods. If a handler isn't running, check the
  annotation first.

---

## See also

- [`../getting-started/index.md`](../getting-started/index.md) — client onramp + hellomod walkthrough
- [`./ui-layout.md`](./ui-layout.md) — the GUI layout API used inside `ClientHUDInitializedEvent` handlers
- Server analogue: [`../../../WurmModLoader/docs/guides/event-bus.md`](../../../WurmModLoader/docs/guides/event-bus.md)
- Real-world reference: [`../../mods/livemap/src/main/java/com/garward/mods/livemap/LiveMapClientMod.java`](../../mods/livemap/src/main/java/com/garward/mods/livemap/LiveMapClientMod.java) — uses four of the six lifecycle events
