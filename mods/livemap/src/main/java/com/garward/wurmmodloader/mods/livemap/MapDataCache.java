package com.garward.wurmmodloader.mods.livemap;

import com.garward.wurmmodloader.mods.livemap.data.MapOverlayData;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.imageio.ImageIO;

/**
 * Cache for map tiles and live data.
 *
 * <p>This cache stores:
 * <ul>
 *   <li>Map tiles (PNG images decoded to BufferedImage)</li>
 *   <li>Live map data (players, villages, altars)</li>
 * </ul>
 *
 * <p>All entries have TTL (time-to-live) to prevent stale data.
 *
 * @since 0.2.0
 */
public class MapDataCache {

    private static final Logger logger = Logger.getLogger(MapDataCache.class.getName());

    // Tile cache (key: "z/x/y", value: cached tile)
    private final Map<String, CachedTile> tileCache = new HashMap<>();

    // Map data cache (latest fetched data)
    private CachedMapData mapData = null;

    // Parsed overlay snapshot (players, villages, altars, towers)
    private volatile MapOverlayData overlay = MapOverlayData.EMPTY;

    // Configuration
    private final long tileTtlMs;
    private final long dataTtlMs;

    public MapDataCache(long tileTtlMs, long dataTtlMs) {
        this.tileTtlMs = tileTtlMs;
        this.dataTtlMs = dataTtlMs;
    }

    /**
     * Cache a tile image.
     *
     * @param zoom zoom level
     * @param tileX tile X coordinate
     * @param tileY tile Y coordinate
     * @param pngData raw PNG data
     */
    public void cacheTile(int zoom, int tileX, int tileY, byte[] pngData) {
        try {
            // Decode PNG to BufferedImage
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(pngData));

            if (image != null) {
                String key = getTileKey(zoom, tileX, tileY);
                tileCache.put(key, new CachedTile(image, tileTtlMs));
                logger.fine(String.format("[LiveMap] Cached tile %s", key));
            } else {
                logger.warning(String.format("[LiveMap] Failed to decode tile PNG: %d/%d/%d", zoom, tileX, tileY));
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, String.format("[LiveMap] Error caching tile %d/%d/%d", zoom, tileX, tileY), e);
        }
    }

    /**
     * Get a cached tile, or null if not cached/expired.
     *
     * @param zoom zoom level
     * @param tileX tile X coordinate
     * @param tileY tile Y coordinate
     * @return cached tile image, or null
     */
    public BufferedImage getTile(int zoom, int tileX, int tileY) {
        String key = getTileKey(zoom, tileX, tileY);
        CachedTile cached = tileCache.get(key);

        if (cached != null) {
            if (!cached.isExpired()) {
                return cached.image;
            } else {
                // Remove expired tile
                tileCache.remove(key);
                logger.fine(String.format("[LiveMap] Tile %s expired", key));
            }
        }

        return null;
    }

    /**
     * Cache map data (players, villages, altars).
     *
     * @param jsonData raw JSON data
     */
    public void cacheMapData(String jsonData) {
        mapData = new CachedMapData(jsonData, dataTtlMs);
        overlay = MapOverlayData.parse(jsonData);
        logger.fine("[LiveMap] Cached map data: " + jsonData.length() + " bytes");
    }

    /** Latest parsed overlay snapshot. Never null — returns EMPTY if nothing cached. */
    public MapOverlayData getOverlay() {
        return overlay;
    }

    /**
     * Get cached map data, or null if not cached/expired.
     *
     * @return cached JSON data, or null
     */
    public String getMapData() {
        if (mapData != null) {
            if (!mapData.isExpired()) {
                return mapData.jsonData;
            } else {
                // Remove expired data
                mapData = null;
                logger.fine("[LiveMap] Map data expired");
            }
        }

        return null;
    }

    /**
     * Clear all cached tiles and data.
     */
    public void clear() {
        tileCache.clear();
        mapData = null;
        overlay = MapOverlayData.EMPTY;
        logger.info("[LiveMap] Cache cleared");
    }

    /**
     * Get tile cache key.
     */
    private String getTileKey(int zoom, int tileX, int tileY) {
        return zoom + "/" + tileX + "/" + tileY;
    }

    /**
     * Cached tile with expiration.
     */
    private static class CachedTile {
        final BufferedImage image;
        final long expiresAt;

        CachedTile(BufferedImage image, long ttlMs) {
            this.image = image;
            this.expiresAt = System.currentTimeMillis() + ttlMs;
        }

        boolean isExpired() {
            return System.currentTimeMillis() > expiresAt;
        }
    }

    /**
     * Cached map data with expiration.
     */
    private static class CachedMapData {
        final String jsonData;
        final long expiresAt;

        CachedMapData(String jsonData, long ttlMs) {
            this.jsonData = jsonData;
            this.expiresAt = System.currentTimeMillis() + ttlMs;
        }

        boolean isExpired() {
            return System.currentTimeMillis() > expiresAt;
        }
    }
}
