package com.garward.wurmmodloader.client.api.events.map;

import com.garward.wurmmodloader.client.api.events.base.Event;

/**
 * Fired when the client HUD is fully initialized.
 *
 * <p>This event allows mods to add custom windows, buttons, and components to the HUD
 * after it has been set up. This is the ideal time to register map windows, overlays,
 * and other UI elements.
 *
 * <p>Provides access to HUD, World, MainMenu, and screen dimensions for component setup.
 *
 * <p>Example usage:
 * <pre>{@code
 * @SubscribeEvent
 * public void onHUDInit(ClientHUDInitializedEvent event) {
 *     // Create map window
 *     LiveMapWindow mapWindow = new LiveMapWindow(event.getWorld(), event.getScreenWidth(), event.getScreenHeight());
 *
 *     // Register with MainMenu
 *     event.getMainMenu().registerComponent("Live Map", mapWindow);
 *
 *     // Add to HUD
 *     event.getHud().addComponent(mapWindow);
 * }
 * }</pre>
 *
 * @since 0.2.0
 */
public class ClientHUDInitializedEvent extends Event {

    private final Object hud; // HeadsUpDisplay
    private final Object world; // World
    private final Object mainMenu; // MainMenu
    private final int screenWidth;
    private final int screenHeight;

    public ClientHUDInitializedEvent(Object hud, Object world, Object mainMenu, int screenWidth, int screenHeight) {
        super(false); // Not cancellable - HUD already initialized

        this.hud = hud;
        this.world = world;
        this.mainMenu = mainMenu;
        this.screenWidth = screenWidth;
        this.screenHeight = screenHeight;
    }

    /**
     * Get the HUD object (com.wurmonline.client.renderer.gui.HeadsUpDisplay).
     */
    public Object getHud() {
        return hud;
    }

    /**
     * Get the World object (com.wurmonline.client.game.World).
     */
    public Object getWorld() {
        return world;
    }

    /**
     * Get the MainMenu object (com.wurmonline.client.renderer.gui.MainMenu).
     */
    public Object getMainMenu() {
        return mainMenu;
    }

    /**
     * Get screen width in pixels.
     */
    public int getScreenWidth() {
        return screenWidth;
    }

    /**
     * Get screen height in pixels.
     */
    public int getScreenHeight() {
        return screenHeight;
    }

    @Override
    public String toString() {
        return "ClientHUDInitialized[" + screenWidth + "x" + screenHeight + "]";
    }
}
