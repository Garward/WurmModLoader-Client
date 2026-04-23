package com.garward.wurmmodloader.client.core.bytecode.patches;

import com.garward.wurmmodloader.client.api.bytecode.BytecodePatch;
import javassist.CtClass;
import javassist.CtMethod;

import java.util.Collection;
import java.util.Collections;
import java.util.logging.Logger;

/**
 * Patches {@code PlayerAction.getName()} to consult mods for a display-name
 * override — enables e.g. "act_show on" (show action ids in right-click menus)
 * without the mod touching Wurm bytecode itself.
 *
 * @since 0.3.0
 */
public class PlayerActionNamePatch implements BytecodePatch {

    private static final Logger logger = Logger.getLogger(PlayerActionNamePatch.class.getName());
    private static final String PROXY = "com.garward.wurmmodloader.client.modloader.ProxyClientHook";

    @Override
    public String getTargetClassName() {
        return "com.wurmonline.shared.constants.PlayerAction";
    }

    @Override
    public void apply(CtClass ctClass) throws Exception {
        CtMethod m = ctClass.getDeclaredMethod("getName");
        m.insertBefore(
            "{ try { " +
            "String _ov = " + PROXY + ".fireGetPlayerActionNameEvent(this.id, this.name); " +
            "if (_ov != null) return _ov; " +
            "} catch (Throwable t) { t.printStackTrace(); } }"
        );
        logger.info("[PlayerActionNamePatch] Patched PlayerAction.getName");
    }

    @Override
    public Collection<String> getConflictKeys() {
        return Collections.singleton("client.action.name");
    }

    @Override
    public String getDescription() {
        return "Fire PlayerActionNameResolvedEvent from PlayerAction.getName";
    }
}
