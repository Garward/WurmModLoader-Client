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
 * Circular marker — small textured "blip" for skill-tree nodes, map pins,
 * status dots, etc. Bakes a single antialiased filled circle (with optional
 * outline) into a BufferedImage texture, cached by geometry signature so
 * thousands of blips share a handful of GPU uploads.
 *
 * <p>Color is multiplied at draw time so one baked white blip recolors
 * cheaply. Combine with {@link ModComponent#setHoverText(String)} for a
 * tooltip on hover (the same {@code pick}/{@code addText} mechanism vanilla
 * buttons use).
 */
public class ModBlip extends ModComponent {

    private static final Logger logger = Logger.getLogger(ModBlip.class.getName());
    private static final Map<Long, ImageTexture> CACHE = new HashMap<>();

    private final int diameter;
    private final int outlineThickness;
    private final float r, g, b, a;
    private final float or, og, ob, oa;

    private ImageTexture texture;

    /** Solid filled blip, no outline. */
    public ModBlip(int diameter, float r, float g, float b, float a) {
        this(diameter, r, g, b, a, 0, 0f, 0f, 0f, 0f);
    }

    /**
     * @param diameter pixel size (≥2)
     * @param outlineThickness 0 to disable
     */
    public ModBlip(int diameter,
                   float r, float g, float b, float a,
                   int outlineThickness,
                   float or, float og, float ob, float oa) {
        super("blip", Math.max(2, diameter), Math.max(2, diameter));
        this.diameter = Math.max(2, diameter);
        this.outlineThickness = Math.max(0, outlineThickness);
        this.r = r; this.g = g; this.b = b; this.a = a;
        this.or = or; this.og = og; this.ob = ob; this.oa = oa;
    }

    @Override
    protected boolean consumesMouseInput() {
        return false;
    }

    @Override
    protected void onRender(Queue queue, float alpha) {
        ImageTexture tex = ensureTexture();
        if (tex == null) return;
        int w = getComponentWidth();
        int h = getComponentHeight();
        // Hardcoded full alpha: same flicker fix as ModLabel/ModEdge.
        Renderer.texturedQuadAlphaBlend(queue, tex, r, g, b, a,
                getScreenX(), getScreenY(), w, h, 0f, 0f, 1f, 1f);
    }

    private ImageTexture ensureTexture() {
        if (texture != null) return texture;
        long key = ((long) (diameter & 0xFFFF) << 16)
                | (long) (outlineThickness & 0xFFFF);
        // Outline color baked into texture (it's not multiplied at draw time
        // because the fill *is*). Fold its bits into the cache key so two
        // blips with same geometry but different outline colors don't share.
        key ^= ((long) Float.floatToIntBits(or + og * 7f + ob * 13f + oa * 19f)) << 32;
        ImageTexture cached = CACHE.get(key);
        if (cached != null) {
            texture = cached;
            return cached;
        }
        try {
            int size = diameter;
            BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2 = img.createGraphics();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);
                int inset = outlineThickness;
                int fillSize = size - inset * 2;
                if (fillSize > 0) {
                    g2.setColor(Color.WHITE);
                    g2.fillOval(inset, inset, fillSize, fillSize);
                }
                if (outlineThickness > 0 && oa > 0f) {
                    g2.setStroke(new BasicStroke(outlineThickness));
                    g2.setColor(new Color(
                            clamp01(or), clamp01(og), clamp01(ob), clamp01(oa)));
                    int half = outlineThickness / 2;
                    g2.drawOval(half, half, size - 1 - outlineThickness + 1, size - 1 - outlineThickness + 1);
                }
            } finally {
                g2.dispose();
            }
            ImageTexture tex = ImageTextureLoader.loadNowrapNearestTexture(img, false);
            CACHE.put(key, tex);
            texture = tex;
            return tex;
        } catch (Throwable t) {
            logger.warning("[ModBlip] failed to bake circle: " + t);
            return null;
        }
    }

    private static float clamp01(float v) {
        return v < 0f ? 0f : (v > 1f ? 1f : v);
    }
}
