package com.garward.wurmmodloader.client.patcher;

import com.garward.wurmmodloader.client.api.bytecode.BytecodePatch;
import com.garward.wurmmodloader.client.api.bytecode.PatchRegistry;
import com.garward.wurmmodloader.client.core.bytecode.CorePatches;
import com.garward.wurmmodloader.client.core.bytecode.PatchManager;
import com.garward.wurmmodloader.client.core.bytecode.patches.SimpleServerConnectionModCommPatch;
import com.garward.wurmmodloader.client.core.bytecode.patches.gui.FlexComponentAccessPatch;
import com.garward.wurmmodloader.client.core.bytecode.patches.gui.GuiClassWideningPatch;
import com.garward.wurmmodloader.client.core.bytecode.patches.gui.WurmComponentAccessPatch;

import java.util.HashMap;
import java.util.Map;

import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtConstructor;
import javassist.CtField;
import javassist.CtMethod;
import javassist.CtNewConstructor;
import javassist.LoaderClassPath;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;
import java.util.jar.JarFile;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Main class for the Wurm client patcher.
 *
 * <p>This class can be used in two modes:
 * <ol>
 *   <li><b>Java Agent Mode</b> (preferred): Use {@code -javaagent:wurmmodloader-client.jar}
 *       to transform classes as they are loaded by the JVM.</li>
 *   <li><b>Standalone Mode</b>: Run directly to patch the client JAR file and create
 *       a modified version.</li>
 * </ol>
 *
 * <h2>Usage</h2>
 *
 * <h3>As Java Agent (Recommended)</h3>
 * <pre>{@code
 * java -javaagent:wurmmodloader-client.jar -jar client.jar
 * }</pre>
 *
 * <h3>As Standalone Patcher</h3>
 * <pre>{@code
 * java -jar wurmmodloader-client.jar [path/to/client.jar]
 * }</pre>
 *
 * @since 0.1.0
 */
public class ClientPatcher {

    private static final Logger logger = Logger.getLogger(ClientPatcher.class.getName());
    private static PatchManager patchManager;

    /**
     * Java Agent entry point.
     *
     * <p>Called by the JVM when using {@code -javaagent}. This is the
     * preferred way to run the patcher as it transforms classes on-the-fly
     * without modifying the original JAR.
     *
     * @param agentArgs agent arguments (unused)
     * @param inst the Instrumentation instance
     */
    public static void premain(String agentArgs, Instrumentation inst) {
        logger.info("=== WurmModLoader Client Patcher Starting (Agent Mode) ===");
        logger.info("Version: 0.1.0");

        // Initialize the patch system
        initializePatchSystem();

        // Register our class transformer
        inst.addTransformer(new WurmClientTransformer());

        logger.info("Client patcher initialized successfully");
        logger.info("Classes will be patched as they are loaded");
    }

    /**
     * Main entry point for standalone execution.
     *
     * <p>When run directly, this will patch a client JAR file in-place.
     *
     * @param args command line arguments (optional client.jar path)
     */
    public static void main(String[] args) {
        logger.info("=== WurmModLoader Client Patcher ===");
        logger.info("Version: 0.1.0");
        logger.info("");

        // Auto-detect Steam installation if no path provided
        String clientJarPath;
        if (args.length == 0) {
            String home = System.getProperty("user.home");
            clientJarPath = home + "/.local/share/Steam/steamapps/common/Wurm Unlimited/WurmLauncher/client.jar";
            logger.info("No path provided, auto-detecting Steam installation...");
            logger.info("Looking for: " + clientJarPath);
            logger.info("");
        } else {
            clientJarPath = args[0];
        }

        File clientJar = new File(clientJarPath);

        if (!clientJar.exists()) {
            logger.severe("Client JAR not found: " + clientJarPath);
            logger.severe("");
            printUsage();
            System.exit(1);
        }

        logger.info("Target: " + clientJar.getAbsolutePath());
        logger.info("Mode: Standalone JAR patching");
        logger.info("");

        // Patch the JAR
        try {
            patchJarFile(clientJar);
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to patch client JAR", e);
            System.exit(1);
        }
    }

    /**
     * Initializes the patch system by registering all patches.
     */
    private static void initializePatchSystem() {
        logger.info("Initializing patch system...");

        // Register core patches
        CorePatches.registerAll();

        // TODO: Load and register patches from mods
        // ModLoader.loadModPatches();

        logger.info("Patch system initialized");
        logger.info("Total patches: " + PatchRegistry.getPatchCount());
        logger.info("Target classes: " + PatchRegistry.getAllTargetClasses().size());
        logger.info("");

        // Create the PatchManager
        ClassPool classPool = ClassPool.getDefault();
        classPool.appendClassPath(new LoaderClassPath(ClassLoader.getSystemClassLoader()));
        patchManager = new PatchManager(classPool);
    }

    /**
     * Patches a client JAR file (standalone mode).
     *
     * <p>This method modifies the client.jar in-place by injecting our modloader
     * bootstrap code into WurmMain.main(). The original is backed up first.
     *
     * @param clientJar the client JAR file to patch
     * @throws Exception if patching fails
     */
    private static void patchJarFile(File clientJar) throws Exception {
        logger.info("Starting JAR file patching...");
        logger.info("Target: " + clientJar.getAbsolutePath());
        logger.info("");

        // Check if already patched
        if (isAlreadyPatched(clientJar)) {
            logger.warning("client.jar is already patched!");
            logger.warning("To re-patch, restore from client.jar.backup first");
            return;
        }

        // Backup original client.jar
        File backup = new File(clientJar.getParentFile(), "client.jar.backup");
        if (!backup.exists()) {
            logger.info("Creating backup: " + backup.getName());
            java.nio.file.Files.copy(clientJar.toPath(), backup.toPath());
            logger.info("✓ Backup created");
        } else {
            logger.info("Backup already exists: " + backup.getName());
        }

        // Patch WurmMain class in the JAR
        logger.info("");
        logger.info("Patching client JAR...");
        patchClientJar(clientJar);
        logger.info("✓ Client JAR patched successfully");

        logger.info("");
        logger.info("======================================================================");
        logger.info("✅ Client patching complete!");
        logger.info("======================================================================");
        logger.info("");
        logger.info("The client will now automatically load mods when launched.");
        logger.info("Original client backed up to: " + backup.getName());
        logger.info("");
        logger.info("To restore original client:");
        logger.info("  mv " + backup.getName() + " " + clientJar.getName());
    }

    /**
     * Checks if client.jar is already patched by looking for our marker.
     */
    private static boolean isAlreadyPatched(File clientJar) throws Exception {
        try (JarFile jar = new JarFile(clientJar)) {
            java.util.zip.ZipEntry entry = jar.getEntry("com/wurmonline/client/launcherfx/WurmMain.class");
            if (entry == null) {
                return false;
            }

            try (InputStream is = jar.getInputStream(entry)) {
                ClassPool cp = ClassPool.getDefault();
                CtClass ctClass = cp.makeClass(is);

                // Check if our bootstrap method exists
                try {
                    ctClass.getDeclaredMethod("wurmModLoaderBootstrap");
                    return true;
                } catch (javassist.NotFoundException e) {
                    return false;
                }
            }
        }
    }

    /**
     * Patches client JAR - patches WurmMain (bootstrap) and WurmClientBase (events).
     */
    private static void patchClientJar(File clientJar) throws Exception {
        File tempJar = File.createTempFile("client", ".jar.tmp");
        tempJar.deleteOnExit();

        int patchedClasses = 0;

        // Build a map of JAR-safe access-widening patches keyed by JAR entry path.
        // These patches have no runtime hook dependencies and can be baked directly
        // into client.jar on disk, which is what `javac` sees at mod compile time.
        Map<String, java.util.List<BytecodePatch>> accessWideningPatches = new HashMap<>();
        addPatch(accessWideningPatches, "com/wurmonline/client/renderer/gui/WurmComponent.class", new WurmComponentAccessPatch());
        addPatch(accessWideningPatches, "com/wurmonline/client/renderer/gui/FlexComponent.class", new FlexComponentAccessPatch());
        for (GuiClassWideningPatch p : CorePatches.GUI_CLASS_WIDENINGS) {
            addPatch(accessWideningPatches, p.getJarEntryName(), p);
        }
        // ModComm install — pure-bytecode hook at client level; needs client.jar
        // on the applyInPlace ClassPool (added above) so Javassist can resolve
        // enclosing-class field types (e.g. ServerConnectionListenerClass).
        addPatch(accessWideningPatches,
            "com/wurmonline/client/comm/SimpleServerConnectionClass.class",
            new SimpleServerConnectionModCommPatch()
        );
        // HUD init hook — fires ClientHUDInitializedEvent so mods (livemap,
        // etc.) can register windows + menu entries. Stacked on top of the
        // GUI access-widening patch for HeadsUpDisplay.
        addPatch(accessWideningPatches,
            "com/wurmonline/client/renderer/gui/HeadsUpDisplay.class",
            new com.garward.wurmmodloader.client.core.bytecode.patches.HeadsUpDisplayInitPatch()
        );

        try (JarFile inputJar = new JarFile(clientJar);
             FileOutputStream fos = new FileOutputStream(tempJar);
             java.util.jar.JarOutputStream jos = new java.util.jar.JarOutputStream(fos)) {

            // Copy all entries, patching specific classes
            java.util.Enumeration<java.util.jar.JarEntry> entries = inputJar.entries();
            while (entries.hasMoreElements()) {
                java.util.jar.JarEntry entry = entries.nextElement();
                byte[] patchedBytes = null;

                // Patch WurmMain for bootstrap and init event
                if (entry.getName().equals("com/wurmonline/client/launcherfx/WurmMain.class")) {
                    logger.info("  Patching WurmMain.class (bootstrap + init)...");
                    patchedBytes = patchWurmMainClass(inputJar.getInputStream(entry));
                    patchedClasses++;
                }
                // Patch WurmClientBase for events
                else if (entry.getName().equals("com/wurmonline/client/WurmClientBase.class")) {
                    logger.info("  Patching WurmClientBase.class (events)...");
                    patchedBytes = patchWurmClientBaseClass(inputJar.getInputStream(entry));
                    patchedClasses++;
                }
                // Data-driven: bake any registered access-widening patch into the JAR.
                else if (accessWideningPatches.containsKey(entry.getName())) {
                    java.util.List<BytecodePatch> patches = accessWideningPatches.get(entry.getName());
                    logger.info("  Patching " + entry.getName() + " (" + patches.size() + " patch(es))...");
                    patchedBytes = applyInPlace(inputJar.getInputStream(entry), patches);
                    patchedClasses++;
                }

                // Write patched or original class
                if (patchedBytes != null) {
                    java.util.jar.JarEntry newEntry = new java.util.jar.JarEntry(entry.getName());
                    jos.putNextEntry(newEntry);
                    jos.write(patchedBytes);
                    jos.closeEntry();
                } else {
                    // Copy as-is
                    jos.putNextEntry(entry);
                    java.io.InputStream is = inputJar.getInputStream(entry);
                    byte[] buffer = new byte[4096];
                    int bytesRead;
                    while ((bytesRead = is.read(buffer)) != -1) {
                        jos.write(buffer, 0, bytesRead);
                    }
                    jos.closeEntry();
                }
            }
        }

        logger.info("  Patched " + patchedClasses + " classes");

        // Replace original with patched version using Files.move for better reliability
        java.nio.file.Files.delete(clientJar.toPath());
        java.nio.file.Files.move(tempJar.toPath(), clientJar.toPath());
    }

    /**
     * Loads a class from the given input stream, runs a single BytecodePatch
     * against it, and returns the patched bytecode. Used to bake pure-bytecode
     * patches (no runtime hook dependencies) into client.jar on disk.
     */
    private static byte[] applyInPlace(InputStream classBytes, java.util.List<BytecodePatch> patches) throws Exception {
        ClassPool classPool = new ClassPool(true);
        String home = System.getProperty("user.home");
        String clientJarPath = home + "/.local/share/Steam/steamapps/common/Wurm Unlimited/WurmLauncher/client.jar";
        String commonJarPath = home + "/.local/share/Steam/steamapps/common/Wurm Unlimited/WurmLauncher/common.jar";
        for (String path : new String[] { clientJarPath, commonJarPath }) {
            if (new File(path).exists()) {
                classPool.appendClassPath(path);
            }
        }
        CtClass ctClass = classPool.makeClass(classBytes);
        // Apply in priority order (highest first) so access-wideners run before
        // hook-installing patches that rely on visible fields/methods.
        java.util.List<BytecodePatch> ordered = new java.util.ArrayList<>(patches);
        ordered.sort((a, b) -> Integer.compare(b.getPriority(), a.getPriority()));
        for (BytecodePatch p : ordered) {
            p.apply(ctClass);
        }
        return ctClass.toBytecode();
    }

    private static void addPatch(Map<String, java.util.List<BytecodePatch>> map, String entry, BytecodePatch patch) {
        map.computeIfAbsent(entry, k -> new java.util.ArrayList<>()).add(patch);
    }

    /**
     * Patches the WurmMain class to bootstrap our modloader.
     */
    private static byte[] patchWurmMainClass(InputStream classBytes) throws Exception {
        ClassPool classPool = ClassPool.getDefault();
        CtClass ctClass = classPool.makeClass(classBytes);

        // Add our bootstrap method that loads the modloader JAR
        CtMethod bootstrap = CtMethod.make(
            "private static void wurmModLoaderBootstrap() {" +
            "  try {" +
            "    java.io.FileOutputStream logFileStream = new java.io.FileOutputStream(\"client.log\", false);" +
            "    java.io.PrintStream logStream = new java.io.PrintStream(logFileStream, true);" +
            "    System.setOut(logStream);" +
            "    System.setErr(logStream);" +
            "  } catch (Exception e) {" +
            "    System.err.println(\"[WurmModLoader] Warning: Could not set up logging: \" + e.getMessage());" +
            "  }" +
            "  System.out.println(\"[WurmModLoader] Initializing client modloader...\");" +
            "  java.io.File currentDir = new java.io.File(\".\");" +
            "  System.out.println(\"[WurmModLoader] Working directory: \" + currentDir.getAbsolutePath());" +
            "  try {" +
            "    java.io.File modloaderJar = null;" +
            "    java.io.File[] allFiles = currentDir.listFiles();" +
            "    if (allFiles != null) {" +
            "      for (int i = 0; i < allFiles.length; i++) {" +
            "        String name = allFiles[i].getName();" +
            "        if (name.startsWith(\"wurmmodloader-client-\") && name.endsWith(\".jar\")) {" +
            "          modloaderJar = allFiles[i];" +
            "          break;" +
            "        }" +
            "      }" +
            "    }" +
            "    if (modloaderJar == null) {" +
            "      System.err.println(\"[WurmModLoader] Error: Could not find wurmmodloader-client-*.jar\");" +
            "      return;" +
            "    }" +
            "    System.out.println(\"[WurmModLoader] Found modloader: \" + modloaderJar.getName());" +
            "    java.net.URL[] urls = new java.net.URL[1];" +
            "    urls[0] = modloaderJar.toURI().toURL();" +
            "    java.net.URLClassLoader loader = new java.net.URLClassLoader(urls, Thread.currentThread().getContextClassLoader());" +
            "    Thread.currentThread().setContextClassLoader(loader);" +
            "    Class hookClass = loader.loadClass(\"com.garward.wurmmodloader.client.modloader.ProxyClientHook\");" +
            "    java.lang.reflect.Method getInstance = hookClass.getMethod(\"getInstance\", new Class[0]);" +
            "    getInstance.invoke(null, new Object[0]);" +
            "    System.out.println(\"[WurmModLoader] Client modloader initialized successfully\");" +
            "  } catch (Exception e) {" +
            "    System.err.println(\"[WurmModLoader] Failed to initialize: \" + e.getMessage());" +
            "    e.printStackTrace();" +
            "  }" +
            "}",
            ctClass
        );
        ctClass.addMethod(bootstrap);

        // Inject bootstrap call at start of main()
        CtMethod main = ctClass.getDeclaredMethod("main", new CtClass[] {
            classPool.get("java.lang.String[]")
        });
        main.insertBefore("wurmModLoaderBootstrap();");

        // NOTE: We would patch start() here to fire ClientInitEvent early, but it requires
        // JavaFX classes in the classpool which complicates the patcher.
        // Instead, ClientInitEvent is fired from WurmClientBase.run() when the game actually starts.
        // This is actually better as it fires when the client is truly initialized and ready to play.

        return ctClass.toBytecode();
    }

    /**
     * Patches WurmClientBase to fire events.
     */
    private static byte[] patchWurmClientBaseClass(InputStream classBytes) throws Exception {
        ClassPool classPool = ClassPool.getDefault();
        CtClass ctClass = classPool.makeClass(classBytes);

        try {
            // Patch run() method to fire ClientInitEvent
            // Use insertAfter for initialization (safer - ensures client is initialized)
            CtMethod runMethod = ctClass.getDeclaredMethod("run");
            runMethod.insertBefore(
                "try {" +
                "  System.out.println(\"[WurmModLoader] WurmClientBase.run() - firing ClientInitEvent\");" +
                "  com.garward.wurmmodloader.client.modloader.ProxyClientHook.fireClientInitEvent();" +
                "} catch (Exception e) {" +
                "  System.err.println(\"[WurmModLoader] Error firing ClientInitEvent: \" + e.getMessage());" +
                "  e.printStackTrace();" +
                "}"
            );

            // Patch tick(boolean) method to fire ClientTickEvent
            // tick(boolean) is the main game loop method with descriptor (Z)Z
            CtMethod tickMethod = ctClass.getDeclaredMethod("tick", new CtClass[] {
                classPool.get("boolean")
            });

            // Add deltaTime tracking field
            CtField lastTickField = new CtField(CtClass.longType, "wurmModLoader_lastTickTime", ctClass);
            ctClass.addField(lastTickField, "System.currentTimeMillis()");

            // Fire tick event with calculated deltaTime
            tickMethod.insertBefore(
                "{" +
                "  long now = System.currentTimeMillis();" +
                "  float deltaTime = (now - wurmModLoader_lastTickTime) / 1000.0f;" +
                "  wurmModLoader_lastTickTime = now;" +
                "  try {" +
                "    com.garward.wurmmodloader.client.modloader.ProxyClientHook.fireClientTickEvent(deltaTime);" +
                "  } catch (Exception e) {" +
                "    System.err.println(\"[WurmModLoader] Error firing ClientTickEvent: \" + e.getMessage());" +
                "  }" +
                "}"
            );

        } catch (javassist.NotFoundException e) {
            logger.log(Level.WARNING, "Could not find method to patch in WurmClientBase", e);
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error patching WurmClientBase", e);
        }

        return ctClass.toBytecode();
    }

    /**
     * Prints usage information.
     */
    private static void printUsage() {
        System.out.println("WurmModLoader Client Patcher v0.1.0");
        System.out.println("");
        System.out.println("This tool patches your Wurm Unlimited client to automatically load mods.");
        System.out.println("");
        System.out.println("Usage:");
        System.out.println("");
        System.out.println("  Auto-detect Steam installation (Linux):");
        System.out.println("    java -jar wurmmodloader-client.jar");
        System.out.println("");
        System.out.println("  Specify client.jar path:");
        System.out.println("    java -jar wurmmodloader-client.jar <path-to-client.jar>");
        System.out.println("");
        System.out.println("What it does:");
        System.out.println("  1. Backs up client.jar to client.jar.backup");
        System.out.println("  2. Patches WurmMain to bootstrap the modloader");
        System.out.println("  3. Client will auto-load mods when launched through Steam");
        System.out.println("");
        System.out.println("To uninstall:");
        System.out.println("  mv client.jar.backup client.jar");
    }

    /**
     * ClassFileTransformer that applies bytecode patches as classes are loaded.
     */
    private static class WurmClientTransformer implements ClassFileTransformer {

        @Override
        public byte[] transform(
                ClassLoader loader,
                String className,
                Class<?> classBeingRedefined,
                ProtectionDomain protectionDomain,
                byte[] classfileBuffer
        ) throws IllegalClassFormatException {

            // Convert internal class name (com/foo/Bar) to standard (com.foo.Bar)
            String standardClassName = className.replace('/', '.');

            // Check if we have patches for this class
            if (PatchRegistry.getPatchesForClass(standardClassName).isEmpty()) {
                return null;  // No transformation needed
            }

            try {
                // Apply patches
                byte[] patchedBytes = patchManager.patchClass(standardClassName);

                if (patchedBytes != null) {
                    logger.fine("Transformed class: " + standardClassName);
                    return patchedBytes;
                }
            } catch (Exception e) {
                logger.log(Level.WARNING, "Failed to transform " + standardClassName, e);
            }

            return null;  // Return null to use original bytecode
        }
    }
}
