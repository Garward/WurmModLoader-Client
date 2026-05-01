package com.garward.wurmmodloader.client.api.gui;

import com.wurmonline.client.renderer.backend.Queue;
import com.wurmonline.client.renderer.gui.Renderer;
import com.wurmonline.client.resources.textures.ImageTexture;
import com.wurmonline.client.resources.textures.ImageTextureLoader;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Soft radial-gradient circle — a "glow" that sits behind a node to suggest
 * status (selected, available, on cooldown). The gradient is opaque white at
 * the center fading to transparent at the rim, baked once per diameter and
 * cached; the runtime tint is applied at draw time, so dozens of halos with
 * different colors share a single GPU upload per size.
 *
 * <p>Always input-transparent — drags pass through to a containing
 * {@link ModViewport} and clicks reach a sibling {@link ModBlip} or
 * {@link ModFrame} layered on top.
 */
public class ModHalo extends ModComponent {

    private static final Logger logger = Logger.getLogger(ModHalo.class.getName());
    private static final Map<Integer, ImageTexture> CACHE = new HashMap<>();

    private final int diameter;
    private final float r, g, b, a;

    private ImageTexture texture;

    /**
     * @param diameter pixel size (≥4)
     * @param r,g,b,a center color components (0..1); rim fades to alpha 0
     */
    public ModHalo(int diameter, float r, float g, float b, float a) {
        super("halo", Math.max(4, diameter), Math.max(4, diameter));
        this.diameter = Math.max(4, diameter);
        this.r = r; this.g = g; this.b = b; this.a = a;
    }

    @Override
    protected boolean consumesMouseInput() {
        return false;
    }

    @Override
    protected void onRender(Queue queue, float alpha) {
        ImageTexture tex = ensureTexture();
        if (tex == null) return;
        Renderer.texturedQuadAlphaBlend(queue, tex, r, g, b, a,
                getScreenX(), getScreenY(),
                getComponentWidth(), getComponentHeight(),
                0f, 0f, 1f, 1f);
    }

    private ImageTexture ensureTexture() {
        if (texture != null) return texture;
        // Cache by diameter only — color is multiplied at draw time so a single
        // baked white halo serves every recolor.
        ImageTexture cached = CACHE.get(diameter);
        if (cached != null) {
            texture = cached;
            return cached;
        }
        try {
            BufferedImage img = new BufferedImage(diameter, diameter, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2 = img.createGraphics();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);
                Point2D center = new Point2D.Float(diameter / 2f, diameter / 2f);
                float radius = diameter / 2f;
                // Two-stop ease — bright core, long fade. The 0.4 stop keeps
                // the center readable when stacked under a small icon.
                float[] stops = { 0f, 0.4f, 1f };
                Color[] colors = {
                        new Color(1f, 1f, 1f, 1f),
                        new Color(1f, 1f, 1f, 0.55f),
                        new Color(1f, 1f, 1f, 0f),
                };
                RadialGradientPaint paint = new RadialGradientPaint(center, radius, stops, colors);
                g2.setPaint(paint);
                g2.fillOval(0, 0, diameter, diameter);
            } finally {
                g2.dispose();
            }
            ImageTexture tex = ImageTextureLoader.loadNowrapNearestTexture(img, false);
            CACHE.put(diameter, tex);
            texture = tex;
            return tex;
        } catch (Throwable t) {
            logger.warning("[ModHalo] failed to bake halo texture: " + t);
            return null;
        }
    }
}
