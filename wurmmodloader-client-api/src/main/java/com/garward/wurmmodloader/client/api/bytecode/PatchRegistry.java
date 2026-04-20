package com.garward.wurmmodloader.client.api.bytecode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Registry for bytecode patches.
 *
 * <p>Patches are registered here during client startup and then applied
 * by the {@code PatchManager}. This class is thread-safe.
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * // Register a patch
 * PatchRegistry.register(new ClientInitPatch());
 * PatchRegistry.register(new ClientTickPatch());
 *
 * // Get all patches for a class
 * List<BytecodePatch> patches = PatchRegistry.getPatchesForClass("com.wurmonline.client.WurmClientBase");
 * }</pre>
 *
 * @since 0.1.0
 */
public class PatchRegistry {

    private static final Map<String, List<BytecodePatch>> patches = new HashMap<>();
    private static final Object lock = new Object();

    /**
     * Registers a bytecode patch.
     *
     * @param patch the patch to register
     * @throws IllegalArgumentException if patch is null
     */
    public static void register(BytecodePatch patch) {
        if (patch == null) {
            throw new IllegalArgumentException("Patch cannot be null");
        }

        synchronized (lock) {
            String className = patch.getTargetClassName();
            patches.computeIfAbsent(className, k -> new ArrayList<>()).add(patch);
        }
    }

    /**
     * Gets all patches registered for a specific class.
     *
     * <p>Patches are returned in priority order (highest priority first).
     *
     * @param className the fully qualified class name
     * @return an unmodifiable list of patches for the class (may be empty)
     */
    public static List<BytecodePatch> getPatchesForClass(String className) {
        synchronized (lock) {
            List<BytecodePatch> classPatches = patches.get(className);
            if (classPatches == null || classPatches.isEmpty()) {
                return Collections.emptyList();
            }

            // Sort by priority (highest first)
            List<BytecodePatch> sorted = new ArrayList<>(classPatches);
            sorted.sort(Comparator.comparingInt(BytecodePatch::getPriority).reversed());
            return Collections.unmodifiableList(sorted);
        }
    }

    /**
     * Gets all registered target class names.
     *
     * @return an unmodifiable set of class names that have patches
     */
    public static List<String> getAllTargetClasses() {
        synchronized (lock) {
            return Collections.unmodifiableList(new ArrayList<>(patches.keySet()));
        }
    }

    /**
     * Clears all registered patches.
     *
     * <p><b>Warning:</b> This is primarily for testing. Do not call in
     * production code.
     */
    public static void clear() {
        synchronized (lock) {
            patches.clear();
        }
    }

    /**
     * Returns the total number of registered patches.
     *
     * @return the patch count
     */
    public static int getPatchCount() {
        synchronized (lock) {
            return patches.values().stream().mapToInt(List::size).sum();
        }
    }
}
