package com.garward.wurmmodloader.mods.livemap;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * HTTP client for fetching live map data and tiles from the server.
 *
 * <p>This client connects to the server's HTTP endpoints to fetch:
 * <ul>
 *   <li>{@code /livemap/data/config.json} - Map configuration</li>
 *   <li>{@code /livemap/data/players.json}, {@code villages.json},
 *       {@code guardtowers.json} - Live overlay data (combined client-side
 *       into the legacy {@code {players,villages,altars,towers}} shape)</li>
 *   <li>{@code /livemap/images/{x}-{y}.png} - Single-zoom static tile grid</li>
 * </ul>
 *
 * <p>All fetch operations run in background threads to avoid blocking the client.
 * Results are delivered via callbacks.
 *
 * @since 0.2.0
 */
public class MapHttpClient {

    private static final Logger logger = Logger.getLogger(MapHttpClient.class.getName());
    private static final int TIMEOUT_MS = 5000; // 5 second timeout

    private final String serverUrl;

    public MapHttpClient(String serverUrl) {
        this.serverUrl = serverUrl.endsWith("/") ? serverUrl.substring(0, serverUrl.length() - 1) : serverUrl;
        logger.info("[LiveMap] HTTP client initialized for: " + this.serverUrl);
    }

    /**
     * Fetch map configuration from server.
     * Returns JSON string, or null on error.
     */
    public String fetchConfig() {
        try {
            return fetchJson(serverUrl + "/livemap/data/config.json");
        } catch (Exception e) {
            logger.log(Level.WARNING, "[LiveMap] Failed to fetch config", e);
            return null;
        }
    }

    /**
     * Fetch live map data from server (async).
     *
     * @param callback called with JSON data when fetch completes
     */
    public void fetchDataAsync(Consumer<String> callback) {
        fetchDataAsync(null, callback);
    }

    /**
     * Fetch live map data and combine the three split server JSONs
     * ({@code players.json}, {@code villages.json}, {@code guardtowers.json})
     * into the legacy combined shape {@code {players,villages,altars,towers}}
     * the parser expects.
     *
     * <p>The {@code viewerName} parameter is currently ignored — the public
     * HTTP feed is unscoped (the village-gated per-viewer feed only exists
     * over the {@code livemap.markers} ModComm channel).
     */
    public void fetchDataAsync(String viewerName, Consumer<String> callback) {
        new Thread(() -> {
            try {
                String players  = fetchJson(serverUrl + "/livemap/data/players.json");
                String villages = fetchJson(serverUrl + "/livemap/data/villages.json");
                String towers   = fetchJson(serverUrl + "/livemap/data/guardtowers.json");

                String combined = "{"
                        + "\"players\":"  + extractArrayBody(players,  "players")     + ","
                        + "\"villages\":" + extractArrayBody(villages, "villages")    + ","
                        + "\"altars\":[],"
                        + "\"towers\":"   + extractArrayBody(towers,   "guardtowers")
                        + "}";

                if (callback != null) {
                    callback.accept(combined);
                    logger.fine("[LiveMap] Fetched map data: " + combined.length() + " bytes");
                }
            } catch (Exception e) {
                logger.log(Level.WARNING, "[LiveMap] Failed to fetch map data", e);
            }
        }, "LiveMap-DataFetcher").start();
    }

    /**
     * Pulls the named array out of a tiny flat object like
     * {@code {"players":[...]}} and returns it as a literal JSON array
     * string (with brackets). Returns {@code "[]"} when the source is
     * null/empty or the key is missing — keeps the combined output valid
     * even if one of the three endpoints fails.
     */
    private static String extractArrayBody(String json, String key) {
        if (json == null || json.isEmpty()) return "[]";
        int keyIdx = json.indexOf("\"" + key + "\"");
        if (keyIdx < 0) return "[]";
        int open = json.indexOf('[', keyIdx);
        if (open < 0) return "[]";
        int depth = 0;
        for (int i = open; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '[') depth++;
            else if (c == ']') {
                depth--;
                if (depth == 0) return json.substring(open, i + 1);
            }
        }
        return "[]";
    }

    /**
     * Fetch a map tile from server (async).
     *
     * @param zoom zoom level (0-5)
     * @param tileX tile X coordinate
     * @param tileY tile Y coordinate
     * @param callback called with tile data when fetch completes
     */
    public void fetchTileAsync(int zoom, int tileX, int tileY, TileCallback callback) {
        new Thread(() -> {
            byte[] pngData = null;
            try {
                // Server emits a single-zoom static tile grid; the zoom
                // parameter is kept for callback signature compatibility but
                // not used in the URL.
                String url = String.format("%s/livemap/images/%d-%d.png", serverUrl, tileX, tileY);
                pngData = fetchBytes(url);

                if (pngData != null && pngData.length > 0) {
                    logger.fine(String.format("[LiveMap] Fetched tile %d/%d/%d: %d bytes", zoom, tileX, tileY, pngData.length));
                } else {
                    logger.warning(String.format("[LiveMap] Empty tile response for %d/%d/%d", zoom, tileX, tileY));
                }
            } catch (Exception e) {
                logger.log(Level.WARNING, String.format("[LiveMap] Failed to fetch tile %d/%d/%d", zoom, tileX, tileY), e);
            } finally {
                // Always notify — the caller relies on this to clear its in-flight
                // dedupe set. Pass null on failure so success can still be distinguished.
                if (callback != null) {
                    try {
                        callback.onTileReceived(zoom, tileX, tileY, pngData);
                    } catch (Throwable t) {
                        logger.log(Level.WARNING, "[LiveMap] Tile callback threw", t);
                    }
                }
            }
        }, String.format("LiveMap-TileFetcher-%d-%d-%d", zoom, tileX, tileY)).start();
    }

    /**
     * Callback interface for tile fetching.
     */
    @FunctionalInterface
    public interface TileCallback {
        void onTileReceived(int zoom, int tileX, int tileY, byte[] tileData);
    }

    /**
     * Fetch JSON from URL (blocking).
     */
    private String fetchJson(String urlString) {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(urlString);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(TIMEOUT_MS);
            conn.setReadTimeout(TIMEOUT_MS);

            int responseCode = conn.getResponseCode();
            if (responseCode != 200) {
                logger.warning("[LiveMap] HTTP " + responseCode + " for: " + urlString);
                return null;
            }

            BufferedReader reader = new BufferedReader(
                new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8)
            );

            StringBuilder result = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                result.append(line);
            }

            return result.toString();

        } catch (Exception e) {
            logger.log(Level.WARNING, "[LiveMap] Error fetching: " + urlString, e);
            return null;
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    /**
     * Fetch bytes from URL (blocking).
     */
    private byte[] fetchBytes(String urlString) {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(urlString);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(TIMEOUT_MS);
            conn.setReadTimeout(TIMEOUT_MS);

            int responseCode = conn.getResponseCode();
            if (responseCode != 200) {
                logger.warning("[LiveMap] HTTP " + responseCode + " for: " + urlString);
                return null;
            }

            InputStream is = conn.getInputStream();
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();

            byte[] chunk = new byte[4096];
            int bytesRead;
            while ((bytesRead = is.read(chunk)) != -1) {
                buffer.write(chunk, 0, bytesRead);
            }

            return buffer.toByteArray();

        } catch (Exception e) {
            logger.log(Level.WARNING, "[LiveMap] Error fetching: " + urlString, e);
            return null;
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }
}
