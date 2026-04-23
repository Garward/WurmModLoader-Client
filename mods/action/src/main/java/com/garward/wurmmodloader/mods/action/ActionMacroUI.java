package com.garward.wurmmodloader.mods.action;

import com.garward.wurmmodloader.client.api.gui.ArrayDirection;
import com.garward.wurmmodloader.client.api.gui.Insets;
import com.garward.wurmmodloader.client.api.gui.ModButton;
import com.garward.wurmmodloader.client.api.gui.ModHud;
import com.garward.wurmmodloader.client.api.gui.ModStackPanel;
import com.garward.wurmmodloader.client.api.gui.ModWindow;

import com.wurmonline.client.renderer.gui.HeadsUpDisplay;

import org.lwjgl.input.Keyboard;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Target-picker window for the hold-key-over-action quick-bind gesture.
 *
 * <p>Opens when the user holds a key over a right-click action (once {@code
 * act_show on}). The window shows the action, the key combo, and one button
 * per target keyword from {@link ActionClientMod#parseAct}. Clicking a target
 * writes a {@code bind <key> "act <id> <target>"} binding via the vanilla
 * console and closes.
 */
public final class ActionMacroUI extends ModWindow {

    private static final Logger logger = Logger.getLogger(ActionMacroUI.class.getName());

    /** Common targets laid out in the order users typically want them. */
    private static final String[] TARGETS = {
            "hover", "tile", "tool", "body", "selected", "area",
            "tile_n", "tile_s", "tile_e", "tile_w",
            "tile_ne", "tile_nw", "tile_se", "tile_sw",
            "toolbelt",
    };

    private final short actionId;
    private final String bindKey;   // already lowercased w/ "-" separators

    public ActionMacroUI(short actionId, String actionName, String bindKey) {
        super("Bind macro — " + stripIdSuffix(actionName, actionId)
                + " (" + actionId + ")  [" + bindKey + "]");
        this.actionId = actionId;
        this.bindKey = bindKey;
        lockSize();

        ModStackPanel root = new ModStackPanel("Macro targets", ArrayDirection.VERTICAL)
                .setBackgroundPainted(true)
                .setPadding(Insets.uniform(6))
                .setGap(3);

        for (String target : TARGETS) {
            String captured = target;
            root.addChild(new ModButton(target, "Bind: act " + actionId + " " + target,
                    () -> commit(captured)));
        }

        installContent(root, 260, 32 + TARGETS.length * 22);
    }

    private void commit(String target) {
        String cmd = "bind " + bindKey + " \"act " + actionId + " " + target + "\"";
        HeadsUpDisplay hud = ActionClientMod.getHud();
        if (hud == null) return;
        try {
            Field f = HeadsUpDisplay.class.getDeclaredField("console");
            f.setAccessible(true);
            Object console = f.get(hud);
            Method handleInput = console.getClass()
                    .getDeclaredMethod("handleInput", String.class, boolean.class);
            handleInput.setAccessible(true);
            handleInput.invoke(console, cmd, false);
            hud.consoleOutput("Bound " + bindKey + " → act " + actionId + " " + target);
        } catch (ReflectiveOperationException e) {
            logger.log(Level.WARNING, "[ActionMacroUI] failed to dispatch bind", e);
            hud.consoleOutput("act: failed to write bind (see log)");
        }
        ModHud.get().toggle(this);
    }

    /** Drop the trailing " (N)" that {@code act_show on} appends, if present. */
    private static String stripIdSuffix(String name, short id) {
        if (name == null) return "";
        String suffix = " (" + id + ")";
        return name.endsWith(suffix) ? name.substring(0, name.length() - suffix.length()) : name;
    }

    /**
     * Convert the raw key encoding from {@code QuickActionRebindEvent} plus its
     * modifier booleans into the same {@code ctrl-shift-a} form vanilla writes.
     */
    public static String formatBindKey(int rawKey, boolean ctrlDown, boolean shiftDown, boolean altDown) {
        String base = rawKey > 4096 ? "mouse" + (rawKey - 4096)
                                    : Keyboard.getKeyName(rawKey).toLowerCase();
        StringBuilder sb = new StringBuilder();
        if (ctrlDown) sb.append("ctrl-");
        if (shiftDown) sb.append("shift-");
        if (altDown) sb.append("alt-");
        sb.append(base);
        return sb.toString();
    }
}
