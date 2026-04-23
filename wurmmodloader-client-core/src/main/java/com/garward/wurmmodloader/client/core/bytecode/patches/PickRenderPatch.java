package com.garward.wurmmodloader.client.core.bytecode.patches;

import com.garward.wurmmodloader.client.api.bytecode.BytecodePatch;
import javassist.CtClass;
import javassist.CtMethod;

import java.util.Collection;
import java.util.Collections;
import java.util.logging.Logger;

/**
 * Patches {@code PickRenderer.execute(Object)} to fire pre/post render events.
 *
 * <p>Pick render is the engine's per-frame pass that queues cell, terrain, cave,
 * and sky pick geometry into the active {@code Queue}. Overlay mods (ESP,
 * waypoint markers, highlight rings, etc.) want to contribute extra primitives
 * to that same queue without fighting each other — exposing a framework-level
 * pre/post seam is the arbitration point.
 *
 * <h2>Event flow</h2>
 * <pre>
 * PickRenderer.execute(Object arg)
 *   ↓ insertBefore  →  ProxyClientHook.firePickRenderPreEvent(arg)
 *   ... engine queues native pick geometry ...
 *   ↓ insertAfter   →  ProxyClientHook.firePickRenderPostEvent(arg)
 * </pre>
 *
 * @since 0.3.0
 */
public class PickRenderPatch implements BytecodePatch {

    private static final Logger logger = Logger.getLogger(PickRenderPatch.class.getName());

    private static final String PROXY = "com.garward.wurmmodloader.client.modloader.ProxyClientHook";

    @Override
    public String getTargetClassName() {
        return "com.wurmonline.client.renderer.PickRenderer";
    }

    @Override
    public void apply(CtClass ctClass) throws Exception {
        CtMethod execute = ctClass.getDeclaredMethod("execute");

        execute.insertBefore(
            "{ try { " + PROXY + ".firePickRenderPreEvent($1); } " +
            "catch (Throwable t) { t.printStackTrace(); } }"
        );

        execute.insertAfter(
            "{ try { " + PROXY + ".firePickRenderPostEvent($1); } " +
            "catch (Throwable t) { t.printStackTrace(); } }"
        );

        logger.info("[PickRenderPatch] Patched PickRenderer.execute with pre/post render hooks");
    }

    @Override
    public Collection<String> getConflictKeys() {
        return Collections.singleton("client.render.pick");
    }

    @Override
    public String getDescription() {
        return "Fire PickRenderPre/PostEvent around PickRenderer.execute";
    }
}
