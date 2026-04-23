package com.garward.wurmmodloader.client.api.events.client;

import com.garward.wurmmodloader.client.api.events.base.Event;

/**
 * Fired every time {@code PlayerAction.getName()} is called on the client.
 * Lets mods rewrite the name shown in right-click menus, hotbar tooltips, and
 * keybind dialogs — e.g. appending the numeric action id for discoverability.
 *
 * <p>Call {@link #setOverrideName(String)} from a subscriber to replace the
 * label. Last writer wins; leave it {@code null} to keep the original. Fires
 * extremely often (every menu render), so subscribers must be cheap.
 *
 * @since 0.3.0
 */
public class PlayerActionNameResolvedEvent extends Event {

    private final short actionId;
    private final String originalName;
    private String overrideName;

    public PlayerActionNameResolvedEvent(short actionId, String originalName) {
        this.actionId = actionId;
        this.originalName = originalName;
    }

    public short getActionId() { return actionId; }

    public String getOriginalName() { return originalName; }

    public String getOverrideName() { return overrideName; }

    public void setOverrideName(String overrideName) { this.overrideName = overrideName; }
}
