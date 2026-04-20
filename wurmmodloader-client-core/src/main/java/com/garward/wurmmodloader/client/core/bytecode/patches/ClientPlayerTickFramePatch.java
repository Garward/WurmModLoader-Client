package com.garward.wurmmodloader.client.core.bytecode.patches;

import com.garward.wurmmodloader.client.api.bytecode.BytecodePatch;
import javassist.CtClass;
import javassist.CtMethod;

import java.util.Collection;
import java.util.Collections;

/**
 * Patches WurmClientBase to fire pre/post player update events.
 *
 * <p>This patch hooks into the tickFrame method and fires events before and after
 * the player's transform is updated. This is the ideal location for client-side
 * prediction and input processing.
 *
 * @since 0.2.0
 */
public class ClientPlayerTickFramePatch implements BytecodePatch {

    @Override
    public String getTargetClassName() {
        return "com.wurmonline.client.WurmClientBase";
    }

    @Override
    public void apply(CtClass ctClass) throws Exception {
        // Hook the tickFrame() method
        CtMethod method = ctClass.getDeclaredMethod("tickFrame");

        // Fire pre-update event at the start
        method.insertBefore(
            "{ " +
            "  float deltaTime = 1.0f / 20.0f;" +  // 20 ticks per second
            "  com.garward.wurmmodloader.client.modloader.ProxyClientHook.fireClientPrePlayerUpdateEvent(deltaTime);" +
            "}"
        );

        // Fire post-update event at the end
        method.insertAfter(
            "{ " +
            "  float deltaTime = 1.0f / 20.0f;" +  // 20 ticks per second
            "  com.garward.wurmmodloader.client.modloader.ProxyClientHook.fireClientPostPlayerUpdateEvent(deltaTime);" +
            "}"
        );
    }

    @Override
    public int getPriority() {
        return 80;  // High priority - runs early in patch set
    }

    @Override
    public String getDescription() {
        return "Player update pre/post hook";
    }

    @Override
    public Collection<String> getConflictKeys() {
        return Collections.singleton("client.lifecycle.playerupdate");
    }
}
