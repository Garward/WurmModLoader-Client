package com.garward.wurmmodloader.client.api.gui;

/**
 * Immutable per-edge spacing in pixels.
 *
 * <p>Used as both container padding (inside the panel) and child margin
 * (outside the child). Construct via {@link #of}, {@link #uniform}, or
 * {@link #symmetric}.
 */
public final class Insets {

    public static final Insets ZERO = new Insets(0, 0, 0, 0);

    public final int top, right, bottom, left;

    private Insets(int top, int right, int bottom, int left) {
        this.top = top;
        this.right = right;
        this.bottom = bottom;
        this.left = left;
    }

    public static Insets of(int top, int right, int bottom, int left) {
        return new Insets(top, right, bottom, left);
    }

    public static Insets uniform(int n) {
        return new Insets(n, n, n, n);
    }

    public static Insets symmetric(int vertical, int horizontal) {
        return new Insets(vertical, horizontal, vertical, horizontal);
    }
}
