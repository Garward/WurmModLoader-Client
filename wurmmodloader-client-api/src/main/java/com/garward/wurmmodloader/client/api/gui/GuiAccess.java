package com.garward.wurmmodloader.client.api.gui;

import com.wurmonline.client.renderer.gui.WurmComponent;
import com.wurmonline.client.resources.textures.ResourceTexture;

import java.lang.reflect.Field;

/**
 * Reflection helpers for reading package-private {@link WurmComponent} state
 * that isn't exposed through widening patches. Centralized here so the
 * reflective lookups are cached once and mod classes don't each reinvent them.
 *
 * <p>If future widening patches expose these members directly, swap the field
 * reads for direct access — all call sites funnel through this class.
 */
final class GuiAccess {

    private static final Field WIDTH_FIELD;
    private static final Field HEIGHT_FIELD;
    private static final Field BACKGROUND_TEXTURE_2_FIELD;

    static {
        WIDTH_FIELD = lookupField("width");
        HEIGHT_FIELD = lookupField("height");
        BACKGROUND_TEXTURE_2_FIELD = lookupField("backgroundTexture2");
    }

    private GuiAccess() {}

    static int getWidth(WurmComponent c) {
        return readInt(WIDTH_FIELD, c);
    }

    static int getHeight(WurmComponent c) {
        return readInt(HEIGHT_FIELD, c);
    }

    static ResourceTexture getPanelBackgroundTexture() {
        if (BACKGROUND_TEXTURE_2_FIELD == null) return null;
        try {
            return (ResourceTexture) BACKGROUND_TEXTURE_2_FIELD.get(null);
        } catch (IllegalAccessException e) {
            return null;
        }
    }

    private static Field lookupField(String name) {
        try {
            Field f = WurmComponent.class.getDeclaredField(name);
            f.setAccessible(true);
            return f;
        } catch (NoSuchFieldException e) {
            return null;
        }
    }

    private static int readInt(Field f, WurmComponent c) {
        if (f == null) return 0;
        try {
            return f.getInt(c);
        } catch (IllegalAccessException e) {
            return 0;
        }
    }
}
