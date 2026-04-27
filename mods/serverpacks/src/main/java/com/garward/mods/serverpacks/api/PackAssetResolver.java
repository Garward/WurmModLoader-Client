package com.garward.mods.serverpacks.api;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Public lookup API for assets inside server-pushed pack jars.
 *
 * <p>Peer client mods (e.g. declarativeui's ModImage with the {@code pack:} URI
 * scheme) call this to resolve {@code packId + relativePath} into an
 * {@link InputStream} from the local pack cache, without taking a hard
 * compile-time dependency on serverpacks internals.
 *
 * <p>Cache layout: pack jars land at {@code packs/<packId>.jar} (atomically
 * moved into place after download), so the file's existence is the readiness
 * signal — no callback registry needed.
 */
public final class PackAssetResolver {

    private PackAssetResolver() {}

    private static Path packPath(String packId) {
        return Paths.get("packs", packId + ".jar");
    }

    /** True once the pack has been fully downloaded and is on disk. */
    public static boolean isPackReady(String packId) {
        if (packId == null || packId.isEmpty()) return false;
        return Files.isRegularFile(packPath(packId));
    }

    /**
     * Open a stream for an entry inside a downloaded pack. Caller closes the
     * returned stream; the underlying JarFile closes with it.
     *
     * @return the entry stream, or {@code null} if the pack isn't ready or the
     *         entry doesn't exist inside it.
     */
    public static InputStream openStream(String packId, String relPath) throws IOException {
        if (packId == null || relPath == null) return null;
        Path p = packPath(packId);
        if (!Files.isRegularFile(p)) return null;

        final JarFile jar = new JarFile(p.toFile());
        try {
            String entryPath = relPath.startsWith("/") ? relPath.substring(1) : relPath;
            JarEntry entry = jar.getJarEntry(entryPath);
            if (entry == null) {
                jar.close();
                return null;
            }
            InputStream entryStream = jar.getInputStream(entry);
            return new FilterInputStream(entryStream) {
                @Override
                public void close() throws IOException {
                    try { super.close(); } finally { jar.close(); }
                }
            };
        } catch (IOException e) {
            jar.close();
            throw e;
        }
    }
}
