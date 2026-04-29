package com.garward.wurmmodloader.client.api.events.gui;

import com.garward.wurmmodloader.client.api.events.base.Event;

/**
 * Fired at the start of
 * {@code com.wurmonline.client.renderer.cave.CaveWallPicker.getHoverName()}.
 * Mirrors {@link TilePickerHoverNameEvent} for cave-wall picks.
 *
 * <p>The patch pre-extracts the World, tile coords, vanilla {@code wallSide}
 * (0 = floor, 1 = ceiling, 7 = corner, 8/9/10/11 = borders), the vanilla
 * {@code name} field (e.g. {@code "Iron vein"}, {@code "Rock"}), and the
 * pre-computed {@code getSlopeSuffix(true)} so mods can preserve vanilla's
 * slope formatting in overrides.
 *
 * @since 0.4.1
 */
public class CaveWallPickerHoverNameEvent extends Event {

    private final Object picker;
    private final Object world;
    private final int x;
    private final int y;
    private final int wallSide;
    private final String name;
    private final String slopeSuffix;
    private String overrideName;

    public CaveWallPickerHoverNameEvent(Object picker, Object world, int x, int y,
                                        int wallSide, String name, String slopeSuffix) {
        super(false);
        this.picker = picker;
        this.world = world;
        this.x = x;
        this.y = y;
        this.wallSide = wallSide;
        this.name = name;
        this.slopeSuffix = slopeSuffix;
    }

    /** The {@code com.wurmonline.client.renderer.cave.CaveWallPicker} instance. */
    public Object getPicker() {
        return picker;
    }

    public Object getWorld() {
        return world;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    /**
     * Vanilla wallSide: 0 = floor, 1 = ceiling, 7 = corner,
     * 8/9/10/11 = borders.
     */
    public int getWallSide() {
        return wallSide;
    }

    /** Vanilla {@code name} field — the rock-type label. */
    public String getName() {
        return name;
    }

    /**
     * Pre-computed {@code CaveWallPicker.getSlopeSuffix(true)} or {@code null}.
     */
    public String getSlopeSuffix() {
        return slopeSuffix;
    }

    public String getOverrideName() {
        return overrideName;
    }

    public void setOverrideName(String overrideName) {
        this.overrideName = overrideName;
    }

    @Override
    public String toString() {
        return "CaveWallPickerHoverName[" + x + "," + y + ",wallSide=" + wallSide + "]";
    }
}
