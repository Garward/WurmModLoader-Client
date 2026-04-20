# Getting Started — Client Mods

A modder's onramp to **WurmModLoader-Client**. If you've written a server
mod with the [server framework](../../../WurmModLoader/docs/getting-started/index.md)
the shape will be familiar — same `@SubscribeEvent` annotation, same
"framework owns Wurm imports" rule. The differences are explained below.

> Looking for **server** mods (item templates, combat hooks, capabilities)?
> See the [server getting-started hub](../../../WurmModLoader/docs/getting-started/index.md).
> The two repos are separate. A complete feature often has *both* — the
> [LiveMap mod](../../mods/livemap/) is a real example, with a server-side
> HTTP endpoint and this client mod consuming it.

---

## What WurmModLoader-Client is

A drop-in replacement for Ago's `WurmClientModLauncher`. It patches the
Wurm client jar with bytecode hooks at lifecycle / render / input
boundaries, isolates each mod in its own classloader, and exposes a typed
event bus mods subscribe to. Same architecture as the server framework,
just on the other side of the wire.

> **Windows users:** every `./foo.sh` / `./gradlew` command shown below has
> a `foo.bat` / `gradlew.bat` equivalent. Run them from `cmd.exe` or
> PowerShell in the repo root.

**What you get over the Ago-era client launcher:**

- Annotation-driven event bus (`@SubscribeEvent`) — no marker interfaces
- A real GUI layout API (`ModStackPanel`, `ModBorderPanel`, `ModImageButton`)
  so you can build sidebars and HUD panels without pixel-guessing — see
  [`../guides/ui-layout.md`](../guides/ui-layout.md)
- HUD registration helpers (`ModHud`) for persistent windows that survive
  world reloads
- ServerInfo discovery (`ServerInfoRegistry`) so client mods can find
  their server-side counterpart's HTTP endpoints
- ModComm channel hookup for typed server↔client packets
- Gradle build, single-command deploy

Legacy Ago client mods still work via the legacy bridge — but **new mods
should use the `com.garward.wurmmodloader.client.api.*` namespace**.
See [`../guides/legacy-mod-compat.md`](../guides/legacy-mod-compat.md)
if you have an Ago-era client mod to keep running.

---

## Server vs client — what's different

| Concern | Server framework | Client framework |
|---|---|---|
| Wurm package | `com.wurmonline.server.*` | `com.wurmonline.client.*` |
| Mod interface | `WurmServerMod` (with `init()` / `preInit()`) | None required — just a class with `@SubscribeEvent` methods |
| First-run target | `ServerStartedEvent` | `ClientInitEvent` (boot) and `ClientWorldLoadedEvent` (in-world) |
| Mod descriptor | `mods/<name>.properties` (auto-deployed by `build-and-deploy.sh`) | `WurmLauncher/mods/<name>.properties` — **manual `cp` required**, the build script doesn't deploy mods |
| What runs in your handler | Game state mutations (items, creatures, players) | UI + input + presentation; talk to the server via packets / HTTP |

The "manual deploy" caveat is real and has bitten me before — the client
`build-and-deploy.sh` builds and patches the client jar but doesn't copy
mod jars into `WurmLauncher/mods/`. Use the per-mod `deployMod` Gradle
task or `cp` it yourself.

---

## Project layout — what lives where

| Path | What it is |
|---|---|
| `wurmmodloader-client-api/` | Public API — events, GUI primitives, `ModHud`, `ServerInfoRegistry`. Mods compile against this. |
| `wurmmodloader-client-core/` | Engine internals — bytecode patches, hook installation, event bus. Mods don't import from here. |
| `wurmmodloader-client-patcher/` | The launcher: patches `client.jar` and starts the modded client. Run once per Wurm update. |
| `wurmmodloader-client-legacy/` | Bridges Ago client mods to the modern event system. Don't depend on it from new mods. |
| `examples/hellomod/` | **Smallest possible client mod.** One class, two event handlers. Start here. |
| `mods/livemap/` | **Reference mod** — connects to a server-side HTTP endpoint via `ServerInfoRegistry`, paints a live map window + minimap with the GUI layout API, registers itself with `ModHud`. Read this when you want to see how a real client mod is structured. |
| `mods/serverpacks/` | Smaller reference mod for server-pushed resource packs. |

---

## Hello-mod in 10 minutes

A complete, working client mod that logs when the client boots and when
the player enters a world.

### 1. Source code

`examples/hellomod/src/main/java/com/garward/wurmmodloader/examples/hellomod/HelloMod.java`:

```java
package com.garward.wurmmodloader.examples.hellomod;

import com.garward.wurmmodloader.client.api.events.base.SubscribeEvent;
import com.garward.wurmmodloader.client.api.events.lifecycle.ClientInitEvent;
import com.garward.wurmmodloader.client.api.events.lifecycle.ClientWorldLoadedEvent;

import java.util.logging.Logger;

public class HelloMod {
    private static final Logger logger = Logger.getLogger(HelloMod.class.getName());

    @SubscribeEvent
    public void onClientInit(ClientInitEvent event) {
        logger.info("[HelloMod] Client initialized — modloader is alive.");
    }

    @SubscribeEvent
    public void onWorldLoaded(ClientWorldLoadedEvent event) {
        logger.info("[HelloMod] World loaded — welcome in.");
    }
}
```

No `com.wurmonline.*` imports. No mod interface to implement — the
framework discovers `@SubscribeEvent` methods on whatever class the
descriptor points at.

### 2. Mod descriptor

`examples/hellomod/HelloMod.properties`:

```properties
classname=com.garward.wurmmodloader.examples.hellomod.HelloMod
classpath=hellomod.jar
sharedClassLoader=true
```

`sharedClassLoader=true` lets your mod see Wurm's classes. Set it `false`
if you want strict isolation (rare for client mods that touch the GUI).

### 3. Build script

`examples/hellomod/build.gradle.kts`:

```kotlin
plugins { java }

java {
    toolchain { languageVersion.set(JavaLanguageVersion.of(17)) }
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

dependencies {
    compileOnly(project(":wurmmodloader-client-api"))
    val wurm = System.getenv("WURM_CLIENT_DIR")
        ?: error("Set WURM_CLIENT_DIR (or wurmClientDir in ~/.gradle/gradle.properties) to your Wurm Unlimited/WurmLauncher dir.")
    compileOnly(files("$wurm/client.jar"))
    compileOnly(files("$wurm/common.jar"))
}
```

Java 17 toolchain (build), Java 8 bytecode (Wurm runtime).

### 4. Build + deploy

The hellomod ships with a `deployMod` task that copies the jar +
properties into `WurmLauncher/mods/`:

```bash
./gradlew :examples:hellomod:deployMod
```

For your own mods, copy that task block into your `build.gradle.kts`.
Without it you'd run `./gradlew :examples:hellomod:jar` and `cp` the jar
yourself.

### 5. Launch + verify

Start the patched client (see [`INSTALLATION.md`](../../INSTALLATION.md)
for first-time setup) and watch the client log. You should see:

```
INFO: [HelloMod] Client initialized — modloader is alive.
INFO: [HelloMod] World loaded — welcome in.
```

If only the first line appears, the mod loaded but never made it
in-world — that's normal until you actually log into a server.

---

## Questions you probably have right now

If you're coming from Ago-era client modding, these are the "wait,
why?" moments that trip people up on the first mod. Answers are short
on purpose — each points at the doc with the full story.

### "Why Java 17 toolchain but Java 8 bytecode?"

Wurm runs on Java 8. Your mod's *class files* must be Java 8
compatible or the client's classloader rejects them. But modern Gradle
and modern IDEs want a recent JDK to *run*. The split —
`languageVersion = 17`, `sourceCompatibility / targetCompatibility = 1.8`
— means you build with a modern toolchain but emit bytecode Wurm can
actually load. Don't "upgrade" those `1.8` lines; you'll get
`UnsupportedClassVersionError` at client launch.

### "Where's the auto-deploy step for my mod jar?"

There isn't one at the framework level — deploy is per-mod on
purpose. Every mod owns a `deployMod` Gradle task (template in
`examples/hellomod/build.gradle.kts`) that copies its jar +
`.properties` into `WurmLauncher/mods/`. Forgetting to add that task
and then wondering why `./gradlew build` "did nothing" is the single
most common first-mod footgun. See
[`../guides/troubleshooting.md`](../guides/troubleshooting.md).

### "Why no `WurmClientMod` interface anymore?"

The modern framework is annotation-driven: any class with
`@SubscribeEvent` methods is a mod, the descriptor just names the
class. Ago-era mods implementing `WurmClientMod` still work via the
legacy bridge — see
[`../guides/legacy-mod-compat.md`](../guides/legacy-mod-compat.md) —
but new code shouldn't use it.

### "Do I still have to write Javassist patches by hand?"

Usually no. The framework ships patches for the lifecycle, render,
input, GUI, map, movement, combat, and packet boundaries — you
subscribe to events instead. See
[`../guides/lifecycle-events.md`](../guides/lifecycle-events.md) for
the full catalog. If the event you need isn't there, the
centralized-patch pattern is documented in
[`../guides/widening-and-guiaccess.md`](../guides/widening-and-guiaccess.md)
— but check the catalog first.

### "Why can't I just `new WWindow(...)` — it says 'not public'?"

Wurm's GUI toolkit is almost all package-private. The framework
widens a curated set of classes to `public` via bytecode patch so mods
can compile against them like a normal API. If you hit a widget
that's not on the list, append it to `GUI_CLASS_WIDENINGS` and rebuild
the patcher — full four-step recipe in
[`../guides/widening-and-guiaccess.md`](../guides/widening-and-guiaccess.md).

### "Why `compileOnly` for `client.jar` / `common.jar`?"

Those jars are already on the classpath at runtime (the player has
them — it's the game). Bundling them into your mod jar would bloat it
by ~30MB and risk classloader conflicts. `compileOnly` means "use for
compilation, don't ship."

### "Why no version suffix in the jar filename?"

The loader matches by exact filename against `classpath=` in the
`.properties` descriptor. `hellomod-0.1.0.jar` ≠ `hellomod.jar`. Set
`archiveVersion.set("")` in your `build.gradle.kts` to strip it.

### "Why does the client need patching at all? Ago's loader ran patches at runtime."

It still *runs* Javassist, just earlier: once into `client.jar`
instead of on every launch. That means your IDE sees widened classes,
code-completes against them, and mods compile against a real public
API rather than reflecting into package-private internals. The
tradeoff is that adding a new widening means re-running the patcher
(restore from `client.jar.backup` first — the idempotence check will
otherwise skip). Wurm is effectively frozen, so this is a setup-time
cost, not a recurring one. See [`../../PATCHER.md`](../../PATCHER.md).

### "What about `sharedClassLoader=true` — what does that actually do?"

Without it, your mod runs in its own `URLClassLoader` that can't see
`com.wurmonline.*` — fine for pure logic mods, broken the moment you
touch the GUI or a game class. Set it `true` (the default for most
client mods) unless you specifically want isolation.

### "Is there a server-side half to this framework?"

Yes, separate repo:
[`../../../WurmModLoader/`](../../../WurmModLoader/). Same event-bus
architecture, same `@SubscribeEvent` annotation, different Wurm
package (`com.wurmonline.server.*`). A feature with both ends (chat
tweaks, live map, custom items-with-UI) is usually two mods — one per
side. LiveMap is the reference pair.

---

## Where to go from here — system index

Each link below is one focused topic. Pick the one matching what you're
building.

### Core systems

| Want to… | Read |
|---|---|
| Subscribe to lifecycle / render / input events | [`../guides/lifecycle-events.md`](../guides/lifecycle-events.md) |
| Diagnose why a mod won't load, crashes, or renders wrong | [`../guides/troubleshooting.md`](../guides/troubleshooting.md) |
| Build a sidebar, HUD panel, or icon-button row without pixel guessing | **[`../guides/ui-layout.md`](../guides/ui-layout.md)** — `ModStackPanel`, `ModBorderPanel`, `ModImageButton`, `LayoutHints` |
| Find your server-side counterpart's HTTP endpoint (e.g. for a live map) | **[`../guides/client-server-bridge.md`](../guides/client-server-bridge.md)** — HTTP endpoints, `ServerInfoRegistry`, `ServerCapabilities`, ModComm |
| Register a window so it survives world reloads + remembers position | `ModHud` — see livemap's `ClientHUDInitializedEvent` handler |
| Paint a popup / form to the player | Two paths: **server-authored** (works on any vanilla client, no client mod needed) — [`../../../WurmModLoader/docs/guides/questions-api.md`](../../../WurmModLoader/docs/guides/questions-api.md) + [`../../../WurmModLoader/docs/guides/bml-ui.md`](../../../WurmModLoader/docs/guides/bml-ui.md). **Client-authored** (rich custom UI, requires your client mod) — [`./ui-layout.md`](../guides/ui-layout.md) |
| Receive typed packets from a server mod | [`../guides/client-server-bridge.md`](../guides/client-server-bridge.md) — ModComm section; see also `serverpacks/` |
| Patch a vanilla widget that isn't widened yet | [`../guides/widening-and-guiaccess.md`](../guides/widening-and-guiaccess.md) |

### Reference

| Need | Doc |
|---|---|
| First-time client install / launcher setup | [`../../INSTALLATION.md`](../../INSTALLATION.md), [`../../PATCHER.md`](../../PATCHER.md) |
| Bytecode patch internals (FOV detection, etc.) | [`../../FOV_CHANGE_DETECTION.md`](../../FOV_CHANGE_DETECTION.md), [`../../GUI_FRAMEWORK.md`](../../GUI_FRAMEWORK.md) |
| Architecture overview | [`../../README.md`](../../README.md) |

### Reading mod source

When you want to see "how is this actually done in a real client mod":

- **[`../../examples/hellomod/`](../../examples/hellomod/)** — minimum viable client mod
- **[`../../mods/livemap/`](../../mods/livemap/)** — **the reference**: a
  full mod with custom UI (sidebar with icon buttons, full-window map +
  HUD minimap), HTTP fetch + tile cache, ModComm-driven server discovery,
  HUD registration with persistent positions. Read
  [`LiveMapClientMod.java`](../../mods/livemap/src/main/java/com/garward/mods/livemap/LiveMapClientMod.java)
  for the wiring, then
  [`gui/LiveMapWindow.java`](../../mods/livemap/src/main/java/com/garward/mods/livemap/gui/LiveMapWindow.java)
  for the layout-API usage.
- **[`../../mods/serverpacks/`](../../mods/serverpacks/)** — small mod for
  server-pushed resource packs; good ModComm reference.

---

## Conventions

- **Namespace.** New client mods go under `com.garward.mods.<name>` (or
  your own `com.<you>.*`). The framework itself lives under
  `com.garward.wurmmodloader.client.*` — don't put mod code there.
- **No vanilla imports unless you need them.** The layout API,
  `ModHud`, `ServerInfoRegistry`, and the events cover most cases. If you
  do need `com.wurmonline.client.*`, that's fine — but check whether
  there's a wrapper first. Widenings are added centrally; see
  [`../guides/widening-and-guiaccess.md`](../guides/widening-and-guiaccess.md)
  *(when written)*.
- **Mod jars must match the descriptor.** `classpath=hellomod.jar` in
  `.properties` → built jar named exactly `hellomod.jar` (no version
  suffix in the filename — `archiveVersion.set("")` in `build.gradle.kts`).
- **Manual deploy.** The repo's `build-and-deploy.sh` builds and patches
  the client; it does **not** copy mod jars. Use a per-mod `deployMod`
  Gradle task (template in hellomod) or `cp` by hand.
- **Verify in the client log, not in theory.** The client log lives in
  Wurm's launcher dir; tail it while testing.

---

## Adjacent docs

- [`../../README.md`](../../README.md) — project overview + module map
- [`../../INSTALLATION.md`](../../INSTALLATION.md) — first-time setup
- [`../../PATCHER.md`](../../PATCHER.md) — how the patcher works
- [`../guides/ui-layout.md`](../guides/ui-layout.md) — the GUI layout API
- Server side: [`../../../WurmModLoader/docs/getting-started/index.md`](../../../WurmModLoader/docs/getting-started/index.md)
