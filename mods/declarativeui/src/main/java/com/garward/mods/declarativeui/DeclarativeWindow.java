package com.garward.mods.declarativeui;

import com.garward.wurmmodloader.client.api.gui.ModWindow;
import com.wurmonline.client.renderer.gui.FlexComponent;

/**
 * Concrete {@link ModWindow} subclass used for all declaratively-built windows.
 * Vanilla {@code ModWindow} is abstract, so we need a subclass just to allow
 * instantiation — it exists for no other reason.
 */
final class DeclarativeWindow extends ModWindow {
    DeclarativeWindow(String title, FlexComponent content, int width, int height) {
        super(title);
        lockSize();
        installContent(content, width, height);
    }
}
