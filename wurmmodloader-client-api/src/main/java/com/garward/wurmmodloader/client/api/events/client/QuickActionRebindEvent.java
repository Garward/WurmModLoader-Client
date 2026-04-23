package com.garward.wurmmodloader.client.api.events.client;

import com.garward.wurmmodloader.client.api.events.base.Event;

/**
 * Fired when vanilla's {@code WurmPopup.rebindPrimary()} is about to commit
 * a "hold key over right-click action" quick-bind. Cancellable — a handler
 * that {@link #cancel()}s suppresses vanilla's {@code bind <key> <class>} /
 * {@code temporaryBind} path, then is free to write its own bind (e.g. a
 * target-aware {@code act <id> <target>} macro).
 *
 * <p>The raw key encoding matches {@code WurmPopup.quickBindKey}: LWJGL
 * keyboard codes when {@code rawKey <= 4096}, or {@code 4096 + mouseButton}
 * for mouse buttons. Resolve via {@code org.lwjgl.input.Keyboard.getKeyName}
 * (minus "Mouse" prefix handling).
 *
 * @since 0.3.0
 */
public class QuickActionRebindEvent extends Event {

    private final short actionId;
    private final String actionName;
    private final int rawKey;
    private final boolean ctrlDown;
    private final boolean shiftDown;
    private final boolean altDown;

    public QuickActionRebindEvent(short actionId, String actionName, int rawKey,
                                  boolean ctrlDown, boolean shiftDown, boolean altDown) {
        super(true);
        this.actionId = actionId;
        this.actionName = actionName;
        this.rawKey = rawKey;
        this.ctrlDown = ctrlDown;
        this.shiftDown = shiftDown;
        this.altDown = altDown;
    }

    public short getActionId() { return actionId; }
    public String getActionName() { return actionName; }
    public int getRawKey() { return rawKey; }
    public boolean isCtrlDown() { return ctrlDown; }
    public boolean isShiftDown() { return shiftDown; }
    public boolean isAltDown() { return altDown; }
    public boolean isMouseButton() { return rawKey > 4096; }
}
