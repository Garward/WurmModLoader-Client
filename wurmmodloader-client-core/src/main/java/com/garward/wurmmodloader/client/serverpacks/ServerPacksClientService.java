package com.garward.wurmmodloader.client.serverpacks;

import com.garward.wurmmodloader.client.api.events.base.SubscribeEvent;
import com.garward.wurmmodloader.client.api.events.client.ClientConsoleInputEvent;
import com.garward.wurmmodloader.client.api.events.lifecycle.ClientInitEvent;
import com.garward.wurmmodloader.client.api.events.lifecycle.ClientTickEvent;
import com.garward.wurmmodloader.client.api.events.map.ClientHUDInitializedEvent;
import com.wurmonline.client.renderer.gui.HeadsUpDisplay;
import com.garward.wurmmodloader.client.modcomm.Channel;
import com.garward.wurmmodloader.client.modcomm.IChannelListener;
import com.garward.wurmmodloader.client.modcomm.ModComm;
import com.garward.wurmmodloader.client.modcomm.PacketReader;
import com.garward.wurmmodloader.client.modcomm.PacketWriter;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Built-in framework service that handles server-pushed pack downloads —
 * protocol-compatible with Ago-hosted servers.
 *
 * <p>Registers two ModComm channels:
 * <ul>
 *   <li>{@code com.garward.serverpacks} — canonical, includes per-pack
 *       SHA-256 + size for cache verification.</li>
 *   <li>{@code ago.serverpacks} — legacy alias matching upstream tyoda/ago1024
 *       wire format ({@code int n; for n: UTF packId; UTF uri;}).</li>
 * </ul>
 *
 * <p>Pack installation (JarPack construction + splice into
 * {@code Resources.packs} + resolved/unresolved flush) is done via direct
 * reflection against the vanilla client, mirroring the approach in
 * {@code org.gotti.wurmunlimited.modsupport.packs.ModPacks} without depending
 * on the legacy launcher's modsupport jar.
 *
 * <p>Promoted from the {@code mods/serverpacks} mod into the framework so
 * {@link PackAssetResolver} sits in the framework classloader — the previous
 * arrangement broke cross-classloader {@code Class.forName} lookups from
 * scheduler threads (icon registry rebuild, declarativeui {@code pack:}
 * URI resolution).
 */
public final class ServerPacksClientService {

    private static final Logger logger = Logger.getLogger(ServerPacksClientService.class.getName());
    private static final byte CMD_REFRESH = 0x01;

    public static final String CHANNEL = "com.garward.serverpacks";
    public static final String LEGACY_CHANNEL = "ago.serverpacks";

    private static final ConcurrentLinkedQueue<Runnable> mainThreadTasks = new ConcurrentLinkedQueue<>();
    private static volatile Channel channel;
    private static volatile Channel legacyChannel;
    private static volatile HeadsUpDisplay hud;

    @SubscribeEvent
    public void onHudInit(ClientHUDInitializedEvent event) {
        hud = (HeadsUpDisplay) event.getHud();
    }

    static void consoleOutput(String msg) {
        HeadsUpDisplay h = hud;
        if (h != null) {
            try { h.consoleOutput(msg); } catch (Throwable ignored) {}
        }
        logger.info(msg);
    }

    @SubscribeEvent
    public void onClientInit(ClientInitEvent event) {
        try {
            PackInstaller.init();
            channel = ModComm.registerChannel(CHANNEL, new ServerPacksListener(true));
            legacyChannel = ModComm.registerChannel(LEGACY_CHANNEL, new ServerPacksListener(false));
            logger.info("[ServerPacks] Registered channels: " + CHANNEL + " + " + LEGACY_CHANNEL + " (alias)");
        } catch (Throwable t) {
            logger.log(Level.SEVERE, "[ServerPacks] init failed", t);
        }
    }

    /** Main-thread pump — pack install must touch Resources from the client thread. */
    @SubscribeEvent
    public void onTick(ClientTickEvent event) {
        Runnable task;
        while ((task = mainThreadTasks.poll()) != null) {
            try {
                task.run();
            } catch (Throwable t) {
                logger.log(Level.SEVERE, "[ServerPacks] main-thread task failed", t);
            }
        }
    }

    static void runOnMainThread(Runnable r) {
        mainThreadTasks.offer(r);
    }

    /**
     * Diagnostic + admin console commands for pack management.
     *
     * <ul>
     *   <li>{@code sp_packs} — list installed packs in lookup order.</li>
     *   <li>{@code sp_reload} — force cache invalidation + re-resolve.</li>
     *   <li>{@code sp_probe <mappingKey>} — show which pack owns a key.</li>
     *   <li>{@code sp_installpack <packId> <url>} — manually pull a pack
     *       (mirrors upstream tyoda's {@code mod serverpacks installpack}).</li>
     *   <li>{@code sp_refresh} — send CMD_REFRESH to the server to re-sync
     *       creatures and models (mirrors upstream {@code mod serverpacks refresh}).</li>
     * </ul>
     */
    @SubscribeEvent
    public void onConsoleInput(ClientConsoleInputEvent event) {
        String cmd = event.getCommand();
        String[] args = event.getArgs();
        if (cmd == null) return;

        if ("sp_packs".equals(cmd)) {
            runOnMainThread(PackInstaller::dumpPacks);
            event.cancel();
        } else if ("sp_reload".equals(cmd)) {
            runOnMainThread(PackInstaller::reloadAll);
            event.cancel();
        } else if ("sp_probe".equals(cmd)) {
            if (args == null || args.length < 2) {
                consoleOutput("Usage: sp_probe <mapping.key>");
            } else {
                final String key = args[1];
                runOnMainThread(() -> PackInstaller.probe(key));
            }
            event.cancel();
        } else if ("sp_installpack".equals(cmd)) {
            if (args == null || args.length < 3) {
                consoleOutput("Usage: sp_installpack <packId> <url>");
            } else {
                installServerPack(args[1], args[2]);
            }
            event.cancel();
        } else if ("sp_refresh".equals(cmd)) {
            scheduleRefreshModels();
            consoleOutput("[ServerPacks] CMD_REFRESH queued");
            event.cancel();
        }
    }

    private static final class ServerPacksListener implements IChannelListener {
        private final boolean canonical;
        ServerPacksListener(boolean canonical) { this.canonical = canonical; }

        @Override
        public void handleMessage(ByteBuffer message) {
            try (PacketReader reader = new PacketReader(message)) {
                int n = reader.readInt();
                while (n-- > 0) {
                    String packId = reader.readUTF();
                    String uri = reader.readUTF();
                    String sha256 = null;
                    long size = -1L;
                    if (canonical) {
                        sha256 = reader.readUTF();
                        if (sha256.isEmpty()) sha256 = null;
                        size = reader.readLong();
                    }
                    logger.info(String.format("[ServerPacks] Got server pack %s (%s) sha256=%s size=%d",
                            packId, uri, sha256, size));
                    installServerPack(packId, uri, sha256, size);
                }
                scheduleRefreshModels();
            } catch (IOException e) {
                logger.log(Level.SEVERE, "[ServerPacks] failed to decode serverpacks packet", e);
            }
        }
    }

    private static void installServerPack(String packId, String packUri) {
        installServerPack(packId, packUri, null, -1L);
    }

    private static void installServerPack(String packId, String packUri, String expectedSha256, long expectedSize) {
        final URL packUrl;
        try {
            packUrl = new URL(packUri);
        } catch (MalformedURLException e) {
            logger.log(Level.SEVERE, "[ServerPacks] bad pack URI: " + packUri, e);
            return;
        }

        Path existing = Paths.get("packs", packId + ".jar");
        boolean force = packUri.contains("force=true") || packUri.contains("force=1");
        if (!force && Files.isRegularFile(existing)) {
            if (expectedSha256 != null) {
                try {
                    long sz = Files.size(existing);
                    if (expectedSize >= 0 && sz != expectedSize) {
                        logger.info("[ServerPacks] cached " + packId + " size mismatch (" + sz + " vs " + expectedSize + "), redownloading");
                    } else {
                        String actual = sha256Hex(existing);
                        if (expectedSha256.equalsIgnoreCase(actual)) {
                            logger.info("[ServerPacks] cached " + packId + " matches manifest, skipping download");
                            runOnMainThread(() -> PackInstaller.enableDownloadedPack(packId, packUrl, existing));
                            return;
                        }
                        logger.info("[ServerPacks] cached " + packId + " hash mismatch, redownloading");
                    }
                } catch (IOException e) {
                    logger.log(Level.WARNING, "[ServerPacks] failed to verify cached pack " + packId + ", redownloading", e);
                }
            } else {
                runOnMainThread(() -> PackInstaller.enableDownloadedPack(packId, packUrl, existing));
                return;
            }
        }

        Thread t = new Thread(new PackDownloader(packUrl, packId, expectedSize, (id, tempFile) ->
                runOnMainThread(() -> {
                    try {
                        Path packFile = Paths.get("packs", id + ".jar");
                        PackInstaller.closePack(packFile);
                        Files.move(tempFile, packFile, StandardCopyOption.REPLACE_EXISTING);
                        PackInstaller.enableDownloadedPack(id, packUrl, packFile);
                    } catch (IOException e) {
                        logger.log(Level.SEVERE, "[ServerPacks] failed to install pack " + id, e);
                    }
                })), "ServerPacks-Downloader-" + packId);
        t.setDaemon(true);
        t.start();
    }

    private static String sha256Hex(Path file) throws IOException {
        try (InputStream is = Files.newInputStream(file)) {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] buf = new byte[8192];
            int n;
            while ((n = is.read(buf)) != -1) {
                if (n > 0) md.update(buf, 0, n);
            }
            byte[] d = md.digest();
            StringBuilder sb = new StringBuilder(d.length * 2);
            for (byte b : d) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IOException(e);
        }
    }

    private static void scheduleRefreshModels() {
        runOnMainThread(() -> {
            Channel c = pickActiveChannel();
            if (c == null) return;
            try (PacketWriter writer = new PacketWriter()) {
                writer.writeByte(CMD_REFRESH);
                c.sendMessage(writer.getBytes());
            } catch (IOException e) {
                logger.log(Level.WARNING, "[ServerPacks] failed to send CMD_REFRESH", e);
            }
        });
    }

    /** Canonical wins; falls back to legacy alias if that's what the server registered. */
    private static Channel pickActiveChannel() {
        Channel c = channel;
        if (c != null && c.isActive()) return c;
        Channel l = legacyChannel;
        if (l != null && l.isActive()) return l;
        return null;
    }
}
