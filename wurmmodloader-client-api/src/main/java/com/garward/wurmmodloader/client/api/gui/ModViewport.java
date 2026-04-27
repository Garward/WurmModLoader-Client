package com.garward.wurmmodloader.client.api.gui;

import com.wurmonline.client.renderer.backend.Queue;
import com.wurmonline.client.renderer.gui.ContainerComponent;
import com.wurmonline.client.renderer.gui.FlexComponent;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Pan-on-drag, zoom-on-wheel absolute-positioning container — like
 * {@link ModCanvas} but with a movable, scalable viewport. Children are placed
 * at world-space (x, y, w, h); a mouse drag on empty viewport space scrolls
 * the world coordinate system underneath, and the wheel zooms anchored on the
 * cursor (the world point under the cursor stays under the cursor).
 *
 * <p>Modeled on livemap's drag pattern: anchor mouse + world position on
 * {@code leftPressed}, accumulate delta into {@code panX/panY} on
 * {@code mouseDragged}. On each layout pass, children's screen positions are
 * recomputed as {@code (worldX * scale - panX)} so vanilla hit-testing keeps
 * working without any inverse-transform plumbing.
 *
 * <p>Hit-testing semantics: clicks on a child are forwarded to that child;
 * clicks on empty viewport area initiate panning. Decorative widgets
 * ({@code ModImage}, {@code ModEdge}, {@code ModBlip}, {@code ModLabel}) opt
 * out of click consumption via {@link ModComponent#consumesMouseInput()}, so
 * even a viewport whose entire area is covered by a background image stays
 * draggable on the empty spaces between buttons.
 */
public class ModViewport extends ContainerComponent {

    /** Anchor mode for wheel zoom. */
    public enum ZoomAnchor {
        /** Keep the world point under the viewport's centre fixed in screen space. */
        CENTER,
        /** Keep the world point under the cursor fixed in screen space. */
        CURSOR
    }

    private final List<FlexComponent> children = new ArrayList<>();
    private final Map<FlexComponent, Placement> placements = new IdentityHashMap<>();

    // Optional fixed background — drawn first, stretched to viewport bounds,
    // ignores pan/zoom and never participates in hit-testing. Lets a viewport
    // present a "wallpaper" that's always visible regardless of where the
    // user has panned the world.
    private FlexComponent background;

    // panX/panY are screen-space offsets subtracted from each child's
    // world*scale position. Drag right → panX decreases (content follows).
    private int panX = 0;
    private int panY = 0;
    private float scale = 1.0f;

    private float minScale = 0.25f;
    private float maxScale = 4.0f;
    private float zoomStep = 1.1f;
    private ZoomAnchor zoomAnchor = ZoomAnchor.CENTER;

    private boolean dragging = false;
    private int dragAnchorMouseX = 0;
    private int dragAnchorMouseY = 0;
    private int dragAnchorPanX = 0;
    private int dragAnchorPanY = 0;

    public ModViewport(String name, int width, int height) {
        super(name);
        setInitialSize(width, height, false);
        sizeFlags = FIXED_WIDTH | FIXED_HEIGHT;
    }

    public ModViewport placeChild(FlexComponent child, int worldX, int worldY, int width, int height) {
        children.add(child);
        placements.put(child, new Placement(worldX, worldY, width, height));
        child.parent = this;
        layout();
        return this;
    }

    /**
     * Set a fixed background that fills the viewport regardless of pan/zoom.
     * Replaces any previously-set background. Pass {@code null} to clear.
     */
    public ModViewport setBackground(FlexComponent bg) {
        this.background = bg;
        if (bg != null) bg.parent = this;
        layout();
        return this;
    }

    /** Wheel-zoom bounds + step (multiplier per wheel notch, e.g. 1.1 = 10% per tick). */
    public ModViewport setZoomBounds(float minScale, float maxScale, float zoomStep) {
        this.minScale = Math.max(0.001f, minScale);
        this.maxScale = Math.max(this.minScale, maxScale);
        this.zoomStep = Math.max(1.0001f, zoomStep);
        this.scale = Math.max(this.minScale, Math.min(this.maxScale, this.scale));
        layout();
        return this;
    }

    public ModViewport setZoomAnchor(ZoomAnchor anchor) {
        if (anchor != null) this.zoomAnchor = anchor;
        return this;
    }

    /** Initial scale + screen-space pan offsets. Call before the window first lays out. */
    public ModViewport setInitialView(float initScale, int initPanX, int initPanY) {
        this.scale = Math.max(minScale, Math.min(maxScale, initScale));
        this.panX = initPanX;
        this.panY = initPanY;
        layout();
        return this;
    }

    @Override
    public void performLayout() {
        if (background != null) {
            int saved = background.sizeFlags;
            background.sizeFlags = 0;
            background.setLocation(this.x, this.y,
                    GuiAccess.getWidth(this), GuiAccess.getHeight(this));
            background.sizeFlags = saved;
        }
        for (FlexComponent c : children) {
            Placement p = placements.get(c);
            if (p == null) continue;
            int sx = this.x + Math.round(p.x * scale) - panX;
            int sy = this.y + Math.round(p.y * scale) - panY;
            int sw = Math.max(1, Math.round(p.w * scale));
            int sh = Math.max(1, Math.round(p.h * scale));
            // ModComponent's constructor sets FIXED_WIDTH|FIXED_HEIGHT so its
            // width/height survive a parent's layout pass. setLocation honours
            // those flags by reverting any new dimensions, which made zoom a
            // no-op for size — children moved to scaled positions but kept
            // their unscaled bounds. Clear the flags around setLocation so the
            // resize lands, then restore so the next render-time layout from
            // an unrelated parent can't squash them. ModLabel keeps native
            // size visually because TextFont rasterizes glyphs at fixed pixel
            // size; only its hit-box scales, which is harmless (labels don't
            // consume mouse input).
            int saved = c.sizeFlags;
            c.sizeFlags = 0;
            c.setLocation(sx, sy, sw, sh);
            c.sizeFlags = saved;
        }
    }

    @Override
    public void childResized(FlexComponent c) {
        // Viewport children are explicitly sized; ignore self-resize.
    }

    @Override
    protected void renderComponent(Queue queue, float alpha) {
        if (background != null) background.render(queue, alpha);
        // Cheap viewport-bounds culling: skip children fully outside the visible
        // window. Avoids drawing offscreen blips/edges/labels at the cost of one
        // intersection test per child. (Not a substitute for glScissor: any
        // child that straddles the edge will still draw across the boundary.)
        int vx0 = this.x;
        int vy0 = this.y;
        int vx1 = vx0 + GuiAccess.getWidth(this);
        int vy1 = vy0 + GuiAccess.getHeight(this);
        for (FlexComponent c : children) {
            int cx0 = GuiAccess.getX(c);
            int cy0 = GuiAccess.getY(c);
            int cx1 = cx0 + GuiAccess.getWidth(c);
            int cy1 = cy0 + GuiAccess.getHeight(c);
            if (cx1 < vx0 || cx0 > vx1 || cy1 < vy0 || cy0 > vy1) continue;
            c.render(queue, alpha);
        }
    }

    @Override
    public void gameTick() {
        if (background != null) background.gameTick();
        for (FlexComponent c : children) c.gameTick();
    }

    @Override
    public FlexComponent getComponentAt(int xMouse, int yMouse) {
        if (!contains(xMouse, yMouse)) return null;
        for (int i = children.size() - 1; i >= 0; i--) {
            FlexComponent r = children.get(i).getComponentAt(xMouse, yMouse);
            if (r != null) return r;
        }
        return this;
    }

    @Override
    public void leftPressed(int xMouse, int yMouse, int clickCount) {
        dragging = true;
        dragAnchorMouseX = xMouse;
        dragAnchorMouseY = yMouse;
        dragAnchorPanX = panX;
        dragAnchorPanY = panY;
    }

    @Override
    public void leftReleased(int xMouse, int yMouse) {
        dragging = false;
    }

    @Override
    public void mouseDragged(int xMouse, int yMouse) {
        if (!dragging) return;
        // Mouse drag right → world pans left (content follows the cursor).
        panX = dragAnchorPanX - (xMouse - dragAnchorMouseX);
        panY = dragAnchorPanY - (yMouse - dragAnchorMouseY);
        layout();
    }

    @Override
    public void mouseWheeled(int xMouse, int yMouse, int wheelDelta) {
        if (wheelDelta == 0) return;
        float oldScale = scale;
        // Positive wheelDelta = scroll DOWN in this dispatch path (verified
        // empirically — opposite of LiveMinimap's documented convention).
        // Down = zoom out, up = zoom in.
        float factor = (wheelDelta > 0) ? (1f / zoomStep) : zoomStep;
        float newScale = Math.max(minScale, Math.min(maxScale, oldScale * factor));
        if (newScale == oldScale) return;

        // Anchor the zoom on either the viewport centre (default — symmetric
        // expand/contract, doesn't feel like a pan) or the cursor (familiar
        // map-zoom UX where the world point under the pointer stays fixed).
        //   screenX = this.x + worldX * scale - panX
        //   ⇒ worldX = (screenX - this.x + panX) / scale
        // We want the same worldX to project to the same anchor after the
        // scale change, so panX' = worldX * newScale - (anchorX - this.x).
        float anchorX, anchorY;
        if (zoomAnchor == ZoomAnchor.CURSOR) {
            anchorX = xMouse;
            anchorY = yMouse;
        } else {
            anchorX = this.x + GuiAccess.getWidth(this) / 2f;
            anchorY = this.y + GuiAccess.getHeight(this) / 2f;
        }
        float worldUx = (anchorX - this.x + panX) / oldScale;
        float worldUy = (anchorY - this.y + panY) / oldScale;
        scale = newScale;
        panX = Math.round(worldUx * newScale - (anchorX - this.x));
        panY = Math.round(worldUy * newScale - (anchorY - this.y));
        layout();
    }

    private static final class Placement {
        final int x, y, w, h;
        Placement(int x, int y, int w, int h) { this.x = x; this.y = y; this.w = w; this.h = h; }
    }
}
