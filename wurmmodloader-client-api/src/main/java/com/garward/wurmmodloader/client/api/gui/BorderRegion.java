package com.garward.wurmmodloader.client.api.gui;

import com.wurmonline.client.renderer.gui.WurmBorderPanel;

/**
 * Regions in a {@link ModBorderPanel}.
 *
 * <p>Wraps the raw {@code int} constants from {@code WurmBorderPanel} so mods
 * never touch them directly — those constants are part of Wurm's internals
 * and are not meant to be treated as a public enum by mod authors.
 */
public enum BorderRegion {
    NORTH(WurmBorderPanel.NORTH),
    EAST(WurmBorderPanel.EAST),
    SOUTH(WurmBorderPanel.SOUTH),
    WEST(WurmBorderPanel.WEST),
    CENTER(WurmBorderPanel.CENTER);

    private final int wurmConstant;

    BorderRegion(int wurmConstant) {
        this.wurmConstant = wurmConstant;
    }

    public int toWurmConstant() {
        return wurmConstant;
    }
}
