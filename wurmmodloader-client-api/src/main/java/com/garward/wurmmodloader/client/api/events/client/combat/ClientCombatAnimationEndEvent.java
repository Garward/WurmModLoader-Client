package com.garward.wurmmodloader.client.api.events.client.combat;

import com.garward.wurmmodloader.client.api.events.base.Event;

/**
 * Fired on the client when a combat animation finishes for a creature.
 * <p>
 * This can be used to clean up temporary visual state, predicted overlays,
 * or reset cached combat prediction data.
 */
public final class ClientCombatAnimationEndEvent extends Event {

    private final long attackerId;
    private final String animationName;

    public ClientCombatAnimationEndEvent(long attackerId, String animationName) {
        super(false);
        this.attackerId = attackerId;
        this.animationName = animationName;
    }

    public long getAttackerId() {
        return attackerId;
    }

    public String getAnimationName() {
        return animationName;
    }

    @Override
    public String toString() {
        return "ClientCombatAnimationEndEvent{" +
            "attackerId=" + attackerId +
            ", animationName='" + animationName + '\'' +
            '}';
    }
}
