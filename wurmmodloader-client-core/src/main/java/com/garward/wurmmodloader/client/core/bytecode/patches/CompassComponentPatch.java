package com.garward.wurmmodloader.client.core.bytecode.patches;

import com.garward.wurmmodloader.client.api.bytecode.BytecodePatch;
import javassist.CtClass;
import javassist.CtMethod;

import java.util.Collection;
import java.util.Collections;
import java.util.logging.Logger;

/**
 * Patches {@code CompassComponent.gameTick()} and
 * {@code CompassComponent.pick(PickData,int,int)} to fire
 * {@code CompassComponentTickEvent} / {@code CompassComponentPickEvent}.
 *
 * @since 0.3.0
 */
public class CompassComponentPatch implements BytecodePatch {

    private static final Logger logger = Logger.getLogger(CompassComponentPatch.class.getName());
    private static final String PROXY = "com.garward.wurmmodloader.client.modloader.ProxyClientHook";

    @Override
    public String getTargetClassName() {
        return "com.wurmonline.client.renderer.gui.CompassComponent";
    }

    @Override
    public void apply(CtClass ctClass) throws Exception {
        CtMethod tick = ctClass.getDeclaredMethod("gameTick");
        tick.insertAfter(
            "{ try { " + PROXY + ".fireCompassComponentTickEvent($0); } " +
            "catch (Throwable t) { t.printStackTrace(); } }"
        );

        CtMethod pick = ctClass.getDeclaredMethod("pick");
        pick.insertAfter(
            "{ try { " + PROXY + ".fireCompassComponentPickEvent($0, $1, $2, $3); } " +
            "catch (Throwable t) { t.printStackTrace(); } }"
        );

        logger.info("[CompassComponentPatch] Patched CompassComponent.gameTick + pick");
    }

    @Override
    public Collection<String> getConflictKeys() {
        return Collections.singleton("client.gui.compass");
    }

    @Override
    public String getDescription() {
        return "Fire CompassComponentTick/PickEvent after CompassComponent.gameTick/pick";
    }
}
