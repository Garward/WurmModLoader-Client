package com.garward.wurmmodloader.client.modloader;

import org.gotti.wurmunlimited.modloader.interfaces.WurmClientMod;

import java.io.File;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Loads legacy client mods that implement {@link WurmClientMod} interface.
 *
 * <p>This loader provides compatibility with Ago's WurmClientModLauncher mods.
 * Legacy mods use a lifecycle-based approach (preInit/init) instead of events.
 *
 * <h2>Compatibility Warning:</h2>
 * <p>Legacy mods and modern event-based mods use different architectures and may conflict.
 * It's recommended to use only one type of mod system at a time.
 *
 * @since 0.1.0
 * @see WurmClientMod
 * @see ModLoader for modern event-based mods
 */
public class LegacyModLoader {

    private static final Logger logger = Logger.getLogger(LegacyModLoader.class.getName());
    private final List<WurmClientMod> loadedMods = new ArrayList<>();

    /**
     * Loads all legacy mods from the mods/ directory.
     * Supports two loading modes:
     * 1. Properties-based (canonical): mods/modname.properties + mods/modname/modname.jar
     *    (legacy flat mods/modname.jar still accepted; "classpath=" in .properties overrides)
     * 2. Direct JAR scanning: fallback when no .properties files are present; picks up
     *    both mods/*.jar and mods/<name>/<name>.jar
     *
     * <p>Calls lifecycle methods in order:
     * <ol>
     *   <li>preInit() on all mods</li>
     *   <li>init() on all mods</li>
     * </ol>
     */
    public void loadLegacyMods() {
        String separator = "======================================================================";
        logger.info(separator);
        logger.info("Loading LEGACY mods from mods/ directory...");
        logger.info("(Using Ago's WurmClientMod interface)");
        logger.info(separator);

        File modsDir = new File("mods");
        if (!modsDir.exists()) {
            logger.info("No mods directory found, creating...");
            if (modsDir.mkdir()) {
                logger.info("Created mods/ directory");
            } else {
                logger.warning("Failed to create mods/ directory");
            }
            return;
        }

        if (!modsDir.isDirectory()) {
            logger.warning("mods/ exists but is not a directory!");
            return;
        }

        EnabledRegistry enabled = EnabledRegistry.load(modsDir);
        java.util.Set<String> handled = new java.util.HashSet<>();

        // Phase 0: canonical self-contained subfolder legacy mods
        loadCanonicalSubfolderMods(modsDir, handled, enabled);

        // Phase 1: legacy flat-properties
        File[] propsFiles = modsDir.listFiles((dir, name) -> name.endsWith(".properties"));
        if (propsFiles != null && propsFiles.length > 0) {
            logger.info("Found " + propsFiles.length + " .properties file(s) - properties-based loading");
            logger.info("");
            loadModsFromProperties(modsDir, propsFiles, handled, enabled);
        }

        // Phase 2: unmanaged jars
        loadModsFromJars(modsDir, handled, enabled);

        if (loadedMods.isEmpty()) {
            logger.warning("No legacy mods found");
            return;
        }

        logger.info("");
        logger.info("Calling preInit() on all mods...");

        // Call preInit() on all mods
        for (WurmClientMod mod : loadedMods) {
            try {
                String modName = mod.getClass().getName();
                String version = mod.getVersion();
                logger.info("  preInit: " + modName + (version != null ? " v" + version : ""));
                mod.preInit();
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Error in preInit() for " + mod.getClass().getName(), e);
            }
        }

        logger.info("");
        logger.info("Calling init() on all mods...");

        // Call init() on all mods
        for (WurmClientMod mod : loadedMods) {
            try {
                String modName = mod.getClass().getName();
                logger.info("  init: " + modName);
                mod.init();
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Error in init() for " + mod.getClass().getName(), e);
            }
        }

        logger.info("");
        logger.info(separator);
        logger.info("Loaded " + loadedMods.size() + " legacy mod(s) successfully");
        logger.info(separator);
    }

    /**
     * Loads mods using .properties files (matches server modloader structure).
     */
    private void loadCanonicalSubfolderMods(File modsDir, java.util.Set<String> handled,
                                            EnabledRegistry enabled) {
        File[] subdirs = modsDir.listFiles(File::isDirectory);
        if (subdirs == null) return;
        for (File subDir : subdirs) {
            String modName = subDir.getName();
            if (handled.contains(modName)) continue;
            File modJar = new File(subDir, modName + ".jar");
            File propsFile = new File(subDir, "mod.properties");
            if (!modJar.exists() || !propsFile.exists()) continue;
            if (enabled.isExplicitlyDisabled(modName)) {
                logger.info("Skipping disabled legacy mod (enabled.json): " + modName);
                handled.add(modName);
                continue;
            }
            try {
                logger.info("Loading canonical legacy mod: " + modName);
                java.util.Properties props = new java.util.Properties();
                try (java.io.FileInputStream fis = new java.io.FileInputStream(propsFile)) {
                    props.load(fis);
                }
                String classname = props.getProperty("classname");
                if (classname != null && !classname.isEmpty()) {
                    loadLegacyModFromClassname(modJar, classname);
                } else {
                    loadLegacyMod(modJar);
                }
                handled.add(modName);
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Failed to load canonical legacy mod: " + modName, e);
            }
        }
    }

    private void loadModsFromProperties(File modsDir, File[] propsFiles,
                                        java.util.Set<String> handled, EnabledRegistry enabled) {
        for (File propsFile : propsFiles) {
            try {
                String modName = propsFile.getName().replaceAll("\\.properties$", "");
                if (handled.contains(modName)) continue;
                if (enabled.isExplicitlyDisabled(modName)) {
                    logger.info("Skipping disabled legacy mod (enabled.json): " + modName);
                    handled.add(modName);
                    continue;
                }
                logger.info("Loading legacy mod from properties: " + modName);

                java.util.Properties props = new java.util.Properties();
                try (java.io.FileInputStream fis = new java.io.FileInputStream(propsFile)) {
                    props.load(fis);
                }

                // Canonical layout: mods/<name>/<name>.jar (matches server-side modloader).
                // Legacy flat layout (mods/<name>.jar) is still accepted as a fallback.
                // A "classpath=" entry in the .properties overrides both.
                String explicitClasspath = props.getProperty("classpath");
                File modJar;
                if (explicitClasspath != null && !explicitClasspath.isEmpty()) {
                    modJar = new File(modsDir, explicitClasspath);
                } else {
                    File subfolderJar = new File(modsDir, modName + "/" + modName + ".jar");
                    File flatJar = new File(modsDir, modName + ".jar");
                    modJar = subfolderJar.exists() ? subfolderJar : flatJar;
                }

                if (!modJar.exists()) {
                    logger.warning("  JAR not found for mod '" + modName + "' (checked "
                            + modName + "/" + modName + ".jar and " + modName + ".jar)");
                    continue;
                }

                // Get classname if specified (optional - falls back to scanning)
                String classname = props.getProperty("classname");

                if (classname != null && !classname.isEmpty()) {
                    loadLegacyModFromClassname(modJar, classname);
                } else {
                    // No classname specified - scan JAR for WurmClientMod implementations
                    loadLegacyMod(modJar);
                }
                handled.add(modName);

            } catch (Exception e) {
                logger.log(Level.SEVERE, "Failed to load legacy mod from properties: " + propsFile.getName(), e);
            }
        }
    }

    /**
     * Loads mods by scanning all JAR files directly (simple mode).
     *
     * <p>Picks up both the canonical subfolder layout ({@code mods/<name>/<name>.jar})
     * and the legacy flat layout ({@code mods/<name>.jar}).</p>
     */
    private void loadModsFromJars(File modsDir, java.util.Set<String> handled, EnabledRegistry enabled) {
        java.util.List<File> modJarList = new java.util.ArrayList<>();

        File[] flatJars = modsDir.listFiles((dir, name) -> name.endsWith(".jar"));
        if (flatJars != null) {
            for (File j : flatJars) {
                String modName = j.getName().replaceAll("\\.jar$", "");
                if (handled.contains(modName)) continue;
                if (enabled.isExplicitlyDisabled(modName)) {
                    logger.info("Skipping disabled legacy mod (enabled.json): " + modName);
                    handled.add(modName);
                    continue;
                }
                modJarList.add(j);
                handled.add(modName);
            }
        }

        File[] subdirs = modsDir.listFiles(File::isDirectory);
        if (subdirs != null) {
            for (File sub : subdirs) {
                String modName = sub.getName();
                if (handled.contains(modName)) continue;
                File expected = new File(sub, modName + ".jar");
                if (!expected.exists()) continue;
                if (enabled.isExplicitlyDisabled(modName)) {
                    logger.info("Skipping disabled legacy mod (enabled.json): " + modName);
                    handled.add(modName);
                    continue;
                }
                modJarList.add(expected);
                handled.add(modName);
            }
        }

        if (modJarList.isEmpty()) {
            return;
        }

        logger.info("Found " + modJarList.size() + " unmanaged legacy mod JAR(s)");
        logger.info("");

        for (File modJar : modJarList) {
            loadLegacyMod(modJar);
        }
    }

    /**
     * Loads a specific legacy mod class from a JAR (properties-based loading).
     */
    private void loadLegacyModFromClassname(File modJar, String classname) {
        logger.info("  Loading legacy class: " + classname);

        try {
            URL[] urls = new URL[] { modJar.toURI().toURL() };
            URLClassLoader modClassLoader = new URLClassLoader(urls, getClass().getClassLoader());

            Class<?> modClass = modClassLoader.loadClass(classname);

            // Verify it implements WurmClientMod
            if (!WurmClientMod.class.isAssignableFrom(modClass)) {
                logger.warning("  Class " + classname + " does not implement WurmClientMod");
                return;
            }

            WurmClientMod modInstance = (WurmClientMod) modClass.newInstance();
            loadedMods.add(modInstance);

            logger.info("  ✓ Loaded: " + classname);

        } catch (Exception e) {
            logger.log(Level.SEVERE, "  ✗ Failed to load legacy mod class: " + classname, e);
        }
    }

    /**
     * Loads a single legacy mod JAR file.
     */
    private void loadLegacyMod(File modJar) {
        logger.info("Scanning: " + modJar.getName());

        try {
            // Create URLClassLoader for the mod
            URL[] urls = new URL[] { modJar.toURI().toURL() };
            URLClassLoader modClassLoader = new URLClassLoader(urls, getClass().getClassLoader());

            // Scan JAR for classes implementing WurmClientMod
            List<String> modClasses = scanJarForLegacyMods(modJar);

            if (modClasses.isEmpty()) {
                logger.fine("  No WurmClientMod implementations found in " + modJar.getName());
                return;
            }

            logger.info("  Found " + modClasses.size() + " legacy mod class(es)");

            // Load and instantiate each mod class
            int loaded = 0;
            for (String className : modClasses) {
                try {
                    Class<?> modClass = modClassLoader.loadClass(className);

                    // Verify it implements WurmClientMod
                    if (!WurmClientMod.class.isAssignableFrom(modClass)) {
                        continue;
                    }

                    WurmClientMod modInstance = (WurmClientMod) modClass.newInstance();
                    loadedMods.add(modInstance);

                    logger.info("  ✓ Loaded: " + className);
                    loaded++;
                } catch (Exception e) {
                    logger.log(Level.WARNING, "  ✗ Failed to load legacy mod class: " + className, e);
                }
            }

            if (loaded == 0) {
                logger.warning("  No valid legacy mods found in " + modJar.getName());
            }

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to scan legacy mod JAR: " + modJar.getName(), e);
        }
    }

    /**
     * Scans a JAR file for classes that might implement WurmClientMod.
     */
    private List<String> scanJarForLegacyMods(File jarFile) {
        List<String> candidateClasses = new ArrayList<>();

        try (JarFile jar = new JarFile(jarFile)) {
            Enumeration<JarEntry> entries = jar.entries();

            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String name = entry.getName();

                // Only look at .class files
                if (!name.endsWith(".class")) {
                    continue;
                }

                // Convert path to class name
                String className = name.substring(0, name.length() - 6).replace('/', '.');

                // Skip package-info and module-info
                if (className.endsWith("package-info") || className.endsWith("module-info")) {
                    continue;
                }

                // Skip system classes
                if (className.startsWith("java.") ||
                    className.startsWith("javax.") ||
                    className.startsWith("com.sun.")) {
                    continue;
                }

                candidateClasses.add(className);
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, "Error scanning JAR: " + jarFile.getName(), e);
        }

        return candidateClasses;
    }

    /**
     * Gets the list of loaded legacy mods.
     *
     * @return list of loaded legacy mods
     */
    public List<WurmClientMod> getLoadedMods() {
        return new ArrayList<>(loadedMods);
    }
}
