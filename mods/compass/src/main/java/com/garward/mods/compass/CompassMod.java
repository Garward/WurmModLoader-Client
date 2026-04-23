package com.garward.mods.compass;

import java.lang.reflect.Field;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.garward.wurmmodloader.client.api.events.base.SubscribeEvent;
import com.garward.wurmmodloader.client.api.events.gui.CompassComponentPickEvent;
import com.garward.wurmmodloader.client.api.events.gui.CompassComponentTickEvent;

import com.wurmonline.client.game.World;
import com.wurmonline.client.renderer.PickData;

/**
 * Port of bdew's compass mod. Forces the compass to always display at
 * quality 99 with full opacity, and appends angle/position/height to the
 * hover tooltip.
 *
 * <p>Original implementation used {@code HookManager} + reflection; this
 * port rides the framework's {@code CompassComponentTick/PickEvent} and
 * accesses fields directly (class/fields widened to public by
 * {@code CorePatches.GUI_CLASS_WIDENINGS}).
 */
public class CompassMod {

    private static final Logger logger = Logger.getLogger("CompassMod");

    @SubscribeEvent
    public void onCompassTick(CompassComponentTickEvent event) {
        try {
            Object c = event.getComponent();
            setInt(c, "ql", 99);
            setBool(c, "isMoving", false);
            setFloat(c, "fadeAlpha", 1.0f);
        } catch (Throwable t) {
            logger.log(Level.FINE, "[CompassMod] tick override soft-fail", t);
        }
    }

    @SubscribeEvent
    public void onCompassPick(CompassComponentPickEvent event) {
        try {
            Object c = event.getComponent();
            PickData pickData = (PickData) event.getPickData();

            float actualAngle = getFloat(c, "actualAngle");
            World world = getWorld(c);

            float prettyAngle = actualAngle % 360.0f;
            if (prettyAngle < 0.0f) {
                prettyAngle += 360.0f;
            }

            pickData.addText("Angle: " + prettyAngle);
            if (world != null) {
                pickData.addText(String.format("Position: %.1f / %.1f",
                        world.getPlayer().getPos().getX() / 4f,
                        world.getPlayer().getPos().getY() / 4f));
                pickData.addText(String.format("Height: %.1f",
                        world.getPlayer().getPos().getH() * 10f));
            }
        } catch (Throwable t) {
            logger.log(Level.FINE, "[CompassMod] pick override soft-fail", t);
        }
    }

    private static Field field(Class<?> cls, String name) throws NoSuchFieldException {
        Field f = cls.getDeclaredField(name);
        f.setAccessible(true);
        return f;
    }

    private static void setInt(Object o, String name, int v) throws Exception {
        field(o.getClass(), name).setInt(o, v);
    }

    private static void setBool(Object o, String name, boolean v) throws Exception {
        field(o.getClass(), name).setBoolean(o, v);
    }

    private static void setFloat(Object o, String name, float v) throws Exception {
        field(o.getClass(), name).setFloat(o, v);
    }

    private static float getFloat(Object o, String name) throws Exception {
        return field(o.getClass(), name).getFloat(o);
    }

    private static World getWorld(Object o) throws Exception {
        return (World) field(o.getClass(), "world").get(o);
    }
}
