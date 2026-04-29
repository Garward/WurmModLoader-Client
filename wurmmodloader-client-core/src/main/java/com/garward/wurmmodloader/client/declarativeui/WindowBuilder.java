package com.garward.wurmmodloader.client.declarativeui;

import com.garward.wurmmodloader.client.api.gui.ArrayDirection;
import com.garward.wurmmodloader.client.api.gui.Insets;
import com.garward.wurmmodloader.client.api.gui.ModBlip;
import com.garward.wurmmodloader.client.api.gui.ModButton;
import com.garward.wurmmodloader.client.api.gui.ModCanvas;
import com.garward.wurmmodloader.client.api.gui.ModComponent;
import com.garward.wurmmodloader.client.api.gui.ModEdge;
import com.garward.wurmmodloader.client.api.gui.ModImage;
import com.garward.wurmmodloader.client.api.gui.ModLabel;
import com.garward.wurmmodloader.client.api.gui.ModStackPanel;
import com.garward.wurmmodloader.client.api.gui.ModViewport;
import com.wurmonline.client.renderer.gui.FlexComponent;
import com.wurmonline.client.renderer.gui.WButton;

import java.util.function.Consumer;
import java.util.logging.Logger;

/**
 * Walks a parsed {@link WidgetNode} tree and instantiates real HUD widgets.
 * Unknown widget types are skipped with a warning — a malformed server payload
 * never brings the HUD down.
 */
final class WindowBuilder {

    private static final Logger logger = Logger.getLogger(WindowBuilder.class.getName());

    private WindowBuilder() {}

    static MountedWindow build(String windowId,
                               String title,
                               int width,
                               int height,
                               WidgetNode root,
                               Consumer<String> actionSender) {
        MountedWindow mw = new MountedWindow(windowId, null);
        FlexComponent content = buildNode(root, mw, actionSender);
        if (content == null) {
            content = new ModStackPanel("empty", ArrayDirection.VERTICAL);
        }
        mw.window = new DeclarativeWindow(title, content, width, height);
        return mw;
    }

    private static FlexComponent buildNode(WidgetNode node,
                                           MountedWindow mw,
                                           Consumer<String> actionSender) {
        FlexComponent c = buildNodeInner(node, mw, actionSender);
        if (c != null && node != null) applyTooltip(node, c);
        return c;
    }

    // Tooltip prop wins over the legacy `hover` prop on Buttons. ModComponent
    // routes through our setHoverText override; vanilla WButton subclasses use
    // the engine's own setHoverString slot.
    private static void applyTooltip(WidgetNode node, FlexComponent c) {
        String tip = node.props.get("tooltip");
        if (tip == null || tip.isEmpty()) return;
        if (c instanceof ModComponent) {
            ((ModComponent) c).setHoverText(tip);
        } else if (c instanceof WButton) {
            ((WButton) c).setHoverString(tip);
        }
    }

    private static FlexComponent buildNodeInner(WidgetNode node,
                                                MountedWindow mw,
                                                Consumer<String> actionSender) {
        if (node == null) return null;
        switch (node.type) {
            case UiProtocol.W_STACK: {
                ArrayDirection dir = "horizontal".equalsIgnoreCase(node.prop("direction", "vertical"))
                        ? ArrayDirection.HORIZONTAL
                        : ArrayDirection.VERTICAL;
                ModStackPanel panel = new ModStackPanel("stack", dir)
                        .setGap(node.propInt("gap", 4))
                        .setPadding(Insets.uniform(node.propInt("padding", 4)));
                for (WidgetNode child : node.children) {
                    FlexComponent c = buildNode(child, mw, actionSender);
                    if (c != null) panel.addChild(c);
                }
                return panel;
            }
            case UiProtocol.W_LABEL: {
                ModLabel label = new ModLabel(node.prop("text", ""));
                String bind = node.props.get("bind");
                if (bind != null && !bind.isEmpty()) {
                    mw.bindings.put(bind, label);
                }
                return label;
            }
            case UiProtocol.W_BUTTON: {
                final String action = node.prop("action", "");
                String hover = node.props.get("hover");
                return new ModButton(
                        node.prop("label", "Button"),
                        (hover == null || hover.isEmpty()) ? null : hover,
                        () -> actionSender.accept(action));
            }
            case UiProtocol.W_SPACER: {
                return new Spacer(node.propInt("width", 8), node.propInt("height", 8));
            }
            case UiProtocol.W_CANVAS: {
                int cw = node.propInt("width", 256);
                int ch = node.propInt("height", 256);
                ModCanvas canvas = new ModCanvas("canvas", cw, ch);
                for (WidgetNode child : node.children) {
                    FlexComponent c = buildNode(child, mw, actionSender);
                    if (c == null) continue;
                    int[] xywh = resolvePlacement(child, c);
                    canvas.placeChild(c, xywh[0], xywh[1], xywh[2], xywh[3]);
                }
                return canvas;
            }
            case UiProtocol.W_VIEWPORT: {
                int cw = node.propInt("width", 256);
                int ch = node.propInt("height", 256);
                ModViewport viewport = new ModViewport("viewport", cw, ch);

                float zMin  = (float) node.propDouble("zoomMin",  0.25);
                float zMax  = (float) node.propDouble("zoomMax",  4.0);
                float zStep = (float) node.propDouble("zoomStep", 1.1);
                viewport.setZoomBounds(zMin, zMax, zStep);
                String anchor = node.prop("zoomAnchor", "center");
                viewport.setZoomAnchor("cursor".equalsIgnoreCase(anchor)
                        ? ModViewport.ZoomAnchor.CURSOR
                        : ModViewport.ZoomAnchor.CENTER);

                float initScale = (float) node.propDouble("initScale", 1.0);
                int initPanX = node.propInt("initPanX", 0);
                int initPanY = node.propInt("initPanY", 0);
                viewport.setInitialView(initScale, initPanX, initPanY);

                String bg = node.prop("bg", "");
                if (!bg.isEmpty()) {
                    float[] bgTint = parseColor(node.prop("bgTint", "1,1,1,1"));
                    // 1×1 — ModViewport.performLayout stretches to viewport bounds each pass.
                    viewport.setBackground(new ModImage(bg, WindowBuilder.class, 1, 1,
                            bgTint[0], bgTint[1], bgTint[2], bgTint[3]));
                }
                for (WidgetNode child : node.children) {
                    FlexComponent c = buildNode(child, mw, actionSender);
                    if (c == null) continue;
                    int[] xywh = resolvePlacement(child, c);
                    viewport.placeChild(c, xywh[0], xywh[1], xywh[2], xywh[3]);
                }
                return viewport;
            }
            case UiProtocol.W_IMAGE: {
                int iw = node.propInt("width", 256);
                int ih = node.propInt("height", 256);
                float[] tint = parseColor(node.prop("tint", "1,1,1,1"));
                return new ModImage(node.prop("src", ""), WindowBuilder.class, iw, ih,
                        tint[0], tint[1], tint[2], tint[3]);
            }
            case UiProtocol.W_EDGE: {
                int x1 = node.propInt("x1", 0);
                int y1 = node.propInt("y1", 0);
                int x2 = node.propInt("x2", 0);
                int y2 = node.propInt("y2", 0);
                int thickness = node.propInt("thickness", 2);
                float[] rgba = parseColor(node.prop("color", "1,1,1,1"));
                return new ModEdge(x1, y1, x2, y2, thickness, rgba[0], rgba[1], rgba[2], rgba[3]);
            }
            case UiProtocol.W_BLIP: {
                int diameter = node.propInt("diameter", 12);
                float[] fill = parseColor(node.prop("fill", "1,1,1,1"));
                int outlineThickness = node.propInt("outlineThickness", 0);
                if (outlineThickness > 0) {
                    float[] outline = parseColor(node.prop("outline", "0,0,0,1"));
                    return new ModBlip(diameter,
                            fill[0], fill[1], fill[2], fill[3],
                            outlineThickness,
                            outline[0], outline[1], outline[2], outline[3]);
                }
                return new ModBlip(diameter, fill[0], fill[1], fill[2], fill[3]);
            }
            default:
                logger.warning("[DeclarativeUI] unknown widget type: " + node.type);
                return null;
        }
    }

    /**
     * Resolve a child's (x, y, width, height) for an absolute-positioning
     * container (Canvas / Viewport). Edge widgets size themselves to their
     * bounding box; blips center on (x, y) and size to their diameter; other
     * widgets fall back to a sensible default so layout doesn't render at 0×0.
     */
    private static int[] resolvePlacement(WidgetNode child, FlexComponent c) {
        int cx = child.propInt("x", 0);
        int cy = child.propInt("y", 0);
        int cwc = child.propInt("width", -1);
        int chc = child.propInt("height", -1);
        if (cwc >= 0 && chc >= 0) return new int[] { cx, cy, cwc, chc };
        if (c instanceof ModEdge) {
            int x1 = child.propInt("x1", 0);
            int y1 = child.propInt("y1", 0);
            int x2 = child.propInt("x2", 0);
            int y2 = child.propInt("y2", 0);
            int t  = child.propInt("thickness", 2);
            cx = ModEdge.boxX(x1, x2, t);
            cy = ModEdge.boxY(y1, y2, t);
            if (cwc < 0) cwc = ModEdge.boxWidth(x1, x2, t);
            if (chc < 0) chc = ModEdge.boxHeight(y1, y2, t);
        } else if (c instanceof ModBlip) {
            int d = child.propInt("diameter", 12);
            cx -= d / 2;
            cy -= d / 2;
            if (cwc < 0) cwc = d;
            if (chc < 0) chc = d;
        } else {
            if (cwc < 0) cwc = 100;
            if (chc < 0) chc = 20;
        }
        return new int[] { cx, cy, cwc, chc };
    }

    private static final class Spacer extends ModComponent {
        Spacer(int w, int h) {
            super("spacer", w, h);
        }
    }

    /** Parse "r,g,b,a" floats; missing components default to 1.0. */
    private static float[] parseColor(String s) {
        float[] out = { 1f, 1f, 1f, 1f };
        if (s == null || s.isEmpty()) return out;
        String[] parts = s.split(",");
        for (int i = 0; i < parts.length && i < 4; i++) {
            try {
                out[i] = Float.parseFloat(parts[i].trim());
            } catch (NumberFormatException ignored) {
            }
        }
        return out;
    }
}
