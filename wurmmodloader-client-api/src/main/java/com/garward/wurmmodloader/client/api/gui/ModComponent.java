package com.garward.wurmmodloader.client.api.gui;

import com.wurmonline.client.renderer.backend.Queue;
import com.wurmonline.client.renderer.gui.FlexComponent;

/**
 * Base class for custom mod-authored HUD/panel components.
 *
 * <p>Extends Wurm's {@link FlexComponent} so it plugs into the vanilla layout
 * system, but exposes a clean API surface to mods: override {@link #onRender}
 * for drawing, {@link #onLeftPressed} / {@link #onMouseWheel} / etc. for input.
 *
 * <p>Position and size are available via {@link #getScreenX()} / {@link #getScreenY()} —
 * these forward to the widened {@code x}/{@code y} fields on {@code WurmComponent}.
 * Do not read those fields directly; their layout semantics (screen-space vs.
 * parent-relative, updated mid-render, etc.) are a vanilla-internal concern.
 */
public abstract class ModComponent extends FlexComponent {

    protected ModComponent(String name) {
        super(name);
    }

    protected ModComponent(String name, int initialWidth, int initialHeight) {
        super(name);
        setInitialSize(initialWidth, initialHeight, false);
        sizeFlags = FIXED_WIDTH | FIXED_HEIGHT;
    }

    /** Subclass hook: draw the component. Default paints nothing. */
    protected void onRender(Queue queue, float alpha) {
    }

    /** Subclass hook: left mouse button pressed at screen coords. */
    protected void onLeftPressed(int xMouse, int yMouse, int clickCount) {
    }

    /** Subclass hook: left mouse button released at screen coords. */
    protected void onLeftReleased(int xMouse, int yMouse) {
    }

    /** Subclass hook: mouse dragged with a button held. */
    protected void onMouseDragged(int xMouse, int yMouse) {
    }

    /** Subclass hook: mouse wheel scrolled. {@code delta} positive = up. */
    protected void onMouseWheel(int xMouse, int yMouse, int delta) {
    }

    public int getScreenX() {
        return x;
    }

    public int getScreenY() {
        return y;
    }

    public int getComponentWidth() {
        return GuiAccess.getWidth(this);
    }

    public int getComponentHeight() {
        return GuiAccess.getHeight(this);
    }

    @Override
    protected final void renderComponent(Queue queue, float alpha) {
        onRender(queue, alpha);
    }

    @Override
    public final void leftPressed(int xMouse, int yMouse, int clickCount) {
        onLeftPressed(xMouse, yMouse, clickCount);
    }

    @Override
    public final void leftReleased(int xMouse, int yMouse) {
        onLeftReleased(xMouse, yMouse);
    }

    @Override
    public final void mouseDragged(int xMouse, int yMouse) {
        onMouseDragged(xMouse, yMouse);
    }

    @Override
    public final void mouseWheeled(int xMouse, int yMouse, int delta) {
        onMouseWheel(xMouse, yMouse, delta);
    }
}
