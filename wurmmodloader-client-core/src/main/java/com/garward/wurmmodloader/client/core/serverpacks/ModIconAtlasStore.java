package com.garward.wurmmodloader.client.core.serverpacks;

import javax.imageio.ImageIO;
import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Client-side dynamic-icon atlas builder.
 *
 * <p>Keeps the synced registry from the server (id → pack URI) and assembles
 * 640×384 atlas sheets on demand for icon ids beyond the seven vanilla sheets
 * (1680 .. 32767). Built sheets are cached; the cache is dropped whenever the
 * registry changes or a referenced pack arrives mid-flight.
 *
 * <h3>Wire-up</h3>
 * Sheet 0..6 are vanilla; the first dynamic sheet is absolute index 7.
 * <ul>
 *   <li>{@link #extraSheetCount()} — how many extra sheets the merger should
 *       splice in past the vanilla seven (indexed 0 .. count-1).
 *   <li>{@link #sheet(int)} — absolute-sheet equivalent: {@code extraIdx + 7}.
 *       Returns the painted atlas, or {@code null} if no entries land on it.
 *   <li>{@link #isDirty(int)} — true if the last build for that extra index
 *       had pack assets it couldn't yet resolve. The caller should rebuild
 *       (and re-upload to the GPU) when the pack lands; we deliberately
 *       don't silently retry — the merger needs to know when to invalidate
 *       its own atlas.
 * </ul>
 *
 * <h3>Atomic build</h3>
 * Sheets paint into a fresh {@code BufferedImage} that's only published into
 * the cache after the loop completes. Concurrent callers either get the
 * fully-built sheet or a {@code null} (no entries / build in progress);
 * they never see a half-painted atlas. Same lesson the inventory-icon race
 * fix taught us — first-publish-wins is safer than mid-build reads.
 *
 * <h3>Pack resolution</h3>
 * URIs in the registry are {@code pack:<packId>/<relPath>}; we walk them
 * through {@code com.garward.mods.serverpacks.api.PackAssetResolver}
 * reflectively (same hop as {@code ModImage}) so this class doesn't take a
 * compile-time dependency on the serverpacks mod.
 *
 * @since 0.4.1
 */
public final class ModIconAtlasStore {

    private static final Logger logger = Logger.getLogger(ModIconAtlasStore.class.getName());

    private static final int SLOT_SIZE = 32;
    private static final int SHEET_W = 640;
    private static final int SHEET_H = 384;
    private static final int SLOTS_PER_ROW = SHEET_W / SLOT_SIZE; // 20
    private static final int SLOTS_PER_COL = SHEET_H / SLOT_SIZE; // 12
    private static final int SLOTS_PER_SHEET = SLOTS_PER_ROW * SLOTS_PER_COL; // 240
    private static final int VANILLA_SHEETS = 7;

    private static final Object LOCK = new Object();
    private static volatile Map<Short, String> registry = Collections.emptyMap();

    private static final Map<Integer, BufferedImage> sheetCache = new ConcurrentHashMap<>();
    private static final Map<Integer, Boolean> dirty = new ConcurrentHashMap<>();

    private ModIconAtlasStore() {}

    /**
     * Replace the synced registry with the given snapshot. Invalidates every
     * cached sheet — callers must rebuild their atlases on the next access.
     *
     * <p>Called from the {@code com.garward.icons} ModComm channel listener
     * once the server's full-registry packet arrives.
     */
    public static void update(Map<Short, String> entries) {
        synchronized (LOCK) {
            registry = entries == null
                ? Collections.<Short, String>emptyMap()
                : new HashMap<>(entries);
            sheetCache.clear();
            dirty.clear();
        }
        logger.info("[ModIconAtlasStore] registry updated (" + registry.size() + " entries); cache invalidated");
    }

    /** Snapshot of the current registry. Empty when nothing has been synced yet. */
    public static Map<Short, String> entries() {
        return Collections.unmodifiableMap(registry);
    }

    /**
     * Number of dynamic sheets the registry needs. Sheets are 0-indexed past
     * the vanilla range — extra sheet {@code i} corresponds to absolute icon
     * sheet {@code i + 7}.
     */
    public static int extraSheetCount() {
        Map<Short, String> snap = registry;
        if (snap.isEmpty()) return 0;
        int maxAbs = 0;
        for (Short id : snap.keySet()) {
            int abs = (id & 0xFFFF) / SLOTS_PER_SHEET;
            if (abs > maxAbs) maxAbs = abs;
        }
        int extras = maxAbs - (VANILLA_SHEETS - 1);
        return Math.max(0, extras);
    }

    /**
     * True if the last build for {@code extraIdx} couldn't fully resolve every
     * entry (some packs hadn't downloaded yet). Caller should rebuild after
     * the missing pack lands. Stays {@code false} if the sheet hasn't been
     * built yet — callers should treat "not built" and "clean built" the
     * same.
     */
    public static boolean isDirty(int extraIdx) {
        Boolean d = dirty.get(extraIdx);
        return d != null && d;
    }

    /**
     * Build (or return cached) atlas for the given extra-sheet index.
     * {@code extraIdx + 7} is the absolute sheet number.
     *
     * <p>Returns {@code null} if no registry entry maps to that sheet. A
     * non-null result always has dimensions {@value #SHEET_W} × {@value #SHEET_H}
     * regardless of how many slots are populated; transparent slots stay
     * transparent.
     */
    public static BufferedImage sheet(int extraIdx) {
        if (extraIdx < 0) return null;
        BufferedImage cached = sheetCache.get(extraIdx);
        if (cached != null) return cached;

        int absSheet = extraIdx + VANILLA_SHEETS;
        Map<Short, String> snap = registry;
        if (snap.isEmpty()) return null;

        // Filter to entries on this sheet first — avoid building the canvas
        // if nothing lands here.
        Map<Short, String> onSheet = new HashMap<>();
        for (Map.Entry<Short, String> e : snap.entrySet()) {
            int abs = (e.getKey() & 0xFFFF) / SLOTS_PER_SHEET;
            if (abs == absSheet) onSheet.put(e.getKey(), e.getValue());
        }
        if (onSheet.isEmpty()) return null;

        BufferedImage atlas = new BufferedImage(SHEET_W, SHEET_H, BufferedImage.TYPE_4BYTE_ABGR);
        boolean sawDirty = false;
        int painted = 0;
        Graphics2D g = atlas.createGraphics();
        try {
            g.setComposite(AlphaComposite.Src);
            for (Map.Entry<Short, String> e : onSheet.entrySet()) {
                int id = e.getKey() & 0xFFFF;
                int slot = id % SLOTS_PER_SHEET;
                int x = (slot % SLOTS_PER_ROW) * SLOT_SIZE;
                int y = (slot / SLOTS_PER_ROW) * SLOT_SIZE;
                BufferedImage icon = loadIcon(e.getValue());
                if (icon == null) {
                    sawDirty = true;
                    continue;
                }
                g.drawImage(icon, x, y, SLOT_SIZE, SLOT_SIZE, null);
                painted++;
            }
        } finally {
            g.dispose();
        }

        synchronized (LOCK) {
            // Don't clobber a fresh build kicked off by a concurrent update().
            if (registry == snap || registry.equals(snap)) {
                sheetCache.put(extraIdx, atlas);
                dirty.put(extraIdx, sawDirty);
            }
        }
        logger.info("[ModIconAtlasStore] built extra sheet " + extraIdx
            + " (abs=" + absSheet + ", painted=" + painted + "/" + onSheet.size()
            + ", dirty=" + sawDirty + ")");
        return atlas;
    }

    /**
     * Resolve a single icon URI (currently {@code pack:<id>/<path>}) into a
     * 32×32 image. Returns {@code null} if the pack hasn't downloaded yet —
     * the caller marks the sheet dirty and rebuilds later.
     *
     * <p>Non-pack URIs (none expected today) fall through to {@code null}.
     */
    private static BufferedImage loadIcon(String uri) {
        if (uri == null || !uri.startsWith("pack:")) {
            logger.warning("[ModIconAtlasStore] unsupported icon URI: " + uri);
            return null;
        }
        String spec = uri.substring("pack:".length());
        int slash = spec.indexOf('/');
        if (slash <= 0) {
            logger.warning("[ModIconAtlasStore] malformed pack URI: " + uri);
            return null;
        }
        String packId = spec.substring(0, slash);
        String relPath = spec.substring(slash + 1);
        try {
            if (!com.garward.wurmmodloader.client.api.serverpacks.PackAssetResolver.isPackReady(packId)) {
                return null;
            }
            try (InputStream in = com.garward.wurmmodloader.client.api.serverpacks.PackAssetResolver.openStream(packId, relPath)) {
                if (in == null) return null;
                BufferedImage img = ImageIO.read(in);
                if (img == null) return null;
                if (img.getWidth() != SLOT_SIZE || img.getHeight() != SLOT_SIZE) {
                    logger.warning("[ModIconAtlasStore] icon " + uri
                        + " is " + img.getWidth() + "x" + img.getHeight()
                        + ", expected " + SLOT_SIZE + "x" + SLOT_SIZE);
                }
                return img;
            }
        } catch (Throwable t) {
            logger.log(Level.WARNING, "[ModIconAtlasStore] failed to load " + uri, t);
            return null;
        }
    }
}
