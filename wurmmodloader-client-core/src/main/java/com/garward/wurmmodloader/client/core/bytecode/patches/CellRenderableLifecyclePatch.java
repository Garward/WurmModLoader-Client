package com.garward.wurmmodloader.client.core.bytecode.patches;

import com.garward.wurmmodloader.client.api.bytecode.BytecodePatch;
import javassist.CtClass;
import javassist.CtConstructor;
import javassist.CtMethod;
import javassist.NotFoundException;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.logging.Logger;

/**
 * Fires {@code CellRenderableInitEvent} / {@code CellRenderableRemovedEvent}
 * so mods can track creature/player/ground-item lifecycle without reflective
 * map scans.
 *
 * <p>Three sites are patched, one patch per target class. The registry keys
 * each class, so this patch returns whichever class it's currently applying.
 * {@link #apply(CtClass)} dispatches per class.
 *
 * <ul>
 *   <li>{@code MobileModelRenderable.initialize()} — covers creatures + players</li>
 *   <li>{@code GroundItemCellRenderable} constructor — ground items have no
 *       {@code initialize} override</li>
 *   <li>{@code CellRenderable.removed(boolean)} — parent, catches all subclasses
 *       via {@code super.removed()}</li>
 * </ul>
 *
 * @since 0.3.0
 */
public class CellRenderableLifecyclePatch implements BytecodePatch {

    private static final Logger logger = Logger.getLogger(CellRenderableLifecyclePatch.class.getName());
    private static final String PROXY = "com.garward.wurmmodloader.client.modloader.ProxyClientHook";

    private final String target;

    public CellRenderableLifecyclePatch(String target) {
        this.target = target;
    }

    @Override
    public String getTargetClassName() {
        return target;
    }

    @Override
    public void apply(CtClass ctClass) throws Exception {
        switch (target) {
            case "com.wurmonline.client.renderer.cell.MobileModelRenderable":
                patchInitialize(ctClass);
                break;
            case "com.wurmonline.client.renderer.cell.GroundItemCellRenderable":
                patchGroundItemCtor(ctClass);
                break;
            case "com.wurmonline.client.renderer.cell.CellRenderable":
                patchRemoved(ctClass);
                break;
            default:
                throw new IllegalStateException("Unknown target: " + target);
        }
    }

    private void patchInitialize(CtClass cc) throws NotFoundException, javassist.CannotCompileException {
        CtMethod m = cc.getDeclaredMethod("initialize");
        m.insertAfter(
            "{ try { " + PROXY + ".fireCellRenderableInitEvent($0); } " +
            "catch (Throwable t) { t.printStackTrace(); } }"
        );
        logger.info("[CellRenderableLifecyclePatch] Patched MobileModelRenderable.initialize");
    }

    private void patchGroundItemCtor(CtClass cc) throws javassist.CannotCompileException {
        for (CtConstructor ctor : cc.getDeclaredConstructors()) {
            ctor.insertAfter(
                "{ try { " + PROXY + ".fireCellRenderableInitEvent($0); } " +
                "catch (Throwable t) { t.printStackTrace(); } }"
            );
        }
        logger.info("[CellRenderableLifecyclePatch] Patched GroundItemCellRenderable ctor(s)");
    }

    private void patchRemoved(CtClass cc) throws NotFoundException, javassist.CannotCompileException {
        CtMethod m = cc.getDeclaredMethod("removed");
        m.insertBefore(
            "{ try { " + PROXY + ".fireCellRenderableRemovedEvent($0, $1); } " +
            "catch (Throwable t) { t.printStackTrace(); } }"
        );
        logger.info("[CellRenderableLifecyclePatch] Patched CellRenderable.removed");
    }

    @Override
    public Collection<String> getConflictKeys() {
        return Collections.singleton("client.cellrenderable.lifecycle." + target);
    }

    @Override
    public String getDescription() {
        return "CellRenderable lifecycle events @ " + target;
    }
}
