package com.garward.wurmmodloader.client.core.bytecode.patches;

import com.garward.wurmmodloader.client.api.bytecode.BytecodePatch;
import javassist.CtClass;
import javassist.CtMethod;

import java.util.Collection;
import java.util.Collections;

/**
 * Patches PlayerObj to fire AuthoritativePlayerPositionEvent when server position is applied.
 *
 * <p>This patch hooks into setNewPlayerPosition and fires an event when the client
 * receives an authoritative position update from the server. This is critical for
 * client-side prediction reconciliation.
 *
 * @since 0.2.0
 */
public class ClientAuthoritativePlayerPositionPatch implements BytecodePatch {

    @Override
    public String getTargetClassName() {
        return "com.wurmonline.client.game.PlayerObj";
    }

    @Override
    public void apply(CtClass ctClass) throws Exception {
        // Hook the setNewPlayerPosition(float, float, float) method
        CtMethod method = ctClass.getDeclaredMethod("setNewPlayerPosition",
            new CtClass[] {
                CtClass.floatType,
                CtClass.floatType,
                CtClass.floatType
            });

        // Fire event when authoritative position is set
        // $1 = x, $2 = y, $3 = height
        method.insertBefore(
            "{ " +
            "  long sequence = System.currentTimeMillis();" +
            "  com.garward.wurmmodloader.client.modloader.ProxyClientHook.fireAuthoritativePlayerPositionEvent($1, $2, $3, sequence);" +
            "}"
        );
    }

    @Override
    public int getPriority() {
        return 60;  // Mid-high priority
    }

    @Override
    public String getDescription() {
        return "Authoritative player position hook";
    }

    @Override
    public Collection<String> getConflictKeys() {
        return Collections.singleton("client.network.position");
    }
}
