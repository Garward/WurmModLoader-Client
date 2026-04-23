package com.garward.wurmmodloader.client.core.bytecode.patches;

import com.garward.wurmmodloader.client.api.bytecode.BytecodePatch;
import javassist.CtClass;
import javassist.CtMethod;

import java.util.Collection;
import java.util.Collections;
import java.util.logging.Logger;

/**
 * Patches {@code WurmPopup.rebindPrimary()} to fire a cancellable
 * {@code QuickActionRebindEvent} before vanilla writes its {@code bind <key>
 * <class>} / {@code temporaryBind}. A cancellation lets a mod hijack the
 * hold-key-over-right-click gesture to install a target-aware macro (e.g.
 * {@code act <id> <target>}) via its own UI.
 *
 * @since 0.3.0
 */
public class WurmPopupRebindPatch implements BytecodePatch {

    private static final Logger logger = Logger.getLogger(WurmPopupRebindPatch.class.getName());
    private static final String PROXY = "com.garward.wurmmodloader.client.modloader.ProxyClientHook";

    @Override
    public String getTargetClassName() {
        return "com.wurmonline.client.renderer.gui.WurmPopup";
    }

    @Override
    public void apply(CtClass ctClass) throws Exception {
        CtMethod m = ctClass.getDeclaredMethod("rebindPrimary");
        // Vanilla's first line already guards against a null quickBindButton,
        // but we gate anyway so the proxy never sees a null action.
        m.insertBefore(
            "{ try { " +
            "  if (this.quickBindButton != null && this.quickBindButton.action != null) { " +
            "    if (" + PROXY + ".fireQuickActionRebindEventCancelled(" +
            "        this.quickBindButton.action.getId(), " +
            "        this.quickBindButton.action.getName(), " +
            "        this.quickBindKey, this.ctrlDown, this.shiftDown, this.altDown)) { " +
            "      return; " +
            "    } " +
            "  } " +
            "} catch (Throwable t) { t.printStackTrace(); } }"
        );
        logger.info("[WurmPopupRebindPatch] Patched WurmPopup.rebindPrimary");
    }

    @Override
    public Collection<String> getConflictKeys() {
        return Collections.singleton("client.wurmpopup.rebindPrimary");
    }

    @Override
    public String getDescription() {
        return "Fire cancellable QuickActionRebindEvent from WurmPopup.rebindPrimary";
    }
}
