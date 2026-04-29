package com.garward.wurmmodloader.client.declarativeui;

import com.garward.wurmmodloader.client.api.gui.ModWindow;
import com.wurmonline.client.renderer.gui.FlexComponent;

/**
 * Concrete {@link ModWindow} subclass used for all declaratively-built windows.
 * {@code ModWindow} is abstract; this exists solely to allow instantiation.
 */
final class DeclarativeWindow extends ModWindow {
    DeclarativeWindow(String title, FlexComponent content, int width, int height) {
        super(title);
        lockSize();
        installContent(content, width, height);
    }
}
