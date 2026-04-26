package com.garward.wurmmodloader.client.api.gui;

import com.wurmonline.client.renderer.backend.Queue;
import com.wurmonline.client.renderer.gui.Renderer;
import com.wurmonline.client.resources.textures.ImageTexture;
import com.wurmonline.client.resources.textures.ImageTextureLoader;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Line primitive between two canvas-local points. Bakes the line into a
 * BufferedImage texture (antialiased via Graphics2D) and renders it as a
 * single textured quad — the only primitive Wurm's GUI renderer exposes.
 *
 * <p>Textures are cached per geometry signature so a tree with thousands of
 * edges only pays the rasterization cost once per unique edge shape.
 *
 * <p>Coordinates are interpreted as relative to the parent {@link ModCanvas}.
 * The widget sizes itself to the line's bounding box and is positioned at the
 * box's top-left.
 */
public class ModEdge extends ModComponent {

    private static final Logger logger = Logger.getLogger(ModEdge.class.getName());
    private static final Map<Long, ImageTexture> CACHE = new HashMap<>();

    private final int dx;
    private final int dy;
    private final int thickness;
    private final float r, g, b, a;
    private final boolean startsTopLeft;

    private ImageTexture texture;

    /**
     * @param x1,y1 first endpoint (canvas-local)
     * @param x2,y2 second endpoint (canvas-local)
     * @param thickness pixel thickness (≥1)
     * @param r,g,b,a color components (0..1)
     */
    public ModEdge(int x1, int y1, int x2, int y2, int thickness,
                   float r, float g, float b, float a) {
        super("edge", boxWidth(x1, x2, thickness), boxHeight(y1, y2, thickness));
        this.dx = Math.abs(x2 - x1);
        this.dy = Math.abs(y2 - y1);
        this.thickness = Math.max(1, thickness);
        this.r = r; this.g = g; this.b = b; this.a = a;
        // The line in the texture goes either top-left → bottom-right
        // or bottom-left → top-right depending on the original sign.
        this.startsTopLeft = ((x2 >= x1) == (y2 >= y1));
    }

    /** Top-left corner of this edge's bounding box, in canvas-local pixels. */
    public static int boxX(int x1, int x2, int thickness) {
        return Math.min(x1, x2) - Math.max(1, thickness) / 2;
    }

    public static int boxY(int y1, int y2, int thickness) {
        return Math.min(y1, y2) - Math.max(1, thickness) / 2;
    }

    public static int boxWidth(int x1, int x2, int thickness) {
        return Math.abs(x2 - x1) + Math.max(1, thickness);
    }

    public static int boxHeight(int y1, int y2, int thickness) {
        return Math.abs(y2 - y1) + Math.max(1, thickness);
    }

    @Override
    protected void onRender(Queue queue, float alpha) {
        ImageTexture tex = ensureTexture();
        if (tex == null) return;
        int w = getComponentWidth();
        int h = getComponentHeight();
        Renderer.texturedQuadAlphaBlend(queue, tex, r, g, b, a * alpha,
                getScreenX(), getScreenY(), w, h, 0f, 0f, 1f, 1f);
    }

    private ImageTexture ensureTexture() {
        if (texture != null) return texture;
        // Cache by geometry only — color is multiplied at draw time so a single
        // baked white line serves every recolor.
        long key = ((long) (dx & 0xFFFF) << 32)
                | ((long) (dy & 0xFFFF) << 16)
                | ((long) (thickness & 0xFF) << 8)
                | (startsTopLeft ? 1L : 0L);
        ImageTexture cached = CACHE.get(key);
        if (cached != null) {
            texture = cached;
            return cached;
        }
        try {
            int padding = Math.max(1, thickness);
            int w = dx + padding;
            int h = dy + padding;
            BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2 = img.createGraphics();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setStroke(new BasicStroke(thickness, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.setColor(Color.WHITE);
                int half = padding / 2;
                if (startsTopLeft) {
                    g2.drawLine(half, half, w - 1 - half, h - 1 - half);
                } else {
                    g2.drawLine(half, h - 1 - half, w - 1 - half, half);
                }
            } finally {
                g2.dispose();
            }
            ImageTexture tex = ImageTextureLoader.loadNowrapNearestTexture(img, false);
            CACHE.put(key, tex);
            texture = tex;
            return tex;
        } catch (Throwable t) {
            logger.warning("[ModEdge] failed to bake line texture: " + t);
            return null;
        }
    }
}
