package com.garward.wurmmodloader.client.api.events.client;

import com.garward.wurmmodloader.client.api.events.base.Event;

/**
 * Fires whenever the server pushes a line into the client's event/chat tabs
 * (vanilla calls {@code ServerConnectionListenerClass.textMessage}). Provides
 * the destination window, the plain text (multicolor segments concatenated),
 * and the message-type byte.
 *
 * <p>Cancellable: cancelling stops the message from being added to the HUD's
 * text log. Useful for filter / mute mods.
 *
 * @since 0.4.0
 */
public class ClientEventMessageReceivedEvent extends Event {

    private final String window;
    private final String text;
    private final byte messageType;

    public ClientEventMessageReceivedEvent(String window, String text, byte messageType) {
        super(true);
        this.window = window;
        this.text = text;
        this.messageType = messageType;
    }

    /** The chat window / tab the server targeted (e.g. "Event", "Combat"). */
    public String getWindow() { return window; }

    /** The plain-text payload (multicolor segments are pre-joined). */
    public String getText() { return text; }

    /** Vanilla {@code MessageServer} type byte. */
    public byte getMessageType() { return messageType; }

    @Override
    public String toString() {
        return "ClientEventMessageReceivedEvent{window=" + window
                + ", text=" + text + ", type=" + messageType + "}";
    }
}
