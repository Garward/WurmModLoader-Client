package com.garward.wurmmodloader.client.core.bytecode.patches;

import com.garward.wurmmodloader.client.api.bytecode.BytecodePatch;
import javassist.CtClass;
import javassist.CtMethod;
import javassist.NotFoundException;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.logging.Logger;

/**
 * Patches {@code ServerConnectionListenerClass.textMessage} (both overloads)
 * to fire {@code ClientEventMessageReceivedEvent} so mods can observe / filter
 * server messages.
 *
 * <ul>
 *   <li>Overload A: {@code textMessage(String window, float r, float g, float b, String text, byte type)}
 *       — plain string in {@code $5}.</li>
 *   <li>Overload B: {@code textMessage(String window, List<MulticolorLineSegment> segments, byte type)}
 *       — concatenate segments via {@code getText()}.</li>
 * </ul>
 *
 * If a subscriber cancels, return early so the message is suppressed.
 *
 * @since 0.4.0
 */
public class ServerConnectionTextMessagePatch implements BytecodePatch {

    private static final Logger logger = Logger.getLogger(ServerConnectionTextMessagePatch.class.getName());
    private static final String PROXY = "com.garward.wurmmodloader.client.modloader.ProxyClientHook";

    @Override
    public String getTargetClassName() {
        return "com.wurmonline.client.comm.ServerConnectionListenerClass";
    }

    @Override
    public void apply(CtClass ctClass) throws Exception {
        // Overload A — String text in $5, byte type in $6
        CtMethod a = findOverload(ctClass,
                "java.lang.String", "float", "float", "float", "java.lang.String", "byte");
        a.insertBefore(
            "{ try { " +
            "if (" + PROXY + ".fireClientEventMessageReceivedEvent($1, $5, $6)) return; " +
            "} catch (Throwable t) { t.printStackTrace(); } }"
        );

        // Overload B — List<MulticolorLineSegment> in $2, byte type in $3
        CtMethod b = findOverload(ctClass,
                "java.lang.String", "java.util.List", "byte");
        b.insertBefore(
            "{ try { " +
            "StringBuilder _sb = new StringBuilder(); " +
            "if ($2 != null) { " +
            "  java.util.Iterator _it = $2.iterator(); " +
            "  while (_it.hasNext()) { " +
            "    Object _seg = _it.next(); " +
            "    if (_seg != null) _sb.append(((com.wurmonline.shared.util.MulticolorLineSegment) _seg).getText()); " +
            "  } " +
            "} " +
            "if (" + PROXY + ".fireClientEventMessageReceivedEvent($1, _sb.toString(), $3)) return; " +
            "} catch (Throwable t) { t.printStackTrace(); } }"
        );

        logger.info("[ServerConnectionTextMessagePatch] Patched ServerConnectionListenerClass.textMessage (both overloads)");
    }

    private static CtMethod findOverload(CtClass cc, String... paramTypeNames) throws NotFoundException {
        outer:
        for (CtMethod m : cc.getDeclaredMethods("textMessage")) {
            CtClass[] params = m.getParameterTypes();
            if (params.length != paramTypeNames.length) continue;
            for (int i = 0; i < params.length; i++) {
                if (!params[i].getName().equals(paramTypeNames[i])) continue outer;
            }
            return m;
        }
        throw new NotFoundException("textMessage" + Arrays.toString(paramTypeNames));
    }

    @Override
    public Collection<String> getConflictKeys() {
        return Collections.singleton("client.event-message.received");
    }

    @Override
    public String getDescription() {
        return "Fire ClientEventMessageReceivedEvent from ServerConnectionListenerClass.textMessage (both overloads)";
    }
}
