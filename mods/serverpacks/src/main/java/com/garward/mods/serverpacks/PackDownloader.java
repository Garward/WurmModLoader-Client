package com.garward.mods.serverpacks;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Downloads server packs in a background thread.
 *
 * <p>This is a modern replacement for the old reflection-based pack downloader.
 * Uses simple URL streaming to download packs from HTTP/HTTPS URLs.
 *
 * @since 0.2.0
 */
public class PackDownloader implements Runnable {

    private static final Logger logger = Logger.getLogger(PackDownloader.class.getName());

    private final URL packUrl;
    private final String packId;
    private final DownloadCompleteHandler handler;

    /**
     * Callback interface for download completion.
     */
    @FunctionalInterface
    public interface DownloadCompleteHandler {
        void onComplete(String packId, Path tempFile);
    }

    public PackDownloader(URL packUrl, String packId, DownloadCompleteHandler handler) {
        this.packUrl = packUrl;
        this.packId = packId;
        this.handler = handler;
    }

    @Override
    public void run() {
        try {
            Path tmpName = Paths.get("packs", packId + ".tmp");

            logger.info(String.format("[ServerPacks] Downloading pack '%s' from %s", packId, packUrl));

            try (InputStream is = packUrl.openStream()) {
                Files.copy(is, tmpName, StandardCopyOption.REPLACE_EXISTING);
            }

            logger.info(String.format("[ServerPacks] Download complete: %s (size: %d bytes)",
                    packId, Files.size(tmpName)));

            handler.onComplete(packId, tmpName);

        } catch (IOException e) {
            logger.log(Level.SEVERE, String.format("[ServerPacks] Failed to download pack '%s': %s",
                    packId, e.getMessage()), e);
        }
    }
}
