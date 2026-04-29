package com.garward.wurmmodloader.client.core.bytecode.patches;

import com.garward.wurmmodloader.client.api.bytecode.BytecodePatch;
import javassist.CtClass;
import javassist.CtMethod;

import java.util.Collection;
import java.util.Collections;
import java.util.logging.Logger;

/**
 * Patches {@code com.wurmonline.client.renderer.cave.CaveWallPicker.getHoverName()}
 * to fire {@link com.garward.wurmmodloader.client.api.events.gui.CaveWallPickerHoverNameEvent}
 * before vanilla logic. Mirrors {@link TilePickerHoverNamePatch}.
 *
 * @since 0.4.1
 */
public class CaveWallPickerHoverNamePatch implements BytecodePatch {

    private static final Logger logger = Logger.getLogger(CaveWallPickerHoverNamePatch.class.getName());
    private static final String PROXY = "com.garward.wurmmodloader.client.modloader.ProxyClientHook";

    @Override
    public String getTargetClassName() {
        return "com.wurmonline.client.renderer.cave.CaveWallPicker";
    }

    @Override
    public void apply(CtClass ctClass) throws Exception {
        CtMethod method = ctClass.getDeclaredMethod("getHoverName", new CtClass[0]);
        method.insertBefore(
            "{ try {" +
            "  String __ovr = " + PROXY + ".fireCaveWallPickerHoverNameEvent(" +
            "      $0, $0.world, $0.x, $0.y, $0.wallSide, $0.name, $0.getSlopeSuffix(true));" +
            "  if (__ovr != null) return __ovr;" +
            "} catch (Throwable __t) { __t.printStackTrace(); } }"
        );
        logger.info("[CaveWallPickerHoverNamePatch] Patched CaveWallPicker.getHoverName");
    }

    @Override
    public int getPriority() {
        return 50;
    }

    @Override
    public String getDescription() {
        return "Fire CaveWallPickerHoverNameEvent at start of CaveWallPicker.getHoverName";
    }

    @Override
    public Collection<String> getConflictKeys() {
        return Collections.singleton("client.cavewallpicker.hovername");
    }
}
