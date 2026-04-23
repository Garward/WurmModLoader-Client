package com.garward.wurmmodloader.client.api.events.gui;

import com.garward.wurmmodloader.client.api.events.base.Event;

/**
 * Fired after {@code CompassComponent.gameTick()} — one shot per frame.
 * Subscribers can override the compass's quality/stability state
 * (e.g. to force a max-quality, always-on compass).
 *
 * @since 0.3.0
 */
public class CompassComponentTickEvent extends Event {

    private final Object component;

    public CompassComponentTickEvent(Object component) {
        super(false);
        this.component = component;
    }

    /** The {@code com.wurmonline.client.renderer.gui.CompassComponent} instance. */
    public Object getComponent() {
        return component;
    }

    @Override
    public String toString() {
        return "CompassComponentTick[" + component + "]";
    }
}
