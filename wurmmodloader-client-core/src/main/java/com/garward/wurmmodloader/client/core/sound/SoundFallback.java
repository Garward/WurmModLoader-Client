package com.garward.wurmmodloader.client.core.sound;

import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Runtime helper used by client bytecode patches around the audio path.
 *
 * <p>Logs the first occurrence of every {@code .ogg} resource the client
 * fails to resolve, so we can identify packs that are missing samples
 * referenced by templates/spells/creatures. Subsequent misses for the same
 * name are deduped to avoid log flooding.
 *
 * <p>Reached from {@link com.garward.wurmmodloader.client.core.bytecode.patches.SoundResourceLoggingPatch}
 * via {@code Resources.getResource}.
 */
public final class SoundFallback {

    private static final Logger LOG = Logger.getLogger(SoundFallback.class.getName());
    private static final ConcurrentHashMap<String, Boolean> LOGGED = new ConcurrentHashMap<>();

    private SoundFallback() {}

    /**
     * Called from the patched {@code Resources.getResource} when a lookup
     * returns null. Filters to {@code .ogg} resources only and dedupes.
     */
    public static void noteMissingResource(String resourceName) {
        if (resourceName == null || !resourceName.endsWith(".ogg")) {
            return;
        }
        if (LOGGED.putIfAbsent(resourceName, Boolean.TRUE) == null) {
            LOG.warning("[SoundFallback] Missing sound resource: " + resourceName);
        }
    }
}
