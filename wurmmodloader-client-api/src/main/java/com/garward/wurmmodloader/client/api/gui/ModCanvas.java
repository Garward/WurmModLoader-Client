package com.garward.wurmmodloader.client.api.gui;

import com.wurmonline.client.renderer.backend.Queue;
import com.wurmonline.client.renderer.gui.ContainerComponent;
import com.wurmonline.client.renderer.gui.FlexComponent;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Absolute-positioning container. Each child is placed at a fixed
 * canvas-local (x, y) with an explicit (width, height). No layout-driven
 * resizing. Use for free-form HUDs: skill trees, maps, node graphs.
 *
 * <pre>{@code
 * ModCanvas canvas = new ModCanvas("tree", 800, 600);
 * canvas.placeChild(new ModLabel("Foundation"), 100, 60, 120, 24);
 * canvas.placeChild(edge, 100, 80, 200, 8);
 * }</pre>
 */
public class ModCanvas extends ContainerComponent {

    private final List<FlexComponent> children = new ArrayList<>();
    private final Map<FlexComponent, Placement> placements = new IdentityHashMap<>();

    public ModCanvas(String name, int width, int height) {
        super(name);
        setInitialSize(width, height, false);
        sizeFlags = FIXED_WIDTH | FIXED_HEIGHT;
    }

    public ModCanvas placeChild(FlexComponent child, int x, int y, int width, int height) {
        children.add(child);
        placements.put(child, new Placement(x, y, width, height));
        // Same trick as ModStackPanel — establish parent so setLocation2
        // doesn't clamp children to screen bounds.
        child.parent = this;
        layout();
        return this;
    }

    @Override
    public void performLayout() {
        for (FlexComponent c : children) {
            Placement p = placements.get(c);
            if (p == null) continue;
            c.setLocation(this.x + p.x, this.y + p.y, p.w, p.h);
        }
    }

    @Override
    public void childResized(FlexComponent c) {
        // Canvas children are explicitly sized; ignore self-resize.
    }

    @Override
    protected void renderComponent(Queue queue, float alpha) {
        for (FlexComponent c : children) {
            c.render(queue, alpha);
        }
    }

    @Override
    public void gameTick() {
        for (FlexComponent c : children) c.gameTick();
    }

    @Override
    public FlexComponent getComponentAt(int xMouse, int yMouse) {
        if (!contains(xMouse, yMouse)) return null;
        // Hit-test top-most-last (children list = paint order, so iterate reverse).
        for (int i = children.size() - 1; i >= 0; i--) {
            FlexComponent r = children.get(i).getComponentAt(xMouse, yMouse);
            if (r != null) return r;
        }
        return this;
    }

    private static final class Placement {
        final int x, y, w, h;
        Placement(int x, int y, int w, int h) { this.x = x; this.y = y; this.w = w; this.h = h; }
    }
}
