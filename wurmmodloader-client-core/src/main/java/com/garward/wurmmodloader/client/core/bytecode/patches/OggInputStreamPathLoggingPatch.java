package com.garward.wurmmodloader.client.core.bytecode.patches;

import com.garward.wurmmodloader.client.api.bytecode.BytecodePatch;
import javassist.CtClass;
import javassist.CtMethod;
import javassist.expr.ExprEditor;
import javassist.expr.MethodCall;

import java.util.Collection;
import java.util.Collections;
import java.util.logging.Logger;

/**
 * Augments {@code OggInputStream}'s error logging so every SEVERE includes
 * {@code filePath}. Vanilla logs {@code "Failure reading in vorbis"} and
 * {@code "Failed to read Vorbis"} without identifying which sample failed,
 * which makes diagnosing corrupt sounds in packs essentially impossible.
 *
 * <p>Rewrites {@code logger.log(Level, String, Throwable)} and
 * {@code logger.log(Level, String)} calls inside the class to append
 * {@code " [filePath=" + this.filePath + "]"} to the message.
 *
 * @since 0.4.0
 */
public class OggInputStreamPathLoggingPatch implements BytecodePatch {

    private static final Logger logger = Logger.getLogger(OggInputStreamPathLoggingPatch.class.getName());

    @Override
    public String getTargetClassName() {
        return "com.wurmonline.client.sound.formats.OggInputStream";
    }

    @Override
    public void apply(CtClass ctClass) throws Exception {
        for (CtMethod m : ctClass.getDeclaredMethods()) {
            m.instrument(new ExprEditor() {
                @Override
                public void edit(MethodCall mc) throws javassist.CannotCompileException {
                    if (!"log".equals(mc.getMethodName())) return;
                    if (!"java.util.logging.Logger".equals(mc.getClassName())) return;
                    // Both 2-arg log(Level,String) and 3-arg log(Level,String,Throwable)
                    // have $2 = message string. Append filePath via the surrounding
                    // OggInputStream's private field — javassist compiles the
                    // replacement in the context of the enclosing method so
                    // this.filePath is accessible.
                    mc.replace(
                        "{ $2 = $2 + \" [filePath=\" + this.filePath + \"]\"; $_ = $proceed($$); }"
                    );
                }
            });
        }
        logger.info("[OggInputStreamPathLoggingPatch] Patched OggInputStream error logs to include filePath");
    }

    @Override
    public Collection<String> getConflictKeys() {
        return Collections.singleton("client.sound.ogginputstream.pathlogging");
    }

    @Override
    public String getDescription() {
        return "Append filePath to OggInputStream SEVERE log messages so corrupt samples are identifiable";
    }
}
