package com.garward.wurmmodloader.client.api.gui;

import com.wurmonline.client.renderer.backend.Queue;
import com.wurmonline.client.renderer.gui.ContainerComponent;
import com.wurmonline.client.renderer.gui.FlexComponent;
import com.wurmonline.client.renderer.gui.Renderer;
import com.wurmonline.client.resources.textures.ResourceTexture;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Layout-aware vertical/horizontal stack with per-child {@link LayoutHints}.
 *
 * <p>Replaces {@code ModArrayPanel} when you need real layout control:
 * alignment per child, weight-based main-axis distribution, aspect ratios,
 * gaps, padding, and an optional Wurm panel-background fill.
 *
 * <pre>{@code
 * ModStackPanel sidebar = new ModStackPanel("Sidebar", ArrayDirection.VERTICAL)
 *     .setPadding(Insets.uniform(4))
 *     .setGap(4)
 *     .setBackgroundPainted(true);
 *
 * sidebar.addChild(zoomInButton);   // ModImageButton -> auto 1:1 aspect
 * sidebar.addChild(zoomOutButton);
 * sidebar.addChild(spacer, new LayoutHints().weight(1f));   // pushes next to bottom
 * sidebar.addChild(toggleButton);
 * }</pre>
 */
public class ModStackPanel extends ContainerComponent {

    private static final float BG_TILE = 64f;

    private final ArrayDirection direction;
    private final List<FlexComponent> children = new ArrayList<>();
    private final Map<FlexComponent, LayoutHints> hints = new IdentityHashMap<>();

    private int gap = 0;
    private Insets padding = Insets.ZERO;
    private boolean backgroundPainted;

    public ModStackPanel(String name, ArrayDirection direction) {
        super(name);
        this.direction = direction;
    }

    /** Add a child using its default hints (or {@link LayoutHints} defaults). */
    public ModStackPanel addChild(FlexComponent child) {
        LayoutHints h = (child instanceof LayoutHints.Provider)
                ? ((LayoutHints.Provider) child).getDefaultLayoutHints()
                : new LayoutHints();
        return addChild(child, h);
    }

    public ModStackPanel addChild(FlexComponent child, LayoutHints h) {
        children.add(child);
        hints.put(child, h != null ? h : new LayoutHints());
        // Establish parent so WurmComponent.setLocation2 doesn't clamp the
        // child to screen bounds (parent == null is the "I'm a top-level
        // window" signal). Without this, performLayout's setLocation snaps
        // children to screen edges and produces flickery layout artifacts.
        child.parent = this;
        layout();
        return this;
    }

    public ModStackPanel setGap(int gap) { this.gap = gap; layout(); return this; }
    public ModStackPanel setPadding(Insets p) { this.padding = p != null ? p : Insets.ZERO; layout(); return this; }
    public ModStackPanel setBackgroundPainted(boolean v) { this.backgroundPainted = v; return this; }

    @Override
    public void performLayout() {
        if (children.isEmpty()) return;

        int w = GuiAccess.getWidth(this);
        int h = GuiAccess.getHeight(this);
        boolean vertical = direction == ArrayDirection.VERTICAL;

        int innerX = this.x + padding.left;
        int innerY = this.y + padding.top;
        int innerW = Math.max(0, w - padding.left - padding.right);
        int innerH = Math.max(0, h - padding.top - padding.bottom);
        int crossAvail = vertical ? innerW : innerH;
        int mainAvail = (vertical ? innerH : innerW)
                - gap * Math.max(0, children.size() - 1);

        // Pass 1: resolve fixed main-axis sizes; track weight pool.
        int[] mainSize = new int[children.size()];
        float totalWeight = 0f;
        int fixedTotal = 0;

        for (int i = 0; i < children.size(); i++) {
            FlexComponent c = children.get(i);
            LayoutHints lh = hints.get(c);
            int margMain = vertical ? lh.margin.top + lh.margin.bottom
                    : lh.margin.left + lh.margin.right;
            int margCross = vertical ? lh.margin.left + lh.margin.right
                    : lh.margin.top + lh.margin.bottom;
            int crossInner = Math.max(0, crossAvail - margCross);

            if (lh.weight > 0f) {
                totalWeight += lh.weight;
                mainSize[i] = -1;
                continue;
            }

            int prefAxis = vertical ? lh.preferredHeight : lh.preferredWidth;
            int pref;
            if (prefAxis >= 0) {
                pref = prefAxis;
            } else if (lh.aspectRatio > 0f) {
                int cross = crossInner;
                pref = vertical
                        ? Math.round(cross / lh.aspectRatio)
                        : Math.round(cross * lh.aspectRatio);
            } else {
                pref = vertical ? GuiAccess.getHeight(c) : GuiAccess.getWidth(c);
                if (pref <= 0) pref = crossInner;
            }
            mainSize[i] = pref + margMain;
            fixedTotal += mainSize[i];
        }

        int remaining = Math.max(0, mainAvail - fixedTotal);
        for (int i = 0; i < children.size(); i++) {
            if (mainSize[i] != -1) continue;
            FlexComponent c = children.get(i);
            LayoutHints lh = hints.get(c);
            int margMain = vertical ? lh.margin.top + lh.margin.bottom
                    : lh.margin.left + lh.margin.right;
            int share = totalWeight > 0f
                    ? Math.round(remaining * (lh.weight / totalWeight))
                    : 0;
            mainSize[i] = share + margMain;
        }

        // Pass 2: place children.
        int cursor = vertical ? innerY : innerX;
        for (int i = 0; i < children.size(); i++) {
            FlexComponent c = children.get(i);
            LayoutHints lh = hints.get(c);
            int margMain = vertical ? lh.margin.top + lh.margin.bottom
                    : lh.margin.left + lh.margin.right;
            int margCross = vertical ? lh.margin.left + lh.margin.right
                    : lh.margin.top + lh.margin.bottom;
            int crossInner = Math.max(0, crossAvail - margCross);
            int childMain = Math.max(0, mainSize[i] - margMain);
            int childCross = resolveCrossSize(lh, crossInner);

            Alignment crossAlign = vertical ? lh.alignX : lh.alignY;
            int crossOff = alignOffset(crossAlign, childCross, crossInner);
            int mainStart = cursor + (vertical ? lh.margin.top : lh.margin.left);
            int crossStart = (vertical ? innerX : innerY)
                    + (vertical ? lh.margin.left : lh.margin.top)
                    + crossOff;

            int cx = vertical ? crossStart : mainStart;
            int cy = vertical ? mainStart : crossStart;
            int cw = vertical ? childCross : childMain;
            int ch = vertical ? childMain : childCross;
            c.setLocation(cx, cy, cw, ch);

            cursor += mainSize[i] + gap;
        }
    }

    private int resolveCrossSize(LayoutHints lh, int crossInner) {
        boolean vertical = direction == ArrayDirection.VERTICAL;
        Alignment crossAlign = vertical ? lh.alignX : lh.alignY;

        if (lh.aspectRatio > 0f) {
            // Cross size is bounded by crossInner; main size is derived from it.
            return crossInner;
        }
        if (crossAlign == Alignment.FILL) return crossInner;

        int prefCross = vertical ? lh.preferredWidth : lh.preferredHeight;
        if (prefCross >= 0) return Math.min(prefCross, crossInner);
        return crossInner;
    }

    private static int alignOffset(Alignment a, int childSize, int avail) {
        if (a == null) return 0;
        switch (a) {
            case END:    return Math.max(0, avail - childSize);
            case CENTER: return Math.max(0, (avail - childSize) / 2);
            case START:
            case FILL:
            default:     return 0;
        }
    }

    @Override
    public void childResized(FlexComponent c) {
        layout();
    }

    @Override
    protected void renderComponent(Queue queue, float alpha) {
        if (backgroundPainted) {
            int w = GuiAccess.getWidth(this);
            int h = GuiAccess.getHeight(this);
            ResourceTexture bg = GuiAccess.getPanelBackgroundTexture();
            if (bg != null) {
                Renderer.texturedQuadAlphaBlend(queue, bg, 1f, 1f, 1f, 1f,
                        this.x, this.y, w, h,
                        0f, 0f, w / BG_TILE, h / BG_TILE);
            }
        }
        for (FlexComponent c : children) {
            c.render(queue, alpha);
        }
    }

    @Override
    public void gameTick() {
        for (FlexComponent c : children) {
            c.gameTick();
        }
    }

    @Override
    public FlexComponent getComponentAt(int xMouse, int yMouse) {
        if (!contains(xMouse, yMouse)) return null;
        for (FlexComponent c : children) {
            FlexComponent r = c.getComponentAt(xMouse, yMouse);
            if (r != null) return r;
        }
        return this;
    }
}
