package com.garward.wurmmodloader.client.core.bytecode.patches;

import com.garward.wurmmodloader.client.api.bytecode.BytecodePatch;
import javassist.CtClass;
import javassist.CtMethod;

import java.util.Collection;
import java.util.Collections;
import java.util.logging.Logger;

/**
 * Logs every sound the engine plays. Diagnostic-only — used to identify
 * which sample is the source of unexplained audio (e.g. a looping ambient
 * that bypasses category mutes).
 *
 * <p>Patches three entry points on {@code SoundEngine}:
 * <ul>
 *   <li>{@code play(String, SoundSource, float, float, float, boolean, boolean)}
 *   <li>{@code play(ResourceUrl, SoundSource, float, float, float, boolean, boolean)}
 *   <li>{@code playMusic(String, SoundSource, float, float, float)}
 * </ul>
 */
public class SoundPlayLoggingPatch implements BytecodePatch {

    private static final Logger logger = Logger.getLogger(SoundPlayLoggingPatch.class.getName());

    @Override
    public String getTargetClassName() {
        return "com.wurmonline.client.sound.SoundEngine";
    }

    @Override
    public void apply(CtClass ctClass) throws Exception {
        for (CtMethod m : ctClass.getDeclaredMethods()) {
            String n = m.getName();
            if (!"play".equals(n) && !"playMusic".equals(n)) continue;
            String paramSig = m.getSignature();
            // First parameter is either String or ResourceUrl
            String firstParam;
            if (paramSig.startsWith("(Ljava/lang/String;")) {
                firstParam = "$1";
            } else if (paramSig.startsWith("(Lcom/wurmonline/client/resources/ResourceUrl;")) {
                firstParam = "String.valueOf($1)";
            } else {
                continue;
            }
            m.insertBefore(
                "java.util.logging.Logger.getLogger(\"SoundPlayLog\").info(" +
                "\"[SoundPlay] \" + \"" + n + "\" + \" looping=\" + " +
                (n.equals("play") ? "$7" : "false") +
                " + \" name=\" + " + firstParam + ");"
            );
            logger.info("[SoundPlayLoggingPatch] Patched " + n + paramSig);
        }
    }

    @Override
    public Collection<String> getConflictKeys() {
        return Collections.singleton("client.sound.soundengine.playlogging");
    }

    @Override
    public String getDescription() {
        return "Log every sound played by SoundEngine (diagnostic)";
    }
}
