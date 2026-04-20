# World Map Replacement — Hook Points Reference

Research snapshot for replacing Wurm Unlimited's hardcoded vanilla "World Map"
window (Freedom Isles parchment of Xanadu/Celebration/Deliverance/etc. — servers
that don't exist on WU installs) with a custom window that renders our own live
map tiles and supports scroll-wheel zoom + drag pan.

**Status:** Phase 2 target. Phase 1 (minimap port from legacy `LiveHudMap`) must
land first — this document is the plan for what comes after.

---

## Vanilla map entry points

- **`HeadsUpDisplay.toggleWorldMapVisible()`** — primary toggle (line ~1547 in
  decompiled source). No args, void return. Descriptor: `()V`.
- **`HeadsUpDisplay.getWorldMap()`** — accessor for the cached window (line ~2362).
- **M key** triggers it through the `PlayerObj` key-handler chain.

## WorldMap window internals

- **Hierarchy:** `WorldMap extends WWindow` (confirmed via override signatures).
- **Rendering:** `renderComponent(Queue queue, float alpha)` — this is the method
  that paints the hardcoded parchment. Override/skip this to replace.
- **Static bitmap source:** `WorldMapXml.load()` — where the Freedom Isles
  parchment data originates.
- **Mouse handling (from `WurmComponent` / `WWindow` parents):**
  - `leftPressed(int xMouse, int yMouse, int clickCount)`
  - `leftReleased(int xMouse, int yMouse)`
  - `mouseDragged(int xMouse, int yMouse)`
  - `mouseWheeled(int xMouse, int yMouse, int wheelDelta)`
    (positive=up, negative=down)
  - `rightPressed(int xMouse, int yMouse, int clickCount)`
- **Built-in zoom:** `toggleZoom(int xMouse, int yMouse)` / `toggleZoomCenter()`
  — binary toggle only, not scroll-wheel continuous zoom.

## Replacement strategies (ranked)

### Strategy A — Bytecode patch `toggleWorldMapVisible` (LOW-MEDIUM)
- New `WorldMapTogglePatch` intercepts the toggle method.
- Fires `WorldMapToggleRequestedEvent`.
- Mod listens and opens its own window; vanilla call is skipped.
- Touch surface: 1 patch + 1 event class + hook plumbing.
- Risk: conflicts with other patches on same method (handled by conflict keys).

### Strategy B — Reflection replace via `ClientHUDInitializedEvent` (VERY LOW)
- No bytecode patch.
- On HUD init, reflect the `worldMap` field in `HeadsUpDisplay` and swap in our
  window subclass.
- Risk: fragile to field-name changes; init-timing dependent.

### Strategy C — Event-driven with suppress flag (RECOMMENDED)
- Same as Strategy A, but the event exposes `suppressVanilla()` so multiple mods
  can cooperate.
- Patch checks the flag after firing; if suppressed, skips vanilla code path.
- Most flexible for multi-mod environments.

## Scroll-wheel zoom + drag pan

- **Mouse-wheel API:** `mouseWheeled(int xMouse, int yMouse, int wheelDelta)` —
  inherited from `WurmComponent`; `WWindow` doesn't override by default. Our
  custom window overrides it for continuous zoom.
- **Legacy LiveHudMap reference:**
  - `MapLayerView` tracks zoom level; `zoomIn()` / `zoomOut()` flip a dirty flag.
  - Pan: track last-mouse on `leftPressed`, compute delta in `mouseDragged`,
    apply offset.
  - See `LiveMap.java:106-127` — zoom marks dirty, `update()` re-renders tiles.
- **Framework event already exists:** `MouseScrollEvent` (with `isScrollUp()` /
  `isScrollDown()`). Can be reused if we want wheel events delivered via bus
  rather than overriding the component method directly.

## Existing framework touch-points

- **`ClientHUDInitializedEvent`** — already implemented. Fired after
  `HeadsUpDisplay.init(int, int)` by `HeadsUpDisplayInitPatch`. Provides
  `hud`, `world`, `mainMenu` (as `Object`), plus `screenWidth` / `screenHeight`.
  Perfect moment to construct the replacement window.
- **`ClientWorldLoadedEvent`** — wait for this before enabling the tile
  renderer (world + terrain data must be ready).
- **Patch registration:** `BytecodePatch` interface with `getTargetClassName()`,
  `apply(CtClass)`, `getPriority()`, `getConflictKeys()`. Central
  `PatchRegistry` is TODO per client CLAUDE.md — patches currently registered
  by direct wiring. Check current registration pattern by reading
  `HeadsUpDisplayInitPatch` and `ClientInitPatch` before adding new ones.

## Minimum patches list

To replace the vanilla world map (Strategy C):

### New files
1. `wurmmodloader-client-api/.../client/api/events/map/WorldMapToggleRequestedEvent.java`
   — extends `Event`, exposes `suppressVanilla()` + `isSuppressed()`.
2. `wurmmodloader-client-core/.../client/core/bytecode/patches/WorldMapTogglePatch.java`
   — `BytecodePatch`, target
   `com.wurmonline.client.renderer.gui.HeadsUpDisplay`, hook on
   `toggleWorldMapVisible()`, fires
   `ProxyClientHook.fireWorldMapToggleRequestedEvent()`; if suppressed,
   short-circuits before vanilla code.

### Files to modify
1. `wurmmodloader-client-core/.../client/modloader/ProxyClientHook.java` —
   add `public static boolean fireWorldMapToggleRequestedEvent()` (returns
   suppress flag).
2. `wurmmodloader-client-core/.../client/modloader/ClientHook.java` —
   add `public boolean fireWorldMapToggle()` that posts the event and returns
   `event.isSuppressed()`.
3. Wherever client patches are currently registered — add
   `WorldMapTogglePatch`.

### Mod-side
- Custom window extending `WWindow`, placed in
  `com.wurmonline.client.renderer.gui.*` (package-private access requirement,
  not a legacy-path exception).
- Override `renderComponent`, `mouseWheeled`, `mouseDragged`, `leftPressed`,
  `leftReleased`.
- `@SubscribeEvent` handler on `WorldMapToggleRequestedEvent` calls
  `event.suppressVanilla()` and toggles visibility on the custom window.

## Reference classes

- `modules/mods/LiveHudMap/src/main/java/com/wurmonline/client/renderer/gui/LiveMapWindow.java`
- `modules/mods/LiveHudMap/src/main/java/com/wurmonline/client/renderer/gui/LiveMapView.java`
- `modules/mods/LiveHudMap/src/main/java/org/gotti/wurmonline/clientmods/livehudmap/LiveMap.java`
  (zoom/pan logic at 106-127)
- Patch examples: `HeadsUpDisplayInitPatch.java`, `ClientInitPatch.java`,
  `FOVChangePatch.java`

---

**Last captured:** 2026-04-18 (post-compaction explore-agent run). Verify method
signatures against live decompiled source before coding — this is a snapshot.
