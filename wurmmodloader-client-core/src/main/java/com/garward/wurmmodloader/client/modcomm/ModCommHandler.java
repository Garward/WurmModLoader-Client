package com.garward.wurmmodloader.client.modcomm;

import com.wurmonline.communication.SocketConnection;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Client-side ModComm dispatcher. Mirrors the wire format of the server's
 * {@code com.garward.wurmmodloader.modcomm.ModCommHandler}.
 *
 * <p>{@link #handlePacket(ByteBuffer)} is called from
 * {@code SimpleServerConnectionClass.reallyHandle} when the CMD_MODCOMM byte
 * is seen. {@link #startHandshake()} is called when the {@code :Event} banner
 * message is received, to advertise our registered channels to the server.
 */
public class ModCommHandler {

    public static void handlePacket(ByteBuffer msg) {
        try {
            byte type = msg.get();
            switch (type) {
                case ModCommConstants.PACKET_MESSAGE:
                    handlePacketMessage(msg);
                    break;
                case ModCommConstants.PACKET_CHANNELS:
                    handlePacketChannels(msg);
                    break;
                default:
                    ModComm.logWarning(String.format("Unknown ModComm packet type %d", type));
            }
        } catch (Exception e) {
            ModComm.logException("Error handling ModComm packet", e);
        }
    }

    public static void startHandshake() {
        try (PacketWriter writer = new PacketWriter()) {
            writer.writeByte(ModCommConstants.CMD_MODCOMM);
            writer.writeByte(ModCommConstants.PACKET_CHANNELS);
            writer.writeByte(ModCommConstants.PROTO_VERSION);
            writer.writeInt(ModComm.channels.size());
            for (Channel channel : ModComm.channels.values()) {
                writer.writeUTF(channel.name);
            }

            SocketConnection conn = ModComm.getServerConnection();
            ByteBuffer buff = conn.getBuffer();
            buff.put(writer.getBytes());
            conn.flush();

            ModComm.logInfo(String.format("Sent ModComm handshake (%d channels)", ModComm.channels.size()));
        } catch (Exception e) {
            ModComm.logException("Error starting ModComm handshake", e);
        }
    }

    private static void handlePacketChannels(ByteBuffer msg) throws IOException {
        PacketReader reader = new PacketReader(msg);
        byte version = reader.readByte();
        ModComm.serverVersion = version;
        int n = reader.readInt();
        ModComm.logInfo(String.format("Server ModComm handshake reply: version=%d, %d channels", version, n));

        java.util.List<Channel> activated = new java.util.ArrayList<>();
        while (n-- > 0) {
            int id = reader.readInt();
            String name = reader.readUTF();
            Channel ch = ModComm.channels.get(name);
            if (ch != null) {
                ch.id = id;
                ModComm.idMap.put(id, ch);
                activated.add(ch);
            }
        }

        for (Channel ch : activated) {
            try {
                ch.listener.onServerConnected();
            } catch (Exception e) {
                ModComm.logException(String.format("Error in channel %s onServerConnected", ch.name), e);
            }
        }
    }

    private static void handlePacketMessage(ByteBuffer msg) {
        int id = msg.getInt();
        Channel ch = ModComm.idMap.get(id);
        if (ch == null) {
            ModComm.logWarning(String.format("Message on unregistered channel id %d", id));
            return;
        }
        if (!ch.isActive()) {
            ModComm.logWarning(String.format("Message on inactive channel %s", ch.name));
            return;
        }
        try {
            ch.listener.handleMessage(msg.slice());
        } catch (Exception e) {
            ModComm.logException(String.format("Error in channel handler %s", ch.name), e);
        }
    }
}
