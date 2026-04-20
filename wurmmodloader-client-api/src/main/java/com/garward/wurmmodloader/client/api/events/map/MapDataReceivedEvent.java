package com.garward.wurmmodloader.client.api.events.map;

import com.garward.wurmmodloader.client.api.events.base.Event;

/**
 * Fired when live map data is received from the server HTTP API.
 *
 * <p>This event contains real-time player positions, village boundaries, and altar locations
 * fetched from the server's /livemap/api/data endpoint. Mods can use this data to update
 * map overlays, track players, or display points of interest.
 *
 * <p>The JSON data is provided as a raw string for flexibility. Mods can parse it as needed.
 *
 * <p>Example usage:
 * <pre>{@code
 * @SubscribeEvent
 * public void onMapData(MapDataReceivedEvent event) {
 *     String json = event.getJsonData();
 *     // Parse JSON and update map overlays
 *     updatePlayerMarkers(parsePlayersFromJson(json));
 * }
 * }</pre>
 *
 * @since 0.2.0
 */
public class MapDataReceivedEvent extends Event {
    private final String jsonData;
    private final long timestamp;

    public MapDataReceivedEvent(String jsonData) {
        super(false); // Not cancellable - data already fetched
        this.jsonData = jsonData;
        this.timestamp = System.currentTimeMillis();
    }

    /**
     * Get the raw JSON data from the server.
     *
     * @return JSON string containing players, villages, and altars
     */
    public String getJsonData() {
        return jsonData;
    }

    /**
     * Get the timestamp when this data was received.
     *
     * @return timestamp in milliseconds
     */
    public long getTimestamp() {
        return timestamp;
    }

    @Override
    public String toString() {
        return String.format("MapDataReceived[dataSize=%d bytes, timestamp=%d]",
                jsonData.length(), timestamp);
    }
}
