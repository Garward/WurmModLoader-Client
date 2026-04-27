package com.garward.wurmmodloader.client.api.events.eventlogic.action;

import com.wurmonline.client.comm.ServerConnectionListenerClass;
import com.wurmonline.client.game.inventory.InventoryMetaItem;
import com.wurmonline.client.renderer.PickableUnit;
import com.wurmonline.client.renderer.cell.GroundItemCellRenderable;
import com.wurmonline.client.renderer.gui.HeadsUpDisplay;
import com.wurmonline.client.renderer.gui.PaperDollInventory;
import com.wurmonline.client.renderer.gui.PaperDollSlot;
import com.wurmonline.client.renderer.gui.SelectBar;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;

/**
 * Framework-public reflection cache for the handful of private Wurm-client
 * members the action-dispatch layer needs. One-time
 * {@link #setup()} at HUD-init time; subsequent calls are cheap field/method
 * accesses.
 *
 * <p>This is the canonical extraction of the original {@code Reflect}
 * helper that shipped inside the {@code action} mod. Mods that need any of
 * these accessors (active toolbelt item, paperdoll body slot, ground items
 * map, select-bar selected unit, etc.) should depend on this class rather
 * than copying their own reflection helpers — keeping a single source of
 * truth means a future Wurm-client field rename only needs to be patched
 * here.
 *
 * @since 0.4.0
 */
public final class ClientItemReflect {

    private static Field fldBodyItem;              // PaperDollInventory.bodyItem
    private static Method mGetFrameFromSlotnumber; // PaperDollInventory.getFrameFromSlotnumber(byte)
    private static Field fldActiveToolItem;        // HeadsUpDisplay.activeToolItem
    private static Field fldSelectedUnit;          // SelectBar.selectedUnit
    private static Field fldGroundItems;           // ServerConnectionListenerClass.groundItems

    private ClientItemReflect() {}

    public static void setup() throws ReflectiveOperationException {
        fldBodyItem = accessible(PaperDollInventory.class.getDeclaredField("bodyItem"));
        fldActiveToolItem = accessible(HeadsUpDisplay.class.getDeclaredField("activeToolItem"));
        fldSelectedUnit = accessible(SelectBar.class.getDeclaredField("selectedUnit"));
        mGetFrameFromSlotnumber = PaperDollInventory.class.getDeclaredMethod(
                "getFrameFromSlotnumber", byte.class);
        mGetFrameFromSlotnumber.setAccessible(true);
        fldGroundItems = accessible(ServerConnectionListenerClass.class.getDeclaredField("groundItems"));
    }

    public static InventoryMetaItem getBodyItem(PaperDollInventory pd) throws ReflectiveOperationException {
        PaperDollSlot slot = (PaperDollSlot) fldBodyItem.get(pd);
        return slot == null ? null : slot.getItem();
    }

    public static InventoryMetaItem getActiveToolItem(HeadsUpDisplay hud) throws ReflectiveOperationException {
        return (InventoryMetaItem) fldActiveToolItem.get(hud);
    }

    public static PickableUnit getSelectedUnit(SelectBar s) throws ReflectiveOperationException {
        return (PickableUnit) fldSelectedUnit.get(s);
    }

    public static PaperDollSlot getFrameFromSlotnumber(PaperDollInventory pd, byte slot)
            throws ReflectiveOperationException {
        return (PaperDollSlot) mGetFrameFromSlotnumber.invoke(pd, slot);
    }

    @SuppressWarnings("unchecked")
    public static Map<Long, GroundItemCellRenderable> getGroundItems(ServerConnectionListenerClass conn)
            throws ReflectiveOperationException {
        return (Map<Long, GroundItemCellRenderable>) fldGroundItems.get(conn);
    }

    private static Field accessible(Field f) {
        f.setAccessible(true);
        return f;
    }
}
