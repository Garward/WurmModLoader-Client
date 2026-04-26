package com.garward.wurmmodloader.client.core.bytecode.patches;

import com.garward.wurmmodloader.client.api.bytecode.BytecodePatch;
import javassist.CtClass;
import javassist.CtMethod;

import java.util.Collection;
import java.util.Collections;
import java.util.logging.Logger;

/**
 * Logs every {@code .ogg} resource the client fails to resolve, so we can
 * identify packs that are missing samples referenced by templates,
 * creatures, or spells.
 *
 * <p>Patches {@code Resources.getResource(String)} with an {@code insertAfter}
 * guard that fires only when the lookup returned {@code null} and the
 * resource name still ends in {@code .ogg} (the recursive extension-stripped
 * retry inside the same method passes a stripped name and is ignored).
 *
 * @since 0.4.0
 */
public class SoundResourceLoggingPatch implements BytecodePatch {

    private static final Logger logger = Logger.getLogger(SoundResourceLoggingPatch.class.getName());
    private static final String FALLBACK = "com.garward.wurmmodloader.client.core.sound.SoundFallback";

    @Override
    public String getTargetClassName() {
        return "com.wurmonline.client.resources.Resources";
    }

    @Override
    public void apply(CtClass ctClass) throws Exception {
        CtMethod m = ctClass.getDeclaredMethod("getResource",
                new CtClass[] { ctClass.getClassPool().get("java.lang.String") });
        m.insertAfter(
            "if ($_ == null && $1 != null && $1.endsWith(\".ogg\")) {" +
            "  " + FALLBACK + ".noteMissingResource($1);" +
            "}"
        );
        logger.info("[SoundResourceLoggingPatch] Patched Resources.getResource(String)");
    }

    @Override
    public Collection<String> getConflictKeys() {
        return Collections.singleton("client.resources.getresource.soundlogging");
    }

    @Override
    public String getDescription() {
        return "Log unresolved .ogg resource names so missing audio can be diagnosed";
    }
}
