package com.garward.wurmmodloader.client.serverpacks;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Downloads server packs in a background thread.
 *
 * <p>Features:
 * <ul>
 *   <li>Resumable downloads — if a prior attempt left a {@code .part} file,
 *       reopens with an HTTP {@code Range} header and appends. A 200 response
 *       (server ignored the range) falls back to a fresh download.</li>
 *   <li>Progress reporting via {@link ServerPacksClientService#consoleOutput} at
 *       ~10% or 1 MiB intervals (whichever is sparser). If the server sends no
 *       {@code Content-Length} and no expectedSize was provided, reports raw
 *       byte counts.</li>
 * </ul>
 */
public class PackDownloader implements Runnable {

    private static final Logger logger = Logger.getLogger(PackDownloader.class.getName());
    private static final long PROGRESS_BYTE_STEP = 1L << 20; // 1 MiB

    private final URL packUrl;
    private final String packId;
    private final long expectedSize;
    private final DownloadCompleteHandler handler;

    @FunctionalInterface
    public interface DownloadCompleteHandler {
        void onComplete(String packId, Path tempFile);
    }

    public PackDownloader(URL packUrl, String packId, DownloadCompleteHandler handler) {
        this(packUrl, packId, -1L, handler);
    }

    public PackDownloader(URL packUrl, String packId, long expectedSize, DownloadCompleteHandler handler) {
        this.packUrl = packUrl;
        this.packId = packId;
        this.expectedSize = expectedSize;
        this.handler = handler;
    }

    @Override
    public void run() {
        Path partFile = Paths.get("packs", packId + ".part");
        try {
            Files.createDirectories(partFile.getParent());

            long existing = Files.isRegularFile(partFile) ? Files.size(partFile) : 0L;
            if (expectedSize > 0 && existing >= expectedSize) {
                Files.deleteIfExists(partFile);
                existing = 0L;
            }

            HttpURLConnection conn = (HttpURLConnection) packUrl.openConnection();
            conn.setRequestProperty("User-Agent", "WurmModLoader-Client/serverpacks");
            if (existing > 0) {
                conn.setRequestProperty("Range", "bytes=" + existing + "-");
            }
            conn.connect();

            int status = conn.getResponseCode();
            boolean append;
            if (status == HttpURLConnection.HTTP_PARTIAL) {
                append = true;
                ServerPacksClientService.consoleOutput(
                        String.format("[ServerPacks] Resuming %s at %s", packId, formatBytes(existing)));
            } else if (status == HttpURLConnection.HTTP_OK) {
                append = false;
                existing = 0L;
                Files.deleteIfExists(partFile);
            } else {
                logger.log(Level.SEVERE,
                        String.format("[ServerPacks] HTTP %d downloading %s", status, packId));
                return;
            }

            long contentLen = conn.getContentLengthLong();
            long total = (expectedSize > 0) ? expectedSize
                    : (contentLen >= 0 ? existing + contentLen : -1L);

            logger.info(String.format("[ServerPacks] Downloading '%s' from %s (resume=%s, total=%s)",
                    packId, packUrl, append, total > 0 ? formatBytes(total) : "unknown"));

            long downloaded = existing;
            long nextByteMark = downloaded + PROGRESS_BYTE_STEP;
            int nextPctMark = total > 0 ? (int) (downloaded * 100L / total) + 10 : -1;

            try (InputStream is = conn.getInputStream();
                 OutputStream os = Files.newOutputStream(partFile,
                         append ? StandardOpenOption.APPEND : StandardOpenOption.CREATE,
                         append ? StandardOpenOption.WRITE : StandardOpenOption.TRUNCATE_EXISTING)) {
                byte[] buf = new byte[16 * 1024];
                int n;
                while ((n = is.read(buf)) != -1) {
                    os.write(buf, 0, n);
                    downloaded += n;

                    if (total > 0) {
                        int pct = (int) (downloaded * 100L / total);
                        if (pct >= nextPctMark && downloaded >= nextByteMark) {
                            ServerPacksClientService.consoleOutput(String.format(
                                    "[ServerPacks] %s: %d%% (%s / %s)",
                                    packId, pct, formatBytes(downloaded), formatBytes(total)));
                            nextPctMark = pct + 10;
                            nextByteMark = downloaded + PROGRESS_BYTE_STEP;
                        }
                    } else if (downloaded >= nextByteMark) {
                        ServerPacksClientService.consoleOutput(String.format(
                                "[ServerPacks] %s: %s downloaded", packId, formatBytes(downloaded)));
                        nextByteMark = downloaded + PROGRESS_BYTE_STEP;
                    }
                }
            }

            if (expectedSize > 0 && Files.size(partFile) != expectedSize) {
                logger.log(Level.SEVERE, String.format(
                        "[ServerPacks] %s size mismatch after download: got %d, expected %d — leaving .part for retry",
                        packId, Files.size(partFile), expectedSize));
                return;
            }

            Path tmpName = Paths.get("packs", packId + ".tmp");
            Files.move(partFile, tmpName, StandardCopyOption.REPLACE_EXISTING);
            ServerPacksClientService.consoleOutput(String.format(
                    "[ServerPacks] %s: download complete (%s)", packId, formatBytes(Files.size(tmpName))));

            handler.onComplete(packId, tmpName);

        } catch (IOException e) {
            logger.log(Level.SEVERE, String.format("[ServerPacks] Failed to download pack '%s': %s",
                    packId, e.getMessage()), e);
        }
    }

    private static String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024L * 1024L) return String.format("%.1f KiB", bytes / 1024.0);
        if (bytes < 1024L * 1024L * 1024L) return String.format("%.1f MiB", bytes / (1024.0 * 1024.0));
        return String.format("%.2f GiB", bytes / (1024.0 * 1024.0 * 1024.0));
    }
}
