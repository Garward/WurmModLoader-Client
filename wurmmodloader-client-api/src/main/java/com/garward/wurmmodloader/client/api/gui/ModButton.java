package com.garward.wurmmodloader.client.api.gui;

import com.wurmonline.client.renderer.gui.ButtonListener;
import com.wurmonline.client.renderer.gui.WButton;

/**
 * Button with a {@link Runnable} click callback.
 *
 * <p>Wraps {@link WButton} + {@link ButtonListener} so mods don't have to
 * hand-write anonymous listener classes. Use {@link #onClick(Runnable)} to
 * change the action after construction.
 */
public class ModButton extends WButton implements ButtonListener {

    private Runnable onClick;

    public ModButton(String label) {
        this(label, null, null);
    }

    public ModButton(String label, String hoverText, Runnable onClick) {
        super(label, null, hoverText);
        setButtonListener(this);
        this.onClick = onClick;
    }

    /**
     * Larger button variant: the label is drawn with extra internal padding,
     * producing a square-ish chunky button useful for icon glyphs.
     *
     * <p>{@code hPadding} and {@code vPadding} are added on each side of the
     * text when Wurm's {@link WButton} auto-sizes on label change. The base
     * label sizing adds 8px of frame padding on top.
     */
    public ModButton(String label, String hoverText, Runnable onClick, int hPadding, int vPadding) {
        super(label, hPadding, vPadding);
        setButtonListener(this);
        if (hoverText != null && !hoverText.isEmpty()) {
            setHoverString(hoverText);
        }
        this.onClick = onClick;
    }

    /** Replace the click handler. */
    public ModButton onClick(Runnable handler) {
        this.onClick = handler;
        return this;
    }

    @Override
    public void buttonPressed(WButton button) {
        // no-op; fires on down-press. Mods care about the click.
    }

    @Override
    public void buttonClicked(WButton button) {
        if (onClick != null) {
            onClick.run();
        }
    }
}
