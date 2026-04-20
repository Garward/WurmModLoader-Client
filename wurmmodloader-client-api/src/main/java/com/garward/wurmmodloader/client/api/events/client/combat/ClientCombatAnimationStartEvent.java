package com.garward.wurmmodloader.client.api.events.client.combat;

import com.garward.wurmmodloader.client.api.events.base.Event;

/**
 * Fired on the client whenever a combat animation begins for a creature.
 * <p>
 * Intended as a hook for predicted combat animations, UI feedback and
 * cosmetic effects. This event does not imply a hit was registered server-side.
 */
public final class ClientCombatAnimationStartEvent extends Event {

    private final long attackerId;
    private final long targetId;
    private final String animationName;
    private final boolean localPlayerInvolved;

    public ClientCombatAnimationStartEvent(long attackerId,
                                           long targetId,
                                           String animationName,
                                           boolean localPlayerInvolved) {
        super(false);
        this.attackerId = attackerId;
        this.targetId = targetId;
        this.animationName = animationName;
        this.localPlayerInvolved = localPlayerInvolved;
    }

    public long getAttackerId() {
        return attackerId;
    }

    public long getTargetId() {
        return targetId;
    }

    public String getAnimationName() {
        return animationName;
    }

    public boolean isLocalPlayerInvolved() {
        return localPlayerInvolved;
    }

    @Override
    public String toString() {
        return "ClientCombatAnimationStartEvent{" +
            "attackerId=" + attackerId +
            ", targetId=" + targetId +
            ", animationName='" + animationName + '\'' +
            ", localPlayerInvolved=" + localPlayerInvolved +
            '}';
    }
}
