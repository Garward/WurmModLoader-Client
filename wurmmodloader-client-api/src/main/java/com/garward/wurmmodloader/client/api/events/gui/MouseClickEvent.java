package com.garward.wurmmodloader.client.api.events.gui;

import com.garward.wurmmodloader.client.api.events.base.Event;

/**
 * Fired when a mouse click occurs on a custom GUI component.
 *
 * <p>This event allows mods to handle mouse clicks without extending Wurm GUI classes.
 *
 * @since 0.2.0
 */
public class MouseClickEvent extends Event {

    private final Object component;
    private final int mouseX, mouseY; // Screen coordinates
    private final int button; // 0 = left, 1 = right, 2 = middle
    private final int clickCount; // 1 = single, 2 = double
    private final boolean pressed; // true = pressed, false = released

    public MouseClickEvent(Object component, int mouseX, int mouseY, int button, int clickCount, boolean pressed) {
        super(true); // Cancellable - prevent default handling

        this.component = component;
        this.mouseX = mouseX;
        this.mouseY = mouseY;
        this.button = button;
        this.clickCount = clickCount;
        this.pressed = pressed;
    }

    public Object getComponent() {
        return component;
    }

    public int getMouseX() {
        return mouseX;
    }

    public int getMouseY() {
        return mouseY;
    }

    public int getButton() {
        return button;
    }

    public boolean isLeftButton() {
        return button == 0;
    }

    public boolean isRightButton() {
        return button == 1;
    }

    public boolean isMiddleButton() {
        return button == 2;
    }

    public int getClickCount() {
        return clickCount;
    }

    public boolean isPressed() {
        return pressed;
    }

    public boolean isReleased() {
        return !pressed;
    }

    @Override
    public String toString() {
        return "MouseClick[button=" + button + ", x=" + mouseX + ", y=" + mouseY +
               ", clicks=" + clickCount + ", pressed=" + pressed + "]";
    }
}
