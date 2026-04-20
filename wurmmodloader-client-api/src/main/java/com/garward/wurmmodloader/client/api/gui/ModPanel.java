package com.garward.wurmmodloader.client.api.gui;

import com.wurmonline.client.renderer.backend.Queue;
import com.wurmonline.client.renderer.gui.ContainerComponent;
import com.wurmonline.client.renderer.gui.FlexComponent;
import com.wurmonline.client.renderer.gui.Renderer;
import com.wurmonline.client.resources.textures.ResourceTexture;

/**
 * Single-child container that respects the bounds assigned by its parent
 * (unlike {@link ModArrayPanel}, which shrinks to sum-of-children) and
 * optionally paints Wurm's standard window-body background behind the child.
 *
 * <p>Typical use: fill a sparsely-populated border region (e.g. a sidebar
 * with three buttons stacked in a {@code ModBorderPanel.EAST} slot) so the
 * empty space below the content renders as solid panel background rather
 * than see-through to the game world.
 *
 * <pre>{@code
 * ModArrayPanel<ModButton> buttons = new ModArrayPanel<>("Buttons", ArrayDirection.VERTICAL);
 * buttons.addComponent(new ModButton("+", "Zoom in", this::zoomIn));
 * buttons.addComponent(new ModButton("-", "Zoom out", this::zoomOut));
 *
 * ModPanel sidebar = new ModPanel("Sidebar", buttons);
 * sidebar.setBackgroundPainted(true);
 * sidebar.setInitialSize(40, 600, false);
 *
 * borderPanel.setRegion(sidebar, BorderRegion.EAST);
 * }</pre>
 *
 * <p>The child is placed at the panel's top-left and sized to the panel's
 * current width. Its preferred height is respected (so stacked buttons stay
 * at their natural size); the remaining vertical space becomes painted
 * background. This is almost always what mods want for sidebars.
 */
public class ModPanel extends ContainerComponent {

    private static final float BG_TILE = 64f;

    private FlexComponent child;
    private boolean backgroundPainted;

    public ModPanel(String name) {
        super(name);
    }

    public ModPanel(String name, FlexComponent child) {
        super(name);
        setChild(child);
    }

    public void setChild(FlexComponent newChild) {
        this.child = newChild;
        layout();
    }

    public FlexComponent getChild() {
        return child;
    }

    /** Paint Wurm's standard window-body background behind the child. */
    public void setBackgroundPainted(boolean enabled) {
        this.backgroundPainted = enabled;
    }

    @Override
    public void performLayout() {
        if (child == null) return;
        int w = GuiAccess.getWidth(this);
        int h = GuiAccess.getHeight(this);
        int childH = Math.min(GuiAccess.getHeight(child), h);
        child.setLocation(this.x, this.y, w, childH);
    }

    @Override
    public void childResized(FlexComponent c) {
        layout();
    }

    @Override
    protected void renderComponent(Queue queue, float alpha) {
        int w = GuiAccess.getWidth(this);
        int h = GuiAccess.getHeight(this);
        if (backgroundPainted) {
            ResourceTexture bg = GuiAccess.getPanelBackgroundTexture();
            if (bg != null) {
                float u = w / BG_TILE;
                float v = h / BG_TILE;
                Renderer.texturedQuadAlphaBlend(queue, bg, 1.0f, 1.0f, 1.0f, 1.0f,
                        this.x, this.y, w, h, 0f, 0f, u, v);
            }
        }
        if (child != null) {
            child.render(queue, alpha);
        }
    }

    @Override
    public void gameTick() {
        if (child != null) {
            child.gameTick();
        }
    }

    @Override
    public FlexComponent getComponentAt(int xMouse, int yMouse) {
        if (!contains(xMouse, yMouse)) return null;
        if (child != null) {
            FlexComponent res = child.getComponentAt(xMouse, yMouse);
            if (res != null) return res;
        }
        return this;
    }

}
