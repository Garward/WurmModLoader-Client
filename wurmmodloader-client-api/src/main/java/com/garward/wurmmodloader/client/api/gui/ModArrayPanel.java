package com.garward.wurmmodloader.client.api.gui;

import com.wurmonline.client.renderer.gui.FlexComponent;
import com.wurmonline.client.renderer.gui.WurmArrayPanel;

/**
 * Stacked layout container (vertical / horizontal list of children).
 *
 * <p>Thin typed wrapper over {@link WurmArrayPanel} — {@link ArrayDirection}
 * replaces the raw {@code DIR_*} ints.
 */
public class ModArrayPanel<T extends FlexComponent> extends WurmArrayPanel<T> {

    public ModArrayPanel(String name, ArrayDirection direction) {
        super(name, direction.toWurmConstant());
    }
}
