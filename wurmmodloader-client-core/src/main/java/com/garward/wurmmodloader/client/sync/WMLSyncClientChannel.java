package com.garward.wurmmodloader.client.sync;

import com.garward.wurmmodloader.client.modloader.ProxyClientHook;

import java.nio.ByteBuffer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Client-side WML_SYNC ModComm channel for prediction synchronization.
 *
 * <p>This class provides methods for:
 * <ul>
 *   <li>Sending movement intent to the server</li>
 *   <li>Sending predicted position for debugging</li>
 *   <li>Receiving position corrections from the server</li>
 * </ul>
 *
 * <p><b>Usage (Client-side Mod):</b>
 * <pre>{@code
 * // Send movement intent on key press
 * long seqId = System.currentTimeMillis();
 * byte inputState = MovementIntentMessage.FLAG_MOVE_FORWARD;
 * WMLSyncClientChannel.sendMovementIntent(seqId, inputState);
 *
 * // Subscribe to corrections
 * @SubscribeEvent
 * public void onServerCorrection(ServerCorrectionReceivedEvent event) {
 *     // Reconcile prediction with server position
 * }
 * }</pre>
 *
 * <p><b>Note:</b> This is a minimal ModComm client implementation. It assumes the server
 * has the WML_SYNC channel registered and will accept messages.
 *
 * @since 0.2.0
 */
public class WMLSyncClientChannel {
    private static final Logger logger = Logger.getLogger(WMLSyncClientChannel.class.getName());
    private static final String CHANNEL_NAME = "WML_SYNC";

    private static boolean initialized = false;

    /**
     * Initialize the WML_SYNC client channel. Called during client startup.
     *
     * <p>This method registers the channel and sets up message handlers.
     */
    public static void initialize() {
        if (!initialized) {
            initialized = true;
            logger.info("[WMLSync] WML_SYNC client channel initialized");
            // TODO: Actual ModComm client registration when we have full ModComm client support
            // For now, we'll just mark as initialized and handle messages manually
        }
    }

    /**
     * Send movement intent to the server.
     *
     * @param seqId Unique sequence ID for this input (use System.currentTimeMillis() or similar)
     * @param inputState Bitfield of input flags (see MovementIntentMessage constants)
     */
    public static void sendMovementIntent(long seqId, byte inputState) {
        if (!initialized) {
            logger.warning("[WMLSync] Cannot send movement intent - channel not initialized");
            return;
        }

        try {
            MovementIntentMessage message = new MovementIntentMessage(seqId, inputState);

            // Create ByteBuffer for message
            ByteBuffer buffer = ByteBuffer.allocate(10); // 1 (type) + 8 (seqId) + 1 (inputState)
            message.writeTo(buffer);
            buffer.flip();

            // TODO: Actually send via ModComm when client ModComm is implemented
            // For now, just log that we would send
            logger.fine("[WMLSync] Would send movement intent: " + message);

            // Placeholder for actual send:
            // modCommClient.sendMessage(CHANNEL_NAME, buffer);

        } catch (Exception e) {
            logger.log(Level.WARNING, "[WMLSync] Failed to send movement intent", e);
        }
    }

    /**
     * Send predicted position to server for debugging.
     *
     * @param seqId Sequence ID matching the movement intent
     * @param predictedX Predicted X position
     * @param predictedY Predicted Y position
     * @param predictedHeight Predicted height
     */
    public static void sendPredictionState(long seqId, float predictedX, float predictedY, float predictedHeight) {
        if (!initialized) {
            logger.warning("[WMLSync] Cannot send prediction state - channel not initialized");
            return;
        }

        try {
            PredictionStateMessage message = new PredictionStateMessage(seqId, predictedX, predictedY, predictedHeight);

            // Create ByteBuffer for message
            ByteBuffer buffer = ByteBuffer.allocate(21); // 1 (type) + 8 (seqId) + 12 (3 floats)
            message.writeTo(buffer);
            buffer.flip();

            // TODO: Actually send via ModComm when client ModComm is implemented
            logger.fine("[WMLSync] Would send prediction state: " + message);

        } catch (Exception e) {
            logger.log(Level.WARNING, "[WMLSync] Failed to send prediction state", e);
        }
    }

    /**
     * Handle incoming message from server.
     *
     * <p>This is called by the ModComm client when a message is received on the WML_SYNC channel.
     *
     * @param message ByteBuffer containing the message data
     */
    public static void handleMessage(ByteBuffer message) {
        try {
            if (!message.hasRemaining()) {
                logger.warning("[WMLSync] Received empty message from server");
                return;
            }

            // Read message type
            byte typeId = message.get();
            WMLSyncMessageType type = WMLSyncMessageType.fromId(typeId);

            switch (type) {
                case SERVER_CORRECTION:
                    handleServerCorrection(message);
                    break;

                case MOVEMENT_INTENT:
                case PREDICTION_STATE:
                    // Client shouldn't receive these from server
                    logger.warning("[WMLSync] Received unexpected message type from server: " + type);
                    break;

                default:
                    logger.warning("[WMLSync] Unknown message type from server: " + typeId);
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, "[WMLSync] Error handling message from server", e);
        }
    }

    /**
     * Handle server correction message.
     */
    private static void handleServerCorrection(ByteBuffer message) {
        ServerCorrectionMessage msg = ServerCorrectionMessage.readFrom(message);

        logger.fine("[WMLSync] Received server correction: " + msg);

        // Convert ServerCorrectionMessage.CorrectionReason to event CorrectionReason
        com.garward.wurmmodloader.client.api.events.sync.ServerCorrectionReceivedEvent.CorrectionReason eventReason =
            convertReason(msg.getReason());

        // Fire event into EventBus via ProxyClientHook
        ProxyClientHook.fireServerCorrectionReceivedEvent(
            msg.getSeqId(),
            msg.getX(),
            msg.getY(),
            msg.getHeight(),
            eventReason
        );
    }

    /**
     * Convert message CorrectionReason to event CorrectionReason.
     */
    private static com.garward.wurmmodloader.client.api.events.sync.ServerCorrectionReceivedEvent.CorrectionReason
        convertReason(ServerCorrectionMessage.CorrectionReason msgReason) {
        switch (msgReason) {
            case POSITION_DEVIATION:
                return com.garward.wurmmodloader.client.api.events.sync.ServerCorrectionReceivedEvent.CorrectionReason.POSITION_DEVIATION;
            case COLLISION:
                return com.garward.wurmmodloader.client.api.events.sync.ServerCorrectionReceivedEvent.CorrectionReason.COLLISION;
            case STAMINA_EXHAUSTED:
                return com.garward.wurmmodloader.client.api.events.sync.ServerCorrectionReceivedEvent.CorrectionReason.STAMINA_EXHAUSTED;
            case INVALID_TERRAIN:
                return com.garward.wurmmodloader.client.api.events.sync.ServerCorrectionReceivedEvent.CorrectionReason.INVALID_TERRAIN;
            case SPEED_LIMIT:
                return com.garward.wurmmodloader.client.api.events.sync.ServerCorrectionReceivedEvent.CorrectionReason.SPEED_LIMIT;
            case OTHER:
            default:
                return com.garward.wurmmodloader.client.api.events.sync.ServerCorrectionReceivedEvent.CorrectionReason.OTHER;
        }
    }
}
