package com.garward.wurmmodloader.client.core.bytecode.patches;

import com.garward.wurmmodloader.client.api.bytecode.BytecodePatch;
import javassist.CtClass;
import javassist.CtMethod;

import java.util.Collection;
import java.util.Collections;
import java.util.logging.Logger;

/**
 * Patches {@code WurmConsole.handleDevInput(String, String[])} to fire a
 * cancellable {@code ClientConsoleInputEvent} before the vanilla dev-command
 * dispatcher runs. If a handler cancels, the patch returns {@code true}
 * (matching the method's "handled" contract), skipping the vanilla lookup.
 *
 * @since 0.3.0
 */
public class ConsoleInputPatch implements BytecodePatch {

    private static final Logger logger = Logger.getLogger(ConsoleInputPatch.class.getName());
    private static final String PROXY = "com.garward.wurmmodloader.client.modloader.ProxyClientHook";

    @Override
    public String getTargetClassName() {
        return "com.wurmonline.client.console.WurmConsole";
    }

    @Override
    public void apply(CtClass ctClass) throws Exception {
        CtMethod m = ctClass.getDeclaredMethod("handleDevInput");
        m.insertBefore(
            "{ try { " +
            "if (" + PROXY + ".fireClientConsoleInputEventCancelled($1, $2)) return true; " +
            "} catch (Throwable t) { t.printStackTrace(); } }"
        );
        logger.info("[ConsoleInputPatch] Patched WurmConsole.handleDevInput");
    }

    @Override
    public Collection<String> getConflictKeys() {
        return Collections.singleton("client.console.devinput");
    }

    @Override
    public String getDescription() {
        return "Fire cancellable ClientConsoleInputEvent from WurmConsole.handleDevInput";
    }
}
