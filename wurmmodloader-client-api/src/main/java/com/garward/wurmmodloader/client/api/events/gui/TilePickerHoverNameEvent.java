package com.garward.wurmmodloader.client.api.events.gui;

import com.garward.wurmmodloader.client.api.events.base.Event;

/**
 * Fired at the start of {@code com.wurmonline.client.renderer.TilePicker.getHoverName()}.
 * Subscribers may call {@link #setOverrideName(String)} to fully replace the
 * hover-name string returned to the engine; if no subscriber sets one, the
 * patch falls through to vanilla logic.
 *
 * <p>The patch pre-extracts data the subscriber would otherwise need
 * reflection to read: the World, tile-mesh coords, the section enum used by
 * vanilla (0 = main tile, 1/2 = borders, 3 = corner), and the result of
 * {@code TilePicker.getSlopeSuffix(true)} (a {@code " (Up 12)"}-shaped string
 * or {@code null}). Mods that want vanilla's slope segment can paste it into
 * their override verbatim.
 *
 * @since 0.4.1
 */
public class TilePickerHoverNameEvent extends Event {

    private final Object picker;
    private final Object world;
    private final int x;
    private final int y;
    private final int section;
    private final String slopeSuffix;
    private String overrideName;

    public TilePickerHoverNameEvent(Object picker, Object world, int x, int y,
                                    int section, String slopeSuffix) {
        super(false);
        this.picker = picker;
        this.world = world;
        this.x = x;
        this.y = y;
        this.section = section;
        this.slopeSuffix = slopeSuffix;
    }

    /** The {@code com.wurmonline.client.renderer.TilePicker} instance. */
    public Object getPicker() {
        return picker;
    }

    /** The {@code com.wurmonline.client.renderer.TilePicker.world} field. */
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
     * Vanilla section: 0 = main tile, 1 = north border, 2 = west border,
     * 3 = corner. Subscribers usually only override section 0.
     */
    public int getSection() {
        return section;
    }

    /**
     * Pre-computed result of {@code TilePicker.getSlopeSuffix(true)} —
     * {@code null} or a {@code " (Up 12)"}-shaped string. Read from the
     * patched class so mods don't have to reflect on the private method.
     */
    public String getSlopeSuffix() {
        return slopeSuffix;
    }

    public String getOverrideName() {
        return overrideName;
    }

    /**
     * Sets the hover-name returned to the engine. Pass {@code null} to fall
     * through to vanilla; any non-null value short-circuits the vanilla body.
     */
    public void setOverrideName(String overrideName) {
        this.overrideName = overrideName;
    }

    @Override
    public String toString() {
        return "TilePickerHoverName[" + x + "," + y + ",section=" + section + "]";
    }
}
