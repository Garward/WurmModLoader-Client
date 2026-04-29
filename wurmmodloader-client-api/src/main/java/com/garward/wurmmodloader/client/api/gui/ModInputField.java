package com.garward.wurmmodloader.client.api.gui;

import com.wurmonline.client.renderer.gui.InputFieldListener;
import com.wurmonline.client.renderer.gui.WurmInputField;

import java.lang.reflect.Field;
import java.util.function.Consumer;

/**
 * Single-line text input wrapping vanilla {@link WurmInputField}.
 *
 * <p>Provides a {@link Consumer Consumer&lt;String&gt;} callback that fires on
 * every keystroke (live updates) and another for Enter / submit. Auto-sized to
 * a configurable pixel width on construction.
 */
public class ModInputField extends WurmInputField {

    private static final int DEFAULT_HEIGHT = 22;

    private Consumer<String> onChange;
    private Consumer<String> onSubmit;

    public ModInputField(String name, int widthPixels) {
        this(name, widthPixels, DEFAULT_HEIGHT, null, null);
    }

    public ModInputField(String name,
                         int widthPixels,
                         Consumer<String> onChange,
                         Consumer<String> onSubmit) {
        this(name, widthPixels, DEFAULT_HEIGHT, onChange, onSubmit);
    }

    public ModInputField(String name,
                         int widthPixels,
                         int heightPixels,
                         Consumer<String> onChange,
                         Consumer<String> onSubmit) {
        super(name, new ListenerBridge(), 1, -1);
        // The listener is stored in a private final field; reach in
        // reflectively to back-link it to this owner.
        try {
            Field f = WurmInputField.class.getDeclaredField("inputFieldListener");
            f.setAccessible(true);
            ListenerBridge bridge = (ListenerBridge) f.get(this);
            bridge.owner = this;
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("ModInputField: cannot bind listener", e);
        }
        this.onChange = onChange;
        this.onSubmit = onSubmit;
        // Force a fixed pixel size so the parent panel doesn't resize the
        // input to fit its (initially empty) text.
        this.sizeFlags = FIXED_WIDTH | FIXED_HEIGHT;
        setSize(widthPixels, heightPixels);
    }

    public ModInputField onChange(Consumer<String> handler) {
        this.onChange = handler;
        return this;
    }

    public ModInputField onSubmit(Consumer<String> handler) {
        this.onSubmit = handler;
        return this;
    }

    /** Returns the current input string. */
    public String value() {
        return getText();
    }

    /** Sets the input string and moves the caret to the end. */
    public ModInputField setValue(String text) {
        setTextMoveToEnd(text == null ? "" : text);
        return this;
    }

    private static final class ListenerBridge implements InputFieldListener {
        ModInputField owner;

        @Override
        public void handleInput(String text) {
            if (owner != null && owner.onSubmit != null) {
                owner.onSubmit.accept(text);
            }
        }

        @Override
        public void handleInputChanged(WurmInputField field, String text) {
            if (owner != null && owner.onChange != null) {
                owner.onChange.accept(text);
            }
        }

        @Override
        public void handleEscape(WurmInputField field) {
            // No-op: leave focus / value alone.
        }
    }
}
