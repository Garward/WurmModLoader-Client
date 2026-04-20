# Client Modloader GUI Framework Expansion

## Overview

This document describes the event-driven GUI framework expansion for WurmModLoader Client (v0.2.0+).

## Problem Statement

Wurm's GUI system (`com.wurmonline.client.renderer.gui`) has several restrictions:
- `FlexComponent` fields (x, y, width, height) are package-private
- `Renderer` methods are package-private or protected
- Extending `FlexComponent` requires mods to be in the `com.wurmonline.client.renderer.gui` package
- No clean way to create custom GUI components without package pollution

**Old approach:** Put mod code in `com.wurmonline.client.renderer.gui` package (BAD - mixes mod/vanilla code)

**New approach:** Event-driven GUI system with proper mod isolation (GOOD - follows server modloader pattern)

## Architecture

### Event-Driven GUI Components

Mods create GUI components by:
1. Extending `ModComponent` base class (in mod package)
2. Subscribing to GUI events (`ComponentRenderEvent`, `MouseClickEvent`, etc.)
3. Using `RendererHelper` for safe rendering
4. Bytecode patches fire events from vanilla GUI lifecycle methods

### Component Lifecycle

```
Vanilla FlexComponent.renderComponent()
    ↓
Bytecode patch intercepts
    ↓
ProxyClientHook.fireComponentRenderEvent(component, queue, x, y, width, height, alpha)
    ↓
ClientHook.fireComponentRender(...)
    ↓
EventBus.post(ComponentRenderEvent)
    ↓
Mod's @SubscribeEvent handler receives event
    ↓
Mod renders using RendererHelper.drawTexture(...)
```

## New APIs

### 1. Events (wurmmodloader-client-api)

**`ComponentRenderEvent`** - Fired when component needs to render
- Provides: queue, x, y, width, height, alpha
- Use: Render custom content with `RendererHelper`

**`MouseClickEvent`** - Fired on mouse press/release
- Provides: button (left/right/middle), position, click count, pressed state
- Cancellable: yes

**`MouseDragEvent`** - Fired when mouse dragged
- Provides: position, delta
- Cancellable: yes

**`MouseScrollEvent`** - Fired on mouse wheel scroll
- Provides: delta (up/down)
- Cancellable: yes

### 2. Rendering Helper

**`RendererHelper`** - Safe access to Wurm rendering (uses reflection internally)
- `drawTexture(queue, texture, x, y, width, height)` - Basic textured quad
- `drawTexture(queue, texture, r, g, b, a, x, y, width, height)` - With tint
- `drawTextureWithUV(...)` - Advanced UV mapping

### 3. Base Class

**`ModComponent`** - Base class for custom components
- Stores component ID
- Provides `isMyComponent(event)` helper
- Tracks bounds (x, y, width, height)

## Usage Example

### Creating a Custom Map Component

```java
package com.garward.mods.livemap.gui;

import com.garward.wurmmodloader.client.api.events.base.SubscribeEvent;
import com.garward.wurmmodloader.client.api.events.gui.*;
import com.garward.wurmmodloader.client.api.gui.ModComponent;
import com.garward.wurmmodloader.client.api.rendering.RendererHelper;

public class LiveMapView extends ModComponent {

    private final MapDataCache cache;
    private final TileRenderer renderer;

    public LiveMapView(String id, MapDataCache cache) {
        super(id);
        this.cache = cache;
        this.renderer = new TileRenderer(cache);
    }

    @SubscribeEvent
    public void onRender(ComponentRenderEvent event) {
        if (!isMyComponent(event)) return;

        // Render map tiles
        for (Tile tile : getVisibleTiles()) {
            BufferedImage tileImage = cache.getTile(tile.zoom, tile.x, tile.y);
            if (tileImage != null) {
                ImageTexture texture = convertToTexture(tileImage);
                RendererHelper.drawTexture(
                    event.getQueue(),
                    texture,
                    event.getX() + tile.screenX,
                    event.getY() + tile.screenY,
                    256, 256
                );
            }
        }
    }

    @SubscribeEvent
    public void onMouseClick(MouseClickEvent event) {
        if (!isMyComponent(event)) return;

        if (event.isLeftButton() && event.isPressed()) {
            // Handle map click
            handleMapClick(event.getMouseX(), event.getMouseY());
        }
    }

    @SubscribeEvent
    public void onMouseDrag(MouseDragEvent event) {
        if (!isMyComponent(event)) return;

        // Pan the map
        panMap(event.getDeltaX(), event.getDeltaY());
    }

    @SubscribeEvent
    public void onMouseScroll(MouseScrollEvent event) {
        if (!isMyComponent(event)) return;

        if (event.isScrollUp()) {
            zoomIn();
        } else {
            zoomOut();
        }
    }
}
```

### Registering with HUD

```java
@SubscribeEvent
public void onHUDInitialized(ClientHUDInitializedEvent event) {
    // Create mod component
    LiveMapView mapView = new LiveMapView("livemap_view", cache);

    // TODO: Need FlexComponent wrapper that:
    // 1. Stores modComponentId field
    // 2. Delegates rendering/input to event system
    // 3. Can be added to HUD like normal component

    // For now, create wrapper via reflection or helper
    Object flexWrapper = createFlexComponentWrapper(mapView);

    // Add to HUD
    HeadsUpDisplay hud = (HeadsUpDisplay) event.getHud();
    hud.addComponent(flexWrapper);
}
```

## Remaining Implementation

### Bytecode Patches Needed

**1. FlexComponentRenderPatch**
```java
// Hook: FlexComponent.renderComponent(Queue queue, float alpha)
// After: Fire ComponentRenderEvent with this component's bounds
```

**2. FlexComponentMousePatch**
```java
// Hook: FlexComponent.leftPressed/leftReleased/rightPressed/mouseDragged/mouseScrolled
// After: Fire appropriate MouseXxxEvent
```

**3. FlexComponentWrapperPatch** (if needed)
```java
// Create or modify FlexComponent to support modComponentId field
// This allows events to identify which ModComponent they're for
```

### Event Wiring

Add to `ClientHook.java`:
```java
public void fireComponentRender(Object component, Object queue, int x, int y, int width, int height, float alpha) {
    ComponentRenderEvent event = new ComponentRenderEvent(component, queue, x, y, width, height, alpha);
    eventBus.post(event);
}

public void fireMouseClick(Object component, int x, int y, int button, int clickCount, boolean pressed) {
    MouseClickEvent event = new MouseClickEvent(component, x, y, button, clickCount, pressed);
    eventBus.post(event);
}
// ... etc for drag, scroll
```

Add to `ProxyClientHook.java`:
```java
public static void fireComponentRenderEvent(Object component, Object queue, int x, int y, int width, int height, float alpha) {
    getInstance().fireComponentRender(component, queue, x, y, width, height, alpha);
}
// ... etc
```

## Benefits

✅ **Clean separation** - Mod code stays in mod packages
✅ **No reflection in mods** - Framework handles it via RendererHelper
✅ **Event-driven** - Same pattern as server modloader
✅ **Extensible** - Easy to add more events as needed
✅ **Safe** - Mods can't break vanilla GUI by accident

## Next Steps

1. Create bytecode patches for FlexComponent rendering/input
2. Wire events through ClientHook/ProxyClientHook
3. Create FlexComponent wrapper helper
4. Refactor LiveMap to use new system
5. Test and document

## Migration Path

**Old approach (BAD):**
```java
// Mod code in com.wurmonline.client.renderer.gui package
public class LiveMapWindow extends WWindow {
    protected void renderComponent(Queue queue, float alpha) {
        // Direct access to protected/package fields
        Renderer.texturedQuad(queue, texture, x, y, width, height);
    }
}
```

**New approach (GOOD):**
```java
// Mod code in com.garward.mods.livemap.gui package
public class LiveMapView extends ModComponent {
    @SubscribeEvent
    public void onRender(ComponentRenderEvent event) {
        RendererHelper.drawTexture(event.getQueue(), texture,
            event.getX(), event.getY(), 256, 256);
    }
}
```

---

**Version**: 0.2.0
**Status**: In Progress
**Last Updated**: 2025-11-20
