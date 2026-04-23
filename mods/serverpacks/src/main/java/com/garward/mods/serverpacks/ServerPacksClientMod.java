package com.garward.mods.serverpacks;

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
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Client-side serverpacks mod — protocol-compatible with Ago-hosted servers.
 *
 * <p>Registers the legacy {@code "ago.serverpacks"} ModComm channel using the
 * same wire format upstream tyoda/ago1024 servers send:
 * <pre>
 *   int n;
 *   for n:  UTF packId;  UTF uri;
 * </pre>
 *
 * <p>Pack installation (JarPack construction + splice into {@code Resources.packs}
 * + resolved/unresolved flush) is done via direct reflection against the vanilla
 * client, mirroring the approach in {@code org.gotti.wurmunlimited.modsupport.packs.ModPacks}
 * without taking a dependency on the legacy launcher's modsupport jar.
 */
public class ServerPacksClientMod {

    private static final Logger logger = Logger.getLogger(ServerPacksClientMod.class.getName());
    private static final byte CMD_REFRESH = 0x01;

    private static final ConcurrentLinkedQueue<Runnable> mainThreadTasks = new ConcurrentLinkedQueue<>();
    private static volatile Channel channel;
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
            channel = ModComm.registerChannel("ago.serverpacks", new AgoServerPacksListener());
            logger.info("[ServerPacks] Registered ago.serverpacks channel");
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
     * Diagnostic console commands for probing pack resolution:
     * <ul>
     *   <li>{@code sp_packs} — list installed packs in lookup order.</li>
     *   <li>{@code sp_probe <mappingKey>} — call Resources.getAllState(key) to
     *       see which pack(s) own the mapping (logs to client log).</li>
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
        }
    }

    private static final class AgoServerPacksListener implements IChannelListener {
        @Override
        public void handleMessage(ByteBuffer message) {
            try (PacketReader reader = new PacketReader(message)) {
                int n = reader.readInt();
                while (n-- > 0) {
                    String packId = reader.readUTF();
                    String uri = reader.readUTF();
                    logger.info(String.format("[ServerPacks] Got server pack %s (%s)", packId, uri));
                    installServerPack(packId, uri);
                }
                scheduleRefreshModels();
            } catch (IOException e) {
                logger.log(Level.SEVERE, "[ServerPacks] failed to decode ago.serverpacks packet", e);
            }
        }
    }

    private static void installServerPack(String packId, String packUri) {
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
            runOnMainThread(() -> PackInstaller.enableDownloadedPack(packId, packUrl, existing));
            return;
        }

        Thread t = new Thread(new PackDownloader(packUrl, packId, (id, tempFile) ->
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

    private static void scheduleRefreshModels() {
        runOnMainThread(() -> {
            Channel c = channel;
            if (c == null || !c.isActive()) return;
            try (PacketWriter writer = new PacketWriter()) {
                writer.writeByte(CMD_REFRESH);
                c.sendMessage(writer.getBytes());
            } catch (IOException e) {
                logger.log(Level.WARNING, "[ServerPacks] failed to send CMD_REFRESH", e);
            }
        });
    }
}
