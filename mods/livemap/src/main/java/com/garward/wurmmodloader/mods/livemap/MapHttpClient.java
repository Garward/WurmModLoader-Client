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
 *   <li>/livemap/api/config - Map configuration (size, tile size, max zoom)</li>
 *   <li>/livemap/api/data - Live data (players, villages, altars)</li>
 *   <li>/livemap/tile/{z}/{x}/{y}.png - Map tiles</li>
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
            String url = serverUrl + "/livemap/api/config";
            return fetchJson(url);
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
     * Fetch live map data scoped to a viewer. The server filters the
     * {@code players[]} array to fellow villagers of {@code viewerName}; when
     * {@code viewerName} is null, no players are returned.
     */
    public void fetchDataAsync(String viewerName, Consumer<String> callback) {
        new Thread(() -> {
            try {
                String url;
                if (viewerName == null || viewerName.isEmpty()) {
                    url = serverUrl + "/livemap/api/data";
                } else {
                    url = serverUrl + "/livemap/api/data/me/"
                        + java.net.URLEncoder.encode(viewerName, "UTF-8");
                }
                String json = fetchJson(url);

                if (json != null && callback != null) {
                    callback.accept(json);
                    logger.fine("[LiveMap] Fetched map data: " + json.length() + " bytes");
                }
            } catch (Exception e) {
                logger.log(Level.WARNING, "[LiveMap] Failed to fetch map data", e);
            }
        }, "LiveMap-DataFetcher").start();
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
            try {
                String url = String.format("%s/livemap/tile/%d/%d/%d.png", serverUrl, zoom, tileX, tileY);
                byte[] pngData = fetchBytes(url);

                if (pngData != null && pngData.length > 0 && callback != null) {
                    callback.onTileReceived(zoom, tileX, tileY, pngData);
                    logger.fine(String.format("[LiveMap] Fetched tile %d/%d/%d: %d bytes", zoom, tileX, tileY, pngData.length));
                } else if (pngData == null || pngData.length == 0) {
                    logger.warning(String.format("[LiveMap] Empty tile response for %d/%d/%d", zoom, tileX, tileY));
                }
            } catch (Exception e) {
                logger.log(Level.WARNING, String.format("[LiveMap] Failed to fetch tile %d/%d/%d", zoom, tileX, tileY), e);
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
