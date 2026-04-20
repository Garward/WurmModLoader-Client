package com.garward.wurmmodloader.client.core.bytecode.patches.gui;

import com.garward.wurmmodloader.client.api.bytecode.BytecodePatch;

import javassist.CtClass;
import javassist.CtConstructor;
import javassist.CtField;
import javassist.Modifier;
import javassist.NotFoundException;

import java.util.Collection;
import java.util.Collections;
import java.util.logging.Logger;

/**
 * Widens package-private members on {@code FlexComponent} to {@code public}.
 *
 * <p>Paired with {@link WurmComponentAccessPatch}. Split into two patches so
 * each one targets a single class and can be baked into client.jar by the
 * standalone patcher without a classpool cross-reference.
 */
public class FlexComponentAccessPatch implements BytecodePatch {

    private static final Logger logger = Logger.getLogger(FlexComponentAccessPatch.class.getName());

    @Override
    public String getTargetClassName() {
        return "com.wurmonline.client.renderer.gui.FlexComponent";
    }

    @Override
    public void apply(CtClass ctClass) throws Exception {
        WurmComponentAccessPatch.widenField(ctClass, "sizeFlags");
        widenStaticField(ctClass, "FIXED_WIDTH");
        widenStaticField(ctClass, "FIXED_HEIGHT");

        // Widen every constructor so mods can subclass from their own package.
        for (CtConstructor ctor : ctClass.getDeclaredConstructors()) {
            ctor.setModifiers(Modifier.setPublic(ctor.getModifiers()));
        }

        // Widen the class itself so mods can extend / reference it directly.
        ctClass.setModifiers(Modifier.setPublic(ctClass.getModifiers()));
    }

    private static void widenStaticField(CtClass klass, String name) {
        try {
            CtField f = klass.getDeclaredField(name);
            f.setModifiers(Modifier.setPublic(f.getModifiers()));
        } catch (NotFoundException e) {
            logger.warning("FlexComponentAccessPatch: field " + klass.getName() + "." + name + " not found");
        }
    }

    @Override
    public int getPriority() {
        return 200;
    }

    @Override
    public String getDescription() {
        return "Widen FlexComponent package-private members to public";
    }

    @Override
    public Collection<String> getConflictKeys() {
        return Collections.singleton("client.gui.flexcomponent.access");
    }
}
