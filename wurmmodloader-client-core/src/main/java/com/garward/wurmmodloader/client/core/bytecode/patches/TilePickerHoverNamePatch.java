package com.garward.wurmmodloader.client.core.bytecode.patches;

import com.garward.wurmmodloader.client.api.bytecode.BytecodePatch;
import javassist.CtClass;
import javassist.CtMethod;

import java.util.Collection;
import java.util.Collections;
import java.util.logging.Logger;

/**
 * Patches {@code com.wurmonline.client.renderer.TilePicker.getHoverName()} to
 * fire {@link com.garward.wurmmodloader.client.api.events.gui.TilePickerHoverNameEvent}
 * before vanilla logic. If any subscriber sets an override name the method
 * returns it directly; otherwise the vanilla body runs unchanged.
 *
 * <p>The patch pre-computes {@code getSlopeSuffix(true)} from inside the class
 * so subscribers don't need to widen/reflect the private slope helper.
 *
 * @since 0.4.1
 */
public class TilePickerHoverNamePatch implements BytecodePatch {

    private static final Logger logger = Logger.getLogger(TilePickerHoverNamePatch.class.getName());
    private static final String PROXY = "com.garward.wurmmodloader.client.modloader.ProxyClientHook";

    @Override
    public String getTargetClassName() {
        return "com.wurmonline.client.renderer.TilePicker";
    }

    @Override
    public void apply(CtClass ctClass) throws Exception {
        CtMethod method = ctClass.getDeclaredMethod("getHoverName", new CtClass[0]);
        method.insertBefore(
            "{ try {" +
            "  String __ovr = " + PROXY + ".fireTilePickerHoverNameEvent(" +
            "      $0, $0.world, $0.x, $0.y, $0.section, $0.getSlopeSuffix(true));" +
            "  if (__ovr != null) return __ovr;" +
            "} catch (Throwable __t) { __t.printStackTrace(); } }"
        );
        logger.info("[TilePickerHoverNamePatch] Patched TilePicker.getHoverName");
    }

    @Override
    public int getPriority() {
        return 50;
    }

    @Override
    public String getDescription() {
        return "Fire TilePickerHoverNameEvent at start of TilePicker.getHoverName";
    }

    @Override
    public Collection<String> getConflictKeys() {
        return Collections.singleton("client.tilepicker.hovername");
    }
}
