package com.garward.wurmmodloader.client.core.bytecode.patches;

import com.garward.wurmmodloader.client.api.bytecode.BytecodePatch;
import javassist.CtClass;
import javassist.CtMethod;

import java.util.Collection;
import java.util.Collections;
import java.util.logging.Logger;

/**
 * Patches {@code WorldRender.renderPickedItem(Queue)} to fire a post-render
 * event — the visible pass where overlays should emit primitives so they
 * actually appear on-screen (as opposed to {@code PickRenderer.execute},
 * which only populates the pick buffer).
 *
 * @since 0.3.0
 */
public class WorldRenderPatch implements BytecodePatch {

    private static final Logger logger = Logger.getLogger(WorldRenderPatch.class.getName());
    private static final String PROXY = "com.garward.wurmmodloader.client.modloader.ProxyClientHook";

    @Override
    public String getTargetClassName() {
        return "com.wurmonline.client.renderer.WorldRender";
    }

    @Override
    public void apply(CtClass ctClass) throws Exception {
        CtMethod method = ctClass.getDeclaredMethod("renderPickedItem");
        method.insertAfter(
            "{ try { " + PROXY + ".fireWorldRenderPostEvent($1, $0, $0.pickRenderer); } " +
            "catch (Throwable t) { t.printStackTrace(); } }"
        );
        logger.info("[WorldRenderPatch] Patched WorldRender.renderPickedItem");
    }

    @Override
    public Collection<String> getConflictKeys() {
        return Collections.singleton("client.render.world.picked");
    }

    @Override
    public String getDescription() {
        return "Fire WorldRenderPostEvent after WorldRender.renderPickedItem";
    }
}
