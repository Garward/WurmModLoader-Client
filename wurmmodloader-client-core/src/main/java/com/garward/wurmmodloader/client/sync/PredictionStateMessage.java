package com.garward.wurmmodloader.client.sync;

import java.nio.ByteBuffer;

/**
 * Client → Server message containing predicted position for debugging.
 *
 * <p>Optional message that allows the server to compare client predictions
 * with authoritative simulation. Useful for tuning prediction parameters.
 *
 * @since 0.2.0
 */
public class PredictionStateMessage {
    private final long seqId;
    private final float predictedX;
    private final float predictedY;
    private final float predictedHeight;

    public PredictionStateMessage(long seqId, float predictedX, float predictedY, float predictedHeight) {
        this.seqId = seqId;
        this.predictedX = predictedX;
        this.predictedY = predictedY;
        this.predictedHeight = predictedHeight;
    }

    public long getSeqId() {
        return seqId;
    }

    public float getPredictedX() {
        return predictedX;
    }

    public float getPredictedY() {
        return predictedY;
    }

    public float getPredictedHeight() {
        return predictedHeight;
    }

    /**
     * Serialize to ByteBuffer for network transmission.
     */
    public void writeTo(ByteBuffer buffer) {
        buffer.put(WMLSyncMessageType.PREDICTION_STATE.getId());
        buffer.putLong(seqId);
        buffer.putFloat(predictedX);
        buffer.putFloat(predictedY);
        buffer.putFloat(predictedHeight);
    }

    /**
     * Deserialize from ByteBuffer (assumes message type byte already read).
     */
    public static PredictionStateMessage readFrom(ByteBuffer buffer) {
        long seqId = buffer.getLong();
        float x = buffer.getFloat();
        float y = buffer.getFloat();
        float height = buffer.getFloat();
        return new PredictionStateMessage(seqId, x, y, height);
    }

    @Override
    public String toString() {
        return String.format("PredictionState[seq=%d, predicted=(%.2f, %.2f, %.2f)]",
                seqId, predictedX, predictedY, predictedHeight);
    }
}
