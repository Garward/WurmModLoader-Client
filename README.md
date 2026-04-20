# WurmModLoader Client

Event-driven client-side modding framework for Wurm Unlimited. A ground-up rewrite of Ago's `WurmClientModLauncher` — Gradle build, proper event bus, isolated classloaders, ModComm, and a layout-aware GUI toolkit for mods.

Pairs with [WurmModLoader (server)](https://github.com/Garward/WurmModLoader). Each side is independent — you can use one without the other — but matched pairs unlock the full feature set (server-pushed resource packs, livemap, ModComm channels).

## What's in the box

**Framework**

- Thread-safe event bus with `@SubscribeEvent` scanning, priority ordering, cancellation
- 20+ bytecode patches registered through a central `PatchRegistry`, applied at class-load time via a Java agent (`ClientPatcher.premain`)
- Isolated `URLClassLoader` per mod; two loading modes (properties-file + JAR-scan)
- GUI access-widening: 8 Wurm client GUI classes exposed to mods without reflection gymnastics
- ModComm client channel ported from Ago, integrated with the event bus
- Server discovery / `ServerInfoRegistry` for mods that need to know what server they're on

**Events defined (not exhaustive)**

- Lifecycle — `ClientInitEvent`, `ClientWorldLoadedEvent`, `ClientTickEvent`
- GUI input — `MouseClickEvent`, `MouseScrollEvent`, `MouseDragEvent`, `ComponentRenderEvent`
- Movement — `ClientMovementIntentEvent`, `ClientPrePlayerUpdateEvent`, `ClientPostPlayerUpdateEvent`, `AuthoritativePlayerPositionEvent`, `ServerCorrectionReceivedEvent`
- Combat — `ClientCombatAnimationStartEvent`, `ClientCombatAnimationEndEvent`
- World / HUD — `ClientNpcUpdateEvent`, `MapTileReceivedEvent`, `MapDataReceivedEvent`, `WorldMapToggleRequestedEvent`, `ClientHUDInitializedEvent`, `FOVChangedEvent`

**GUI toolkit for mods**

`ModComponent`, `ModStackPanel`, `ModBorderPanel`, `ModImageButton`, `ModHud`, `LayoutHints`, `Alignment` — declarative layout with weights/alignment/padding, no pixel-hunting. See [`docs/guides/ui-layout.md`](docs/guides/ui-layout.md).

**Bundled reference mods**

- [`mods/livemap/`](mods/livemap/) — live server map rendered inside the client HUD (tile renderer, HTTP fetch, ModComm-assisted server discovery)
- [`mods/serverpacks/`](mods/serverpacks/) — server-pushed resource pack downloader. Replaces Ago's serverpacks (~50% reliability) with an event-driven version that lands closer to 100%
- [`examples/hellomod/`](examples/hellomod/) — minimal `@SubscribeEvent` example, useful as a starter template

## Installation

Install into your Wurm Unlimited client directory (the one containing `client.jar` + `common.jar`).

**Linux/macOS**
```bash
./install-client-modloader.sh
```

**Windows**
```cmd
install-client-modloader.bat
```

Set `WURM_CLIENT_DIR` if your install isn't at the Steam default:
- Windows: `C:\Program Files (x86)\Steam\steamapps\common\Wurm Unlimited\WurmLauncher`
- Linux: `~/.local/share/Steam/steamapps/common/Wurm Unlimited/WurmLauncher`

See [`INSTALLATION.md`](INSTALLATION.md) for the full walkthrough, or [`PATCHER.md`](PATCHER.md) for what the patcher does to `client.jar`.

## Writing a mod

A mod is a JAR containing one or more classes with `@SubscribeEvent` methods. There's a 10-minute onramp in [`docs/getting-started/index.md`](docs/getting-started/index.md); the shape looks like:

```java
public class MyMod {
    @SubscribeEvent
    public void onClientInit(ClientInitEvent event) {
        System.out.println("[MyMod] client initialized");
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onMouseClick(MouseClickEvent event) {
        // ...
    }
}
```

Drop the built JAR into `<WurmLauncher>/mods/` (optionally alongside a `<modname>.properties` config) and launch.

## Building from source

```bash
./gradlew build                    # compile + test + jar
./gradlew dist                     # create distribution ZIP under build/distributions
./build-and-deploy.sh              # Linux/macOS: build + copy to your WurmLauncher
build-and-deploy.bat               :: Windows equivalent
```

Set `wurmClientDir=/path/to/WurmLauncher` in `~/.gradle/gradle.properties` (or `WURM_CLIENT_DIR` env var). See [`gradle.properties.example`](gradle.properties.example) and [`BUILD.md`](BUILD.md).

**Requirements:** Java 17 (toolchain), Gradle 8.x (via wrapper), Wurm Unlimited client JARs.

## Architecture

```
BYTECODE PATCH in Wurm client class
    ↓
ProxyClientHook.fireXyzEvent(...)      [static, ends in "Event"]
    ↓
ClientHook.fireXyz(...)                [instance, no "Event" suffix]
    ↓
EventBus.post(XyzEvent(...))
    ↓
@SubscribeEvent handlers in mods
```

Modules:

- `wurmmodloader-client-api` — public API: events, GUI toolkit, `BytecodePatch` / `PatchRegistry` interfaces, server-info types
- `wurmmodloader-client-core` — `EventBus`, `ClientHook`/`ProxyClientHook`, `ModLoader`, `PatchManager`, `CorePatches`, all bundled bytecode patches
- `wurmmodloader-client-patcher` — `ClientPatcher` Java agent + `WurmClientTransformer` (shadowed uber-JAR with relocated Javassist)
- `wurmmodloader-client-legacy` — `WurmClientMod` / `Versioned` interfaces for Ago-era mods (bridge adapter is still TODO — legacy mods don't currently load as-is)

## Status

**v0.2.0** — event bus, bytecode patching, mod loading, ModComm, and GUI access-widening are all working. Two substantive reference mods ship in the tree. Docs cover the common use cases (getting started, layout, lifecycle, client-server bridge, widening/GUI access, legacy compat, troubleshooting).

**Not there yet:**
- Legacy mod bridge — `wurmmodloader-client-legacy` currently only exposes the old `WurmClientMod` interface; no adapter yet that runs an Ago mod unmodified. Porting is straightforward in the meantime (see [`docs/guides/legacy-mod-compat.md`](docs/guides/legacy-mod-compat.md))
- The API surface is pre-1.0 and may break between minor versions until v1.0

## Documentation

- [`docs/getting-started/index.md`](docs/getting-started/index.md) — write your first mod
- [`docs/guides/lifecycle-events.md`](docs/guides/lifecycle-events.md) — init / world-loaded / tick
- [`docs/guides/ui-layout.md`](docs/guides/ui-layout.md) — `ModStackPanel`, `LayoutHints`, alignment
- [`docs/guides/widening-and-guiaccess.md`](docs/guides/widening-and-guiaccess.md) — touching Wurm's own GUI classes
- [`docs/guides/client-server-bridge.md`](docs/guides/client-server-bridge.md) — ModComm channels, server discovery
- [`docs/guides/legacy-mod-compat.md`](docs/guides/legacy-mod-compat.md) — porting Ago-era mods
- [`docs/guides/troubleshooting.md`](docs/guides/troubleshooting.md)

## License

MIT. See [`LICENSE`](LICENSE).

## Credits

- [Ago (`ago1024`)](https://github.com/ago1024/WurmClientModLauncher) — original client modlauncher this project replaces; ModComm protocol and the `WurmClientMod` interface shape carry forward from his work
- [WurmModLoader (server)](https://github.com/Garward/WurmModLoader) — sibling project; architecture and naming conventions mirror it where it makes sense
