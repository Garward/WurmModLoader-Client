package com.garward.wurmmodloader.client.api.events.eventlogic.action;

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

import java.util.Collection;
import java.util.stream.Stream;

/**
 * Reusable dispatcher for {@link PlayerAction} against the standard target
 * keywords. Lifted from the action mod so any client mod can invoke the
 * same dispatch path without an inter-mod dependency.
 *
 * <p>Target keywords (all lowercase):
 * <ul>
 *   <li>{@code hover} — currently hovered pickable unit.</li>
 *   <li>{@code body} — the player's body item (paper-doll slot).</li>
 *   <li>{@code tile} — the tile under the player.</li>
 *   <li>{@code tile_n / s / e / w / ne / nw / se / sw} — neighbouring tile.</li>
 *   <li>{@code tool} — the currently active toolbelt item.</li>
 *   <li>{@code selected} — the active select-bar unit.</li>
 *   <li>{@code inventory_selection} — every item blue-highlighted in the inventory.</li>
 *   <li>{@code area} — every tile in a 3x3 around the player.</li>
 *   <li>{@code toolbelt} — switch active tool slot to id (1-10).</li>
 *   <li>{@code @tbN} — fire on the item in toolbelt slot N (1-10).</li>
 *   <li>{@code @eqN} — fire on the equipped item in paper-doll slot N.</li>
 *   <li>{@code @nearbyR} — fire on every ground item / creature within R metres.</li>
 * </ul>
 *
 * <p>Always call {@link ClientItemReflect#setup()} once after the HUD has
 * initialized before invoking {@link #dispatch}.
 *
 * @since 0.4.0
 */
public final class PlayerActionDispatcher {

    private PlayerActionDispatcher() {}

    /**
     * Send {@code actionId} against {@code target}. Writes a console message
     * to the HUD on bad target / slot. Throws on reflection failure (mods
     * should log and stop).
     */
    public static void dispatch(HeadsUpDisplay hud, short actionId, String target)
            throws ReflectiveOperationException {
        if (hud == null) return;
        PlayerAction act = new PlayerAction(actionId, PlayerAction.ANYTHING, "", false);
        switch (target) {
            case "hover":
                hud.getWorld().sendHoveredAction(act);
                break;
            case "body": {
                InventoryMetaItem body = ClientItemReflect.getBodyItem(hud.getPaperDollInventory());
                if (body != null) hud.sendAction(act, body.getId());
                break;
            }
            case "tile":
                hud.getWorld().sendLocalAction(act);
                break;
            case "tile_n": sendLocal(hud, act, 0, -1); break;
            case "tile_s": sendLocal(hud, act, 0, 1);  break;
            case "tile_e": sendLocal(hud, act, 1, 0);  break;
            case "tile_w": sendLocal(hud, act, -1, 0); break;
            case "tile_ne": sendLocal(hud, act, 1, -1); break;
            case "tile_nw": sendLocal(hud, act, -1, -1); break;
            case "tile_se": sendLocal(hud, act, 1, 1); break;
            case "tile_sw": sendLocal(hud, act, -1, 1); break;
            case "tool": {
                InventoryMetaItem t = ClientItemReflect.getActiveToolItem(hud);
                if (t != null) hud.sendAction(act, t.getId());
                else hud.consoleOutput("dispatch: tool target requires an active tool selected");
                break;
            }
            case "selected": {
                PickableUnit p = ClientItemReflect.getSelectedUnit(hud.getSelectBar());
                if (p != null) hud.sendAction(act, p.getId());
                break;
            }
            case "inventory_selection": {
                long[] ids = ClientItemReflect.getSelectedInventoryItemIds(hud);
                if (ids.length == 0) {
                    hud.consoleOutput("dispatch: nothing selected in inventory");
                } else {
                    for (long id : ids) hud.sendAction(act, id);
                }
                break;
            }
            case "area":
                for (int dx = -1; dx <= 1; dx++)
                    for (int dy = -1; dy <= 1; dy++)
                        sendLocal(hud, act, dx, dy);
                break;
            case "toolbelt":
                if (actionId >= 1 && actionId <= 10) hud.setActiveTool(actionId - 1);
                else hud.consoleOutput("dispatch: invalid toolbelt slot '" + actionId + "'");
                break;
            default:
                if (target.startsWith("@tb")) {
                    int slot = Integer.parseInt(target.substring(3));
                    if (slot >= 1 && slot <= 10 && hud.getToolBelt().getItemInSlot(slot - 1) != null) {
                        hud.sendAction(act, hud.getToolBelt().getItemInSlot(slot - 1).getId());
                    } else {
                        hud.consoleOutput("dispatch: invalid toolbelt slot '" + slot + "'");
                    }
                } else if (target.startsWith("@eq")) {
                    byte slot = Byte.parseByte(target.substring(3));
                    PaperDollSlot obj = ClientItemReflect.getFrameFromSlotnumber(hud.getPaperDollInventory(), slot);
                    if (obj == null) {
                        hud.consoleOutput("dispatch: invalid equipment slot " + slot);
                    } else if (obj.getEquippedItem() == null) {
                        hud.consoleOutput("dispatch: no item in equipment slot " + slot);
                    } else {
                        hud.sendAction(act, obj.getEquippedItem().getId());
                    }
                } else if (target.startsWith("@nearby")) {
                    float range = Float.parseFloat(target.substring(7));
                    final float rangeSq = range * range;
                    ServerConnectionListenerClass conn =
                            hud.getWorld().getServerConnection().getServerConnectionListener();
                    Collection<GroundItemCellRenderable> items = ClientItemReflect.getGroundItems(conn).values();
                    Collection<CreatureCellRenderable> creatures = conn.getCreatures().values();
                    Stream.concat(items.stream(), creatures.stream())
                            .filter(x -> x.getSquaredLengthFromPlayer() < rangeSq)
                            .mapToLong(CellRenderable::getId)
                            .forEach(tid -> hud.sendAction(act, tid));
                } else {
                    hud.consoleOutput("dispatch: invalid target keyword '" + target + "'");
                }
        }
    }

    private static void sendLocal(HeadsUpDisplay hud, PlayerAction action, int xo, int yo) {
        int x = hud.getWorld().getPlayerCurrentTileX();
        int y = hud.getWorld().getPlayerCurrentTileY();
        hud.sendAction(action, Tiles.getTileId(x + xo, y + yo, 0));
    }
}
