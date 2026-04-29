package com.garward.wurmmodloader.client.declarativeui;

import com.garward.wurmmodloader.client.api.gui.ModLabel;

import java.util.HashMap;
import java.util.Map;

/**
 * Live state for a single server-declared window: its registered HUD window
 * plus the bind-key → label map populated by {@link WindowBuilder} so
 * {@code BIND} updates can find the right widgets.
 */
final class MountedWindow {

    final String windowId;
    DeclarativeWindow window;
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
