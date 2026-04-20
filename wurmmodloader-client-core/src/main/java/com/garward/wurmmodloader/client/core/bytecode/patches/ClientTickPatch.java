package com.garward.wurmmodloader.client.core.bytecode.patches;

import com.garward.wurmmodloader.client.api.bytecode.BytecodePatch;
import javassist.CtClass;
import javassist.CtMethod;

import java.util.Collection;
import java.util.Collections;

/**
 * Patches the client's game loop to fire ClientTickEvent every frame.
 *
 * <p>This patch hooks into the main game loop (typically in LwjglClient)
 * and fires an event each frame with the delta time. This allows mods to
 * run code continuously during gameplay.
 *
 * <p><b>Note:</b> This event fires very frequently (60+ times per second).
 * Handlers should be lightweight to avoid performance issues.
 *
 * @since 0.1.0
 */
public class ClientTickPatch implements BytecodePatch {

    @Override
    public String getTargetClassName() {
        return "com.wurmonline.client.WurmClientBase";
    }

    @Override
    public void apply(CtClass ctClass) throws Exception {
        // Hook the tick() method which is called each game tick
        // Signature: public final boolean tick(boolean)
        CtMethod method = ctClass.getDeclaredMethod("tick", new CtClass[] {
            javassist.CtPrimitiveType.booleanType
        });

        // Add delta time tracking and fire event each tick
        // Default to 1/20th of a second (20 TPS based on TICKS_PER_SECOND constant)
        method.insertBefore(
            "{ " +
            "  float deltaTime = 1.0f / 20.0f;" +  // 20 ticks per second
            "  com.garward.wurmmodloader.client.modloader.ProxyClientHook.fireClientTickEvent(deltaTime);" +
            "}"
        );

        // Note: We don't add System.out.println here because tick() is called very frequently
        // and would spam the console. The ClientHook logging will show the first tick only.
    }

    @Override
    public int getPriority() {
        return 500;  // Medium priority
    }

    @Override
    public String getDescription() {
        return "Client tick/frame hook";
    }

    @Override
    public Collection<String> getConflictKeys() {
        return Collections.singleton("client.lifecycle.tick");
    }
}
