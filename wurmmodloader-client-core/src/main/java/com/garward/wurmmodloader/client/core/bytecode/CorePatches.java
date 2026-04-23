package com.garward.wurmmodloader.client.core.bytecode;

import com.garward.wurmmodloader.client.api.bytecode.PatchRegistry;
import com.garward.wurmmodloader.client.core.bytecode.patches.ClientInitPatch;
import com.garward.wurmmodloader.client.core.bytecode.patches.ClientTickPatch;
import com.garward.wurmmodloader.client.core.bytecode.patches.FOVChangePatch;
import com.garward.wurmmodloader.client.core.bytecode.patches.HeadsUpDisplayInitPatch;
import com.garward.wurmmodloader.client.core.bytecode.patches.CellRenderableLifecyclePatch;
import com.garward.wurmmodloader.client.core.bytecode.patches.CompassComponentPatch;
import com.garward.wurmmodloader.client.core.bytecode.patches.ConsoleInputPatch;
import com.garward.wurmmodloader.client.core.bytecode.patches.DeedPlanPacketPatch;
import com.garward.wurmmodloader.client.core.bytecode.patches.PickRenderPatch;
import com.garward.wurmmodloader.client.core.bytecode.patches.IsDevOverridePatch;
import com.garward.wurmmodloader.client.core.bytecode.patches.PlayerActionNamePatch;
import com.garward.wurmmodloader.client.core.bytecode.patches.WurmPopupRebindPatch;
import com.garward.wurmmodloader.client.core.bytecode.patches.SimpleServerConnectionModCommPatch;
import com.garward.wurmmodloader.client.core.bytecode.patches.WorldMapTogglePatch;
import com.garward.wurmmodloader.client.core.bytecode.patches.WorldRenderPatch;
import com.garward.wurmmodloader.client.core.bytecode.patches.gui.FlexComponentAccessPatch;
import com.garward.wurmmodloader.client.core.bytecode.patches.gui.GuiClassWideningPatch;
import com.garward.wurmmodloader.client.core.bytecode.patches.gui.WurmComponentAccessPatch;
import com.garward.wurmmodloader.client.core.bytecode.patches.serverpacks.PackGetResourceCrossPackPatch;
import com.garward.wurmmodloader.client.core.bytecode.patches.serverpacks.PackInitVirtualPacksPatch;
import com.garward.wurmmodloader.client.core.bytecode.patches.serverpacks.PackResourceUrlDeriveCrossPackPatch;
import com.garward.wurmmodloader.client.core.bytecode.patches.serverpacks.PackResourceUrlRawFilePathPatch;
import com.garward.wurmmodloader.client.core.bytecode.patches.serverpacks.ResourcesFindPackPatch;

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
     * Classes whose package-private class modifier + members get widened to public.
     * Despite the name, this list is not GUI-only — any {@code com.wurmonline.client.*}
     * class that mods need to subclass or call across packages belongs here. The
     * widening patch is a no-op on members that are already public, so adding
     * already-public classes is safe. Both the runtime transformer and the
     * standalone JAR patcher iterate this list.
     */
    public static final GuiClassWideningPatch[] GUI_CLASS_WIDENINGS = new GuiClassWideningPatch[] {
        // GUI toolkit — base widgets. WurmComponent/FlexComponent expose pkg-private
        // fields (text, width, height, etc.) that subclasses in mod classloaders
        // can't reach without widening — same package name, different runtime pkg.
        new GuiClassWideningPatch("com.wurmonline.client.renderer.gui.WurmComponent"),
        new GuiClassWideningPatch("com.wurmonline.client.renderer.gui.FlexComponent"),
        new GuiClassWideningPatch("com.wurmonline.client.renderer.gui.WWindow"),
        new GuiClassWideningPatch("com.wurmonline.client.renderer.gui.WButton"),
        new GuiClassWideningPatch("com.wurmonline.client.renderer.gui.WTextureButton"),
        new GuiClassWideningPatch("com.wurmonline.client.renderer.gui.WurmBorderPanel"),
        new GuiClassWideningPatch("com.wurmonline.client.renderer.gui.WurmArrayPanel"),
        new GuiClassWideningPatch("com.wurmonline.client.renderer.gui.ButtonListener"),
        new GuiClassWideningPatch("com.wurmonline.client.renderer.gui.ContainerComponent"),
        new GuiClassWideningPatch("com.wurmonline.client.renderer.gui.HeadsUpDisplay"),
        new GuiClassWideningPatch("com.wurmonline.client.renderer.gui.MainMenu"),
        new GuiClassWideningPatch("com.wurmonline.client.renderer.gui.CompassComponent"),
        new GuiClassWideningPatch("com.wurmonline.client.renderer.PickData"),

        // World / player state — needed by ESP, minimap, livemap, prediction mods.
        new GuiClassWideningPatch("com.wurmonline.client.game.World"),
        new GuiClassWideningPatch("com.wurmonline.client.game.PlayerPosition"),
        new GuiClassWideningPatch("com.wurmonline.client.game.CaveDataBuffer"),
        new GuiClassWideningPatch("com.wurmonline.client.game.NearTerrainDataBuffer"),

        // Render pipeline — PickRenderer + backend Queue are the draw seams.
        new GuiClassWideningPatch("com.wurmonline.client.renderer.PickRenderer"),
        new GuiClassWideningPatch("com.wurmonline.client.renderer.PickableUnit"),
        new GuiClassWideningPatch("com.wurmonline.client.renderer.GroundItemData"),
        new GuiClassWideningPatch("com.wurmonline.client.renderer.backend.Queue"),

        // Cell renderables — per-entity render nodes that overlays track.
        new GuiClassWideningPatch("com.wurmonline.client.renderer.cell.CellRenderable"),
        new GuiClassWideningPatch("com.wurmonline.client.renderer.cell.MobileModelRenderable"),
        new GuiClassWideningPatch("com.wurmonline.client.renderer.cell.CreatureCellRenderable"),
        new GuiClassWideningPatch("com.wurmonline.client.renderer.cell.GroundItemCellRenderable"),
        new GuiClassWideningPatch("com.wurmonline.client.renderer.cell.PlayerCellRenderable"),

        // World render — visible pass; mods reference renderPickedItem indirectly.
        new GuiClassWideningPatch("com.wurmonline.client.renderer.WorldRender"),

        // Settings + sound — used by camera/position mods and audio-alert mods.
        new GuiClassWideningPatch("com.wurmonline.client.settings.SavePosManager"),
        new GuiClassWideningPatch("com.wurmonline.client.sound.SoundSource"),
        new GuiClassWideningPatch("com.wurmonline.client.sound.FixedSoundSource"),

        // Shared mesh types (used by both client and server) — tile constants, etc.
        new GuiClassWideningPatch("com.wurmonline.mesh.Tiles"),
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

        // Render pipeline — pre/post seam for overlay mods (ESP, waypoints, highlights)
        PatchRegistry.register(new PickRenderPatch());
        PatchRegistry.register(new WorldRenderPatch());

        // Cell renderable lifecycle — push-based tracking for ESP/waypoint mods
        PatchRegistry.register(new CellRenderableLifecyclePatch(
            "com.wurmonline.client.renderer.cell.MobileModelRenderable"));
        PatchRegistry.register(new CellRenderableLifecyclePatch(
            "com.wurmonline.client.renderer.cell.GroundItemCellRenderable"));
        PatchRegistry.register(new CellRenderableLifecyclePatch(
            "com.wurmonline.client.renderer.cell.CellRenderable"));

        // Console + deed-plan packet — runtime toggles and deed-size overlays
        PatchRegistry.register(new ConsoleInputPatch());
        PatchRegistry.register(new DeedPlanPacketPatch());

        // PlayerAction.getName — mods can rewrite menu labels (action-id reveal, renames)
        PatchRegistry.register(new PlayerActionNamePatch());

        // isDev() override — unlock dev-gated UI (quick keybind dialogs, toggleKey, …)
        PatchRegistry.register(new IsDevOverridePatch());

        // WurmPopup.rebindPrimary — cancellable hook for hold-key quick-bind
        PatchRegistry.register(new WurmPopupRebindPatch());

        // Compass widget — tick/pick events for always-on / hover-text mods
        PatchRegistry.register(new CompassComponentPatch());

        // ModComm — install client-side dispatch + banner-triggered handshake
        PatchRegistry.register(new SimpleServerConnectionModCommPatch());

        // GUI access widening — lets mods extend WurmComponent/FlexComponent from
        // their own packages without package-squatting or reflection.
        PatchRegistry.register(new WurmComponentAccessPatch());
        PatchRegistry.register(new FlexComponentAccessPatch());

        // Server packs — cross-pack resolution (~packname/path), findPack(), virtual
        // packs, rawFilePath widening. Without these, packs that reference textures
        // from other packs (e.g. bdew's farmbox → ~graphics.jar/...) render as
        // fallbacks with missing materials.
        PatchRegistry.register(new ResourcesFindPackPatch());
        PatchRegistry.register(new PackResourceUrlRawFilePathPatch());
        PatchRegistry.register(new PackInitVirtualPacksPatch());
        PatchRegistry.register(new PackGetResourceCrossPackPatch());
        PatchRegistry.register(new PackResourceUrlDeriveCrossPackPatch());

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
