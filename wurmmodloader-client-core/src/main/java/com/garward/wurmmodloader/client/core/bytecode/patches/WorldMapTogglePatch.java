package com.garward.wurmmodloader.client.core.bytecode.patches;

import com.garward.wurmmodloader.client.api.bytecode.BytecodePatch;
import javassist.CtClass;
import javassist.CtMethod;

import java.util.Collection;
import java.util.Collections;

/**
 * Patches HeadsUpDisplay.toggleWorldMapVisible() to fire
 * WorldMapToggleRequestedEvent and allow mods to suppress the vanilla
 * "Freedom Isles" parchment window.
 *
 * <p>Target method signature (verified against decompiled
 * {@code HeadsUpDisplay.java} line 1549):
 * <pre>
 *   public boolean toggleWorldMapVisible()   // descriptor: ()Z
 * </pre>
 *
 * <p>The patch inserts at the start of the method a call to
 * {@link com.garward.wurmmodloader.client.modloader.ProxyClientHook#fireWorldMapToggleRequestedEvent(Object)}.
 * If any handler called {@code event.suppressVanilla()}, the static returns
 * {@code true} and we short-circuit with {@code return false;} — the vanilla
 * path (parchment open, server packet, quickbar button) is skipped entirely.
 *
 * @since 0.2.0
 */
public class WorldMapTogglePatch implements BytecodePatch {

    @Override
    public String getTargetClassName() {
        return "com.wurmonline.client.renderer.gui.HeadsUpDisplay";
    }

    @Override
    public void apply(CtClass ctClass) throws Exception {
        // toggleWorldMapVisible takes no args, returns boolean. Descriptor: ()Z
        CtMethod method = ctClass.getDeclaredMethod("toggleWorldMapVisible", new CtClass[0]);

        // Inject at method start. $0 is "this" (HeadsUpDisplay). If mods
        // suppress, return false (the boolean indicates "map is not visible"
        // which is a safe default — we don't want vanilla's side effects).
        method.insertBefore(
            "{ " +
            "  if (com.garward.wurmmodloader.client.modloader.ProxyClientHook" +
            "        .fireWorldMapToggleRequestedEvent($0)) {" +
            "    return false;" +
            "  }" +
            "}"
        );
    }

    @Override
    public int getPriority() {
        return 50;
    }

    @Override
    public String getDescription() {
        return "World map toggle hook (enables mod replacement of vanilla parchment)";
    }

    @Override
    public Collection<String> getConflictKeys() {
        return Collections.singleton("client.gui.worldmap.toggle");
    }
}
