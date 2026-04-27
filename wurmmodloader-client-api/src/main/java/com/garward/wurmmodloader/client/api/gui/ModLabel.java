package com.garward.wurmmodloader.client.api.gui;

import com.wurmonline.client.renderer.backend.Queue;
import com.wurmonline.client.renderer.gui.text.TextFont;

/**
 * Simple single-line text label.
 *
 * <p>Wraps Wurm's {@link TextFont} so mods don't have to touch the vanilla font
 * API directly. Set text via {@link #setText(String)}; the component resizes to
 * fit and requests its parent to re-layout.
 *
 * <p>Choose the font style at construction — default is {@link TextFont#getText()}.
 * RGBA tint is white by default; override with {@link #setColor(float, float, float, float)}.
 */
public class ModLabel extends ModComponent {

    private String text;
    private TextFont font;
    private float r = 1f, g = 1f, b = 1f, a = 1f;

    public ModLabel(String text) {
        this(text, TextFont.getText());
    }

    public ModLabel(String text, TextFont font) {
        super(text == null ? "label" : "label:" + text);
        this.text = text == null ? "" : text;
        this.font = font == null ? TextFont.getText() : font;
        refreshSize();
    }

    public ModLabel setText(String text) {
        String next = text == null ? "" : text;
        if (!next.equals(this.text)) {
            this.text = next;
            refreshSize();
        }
        return this;
    }

    public String getText() {
        return text;
    }

    public ModLabel setFont(TextFont font) {
        if (font != null && font != this.font) {
            this.font = font;
            refreshSize();
        }
        return this;
    }

    public ModLabel setColor(float r, float g, float b, float a) {
        this.r = r; this.g = g; this.b = b; this.a = a;
        return this;
    }

    private void refreshSize() {
        int w = Math.max(1, font.getWidth(text));
        // +1 mirrors vanilla WurmLabel — without the extra row, scissor's
        // half-open bottom edge clips the descender every other frame and
        // looks like the text is fading in and out.
        int h = Math.max(1, font.getHeight() + 1);
        // Clear FIXED flags before setLocation so the resize takes effect;
        // re-arm them afterward so the parent panel's layout can't stretch us.
        // setInitialSize() — used previously — centers on screen, which is fine
        // for top-level windows but yanks a label out of its parent every frame
        // refreshSize runs, producing a visible flicker.
        sizeFlags = 0;
        setLocation(getScreenX(), getScreenY(), w, h);
        sizeFlags = FIXED_WIDTH | FIXED_HEIGHT;
    }

    @Override
    protected boolean consumesMouseInput() {
        return false;
    }

    @Override
    protected void onRender(Queue queue, float alpha) {
        if (text.isEmpty()) return;
        // SimpleTextFont.moveTo() treats y as the baseline; pass bottom edge.
        font.moveTo(getScreenX(), getScreenY() + font.getHeight());
        // Ignore parent alpha for text — vanilla WurmLabel/WButton both hardcode
        // 1.0f. Some parents (windows during fade, panels during hover) animate
        // their alpha every frame, which made the text fade in and out rapidly.
        font.paint(queue, text, r, g, b, a);
    }
}
