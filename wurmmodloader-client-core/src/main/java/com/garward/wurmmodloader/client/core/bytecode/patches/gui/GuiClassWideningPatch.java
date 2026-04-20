package com.garward.wurmmodloader.client.core.bytecode.patches.gui;

import com.garward.wurmmodloader.client.api.bytecode.BytecodePatch;

import javassist.CtClass;
import javassist.CtConstructor;
import javassist.CtField;
import javassist.CtMethod;
import javassist.Modifier;

import java.util.Collection;
import java.util.Collections;

/**
 * Data-driven access-widening patch: promotes every package-private member on
 * a target WU GUI class — class modifier, constructors, methods, fields — to
 * {@code public}. Private and protected members are left alone.
 *
 * <p>WU's {@code com.wurmonline.client.renderer.gui} package declares almost
 * every widget and most of its members package-private, which locks mods into
 * squatting in Wurm's package or doing full reflection. Widening the
 * package-private members turns the whole toolkit into a usable public GUI
 * API without disturbing members the original author deliberately hid.
 *
 * <p>One instance per target class. Register multiple in {@code CorePatches}
 * to widen the whole toolkit.
 */
public class GuiClassWideningPatch implements BytecodePatch {

    private static final int VISIBILITY_MASK =
            Modifier.PUBLIC | Modifier.PRIVATE | Modifier.PROTECTED;

    private final String targetClassName;

    public GuiClassWideningPatch(String targetClassName) {
        this.targetClassName = targetClassName;
    }

    @Override
    public String getTargetClassName() {
        return targetClassName;
    }

    @Override
    public void apply(CtClass ctClass) throws Exception {
        widenIfPackagePrivate(ctClass);

        for (CtConstructor ctor : ctClass.getDeclaredConstructors()) {
            int mods = ctor.getModifiers();
            if (isPackagePrivate(mods)) {
                ctor.setModifiers(Modifier.setPublic(mods));
            }
        }

        for (CtMethod method : ctClass.getDeclaredMethods()) {
            int mods = method.getModifiers();
            if (isPackagePrivate(mods)) {
                method.setModifiers(Modifier.setPublic(mods));
            }
        }

        for (CtField field : ctClass.getDeclaredFields()) {
            int mods = field.getModifiers();
            if (isPackagePrivate(mods)) {
                field.setModifiers(Modifier.setPublic(mods));
            }
        }
    }

    private static void widenIfPackagePrivate(CtClass ctClass) {
        int mods = ctClass.getModifiers();
        if (isPackagePrivate(mods)) {
            ctClass.setModifiers(Modifier.setPublic(mods));
        }
    }

    private static boolean isPackagePrivate(int modifiers) {
        return (modifiers & VISIBILITY_MASK) == 0;
    }

    @Override
    public int getPriority() {
        return 200;
    }

    @Override
    public String getDescription() {
        return "Widen " + targetClassName + " package-private members to public";
    }

    @Override
    public Collection<String> getConflictKeys() {
        return Collections.singleton("client.gui.widen." + targetClassName);
    }

    /**
     * JAR entry path (e.g. {@code com/wurmonline/client/renderer/gui/WButton.class})
     * for the standalone patcher to match against.
     */
    public String getJarEntryName() {
        return targetClassName.replace('.', '/') + ".class";
    }
}
