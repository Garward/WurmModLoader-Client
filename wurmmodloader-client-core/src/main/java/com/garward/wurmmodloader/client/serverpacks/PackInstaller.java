package com.garward.wurmmodloader.client.serverpacks;

import com.wurmonline.client.WurmClientBase;
import com.wurmonline.client.renderer.ItemColorsXml;
import com.wurmonline.client.renderer.effects.CustomParticleEffectXml;
import com.wurmonline.client.renderer.terrain.TerrainTexture;
import com.wurmonline.client.renderer.terrain.TilePropertiesXml;
import com.wurmonline.client.resources.Resources;
import com.wurmonline.client.resources.ResourceUrl;
import com.wurmonline.client.resources.textures.IconLoader;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.jar.JarEntry;
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

    private static final Map<String, Object> addedPacks = new ConcurrentHashMap<>();

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

        ServerPacksClientService.consoleOutput("[ServerPacks] reflection cache primed");
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

            boolean prepend = hasFlag(packUrl, "prepend");

            int positionAdded;
            int totalAfter;
            synchronized (resources) {
                if (prepend) {
                    packs.add(0, jarPack);
                    positionAdded = 0;
                } else {
                    positionAdded = packs.size();
                    packs.add(jarPack);
                }
                totalAfter = packs.size();
                reloadPacks(resources);
            }

            addedPacks.put(file.getAbsoluteFile().toString(), jarPack);
            String query = (packUrl != null && packUrl.getQuery() != null) ? packUrl.getQuery() : "";
            String mappingSummary = summariseJarMappings(file);
            logger.info(String.format(
                "[PackInstaller] Added pack %s (file=%s) query=[%s] prepend=%s position=%d/%d %s",
                packId, file.getName(), query, prepend, positionAdded, totalAfter, mappingSummary));
            logPackOrder();

            scheduleReloadResources();
        } catch (Throwable t) {
            logger.log(Level.SEVERE, "[PackInstaller] failed to enable pack " + packId, t);
        }
    }

    static void closePack(Path packFile) {
        try {
            if (resourcesPacks == null) return;
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

    static void reloadAll() {
        try {
            Resources resources = WurmClientBase.getResourceManager();
            if (resources == null) { logger.warning("[PackInstaller] no resource manager"); return; }
            synchronized (resources) {
                reloadPacks(resources);
            }
            reloadResources();
            ServerPacksClientService.consoleOutput("[ServerPacks] manual reload complete");
        } catch (Throwable t) {
            logger.log(Level.WARNING, "[PackInstaller] reloadAll failed", t);
        }
    }

    private static synchronized void scheduleReloadResources() {
        if (pendingReload != null) pendingReload.cancel(false);
        pendingReload = reloadScheduler.schedule(() -> {
            try { reloadResources(); }
            catch (Throwable t) { logger.log(Level.WARNING, "[PackInstaller] debounced reload failed", t); }
        }, 1500, TimeUnit.MILLISECONDS);
    }

    static void dumpIconSheets() {
        try {
            Class<?> iconConstants = Class.forName("com.wurmonline.shared.constants.IconConstants");
            String[] sheetKeys = (String[]) iconConstants.getField("ICON_SHEET_FILE_NAMES").get(null);
            Resources resources = WurmClientBase.getResourceManager();
            if (resources == null) {
                logger.warning("[PackInstaller] dumpIconSheets: no resource manager");
                return;
            }
            StringBuilder sb = new StringBuilder("[PackInstaller] icon sheet resolution:");
            for (String key : sheetKeys) {
                ResourceUrl url = resources.getResource(key);
                String owner = "UNRESOLVED";
                if (url != null) {
                    if (packResourceUrlClass.isInstance(url)) {
                        Object pack = packResourceUrlPack.get(url);
                        if (jarPackClass.isInstance(pack)) {
                            JarFile jf = (JarFile) jarPackJarFile.get(pack);
                            owner = jf != null ? new File(jf.getName()).getName() : "?jar";
                        } else if (pack != null) {
                            owner = pack.getClass().getSimpleName();
                        }
                    } else {
                        owner = url.getClass().getSimpleName();
                    }
                }
                sb.append("\n  ").append(key).append(" → ").append(owner);
            }
            logger.info(sb.toString());
        } catch (Throwable t) {
            logger.log(Level.WARNING, "[PackInstaller] dumpIconSheets failed", t);
        }
    }

    static void dumpPacks() {
        try {
            Resources resources = WurmClientBase.getResourceManager();
            if (resources == null) { logger.warning("[PackInstaller] no resource manager"); return; }
            @SuppressWarnings("unchecked")
            List<Object> packs = (List<Object>) resourcesPacks.get(resources);
            ServerPacksClientService.consoleOutput("[ServerPacks] " + packs.size() + " packs (lookup order):");
            int i = 0;
            for (Object p : packs) {
                String jar = "";
                try {
                    if (jarPackClass.isInstance(p)) {
                        JarFile jf = (JarFile) jarPackJarFile.get(p);
                        if (jf != null) jar = " jar=" + jf.getName();
                    }
                } catch (Throwable ignored) {}
                ServerPacksClientService.consoleOutput("  [" + (i++) + "] " + p.getClass().getSimpleName() + jar);
            }
        } catch (Throwable t) {
            logger.log(Level.WARNING, "[PackInstaller] dumpPacks failed", t);
        }
    }

    static void probe(String key) {
        try {
            Resources resources = WurmClientBase.getResourceManager();
            if (resources == null) { logger.warning("[PackInstaller] no resource manager"); return; }
            ServerPacksClientService.consoleOutput("[ServerPacks] probe key=" + key);
            try {
                Method getAllState = Resources.class.getMethod("getAllState", String.class);
                getAllState.invoke(resources, key);
            } catch (NoSuchMethodException nsme) {
                ServerPacksClientService.consoleOutput("[ServerPacks] getAllState missing, falling back to getResource");
            }
            ResourceUrl url = resources.getResource(key);
            if (url == null) {
                ServerPacksClientService.consoleOutput("[ServerPacks]   → unresolved (no pack owns '" + key + "')");
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
            ServerPacksClientService.consoleOutput("[ServerPacks]   → " + url + " (owner=" + owner + ")");
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
        try { IconLoader.clear(); IconLoader.initIcons(); }
        catch (Throwable t) { logger.log(Level.FINE, "icons reload", t); }
        dumpIconSheets();
    }

    private static String summariseJarMappings(File file) {
        if (file == null || !file.isFile()) return "(no file)";
        int entries = 0;
        List<String> mappingLines = new ArrayList<>();
        List<String> topLevelResources = new ArrayList<>();
        try (JarFile jf = new JarFile(file)) {
            Enumeration<JarEntry> en = jf.entries();
            while (en.hasMoreElements()) {
                JarEntry e = en.nextElement();
                if (e.isDirectory()) continue;
                entries++;
                String name = e.getName();
                if ("mappings.txt".equals(name) && mappingLines.isEmpty()) {
                    try (InputStream is = jf.getInputStream(e);
                         BufferedReader br = new BufferedReader(
                                 new InputStreamReader(is, StandardCharsets.UTF_8))) {
                        String line;
                        while ((line = br.readLine()) != null && mappingLines.size() < 8) {
                            String trimmed = line.trim();
                            if (!trimmed.isEmpty() && !trimmed.startsWith("#")) {
                                mappingLines.add(trimmed);
                            }
                        }
                    }
                } else if (!name.contains("/") && topLevelResources.size() < 6) {
                    topLevelResources.add(name);
                }
            }
        } catch (Throwable t) {
            return "(read failed: " + t.getClass().getSimpleName() + ")";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("entries=").append(entries);
        if (!topLevelResources.isEmpty()) {
            sb.append(" top=").append(topLevelResources);
        }
        if (!mappingLines.isEmpty()) {
            sb.append(" mappings=").append(mappingLines.size())
              .append(mappingLines.size() >= 8 ? "+" : "")
              .append(mappingLines);
        }
        return sb.toString();
    }

    private static void logPackOrder() {
        try {
            Resources resources = WurmClientBase.getResourceManager();
            if (resources == null) return;
            @SuppressWarnings("unchecked")
            List<Object> packs = (List<Object>) resourcesPacks.get(resources);
            StringBuilder sb = new StringBuilder("[PackInstaller] lookup order (")
                    .append(packs.size()).append(" packs):");
            int i = 0;
            for (Object p : packs) {
                sb.append("\n  [").append(i++).append("] ");
                if (jarPackClass.isInstance(p)) {
                    JarFile jf = (JarFile) jarPackJarFile.get(p);
                    sb.append(jf != null ? new File(jf.getName()).getName() : "?");
                } else {
                    sb.append(p.getClass().getSimpleName());
                }
            }
            logger.info(sb.toString());
        } catch (Throwable t) {
            logger.log(Level.FINE, "logPackOrder failed", t);
        }
    }

    private static boolean hasFlag(URL url, String flag) {
        if (url == null) return false;
        String query = url.getQuery();
        if (query == null || query.isEmpty()) return false;
        for (String token : query.split("&")) {
            int eq = token.indexOf('=');
            String key = eq < 0 ? token : token.substring(0, eq);
            if (!key.equals(flag)) continue;
            if (eq < 0) return true;
            String val = token.substring(eq + 1);
            return val.equalsIgnoreCase("true") || val.equals("1");
        }
        return false;
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
