package com.garward.wurmmodloader.client.core.bytecode.patches;

import com.garward.wurmmodloader.client.api.bytecode.BytecodePatch;
import javassist.CtClass;
import javassist.CtMethod;

import java.util.Collection;
import java.util.Collections;
import java.util.logging.Logger;

/**
 * Patches
 * {@code com.wurmonline.client.renderer.cell.CreatureCellRenderable.getHoverDescription(PickData)}
 * to fire {@link com.garward.wurmmodloader.client.api.events.gui.CreatureHoverDescriptionEvent}
 * after vanilla appends its own lines. Subscribers append additional text via
 * {@code event.getPickData().addText(...)}.
 *
 * <p>The {@code creature} field on {@code CreatureCellRenderable} is
 * {@code private final}, so the patch pre-extracts the model name (which the
 * upstream tooltips mod uses to derive coat / sex / variant labels) from
 * inside the class and passes it through the event.
 *
 * @since 0.4.1
 */
public class CreatureHoverDescriptionPatch implements BytecodePatch {

    private static final Logger logger = Logger.getLogger(CreatureHoverDescriptionPatch.class.getName());
    private static final String PROXY = "com.garward.wurmmodloader.client.modloader.ProxyClientHook";

    @Override
    public String getTargetClassName() {
        return "com.wurmonline.client.renderer.cell.CreatureCellRenderable";
    }

    @Override
    public void apply(CtClass ctClass) throws Exception {
        CtMethod method = ctClass.getDeclaredMethod(
            "getHoverDescription",
            new CtClass[] {
                ctClass.getClassPool().get("com.wurmonline.client.renderer.PickData")
            }
        );
        method.insertAfter(
            "{ try {" +
            "  " + PROXY + ".fireCreatureHoverDescriptionEvent(" +
            "      $0, $1, $0.creature.getModelName().toString());" +
            "} catch (Throwable __t) { __t.printStackTrace(); } }"
        );
        logger.info("[CreatureHoverDescriptionPatch] Patched CreatureCellRenderable.getHoverDescription");
    }

    @Override
    public int getPriority() {
        return 50;
    }

    @Override
    public String getDescription() {
        return "Fire CreatureHoverDescriptionEvent at end of CreatureCellRenderable.getHoverDescription";
    }

    @Override
    public Collection<String> getConflictKeys() {
        return Collections.singleton("client.creature.hoverdescription");
    }
}
