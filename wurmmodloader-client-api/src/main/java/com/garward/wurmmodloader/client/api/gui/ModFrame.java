package com.garward.wurmmodloader.client.api.gui;

import com.wurmonline.client.renderer.PickData;
import com.wurmonline.client.renderer.backend.Queue;
import com.wurmonline.client.renderer.gui.ContainerComponent;
import com.wurmonline.client.renderer.gui.FlexComponent;
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
 * Single-child container with a {@code square} or {@code circle} stroked
 * border. Intended for skill-tree-style framed icons, status panel borders,
 * decorative rings — anywhere you'd reach for a Wurm {@code WButton} frame
 * but want a circular alternative or per-instance border colors.
 *
 * <p>The frame texture is baked once per geometry/colour signature and cached,
 * so a tree of hundreds of identically-sized frames pays a single GPU upload.
 * Both fill and border tints are multiplied at draw time, so changing colors
 * at runtime is free.
 *
 * <p>Children are placed in the inner inset area. Frames hold at most one
 * child; nest frames or wrap a {@link ModStackPanel} inside if you need more.
 *
 * <p>Set {@link #onClick(Runnable)} to make the frame a click target
 * (consumes mouse input). Otherwise the frame is input-transparent so a
 * containing {@link ModViewport} keeps drag-to-pan.
 */
public class ModFrame extends ContainerComponent {

    public enum Shape { SQUARE, CIRCLE }

    private static final Logger logger = Logger.getLogger(ModFrame.class.getName());
    private static final Map<Long, ImageTexture> CACHE = new HashMap<>();

    private final Shape shape;
    private final int frameWidth;
    private final int frameHeight;
    private final int borderThickness;
    private final float fr, fg, fb, fa;        // border color
    private final float bgR, bgG, bgB, bgA;    // fill color (under child)
    private final int innerInset;

    private FlexComponent child;
    private ImageTexture texture;
    private Runnable onClick;
    private boolean hovered;
    private boolean pressed;
    private String hoverText;

    public ModFrame(Shape shape, int width, int height,
                    int borderThickness,
                    float bgR, float bgG, float bgB, float bgA,
                    float fr, float fg, float fb, float fa) {
        super("frame");
        this.shape = shape != null ? shape : Shape.SQUARE;
        this.frameWidth = Math.max(4, width);
        this.frameHeight = Math.max(4, height);
        this.borderThickness = Math.max(1, borderThickness);
        this.bgR = bgR; this.bgG = bgG; this.bgB = bgB; this.bgA = bgA;
        this.fr = fr; this.fg = fg; this.fb = fb; this.fa = fa;
        this.innerInset = this.borderThickness + 1;
        setInitialSize(this.frameWidth, this.frameHeight, false);
        sizeFlags = FIXED_WIDTH | FIXED_HEIGHT;
    }

    /**
     * Set this frame's single child. The child is laid out filling the inner
     * area (frame size minus border thickness on each side). Replaces any
     * previously-set child.
     */
    public ModFrame setChild(FlexComponent c) {
        this.child = c;
        if (c != null) c.parent = this;
        layout();
        return this;
    }

    /**
     * Make the frame clickable. With a handler set, the frame consumes mouse
     * clicks (so it can be tied to {@code ui:action} dispatch); without one,
     * the frame is transparent to mouse input and drags pass through to a
     * containing viewport.
     */
    public ModFrame onClick(Runnable handler) {
        this.onClick = handler;
        return this;
    }

    public ModFrame setHoverText(String text) {
        this.hoverText = text;
        return this;
    }

    @Override
    public void performLayout() {
        if (child == null) return;
        int innerX = this.x + innerInset;
        int innerY = this.y + innerInset;
        int innerW = Math.max(0, frameWidth - innerInset * 2);
        int innerH = Math.max(0, frameHeight - innerInset * 2);
        child.setLocation(innerX, innerY, innerW, innerH);
    }

    @Override
    public void childResized(FlexComponent c) {
        // Frame is fixed-size; children fit the inner inset area.
    }

    @Override
    protected void renderComponent(Queue queue, float alpha) {
        ImageTexture tex = ensureTexture();
        if (tex != null) {
            float tint = (pressed && onClick != null) ? 0.7f : 1.0f;
            // Single textured quad — fill is baked white and tinted at draw
            // time; border is baked into the same texture so we render the
            // whole frame (fill + border) in one call.
            Renderer.texturedQuadAlphaBlend(queue, tex,
                    tint, tint, tint, 1.0f,
                    this.x, this.y,
                    GuiAccess.getWidth(this), GuiAccess.getHeight(this),
                    0f, 0f, 1f, 1f);
        }
        if (child != null) child.render(queue, alpha);
    }

    @Override
    public FlexComponent getComponentAt(int xMouse, int yMouse) {
        if (!contains(xMouse, yMouse)) return null;
        if (child != null) {
            FlexComponent r = child.getComponentAt(xMouse, yMouse);
            if (r != null && r != child) return r;
            if (r == child && onClick == null) return r;
        }
        return this;
    }

    @Override
    public void gameTick() {
        if (child != null) child.gameTick();
    }

    @Override
    public void leftPressed(int xMouse, int yMouse, int clickCount) {
        if (onClick == null) {
            if (parent != null) parent.leftPressed(xMouse, yMouse, clickCount);
            return;
        }
        pressed = true;
    }

    @Override
    public void leftReleased(int xMouse, int yMouse) {
        if (onClick == null) {
            if (parent != null) parent.leftReleased(xMouse, yMouse);
            return;
        }
        boolean wasPressed = pressed;
        pressed = false;
        if (wasPressed && contains(xMouse, yMouse)) {
            try {
                onClick.run();
            } catch (Throwable t) {
                logger.warning("[ModFrame] onClick handler threw: " + t);
            }
        }
    }

    @Override
    public void pick(PickData pickData, int xMouse, int yMouse) {
        if (hoverText != null && !hoverText.isEmpty()) {
            for (String line : hoverText.split("\n")) pickData.addText(line);
        }
        super.pick(pickData, xMouse, yMouse);
    }

    private ImageTexture ensureTexture() {
        if (texture != null) return texture;
        // Bake fill + border into one texture, white. Cache by geometry +
        // border-color (border is baked, not tinted at draw time, so changing
        // border color requires a fresh bake — but fill is tintable).
        long key = ((long) (shape == Shape.CIRCLE ? 1 : 0) << 56)
                | ((long) (frameWidth & 0xFFF) << 44)
                | ((long) (frameHeight & 0xFFF) << 32)
                | ((long) (borderThickness & 0xFF) << 24)
                | ((long) packColor8(fr, fg, fb, fa));
        ImageTexture cached = CACHE.get(key);
        if (cached != null) {
            texture = cached;
            return cached;
        }
        try {
            BufferedImage img = new BufferedImage(frameWidth, frameHeight,
                    BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2 = img.createGraphics();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);
                int half = borderThickness / 2;
                int innerLeft = borderThickness;
                int innerTop = borderThickness;
                int innerW = frameWidth - borderThickness * 2;
                int innerH = frameHeight - borderThickness * 2;
                if (bgA > 0f) {
                    g2.setColor(new Color(clamp01(bgR), clamp01(bgG),
                            clamp01(bgB), clamp01(bgA)));
                    if (shape == Shape.CIRCLE) {
                        g2.fillOval(innerLeft, innerTop,
                                Math.max(1, innerW), Math.max(1, innerH));
                    } else {
                        g2.fillRect(innerLeft, innerTop,
                                Math.max(1, innerW), Math.max(1, innerH));
                    }
                }
                if (fa > 0f) {
                    g2.setStroke(new BasicStroke(borderThickness));
                    g2.setColor(new Color(clamp01(fr), clamp01(fg),
                            clamp01(fb), clamp01(fa)));
                    int strokeW = frameWidth - borderThickness;
                    int strokeH = frameHeight - borderThickness;
                    if (shape == Shape.CIRCLE) {
                        g2.drawOval(half, half, strokeW, strokeH);
                    } else {
                        g2.drawRect(half, half, strokeW, strokeH);
                    }
                }
            } finally {
                g2.dispose();
            }
            ImageTexture tex = ImageTextureLoader.loadNowrapNearestTexture(img, false);
            CACHE.put(key, tex);
            texture = tex;
            return tex;
        } catch (Throwable t) {
            logger.warning("[ModFrame] failed to bake frame texture: " + t);
            return null;
        }
    }

    private static float clamp01(float v) {
        return v < 0f ? 0f : (v > 1f ? 1f : v);
    }

    private static int packColor8(float r, float g, float b, float a) {
        int ri = Math.round(clamp01(r) * 255f);
        int gi = Math.round(clamp01(g) * 255f);
        int bi = Math.round(clamp01(b) * 255f);
        int ai = Math.round(clamp01(a) * 255f);
        return ((ai & 0xFF) << 24) | ((ri & 0xFF) << 16) | ((gi & 0xFF) << 8) | (bi & 0xFF);
    }
}
