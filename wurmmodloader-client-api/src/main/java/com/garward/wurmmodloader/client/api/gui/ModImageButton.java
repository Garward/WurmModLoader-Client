package com.garward.wurmmodloader.client.api.gui;

import com.wurmonline.client.renderer.backend.Queue;
import com.wurmonline.client.renderer.gui.ButtonListener;
import com.wurmonline.client.renderer.gui.Renderer;
import com.wurmonline.client.renderer.gui.WButton;
import com.wurmonline.client.resources.textures.ImageTexture;
import com.wurmonline.client.resources.textures.ImageTextureLoader;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Button whose face is a mod-shipped icon image instead of a text label.
 *
 * <p>Loads a PNG (or any {@link ImageIO}-readable format) from a classpath
 * resource and renders it inset inside Wurm's native button frame. The icon's
 * non-transparent bounding box is computed at load time and used as the UV
 * region — so a poorly-centered source PNG (icon offset within its canvas)
 * still renders centered in the button.
 *
 * <pre>{@code
 * ModImageButton zoomIn = new ModImageButton(
 *     MyMod.class, "icons/zoom_in.png", "Zoom in", mapView::zoomIn);
 * }</pre>
 *
 * <p>The {@code resourceAnchor} class is used as the classloader origin for
 * the resource lookup — pass a class that lives in your mod's JAR so the
 * mod's own classloader resolves the resource path.
 *
 * <p>Textures are cached per resource path, so creating many buttons with
 * the same icon costs a single GL upload.
 */
public class ModImageButton extends WButton implements ButtonListener, LayoutHints.Provider {

    private static final Logger logger = Logger.getLogger(ModImageButton.class.getName());
    private static final Map<String, IconAsset> ICON_CACHE = new HashMap<>();
    private static final int FRAME_INSET = 4;

    private final IconAsset icon;
    private Runnable onClick;

    public ModImageButton(Class<?> resourceAnchor, String resourcePath,
                          String hoverText, Runnable onClick) {
        this(resourceAnchor, resourcePath, hoverText, onClick, 0);
    }

    /**
     * @param size if {@code > 0}, fixes the button to a square of this size
     *             (legacy use). Pass {@code 0} when adding to a layout-aware
     *             container like {@link ModStackPanel} — the layout decides
     *             the size based on this widget's 1:1 aspect hint.
     */
    public ModImageButton(Class<?> resourceAnchor, String resourcePath,
                          String hoverText, Runnable onClick, int size) {
        super("", 0, 0);
        this.icon = loadIcon(resourceAnchor, resourcePath);
        this.onClick = onClick;
        setButtonListener(this);
        if (hoverText != null && !hoverText.isEmpty()) {
            setHoverString(hoverText);
        }
        if (size > 0) {
            setInitialSize(size, size, false);
            sizeFlags = FIXED_WIDTH | FIXED_HEIGHT;
        } else {
            sizeFlags = 0;
        }
    }

    public ModImageButton onClick(Runnable handler) {
        this.onClick = handler;
        return this;
    }

    @Override
    protected void renderComponent(Queue queue, float alpha) {
        super.renderComponent(queue, alpha);
        if (icon == null || icon.texture == null) return;
        float tint = (isDown || isCloseHovered) && isEnabled() ? 0.7f : 1.0f;

        int w = GuiAccess.getWidth(this);
        int h = GuiAccess.getHeight(this);
        int innerX = this.x + FRAME_INSET;
        int innerY = this.y + FRAME_INSET;
        int innerW = Math.max(0, w - FRAME_INSET * 2);
        int innerH = Math.max(0, h - FRAME_INSET * 2);

        // Fit icon's content bbox aspect inside inner square, centred.
        float bboxAspect = (float) icon.bboxW / (float) icon.bboxH;
        int destW, destH;
        int side = Math.min(innerW, innerH);
        if (bboxAspect >= 1f) {
            destW = side;
            destH = Math.round(side / bboxAspect);
        } else {
            destH = side;
            destW = Math.round(side * bboxAspect);
        }
        int dx = (innerW - destW) / 2;
        int dy = (innerH - destH) / 2;

        Renderer.texturedQuadAlphaBlend(queue, icon.texture, tint, tint, tint, 1.0f,
                innerX + dx, innerY + dy, destW, destH,
                icon.u0, icon.v0, icon.u1 - icon.u0, icon.v1 - icon.v0);
    }

    @Override
    public LayoutHints getDefaultLayoutHints() {
        return new LayoutHints().aspectRatio(1f).align(Alignment.CENTER, Alignment.START);
    }

    @Override
    public void buttonPressed(WButton button) {
    }

    @Override
    public void buttonClicked(WButton button) {
        if (onClick != null) {
            onClick.run();
        }
    }

    private static synchronized IconAsset loadIcon(Class<?> anchor, String path) {
        String key = anchor.getName() + "!" + path;
        IconAsset cached = ICON_CACHE.get(key);
        if (cached != null) return cached;
        try (InputStream is = anchor.getResourceAsStream(path)) {
            if (is == null) {
                logger.warning("ModImageButton: resource not found: " + path
                        + " (anchor=" + anchor.getName() + ")");
                return null;
            }
            BufferedImage raw = ImageIO.read(is);
            if (raw == null) {
                logger.warning("ModImageButton: failed to decode image: " + path);
                return null;
            }
            BufferedImage img = raw;
            if (raw.getType() != BufferedImage.TYPE_INT_ARGB) {
                img = new BufferedImage(raw.getWidth(), raw.getHeight(), BufferedImage.TYPE_INT_ARGB);
                java.awt.Graphics2D g = img.createGraphics();
                g.drawImage(raw, 0, 0, null);
                g.dispose();
            }
            int[] bbox = computeAlphaBbox(img);
            ImageTexture tex = ImageTextureLoader.loadNowrapNearestTexture(img, true);
            IconAsset asset = new IconAsset(tex, img.getWidth(), img.getHeight(), bbox);
            ICON_CACHE.put(key, asset);
            return asset;
        } catch (Exception e) {
            logger.log(Level.WARNING, "ModImageButton: error loading " + path, e);
            return null;
        }
    }

    /** Returns {minX, minY, maxX, maxY} of pixels with alpha &gt; 8. */
    private static int[] computeAlphaBbox(BufferedImage img) {
        int w = img.getWidth(), h = img.getHeight();
        int minX = w, minY = h, maxX = -1, maxY = -1;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int a = (img.getRGB(x, y) >>> 24) & 0xFF;
                if (a > 8) {
                    if (x < minX) minX = x;
                    if (y < minY) minY = y;
                    if (x > maxX) maxX = x;
                    if (y > maxY) maxY = y;
                }
            }
        }
        if (maxX < 0) {
            return new int[]{0, 0, w, h};
        }
        return new int[]{minX, minY, maxX + 1, maxY + 1};
    }

    private static final class IconAsset {
        final ImageTexture texture;
        final int bboxW, bboxH;
        final float u0, v0, u1, v1;

        IconAsset(ImageTexture tex, int imgW, int imgH, int[] bbox) {
            this.texture = tex;
            this.bboxW = bbox[2] - bbox[0];
            this.bboxH = bbox[3] - bbox[1];
            this.u0 = bbox[0] / (float) imgW;
            this.v0 = bbox[1] / (float) imgH;
            this.u1 = bbox[2] / (float) imgW;
            this.v1 = bbox[3] / (float) imgH;
        }
    }
}
