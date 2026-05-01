package com.garward.wurmmodloader.client.core.serverpacks;

import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.imageio.ImageIO;

/**
 * Composites a single logical icon sheet from every pack that ships one.
 *
 * <p>Vanilla {@code IconLoader.initIcons()} resolves each icon-sheet key
 * (e.g. {@code img.iconsheet.icons}) to one PNG from the first pack that
 * claims it — first match wins, no merging. Server-pack authors often ship
 * "overlay" sheets that only redraw the slots they care about and leave the
 * rest transparent. With first-match-wins, an overlay pack at the front of
 * the chain wipes every icon it didn't redraw.
 *
 * <p>This merger walks the whole pack chain in priority order and stacks
 * each sheet so that higher-priority packs win where they have opaque pixels
 * and lower-priority packs (ultimately vanilla) fill the transparent gaps.
 *
 * <p>The compositing rule is plain {@code SrcOver}: lowest priority gets
 * drawn first as the base, then each higher-priority sheet is layered on
 * top — its opaque pixels overwrite, its transparent ones let the layer
 * below show through.
 *
 * <p>All Wurm types are accessed reflectively because {@code Pack},
 * {@code Resources}, and {@code ResourceUrl} are package-private at the
 * client-core compile boundary. The framework widens them at runtime, but
 * {@code compileOnly client.jar} sees the original signatures.
 */
public final class IconSheetMerger {

    private static final Logger logger = Logger.getLogger(IconSheetMerger.class.getName());

    private IconSheetMerger() {}

    /**
     * Loads and merges {@code key} across every pack that ships it.
     *
     * @param key the iconsheet resource key (e.g. {@code img.iconsheet.icons})
     * @return composited sheet, or {@code null} if no pack provided one
     */
    public static BufferedImage loadMergedSheet(String key) {
        Object resourceManager = invokeStatic(
            "com.wurmonline.client.WurmClientBase", "getResourceManager");
        if (resourceManager == null) {
            logger.warning("[IconSheetMerger] resource manager is null for " + key);
            return null;
        }

        List<BufferedImage> sheets = collectSheets(resourceManager, key);

        if (sheets.isEmpty()) {
            logger.warning("[IconSheetMerger] no pack produced a sheet for " + key);
            return null;
        }
        if (sheets.size() == 1) {
            return sheets.get(0);
        }

        // Use the first (highest priority) sheet's dimensions as the canvas.
        // Vanilla and overlay packs ship 640x384 in practice.
        int width = sheets.get(0).getWidth();
        int height = sheets.get(0).getHeight();

        // TYPE_4BYTE_ABGR matches what IconLoader.getIcon() uses for its 32x32
        // crop target, and what ImageIO.read() typically returns for ARGB PNGs.
        // Using TYPE_INT_ARGB worked for ImageIO.write (the PNG dump looks
        // correct) but failed for the live Graphics2D draw inside getIcon —
        // some slots rendered as fully transparent textures.
        BufferedImage merged = new BufferedImage(width, height, BufferedImage.TYPE_4BYTE_ABGR);

        // Per-cell selection: walk the 32x32 slot grid and, for each cell,
        // pick the highest-priority pack whose slot passes slotHasContent().
        //
        // We use Graphics2D.drawImage with AlphaComposite.Src to copy each
        // selected slot. Earlier we tried getRGB/setRGB; the bytes round-trip
        // correctly through ImageIO.write (so the debug PNG looks right) but
        // the resulting BufferedImage rendered as transparent textures when
        // IconLoader.getIcon's Graphics2D.drawImage pulled cells from it.
        // Building the merged image through the same Graphics2D pipeline
        // getIcon uses sidesteps whichever color-model / pipeline mismatch
        // breaks the setRGB path.
        int cols = width / SLOT_SIZE;
        int rows = height / SLOT_SIZE;
        int[] slotChoice = new int[sheets.size()];
        Graphics2D g = merged.createGraphics();
        try {
            g.setComposite(AlphaComposite.Src);
            for (int cy = 0; cy < rows; cy++) {
                for (int cx = 0; cx < cols; cx++) {
                    int chosen = -1;
                    for (int p = 0; p < sheets.size(); p++) {
                        BufferedImage sheet = sheets.get(p);
                        if (cx * SLOT_SIZE + SLOT_SIZE > sheet.getWidth()) continue;
                        if (cy * SLOT_SIZE + SLOT_SIZE > sheet.getHeight()) continue;
                        if (slotHasContent(sheet, cx * SLOT_SIZE, cy * SLOT_SIZE)) {
                            chosen = p;
                            break;
                        }
                    }
                    if (chosen >= 0) {
                        BufferedImage sheet = sheets.get(chosen);
                        int sx = cx * SLOT_SIZE;
                        int sy = cy * SLOT_SIZE;
                        g.drawImage(sheet,
                            sx, sy, sx + SLOT_SIZE, sy + SLOT_SIZE,
                            sx, sy, sx + SLOT_SIZE, sy + SLOT_SIZE,
                            null);
                        if (chosen < slotChoice.length) slotChoice[chosen]++;
                    }
                }
            }
        } finally {
            g.dispose();
        }
        StringBuilder breakdown = new StringBuilder();
        for (int p = 0; p < slotChoice.length; p++) {
            if (p > 0) breakdown.append(", ");
            breakdown.append("priority").append(p).append("=").append(slotChoice[p]);
        }
        logger.info("[IconSheetMerger] merged " + sheets.size() + " sheets for " + key
            + " (" + width + "x" + height + ") type=" + merged.getType()
            + " slots-by-pack: " + breakdown);
        // Normalize via PNG round-trip. Vanilla iconsheets in graphics.jar
        // ship with embedded iCCP color profiles. When we Graphics2D-draw
        // from those iCCP-tagged sources, color management leaves residue
        // in the merged BufferedImage that ImageIO.write/read paper over but
        // IconLoader.getIcon's drawImage downstream renders as transparent
        // (only the vanilla-sourced cells turned invisible — exactly the
        // 39-cells-per-sheet count we'd predict). Round-tripping the merged
        // image through PNG bytes hands getIcon a "clean" ImageIO-decoded
        // BufferedImage, identical to what vanilla initIcons would feed it.
        return normalizeViaPng(key, merged);
    }

    private static BufferedImage normalizeViaPng(String key, BufferedImage merged) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream(64 * 1024);
            ImageIO.write(merged, "PNG", baos);
            BufferedImage normalized = ImageIO.read(new ByteArrayInputStream(baos.toByteArray()));
            if (normalized == null) {
                logger.warning("[IconSheetMerger] PNG round-trip returned null for " + key
                    + "; falling back to raw merge");
                return merged;
            }
            return normalized;
        } catch (Throwable t) {
            logger.log(Level.WARNING, "[IconSheetMerger] PNG round-trip failed for " + key
                + "; falling back to raw merge", t);
            return merged;
        }
    }

    private static final int SLOT_SIZE = 32;

    // Minimum opaque pixel count for a slot to be considered "real" content.
    // Overlay packs sometimes ship sparse placeholder slots — a handful of
    // stray pixels from authoring — that pass a naive "any opaque pixel"
    // check and beat the real vanilla icon underneath; some packs also ship
    // ~12-pixel black-opaque artifacts. Legitimate icons typically have 100+
    // opaque pixels, so 16 cleanly separates the two regimes.
    //
    // We count any opaque pixel (not just non-black-opaque). Vanilla icons.png
    // ships solid-black-opaque cells at icons[11,11] and [12,11] which are
    // legitimate content; an "exclude pure-black-opaque" filter wrongly
    // discards them.
    private static final int MIN_REAL_PIXELS = 16;

    private static boolean slotHasContent(BufferedImage sheet, int x0, int y0) {
        int opaque = 0;
        for (int y = y0; y < y0 + SLOT_SIZE; y++) {
            for (int x = x0; x < x0 + SLOT_SIZE; x++) {
                int argb = sheet.getRGB(x, y);
                int a = (argb >>> 24) & 0xff;
                if (a != 0) {
                    opaque++;
                    if (opaque >= MIN_REAL_PIXELS) return true;
                }
            }
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    private static List<BufferedImage> collectSheets(Object resources, String key) {
        List<BufferedImage> sheets = new ArrayList<>();
        Object packsField = readField(resources, "packs");
        int packCount = 0;
        if (packsField instanceof List) {
            List<Object> packs = (List<Object>) packsField;
            packCount = packs.size();
            for (Object pack : packs) {
                String name = safeName(pack);
                boolean claims = packClaims(pack, key);
                BufferedImage img = claims ? readSheet(pack, key) : null;
                logger.info("[IconSheetMerger] " + key + " pack=" + name
                    + " claims=" + claims + " loaded=" + (img != null));
                if (img != null) sheets.add(img);
            }
        }
        // Vanilla iconsheets ship inside InternalPack (rootPack); add it last
        // so it's the lowest-priority base layer.
        Object rootPack = readField(resources, "rootPack");
        if (rootPack != null) {
            String name = safeName(rootPack);
            boolean claims = packClaims(rootPack, key);
            BufferedImage img = claims ? readSheet(rootPack, key) : null;
            logger.info("[IconSheetMerger] " + key + " rootPack=" + name
                + " claims=" + claims + " loaded=" + (img != null));
            if (img != null) sheets.add(img);
        }
        logger.info("[IconSheetMerger] " + key + " scanned " + packCount
            + " packs + rootPack, collected " + sheets.size() + " sheets");
        return sheets;
    }

    private static boolean packClaims(Object pack, String key) {
        try {
            Method getResource = findMethod(pack.getClass(), "getResource", String.class);
            if (getResource == null) return false;
            return getResource.invoke(pack, key) != null;
        } catch (Throwable t) {
            return false;
        }
    }

    private static BufferedImage readSheet(Object pack, String key) {
        try {
            Method getResource = findMethod(pack.getClass(), "getResource", String.class);
            if (getResource == null) return null;
            Object url = getResource.invoke(pack, key);
            if (url == null) return null;
            Method openStream = findMethod(url.getClass(), "openStream");
            if (openStream == null) return null;
            try (InputStream in = (InputStream) openStream.invoke(url)) {
                return ImageIO.read(in);
            }
        } catch (Throwable t) {
            logger.log(Level.WARNING, "[IconSheetMerger] failed to read " + key
                + " from pack " + safeName(pack), t);
            return null;
        }
    }

    private static Method findMethod(Class<?> c, String name, Class<?>... params) {
        Class<?> cur = c;
        while (cur != null) {
            try {
                Method m = cur.getDeclaredMethod(name, params);
                m.setAccessible(true);
                return m;
            } catch (NoSuchMethodException e) {
                cur = cur.getSuperclass();
            }
        }
        return null;
    }

    private static Object invokeStatic(String className, String methodName) {
        try {
            Class<?> clazz = Class.forName(className);
            Method m = findMethod(clazz, methodName);
            if (m == null) {
                logger.warning("[IconSheetMerger] method " + className + "." + methodName + "() not found");
                return null;
            }
            return m.invoke(null);
        } catch (Throwable t) {
            logger.log(Level.WARNING, "[IconSheetMerger] failed to invoke "
                + className + "." + methodName + "()", t);
            return null;
        }
    }

    private static Object readField(Object target, String name) {
        Class<?> c = target.getClass();
        while (c != null) {
            try {
                Field f = c.getDeclaredField(name);
                f.setAccessible(true);
                return f.get(target);
            } catch (NoSuchFieldException e) {
                c = c.getSuperclass();
            } catch (Throwable t) {
                logger.log(Level.WARNING, "[IconSheetMerger] failed to read field "
                    + name + " from " + target.getClass().getName(), t);
                return null;
            }
        }
        return null;
    }

    private static String safeName(Object pack) {
        try {
            Method m = findMethod(pack.getClass(), "getName");
            if (m != null) {
                Object name = m.invoke(pack);
                if (name != null) return name.toString();
            }
        } catch (Throwable ignored) {}
        return pack.getClass().getSimpleName();
    }
}
