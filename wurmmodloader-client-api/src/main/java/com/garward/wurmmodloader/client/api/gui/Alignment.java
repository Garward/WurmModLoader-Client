package com.garward.wurmmodloader.client.api.gui;

/**
 * Per-axis alignment used by layout-aware containers (e.g. {@link ModStackPanel}).
 *
 * <p>Applied independently on the X and Y axis via {@link LayoutHints}.
 * {@link #FILL} stretches the child to the available extent on that axis;
 * {@link #START} / {@link #CENTER} / {@link #END} pin the child at its
 * preferred (or aspect-derived) size.
 */
public enum Alignment {
    START, CENTER, END, FILL
}
