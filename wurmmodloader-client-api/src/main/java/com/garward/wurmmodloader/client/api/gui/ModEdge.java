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
 * <p>Optional <em>glow</em>: a wider, dim line drawn behind the inner stroke,
 * useful for highlighting active connectors in a skill tree. Glow geometry is
 * baked into a separate cached texture so a thousand identical glow edges
 * share a single GPU upload.
 *
 * <p>Textures are cached per geometry signature so a tree with thousands of
 * edges only pays the rasterization cost once per unique edge shape.
 *
 * <p>Coordinates are interpreted as relative to the parent {@link ModCanvas}.
 * The widget sizes itself to the line's bounding box (including any glow
 * padding) and is positioned at the box's top-left.
 */
public class ModEdge extends ModComponent {

    private static final Logger logger = Logger.getLogger(ModEdge.class.getName());
    private static final Map<Long, ImageTexture> CACHE = new HashMap<>();

    private final int dx;
    private final int dy;
    private final int thickness;
    private final int glowThickness;
    private final float r, g, b, a;
    private final float gr, gg, gb, ga;
    private final boolean startsTopLeft;

    private ImageTexture texture;
    private ImageTexture glowTexture;

    /**
     * @param x1,y1 first endpoint (canvas-local)
     * @param x2,y2 second endpoint (canvas-local)
     * @param thickness pixel thickness (≥1)
     * @param r,g,b,a color components (0..1)
     */
    public ModEdge(int x1, int y1, int x2, int y2, int thickness,
                   float r, float g, float b, float a) {
        this(x1, y1, x2, y2, thickness, r, g, b, a, 0, 0f, 0f, 0f, 0f);
    }

    /**
     * Glow-enabled edge. {@code glowThickness} is the full glow width
     * (≥ {@code thickness} or it'll be invisible behind the inner line);
     * pass 0 to disable. Glow is drawn first so the inner line crisps over it.
     */
    public ModEdge(int x1, int y1, int x2, int y2, int thickness,
                   float r, float g, float b, float a,
                   int glowThickness,
                   float gr, float gg, float gb, float ga) {
        super("edge",
                boxWidth(x1, x2, Math.max(thickness, glowThickness)),
                boxHeight(y1, y2, Math.max(thickness, glowThickness)));
        this.dx = Math.abs(x2 - x1);
        this.dy = Math.abs(y2 - y1);
        this.thickness = Math.max(1, thickness);
        this.glowThickness = Math.max(0, glowThickness);
        this.r = r; this.g = g; this.b = b; this.a = a;
        this.gr = gr; this.gg = gg; this.gb = gb; this.ga = ga;
        // The line in the texture goes either top-left → bottom-right
        // or bottom-left → top-right depending on the original sign.
        this.startsTopLeft = ((x2 >= x1) == (y2 >= y1));
    }

    /**
     * Top-left corner of this edge's bounding box, in canvas-local pixels.
     * {@code thickness} should be the larger of inner/glow thickness when a
     * glow is in play.
     */
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
    protected boolean consumesMouseInput() {
        return false;
    }

    @Override
    protected void onRender(Queue queue, float alpha) {
        int w = getComponentWidth();
        int h = getComponentHeight();
        int sx = getScreenX();
        int sy = getScreenY();
        if (glowThickness > 0 && ga > 0f) {
            ImageTexture glow = ensureGlowTexture();
            if (glow != null) {
                Renderer.texturedQuadAlphaBlend(queue, glow, gr, gg, gb, ga,
                        sx, sy, w, h, 0f, 0f, 1f, 1f);
            }
        }
        ImageTexture tex = ensureTexture();
        if (tex == null) return;
        // Inner line is baked at thickness, but the bounding box may be sized
        // for the glow — center the inner texture inside the box.
        int padOuter = Math.max(thickness, glowThickness);
        int padInner = thickness;
        int innerW = dx + Math.max(1, padInner);
        int innerH = dy + Math.max(1, padInner);
        int offX = (Math.max(1, padOuter) - Math.max(1, padInner)) / 2;
        int offY = offX;
        // Ignore parent alpha — same flicker fix as ModLabel. Some parents
        // (windows during fade, panels during hover) animate alpha every
        // frame; multiplying made edges blink in and out.
        Renderer.texturedQuadAlphaBlend(queue, tex, r, g, b, a,
                sx + offX, sy + offY, innerW, innerH, 0f, 0f, 1f, 1f);
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
        texture = bakeLine(dx, dy, thickness, startsTopLeft);
        if (texture != null) CACHE.put(key, texture);
        return texture;
    }

    private ImageTexture ensureGlowTexture() {
        if (glowTexture != null) return glowTexture;
        // Separate cache slot keyed by the same geometry but glow thickness;
        // the high bit keeps it from colliding with the inner-line cache.
        long key = (1L << 63)
                | ((long) (dx & 0xFFFF) << 32)
                | ((long) (dy & 0xFFFF) << 16)
                | ((long) (glowThickness & 0xFF) << 8)
                | (startsTopLeft ? 1L : 0L);
        ImageTexture cached = CACHE.get(key);
        if (cached != null) {
            glowTexture = cached;
            return cached;
        }
        glowTexture = bakeLine(dx, dy, glowThickness, startsTopLeft);
        if (glowTexture != null) CACHE.put(key, glowTexture);
        return glowTexture;
    }

    private static ImageTexture bakeLine(int dx, int dy, int thickness, boolean startsTopLeft) {
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
            return ImageTextureLoader.loadNowrapNearestTexture(img, false);
        } catch (Throwable t) {
            logger.warning("[ModEdge] failed to bake line texture: " + t);
            return null;
        }
    }
}
