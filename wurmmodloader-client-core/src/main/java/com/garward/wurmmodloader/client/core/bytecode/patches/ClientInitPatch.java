package com.garward.wurmmodloader.client.core.bytecode.patches;

import com.garward.wurmmodloader.client.api.bytecode.BytecodePatch;
import javassist.CtClass;
import javassist.CtMethod;

import java.util.Collection;
import java.util.Collections;

/**
 * Patches WurmClientBase to fire ClientInitEvent after initialization.
 *
 * <p>This patch hooks into the client's init() method and fires an event
 * when the client has finished initializing. This is the earliest point
 * where mods can safely interact with the client.
 *
 * @since 0.1.0
 */
public class ClientInitPatch implements BytecodePatch {

    @Override
    public String getTargetClassName() {
        return "com.wurmonline.client.WurmClientBase";
    }

    @Override
    public void apply(CtClass ctClass) throws Exception {
        // Hook the run() method which is called when the client starts
        CtMethod method = ctClass.getDeclaredMethod("run");

        // Fire event at the start of run() - client is starting
        method.insertBefore(
            "{ " +
            "  System.out.println(\"[WurmModLoader] ClientInitPatch: WurmClientBase.run() executing\");" +
            "  com.garward.wurmmodloader.client.modloader.ProxyClientHook.fireClientInitEvent();" +
            "}"
        );
    }

    @Override
    public int getPriority() {
        return 1000;  // High priority - init is critical
    }

    @Override
    public String getDescription() {
        return "Client initialization hook";
    }

    @Override
    public Collection<String> getConflictKeys() {
        return Collections.singleton("client.lifecycle.init");
    }
}
