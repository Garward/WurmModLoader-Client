package com.garward.wurmmodloader.client.core.bytecode.patches;

import com.garward.wurmmodloader.client.api.bytecode.BytecodePatch;
import com.garward.wurmmodloader.client.modcomm.ModCommConstants;

import javassist.CtClass;
import javassist.CtMethod;
import javassist.expr.ExprEditor;
import javassist.expr.MethodCall;

import java.util.Arrays;
import java.util.Collection;

/**
 * Installs the client-side ModComm hooks into
 * {@code com.wurmonline.client.comm.SimpleServerConnectionClass}.
 *
 * <ol>
 *   <li>{@code reallyHandle(int, ByteBuffer)} — before the first {@code bb.get()}
 *       executes, peek the command byte and divert {@code CMD_MODCOMM} packets
 *       to {@link com.garward.wurmmodloader.client.modcomm.ModCommHandler#handlePacket}.</li>
 *   <li>{@code reallyHandleCmdMessage(ByteBuffer)} — before the {@code textMessage}
 *       call executes, inspect the title+message for the ModComm banner
 *       ({@code ":Event"} / {@code [ModCommV1]}) and call
 *       {@link com.garward.wurmmodloader.client.modcomm.ModCommHandler#startHandshake} when matched.</li>
 * </ol>
 *
 * <p>The hook code is emitted as fully-qualified references; the modloader jar
 * is on the context classloader by the time the patched client executes, so
 * this patch has no build-time dependency on Wurm classes.
 */
public class SimpleServerConnectionModCommPatch implements BytecodePatch {

    private static final String HANDLER = "com.garward.wurmmodloader.client.modcomm.ModCommHandler";
    private static final String BANNER_TITLE = ":Event";
    private static final String BANNER_MARKER = ModCommConstants.MARKER;

    @Override
    public String getTargetClassName() {
        return "com.wurmonline.client.comm.SimpleServerConnectionClass";
    }

    @Override
    public void apply(CtClass ctClass) throws Exception {
        CtMethod reallyHandle = ctClass.getDeclaredMethod("reallyHandle");
        reallyHandle.instrument(new ExprEditor() {
            private boolean patched = false;

            @Override
            public void edit(MethodCall m) throws javassist.CannotCompileException {
                if (patched) return;
                if (!"get".equals(m.getMethodName())) return;
                if (!"java.nio.ByteBuffer".equals(m.getClassName())) return;
                m.replace(
                    "$_ = $proceed($$);" +
                    "if ($_ == " + (int) ModCommConstants.CMD_MODCOMM + ") {" +
                    "    " + HANDLER + ".handlePacket($0);" +
                    "    return;" +
                    "}"
                );
                patched = true;
            }
        });

        CtMethod reallyHandleCmdMessage = ctClass.getDeclaredMethod("reallyHandleCmdMessage");
        reallyHandleCmdMessage.instrument(new ExprEditor() {
            @Override
            public void edit(MethodCall m) throws javassist.CannotCompileException {
                if (!"textMessage".equals(m.getMethodName())) return;
                m.replace(
                    "{" +
                    "    if (\"" + BANNER_TITLE + "\".equals($1) && $5 != null && $5.startsWith(\"" + BANNER_MARKER + "\")) {" +
                    "        " + HANDLER + ".startHandshake();" +
                    "    }" +
                    "    $_ = $proceed($$);" +
                    "}"
                );
            }
        });
    }

    @Override
    public int getPriority() {
        return 500;
    }

    @Override
    public String getDescription() {
        return "Install client ModComm hooks on SimpleServerConnectionClass";
    }

    @Override
    public Collection<String> getConflictKeys() {
        return Arrays.asList("client.network.modcomm", "client.network.banner");
    }
}
