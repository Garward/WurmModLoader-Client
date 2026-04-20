package com.garward.mods.serverpacks;

import com.garward.wurmmodloader.client.api.events.base.SubscribeEvent;
import com.garward.wurmmodloader.client.api.events.serverpacks.ServerPackReceivedEvent;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Modern event-driven server packs client mod.
 *
 * <p>This mod replaces the old reflection-heavy implementation with a clean event-based
 * architecture. It subscribes to ServerPackReceivedEvent which is fired when the server
 * sends pack information via ModComm.
 *
 * <p><b>How it works:</b>
 * <ol>
 *   <li>Server sends pack ID + URI via ModComm "ago.serverpacks" channel</li>
 *   <li>ModComm handler fires ServerPackReceivedEvent</li>
 *   <li>This mod receives event via @SubscribeEvent</li>
 *   <li>Downloads pack in background thread</li>
 *   <li>Installs pack to packs/ directory on main thread</li>
 *   <li>Reloads resources and notifies server</li>
 * </ol>
 *
 * @since 0.2.0
 */
public class ServerPacksClientMod {

    private static final Logger logger = Logger.getLogger(ServerPacksClientMod.class.getName());

    /**
     * Handle server pack received event.
     *
     * <p>This is called on the main thread when the server sends pack information.
     * Downloads happen in a background thread to avoid blocking the client.
     */
    @SubscribeEvent
    public void onServerPackReceived(ServerPackReceivedEvent event) {
        String packId = event.getPackId();
        String packUri = event.getPackUri();

        logger.info(String.format("[ServerPacks] Received pack from server: %s (%s)", packId, packUri));

        try {
            URL packUrl = new URL(packUri);
            installServerPack(packId, packUrl);
        } catch (MalformedURLException e) {
            logger.log(Level.SEVERE, String.format("[ServerPacks] Invalid pack URI: %s", packUri), e);
        }
    }

    /**
     * Install a server pack (download if needed, then enable).
     */
    private void installServerPack(String packId, URL packUrl) {
        // Check if pack already exists
        Path packPath = Paths.get("packs", getPackName(packId));
        if (Files.isRegularFile(packPath)) {
            logger.info(String.format("[ServerPacks] Pack '%s' already exists, skipping download", packId));
            enableDownloadedPack(packId, packPath);
            refreshModels();
        } else {
            // Download in background thread
            PackDownloader downloader = new PackDownloader(packUrl, packId, this::handleDownloadComplete);
            new Thread(downloader, "ServerPacks-Downloader-" + packId).start();
        }
    }

    /**
     * Called when pack download completes (on background thread).
     */
    private void handleDownloadComplete(String packId, Path tempFile) {
        // Run installation on main thread using reflection to access ModClient
        try {
            Class<?> modClientClass = Class.forName("org.gotti.wurmunlimited.modsupport.ModClient");
            Method runTaskMethod = modClientClass.getDeclaredMethod("runTask", Runnable.class);

            runTaskMethod.invoke(null, (Runnable) () -> {
                try {
                    Path packFile = Paths.get("packs", getPackName(packId));

                    // Close pack if already open
                    closePack(packFile);

                    // Move temp file to final location
                    Files.move(tempFile, packFile, StandardCopyOption.REPLACE_EXISTING);

                    logger.info(String.format("[ServerPacks] Installed pack: %s", packFile));

                    // Enable the pack
                    enableDownloadedPack(packId, packFile);

                    // Refresh models
                    refreshModels();

                } catch (IOException e) {
                    logger.log(Level.SEVERE, String.format("[ServerPacks] Failed to install pack '%s'", packId), e);
                }
            });
        } catch (Exception e) {
            logger.log(Level.SEVERE, "[ServerPacks] Failed to run installation on main thread", e);
        }
    }

    /**
     * Enable a downloaded pack (add to resource manager and reload resources).
     */
    private void enableDownloadedPack(String packId, Path packFile) {
        try {
            // Use reflection to access ModPacks
            Class<?> modPacksClass = Class.forName("org.gotti.wurmunlimited.modsupport.packs.ModPacks");
            Method addPackMethod = modPacksClass.getDeclaredMethod("addPack", File.class, modPacksClass.getClassLoader().loadClass("org.gotti.wurmunlimited.modsupport.packs.ModPacks$Options[]"));

            // Get OPTIONS_DEFAULT
            Class<?> optionsClass = modPacksClass.getClassLoader().loadClass("org.gotti.wurmunlimited.modsupport.packs.ModPacks$Options");
            Object optionsDefault = java.lang.reflect.Array.newInstance(optionsClass, 0);

            // Add pack
            Boolean added = (Boolean) addPackMethod.invoke(null, packFile.toFile(), optionsDefault);

            if (added) {
                logger.info(String.format("[ServerPacks] Enabled pack: %s", packId));

                // Reload resources
                reloadResources();
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, String.format("[ServerPacks] Failed to enable pack '%s'", packId), e);
        }
    }

    /**
     * Close a pack if it's currently open.
     */
    private void closePack(Path packFile) {
        try {
            Class<?> modPacksClass = Class.forName("org.gotti.wurmunlimited.modsupport.packs.ModPacks");
            Method closePackMethod = modPacksClass.getDeclaredMethod("closePack", File.class);
            closePackMethod.invoke(null, packFile.toFile());
        } catch (Exception e) {
            // Ignore - pack may not be open
        }
    }

    /**
     * Reload client resources after pack installation.
     */
    private void reloadResources() {
        try {
            // Reload particle effects
            Class<?> particleClass = Class.forName("com.wurmonline.client.renderer.effects.CustomParticleEffectXml");
            Method reloadParticles = particleClass.getDeclaredMethod("reloadParticlesFile");
            reloadParticles.invoke(null);

            // Reload item colors
            Class<?> itemColorsClass = Class.forName("com.wurmonline.client.renderer.ItemColorsXml");
            Class<?> worldClass = Class.forName("com.wurmonline.client.game.World");
            Method reloadItemColors = itemColorsClass.getDeclaredMethod("reloadItemColors", worldClass);

            // Get World instance
            Class<?> modClientClass = Class.forName("org.gotti.wurmunlimited.modsupport.ModClient");
            Method getWorldMethod = modClientClass.getDeclaredMethod("getWorld");
            Object world = getWorldMethod.invoke(null);

            reloadItemColors.invoke(null, world);

            // Reload tiles
            Class<?> tilesClass = Class.forName("com.wurmonline.client.renderer.terrain.TilePropertiesXml");
            Method reloadTiles = tilesClass.getDeclaredMethod("reloadTiles");
            reloadTiles.invoke(null);

            // Reload terrain normals
            Class<?> terrainClass = Class.forName("com.wurmonline.client.renderer.terrain.TerrainTexture");
            Method reloadNormals = terrainClass.getDeclaredMethod("reloadNormalMaps");
            reloadNormals.invoke(null);

            logger.info("[ServerPacks] Resources reloaded");

        } catch (Exception e) {
            logger.log(Level.WARNING, "[ServerPacks] Failed to reload resources", e);
        }
    }

    /**
     * Notify server to refresh models (via ModComm).
     */
    private void refreshModels() {
        // TODO: Send CMD_REFRESH to server via ModComm
        // This will require ModComm client implementation
        logger.info("[ServerPacks] Refresh models (not yet implemented)");
    }

    /**
     * Get the pack file name for a pack ID.
     */
    private String getPackName(String packId) {
        return packId + ".jar";
    }
}
