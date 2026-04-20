package com.garward.wurmmodloader.client.core.bytecode.patches;

import com.garward.wurmmodloader.client.api.bytecode.BytecodePatch;
import javassist.CtClass;
import javassist.CtMethod;

import java.util.Collection;
import java.util.Collections;

/**
 * Patches CreatureCellRenderable to fire ClientNpcUpdateEvent on game tick.
 *
 * <p>This patch hooks into the gameTick method for NPCs/creatures and fires an event
 * for each tick. This enables mods to implement client-side interpolation and
 * dead-reckoning for creatures.
 *
 * <p>Accesses creature data through the {@code creature} field which is of type
 * {@code CreatureData}, providing ID and position information.
 *
 * @since 0.2.0
 */
public class ClientNpcGameTickPatch implements BytecodePatch {

    @Override
    public String getTargetClassName() {
        return "com.wurmonline.client.renderer.cell.CreatureCellRenderable";
    }

    @Override
    public void apply(CtClass ctClass) throws Exception {
        // Hook the gameTick() method
        CtMethod method = ctClass.getDeclaredMethod("gameTick");

        // ✅ CLEAN: Fire event at the start of game tick
        // Access creature data through the 'creature' field (type: CreatureData)
        // Use EventLogic constant instead of hardcoded calculation
        method.insertBefore(
            "{ " +
            "  long creatureId = this.creature.getId();" +
            "  float x = this.creature.getX();" +
            "  float y = this.creature.getY();" +
            "  float height = this.creature.getH();" +
            "  float deltaTime = com.garward.wurmmodloader.client.api.events.eventlogic.ClientTickEventLogic.STANDARD_TICK_DELTA;" +
            "  com.garward.wurmmodloader.client.modloader.ProxyClientHook.fireClientNpcUpdateEvent(creatureId, x, y, height, deltaTime);" +
            "}"
        );
    }

    @Override
    public int getPriority() {
        return 50;  // Mid priority
    }

    @Override
    public String getDescription() {
        return "NPC/creature game tick hook";
    }

    @Override
    public Collection<String> getConflictKeys() {
        return Collections.singleton("client.entity.npc");
    }
}
