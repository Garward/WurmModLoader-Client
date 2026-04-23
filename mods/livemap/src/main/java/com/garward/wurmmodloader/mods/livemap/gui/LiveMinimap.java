package com.garward.wurmmodloader.mods.livemap.gui;

import com.garward.wurmmodloader.mods.livemap.LiveMapClientMod;
import com.garward.wurmmodloader.mods.livemap.MapDataCache;
import com.garward.wurmmodloader.mods.livemap.renderer.TileRenderer;
import com.garward.wurmmodloader.mods.livemap.renderer.local.LiveMap;
import com.garward.wurmmodloader.client.api.gui.ArrayDirection;
import com.garward.wurmmodloader.client.api.gui.BorderRegion;
import com.garward.wurmmodloader.client.api.gui.Insets;
import com.garward.wurmmodloader.client.api.gui.ModBorderPanel;
import com.garward.wurmmodloader.client.api.gui.ModComponent;
import com.garward.wurmmodloader.client.api.gui.ModImageButton;
import com.garward.wurmmodloader.client.api.gui.ModStackPanel;
import com.garward.wurmmodloader.client.api.gui.ModWindow;
import com.wurmonline.client.game.PlayerPosition;
import com.wurmonline.client.game.World;
import com.wurmonline.client.renderer.backend.Queue;

import java.util.logging.Logger;

/**
 * Always-on minimap window.
 *
 * <p>Two render modes, toggled by the [M] button:
 * <ul>
 *   <li>{@link Mode#SERVER_HTTP} — tiles + overlays from the server's live-map
 *       HTTP endpoint. Authoritative waypoints, villages, altars, other players.</li>
 *   <li>{@link Mode#CLIENT_LOCAL} — Ago's client-side terrain render. Works on
 *       unmodded servers and as a fallback when HTTP is unreachable.</li>
 * </ul>
 */
public class LiveMinimap extends ModWindow {

    private static final Logger logger = Logger.getLogger(LiveMinimap.class.getName());

    private static final int MAP_SIZE = 220;
    private static final int BUTTON_PANEL_WIDTH = 40;
    private static final int WINDOW_WIDTH = MAP_SIZE + BUTTON_PANEL_WIDTH;
    private static final int WINDOW_HEIGHT = MAP_SIZE;

    public enum Mode { SERVER_HTTP, CLIENT_LOCAL }

    private final MinimapView view;

    public LiveMinimap(World world, LiveMapClientMod mod, MapDataCache cache) {
        super("Minimap");
        lockSize();

        ModBorderPanel main = new ModBorderPanel("Minimap");
        view = new MinimapView(world, mod, cache);

        ModStackPanel sidebar = new ModStackPanel("Minimap Sidebar", ArrayDirection.VERTICAL)
                .setBackgroundPainted(true)
                .setPadding(Insets.uniform(2))
                .setGap(3);
        sidebar.setInitialSize(BUTTON_PANEL_WIDTH, MAP_SIZE, false);

        Class<?> anchor = LiveMapClientMod.class;
        sidebar.addChild(new ModImageButton(anchor, "/com/garward/mods/livemap/icons/zoom_in.png",
                "Zoom in", view::zoomIn));
        sidebar.addChild(new ModImageButton(anchor, "/com/garward/mods/livemap/icons/zoom_out.png",
                "Zoom out", view::zoomOut));
        sidebar.addChild(new ModImageButton(anchor, "/com/garward/mods/livemap/icons/toggle_mode.png",
                "Toggle server/local map", view::toggleMode));

        main.setRegion(view, BorderRegion.CENTER);
        main.setRegion(sidebar, BorderRegion.EAST);

        installContent(main, WINDOW_WIDTH, WINDOW_HEIGHT + 25);

        logger.info("[LiveMap] Minimap window created: " + WINDOW_WIDTH + "x" + WINDOW_HEIGHT);
    }

    public void positionInCorner(int screenWidth, int screenHeight) {
        int padding = 10;
        setPosition(screenWidth - WINDOW_WIDTH - padding, padding);
    }

    public TileRenderer getRenderer() {
        return view.renderer;
    }

    public Mode getMode() {
        return view.mode;
    }

    public void setMode(Mode m) {
        view.setMode(m);
    }

    public void clear() {
        view.renderer.clearTextures();
    }

    private static class MinimapView extends ModComponent {

        private final World world;
        private final TileRenderer renderer;
        private Mode mode = Mode.SERVER_HTTP;
        private LiveMap localMap;
        private int currentZoom = 3;

        MinimapView(World world, LiveMapClientMod mod, MapDataCache cache) {
            super("Minimap View", MAP_SIZE, MAP_SIZE);
            this.world = world;
            this.renderer = new TileRenderer(cache);
            this.renderer.setTileRequestCallback((z, tx, ty) -> mod.requestTile(z, tx, ty));
        }

        @Override
        protected void onRender(Queue queue, float alpha) {
            PlayerPosition pos = world.getPlayer().getPos();
            if (mode == Mode.SERVER_HTTP) {
                renderer.render(queue, getScreenX(), getScreenY(), MAP_SIZE, MAP_SIZE,
                        pos.getTileX(), pos.getTileY(), currentZoom);
                // Minimap is always centered on the player, so the dot sits at
                // the view center — matches the vanilla local-map indicator.
                renderer.drawPlayerDot(queue,
                        getScreenX() + MAP_SIZE / 2f, getScreenY() + MAP_SIZE / 2f);
            } else {
                if (localMap == null) {
                    localMap = new LiveMap(world, MAP_SIZE);
                }
                localMap.update(getScreenX(), getScreenY());
                localMap.render(queue, 0.0F, 0.0F, 1.0F);
            }
        }

        @Override
        protected void onMouseWheel(int xMouse, int yMouse, int delta) {
            if (delta < 0) zoomIn();
            else if (delta > 0) zoomOut();
        }

        void zoomIn()  { if (currentZoom < renderer.getMaxZoom()) currentZoom++; }
        void zoomOut() { if (currentZoom > renderer.getMinZoom()) currentZoom--; }

        void toggleMode() {
            mode = (mode == Mode.SERVER_HTTP) ? Mode.CLIENT_LOCAL : Mode.SERVER_HTTP;
            logger.info("[LiveMap] Minimap mode -> " + mode);
        }

        void setMode(Mode m) {
            if (mode != m) {
                mode = m;
                logger.info("[LiveMap] Minimap mode -> " + mode);
            }
        }
    }
}
