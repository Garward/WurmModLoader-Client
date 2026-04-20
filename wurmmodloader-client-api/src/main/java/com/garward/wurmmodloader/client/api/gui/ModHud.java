package com.garward.wurmmodloader.client.api.gui;

import com.wurmonline.client.renderer.gui.HeadsUpDisplay;
import com.wurmonline.client.renderer.gui.WWindow;
import com.wurmonline.client.renderer.gui.WurmComponent;
import com.wurmonline.client.settings.SavePosManager;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.logging.Logger;

/**
 * Central entry point for attaching mod-authored widgets to the HUD.
 *
 * <p>Centralizes the private-field reflection that used to be copy-pasted into
 * each mod: {@code HeadsUpDisplay.addComponent} (private method) and
 * {@code HeadsUpDisplay.savePosManager} (private field). If vanilla ever
 * renames those, we fix it here once instead of in every mod.
 *
 * <p>Safe to call before the HUD exists — {@link #isReady()} reports whether
 * {@code WurmComponent.hud} has been populated yet. Register components from
 * your {@code ClientInitEvent} handler (or later).
 */
public final class ModHud {

    private static final Logger logger = Logger.getLogger(ModHud.class.getName());
    private static final ModHud INSTANCE = new ModHud();

    public static ModHud get() {
        return INSTANCE;
    }

    private ModHud() {}

    /** True once the vanilla HUD is initialized and accepting components. */
    public boolean isReady() {
        return WurmComponent.hud != null;
    }

    private HeadsUpDisplay hud() {
        HeadsUpDisplay h = WurmComponent.hud;
        if (h == null) {
            throw new IllegalStateException(
                "ModHud: HeadsUpDisplay is not yet initialized. "
              + "Register components from ClientInitEvent or later.");
        }
        return h;
    }

    /**
     * Add a component to the HUD so it will be rendered and receive input.
     * Calls the private {@code HeadsUpDisplay.addComponent} reflectively.
     */
    public void register(WurmComponent component) {
        try {
            Method m = HeadsUpDisplay.class.getDeclaredMethod("addComponent", WurmComponent.class);
            m.setAccessible(true);
            m.invoke(hud(), component);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("ModHud.register failed", e);
        }
    }

    /**
     * Toggle a component's visibility via the HUD. The toggleComponent method
     * itself is public (access-widened), so this is a thin, safe passthrough.
     */
    public void toggle(WurmComponent component) {
        hud().toggleComponent(component);
    }

    /**
     * Register a window with the save-position manager so its location is
     * persisted between sessions.
     *
     * <p>{@code savePosManager} on {@code HeadsUpDisplay} is private; this
     * reaches in reflectively so mods don't have to.
     *
     * @param window window to persist (must implement {@code WindowSerializer})
     * @param saveKey unique config key for this window's position
     */
    public void rememberPosition(WWindow window, String saveKey) {
        try {
            Field f = HeadsUpDisplay.class.getDeclaredField("savePosManager");
            f.setAccessible(true);
            SavePosManager mgr = (SavePosManager) f.get(hud());
            if (mgr == null) {
                logger.warning("ModHud.rememberPosition: savePosManager is null for key " + saveKey);
                return;
            }
            mgr.registerAndRefresh(window, saveKey);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("ModHud.rememberPosition failed for " + saveKey, e);
        }
    }
}
