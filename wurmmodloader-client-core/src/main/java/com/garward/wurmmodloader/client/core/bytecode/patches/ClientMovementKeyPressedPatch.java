package com.garward.wurmmodloader.client.core.bytecode.patches;

import com.garward.wurmmodloader.client.api.bytecode.BytecodePatch;
import javassist.CtClass;
import javassist.CtMethod;

import java.util.Collection;
import java.util.Collections;

/**
 * Patches WurmEventHandler to fire ClientMovementIntentEvent on key press.
 *
 * <p>This patch hooks into the keyPressed method and fires an event when
 * movement-related keys are pressed. This enables mods to implement client-side
 * prediction and input buffering.
 *
 * @since 0.2.0
 */
public class ClientMovementKeyPressedPatch implements BytecodePatch {

    @Override
    public String getTargetClassName() {
        return "com.wurmonline.client.WurmEventHandler";
    }

    @Override
    public void apply(CtClass ctClass) throws Exception {
        // Hook the keyPressed(int, char) method
        CtMethod method = ctClass.getDeclaredMethod("keyPressed",
            new CtClass[] {
                CtClass.intType,
                CtClass.charType
            });

        // Fire event at the start of keyPressed - key was pressed
        method.insertBefore(
            "{ " +
            "  com.garward.wurmmodloader.client.modloader.ProxyClientHook.fireClientMovementIntentEvent($1, $2, true);" +
            "}"
        );
    }

    @Override
    public int getPriority() {
        return 50;  // Mid priority
    }

    @Override
    public String getDescription() {
        return "Movement key press hook";
    }

    @Override
    public Collection<String> getConflictKeys() {
        return Collections.singleton("client.input.movement");
    }
}
