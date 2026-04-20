package com.garward.wurmmodloader.client.api.events.client.movement;

import com.garward.wurmmodloader.client.api.events.base.Event;

/**
 * Fired on the client whenever a key press or release relevant to movement is detected.
 * <p>
 * This is a thin data event: it only reports raw input and does not contain any logic.
 * Client prediction / input buffering should subscribe to this event.
 */
public final class ClientMovementIntentEvent extends Event {

    private final int keyCode;
    private final char keyChar;
    private final boolean pressed;

    public ClientMovementIntentEvent(int keyCode, char keyChar, boolean pressed) {
        super(false); // not cancellable
        this.keyCode = keyCode;
        this.keyChar = keyChar;
        this.pressed = pressed;
    }

    public int getKeyCode() {
        return keyCode;
    }

    public char getKeyChar() {
        return keyChar;
    }

    public boolean isPressed() {
        return pressed;
    }

    @Override
    public String toString() {
        return "ClientMovementIntentEvent{" +
            "keyCode=" + keyCode +
            ", keyChar=" + keyChar +
            ", pressed=" + pressed +
            '}';
    }
}
