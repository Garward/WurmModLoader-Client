package com.garward.wurmmodloader.client.core.bytecode.patches.gui;

import com.garward.wurmmodloader.client.api.bytecode.BytecodePatch;

import javassist.CtClass;
import javassist.CtField;
import javassist.CtMethod;
import javassist.Modifier;
import javassist.NotFoundException;

import java.util.Collection;
import java.util.Collections;
import java.util.logging.Logger;

/**
 * Widens package-private members on {@code WurmComponent} to {@code public}
 * so mods can extend HUD components from their own packages.
 *
 * <p>Vanilla {@code WurmComponent} declares {@code x}, {@code y},
 * {@code leftPressed}, {@code rightPressed}, {@code leftReleased},
 * {@code rightReleased}, {@code mouseDragged}, and {@code mouseWheeled} with
 * no visibility modifier (package-private). That locks every subclass into
 * {@code com.wurmonline.client.renderer.gui} and forces mod authors to either
 * squat in Wurm's package or reflect their way in. This patch flips those
 * members to public so mods stay in their own packages.
 *
 * <p>{@link FlexComponentAccessPatch} does the same for {@code FlexComponent};
 * they're split so each patch touches exactly one class, which lets the
 * standalone JAR patcher (see {@code ClientPatcher.patchClientJar}) bake them
 * into client.jar on disk — the patched JAR is what mods compile against, so
 * {@code javac} sees the widened members as public at compile time.
 */
public class WurmComponentAccessPatch implements BytecodePatch {

    private static final Logger logger = Logger.getLogger(WurmComponentAccessPatch.class.getName());

    @Override
    public String getTargetClassName() {
        return "com.wurmonline.client.renderer.gui.WurmComponent";
    }

    @Override
    public void apply(CtClass ctClass) throws Exception {
        widenField(ctClass, "x");
        widenField(ctClass, "y");
        widenField(ctClass, "hud");

        widenMethod(ctClass, "leftPressed",   CtClass.intType, CtClass.intType, CtClass.intType);
        widenMethod(ctClass, "rightPressed",  CtClass.intType, CtClass.intType, CtClass.intType);
        widenMethod(ctClass, "leftReleased",  CtClass.intType, CtClass.intType);
        widenMethod(ctClass, "rightReleased", CtClass.intType, CtClass.intType);
        widenMethod(ctClass, "mouseDragged",  CtClass.intType, CtClass.intType);
        widenMethod(ctClass, "mouseWheeled",  CtClass.intType, CtClass.intType, CtClass.intType);
    }

    static void widenField(CtClass klass, String name) {
        try {
            CtField f = klass.getDeclaredField(name);
            f.setModifiers(Modifier.setPublic(f.getModifiers()));
        } catch (NotFoundException e) {
            logger.warning("WurmComponentAccessPatch: field " + klass.getName() + "." + name + " not found");
        }
    }

    static void widenMethod(CtClass klass, String name, CtClass... params) {
        try {
            CtMethod m = klass.getDeclaredMethod(name, params);
            m.setModifiers(Modifier.setPublic(m.getModifiers()));
        } catch (NotFoundException e) {
            logger.warning("WurmComponentAccessPatch: method " + klass.getName() + "." + name + " not found");
        }
    }

    @Override
    public int getPriority() {
        return 200; // Run before WurmComponentInputPatch.
    }

    @Override
    public String getDescription() {
        return "Widen WurmComponent package-private members to public";
    }

    @Override
    public Collection<String> getConflictKeys() {
        return Collections.singleton("client.gui.wurmcomponent.access");
    }
}
