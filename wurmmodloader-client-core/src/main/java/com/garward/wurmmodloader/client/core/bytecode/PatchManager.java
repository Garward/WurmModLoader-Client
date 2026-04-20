package com.garward.wurmmodloader.client.core.bytecode;

import com.garward.wurmmodloader.client.api.bytecode.BytecodePatch;
import com.garward.wurmmodloader.client.api.bytecode.PatchRegistry;

import javassist.ClassPool;
import javassist.CtClass;

import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Manages the application of bytecode patches to Wurm client classes.
 *
 * <p>This class loads target classes using Javassist, applies all registered
 * patches, and returns the modified bytecode for the classloader.
 *
 * @since 0.1.0
 */
public class PatchManager {

    private static final Logger logger = Logger.getLogger(PatchManager.class.getName());
    private final ClassPool classPool;
    private int patchesApplied = 0;
    private int patchesFailed = 0;

    /**
     * Creates a new PatchManager with the given ClassPool.
     *
     * @param classPool the Javassist ClassPool to use
     */
    public PatchManager(ClassPool classPool) {
        this.classPool = classPool;
    }

    /**
     * Applies all registered patches to a class.
     *
     * <p>If no patches are registered for the class, returns null.
     * If patches fail to apply, logs the error and returns null.
     *
     * @param className the fully qualified class name
     * @return the modified class bytecode, or null if no patches or failure
     */
    public byte[] patchClass(String className) {
        List<BytecodePatch> patches = PatchRegistry.getPatchesForClass(className);

        if (patches.isEmpty()) {
            return null;
        }

        logger.info("Patching class: " + className + " with " + patches.size() + " patch(es)");

        try {
            CtClass ctClass = classPool.get(className);

            // Apply all patches in priority order
            for (BytecodePatch patch : patches) {
                try {
                    logger.fine("  Applying patch: " + patch.getDescription());
                    patch.apply(ctClass);
                    patchesApplied++;
                } catch (Exception e) {
                    logger.log(Level.SEVERE, "Failed to apply patch " + patch.getDescription() + " to " + className, e);
                    patchesFailed++;
                    // Continue with other patches - partial patching is better than none
                }
            }

            // Convert to bytecode
            byte[] bytecode = ctClass.toBytecode();
            ctClass.detach();  // Free memory

            logger.info("Successfully patched " + className);
            return bytecode;

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to patch class " + className, e);
            patchesFailed += patches.size();
            return null;
        }
    }

    /**
     * Returns the number of patches successfully applied.
     *
     * @return the count of applied patches
     */
    public int getPatchesApplied() {
        return patchesApplied;
    }

    /**
     * Returns the number of patches that failed to apply.
     *
     * @return the count of failed patches
     */
    public int getPatchesFailed() {
        return patchesFailed;
    }

    /**
     * Logs a summary of patch statistics.
     */
    public void logSummary() {
        int total = PatchRegistry.getPatchCount();
        logger.info("=== Patch Summary ===");
        logger.info("Total patches registered: " + total);
        logger.info("Patches applied: " + patchesApplied);
        logger.info("Patches failed: " + patchesFailed);
        logger.info("Target classes: " + PatchRegistry.getAllTargetClasses().size());
    }
}
