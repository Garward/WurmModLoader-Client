package com.garward.wurmmodloader.client.serverpacks;

import com.garward.wurmmodloader.client.core.serverpacks.ModIconAtlasStore;
import com.garward.wurmmodloader.client.modcomm.IChannelListener;
import com.garward.wurmmodloader.client.modcomm.ModComm;

import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Client counterpart to
 * {@code com.garward.wurmmodloader.core.icon.IconRegistrySyncChannel}.
 *
 * <p>Decodes the {@code com.garward.icons} {@code PACKET_REGISTRY_SYNC = 1}
 * packet (full registry snapshot), feeds it into
 * {@link ModIconAtlasStore#update(Map)}, and triggers an icon-loader rebuild
 * so the new dynamic sheets land in {@code IconLoader.itemIconImages}.
 *
 * <h3>Wire format</h3>
 * <pre>
 * byte    type = 1
 * int     count
 * count × {
 *   short id
 *   short uriLen
 *   bytes uri (UTF-8, length = uriLen)
 * }
 * </pre>
 *
 * <h3>Rebuild trigger</h3>
 * After {@code update()} the cache has been invalidated but
 * {@code IconLoader.itemIconImages} still references the old, smaller array.
 * We reflectively call {@code IconLoader.clear()} (drops the texture cache)
 * and {@code IconLoader.initIcons()} (the patched version rebuilds with
 * {@code 7 + extraSheetCount()} sheets).
 *
 * <h3>Debounce</h3>
 * The server sends one full snapshot per login, so a single packet → single
 * rebuild is the expected case. We still debounce by 500ms to coalesce any
 * back-to-back resends that show up in retry/reconnect paths and to give the
 * resource manager a moment to settle if the packet beats it.
 *
 * <h3>Pack-arrival rebuilds</h3>
 * Not handled here — {@code PackInstaller.reloadResources()} in the
 * serverpacks mod already calls {@code IconLoader.clear() + initIcons()} when
 * a pack lands; the patched {@code initIcons} re-resolves dynamic URIs from
 * the now-on-disk pack automatically.
 *
 * @since 0.4.1
 */
public final class IconRegistryClientChannel {

    private static final Logger logger = Logger.getLogger(IconRegistryClientChannel.class.getName());

    private static final String CHANNEL_NAME = "com.garward.icons";
    private static final byte PACKET_REGISTRY_SYNC = 1;

    private static boolean initialized = false;

    private static final ScheduledExecutorService rebuildScheduler =
        Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "IconRegistry-rebuild");
            t.setDaemon(true);
            return t;
        });
    private static ScheduledFuture<?> pendingRebuild;

    private IconRegistryClientChannel() {}

    public static synchronized void initialize() {
        if (initialized) return;
        initialized = true;
        try {
            ModComm.registerChannel(CHANNEL_NAME, new IChannelListener() {
                @Override
                public void handleMessage(ByteBuffer message) {
                    handlePacket(message);
                }
            });
            logger.info("[IconRegistryClient] Registered " + CHANNEL_NAME + " channel");
        } catch (Throwable t) {
            logger.log(Level.WARNING,
                "[IconRegistryClient] Failed to register " + CHANNEL_NAME + " channel", t);
        }
    }

    private static void handlePacket(ByteBuffer buffer) {
        try {
            byte type = buffer.get();
            if (type != PACKET_REGISTRY_SYNC) {
                logger.warning("[IconRegistryClient] Unknown packet type: " + type);
                return;
            }
            int count = buffer.getInt();
            Map<Short, String> entries = new HashMap<>(count * 2);
            for (int i = 0; i < count; i++) {
                short id = buffer.getShort();
                short uriLen = buffer.getShort();
                byte[] uriBytes = new byte[uriLen & 0xFFFF];
                buffer.get(uriBytes);
                entries.put(id, new String(uriBytes, StandardCharsets.UTF_8));
            }
            logger.info("[IconRegistryClient] Received " + entries.size() + " icon registrations");

            ModIconAtlasStore.update(entries);
            scheduleRebuild();
        } catch (Throwable t) {
            logger.log(Level.WARNING, "[IconRegistryClient] Failed to parse packet", t);
        }
    }

    private static synchronized void scheduleRebuild() {
        if (pendingRebuild != null) pendingRebuild.cancel(false);
        pendingRebuild = rebuildScheduler.schedule(
            IconRegistryClientChannel::rebuildAtlases,
            500, TimeUnit.MILLISECONDS);
    }

    private static void rebuildAtlases() {
        try {
            Class<?> iconLoader = Class.forName("com.wurmonline.client.resources.textures.IconLoader");
            Method clear = iconLoader.getMethod("clear");
            Method init = iconLoader.getMethod("initIcons");
            clear.invoke(null);
            init.invoke(null);
            logger.info("[IconRegistryClient] IconLoader rebuilt for "
                + ModIconAtlasStore.entries().size() + " dynamic icons ("
                + ModIconAtlasStore.extraSheetCount() + " extra sheets)");
        } catch (Throwable t) {
            logger.log(Level.WARNING, "[IconRegistryClient] IconLoader rebuild failed", t);
        }
    }
}
