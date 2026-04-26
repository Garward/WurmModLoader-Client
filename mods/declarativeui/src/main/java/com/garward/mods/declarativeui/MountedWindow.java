package com.garward.mods.declarativeui;

import com.garward.wurmmodloader.client.api.gui.ModLabel;

import java.util.HashMap;
import java.util.Map;

/**
 * Live state for a single server-declared window.
 *
 * <p>Holds the {@link DeclarativeWindow} that was registered with the HUD, plus a map
 * from bind-key to the {@link ModLabel} widgets listening for {@code BIND}
 * updates. Separating this from construction keeps {@link WindowBuilder}
 * purely concerned with tree walking.
 */
final class MountedWindow {

    /** Server-provided stable id — used as toggle-save key and for lookups. */
    final String windowId;

    /** Set by {@link WindowBuilder} once the content tree is built. */
    DeclarativeWindow window;

    /** bind-key → label that renders the bound value. */
    final Map<String, ModLabel> bindings = new HashMap<>();

    MountedWindow(String windowId, DeclarativeWindow window) {
        this.windowId = windowId;
        this.window = window;
    }

    void applyBindings(Map<String, String> values) {
        for (Map.Entry<String, String> e : values.entrySet()) {
            ModLabel label = bindings.get(e.getKey());
            if (label != null) {
                label.setText(e.getValue());
            }
        }
    }
}
