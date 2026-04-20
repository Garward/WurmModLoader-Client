package com.garward.wurmmodloader.client.core.bytecode;

import com.garward.wurmmodloader.client.api.bytecode.PatchRegistry;
import com.garward.wurmmodloader.client.core.bytecode.patches.ClientInitPatch;
import com.garward.wurmmodloader.client.core.bytecode.patches.ClientTickPatch;
import com.garward.wurmmodloader.client.core.bytecode.patches.FOVChangePatch;
import com.garward.wurmmodloader.client.core.bytecode.patches.HeadsUpDisplayInitPatch;
import com.garward.wurmmodloader.client.core.bytecode.patches.SimpleServerConnectionModCommPatch;
import com.garward.wurmmodloader.client.core.bytecode.patches.WorldMapTogglePatch;
import com.garward.wurmmodloader.client.core.bytecode.patches.gui.FlexComponentAccessPatch;
import com.garward.wurmmodloader.client.core.bytecode.patches.gui.GuiClassWideningPatch;
import com.garward.wurmmodloader.client.core.bytecode.patches.gui.WurmComponentAccessPatch;

import java.util.logging.Logger;

/**
 * Registers all core bytecode patches.
 *
 * <p>This class is responsible for registering the built-in patches that
 * enable the core modloader functionality. It should be called during
 * patcher startup before any classes are loaded.
 *
 * @since 0.1.0
 */
public class CorePatches {

    private static final Logger logger = Logger.getLogger(CorePatches.class.getName());
    private static boolean registered = false;

    /**
     * GUI classes that get their class modifier + all constructors widened to public.
     * Extra field names per entry are also widened. Both the runtime transformer and
     * the standalone JAR patcher iterate this list.
     */
    public static final GuiClassWideningPatch[] GUI_CLASS_WIDENINGS = new GuiClassWideningPatch[] {
        new GuiClassWideningPatch("com.wurmonline.client.renderer.gui.WWindow"),
        new GuiClassWideningPatch("com.wurmonline.client.renderer.gui.WButton"),
        new GuiClassWideningPatch("com.wurmonline.client.renderer.gui.WTextureButton"),
        new GuiClassWideningPatch("com.wurmonline.client.renderer.gui.WurmBorderPanel"),
        new GuiClassWideningPatch("com.wurmonline.client.renderer.gui.WurmArrayPanel"),
        new GuiClassWideningPatch("com.wurmonline.client.renderer.gui.ButtonListener"),
        new GuiClassWideningPatch("com.wurmonline.client.renderer.gui.ContainerComponent"),
        new GuiClassWideningPatch("com.wurmonline.client.renderer.gui.HeadsUpDisplay"),
    };

    /**
     * Registers all core patches with the PatchRegistry.
     *
     * <p>This method is idempotent - calling it multiple times has no effect.
     */
    public static void registerAll() {
        if (registered) {
            logger.warning("Core patches already registered");
            return;
        }

        logger.info("Registering core bytecode patches...");

        // Lifecycle patches
        PatchRegistry.register(new ClientInitPatch());
        PatchRegistry.register(new ClientTickPatch());

        // Client option patches
        PatchRegistry.register(new FOVChangePatch());

        // HUD / world map patches
        PatchRegistry.register(new HeadsUpDisplayInitPatch());
        PatchRegistry.register(new WorldMapTogglePatch());

        // ModComm — install client-side dispatch + banner-triggered handshake
        PatchRegistry.register(new SimpleServerConnectionModCommPatch());

        // GUI access widening — lets mods extend WurmComponent/FlexComponent from
        // their own packages without package-squatting or reflection.
        PatchRegistry.register(new WurmComponentAccessPatch());
        PatchRegistry.register(new FlexComponentAccessPatch());

        // Widen the rest of the GUI toolkit so mods can extend widgets directly.
        // Add new classes here when a mod hits "not public in com.wurmonline.client.renderer.gui".
        for (GuiClassWideningPatch p : GUI_CLASS_WIDENINGS) {
            PatchRegistry.register(p);
        }

        // TODO: Add more patches as they are implemented
        // PatchRegistry.register(new ClientWorldLoadedPatch());
        // PatchRegistry.register(new ClientInputPatch());
        // PatchRegistry.register(new EntityUpdatePatch());

        registered = true;
        logger.info("Registered " + PatchRegistry.getPatchCount() + " core patches");
        logger.info("Target classes: " + PatchRegistry.getAllTargetClasses());
    }

    /**
     * Returns whether core patches have been registered.
     *
     * @return true if patches are registered
     */
    public static boolean isRegistered() {
        return registered;
    }
}
