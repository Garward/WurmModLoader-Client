package com.garward.wurmmodloader.client.api.gui;

import com.wurmonline.client.renderer.backend.Queue;
import com.wurmonline.client.renderer.gui.Renderer;
import com.wurmonline.client.resources.textures.ImageTexture;
import com.wurmonline.client.resources.textures.ImageTextureLoader;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Static textured-quad widget. Resolves an image URI in one of four forms:
 *
 * <ul>
 *   <li>{@code classpath:/path/to/file.png} — loaded from the supplied resource
 *       anchor's classloader (mod JAR resources)</li>
 *   <li>{@code file:/abs/path.png} — loaded from disk</li>
 *   <li>{@code pack:<packId>/path.png} — loaded from a server-pushed pack jar
 *       via the serverpacks mod's {@code PackAssetResolver}. If the pack
 *       hasn't downloaded yet the widget retries silently each frame; once the
 *       pack lands the texture loads and is cached normally.</li>
 *   <li>{@code <name>} — short form, equivalent to
 *       {@code classpath:/declarativeui/images/<name>}</li>
 * </ul>
 *
 * <p>Textures are cached by URI so reusing the same image across many widgets
 * costs a single GL upload.
 */
public class ModImage extends ModComponent {

    private static final Logger logger = Logger.getLogger(ModImage.class.getName());
    private static final Map<String, ImageTexture> CACHE = new HashMap<>();

    private final String uri;
    private final Class<?> resourceAnchor;
    private final float r, g, b, a;
    private ImageTexture texture;
    private boolean loadAttempted;

    public ModImage(String uri, Class<?> resourceAnchor, int width, int height) {
        this(uri, resourceAnchor, width, height, 1f, 1f, 1f, 1f);
    }

    public ModImage(String uri, Class<?> resourceAnchor, int width, int height,
                    float r, float g, float b, float a) {
        super("image", width, height);
        this.uri = uri;
        this.resourceAnchor = resourceAnchor;
        this.r = r; this.g = g; this.b = b; this.a = a;
    }

    @Override
    protected boolean consumesMouseInput() {
        return false;
    }

    @Override
    protected void onRender(Queue queue, float alpha) {
        ImageTexture t = ensureTexture();
        if (t == null) return;
        // Ignore parent alpha — same flicker fix as ModLabel/ModEdge. Vanilla
        // WurmLabel/WButton both hardcode 1.0; multiplying parent alpha makes
        // backgrounds blink during window fades.
        Renderer.texturedQuadAlphaBlend(queue, t, r, g, b, a,
                getScreenX(), getScreenY(),
                getComponentWidth(), getComponentHeight(),
                0f, 0f, 1f, 1f);
    }

    private ImageTexture ensureTexture() {
        if (texture != null) return texture;
        if (loadAttempted) return null;
        // Defer pack: URIs until the pack is on disk — don't burn the
        // load-attempt budget while a download is still in flight.
        if (uri != null && uri.startsWith("pack:") && !isPackReady(uri)) {
            return null;
        }
        loadAttempted = true;
        texture = loadTexture(uri, resourceAnchor);
        return texture;
    }

    private static boolean isPackReady(String packUri) {
        String spec = packUri.substring("pack:".length());
        int slash = spec.indexOf('/');
        if (slash <= 0) return true; // malformed; let load attempt log the error
        String packId = spec.substring(0, slash);
        try {
            Class<?> resolver = Class.forName("com.garward.mods.serverpacks.api.PackAssetResolver");
            return (boolean) resolver.getMethod("isPackReady", String.class).invoke(null, packId);
        } catch (ClassNotFoundException e) {
            return true; // serverpacks not installed; let openStream produce a clean error
        } catch (Exception e) {
            return true;
        }
    }

    private static synchronized ImageTexture loadTexture(String uri, Class<?> anchor) {
        if (uri == null || uri.isEmpty()) return null;
        ImageTexture cached = CACHE.get(uri);
        if (cached != null) return cached;

        try (InputStream is = openStream(uri, anchor)) {
            if (is == null) {
                logger.warning("[ModImage] resource not found: " + uri);
                return null;
            }
            BufferedImage raw = ImageIO.read(is);
            if (raw == null) {
                logger.warning("[ModImage] failed to decode: " + uri);
                return null;
            }
            BufferedImage img = raw;
            if (raw.getType() != BufferedImage.TYPE_INT_ARGB) {
                img = new BufferedImage(raw.getWidth(), raw.getHeight(), BufferedImage.TYPE_INT_ARGB);
                java.awt.Graphics2D g = img.createGraphics();
                g.drawImage(raw, 0, 0, null);
                g.dispose();
            }
            ImageTexture tex = ImageTextureLoader.loadNowrapNearestTexture(img, true);
            CACHE.put(uri, tex);
            return tex;
        } catch (Exception e) {
            logger.log(Level.WARNING, "[ModImage] error loading " + uri, e);
            return null;
        }
    }

    private static InputStream openStream(String uri, Class<?> anchor) throws Exception {
        if (uri.startsWith("classpath:")) {
            String path = uri.substring("classpath:".length());
            if (!path.startsWith("/")) path = "/" + path;
            ClassLoader cl = (anchor != null ? anchor : ModImage.class).getClassLoader();
            // getResourceAsStream on classloader uses no leading slash
            String clPath = path.startsWith("/") ? path.substring(1) : path;
            InputStream is = cl.getResourceAsStream(clPath);
            if (is != null) return is;
            // Fallback to class-relative.
            return ModImage.class.getResourceAsStream(path);
        }
        if (uri.startsWith("file:")) {
            String path = uri.substring("file:".length());
            return new FileInputStream(new File(path));
        }
        if (uri.startsWith("pack:")) {
            String spec = uri.substring("pack:".length());
            int slash = spec.indexOf('/');
            if (slash <= 0) {
                logger.warning("[ModImage] malformed pack URI (need pack:<id>/<path>): " + uri);
                return null;
            }
            String packId = spec.substring(0, slash);
            String relPath = spec.substring(slash + 1);
            try {
                Class<?> resolver = Class.forName("com.garward.mods.serverpacks.api.PackAssetResolver");
                return (InputStream) resolver
                        .getMethod("openStream", String.class, String.class)
                        .invoke(null, packId, relPath);
            } catch (ClassNotFoundException e) {
                logger.warning("[ModImage] pack: scheme requires the serverpacks mod (not loaded): " + uri);
                return null;
            } catch (java.lang.reflect.InvocationTargetException e) {
                Throwable cause = e.getCause() != null ? e.getCause() : e;
                throw new Exception("PackAssetResolver failed for " + uri, cause);
            }
        }
        // Short form → declarativeui's bundled images dir. Resolve through the
        // anchor's classloader (typically a mod's class) — ModImage itself
        // lives in the framework jar whose classloader can't see resources
        // packed into a separately-loaded mod jar. Fall back to ModImage's own
        // loader so framework-bundled images still work when no anchor is
        // supplied.
        String shortPath = "declarativeui/images/" + uri;
        ClassLoader anchorCl = (anchor != null ? anchor : ModImage.class).getClassLoader();
        InputStream is = anchorCl.getResourceAsStream(shortPath);
        if (is != null) return is;
        is = ModImage.class.getClassLoader().getResourceAsStream(shortPath);
        if (is != null) return is;
        return ModImage.class.getResourceAsStream("/" + shortPath);
    }
}
