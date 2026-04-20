package com.garward.wurmmodloader.client.modcomm;

import com.wurmonline.client.WurmClientBase;
import com.wurmonline.client.comm.SimpleServerConnectionClass;
import com.wurmonline.communication.SocketConnection;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Client-side ModComm, mirror of the server's
 * {@code com.garward.wurmmodloader.modcomm.ModComm}.
 *
 * <p>The wire protocol is identical. The client hooks
 * {@code SimpleServerConnectionClass.reallyHandle(int, ByteBuffer)} so
 * packets with the {@link ModCommConstants#CMD_MODCOMM} command are diverted
 * to {@link ModCommHandler}, and {@code reallyHandleCmdMessage} so the
 * server's ":Event" banner triggers the handshake.
 *
 * <p>Bytecode installation is done by {@code SimpleServerConnectionModCommPatch}
 * in the patcher; call {@link #init()} once at client bootstrap so channels
 * registered before connect are visible when the handshake runs.
 */
public class ModComm {
    static final HashMap<String, Channel> channels = new HashMap<>();
    static final HashMap<Integer, Channel> idMap = new HashMap<>();

    static byte serverVersion = -1;

    private static final Logger logger = Logger.getLogger(ModComm.class.getName());
    private static Field fConnection;
    private static Field fClientObject;

    /**
     * Register a channel. Safe to call before {@link #init()} — channels
     * registered pre-connect get activated during the server handshake.
     */
    public static Channel registerChannel(String name, IChannelListener listener) {
        if (channels.containsKey(name)) {
            throw new RuntimeException(String.format("Channel %s already registered", name));
        }
        Channel ch = new Channel(name, listener);
        channels.put(name, ch);
        logger.info("[ModComm] Registered channel " + name);
        return ch;
    }

    /**
     * Marks the ModComm client subsystem as initialized. The actual
     * {@code reallyHandle} bytecode hook is installed by
     * {@code SimpleServerConnectionModCommPatch} at client-jar patch time,
     * so this method is now a no-op kept for parity with the server API.
     */
    public static void init() {
        logger.info("[ModComm] Client ModComm init (channels=" + channels.size() + ")");
    }

    static SocketConnection getServerConnection() {
        try {
            if (fClientObject == null) {
                fClientObject = WurmClientBase.class.getDeclaredField("clientObject");
                fClientObject.setAccessible(true);
            }
            WurmClientBase client = (WurmClientBase) fClientObject.get(null);
            if (client == null) {
                throw new IllegalStateException("WurmClientBase.clientObject is null; no active client");
            }
            SimpleServerConnectionClass serverConnection = client.getServerConnection();
            if (fConnection == null) {
                fConnection = SimpleServerConnectionClass.class.getDeclaredField("connection");
                fConnection.setAccessible(true);
            }
            return (SocketConnection) fConnection.get(serverConnection);
        } catch (IllegalAccessException | NoSuchFieldException e) {
            throw new RuntimeException(e);
        }
    }

    static void logException(String msg, Throwable e) {
        logger.log(Level.SEVERE, msg, e);
    }

    static void logWarning(String msg) {
        logger.log(Level.WARNING, msg);
    }

    static void logInfo(String msg) {
        logger.log(Level.INFO, msg);
    }
}
