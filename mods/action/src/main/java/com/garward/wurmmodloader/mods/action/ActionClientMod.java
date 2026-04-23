package com.garward.wurmmodloader.mods.action;

import com.garward.wurmmodloader.client.api.events.base.SubscribeEvent;
import com.garward.wurmmodloader.client.api.events.client.ClientConsoleInputEvent;
import com.garward.wurmmodloader.client.api.events.client.PlayerActionNameResolvedEvent;
import com.garward.wurmmodloader.client.api.events.client.QuickActionRebindEvent;
import com.garward.wurmmodloader.client.api.events.lifecycle.ClientTickEvent;
import com.garward.wurmmodloader.client.api.events.map.ClientHUDInitializedEvent;
import com.garward.wurmmodloader.client.api.gui.ModHud;
import com.garward.wurmmodloader.client.modloader.ProxyClientHook;

import com.wurmonline.client.comm.ServerConnectionListenerClass;
import com.wurmonline.client.game.inventory.InventoryMetaItem;
import com.wurmonline.client.renderer.PickableUnit;
import com.wurmonline.client.renderer.cell.CellRenderable;
import com.wurmonline.client.renderer.cell.CreatureCellRenderable;
import com.wurmonline.client.renderer.cell.GroundItemCellRenderable;
import com.wurmonline.client.renderer.gui.HeadsUpDisplay;
import com.wurmonline.client.renderer.gui.PaperDollSlot;
import com.wurmonline.mesh.Tiles;
import com.wurmonline.shared.constants.PlayerAction;

import java.util.Arrays;
import java.util.Collection;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

/**
 * Port of Goldenflamer's fork of bdew's custom-action macro mod. Adds two
 * console commands:
 *
 * <ul>
 *   <li>{@code act_show on|off} — reveal numeric action ids in right-click
 *       menus (so users can discover the id they need).</li>
 *   <li>{@code act <id> <target>[|<id> <target>|…]} — dispatch an action (or
 *       pipe-chained sequence) against one of ~15 target keywords.</li>
 * </ul>
 *
 * <p>Also enables Wurm's dev-only quick keybind / quick mousebind / toggleKey /
 * rebindPrimary / updateWithKeybinds UI for ordinary players, by asking the
 * framework to force {@code isDev()} true (see {@code IsDevOverridePatch}).
 *
 * <p>The heavy-lifting framework patches are {@code PlayerActionNamePatch}
 * (for {@code act_show}) and {@code IsDevOverridePatch} (for the keybind UI
 * unlock). All other hooks (console input, HUD init, reflection) already exist
 * in the shared client-core.
 */
public class ActionClientMod {

    private static final Logger logger = Logger.getLogger("ActionMod");

    private static volatile boolean showActionNums = false;
    private static volatile HeadsUpDisplay hud;
    // Deferred UI open — mutating HUD components from inside rebindPrimary()
    // (which runs during HeadsUpDisplay.gameTick) trips a CME on the HUD's
    // component-list iterator. Stash the request, open it next tick instead.
    private static volatile ActionMacroUI pendingUi;

    @SubscribeEvent
    public void onHudInit(ClientHUDInitializedEvent event) {
        try {
            hud = (HeadsUpDisplay) event.getHud();
            Reflect.setup();
            ProxyClientHook.setDevOverride(true);
            logger.info("[ActionMod] Initialized — dev UI gates unlocked, macros armed.");
        } catch (Throwable t) {
            logger.log(Level.SEVERE, "[ActionMod] HUD init failed", t);
        }
    }

    @SubscribeEvent
    public void onActionName(PlayerActionNameResolvedEvent event) {
        if (!showActionNums) return;
        String name = event.getOriginalName();
        if (name == null || name.isEmpty()) return;
        event.setOverrideName(name + " (" + event.getActionId() + ")");
    }

    /**
     * Intercept the hold-key-over-action gesture to open a target-picker UI
     * instead of vanilla's bare {@code bind key action} write. Only kicks in
     * while {@code act_show on} is active — otherwise vanilla runs unchanged.
     */
    @SubscribeEvent
    public void onQuickRebind(QuickActionRebindEvent event) {
        if (!showActionNums || hud == null) return;
        event.cancel();
        String bindKey = ActionMacroUI.formatBindKey(
                event.getRawKey(), event.isCtrlDown(), event.isShiftDown(), event.isAltDown());
        try {
            pendingUi = new ActionMacroUI(event.getActionId(), event.getActionName(), bindKey);
        } catch (Throwable t) {
            logger.log(Level.WARNING, "[ActionMod] failed to build macro UI", t);
            hud.consoleOutput("act: macro UI failed (see log)");
        }
    }

    @SubscribeEvent
    public void onTick(ClientTickEvent event) {
        ActionMacroUI ui = pendingUi;
        if (ui == null) return;
        pendingUi = null;
        try {
            // register() already adds the component to the HUD's visible list;
            // an extra toggle() would remove it the same tick. Just register.
            ModHud.get().register(ui);
        } catch (Throwable t) {
            logger.log(Level.WARNING, "[ActionMod] failed to open macro UI", t);
            if (hud != null) hud.consoleOutput("act: macro UI failed (see log)");
        }
    }

    @SubscribeEvent
    public void onConsoleInput(ClientConsoleInputEvent event) {
        if (hud == null) return;
        String cmd = event.getCommand();
        String[] data = event.getArgs();
        if (cmd == null || data == null) return;

        if ("act_show".equals(cmd)) {
            handleActShow(data);
            event.cancel();
        } else if ("act".equals(cmd)) {
            handleAct(data);
            event.cancel();
        }
    }

    private static void handleActShow(String[] data) {
        if (data.length == 2) {
            if ("on".equals(data[1])) {
                showActionNums = true;
                hud.consoleOutput("Action numbers on");
                return;
            } else if ("off".equals(data[1])) {
                showActionNums = false;
                hud.consoleOutput("Action numbers off");
                return;
            }
        }
        hud.consoleOutput("Usage: act_show {on|off}");
    }

    private static void handleAct(String[] data) {
        // Stitch back together & split on pipe so "act 3 toolbelt | 154 tile" works.
        String[] commands = String.join(" ", Arrays.copyOfRange(data, 1, data.length)).split("\\|");
        for (String next : commands) {
            String[] parts = next.trim().split(" ");
            try {
                if (parts.length == 2) {
                    parseAct(Short.parseShort(parts[0]), parts[1]);
                } else {
                    hud.consoleOutput("Usage: act <id> <modifier>[|<id> <modifier>|...]");
                }
            } catch (NumberFormatException nfe) {
                hud.consoleOutput("act: Error parsing id '" + parts[0] + "'");
            } catch (ReflectiveOperationException roe) {
                logger.log(Level.WARNING, "[ActionMod] reflect failure", roe);
                hud.consoleOutput("act: internal error (see log)");
            }
        }
    }

    /**
     * Run one action. Public so the macro-builder UI can reuse the exact same
     * dispatch path as the console command (no parser drift).
     */
    public static void parseAct(short id, String target) throws ReflectiveOperationException {
        if (hud == null) return;
        PlayerAction act = new PlayerAction(id, PlayerAction.ANYTHING, "", false);
        switch (target) {
            case "hover":
                hud.getWorld().sendHoveredAction(act);
                break;
            case "body": {
                InventoryMetaItem body = Reflect.getBodyItem(hud.getPaperDollInventory());
                if (body != null) hud.sendAction(act, body.getId());
                break;
            }
            case "tile":
                hud.getWorld().sendLocalAction(act);
                break;
            case "tile_n": sendLocalAction(act, 0, -1); break;
            case "tile_s": sendLocalAction(act, 0, 1);  break;
            case "tile_e": sendLocalAction(act, 1, 0);  break;
            case "tile_w": sendLocalAction(act, -1, 0); break;
            case "tile_ne": sendLocalAction(act, 1, -1); break;
            case "tile_nw": sendLocalAction(act, -1, -1); break;
            case "tile_se": sendLocalAction(act, 1, 1); break;
            case "tile_sw": sendLocalAction(act, -1, 1); break;
            case "tool": {
                InventoryMetaItem t = Reflect.getActiveToolItem(hud);
                if (t != null) hud.sendAction(act, t.getId());
                else hud.consoleOutput("act: tool modifier requires an active tool selected");
                break;
            }
            case "selected": {
                PickableUnit p = Reflect.getSelectedUnit(hud.getSelectBar());
                if (p != null) hud.sendAction(act, p.getId());
                break;
            }
            case "area":
                sendAreaAction(act);
                break;
            case "toolbelt":
                if (id >= 1 && id <= 10) hud.setActiveTool(id - 1);
                else hud.consoleOutput("act: Invalid toolbelt slot '" + id + "'");
                break;
            default:
                if (target.startsWith("@tb")) {
                    int slot = Integer.parseInt(target.substring(3));
                    if (slot >= 1 && slot <= 10 && hud.getToolBelt().getItemInSlot(slot - 1) != null) {
                        hud.sendAction(act, hud.getToolBelt().getItemInSlot(slot - 1).getId());
                    } else {
                        hud.consoleOutput("act: Invalid toolbelt slot '" + slot + "'");
                    }
                } else if (target.startsWith("@eq")) {
                    byte slot = Byte.parseByte(target.substring(3));
                    PaperDollSlot obj = Reflect.getFrameFromSlotnumber(hud.getPaperDollInventory(), slot);
                    if (obj == null) {
                        hud.consoleOutput("act: Invalid equipment slot " + slot);
                    } else if (obj.getEquippedItem() == null) {
                        hud.consoleOutput("act: No item in equipment slot " + slot);
                    } else {
                        hud.sendAction(act, obj.getEquippedItem().getId());
                    }
                } else if (target.startsWith("@nearby")) {
                    float range = Float.parseFloat(target.substring(7));
                    final float rangeSq = range * range;
                    ServerConnectionListenerClass conn =
                            hud.getWorld().getServerConnection().getServerConnectionListener();
                    Collection<GroundItemCellRenderable> items = Reflect.getGroundItems(conn).values();
                    Collection<CreatureCellRenderable> creatures = conn.getCreatures().values();
                    Stream.concat(items.stream(), creatures.stream())
                            .filter(x -> x.getSquaredLengthFromPlayer() < rangeSq)
                            .mapToLong(CellRenderable::getId)
                            .forEach(tid -> hud.sendAction(act, tid));
                } else {
                    hud.consoleOutput("act: Invalid target keyword '" + target + "'");
                }
        }
    }

    private static void sendAreaAction(PlayerAction action) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                sendLocalAction(action, dx, dy);
            }
        }
    }

    private static void sendLocalAction(PlayerAction action, int xo, int yo) {
        int x = hud.getWorld().getPlayerCurrentTileX();
        int y = hud.getWorld().getPlayerCurrentTileY();
        hud.sendAction(action, Tiles.getTileId(x + xo, y + yo, 0));
    }

    /** Exposed so the macro-builder UI can check for readiness. */
    public static HeadsUpDisplay getHud() {
        return hud;
    }
}
