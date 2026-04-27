package com.garward.wurmmodloader.client.api.gui;

import com.wurmonline.client.renderer.PickData;
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

    private String hoverText;

    protected ModComponent(String name) {
        super(name);
    }

    /**
     * Set the tooltip shown when the mouse hovers over this widget.
     * Pass {@code null} to clear. Multi-line tooltips: separate with {@code \n}.
     * Wurm's HUD picks up {@link PickData#addText} during the per-frame
     * mouse-pick pass and renders the popup itself.
     */
    public ModComponent setHoverText(String text) {
        this.hoverText = text;
        return this;
    }

    public String getHoverText() {
        return hoverText;
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

    /**
     * Whether this widget swallows mouse clicks/drags or lets them bubble to
     * the parent. Default: {@code true} (consume — backwards-compatible with
     * leaf widgets like {@code LiveMapView} that want exclusive input).
     *
     * <p>Decorative widgets that fill space without needing input — image,
     * edge, blip, label — override this to {@code false} so a containing
     * {@link ModViewport} (or any other parent) sees the click. Tooltips on
     * those widgets are unaffected; they ride the per-frame {@code pick} pass,
     * not click dispatch.
     */
    protected boolean consumesMouseInput() {
        return true;
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
    public void leftPressed(int xMouse, int yMouse, int clickCount) {
        if (consumesMouseInput()) {
            onLeftPressed(xMouse, yMouse, clickCount);
        } else if (parent != null) {
            parent.leftPressed(xMouse, yMouse, clickCount);
        }
    }

    @Override
    public void leftReleased(int xMouse, int yMouse) {
        if (consumesMouseInput()) {
            onLeftReleased(xMouse, yMouse);
        } else if (parent != null) {
            parent.leftReleased(xMouse, yMouse);
        }
    }

    @Override
    public void mouseDragged(int xMouse, int yMouse) {
        if (consumesMouseInput()) {
            onMouseDragged(xMouse, yMouse);
        } else if (parent != null) {
            parent.mouseDragged(xMouse, yMouse);
        }
    }

    @Override
    public void mouseWheeled(int xMouse, int yMouse, int delta) {
        if (consumesMouseInput()) {
            onMouseWheel(xMouse, yMouse, delta);
        } else if (parent != null) {
            parent.mouseWheeled(xMouse, yMouse, delta);
        }
    }

    @Override
    public void pick(PickData pickData, int xMouse, int yMouse) {
        // Vanilla WButton pattern: on the per-frame pick pass, hand any hover
        // string to the HUD so it can draw the tooltip popup itself.
        if (hoverText != null && !hoverText.isEmpty()) {
            for (String line : hoverText.split("\n")) {
                pickData.addText(line);
            }
        }
    }
}
