package com.garward.wurmmodloader.client.api.gui;

import com.wurmonline.client.renderer.gui.WurmArrayPanel;

/**
 * Layout direction for a {@link ModArrayPanel}.
 *
 * <p>Wraps {@code WurmArrayPanel.DIR_*} so mods never touch the raw ints.
 */
public enum ArrayDirection {
    VERTICAL(WurmArrayPanel.DIR_VERTICAL),
    HORIZONTAL(WurmArrayPanel.DIR_HORIZONTAL),
    VERTICAL_INVERTED(WurmArrayPanel.DIR_VERTICAL_INV);

    private final int wurmConstant;

    ArrayDirection(int wurmConstant) {
        this.wurmConstant = wurmConstant;
    }

    public int toWurmConstant() {
        return wurmConstant;
    }
}
