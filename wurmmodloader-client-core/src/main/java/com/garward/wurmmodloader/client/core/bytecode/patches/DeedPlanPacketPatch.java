package com.garward.wurmmodloader.client.core.bytecode.patches;

import com.garward.wurmmodloader.client.api.bytecode.BytecodePatch;
import javassist.CtClass;
import javassist.CtMethod;

import java.util.Collection;
import java.util.Collections;
import java.util.logging.Logger;

/**
 * Patches {@code SimpleServerConnectionClass.reallyHandleCmdShowDeedPlan(ByteBuffer)}
 * to fire a cancellable {@code DeedPlanPacketEvent}. Mods that compute deed
 * bounds for rendering (e.g. ESP deed-size overlay) can read the payload
 * without re-intercepting the network pipeline.
 *
 * @since 0.3.0
 */
public class DeedPlanPacketPatch implements BytecodePatch {

    private static final Logger logger = Logger.getLogger(DeedPlanPacketPatch.class.getName());
    private static final String PROXY = "com.garward.wurmmodloader.client.modloader.ProxyClientHook";

    @Override
    public String getTargetClassName() {
        return "com.wurmonline.client.comm.SimpleServerConnectionClass";
    }

    @Override
    public void apply(CtClass ctClass) throws Exception {
        CtMethod m = ctClass.getDeclaredMethod("reallyHandleCmdShowDeedPlan");
        m.insertBefore(
            "{ try { " +
            "if (" + PROXY + ".fireDeedPlanPacketEventCancelled($1)) return; " +
            "} catch (Throwable t) { t.printStackTrace(); } }"
        );
        logger.info("[DeedPlanPacketPatch] Patched SimpleServerConnectionClass.reallyHandleCmdShowDeedPlan");
    }

    @Override
    public Collection<String> getConflictKeys() {
        return Collections.singleton("client.net.deedplan");
    }

    @Override
    public String getDescription() {
        return "Fire cancellable DeedPlanPacketEvent from reallyHandleCmdShowDeedPlan";
    }
}
