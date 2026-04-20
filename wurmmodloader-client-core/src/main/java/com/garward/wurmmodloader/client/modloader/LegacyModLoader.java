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
     * 1. Properties-based: modname.properties + modname.jar (or modname/modname.jar)
     * 2. Direct JAR scanning: All .jar files in mods/ (fallback if no .properties found)
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

        // Check for .properties files first
        File[] propsFiles = modsDir.listFiles((dir, name) -> name.endsWith(".properties"));

        if (propsFiles != null && propsFiles.length > 0) {
            // Properties-based loading
            logger.info("Found " + propsFiles.length + " .properties file(s) - using properties-based loading");
            logger.info("");
            loadModsFromProperties(modsDir, propsFiles);
        } else {
            // Fallback to direct JAR scanning
            logger.info("No .properties files found - scanning for JAR files directly");
            logger.info("");
            loadModsFromJars(modsDir);
        }

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
    private void loadModsFromProperties(File modsDir, File[] propsFiles) {
        for (File propsFile : propsFiles) {
            try {
                String modName = propsFile.getName().replaceAll("\\.properties$", "");
                logger.info("Loading legacy mod from properties: " + modName);

                java.util.Properties props = new java.util.Properties();
                try (java.io.FileInputStream fis = new java.io.FileInputStream(propsFile)) {
                    props.load(fis);
                }

                // Get classpath from properties (e.g., "modname/modname.jar" or "modname.jar")
                String classpath = props.getProperty("classpath", modName + ".jar");
                File modJar = new File(modsDir, classpath);

                if (!modJar.exists()) {
                    logger.warning("  JAR not found: " + classpath);
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

            } catch (Exception e) {
                logger.log(Level.SEVERE, "Failed to load legacy mod from properties: " + propsFile.getName(), e);
            }
        }
    }

    /**
     * Loads mods by scanning all JAR files directly (simple mode).
     */
    private void loadModsFromJars(File modsDir) {
        File[] modJars = modsDir.listFiles((dir, name) -> name.endsWith(".jar"));
        if (modJars == null || modJars.length == 0) {
            logger.info("No mod JARs found in mods/ directory");
            logger.info("Place mod JAR files in: " + modsDir.getAbsolutePath());
            return;
        }

        logger.info("Found " + modJars.length + " mod JAR(s)");
        logger.info("");

        // Load all mods first
        for (File modJar : modJars) {
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
