package com.garward.wurmmodloader.mods.livemap;

import com.garward.wurmmodloader.mods.livemap.gui.LiveMapWindow;
import com.garward.wurmmodloader.mods.livemap.gui.LiveMinimap;
import com.garward.wurmmodloader.client.api.events.base.SubscribeEvent;
import com.garward.wurmmodloader.client.api.events.lifecycle.ClientWorldLoadedEvent;
import com.garward.wurmmodloader.client.api.events.lifecycle.ServerInfoAvailableEvent;
import com.garward.wurmmodloader.client.api.events.map.ClientHUDInitializedEvent;
import com.garward.wurmmodloader.client.api.gui.ModHud;
import com.garward.wurmmodloader.client.api.serverinfo.ServerInfoRegistry;

import java.util.Timer;
import java.util.TimerTask;
import java.util.logging.Logger;

/**
 * Modern event-driven live map client mod.
 *
 * <p>This mod connects to the server's HTTP-based live map API to display:
 * <ul>
 *   <li>Real-time rendered map tiles (no client-side rendering needed)</li>
 *   <li>Live player positions</li>
 *   <li>Village boundaries and names</li>
 *   <li>Altar locations</li>
 * </ul>
 *
 * <p><b>Architecture:</b>
 * <ol>
 *   <li>Subscribes to ClientWorldLoadedEvent to detect server connection</li>
 *   <li>Subscribes to ClientHUDInitializedEvent to add map window to HUD</li>
 *   <li>Uses MapHttpClient to fetch tiles and data from server</li>
 *   <li>Uses MapDataCache to cache tiles and player data</li>
 *   <li>Callbacks handle HTTP responses to update display</li>
 * </ol>
 *
 * @since 0.2.0
 */
public class LiveMapClientMod {

    private static final Logger logger = Logger.getLogger(LiveMapClientMod.class.getName());

    // Configuration
    private static final long TILE_CACHE_TTL_MS = 30 * 60 * 1000; // 30 minutes
    private static final long DATA_CACHE_TTL_MS = 30 * 1000; // 30 seconds
    private static final long DATA_POLL_INTERVAL_MS = 15 * 1000; // 15 seconds

    // Components
    private MapHttpClient httpClient;
    private MapDataCache dataCache;
    private Timer dataPollTimer;

    // GUI Components
    private LiveMapWindow fullMapWindow;
    private LiveMinimap minimap;

    // State
    private boolean initialized = false;
    private String currentMapData = null;
    private final java.util.Set<String> inFlightTiles = java.util.concurrent.ConcurrentHashMap.newKeySet();
    /** Tile key -> wall-clock millis when the last fetch failed. Used to throttle retries
     *  on genuinely missing/erroring tiles without permanently locking them out. */
    private final java.util.concurrent.ConcurrentHashMap<String, Long> tileFailureBackoff =
        new java.util.concurrent.ConcurrentHashMap<>();
    private static final long TILE_FAILURE_BACKOFF_MS = 10_000L;

    // Server-provided map dimensions (applied to renderers once both config + GUI exist)
    private int serverMapSize = -1;
    private int serverTileSize = -1;
    private int serverMinZoom = Integer.MIN_VALUE;
    private int serverMaxZoom = Integer.MIN_VALUE;

    // Local player handle — captured at HUD init so the polling thread can
    // scope its data request to the viewer's village.
    private com.wurmonline.client.game.World world;

    /**
     * Handle world loaded event. If the server has already reported its HTTP
     * endpoint (via the {@code wml.serverinfo} ModComm channel), initialize
     * the HTTP client now; otherwise wait for {@link ServerInfoAvailableEvent}.
     */
    @SubscribeEvent
    public void onWorldLoaded(ClientWorldLoadedEvent event) {
        // Cache is local-only; always available so the GUI can render from it
        // even before we know the server's HTTP endpoint.
        if (dataCache == null) {
            dataCache = new MapDataCache(TILE_CACHE_TTL_MS, DATA_CACHE_TTL_MS);
        }
        if (ServerInfoRegistry.isAvailable()) {
            String httpUri = ServerInfoRegistry.getHttpUri();
            logger.info("[LiveMap] World loaded; server info already available (" + httpUri + ")");
            initializeHttpClient(httpUri);
        } else {
            logger.info("[LiveMap] World loaded; waiting for ServerInfoAvailableEvent…");
        }
    }

    /**
     * Fired when the server advertises its HTTP endpoint via ModComm. This is
     * the normal path on login — world-load may race ahead of it.
     */
    @SubscribeEvent
    public void onServerInfoAvailable(ServerInfoAvailableEvent event) {
        if (initialized) {
            return;
        }
        logger.info("[LiveMap] Server info received: httpUri=" + event.getHttpUri()
            + ", modloader=" + event.getModloaderVersion());
        initializeHttpClient(event.getHttpUri());
    }

    private synchronized void initializeHttpClient(String serverUrl) {
        if (initialized) {
            return;
        }
        if (serverUrl == null || serverUrl.isEmpty()) {
            logger.warning("[LiveMap] Server did not expose an HTTP URI — live map disabled");
            return;
        }
        try {
            httpClient = new MapHttpClient(serverUrl);
            if (dataCache == null) {
                dataCache = new MapDataCache(TILE_CACHE_TTL_MS, DATA_CACHE_TTL_MS);
            }

            String config = httpClient.fetchConfig();
            if (config != null) {
                logger.info("[LiveMap] Server supports live map! Config: " + config);
                applyServerConfig(config);
                startDataPolling();
                initialized = true;
            } else {
                logger.warning("[LiveMap] Server does not support live map API");
            }
        } catch (Exception e) {
            logger.warning("[LiveMap] Failed to initialize: " + e.getMessage());
        }
    }

    /**
     * Handle HUD initialized event - add map components to HUD.
     */
    @SubscribeEvent
    public void onHUDInitialized(ClientHUDInitializedEvent event) {
        // Always register the map window + minimap + menu entry so the button
        // is visible even before (or without) the server announcing its HTTP
        // endpoint. Tile/data fetching is gated separately on initializeHttpClient.
        if (dataCache == null) {
            dataCache = new MapDataCache(TILE_CACHE_TTL_MS, DATA_CACHE_TTL_MS);
        }

        try {
            this.world = (com.wurmonline.client.game.World) event.getWorld();
            com.wurmonline.client.game.World world = this.world;
            com.wurmonline.client.renderer.gui.MainMenu mainMenu =
                (com.wurmonline.client.renderer.gui.MainMenu) event.getMainMenu();

            logger.info("[LiveMap] Creating map GUI components...");

            fullMapWindow = new LiveMapWindow(world, this, dataCache);
            minimap = new LiveMinimap(world, this, dataCache);
            minimap.positionInCorner(event.getScreenWidth(), event.getScreenHeight());

            mainMenu.registerComponent("Live Map", fullMapWindow);
            mainMenu.registerComponent("Live Minimap", minimap);

            ModHud hud = ModHud.get();
            hud.register(fullMapWindow);
            hud.register(minimap);
            hud.rememberPosition(fullMapWindow, "livemapwindow");

            // If config already arrived, push it to the renderers now.
            propagateMapConfig();

            logger.info("[LiveMap] GUI components created and registered successfully!");

        } catch (Exception e) {
            logger.severe("[LiveMap] Failed to initialize GUI: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Start polling for live map data (players, villages, altars).
     */
    private void startDataPolling() {
        if (dataPollTimer != null) {
            dataPollTimer.cancel();
        }

        dataPollTimer = new Timer("LiveMap-DataPoll", true);
        dataPollTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                if (httpClient != null) {
                    String viewer = null;
                    try {
                        if (world != null && world.getPlayer() != null) {
                            viewer = world.getPlayer().getPlayerName();
                        }
                    } catch (Throwable ignored) {}
                    // Fetch data asynchronously with callback
                    httpClient.fetchDataAsync(viewer, jsonData -> {
                        // Cache the data
                        dataCache.cacheMapData(jsonData);
                        currentMapData = jsonData;

                        logger.fine("[LiveMap] Received map data: " + jsonData.length() + " bytes");
                    });
                }
            }
        }, 1000, DATA_POLL_INTERVAL_MS); // Start after 1 second, repeat every 5 seconds

        logger.info("[LiveMap] Started polling for live data every " + (DATA_POLL_INTERVAL_MS / 1000) + " seconds");
    }

    /**
     * Stop polling for live map data.
     */
    private void stopDataPolling() {
        if (dataPollTimer != null) {
            dataPollTimer.cancel();
            dataPollTimer = null;
            logger.info("[LiveMap] Stopped polling for live data");
        }
    }

    /**
     * Request a specific tile from the server (called by GUI components).
     *
     * <p>This method can be called by the map window/minimap when they need to display
     * a specific tile. The tile will be fetched asynchronously and cached.
     *
     * @param zoom zoom level (0-5)
     * @param tileX tile X coordinate
     * @param tileY tile Y coordinate
     */
    public void requestTile(int zoom, int tileX, int tileY) {
        if (httpClient == null) {
            logger.warning("[LiveMap] Cannot request tile - not initialized");
            return;
        }

        if (dataCache.getTile(zoom, tileX, tileY) != null) {
            return;
        }

        String key = zoom + "/" + tileX + "/" + tileY;

        // Skip recently-failed tiles to avoid hammering the server, but only briefly —
        // permanent lockout was the bug we're fixing here.
        Long lastFailure = tileFailureBackoff.get(key);
        if (lastFailure != null
                && System.currentTimeMillis() - lastFailure < TILE_FAILURE_BACKOFF_MS) {
            return;
        }

        if (!inFlightTiles.add(key)) {
            return;
        }

        httpClient.fetchTileAsync(zoom, tileX, tileY, (z, x, y, tileData) -> {
            try {
                if (tileData != null && tileData.length > 0) {
                    dataCache.cacheTile(z, x, y, tileData);
                    tileFailureBackoff.remove(key);
                } else {
                    tileFailureBackoff.put(key, System.currentTimeMillis());
                }
            } finally {
                // Always clear the in-flight flag so a future request (after backoff,
                // cache eviction, or terrain change) can actually be issued.
                inFlightTiles.remove(key);
            }
        });
    }

    /**
     * Get the current cached map data (players, villages, altars).
     *
     * @return JSON data, or null if not available
     */
    public String getCurrentMapData() {
        return currentMapData;
    }

    /**
     * Parse {@code /livemap/api/config} JSON and remember mapSize/tileSize.
     * Extraction is regex-based to avoid a JSON dependency — server response
     * is a tiny flat object like {@code {"mapSize":8192,"tileSize":256,...}}.
     */
    private void applyServerConfig(String json) {
        if (json == null) return;
        try {
            int ms = extractInt(json, "mapSize");
            int ts = extractInt(json, "tileSize");
            int minZ = extractSignedInt(json, "minZoom");
            int maxZ = extractSignedInt(json, "maxZoom");
            if (ms > 0 && ts > 0) {
                serverMapSize = ms;
                serverTileSize = ts;
                if (minZ != Integer.MIN_VALUE) serverMinZoom = minZ;
                if (maxZ != Integer.MIN_VALUE) serverMaxZoom = maxZ;
                logger.info("[LiveMap] Server map config: mapSize=" + ms + ", tileSize=" + ts
                        + ", zoom=[" + serverMinZoom + ".." + serverMaxZoom + "]");
                propagateMapConfig();
            } else {
                logger.warning("[LiveMap] Could not parse mapSize/tileSize from: " + json);
            }
        } catch (Exception e) {
            logger.warning("[LiveMap] Failed to apply server config: " + e.getMessage());
        }
    }

    private static int extractInt(String json, String key) {
        java.util.regex.Matcher m = java.util.regex.Pattern
            .compile("\"" + java.util.regex.Pattern.quote(key) + "\"\\s*:\\s*(\\d+)")
            .matcher(json);
        return m.find() ? Integer.parseInt(m.group(1)) : -1;
    }

    private static int extractSignedInt(String json, String key) {
        java.util.regex.Matcher m = java.util.regex.Pattern
            .compile("\"" + java.util.regex.Pattern.quote(key) + "\"\\s*:\\s*(-?\\d+)")
            .matcher(json);
        return m.find() ? Integer.parseInt(m.group(1)) : Integer.MIN_VALUE;
    }

    private void propagateMapConfig() {
        if (serverMapSize <= 0 || serverTileSize <= 0) return;
        if (fullMapWindow != null) {
            fullMapWindow.getRenderer().setMapConfig(serverMapSize, serverTileSize);
            if (serverMinZoom != Integer.MIN_VALUE && serverMaxZoom != Integer.MIN_VALUE) {
                fullMapWindow.getRenderer().setZoomRange(serverMinZoom, serverMaxZoom);
            }
        }
        if (minimap != null) {
            minimap.getRenderer().setMapConfig(serverMapSize, serverTileSize);
            if (serverMinZoom != Integer.MIN_VALUE && serverMaxZoom != Integer.MIN_VALUE) {
                minimap.getRenderer().setZoomRange(serverMinZoom, serverMaxZoom);
            }
        }
    }
}
