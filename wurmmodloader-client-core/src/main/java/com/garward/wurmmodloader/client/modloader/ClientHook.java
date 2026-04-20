package com.garward.wurmmodloader.client.modloader;

import com.garward.wurmmodloader.client.api.events.base.Event;
import com.garward.wurmmodloader.client.api.events.lifecycle.ClientInitEvent;
import com.garward.wurmmodloader.client.api.events.lifecycle.ClientTickEvent;
import com.garward.wurmmodloader.client.api.events.lifecycle.ClientWorldLoadedEvent;
import com.garward.wurmmodloader.client.api.events.client.movement.ClientMovementIntentEvent;
import com.garward.wurmmodloader.client.api.events.client.movement.ClientPrePlayerUpdateEvent;
import com.garward.wurmmodloader.client.api.events.client.movement.ClientPostPlayerUpdateEvent;
import com.garward.wurmmodloader.client.api.events.client.movement.AuthoritativePlayerPositionEvent;
import com.garward.wurmmodloader.client.api.events.client.npc.ClientNpcUpdateEvent;
import com.garward.wurmmodloader.client.api.events.client.combat.ClientCombatAnimationStartEvent;
import com.garward.wurmmodloader.client.api.events.client.combat.ClientCombatAnimationEndEvent;
import com.garward.wurmmodloader.client.core.event.EventBus;

import java.util.logging.Logger;

/**
 * Client-side hook implementation for WurmModLoader.
 *
 * <p>This class provides instance methods for firing events into the client event bus.
 * All static methods should be in {@link ProxyClientHook} which delegates to this instance.
 *
 * <h2>Architecture Rules:</h2>
 * <ul>
 *   <li>ALL instance methods, NO static methods</li>
 *   <li>Methods do NOT end in "Event" (that's for ProxyClientHook)</li>
 *   <li>Each method posts exactly one event to the event bus</li>
 *   <li>This is where the event bus gets initialized</li>
 * </ul>
 *
 * @since 0.1.0
 * @see ProxyClientHook
 */
public class ClientHook {

    private static final Logger logger = Logger.getLogger(ClientHook.class.getName());

    private final EventBus eventBus;

    /**
     * Creates a new ClientHook instance.
     * Called by ProxyClientHook during initialization.
     */
    public ClientHook() {
        logger.info("ClientHook initialized");
        this.eventBus = new EventBus();
        com.garward.wurmmodloader.client.serverinfo.ServerInfoClientChannel.initialize();
    }

    /**
     * Fires when the client finishes initialization.
     */
    public void fireClientInit() {
        logger.info("🎯 Firing ClientInitEvent");
        ClientInitEvent event = new ClientInitEvent();
        postEvent(event);
    }

    /**
     * Fires when the client world finishes loading.
     */
    public void fireClientWorldLoaded() {
        logger.info("🌍 Firing ClientWorldLoadedEvent");
        ClientWorldLoadedEvent event = new ClientWorldLoadedEvent();
        postEvent(event);
    }

    /**
     * Fires every client tick (frame).
     *
     * @param deltaTime time elapsed since last tick in seconds
     */
    public void fireClientTick(float deltaTime) {
        // Only log the first tick to confirm it's working
        if (!firstTickLogged) {
            logger.info("⏱️  Firing ClientTickEvent (deltaTime=" + deltaTime + "s) - further ticks will not be logged");
            firstTickLogged = true;
        }
        ClientTickEvent event = new ClientTickEvent(deltaTime);
        postEvent(event);
    }

    private boolean firstTickLogged = false;

    /**
     * Fires when a movement-related key is pressed or released.
     *
     * @param keyCode the key code
     * @param keyChar the key character
     * @param pressed true if pressed, false if released
     */
    public void fireClientMovementIntent(int keyCode, char keyChar, boolean pressed) {
        ClientMovementIntentEvent event = new ClientMovementIntentEvent(keyCode, keyChar, pressed);
        postEvent(event);
    }

    /**
     * Fires before the local player's transform is updated.
     *
     * @param deltaSeconds time elapsed since last update in seconds
     */
    public void fireClientPrePlayerUpdate(float deltaSeconds) {
        ClientPrePlayerUpdateEvent event = new ClientPrePlayerUpdateEvent(deltaSeconds);
        postEvent(event);
    }

    /**
     * Fires after the local player's transform has been updated.
     *
     * @param deltaSeconds time elapsed since last update in seconds
     */
    public void fireClientPostPlayerUpdate(float deltaSeconds) {
        ClientPostPlayerUpdateEvent event = new ClientPostPlayerUpdateEvent(deltaSeconds);
        postEvent(event);
    }

    /**
     * Fires when the game applies a new authoritative position from the server.
     *
     * @param x the x coordinate
     * @param y the y coordinate
     * @param height the height/z coordinate
     * @param sequence sequence/tick identifier for prediction reconciliation
     */
    public void fireAuthoritativePlayerPosition(float x, float y, float height, long sequence) {
        AuthoritativePlayerPositionEvent event = new AuthoritativePlayerPositionEvent(x, y, height, sequence);
        postEvent(event);
    }

    /**
     * Fires when an NPC/creature renderable's game tick advances.
     *
     * @param creatureId the creature ID
     * @param x the x coordinate
     * @param y the y coordinate
     * @param height the height/z coordinate
     * @param deltaSeconds time elapsed since last tick in seconds
     */
    public void fireClientNpcUpdate(long creatureId, float x, float y, float height, float deltaSeconds) {
        ClientNpcUpdateEvent event = new ClientNpcUpdateEvent(creatureId, x, y, height, deltaSeconds);
        postEvent(event);
    }

    /**
     * Fires when a combat animation begins for a creature.
     *
     * @param attackerId the attacker's ID
     * @param targetId the target's ID
     * @param animationName the animation name
     * @param localPlayerInvolved true if the local player is involved
     */
    public void fireClientCombatAnimationStart(long attackerId, long targetId, String animationName, boolean localPlayerInvolved) {
        ClientCombatAnimationStartEvent event = new ClientCombatAnimationStartEvent(attackerId, targetId, animationName, localPlayerInvolved);
        postEvent(event);
    }

    /**
     * Fires when a combat animation finishes for a creature.
     *
     * @param attackerId the attacker's ID
     * @param animationName the animation name
     */
    public void fireClientCombatAnimationEnd(long attackerId, String animationName) {
        ClientCombatAnimationEndEvent event = new ClientCombatAnimationEndEvent(attackerId, animationName);
        postEvent(event);
    }

    // ========== WML_SYNC MODCOMM CHANNEL EVENTS ==========

    /**
     * Fire ServerCorrectionReceivedEvent when server sends position correction.
     *
     * @param seqId sequence ID of the correction
     * @param x correct X position
     * @param y correct Y position
     * @param height correct height
     * @param reason reason for correction
     */
    public void fireServerCorrectionReceived(long seqId, float x, float y, float height,
                                            com.garward.wurmmodloader.client.api.events.sync.ServerCorrectionReceivedEvent.CorrectionReason reason) {
        com.garward.wurmmodloader.client.api.events.sync.ServerCorrectionReceivedEvent event =
            new com.garward.wurmmodloader.client.api.events.sync.ServerCorrectionReceivedEvent(seqId, x, y, height, reason);
        postEvent(event);
    }

    // ========== SERVER PACKS MODCOMM CHANNEL EVENTS ==========

    /**
     * Fire ServerPackReceivedEvent when server sends pack info via ModComm.
     *
     * @param packId unique pack identifier
     * @param packUri download URI for the pack
     */
    public void fireServerPackReceived(String packId, String packUri) {
        com.garward.wurmmodloader.client.api.events.serverpacks.ServerPackReceivedEvent event =
            new com.garward.wurmmodloader.client.api.events.serverpacks.ServerPackReceivedEvent(packId, packUri);
        postEvent(event);
    }

    // ========== LIVE MAP EVENTS ==========

    /**
     * Fire ClientHUDInitializedEvent when HUD is fully initialized.
     *
     * @param hud HeadsUpDisplay instance
     * @param world World instance
     * @param mainMenu MainMenu instance
     * @param screenWidth screen width in pixels
     * @param screenHeight screen height in pixels
     */
    public void fireClientHUDInitialized(Object hud, Object world, Object mainMenu, int screenWidth, int screenHeight) {
        com.garward.wurmmodloader.client.api.events.map.ClientHUDInitializedEvent event =
            new com.garward.wurmmodloader.client.api.events.map.ClientHUDInitializedEvent(
                hud, world, mainMenu, screenWidth, screenHeight
            );
        postEvent(event);
    }

    /**
     * Fire WorldMapToggleRequestedEvent when HeadsUpDisplay.toggleWorldMapVisible()
     * is invoked. Returns whether any handler suppressed the vanilla path.
     *
     * @param hud HeadsUpDisplay instance
     * @return true if the vanilla path should be skipped
     */
    public boolean fireWorldMapToggle(Object hud) {
        com.garward.wurmmodloader.client.api.events.map.WorldMapToggleRequestedEvent event =
            new com.garward.wurmmodloader.client.api.events.map.WorldMapToggleRequestedEvent(hud);
        postEvent(event);
        return event.isSuppressed();
    }

    /**
     * Fire MapDataReceivedEvent when map data is fetched from server.
     *
     * @param jsonData raw JSON data from server
     */
    public void fireMapDataReceived(String jsonData) {
        com.garward.wurmmodloader.client.api.events.map.MapDataReceivedEvent event =
            new com.garward.wurmmodloader.client.api.events.map.MapDataReceivedEvent(jsonData);
        postEvent(event);
    }

    /**
     * Fire MapTileReceivedEvent when a map tile is fetched from server.
     *
     * @param zoom zoom level
     * @param tileX tile X coordinate
     * @param tileY tile Y coordinate
     * @param tileData PNG tile data
     */
    public void fireMapTileReceived(int zoom, int tileX, int tileY, byte[] tileData) {
        com.garward.wurmmodloader.client.api.events.map.MapTileReceivedEvent event =
            new com.garward.wurmmodloader.client.api.events.map.MapTileReceivedEvent(zoom, tileX, tileY, tileData);
        postEvent(event);
    }

    // ========== GUI EVENTS ==========

    /**
     * Fire ComponentRenderEvent when a GUI component needs to render.
     *
     * @param component the WurmComponent being rendered
     * @param queue rendering queue
     * @param x component X position
     * @param y component Y position
     * @param width component width
     * @param height component height
     * @param alpha transparency value
     */
    public void fireComponentRender(Object component, Object queue, int x, int y, int width, int height, float alpha) {
        com.garward.wurmmodloader.client.api.events.gui.ComponentRenderEvent event =
            new com.garward.wurmmodloader.client.api.events.gui.ComponentRenderEvent(component, queue, x, y, width, height, alpha);
        postEvent(event);
    }

    /**
     * Fire MouseClickEvent from raw input data.
     * Uses MouseInputEventLogic to determine button/pressed from method name.
     *
     * @param component the component receiving the click
     * @param methodName method name (leftPressed, rightPressed, etc.)
     * @param mouseX mouse X position
     * @param mouseY mouse Y position
     * @param clickCount number of clicks
     */
    public void fireMouseInput(Object component, String methodName, int mouseX, int mouseY, int clickCount) {
        // Use eventlogic to determine button and pressed state
        int button = com.garward.wurmmodloader.client.api.events.eventlogic.MouseInputEventLogic
            .getButtonFromMethodName(methodName);
        boolean pressed = com.garward.wurmmodloader.client.api.events.eventlogic.MouseInputEventLogic
            .getPressedStateFromMethodName(methodName);

        com.garward.wurmmodloader.client.api.events.gui.MouseClickEvent event =
            new com.garward.wurmmodloader.client.api.events.gui.MouseClickEvent(
                component, mouseX, mouseY, button, clickCount, pressed);
        postEvent(event);
    }

    /**
     * Fire MouseDragEvent from raw mouse position.
     * Uses MouseInputEventLogic to calculate delta.
     *
     * @param component the component being dragged on
     * @param mouseX current mouse X position
     * @param mouseY current mouse Y position
     */
    public void fireMouseDragRaw(Object component, int mouseX, int mouseY) {
        // Use eventlogic to calculate delta from tracked positions
        int[] delta = com.garward.wurmmodloader.client.api.events.eventlogic.MouseInputEventLogic
            .calculateMouseDelta(component, mouseX, mouseY);

        com.garward.wurmmodloader.client.api.events.gui.MouseDragEvent event =
            new com.garward.wurmmodloader.client.api.events.gui.MouseDragEvent(
                component, mouseX, mouseY, delta[0], delta[1]);
        postEvent(event);
    }

    /**
     * Fire MouseScrollEvent when mouse wheel is scrolled.
     *
     * @param component the component being scrolled on
     * @param delta scroll delta (positive=up, negative=down)
     */
    public void fireMouseScroll(Object component, int delta) {
        com.garward.wurmmodloader.client.api.events.gui.MouseScrollEvent event =
            new com.garward.wurmmodloader.client.api.events.gui.MouseScrollEvent(component, delta);
        postEvent(event);
    }

    /**
     * Fire ServerCapabilitiesReceivedEvent when server sends mod capabilities.
     *
     * @param event the event to fire
     */
    public void fireServerCapabilitiesReceived(
            com.garward.wurmmodloader.client.api.events.lifecycle.ServerCapabilitiesReceivedEvent event) {
        logger.info("🔌 Firing ServerCapabilitiesReceivedEvent with " +
                   event.getServerMods().size() + " server mods");
        postEvent(event);
    }

    /**
     * Fire ServerInfoAvailableEvent once the wml.serverinfo ModComm channel
     * delivers the server's HTTP URI + modloader version.
     */
    public void fireServerInfoAvailable(String httpUri, String modloaderVersion) {
        logger.info("📡 Firing ServerInfoAvailableEvent (httpUri=" + httpUri + ")");
        com.garward.wurmmodloader.client.api.events.lifecycle.ServerInfoAvailableEvent event =
            new com.garward.wurmmodloader.client.api.events.lifecycle.ServerInfoAvailableEvent(httpUri, modloaderVersion);
        postEvent(event);
    }

    // ========== FOV CHANGE EVENTS ==========

    /**
     * Fire FOVChangedEvent when field of view changes.
     *
     * @param oldFOV the previous FOV value (degrees)
     * @param newFOV the new FOV value (degrees)
     */
    public void fireFOVChanged(int oldFOV, int newFOV) {
        logger.info("🎥 Firing FOVChangedEvent (oldFOV=" + oldFOV + ", newFOV=" + newFOV + ")");
        com.garward.wurmmodloader.client.api.events.client.FOVChangedEvent event =
            new com.garward.wurmmodloader.client.api.events.client.FOVChangedEvent(oldFOV, newFOV);
        postEvent(event);
    }

    /**
     * Posts an event to the event bus.
     *
     * @param event the event to post
     */
    private void postEvent(Event event) {
        eventBus.post(event);
        logger.finest("Event posted: " + event);
    }

    /**
     * Registers a listener object with the event bus.
     *
     * @param listener the listener to register
     */
    public void registerListener(Object listener) {
        eventBus.register(listener);
        logger.info("Registered event listener: " + listener.getClass().getName());
    }

    /**
     * Unregisters a listener object from the event bus.
     *
     * @param listener the listener to unregister
     */
    public void unregisterListener(Object listener) {
        eventBus.unregister(listener);
        logger.info("Unregistered event listener: " + listener.getClass().getName());
    }
}
