# WurmModLoader Client

Modern client-side modding framework for Wurm Unlimited, mirroring the architecture of WurmModLoader Server.

## Overview

WurmModLoader Client provides a clean, event-driven architecture for creating client-side mods. It replaces the legacy Maven-based client modlauncher with a modern Gradle build system and introduces a structured event bus pattern.

## Architecture

### Module Structure

- **wurmmodloader-client-api** - Public API for mod developers
  - Event base classes and annotations
  - Client lifecycle events
  - Input and prediction events (planned)

- **wurmmodloader-client-core** - Core implementation
  - ProxyClientHook and ClientHook
  - Event bus implementation
  - Bytecode patch management

- **wurmmodloader-client-patcher** - Client patcher launcher
  - Applies bytecode patches to Wurm client
  - Bootstraps the modloader

- **wurmmodloader-client-legacy** - Compatibility layer
  - Supports legacy Ago client mods
  - Bridges old interfaces to new event system

### Hook Architecture

Following the server-side pattern:

```
BYTECODE PATCH → ProxyClientHook.fireXyzEvent(...) → ClientHook.fireXyz(...) → EventBus.post(...)
```

- **ProxyClientHook**: Static singleton with static methods ending in "Event"
  - Called directly by bytecode patches
  - Thread-safe singleton instance

- **ClientHook**: Instance methods for event firing
  - Manages event bus
  - Registers mod listeners

## Current Status

**Version 0.1.0 - Initial Setup**

✅ Completed:
- Gradle build structure
- Module layout (api, core, patcher, legacy)
- Base event system (Event, SubscribeEvent, EventPriority)
- Lifecycle events (ClientInitEvent, ClientWorldLoadedEvent, ClientTickEvent)
- ProxyClientHook and ClientHook base classes

🚧 In Progress:
- Event bus implementation
- Bytecode patching system
- Client patcher launcher

📋 Planned:
- Input events for prediction
- Entity update events
- ModComm sync channel
- Legacy mod compatibility layer
- Example prediction mod

## Guides

- [UI Layout API](docs/guides/ui-layout.md) — `ModStackPanel`, `LayoutHints`,
  `ModImageButton`, alignment / padding / weights for sidebars and panels
  without pixel guesswork.

## Building

```bash
./gradlew build         # Linux/macOS
gradlew.bat build       :: Windows
```

Create distribution ZIP:

```bash
./gradlew dist          # Linux/macOS
gradlew.bat dist        :: Windows
```

> **Windows users:** every `./foo.sh` command in this repo has a `foo.bat`
> equivalent (`build.bat`, `deploy.bat`, `build-and-deploy.bat`,
> `install-client-modloader.bat`). Run them from `cmd.exe` or PowerShell.
> Set `WURM_CLIENT_DIR` if your Wurm Unlimited install isn't at the Steam
> default (`C:\Program Files (x86)\Steam\steamapps\common\Wurm Unlimited\WurmLauncher\`).

## Roadmap

### Phase 1: Foundation (Current)
- [x] Gradle build system
- [x] Module structure
- [x] Base event classes
- [x] Hook infrastructure
- [ ] Event bus implementation
- [ ] Basic bytecode patching

### Phase 2: Client Lifecycle
- [ ] Client init hooks
- [ ] World loaded hooks
- [ ] Game loop/tick hooks
- [ ] Client patcher launcher

### Phase 3: Prediction Support
- [ ] Input events
- [ ] Entity position update events
- [ ] Client-server sync channel
- [ ] Example prediction mod

### Phase 4: Legacy Support
- [ ] Ago mod interface compatibility
- [ ] Legacy mod loader wrapper
- [ ] Migration guide

## Development

### Requirements

- Java 17 (toolchain)
- Gradle 8.x (included via wrapper)
- Wurm Unlimited client JARs

### Project Structure

```
wurmmodloader-client/
├── wurmmodloader-client-api/          # Public API
│   └── src/main/java/
│       └── com/garward/wurmmodloader/client/api/
│           └── events/
│               ├── base/              # Event, SubscribeEvent, EventPriority
│               └── lifecycle/         # Client lifecycle events
├── wurmmodloader-client-core/         # Core implementation
│   └── src/main/java/
│       └── com/garward/wurmmodloader/client/
│           ├── modloader/            # ClientHook, ProxyClientHook
│           └── bytecode/             # Patch management
├── wurmmodloader-client-patcher/      # Patcher launcher
└── wurmmodloader-client-legacy/       # Legacy compatibility
```

## Design Principles

1. **Mirror Server Architecture**: Follow WurmModLoader Server patterns
2. **Event-Driven**: All mod interactions through event bus
3. **Clean Separation**: Framework vs mod logic
4. **Legacy Compatible**: Support existing Ago mods
5. **Performance**: Lightweight, minimal overhead

## License

MIT License - See LICENSE file

## Credits

- Based on Ago's WurmClientModLauncher
- Architecture inspired by WurmModLoader Server
- Part of the Wurm Unlimited modding ecosystem
