# WurmModLoader — Client

A drop-in replacement for Ago's `WurmClientModLauncher` with an annotation-driven
event bus, widened GUI surface, and a typed layout API so you can build HUD
panels and sidebars without pixel-guessing.

## Quick links

- **[Getting Started](getting-started/index.md)** — onramp for new client modders, 10-minute hello-mod, Q&A for Ago-era habits
- **[Lifecycle Events](guides/lifecycle-events.md)** — boot / world-load / tick / capability-handshake event catalog
- **[Client ↔ Server Bridge](guides/client-server-bridge.md)** — HTTP endpoints, `ServerInfoRegistry`, capability gating, ModComm packets
- **[UI Layout API](guides/ui-layout.md)** — `ModStackPanel`, `ModBorderPanel`, `ModImageButton`, `LayoutHints`
- **[Widening & GuiAccess](guides/widening-and-guiaccess.md)** — patching a vanilla widget that isn't exposed yet
- **[Troubleshooting](guides/troubleshooting.md)** — when it breaks
- **[Legacy Mod Compatibility](guides/legacy-mod-compat.md)** — running Ago-era client mods

## Server-side counterpart

Server mods (game logic, items, combat, capabilities) live in a separate repo:
[WurmModLoader](https://github.com/garward/WurmModLoader). A complete feature
with both ends (e.g. LiveMap) is usually two mods — one per repo.
