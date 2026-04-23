package com.garward.wurmmodloader.mods.action;

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
 * Reflection cache for the handful of private members the action mod needs.
 * One-time setup at HUD-init time; subsequent calls are cheap field/method
 * accesses.
 */
final class Reflect {

    private static Field fldBodyItem;              // PaperDollInventory.bodyItem
    private static Method mGetFrameFromSlotnumber; // PaperDollInventory.getFrameFromSlotnumber(byte)
    private static Field fldActiveToolItem;        // HeadsUpDisplay.activeToolItem
    private static Field fldSelectedUnit;          // SelectBar.selectedUnit
    private static Field fldGroundItems;           // ServerConnectionListenerClass.groundItems

    private Reflect() {}

    static void setup() throws ReflectiveOperationException {
        fldBodyItem = accessible(PaperDollInventory.class.getDeclaredField("bodyItem"));
        fldActiveToolItem = accessible(HeadsUpDisplay.class.getDeclaredField("activeToolItem"));
        fldSelectedUnit = accessible(SelectBar.class.getDeclaredField("selectedUnit"));
        mGetFrameFromSlotnumber = PaperDollInventory.class.getDeclaredMethod(
                "getFrameFromSlotnumber", byte.class);
        mGetFrameFromSlotnumber.setAccessible(true);
        fldGroundItems = accessible(ServerConnectionListenerClass.class.getDeclaredField("groundItems"));
    }

    static InventoryMetaItem getBodyItem(PaperDollInventory pd) throws ReflectiveOperationException {
        PaperDollSlot slot = (PaperDollSlot) fldBodyItem.get(pd);
        return slot == null ? null : slot.getItem();
    }

    static InventoryMetaItem getActiveToolItem(HeadsUpDisplay hud) throws ReflectiveOperationException {
        return (InventoryMetaItem) fldActiveToolItem.get(hud);
    }

    static PickableUnit getSelectedUnit(SelectBar s) throws ReflectiveOperationException {
        return (PickableUnit) fldSelectedUnit.get(s);
    }

    static PaperDollSlot getFrameFromSlotnumber(PaperDollInventory pd, byte slot)
            throws ReflectiveOperationException {
        return (PaperDollSlot) mGetFrameFromSlotnumber.invoke(pd, slot);
    }

    @SuppressWarnings("unchecked")
    static Map<Long, GroundItemCellRenderable> getGroundItems(ServerConnectionListenerClass conn)
            throws ReflectiveOperationException {
        return (Map<Long, GroundItemCellRenderable>) fldGroundItems.get(conn);
    }

    private static Field accessible(Field f) {
        f.setAccessible(true);
        return f;
    }
}
