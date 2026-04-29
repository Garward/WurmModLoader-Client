package com.garward.mods.serverpacks;

import com.wurmonline.client.WurmClientBase;
import com.wurmonline.client.renderer.ItemColorsXml;
import com.wurmonline.client.renderer.effects.CustomParticleEffectXml;
import com.wurmonline.client.renderer.terrain.TerrainTexture;
import com.wurmonline.client.renderer.terrain.TilePropertiesXml;
import com.wurmonline.client.resources.Resources;
import com.wurmonline.client.resources.ResourceUrl;
import com.wurmonline.client.resources.textures.IconLoader;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.jar.JarFile;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Reflective bridge that splices a downloaded pack file into Wurm's
 * {@link Resources} at runtime. Mirrors
 * {@code org.gotti.wurmunlimited.modsupport.packs.ModPacks} without depending on
 * the legacy launcher's modsupport jar.
 */
final class PackInstaller {

    private static final Logger logger = Logger.getLogger(PackInstaller.class.getName());

    private static Class<?> jarPackClass;
    private static Constructor<?> jarPackCtor;
    private static Method jarPackInit;
    private static Field jarPackJarFile;

    private static Field resourcesPacks;
    private static Field resourcesResolved;
    private static Field resourcesUnresolved;

    private static Class<?> packResourceUrlClass;
    private static Field packResourceUrlPack;

    // absolute-path → JarPack instance; used by closePack() to undo add.
    private static final Map<String, Object> addedPacks = new ConcurrentHashMap<>();

    // Debounce reloadResources() — a burst of packs arriving back-to-back
    // would otherwise call IconLoader.clear() once per pack, blowing the GL
    // texture cache N times and stalling the render thread.
    private static final ScheduledExecutorService reloadScheduler =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "PackInstaller-reload");
                t.setDaemon(true);
                return t;
            });
    private static ScheduledFuture<?> pendingReload;

    private PackInstaller() {}

    static synchronized void init() throws ReflectiveOperationException {
        if (jarPackClass != null) return;

        jarPackClass = Class.forName("com.wurmonline.client.resources.JarPack");
        jarPackCtor = jarPackClass.getDeclaredConstructor(File.class);
        jarPackCtor.setAccessible(true);
        jarPackInit = jarPackClass.getSuperclass().getDeclaredMethod("init", Resources.class);
        jarPackInit.setAccessible(true);
        jarPackJarFile = jarPackClass.getDeclaredField("jarFile");
        jarPackJarFile.setAccessible(true);

        resourcesPacks = Resources.class.getDeclaredField("packs");
        resourcesPacks.setAccessible(true);
        resourcesResolved = Resources.class.getDeclaredField("resolvedResources");
        resourcesResolved.setAccessible(true);
        resourcesUnresolved = Resources.class.getDeclaredField("unresolvedResources");
        resourcesUnresolved.setAccessible(true);

        packResourceUrlClass = Class.forName("com.wurmonline.client.resources.PackResourceUrl");
        packResourceUrlPack = packResourceUrlClass.getDeclaredField("pack");
        packResourceUrlPack.setAccessible(true);

        ServerPacksClientMod.consoleOutput("[ServerPacks] reflection cache primed");
    }

    static void enableDownloadedPack(String packId, URL packUrl, Path packFile) {
        try {
            File file = packFile.toFile();
            Resources resources = WurmClientBase.getResourceManager();
            if (resources == null) {
                logger.warning("[PackInstaller] resource manager not ready; deferring pack " + packId);
                return;
            }

            Object jarPack = jarPackCtor.newInstance(file);
            jarPackInit.invoke(jarPack, resources);

            @SuppressWarnings("unchecked")
            List<Object> packs = (List<Object>) resourcesPacks.get(resources);

            boolean prepend = packUrl != null && packUrl.getQuery() != null
                    && (packUrl.getQuery().contains("prepend=true") || packUrl.getQuery().contains("prepend=1"));

            synchronized (resources) {
                if (prepend) packs.add(0, jarPack);
                else packs.add(jarPack);
                reloadPacks(resources);
            }

            addedPacks.put(file.getAbsoluteFile().toString(), jarPack);
            logger.info(String.format("[PackInstaller] Added server pack %s (%s)", packId, file.getName()));

            scheduleReloadResources();
        } catch (Throwable t) {
            logger.log(Level.SEVERE, "[PackInstaller] failed to enable pack " + packId, t);
        }
    }

    static void closePack(Path packFile) {
        try {
            if (resourcesPacks == null) return; // not initialized yet
            File file = packFile.toFile();
            Object jarPack = addedPacks.remove(file.getAbsoluteFile().toString());
            if (jarPack == null) return;

            Resources resources = WurmClientBase.getResourceManager();
            if (resources == null) return;

            JarFile jf = (JarFile) jarPackJarFile.get(jarPack);
            try { jf.close(); } catch (Throwable ignored) {}

            @SuppressWarnings("unchecked")
            List<Object> packs = (List<Object>) resourcesPacks.get(resources);
            packs.remove(jarPack);

            @SuppressWarnings("unchecked")
            Map<String, ResourceUrl> resolved = (Map<String, ResourceUrl>) resourcesResolved.get(resources);
            Map<String, ResourceUrl> oldResolved = new HashMap<>(resolved);
            for (Map.Entry<String, ResourceUrl> e : oldResolved.entrySet()) {
                ResourceUrl url = e.getValue();
                if (packResourceUrlClass.isInstance(url)
                        && packResourceUrlPack.get(url) == jarPack) {
                    resolved.remove(e.getKey());
                    resources.getResource(e.getKey());
                }
            }
        } catch (Throwable t) {
            logger.log(Level.WARNING, "[PackInstaller] failed to close pack " + packFile, t);
        }
    }

    /**
     * Force-reload: clear resolved/unresolved caches, re-resolve every key,
     * then reload XML-driven subsystems (particles, item colors, tiles,
     * terrain). Useful after a batch of server packs finishes downloading
     * late and the world has already rendered with fallbacks.
     */
    static void reloadAll() {
        try {
            Resources resources = WurmClientBase.getResourceManager();
            if (resources == null) { logger.warning("[PackInstaller] no resource manager"); return; }
            synchronized (resources) {
                reloadPacks(resources);
            }
            reloadResources();
            ServerPacksClientMod.consoleOutput("[ServerPacks] manual reload complete");
        } catch (Throwable t) {
            logger.log(Level.WARNING, "[PackInstaller] reloadAll failed", t);
        }
    }

    /**
     * Coalesce reload calls inside a 1.5 s window. Multiple packs arriving
     * back-to-back during login/handshake would otherwise call
     * {@link IconLoader#clear()} per pack, forcing the render thread to
     * re-upload every visible icon as a GL texture each time and stalling
     * the client. The latest call wins; resources reload once after the
     * burst settles.
     */
    private static synchronized void scheduleReloadResources() {
        if (pendingReload != null) pendingReload.cancel(false);
        pendingReload = reloadScheduler.schedule(() -> {
            try { reloadResources(); }
            catch (Throwable t) { logger.log(Level.WARNING, "[PackInstaller] debounced reload failed", t); }
        }, 1500, TimeUnit.MILLISECONDS);
    }

    static void dumpPacks() {
        try {
            Resources resources = WurmClientBase.getResourceManager();
            if (resources == null) { logger.warning("[PackInstaller] no resource manager"); return; }
            @SuppressWarnings("unchecked")
            List<Object> packs = (List<Object>) resourcesPacks.get(resources);
            ServerPacksClientMod.consoleOutput("[ServerPacks] " + packs.size() + " packs (lookup order):");
            int i = 0;
            for (Object p : packs) {
                String jar = "";
                try {
                    if (jarPackClass.isInstance(p)) {
                        JarFile jf = (JarFile) jarPackJarFile.get(p);
                        if (jf != null) jar = " jar=" + jf.getName();
                    }
                } catch (Throwable ignored) {}
                ServerPacksClientMod.consoleOutput("  [" + (i++) + "] " + p.getClass().getSimpleName() + jar);
            }
        } catch (Throwable t) {
            logger.log(Level.WARNING, "[PackInstaller] dumpPacks failed", t);
        }
    }

    static void probe(String key) {
        try {
            Resources resources = WurmClientBase.getResourceManager();
            if (resources == null) { logger.warning("[PackInstaller] no resource manager"); return; }
            ServerPacksClientMod.consoleOutput("[ServerPacks] probe key=" + key);
            try {
                Method getAllState = Resources.class.getMethod("getAllState", String.class);
                getAllState.invoke(resources, key);
            } catch (NoSuchMethodException nsme) {
                ServerPacksClientMod.consoleOutput("[ServerPacks] getAllState missing, falling back to getResource");
            }
            ResourceUrl url = resources.getResource(key);
            if (url == null) {
                ServerPacksClientMod.consoleOutput("[ServerPacks]   → unresolved (no pack owns '" + key + "')");
                return;
            }
            String owner = "unknown";
            if (packResourceUrlClass.isInstance(url)) {
                Object pack = packResourceUrlPack.get(url);
                if (jarPackClass.isInstance(pack)) {
                    JarFile jf = (JarFile) jarPackJarFile.get(pack);
                    if (jf != null) owner = jf.getName();
                } else if (pack != null) {
                    owner = pack.getClass().getSimpleName();
                }
            } else {
                owner = url.getClass().getSimpleName();
            }
            ServerPacksClientMod.consoleOutput("[ServerPacks]   → " + url + " (owner=" + owner + ")");
        } catch (Throwable t) {
            logger.log(Level.WARNING, "[PackInstaller] probe failed", t);
        }
    }

    private static void reloadPacks(Resources resources) throws IllegalAccessException {
        @SuppressWarnings("unchecked")
        Set<String> unresolved = (Set<String>) resourcesUnresolved.get(resources);
        @SuppressWarnings("unchecked")
        Map<String, ResourceUrl> resolved = (Map<String, ResourceUrl>) resourcesResolved.get(resources);

        Map<String, ResourceUrl> old = new HashMap<>(resolved);
        unresolved.clear();
        resolved.clear();
        for (String key : old.keySet()) {
            resources.getResource(key);
        }
    }

    private static void reloadResources() {
        try { CustomParticleEffectXml.reloadParticlesFile(); } catch (Throwable t) { logger.log(Level.FINE, "particles reload", t); }
        try { ItemColorsXml.reloadItemColors(getWorld()); } catch (Throwable t) { logger.log(Level.FINE, "item colors reload", t); }
        try { TilePropertiesXml.reloadTiles(); } catch (Throwable t) { logger.log(Level.FINE, "tiles reload", t); }
        try { TerrainTexture.reloadNormalMaps(); } catch (Throwable t) { logger.log(Level.FINE, "terrain reload", t); }
        // Re-read icon sheets (img.iconsheet.*) so the next getIcon() call slices
        // from the new pack's PNGs. Covers both framework graphics.jar replacements
        // and Ago-style iconzz-pack.jar deliveries — both ship sheet PNGs + a
        // mappings.txt entry for img.iconsheet.icons et al.
        try { IconLoader.clear(); IconLoader.initIcons(); }
        catch (Throwable t) { logger.log(Level.FINE, "icons reload", t); }
    }

    private static com.wurmonline.client.game.World getWorld() throws ReflectiveOperationException {
        Field clientObject = WurmClientBase.class.getDeclaredField("clientObject");
        clientObject.setAccessible(true);
        Object client = clientObject.get(null);
        if (client == null) return null;
        Field worldField = WurmClientBase.class.getDeclaredField("world");
        worldField.setAccessible(true);
        return (com.wurmonline.client.game.World) worldField.get(client);
    }
}
