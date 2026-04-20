package com.garward.wurmmodloader.client.api.gui;

import com.wurmonline.client.renderer.gui.FlexComponent;
import com.wurmonline.client.renderer.gui.WWindow;

/**
 * Base class for mod-authored windows.
 *
 * <p>Extends {@link WWindow}, but mods should only use the protected helpers
 * below — don't poke internal fields like {@code resizable}, {@code minimized},
 * {@code storedHeight} directly. Those are vanilla layout state and writing
 * to them from outside the window's own lifecycle produces spooky bugs.
 *
 * <p>Register the window with the HUD via {@link ModHud#register(WWindow)}
 * once constructed. Toggle visibility with {@link ModHud#toggle(WWindow)}.
 */
public abstract class ModWindow extends WWindow {

    protected ModWindow(String title) {
        super(title);
        setTitle(title);
    }

    /**
     * Install the main content component and finalize sizing. Call once from
     * the constructor after configuring child components.
     */
    protected void installContent(FlexComponent content, int width, int height) {
        setComponent(content);
        setInitialSize(width, height, false);
        layout();
    }

    /**
     * Make the window a fixed size (no resize grip, no stretch).
     */
    protected void lockSize() {
        resizable = false;
    }

    /**
     * Default close behavior: toggle off via the HUD. Override to customize.
     */
    @Override
    public void closePressed() {
        ModHud.get().toggle(this);
    }
}
