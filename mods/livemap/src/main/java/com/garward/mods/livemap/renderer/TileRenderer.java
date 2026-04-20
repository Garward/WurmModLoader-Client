package com.garward.mods.livemap.renderer;

import com.garward.mods.livemap.MapDataCache;
import com.wurmonline.client.renderer.backend.Queue;
import com.wurmonline.client.renderer.gui.Renderer;
import com.wurmonline.client.resources.textures.ImageTexture;
import com.wurmonline.client.resources.textures.ImageTextureLoader;

import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Renders a grid of map tiles from server.
 *
 * <p>This renderer:
 * <ul>
 *   <li>Calculates visible tile range based on world position and zoom</li>
 *   <li>Fetches tiles from cache or requests from server</li>
 *   <li>Converts BufferedImage tiles to OpenGL textures</li>
 *   <li>Renders tiles in correct screen positions</li>
 * </ul>
 *
 * @since 0.2.0
 */
public class TileRenderer {

    private static final Logger logger = Logger.getLogger(TileRenderer.class.getName());

    // Defaults until server config is received. Replaced by setMapConfig().
    private int tileSize = 256;
    private int mapSize = 8192;
    // Server: baseTilesPerSide = ceil(mapSize / tileSize); tilesPerSide(z) = base << z.
    private int baseTilesPerSide = 32;

    private final MapDataCache cache;
    private final Map<String, ImageTexture> textureCache;

    private TileRequestCallback tileRequestCallback;

    public TileRenderer(MapDataCache cache) {
        this.cache = cache;
        this.textureCache = new HashMap<>();
    }

    /** Configure from server's /livemap/api/config response. */
    public void setMapConfig(int mapSize, int tileSize) {
        if (this.mapSize == mapSize && this.tileSize == tileSize) return;
        this.mapSize = mapSize;
        this.tileSize = tileSize;
        this.baseTilesPerSide = (mapSize + tileSize - 1) / tileSize;
        textureCache.clear();
        logger.info(String.format("[LiveMap] Tile config: mapSize=%d, tileSize=%d, baseTilesPerSide=%d",
                mapSize, tileSize, baseTilesPerSide));
    }

    public int getMapSize() { return mapSize; }

    /** World units per screen pixel at the given zoom (for drag math, overlays). */
    public float getWorldUnitsPerPixel(int zoom) {
        float worldUnitsPerTile = (float) mapSize / (baseTilesPerSide << zoom);
        return worldUnitsPerTile / tileSize;
    }

    /**
     * Set callback for requesting uncached tiles.
     */
    public void setTileRequestCallback(TileRequestCallback callback) {
        this.tileRequestCallback = callback;
    }

    /**
     * Render tiles for given world position and view parameters.
     *
     * @param queue OpenGL rendering queue
     * @param screenX screen X position to render at
     * @param screenY screen Y position to render at
     * @param screenWidth width of view in pixels
     * @param screenHeight height of view in pixels
     * @param worldCenterX world X position (in world tiles, 0-4096)
     * @param worldCenterY world Y position (in world tiles, 0-4096)
     * @param zoom zoom level (0 = full world, 5 = max zoom)
     */
    public void render(Queue queue, int screenX, int screenY, int screenWidth, int screenHeight,
                      float worldCenterX, float worldCenterY, int zoom) {

        float worldUnitsPerTile = (float) mapSize / (baseTilesPerSide << zoom);
        float centerTileX = worldCenterX / worldUnitsPerTile;
        float centerTileY = worldCenterY / worldUnitsPerTile;

        float tilesWide = (float) screenWidth / tileSize;
        float tilesHigh = (float) screenHeight / tileSize;

        int maxTile = getMaxTileCoord(zoom);
        int minTileX = Math.max(0, (int) Math.floor(centerTileX - tilesWide / 2f) - 1);
        int maxTileX = Math.min(maxTile, (int) Math.ceil(centerTileX + tilesWide / 2f) + 1);
        int minTileY = Math.max(0, (int) Math.floor(centerTileY - tilesHigh / 2f) - 1);
        int maxTileY = Math.min(maxTile, (int) Math.ceil(centerTileY + tilesHigh / 2f) + 1);

        float screenCenterX = screenX + screenWidth / 2f;
        float screenCenterY = screenY + screenHeight / 2f;

        for (int tileY = minTileY; tileY <= maxTileY; tileY++) {
            for (int tileX = minTileX; tileX <= maxTileX; tileX++) {

                BufferedImage tileImage = cache.getTile(zoom, tileX, tileY);

                if (tileImage != null) {
                    ImageTexture texture = getOrCreateTexture(zoom, tileX, tileY, tileImage);

                    float tilePosX = screenCenterX + (tileX - centerTileX) * tileSize;
                    float tilePosY = screenCenterY + (tileY - centerTileY) * tileSize;

                    Renderer.texturedQuadAlphaBlend(queue, texture, 1.0f, 1.0f, 1.0f, 1.0f,
                            tilePosX, tilePosY, tileSize, tileSize,
                            0.0f, 0.0f, 1.0f, 1.0f);

                } else if (tileRequestCallback != null) {
                    tileRequestCallback.requestTile(zoom, tileX, tileY);
                }
            }
        }
    }

    /**
     * Get maximum tile coordinate for given zoom level.
     * For 4096x4096 world:
     * - Zoom 0: 128x128 tiles (4096/32)
     * - Zoom 1: 256x256 tiles (4096/16)
     * - Zoom 5: 4096x4096 tiles (4096/1)
     */
    private int getMaxTileCoord(int zoom) {
        return (baseTilesPerSide << zoom) - 1;
    }

    /**
     * Get or create OpenGL texture for tile.
     */
    private ImageTexture getOrCreateTexture(int zoom, int tileX, int tileY, BufferedImage image) {
        String key = zoom + "/" + tileX + "/" + tileY;

        ImageTexture texture = textureCache.get(key);
        if (texture == null) {
            texture = ImageTextureLoader.loadNowrapNearestTexture(image, false);
            textureCache.put(key, texture);
        }

        return texture;
    }

    /**
     * Clear all cached textures (call when changing maps or unloading).
     */
    public void clearTextures() {
        textureCache.clear();
    }

    /**
     * Callback interface for requesting tiles.
     */
    @FunctionalInterface
    public interface TileRequestCallback {
        void requestTile(int zoom, int tileX, int tileY);
    }

}
