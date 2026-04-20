package com.garward.wurmmodloader.client.api.gui;

/**
 * Per-child layout instructions for layout-aware containers like
 * {@link ModStackPanel}. Mutable, fluent-builder style — typical use:
 *
 * <pre>{@code
 * panel.addChild(button, new LayoutHints()
 *     .alignX(Alignment.CENTER)
 *     .aspectRatio(1f)
 *     .margin(4));
 * }</pre>
 *
 * <p>Resolution rules (per axis):
 * <ul>
 *   <li>If {@link #weight} &gt; 0: this child gets a share of the container's
 *       remaining main-axis space proportional to its weight (after fixed
 *       children are sized).</li>
 *   <li>Else if a {@code preferredWidth/Height} ≥ 0 is set: that wins.</li>
 *   <li>Else if {@link #aspectRatio} &gt; 0: the main-axis size is derived
 *       from the cross-axis size (which is the container's cross extent).
 *       Aspect = width / height.</li>
 *   <li>Else: the child's natural component size is used.</li>
 * </ul>
 *
 * <p>Cross-axis sizing: {@link Alignment#FILL} (the default for both axes)
 * stretches to the container's cross extent; any other alignment uses the
 * preferred or aspect-derived size and pins it.
 */
public final class LayoutHints {

    public Alignment alignX = Alignment.FILL;
    public Alignment alignY = Alignment.FILL;

    /** width / height ratio. {@code 0} means "no aspect constraint". */
    public float aspectRatio = 0f;

    /** Share of remaining main-axis space. {@code 0} means "use preferred". */
    public float weight = 0f;

    /** -1 means "unset". */
    public int preferredWidth = -1;
    public int preferredHeight = -1;

    public Insets margin = Insets.ZERO;

    public LayoutHints alignX(Alignment a) { this.alignX = a; return this; }
    public LayoutHints alignY(Alignment a) { this.alignY = a; return this; }
    public LayoutHints align(Alignment x, Alignment y) { this.alignX = x; this.alignY = y; return this; }
    public LayoutHints aspectRatio(float r) { this.aspectRatio = r; return this; }
    public LayoutHints weight(float w) { this.weight = w; return this; }
    public LayoutHints preferredSize(int w, int h) { this.preferredWidth = w; this.preferredHeight = h; return this; }
    public LayoutHints margin(Insets m) { this.margin = m; return this; }
    public LayoutHints margin(int n) { this.margin = Insets.uniform(n); return this; }

    /**
     * Components implementing this provide a default {@link LayoutHints} that
     * containers will use if the caller doesn't pass one explicitly. Lets a
     * widget like {@code ModImageButton} declare "I'm 1:1 by nature" without
     * forcing every caller to remember.
     */
    public interface Provider {
        LayoutHints getDefaultLayoutHints();
    }
}
