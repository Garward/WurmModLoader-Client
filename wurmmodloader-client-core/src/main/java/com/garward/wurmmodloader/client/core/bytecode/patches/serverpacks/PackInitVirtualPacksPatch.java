package com.garward.wurmmodloader.client.core.bytecode.patches.serverpacks;

import com.garward.wurmmodloader.client.api.bytecode.BytecodePatch;
import javassist.CannotCompileException;
import javassist.CtClass;
import javassist.CtMethod;
import javassist.expr.ExprEditor;
import javassist.expr.MethodCall;

import java.util.Collection;
import java.util.Collections;

/**
 * Patches Pack.init() to allow virtual packs starting with "~".
 *
 * <p>This patch allows server packs to use virtual paths (starting with "~") that
 * are resolved at runtime, avoiding file existence checks during initialization.
 *
 * @since 0.2.0
 */
public class PackInitVirtualPacksPatch implements BytecodePatch {

    @Override
    public String getTargetClassName() {
        return "com.wurmonline.client.resources.Pack";
    }

    @Override
    public void apply(CtClass ctClass) throws Exception {
        CtMethod initMethod = ctClass.getMethod("init", "(Lcom/wurmonline/client/resources/Resources;)V");

        initMethod.instrument(new ExprEditor() {
            @Override
            public void edit(MethodCall m) throws CannotCompileException {
                if (m.getMethodName().equals("exists")) {
                    // Allow virtual packs (starting with "~") to skip existence check
                    m.replace("$_ = $0.getFilePath().startsWith(\"~\") || $proceed();");
                }
            }
        });
    }

    @Override
    public int getPriority() {
        return 95;  // High priority - must run before packs are initialized
    }

    @Override
    public String getDescription() {
        return "Pack.init() virtual pack support";
    }

    @Override
    public Collection<String> getConflictKeys() {
        return Collections.singleton("client.resources.pack.init");
    }
}
