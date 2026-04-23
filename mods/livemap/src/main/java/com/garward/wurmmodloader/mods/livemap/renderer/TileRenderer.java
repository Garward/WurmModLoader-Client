package com.garward.wurmmodloader.mods.livemap.renderer;

import com.garward.wurmmodloader.mods.livemap.MapDataCache;
import com.garward.wurmmodloader.mods.livemap.data.MapOverlayData;
import com.wurmonline.client.renderer.backend.Queue;
import com.wurmonline.client.renderer.gui.Renderer;
import com.wurmonline.client.renderer.gui.text.TextFont;
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
    // Server: baseTilesPerSide = ceil(mapSize / tileSize). Positive zoom doubles
    // tile count per side; negative zoom halves it (coarser overview tiles).
    private int baseTilesPerSide = 32;
    private int minZoom = -2;
    private int maxZoom = 5;

    /** Tile count along one side of the map at this zoom level. */
    public int tilesPerSideAt(int zoom) {
        if (zoom >= 0) return baseTilesPerSide << zoom;
        return Math.max(1, baseTilesPerSide >> (-zoom));
    }

    public int getMinZoom() { return minZoom; }
    public int getMaxZoom() { return maxZoom; }

    public void setZoomRange(int min, int max) {
        this.minZoom = min;
        this.maxZoom = max;
    }

    private final MapDataCache cache;
    private final Map<String, ImageTexture> textureCache;

    private TileRequestCallback tileRequestCallback;

    private ImageTexture playerDotTexture;
    private ImageTexture playerDotHaloTexture;

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
        float worldUnitsPerTile = (float) mapSize / (tilesPerSideAt(zoom));
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

        float worldUnitsPerTile = (float) mapSize / (tilesPerSideAt(zoom));
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
     * Draw the self-player marker at the given screen point. Callers decide
     * whether that's the view center (minimap) or the projected world position
     * (full map).
     */
    public void drawPlayerDot(Queue queue, float cx, float cy) {
        if (playerDotTexture == null) {
            playerDotTexture = makeSolidDot(6, 0xFFFF0000);
            playerDotHaloTexture = makeSolidDot(12, 0x55FFFFFF);
        }
        if (playerDotHaloTexture != null) {
            Renderer.texturedQuadAlphaBlend(queue, playerDotHaloTexture, 1f, 1f, 1f, 1f,
                    cx - 6f, cy - 6f, 12f, 12f, 0f, 0f, 1f, 1f);
        }
        if (playerDotTexture != null) {
            Renderer.texturedQuadAlphaBlend(queue, playerDotTexture, 1f, 1f, 1f, 1f,
                    cx - 3f, cy - 3f, 6f, 6f, 0f, 0f, 1f, 1f);
        }
    }

    private static ImageTexture makeSolidDot(int size, int argb) {
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        int cx = size / 2;
        int cy = size / 2;
        int r2 = (size / 2) * (size / 2);
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                int dx = x - cx, dy = y - cy;
                if (dx * dx + dy * dy <= r2) {
                    img.setRGB(x, y, argb);
                }
            }
        }
        try {
            return ImageTextureLoader.loadNowrapNearestTexture(img, false);
        } catch (Throwable t) {
            logger.warning("[LiveMap] Could not build player-dot texture: " + t);
            return null;
        }
    }

    // --- Overlays ---------------------------------------------------------

    // Lazily-built solid markers keyed by ARGB.
    private final Map<Integer, ImageTexture> markerCache = new HashMap<>();

    private ImageTexture marker(int argb, int size) {
        int key = (argb & 0xFFFFFF00) | (size & 0xFF);
        ImageTexture tex = markerCache.get(key);
        if (tex != null) return tex;
        tex = makeSolidDot(size, argb);
        if (tex != null) markerCache.put(key, tex);
        return tex;
    }

    /**
     * Project a world-tile coordinate to a screen-pixel point inside the
     * view anchored at (screenX, screenY) with the given size and zoom.
     */
    private float worldToScreenX(float worldX, float viewCenterX, int screenX, int screenWidth, int zoom) {
        float upp = getWorldUnitsPerPixel(zoom);
        return screenX + screenWidth / 2f + (worldX - viewCenterX) / upp;
    }

    private float worldToScreenY(float worldY, float viewCenterY, int screenY, int screenHeight, int zoom) {
        float upp = getWorldUnitsPerPixel(zoom);
        return screenY + screenHeight / 2f + (worldY - viewCenterY) / upp;
    }

    /**
     * Draw overlays (villages, altars, towers, other players) projected into
     * the view. The self-player dot is drawn last at its actual world position
     * if in-view, so it's never hidden by another marker.
     */
    public void renderOverlays(Queue queue,
                               int screenX, int screenY, int screenWidth, int screenHeight,
                               float viewCenterX, float viewCenterY, int zoom,
                               MapOverlayData overlay,
                               float selfWorldX, float selfWorldY,
                               String selfPlayerName) {
        if (overlay == null) overlay = MapOverlayData.EMPTY;
        int maxX = screenX + screenWidth;
        int maxY = screenY + screenHeight;

        // Villages — green token marker
        for (MapOverlayData.Village v : overlay.villages) {
            float px = worldToScreenX(v.tokenX, viewCenterX, screenX, screenWidth, zoom);
            float py = worldToScreenY(v.tokenY, viewCenterY, screenY, screenHeight, zoom);
            if (px < screenX || py < screenY || px > maxX || py > maxY) continue;
            ImageTexture t = marker(0xFF39D14E, 8);
            if (t != null) Renderer.texturedQuadAlphaBlend(queue, t, 1f, 1f, 1f, 1f,
                    px - 4f, py - 4f, 8f, 8f, 0f, 0f, 1f, 1f);
        }

        // Altars — white (three) / black (bone)
        for (MapOverlayData.Altar a : overlay.altars) {
            float px = worldToScreenX(a.x, viewCenterX, screenX, screenWidth, zoom);
            float py = worldToScreenY(a.y, viewCenterY, screenY, screenHeight, zoom);
            if (px < screenX || py < screenY || px > maxX || py > maxY) continue;
            int argb = "huge_bone_altar".equals(a.type) ? 0xFF101010 : 0xFFFFFFFF;
            ImageTexture t = marker(argb, 9);
            if (t != null) Renderer.texturedQuadAlphaBlend(queue, t, 1f, 1f, 1f, 1f,
                    px - 4.5f, py - 4.5f, 9f, 9f, 0f, 0f, 1f, 1f);
        }

        // Guard towers — kingdom-tinted
        for (MapOverlayData.Tower tw : overlay.towers) {
            float px = worldToScreenX(tw.x, viewCenterX, screenX, screenWidth, zoom);
            float py = worldToScreenY(tw.y, viewCenterY, screenY, screenHeight, zoom);
            if (px < screenX || py < screenY || px > maxX || py > maxY) continue;
            ImageTexture t = marker(kingdomColor(tw.kingdom), 7);
            if (t != null) Renderer.texturedQuadAlphaBlend(queue, t, 1f, 1f, 1f, 1f,
                    px - 3.5f, py - 3.5f, 7f, 7f, 0f, 0f, 1f, 1f);
        }

        // Other players — yellow. Filter self by name: server-snapshot x/y
        // lags behind live pos.getTileX(), so coordinate-based filtering
        // leaves a stale yellow marker trailing the red self-dot.
        for (MapOverlayData.Player p : overlay.players) {
            if (selfPlayerName != null && selfPlayerName.equals(p.name)) continue;
            float px = worldToScreenX(p.x, viewCenterX, screenX, screenWidth, zoom);
            float py = worldToScreenY(p.y, viewCenterY, screenY, screenHeight, zoom);
            if (px < screenX || py < screenY || px > maxX || py > maxY) continue;
            ImageTexture t = marker(0xFFFFDD33, 6);
            if (t != null) Renderer.texturedQuadAlphaBlend(queue, t, 1f, 1f, 1f, 1f,
                    px - 3f, py - 3f, 6f, 6f, 0f, 0f, 1f, 1f);
        }

        // Self-player — red, at actual world position (if in-view).
        float sx = worldToScreenX(selfWorldX, viewCenterX, screenX, screenWidth, zoom);
        float sy = worldToScreenY(selfWorldY, viewCenterY, screenY, screenHeight, zoom);
        if (sx >= screenX && sy >= screenY && sx <= maxX && sy <= maxY) {
            drawPlayerDot(queue, sx, sy);
        }
    }

    private ImageTexture whiteSquareTexture;

    private ImageTexture whiteSquare() {
        if (whiteSquareTexture != null) return whiteSquareTexture;
        BufferedImage img = new BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB);
        for (int yy = 0; yy < 2; yy++)
            for (int xx = 0; xx < 2; xx++)
                img.setRGB(xx, yy, 0xFFFFFFFF);
        try {
            whiteSquareTexture = ImageTextureLoader.loadNowrapNearestTexture(img, false);
        } catch (Throwable t) {
            logger.warning("[LiveMap] Could not build tooltip-bg texture: " + t);
        }
        return whiteSquareTexture;
    }

    private void drawRect(Queue queue, float x, float y, float w, float h,
                          float r, float g, float b, float a) {
        ImageTexture t = whiteSquare();
        if (t == null) return;
        Renderer.texturedQuadAlphaBlend(queue, t, r, g, b, a, x, y, w, h, 0f, 0f, 1f, 1f);
    }

    /**
     * Hit-test overlays at the given mouse-screen position; returns a short
     * multiline label for the topmost hit (or null if nothing under cursor).
     * Search order matches visual priority: self/other players > towers > altars > villages.
     */
    public String hitTest(int mouseX, int mouseY,
                          int screenX, int screenY, int screenWidth, int screenHeight,
                          float viewCenterX, float viewCenterY, int zoom,
                          MapOverlayData overlay) {
        if (overlay == null) return null;
        for (MapOverlayData.Player p : overlay.players) {
            float px = worldToScreenX(p.x, viewCenterX, screenX, screenWidth, zoom);
            float py = worldToScreenY(p.y, viewCenterY, screenY, screenHeight, zoom);
            if (within(mouseX, mouseY, px, py, 5)) {
                return p.name + "\n(" + (int) p.x + ", " + (int) p.y + ")";
            }
        }
        for (MapOverlayData.Tower tw : overlay.towers) {
            float px = worldToScreenX(tw.x, viewCenterX, screenX, screenWidth, zoom);
            float py = worldToScreenY(tw.y, viewCenterY, screenY, screenHeight, zoom);
            if (within(mouseX, mouseY, px, py, 5)) {
                return tw.name + "\nKingdom: " + kingdomName(tw.kingdom)
                        + "\nDamage: " + String.format("%.1f", tw.damage)
                        + "\n(" + (int) tw.x + ", " + (int) tw.y + ")";
            }
        }
        for (MapOverlayData.Altar a : overlay.altars) {
            float px = worldToScreenX(a.x, viewCenterX, screenX, screenWidth, zoom);
            float py = worldToScreenY(a.y, viewCenterY, screenY, screenHeight, zoom);
            if (within(mouseX, mouseY, px, py, 6)) {
                return a.name + "\n(" + (int) a.x + ", " + (int) a.y + ")";
            }
        }
        for (MapOverlayData.Village v : overlay.villages) {
            float px = worldToScreenX(v.tokenX, viewCenterX, screenX, screenWidth, zoom);
            float py = worldToScreenY(v.tokenY, viewCenterY, screenY, screenHeight, zoom);
            if (within(mouseX, mouseY, px, py, 6)) {
                StringBuilder sb = new StringBuilder();
                sb.append(v.name);
                if (!v.mayor.isEmpty())  sb.append("\nMayor: ").append(v.mayor);
                if (!v.motto.isEmpty())  sb.append("\n\"").append(v.motto).append("\"");
                sb.append("\nCitizens: ").append(v.citizens);
                if (v.permanent) sb.append(" (permanent)");
                sb.append("\n(").append((int) v.tokenX).append(", ").append((int) v.tokenY).append(")");
                return sb.toString();
            }
        }
        return null;
    }

    private static boolean within(int mx, int my, float cx, float cy, float halfSize) {
        return mx >= cx - halfSize && mx <= cx + halfSize
            && my >= cy - halfSize && my <= cy + halfSize;
    }

    /** Render a multiline tooltip near the cursor, clamped into the view bounds. */
    public void drawTooltip(Queue queue, String text, int mouseX, int mouseY,
                            int screenX, int screenY, int screenWidth, int screenHeight) {
        if (text == null || text.isEmpty()) return;
        TextFont font = TextFont.getText();
        String[] lines = text.split("\n");
        int lineH = font.getHeight();
        int pad = 4;
        int w = 0;
        for (String ln : lines) w = Math.max(w, font.getWidth(ln));
        int boxW = w + pad * 2;
        int boxH = lineH * lines.length + pad * 2;

        int bx = mouseX + 14;
        int by = mouseY + 14;
        if (bx + boxW > screenX + screenWidth)  bx = mouseX - 14 - boxW;
        if (by + boxH > screenY + screenHeight) by = mouseY - 14 - boxH;
        if (bx < screenX) bx = screenX;
        if (by < screenY) by = screenY;

        drawRect(queue, bx, by, boxW, boxH, 0.05f, 0.05f, 0.08f, 0.85f);
        drawRect(queue, bx, by, boxW, 1, 0.9f, 0.9f, 0.9f, 0.7f);
        drawRect(queue, bx, by + boxH - 1, boxW, 1, 0.9f, 0.9f, 0.9f, 0.7f);
        drawRect(queue, bx, by, 1, boxH, 0.9f, 0.9f, 0.9f, 0.7f);
        drawRect(queue, bx + boxW - 1, by, 1, boxH, 0.9f, 0.9f, 0.9f, 0.7f);

        int ty = by + pad + font.getAscent();
        for (String ln : lines) {
            font.moveTo(bx + pad, ty);
            font.paint(queue, ln, 1f, 1f, 1f, 1f);
            ty += lineH;
        }
    }

    private static String kingdomName(byte kingdom) {
        switch (kingdom) {
            case 1: return "Jenn-Kellon";
            case 2: return "Mol-Rehan";
            case 3: return "HOTS";
            case 4: return "Freedom";
            default: return "Unknown";
        }
    }

    private static int kingdomColor(byte kingdom) {
        switch (kingdom) {
            case 1: return 0xFFFFD24A; // Jenn-Kellon — gold
            case 2: return 0xFFDC3A3A; // Mol-Rehan — red
            case 3: return 0xFF202020; // HOTS — black
            case 4: return 0xFF3A8BDC; // Freedom — blue
            default: return 0xFFCCCCCC;
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
        return (tilesPerSideAt(zoom)) - 1;
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
        markerCache.clear();
        playerDotTexture = null;
        playerDotHaloTexture = null;
        whiteSquareTexture = null;
    }

    /**
     * Callback interface for requesting tiles.
     */
    @FunctionalInterface
    public interface TileRequestCallback {
        void requestTile(int zoom, int tileX, int tileY);
    }

}
