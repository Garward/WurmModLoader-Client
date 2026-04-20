package com.garward.wurmmodloader.client.api.gui;

import com.wurmonline.client.renderer.gui.FlexComponent;
import com.wurmonline.client.renderer.gui.WurmBorderPanel;

/**
 * Five-region layout container (north/east/south/west/center).
 *
 * <p>Thin typed wrapper over {@link WurmBorderPanel} — {@link BorderRegion}
 * replaces the raw {@code int} region constants.
 */
public class ModBorderPanel extends WurmBorderPanel {

    public ModBorderPanel(String name) {
        super(name);
    }

    /** Install a child component in the given region. */
    public void setRegion(FlexComponent component, BorderRegion region) {
        setComponent(component, region.toWurmConstant());
    }
}
