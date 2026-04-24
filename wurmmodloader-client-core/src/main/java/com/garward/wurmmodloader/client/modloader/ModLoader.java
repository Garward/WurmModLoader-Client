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
     *
     * <p>Discovery order (first match per mod name wins):</p>
     * <ol>
     *   <li><b>Canonical self-contained:</b> {@code mods/<name>/<name>.jar}
     *       + {@code mods/<name>/mod.properties} — the preferred layout.</li>
     *   <li>Legacy flat properties: {@code mods/<name>.properties} + jar in either
     *       {@code mods/<name>/<name>.jar} or {@code mods/<name>.jar}.</li>
     *   <li>Direct JAR scanning: any leftover {@code mods/*.jar} or
     *       {@code mods/<name>/<name>.jar} without a properties file.</li>
     * </ol>
     *
     * <p>{@code mods/enabled.json} (optional) can explicitly disable individual mods
     * by name. Missing entries default to enabled.</p>
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

        EnabledRegistry enabled = EnabledRegistry.load(modsDir);
        java.util.Set<String> handled = new java.util.HashSet<>();

        // Phase 0: canonical self-contained subfolder mods
        loadCanonicalSubfolderMods(modsDir, handled, enabled);

        // Phase 1: legacy flat-properties layout
        File[] propsFiles = modsDir.listFiles((dir, name) -> name.endsWith(".properties"));
        if (propsFiles != null && propsFiles.length > 0) {
            loadModsFromProperties(modsDir, propsFiles, handled, enabled);
        }

        // Phase 2: leftover jars with no properties at all
        loadModsFromJars(modsDir, handled, enabled);

        logger.info("");
        logger.info(separator);
        logger.info("Loaded " + loadedMods.size() + " mod(s) successfully");
        logger.info(separator);
    }

    /**
     * Phase 0 — canonical layout: {@code mods/<name>/<name>.jar}
     * + {@code mods/<name>/mod.properties}. The whole mod (jar, manifest, config,
     * resources, server-packs, etc.) lives inside its own folder.
     */
    private void loadCanonicalSubfolderMods(File modsDir, java.util.Set<String> handled,
                                            EnabledRegistry enabled) {
        File[] subdirs = modsDir.listFiles(File::isDirectory);
        if (subdirs == null) {
            return;
        }
        for (File subDir : subdirs) {
            String modName = subDir.getName();
            if (handled.contains(modName)) {
                continue;
            }
            File modJar = new File(subDir, modName + ".jar");
            File propsFile = new File(subDir, "mod.properties");
            if (!modJar.exists() || !propsFile.exists()) {
                continue;
            }
            if (enabled.isExplicitlyDisabled(modName)) {
                logger.info("Skipping disabled mod (enabled.json): " + modName);
                handled.add(modName);
                continue;
            }
            try {
                logger.info("Loading canonical mod: " + modName);
                java.util.Properties props = new java.util.Properties();
                try (java.io.FileInputStream fis = new java.io.FileInputStream(propsFile)) {
                    props.load(fis);
                }

                String classname = props.getProperty("classname");
                if (classname != null && !classname.isEmpty()) {
                    loadModFromClassname(modJar, classname, props);
                } else {
                    loadMod(modJar);
                }
                handled.add(modName);
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Failed to load canonical mod: " + modName, e);
            }
        }
    }

    /**
     * Legacy flat-properties loader: {@code mods/<name>.properties} alongside either
     * {@code mods/<name>/<name>.jar} or {@code mods/<name>.jar}.
     */
    private void loadModsFromProperties(File modsDir, File[] propsFiles,
                                        java.util.Set<String> handled, EnabledRegistry enabled) {
        for (File propsFile : propsFiles) {
            try {
                String modName = propsFile.getName().replaceAll("\\.properties$", "");
                if (handled.contains(modName)) {
                    continue;
                }
                if (enabled.isExplicitlyDisabled(modName)) {
                    logger.info("Skipping disabled mod (enabled.json): " + modName);
                    handled.add(modName);
                    continue;
                }
                logger.info("Loading mod from properties: " + modName);

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
                    loadModFromClassname(modJar, classname, props);
                } else {
                    // No classname specified - scan JAR for event handlers
                    loadMod(modJar);
                }
                handled.add(modName);

            } catch (Exception e) {
                logger.log(Level.SEVERE, "Failed to load mod from properties: " + propsFile.getName(), e);
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
        java.util.List<File> modJars = new java.util.ArrayList<>();

        File[] flatJars = modsDir.listFiles((dir, name) -> name.endsWith(".jar"));
        if (flatJars != null) {
            for (File j : flatJars) {
                String modName = j.getName().replaceAll("\\.jar$", "");
                if (handled.contains(modName)) continue;
                if (enabled.isExplicitlyDisabled(modName)) {
                    logger.info("Skipping disabled mod (enabled.json): " + modName);
                    handled.add(modName);
                    continue;
                }
                modJars.add(j);
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
                    logger.info("Skipping disabled mod (enabled.json): " + modName);
                    handled.add(modName);
                    continue;
                }
                modJars.add(expected);
                handled.add(modName);
            }
        }

        if (modJars.isEmpty()) {
            return;
        }

        logger.info("Found " + modJars.size() + " unmanaged mod JAR(s)");
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
