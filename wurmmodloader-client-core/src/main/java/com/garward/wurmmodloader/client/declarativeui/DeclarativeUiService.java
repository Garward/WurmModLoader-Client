package com.garward.wurmmodloader.client.declarativeui;

import com.garward.wurmmodloader.client.api.events.base.SubscribeEvent;
import com.garward.wurmmodloader.client.api.events.lifecycle.ClientInitEvent;
import com.garward.wurmmodloader.client.api.events.lifecycle.ClientTickEvent;
import com.garward.wurmmodloader.client.api.gui.ModHud;
import com.garward.wurmmodloader.client.modcomm.Channel;
import com.garward.wurmmodloader.client.modcomm.IChannelListener;
import com.garward.wurmmodloader.client.modcomm.ModComm;
import com.garward.wurmmodloader.client.modcomm.PacketReader;
import com.garward.wurmmodloader.client.modcomm.PacketWriter;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Built-in client-side service for the {@code com.garward.ui} declarative UI
 * channel. Receives server-authored window trees, instantiates them on the
 * main thread, and sends button-click actions back up. Auto-registered by
 * {@code ProxyClientHook} during modloader bootstrap — no separate mod jar
 * required. See {@link UiProtocol} for the wire format.
 */
public class DeclarativeUiService {

    private static final Logger logger = Logger.getLogger(DeclarativeUiService.class.getName());

    private static final ConcurrentLinkedQueue<Runnable> mainThreadTasks = new ConcurrentLinkedQueue<>();
    private static final Map<String, MountedWindow> windows = new HashMap<>();
    private static volatile Channel channel;

    @SubscribeEvent
    public void onClientInit(ClientInitEvent event) {
        try {
            channel = ModComm.registerChannel(UiProtocol.CHANNEL, new UiListener());
            logger.info("[DeclarativeUI] Registered channel: " + UiProtocol.CHANNEL);
        } catch (Throwable t) {
            logger.log(Level.SEVERE, "[DeclarativeUI] init failed", t);
        }
    }

    @SubscribeEvent
    public void onTick(ClientTickEvent event) {
        Runnable task;
        while ((task = mainThreadTasks.poll()) != null) {
            try {
                task.run();
            } catch (Throwable t) {
                logger.log(Level.SEVERE, "[DeclarativeUI] main-thread task failed", t);
            }
        }
    }

    private static void runOnMainThread(Runnable r) {
        mainThreadTasks.offer(r);
    }

    private static final class UiListener implements IChannelListener {
        @Override
        public void handleMessage(ByteBuffer message) {
            try (PacketReader reader = new PacketReader(message)) {
                byte op = reader.readByte();
                switch (op) {
                    case UiProtocol.OP_MOUNT:    handleMount(reader); break;
                    case UiProtocol.OP_UNMOUNT:  handleUnmount(reader.readUTF()); break;
                    case UiProtocol.OP_BIND:     handleBind(reader); break;
                    case UiProtocol.OP_SHOW:     handleToggle(reader.readUTF(), true); break;
                    case UiProtocol.OP_HIDE:     handleToggle(reader.readUTF(), false); break;
                    default:
                        logger.warning("[DeclarativeUI] unknown opcode: 0x" + Integer.toHexString(op & 0xff));
                }
            } catch (IOException e) {
                logger.log(Level.SEVERE, "[DeclarativeUI] failed to decode packet", e);
            }
        }
    }

    private static void handleMount(PacketReader reader) throws IOException {
        final String windowId = reader.readUTF();
        final String title = reader.readUTF();
        final int width = reader.readInt();
        final int height = reader.readInt();
        final int[] widgetCount = { 0 };
        final WidgetNode root = readTree(reader, 0, widgetCount);
        if (root == null) {
            logger.warning("[DeclarativeUI] MOUNT " + windowId + " dropped (tree rejected)");
            return;
        }
        runOnMainThread(() -> {
            MountedWindow existing = windows.remove(windowId);
            if (existing != null && existing.window != null) {
                try { ModHud.get().toggle(existing.window); } catch (Throwable ignored) {}
            }
            if (!ModHud.get().isReady()) {
                logger.warning("[DeclarativeUI] HUD not ready — dropping MOUNT " + windowId);
                return;
            }
            MountedWindow mw = WindowBuilder.build(windowId, title, width, height, root,
                    action -> sendAction(windowId, action, ""));
            ModHud.get().register(mw.window);
            // register() already adds the component to the HUD's visible list;
            // calling toggle() here would immediately remove it.
            windows.put(windowId, mw);
            logger.info("[DeclarativeUI] mounted " + windowId + " (\"" + title + "\")");
        });
    }

    private static void handleUnmount(String windowId) {
        runOnMainThread(() -> {
            MountedWindow mw = windows.remove(windowId);
            if (mw != null && mw.window != null) {
                try { ModHud.get().toggle(mw.window); } catch (Throwable ignored) {}
            }
        });
    }

    private static void handleBind(PacketReader reader) throws IOException {
        final String windowId = reader.readUTF();
        final int n = reader.readShort() & 0xffff;
        final Map<String, String> values = new LinkedHashMap<>(n);
        for (int i = 0; i < n; i++) {
            values.put(reader.readUTF(), reader.readUTF());
        }
        runOnMainThread(() -> {
            MountedWindow mw = windows.get(windowId);
            if (mw != null) mw.applyBindings(values);
        });
    }

    private static void handleToggle(String windowId, boolean show) {
        runOnMainThread(() -> {
            MountedWindow mw = windows.get(windowId);
            if (mw == null || mw.window == null) return;
            // Server is expected to send SHOW/HIDE pairs that alternate, not
            // absolute states — toggleComponent flips visibility.
            ModHud.get().toggle(mw.window);
        });
    }

    private static WidgetNode readTree(PacketReader reader, int depth, int[] widgetCount) throws IOException {
        if (depth > UiProtocol.MAX_TREE_DEPTH) {
            logger.warning("[DeclarativeUI] tree depth exceeds " + UiProtocol.MAX_TREE_DEPTH + ", truncating");
            return null;
        }
        String type = reader.readUTF();
        WidgetNode node = new WidgetNode(type);

        int propCount = reader.readShort() & 0xffff;
        for (int i = 0; i < propCount; i++) {
            String k = reader.readUTF();
            String v = reader.readUTF();
            node.props.put(k, v);
        }

        int childCount = reader.readShort() & 0xffff;
        for (int i = 0; i < childCount; i++) {
            if (++widgetCount[0] > UiProtocol.MAX_WIDGETS_PER_WINDOW) {
                logger.warning("[DeclarativeUI] widget cap " + UiProtocol.MAX_WIDGETS_PER_WINDOW + " hit, truncating");
                return null;
            }
            WidgetNode child = readTree(reader, depth + 1, widgetCount);
            if (child == null) return null;
            node.children.add(child);
        }
        return node;
    }

    private static void sendAction(String windowId, String action, String payload) {
        Channel c = channel;
        if (c == null || !c.isActive()) return;
        try (PacketWriter writer = new PacketWriter()) {
            writer.writeByte(UiProtocol.OP_ACTION);
            writer.writeUTF(windowId);
            writer.writeUTF(action);
            writer.writeUTF(payload == null ? "" : payload);
            c.sendMessage(writer.getBytes());
        } catch (IOException e) {
            logger.log(Level.WARNING, "[DeclarativeUI] failed to send ACTION " + action, e);
        }
    }
}
