package com.garward.wurmmodloader.client.api.events.map;

import com.garward.wurmmodloader.client.api.events.base.Event;

/**
 * Fired when a map tile image is received from the server HTTP API.
 *
 * <p>This event contains a PNG tile image fetched from the server's /livemap/tile/{z}/{x}/{y}.png
 * endpoint. Tiles are used to render the map at different zoom levels.
 *
 * <p>The tile data is provided as raw bytes (PNG format). Mods can decode and display them
 * as needed, or cache them for future use.
 *
 * <p>Example usage:
 * <pre>{@code
 * @SubscribeEvent
 * public void onMapTile(MapTileReceivedEvent event) {
 *     int z = event.getZoom();
 *     int x = event.getTileX();
 *     int y = event.getTileY();
 *     byte[] pngData = event.getTileData();
 *
 *     // Decode and cache tile
 *     BufferedImage image = ImageIO.read(new ByteArrayInputStream(pngData));
 *     tileCache.put(z + "/" + x + "/" + y, image);
 * }
 * }</pre>
 *
 * @since 0.2.0
 */
public class MapTileReceivedEvent extends Event {
    private final int zoom;
    private final int tileX;
    private final int tileY;
    private final byte[] tileData;

    public MapTileReceivedEvent(int zoom, int tileX, int tileY, byte[] tileData) {
        super(false); // Not cancellable - tile already fetched
        this.zoom = zoom;
        this.tileX = tileX;
        this.tileY = tileY;
        this.tileData = tileData;
    }

    /**
     * Get the zoom level of this tile.
     *
     * @return zoom level (0 = zoomed out, higher = zoomed in)
     */
    public int getZoom() {
        return zoom;
    }

    /**
     * Get the X coordinate of this tile.
     *
     * @return tile X coordinate
     */
    public int getTileX() {
        return tileX;
    }

    /**
     * Get the Y coordinate of this tile.
     *
     * @return tile Y coordinate
     */
    public int getTileY() {
        return tileY;
    }

    /**
     * Get the raw PNG data for this tile.
     *
     * @return PNG image data as byte array
     */
    public byte[] getTileData() {
        return tileData;
    }

    @Override
    public String toString() {
        return String.format("MapTileReceived[zoom=%d, x=%d, y=%d, size=%d bytes]",
                zoom, tileX, tileY, tileData.length);
    }
}
