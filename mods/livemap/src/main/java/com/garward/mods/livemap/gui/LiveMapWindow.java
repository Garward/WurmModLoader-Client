package com.garward.mods.livemap.gui;

import com.garward.mods.livemap.LiveMapClientMod;
import com.garward.mods.livemap.MapDataCache;
import com.garward.mods.livemap.renderer.TileRenderer;
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
import com.wurmonline.client.renderer.PickData;
import com.wurmonline.client.renderer.backend.Queue;

import java.util.logging.Logger;

/**
 * Full-screen live map window (replaces vanilla WorldMap).
 */
public class LiveMapWindow extends ModWindow {

    private static final Logger logger = Logger.getLogger(LiveMapWindow.class.getName());

    private static final int MAP_VIEW_WIDTH = 880;
    private static final int MAP_VIEW_HEIGHT = 620;
    private static final int BUTTON_PANEL_WIDTH = 48;
    private static final int WINDOW_WIDTH = MAP_VIEW_WIDTH + BUTTON_PANEL_WIDTH;
    private static final int WINDOW_HEIGHT = MAP_VIEW_HEIGHT;

    private final LiveMapView mapView;

    public TileRenderer getRenderer() {
        return mapView.renderer;
    }

    public LiveMapWindow(World world, LiveMapClientMod mod, MapDataCache cache) {
        super("Live Map");
        lockSize();

        ModBorderPanel mainPanel = new ModBorderPanel("Live Map");
        mapView = new LiveMapView(world, mod, cache);
        ModStackPanel sidebar = createSidebar();
        sidebar.setInitialSize(BUTTON_PANEL_WIDTH, MAP_VIEW_HEIGHT, false);

        mainPanel.setRegion(mapView, BorderRegion.CENTER);
        mainPanel.setRegion(sidebar, BorderRegion.EAST);

        installContent(mainPanel, WINDOW_WIDTH, WINDOW_HEIGHT + 25);

        logger.info("[LiveMap] Full map window created: " + WINDOW_WIDTH + "x" + WINDOW_HEIGHT);
    }

    private ModStackPanel createSidebar() {
        ModStackPanel sidebar = new ModStackPanel("Live Map Sidebar", ArrayDirection.VERTICAL)
                .setBackgroundPainted(true)
                .setPadding(Insets.uniform(4))
                .setGap(4);

        Class<?> anchor = LiveMapClientMod.class;
        sidebar.addChild(new ModImageButton(anchor, "/com/garward/mods/livemap/icons/zoom_in.png",
                "Zoom in", mapView::zoomIn, 0));
        sidebar.addChild(new ModImageButton(anchor, "/com/garward/mods/livemap/icons/zoom_out.png",
                "Zoom out", mapView::zoomOut, 0));
        sidebar.addChild(new ModImageButton(anchor, "/com/garward/mods/livemap/icons/center.png",
                "Center on player", mapView::centerOnPlayer, 0));
        // TODO: Layer toggle buttons (players, villages, altars)

        return sidebar;
    }

    @Override
    public void pick(PickData pickData, int xMouse, int yMouse) {
        if (mapView.contains(xMouse, yMouse)) {
            // TODO: Handle clicks on map (waypoints, etc.)
        }
    }

    /** Inner pan/zoom map view. */
    private static class LiveMapView extends ModComponent {

        private final World world;
        private final TileRenderer renderer;

        private int currentZoom = 0;
        private float viewCenterX;
        private float viewCenterY;

        private boolean isDragging = false;
        private int dragAnchorMouseX;
        private int dragAnchorMouseY;
        private float dragAnchorWorldX;
        private float dragAnchorWorldY;

        LiveMapView(World world, LiveMapClientMod mod, MapDataCache cache) {
            super("Live Map View", MAP_VIEW_WIDTH, MAP_VIEW_HEIGHT);

            this.world = world;
            this.renderer = new TileRenderer(cache);

            this.renderer.setTileRequestCallback((zoom, tileX, tileY) -> {
                mod.requestTile(zoom, tileX, tileY);
            });

            centerOnPlayer();
        }

        @Override
        protected void onRender(Queue queue, float alpha) {
            renderer.render(queue, getScreenX(), getScreenY(), MAP_VIEW_WIDTH, MAP_VIEW_HEIGHT,
                    viewCenterX, viewCenterY, currentZoom);
            // TODO: Render overlays (players, villages, waypoints)
        }

        @Override
        protected void onLeftPressed(int xMouse, int yMouse, int clickCount) {
            // Only anchor on a fresh press. Defensive — if Wurm re-fires
            // leftPressed mid-drag for any reason, we don't want to reset the
            // anchor and snap the view.
            if (!isDragging) {
                dragAnchorMouseX = xMouse;
                dragAnchorMouseY = yMouse;
                dragAnchorWorldX = viewCenterX;
                dragAnchorWorldY = viewCenterY;
                isDragging = true;
                logger.fine("[LiveMap] drag start mouse=(" + xMouse + "," + yMouse
                        + ") world=(" + viewCenterX + "," + viewCenterY + ")");
            }
        }

        @Override
        protected void onLeftReleased(int xMouse, int yMouse) {
            isDragging = false;
        }

        @Override
        protected void onMouseDragged(int xMouse, int yMouse) {
            if (!isDragging) return;

            float worldScale = getWorldTilesPerPixel();
            float mapMax = renderer.getMapSize();

            viewCenterX = Math.max(0, Math.min(mapMax,
                    dragAnchorWorldX - (xMouse - dragAnchorMouseX) * worldScale));
            viewCenterY = Math.max(0, Math.min(mapMax,
                    dragAnchorWorldY - (yMouse - dragAnchorMouseY) * worldScale));
        }

        @Override
        protected void onMouseWheel(int xMouse, int yMouse, int delta) {
            if (delta < 0) {
                zoomIn();
            } else if (delta > 0) {
                zoomOut();
            }
        }

        void zoomIn() {
            if (currentZoom < 5) {
                currentZoom++;
                logger.fine("[LiveMap] Zoom in: " + currentZoom);
            }
        }

        void zoomOut() {
            if (currentZoom > 0) {
                currentZoom--;
                logger.fine("[LiveMap] Zoom out: " + currentZoom);
            }
        }

        void centerOnPlayer() {
            PlayerPosition playerPos = world.getPlayer().getPos();
            viewCenterX = playerPos.getTileX();
            viewCenterY = playerPos.getTileY();
            logger.fine("[LiveMap] Centered on player: " + viewCenterX + ", " + viewCenterY);
        }

        private float getWorldTilesPerPixel() {
            return renderer.getWorldUnitsPerPixel(currentZoom);
        }
    }
}
