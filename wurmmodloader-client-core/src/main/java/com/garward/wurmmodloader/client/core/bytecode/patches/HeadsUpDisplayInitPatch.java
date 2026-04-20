package com.garward.wurmmodloader.client.core.bytecode.patches;

import com.garward.wurmmodloader.client.api.bytecode.BytecodePatch;
import javassist.CtClass;
import javassist.CtMethod;

import java.util.Collection;
import java.util.Collections;

/**
 * Patches HeadsUpDisplay.init() to fire ClientHUDInitializedEvent.
 *
 * <p>This patch hooks into the HUD initialization and fires an event when the HUD
 * is fully set up. This allows mods to add custom windows, buttons, and overlays
 * to the HUD without reflection-based hooks.
 *
 * @since 0.2.0
 */
public class HeadsUpDisplayInitPatch implements BytecodePatch {

    @Override
    public String getTargetClassName() {
        return "com.wurmonline.client.renderer.gui.HeadsUpDisplay";
    }

    @Override
    public void apply(CtClass ctClass) throws Exception {
        // Hook the init(int, int) method
        CtMethod method = ctClass.getDeclaredMethod("init",
            new CtClass[] {
                CtClass.intType,
                CtClass.intType
            });

        // Fire event after HUD is fully initialized
        // Pass: this (HUD), world, mainMenu, width ($1), height ($2)
        method.insertAfter(
            "{ " +
            "  com.garward.wurmmodloader.client.modloader.ProxyClientHook.fireClientHUDInitializedEvent(" +
            "    this, this.world, this.mainMenu, $1, $2" +
            "  );" +
            "}"
        );
    }

    @Override
    public int getPriority() {
        return 50;  // Mid priority
    }

    @Override
    public String getDescription() {
        return "HUD initialization hook";
    }

    @Override
    public Collection<String> getConflictKeys() {
        return Collections.singleton("client.gui.hud.init");
    }
}
