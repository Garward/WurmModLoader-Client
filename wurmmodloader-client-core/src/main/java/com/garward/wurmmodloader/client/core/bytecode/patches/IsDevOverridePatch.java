package com.garward.wurmmodloader.client.core.bytecode.patches;

import com.garward.wurmmodloader.client.api.bytecode.BytecodePatch;
import javassist.CtClass;
import javassist.CtMethod;

import java.util.Collection;
import java.util.Collections;
import java.util.logging.Logger;

/**
 * Patches {@code SimpleServerConnectionClass.isDev()} to consult the modloader
 * for a forced-true override. When any mod has called
 * {@code ProxyClientHook.setDevOverride(true)}, vanilla code that gates on
 * dev status (quick keybind/mousebind dialogs, toggleKey, rebindPrimary,
 * updateWithKeybinds) behaves as if the player were dev — the canonical
 * unlock point for the Goldenflamer action mod.
 *
 * @since 0.3.0
 */
public class IsDevOverridePatch implements BytecodePatch {

    private static final Logger logger = Logger.getLogger(IsDevOverridePatch.class.getName());
    private static final String PROXY = "com.garward.wurmmodloader.client.modloader.ProxyClientHook";

    @Override
    public String getTargetClassName() {
        return "com.wurmonline.client.comm.SimpleServerConnectionClass";
    }

    @Override
    public void apply(CtClass ctClass) throws Exception {
        CtMethod m = ctClass.getDeclaredMethod("isDev");
        m.insertBefore("if (" + PROXY + ".isDevOverrideActive()) return true;");
        logger.info("[IsDevOverridePatch] Patched SimpleServerConnectionClass.isDev");
    }

    @Override
    public Collection<String> getConflictKeys() {
        return Collections.singleton("client.conn.isdev");
    }

    @Override
    public String getDescription() {
        return "Allow mods to force SimpleServerConnectionClass.isDev() to true";
    }
}
