package com.garward.wurmmodloader.client.modloader;

import com.garward.wurmmodloader.client.api.events.base.SubscribeEvent;

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
 * Loads client mods from the mods/ directory.
 *
 * <p>This class scans for JAR files in the mods directory, loads them,
 * and registers any classes with @SubscribeEvent methods with the event bus.
 *
 * @since 0.1.0
 */
public class ModLoader {

    private static final Logger logger = Logger.getLogger(ModLoader.class.getName());
    private final ClientHook clientHook;
    private final List<Object> loadedMods = new ArrayList<>();

    /**
     * Creates a new ModLoader.
     *
     * @param clientHook the client hook to register mod listeners with
     */
    public ModLoader(ClientHook clientHook) {
        this.clientHook = clientHook;
    }

    /**
     * Loads all mods from the mods/ directory.
     * Supports two loading modes:
     * 1. Properties-based: modname.properties + modname.jar (or modname/modname.jar)
     * 2. Direct JAR scanning: All .jar files in mods/ (fallback if no .properties found)
     */
    public void loadMods() {
        String separator = "======================================================================";
        logger.info(separator);
        logger.info("Loading mods from mods/ directory...");
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

        logger.info("");
        logger.info(separator);
        logger.info("Loaded " + loadedMods.size() + " mod(s) successfully");
        logger.info(separator);
    }

    /**
     * Loads mods using .properties files (matches server modloader structure).
     */
    private void loadModsFromProperties(File modsDir, File[] propsFiles) {
        for (File propsFile : propsFiles) {
            try {
                String modName = propsFile.getName().replaceAll("\\.properties$", "");
                logger.info("Loading mod from properties: " + modName);

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
                    loadModFromClassname(modJar, classname, props);
                } else {
                    // No classname specified - scan JAR for event handlers
                    loadMod(modJar);
                }

            } catch (Exception e) {
                logger.log(Level.SEVERE, "Failed to load mod from properties: " + propsFile.getName(), e);
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

        for (File modJar : modJars) {
            loadMod(modJar);
        }
    }

    /**
     * Loads a specific class from a JAR (properties-based loading with explicit classname).
     */
    private void loadModFromClassname(File modJar, String classname, java.util.Properties props) {
        logger.info("  Loading class: " + classname);

        try {
            URL[] urls = new URL[] { modJar.toURI().toURL() };
            URLClassLoader modClassLoader = new URLClassLoader(urls, getClass().getClassLoader());

            Class<?> modClass = modClassLoader.loadClass(classname);

            // Check if class has event handlers
            boolean hasHandlers = false;
            for (Method method : modClass.getDeclaredMethods()) {
                if (method.isAnnotationPresent(SubscribeEvent.class)) {
                    hasHandlers = true;
                    break;
                }
            }

            if (!hasHandlers) {
                logger.warning("  Class " + classname + " has no @SubscribeEvent methods");
                return;
            }

            Object modInstance = modClass.newInstance();

            // Register with event bus
            clientHook.registerListener(modInstance);
            loadedMods.add(modInstance);

            logger.info("  ✓ Registered: " + classname);

        } catch (Exception e) {
            logger.log(Level.SEVERE, "  ✗ Failed to load mod class: " + classname, e);
        }
    }

    /**
     * Loads a single mod JAR file.
     */
    private void loadMod(File modJar) {
        logger.info("Loading mod: " + modJar.getName());

        try {
            // Create URLClassLoader for the mod
            URL[] urls = new URL[] { modJar.toURI().toURL() };
            URLClassLoader modClassLoader = new URLClassLoader(urls, getClass().getClassLoader());

            // Scan JAR for classes with @SubscribeEvent methods
            List<String> modClasses = scanJarForEventHandlers(modJar);

            if (modClasses.isEmpty()) {
                logger.warning("  No event handlers found in " + modJar.getName());
                return;
            }

            logger.info("  Found " + modClasses.size() + " class(es) with event handlers");

            // Load and instantiate each mod class
            int registered = 0;
            for (String className : modClasses) {
                try {
                    Class<?> modClass = modClassLoader.loadClass(className);

                    // Check if class actually has event handlers
                    boolean hasHandlers = false;
                    for (Method method : modClass.getDeclaredMethods()) {
                        if (method.isAnnotationPresent(SubscribeEvent.class)) {
                            hasHandlers = true;
                            break;
                        }
                    }

                    if (!hasHandlers) {
                        continue; // Skip classes without event handlers
                    }

                    Object modInstance = modClass.newInstance();

                    // Register with event bus
                    clientHook.registerListener(modInstance);
                    loadedMods.add(modInstance);

                    logger.info("  ✓ Registered: " + className);
                    registered++;
                } catch (Exception e) {
                    logger.log(Level.WARNING, "  ✗ Failed to load mod class: " + className, e);
                }
            }

            if (registered == 0) {
                logger.warning("  No valid event handlers found in " + modJar.getName());
            }

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to load mod JAR: " + modJar.getName(), e);
        }
    }

    /**
     * Scans a JAR file for classes that have methods with @SubscribeEvent annotation.
     */
    private List<String> scanJarForEventHandlers(File jarFile) {
        List<String> classesWithEventHandlers = new ArrayList<>();

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

                // Check if class has @SubscribeEvent methods
                if (hasEventHandlers(jarFile, className)) {
                    classesWithEventHandlers.add(className);
                }
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, "Error scanning JAR: " + jarFile.getName(), e);
        }

        return classesWithEventHandlers;
    }

    /**
     * Checks if a class has any methods annotated with @SubscribeEvent.
     *
     * <p>For now, we'll just try to load all classes and let failures be caught later.
     * A better implementation would use bytecode scanning (ASM) to check for annotations
     * without loading classes.
     */
    private boolean hasEventHandlers(File jarFile, String className) {
        // For simplicity, assume all non-system classes might have event handlers
        // We'll filter during actual loading
        return !className.startsWith("java.") &&
               !className.startsWith("javax.") &&
               !className.startsWith("com.sun.");
    }

    /**
     * Gets the list of loaded mod instances.
     *
     * @return list of loaded mods
     */
    public List<Object> getLoadedMods() {
        return new ArrayList<>(loadedMods);
    }
}
