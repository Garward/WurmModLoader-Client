package com.garward.wurmmodloader.client.core.bytecode.patches;

import com.garward.wurmmodloader.client.api.bytecode.BytecodePatch;
import javassist.CtClass;
import javassist.CtMethod;

import java.util.Collection;
import java.util.Collections;
import java.util.logging.Logger;

/**
 * Patches {@code ServerConnectionListenerClass.setStamina(float, float)} to
 * fire {@code ClientStaminaChangedEvent} on every CMD_STAMINA packet.
 *
 * <p>{@code setStamina} signature is {@code (float stamina, float damage)}.
 * We capture {@code $1} (stamina) and ignore {@code $2}.
 *
 * @since 0.4.0
 */
public class ServerConnectionStaminaPatch implements BytecodePatch {

    private static final Logger logger = Logger.getLogger(ServerConnectionStaminaPatch.class.getName());
    private static final String PROXY = "com.garward.wurmmodloader.client.modloader.ProxyClientHook";

    @Override
    public String getTargetClassName() {
        return "com.wurmonline.client.comm.ServerConnectionListenerClass";
    }

    @Override
    public void apply(CtClass ctClass) throws Exception {
        CtMethod m = ctClass.getDeclaredMethod("setStamina");
        m.insertAfter(
            "{ try { " + PROXY + ".fireClientStaminaChangedEvent($1); } " +
            "catch (Throwable t) { t.printStackTrace(); } }"
        );
        logger.info("[ServerConnectionStaminaPatch] Patched ServerConnectionListenerClass.setStamina");
    }

    @Override
    public Collection<String> getConflictKeys() {
        return Collections.singleton("client.stamina.changed");
    }

    @Override
    public String getDescription() {
        return "Fire ClientStaminaChangedEvent from ServerConnectionListenerClass.setStamina";
    }
}
