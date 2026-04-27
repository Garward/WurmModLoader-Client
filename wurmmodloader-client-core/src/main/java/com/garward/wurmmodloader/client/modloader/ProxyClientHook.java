package com.garward.wurmmodloader.client.modloader;

import com.garward.wurmmodloader.client.api.events.base.SubscribeEvent;
import org.gotti.wurmunlimited.modloader.interfaces.WurmClientMod;

import java.io.File;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Enumeration;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.logging.Logger;

/**
 * Static proxy for {@link ClientHook} that is called by bytecode patches.
 *
 * <p>This class provides the static entry points that bytecode patches inject
 * into the Wurm client classes. All methods are static and end with "Event"
 * per the WurmModLoader architecture rules.
 *
 * <h2>Architecture Rules:</h2>
 * <ul>
 *   <li>ALL static methods</li>
 *   <li>Method names MUST end in "Event"</li>
 *   <li>Each method simply delegates to the ClientHook instance</li>
 *   <li>This is the ONLY class that bytecode patches should call</li>
 * </ul>
 *
 * <h2>Usage in Bytecode Patches:</h2>
 * <pre>{@code
 * // In a bytecode patch targeting WurmClientBase.init():
 * public void apply(CtClass ctClass) throws Exception {
 *     CtMethod method = ctClass.getDeclaredMethod("init");
 *     method.insertAfter(
 *         "com.garward.wurmmodloader.client.modloader.ProxyClientHook.fireClientInitEvent();"
 *     );
 * }
 * }</pre>
 *
 * @since 0.1.0
 * @see ClientHook
 */
public class ProxyClientHook extends ClientHook {

    private static final Logger logger = Logger.getLogger(ProxyClientHook.class.getName());
    private static ProxyClientHook instance;

    /**
     * Private constructor - use {@link #getInstance()} instead.
     */
    private ProxyClientHook() {
        super();
    }

    /**
     * Gets the singleton instance of ProxyClientHook.
     * Creates it if it doesn't exist yet.
     *
     * @return the singleton instance
     */
    public static synchronized ProxyClientHook getInstance() {
        if (instance == null) {
            logger.info("Initializing ProxyClientHook singleton");
            instance = new ProxyClientHook();

            // Detect mod type and load appropriately
            logger.info("");
            ModType modType = detectModType();

            switch (modType) {
                case LEGACY:
                    logger.info("Detected LEGACY mods (WurmClientMod interface)");
                    LegacyModLoader legacyLoader = new LegacyModLoader();
                    legacyLoader.loadLegacyMods();
                    break;

                case MODERN:
                    logger.info("Detected MODERN mods (@SubscribeEvent annotations)");
                    ModLoader modLoader = new ModLoader(instance);
                    modLoader.loadMods();
                    break;

                case MIXED:
                    logger.warning("======================================================================");
                    logger.warning("WARNING: Detected BOTH legacy and modern mods!");
                    logger.warning("Legacy mods use WurmClientMod interface (preInit/init)");
                    logger.warning("Modern mods use @SubscribeEvent annotations");
                    logger.warning("");
                    logger.warning("These mod types have different lifecycles and may conflict.");
                    logger.warning("Loading MODERN mods only. Remove legacy mods or use separate install.");
                    logger.warning("======================================================================");
                    ModLoader modernLoader = new ModLoader(instance);
                    modernLoader.loadMods();
                    break;

                case NONE:
                    logger.info("No mods found in mods/ directory");
                    break;
            }
            logger.info("");
        }
        return instance;
    }

    /**
     * Detects what type of mods are in the mods/ directory.
     */
    private static ModType detectModType() {
        File modsDir = new File("mods");
        if (!modsDir.exists() || !modsDir.isDirectory()) {
            return ModType.NONE;
        }

        java.util.List<File> allModJars = new java.util.ArrayList<>();
        File[] flatJars = modsDir.listFiles((dir, name) -> name.endsWith(".jar"));
        if (flatJars != null) {
            java.util.Collections.addAll(allModJars, flatJars);
        }
        File[] subdirs = modsDir.listFiles(File::isDirectory);
        if (subdirs != null) {
            for (File sub : subdirs) {
                File expected = new File(sub, sub.getName() + ".jar");
                if (expected.isFile()) {
                    allModJars.add(expected);
                }
            }
        }
        if (allModJars.isEmpty()) {
            return ModType.NONE;
        }

        boolean hasLegacy = false;
        boolean hasModern = false;

        for (File modJar : allModJars) {
            try (JarFile jar = new JarFile(modJar)) {
                URL[] urls = new URL[] { modJar.toURI().toURL() };
                URLClassLoader loader = new URLClassLoader(urls, ProxyClientHook.class.getClassLoader());

                Enumeration<JarEntry> entries = jar.entries();
                while (entries.hasMoreElements()) {
                    JarEntry entry = entries.nextElement();
                    if (!entry.getName().endsWith(".class")) {
                        continue;
                    }

                    String className = entry.getName().substring(0, entry.getName().length() - 6).replace('/', '.');

                    // Skip system classes
                    if (className.startsWith("java.") || className.startsWith("javax.") ||
                        className.startsWith("com.sun.") || className.endsWith("package-info") ||
                        className.endsWith("module-info")) {
                        continue;
                    }

                    try {
                        Class<?> clazz = loader.loadClass(className);

                        // Check for legacy interface
                        if (WurmClientMod.class.isAssignableFrom(clazz)) {
                            hasLegacy = true;
                        }

                        // Check for modern annotations
                        for (Method method : clazz.getDeclaredMethods()) {
                            if (method.isAnnotationPresent(SubscribeEvent.class)) {
                                hasModern = true;
                                break;
                            }
                        }

                        if (hasLegacy && hasModern) {
                            return ModType.MIXED;
                        }
                    } catch (ClassNotFoundException | NoClassDefFoundError e) {
                        // Skip classes that can't be loaded
                    }
                }
            } catch (Exception e) {
                logger.fine("Error scanning " + modJar.getName() + ": " + e.getMessage());
            }
        }

        if (hasLegacy) {
            return ModType.LEGACY;
        } else if (hasModern) {
            return ModType.MODERN;
        } else {
            return ModType.NONE;
        }
    }

    /**
     * Type of mods detected in mods/ directory.
     */
    private enum ModType {
        /** No mods found */
        NONE,
        /** Legacy mods using WurmClientMod interface */
        LEGACY,
        /** Modern mods using @SubscribeEvent annotations */
        MODERN,
        /** Both legacy and modern mods present (conflict) */
        MIXED
    }

    // ===================================================================
    // STATIC EVENT FIRE METHODS
    // All methods below are called by bytecode patches and delegate
    // to the singleton instance.
    // ===================================================================

    /**
     * Static entry point for client initialization.
     * Called by bytecode patch in WurmClientBase.
     */
    public static void fireClientInitEvent() {
        getInstance().fireClientInit();
    }

    /**
     * Static entry point for world loaded.
     * Called by bytecode patch after world loading completes.
     */
    public static void fireClientWorldLoadedEvent() {
        getInstance().fireClientWorldLoaded();
    }

    /**
     * Static entry point for client tick.
     * Called by bytecode patch in the main game loop.
     *
     * @param deltaTime time elapsed since last tick in seconds
     */
    public static void fireClientTickEvent(float deltaTime) {
        getInstance().fireClientTick(deltaTime);
    }

    /**
     * Static entry point for movement key press/release.
     * Called by bytecode patch in WurmEventHandler.keyPressed/keyReleased.
     *
     * @param keyCode the key code
     * @param keyChar the key character
     * @param pressed true if pressed, false if released
     */
    public static void fireClientMovementIntentEvent(int keyCode, char keyChar, boolean pressed) {
        getInstance().fireClientMovementIntent(keyCode, keyChar, pressed);
    }

    /**
     * Static entry point for pre-player-update.
     * Called by bytecode patch before player transform update.
     *
     * @param deltaSeconds time elapsed since last update in seconds
     */
    public static void fireClientPrePlayerUpdateEvent(float deltaSeconds) {
        getInstance().fireClientPrePlayerUpdate(deltaSeconds);
    }

    /**
     * Static entry point for post-player-update.
     * Called by bytecode patch after player transform update.
     *
     * @param deltaSeconds time elapsed since last update in seconds
     */
    public static void fireClientPostPlayerUpdateEvent(float deltaSeconds) {
        getInstance().fireClientPostPlayerUpdate(deltaSeconds);
    }

    /**
     * Static entry point for authoritative player position.
     * Called by bytecode patch when server position is applied.
     *
     * @param x the x coordinate
     * @param y the y coordinate
     * @param height the height/z coordinate
     * @param sequence sequence/tick identifier for prediction reconciliation
     */
    public static void fireAuthoritativePlayerPositionEvent(float x, float y, float height, long sequence) {
        getInstance().fireAuthoritativePlayerPosition(x, y, height, sequence);
    }

    /**
     * Static entry point for NPC/creature update.
     * Called by bytecode patch in creature game tick.
     *
     * @param creatureId the creature ID
     * @param x the x coordinate
     * @param y the y coordinate
     * @param height the height/z coordinate
     * @param deltaSeconds time elapsed since last tick in seconds
     */
    public static void fireClientNpcUpdateEvent(long creatureId, float x, float y, float height, float deltaSeconds) {
        getInstance().fireClientNpcUpdate(creatureId, x, y, height, deltaSeconds);
    }

    /**
     * Static entry point for combat animation start.
     * Called by bytecode patch when combat animation begins.
     *
     * @param attackerId the attacker's ID
     * @param targetId the target's ID
     * @param animationName the animation name
     * @param localPlayerInvolved true if the local player is involved
     */
    public static void fireClientCombatAnimationStartEvent(long attackerId, long targetId, String animationName, boolean localPlayerInvolved) {
        getInstance().fireClientCombatAnimationStart(attackerId, targetId, animationName, localPlayerInvolved);
    }

    /**
     * Static entry point for combat animation end.
     * Called by bytecode patch when combat animation finishes.
     *
     * @param attackerId the attacker's ID
     * @param animationName the animation name
     */
    public static void fireClientCombatAnimationEndEvent(long attackerId, String animationName) {
        getInstance().fireClientCombatAnimationEnd(attackerId, animationName);
    }

    // ========== WML_SYNC MODCOMM CHANNEL EVENTS ==========

    /**
     * Static entry point for server correction received.
     * Called by WMLSyncClientChannel when server sends position correction.
     *
     * @param seqId sequence ID of the correction
     * @param x correct X position
     * @param y correct Y position
     * @param height correct height
     * @param reason reason for correction
     */
    public static void fireServerCorrectionReceivedEvent(long seqId, float x, float y, float height,
                                                        com.garward.wurmmodloader.client.api.events.sync.ServerCorrectionReceivedEvent.CorrectionReason reason) {
        getInstance().fireServerCorrectionReceived(seqId, x, y, height, reason);
    }

    // ========== SERVER PACKS MODCOMM CHANNEL EVENTS ==========

    /**
     * Static entry point for server pack received.
     * Called by ModComm handler when server sends pack info.
     *
     * @param packId unique pack identifier
     * @param packUri download URI for the pack
     */
    public static void fireServerPackReceivedEvent(String packId, String packUri) {
        getInstance().fireServerPackReceived(packId, packUri);
    }

    // ========== LIVE MAP EVENTS ==========

    /**
     * Static entry point for HUD initialized.
     * Called by bytecode patch in HeadsUpDisplay.init().
     *
     * @param hud HeadsUpDisplay instance
     * @param world World instance
     * @param mainMenu MainMenu instance
     * @param screenWidth screen width in pixels
     * @param screenHeight screen height in pixels
     */
    public static void fireClientHUDInitializedEvent(Object hud, Object world, Object mainMenu, int screenWidth, int screenHeight) {
        getInstance().fireClientHUDInitialized(hud, world, mainMenu, screenWidth, screenHeight);
    }

    /**
     * Static entry point for world map toggle.
     * Called by bytecode patch at the start of HeadsUpDisplay.toggleWorldMapVisible().
     *
     * @param hud HeadsUpDisplay instance
     * @return true if any handler called {@code suppressVanilla()} — patch must
     *         short-circuit the vanilla path when this is true
     */
    public static boolean fireWorldMapToggleRequestedEvent(Object hud) {
        return getInstance().fireWorldMapToggle(hud);
    }

    /**
     * Static entry point for map data received.
     * Called by MapHttpClient when data is fetched.
     *
     * @param jsonData raw JSON data from server
     */
    public static void fireMapDataReceivedEvent(String jsonData) {
        getInstance().fireMapDataReceived(jsonData);
    }

    /**
     * Static entry point for map tile received.
     * Called by MapHttpClient when tile is fetched.
     *
     * @param zoom zoom level
     * @param tileX tile X coordinate
     * @param tileY tile Y coordinate
     * @param tileData PNG tile data
     */
    public static void fireMapTileReceivedEvent(int zoom, int tileX, int tileY, byte[] tileData) {
        getInstance().fireMapTileReceived(zoom, tileX, tileY, tileData);
    }

    // ========== GUI EVENTS ==========

    /**
     * Static entry point for component rendering.
     * Called by bytecode patch in WurmComponent.renderComponent().
     *
     * @param component WurmComponent instance
     * @param queue Queue instance
     * @param x component X position
     * @param y component Y position
     * @param width component width
     * @param height component height
     * @param alpha transparency value
     */
    public static void fireComponentRenderEvent(Object component, Object queue, int x, int y, int width, int height, float alpha) {
        getInstance().fireComponentRender(component, queue, x, y, width, height, alpha);
    }

    /**
     * Static entry point for mouse input (press/release).
     * Called by bytecode patches - NO LOGIC, just passes raw data.
     * Logic handled in MouseInputEventLogic.
     *
     * @param component WurmComponent instance
     * @param methodName method name (leftPressed, rightPressed, leftReleased, rightReleased)
     * @param mouseX mouse X position
     * @param mouseY mouse Y position
     * @param clickCount number of clicks
     */
    public static void fireMouseInputEvent(Object component, String methodName, int mouseX, int mouseY, int clickCount) {
        getInstance().fireMouseInput(component, methodName, mouseX, mouseY, clickCount);
    }

    /**
     * Static entry point for mouse drag (raw position).
     * Called by bytecode patch - NO LOGIC, just passes raw data.
     * Delta calculation handled in MouseInputEventLogic.
     *
     * @param component WurmComponent instance
     * @param mouseX current mouse X position
     * @param mouseY current mouse Y position
     */
    public static void fireMouseDragRawEvent(Object component, int mouseX, int mouseY) {
        getInstance().fireMouseDragRaw(component, mouseX, mouseY);
    }

    /**
     * Static entry point for mouse scroll.
     * Called by bytecode patch (if implemented).
     *
     * @param component WurmComponent instance
     * @param delta scroll delta
     */
    public static void fireMouseScrollEvent(Object component, int delta) {
        getInstance().fireMouseScroll(component, delta);
    }

    // ========== COMPASS ==========

    /**
     * Static entry point fired after {@code CompassComponent.gameTick()}.
     */
    public static void fireCompassComponentTickEvent(Object component) {
        getInstance().fireCompassComponentTick(component);
    }

    /**
     * Static entry point fired after {@code CompassComponent.pick(PickData,int,int)}.
     */
    public static void fireCompassComponentPickEvent(Object component, Object pickData, int mouseX, int mouseY) {
        getInstance().fireCompassComponentPick(component, pickData, mouseX, mouseY);
    }

    // ========== PICK RENDER (overlay seam) ==========

    /**
     * Static entry point fired at the start of {@code PickRenderer.execute(Queue)}.
     * Called by {@code PickRenderPatch}. Mods subscribed to
     * {@code PickRenderPreEvent} can inject primitives into the queue.
     *
     * @param queue the active render Queue
     */
    public static void firePickRenderPreEvent(Object queue) {
        getInstance().firePickRenderPre(queue);
    }

    /**
     * Static entry point fired at the end of {@code PickRenderer.execute(Queue)}.
     * Called by {@code PickRenderPatch}. Mods subscribed to
     * {@code PickRenderPostEvent} can append primitives after the engine's
     * native pick geometry.
     *
     * @param queue the active render Queue
     */
    public static void firePickRenderPostEvent(Object queue) {
        getInstance().firePickRenderPost(queue);
    }

    /**
     * Static entry point fired at the end of
     * {@code WorldRender.renderPickedItem(Queue)} — the visible render pass.
     */
    public static void fireWorldRenderPostEvent(Object queue, Object worldRender, Object pickRenderer) {
        getInstance().fireWorldRenderPost(queue, worldRender, pickRenderer);
    }

    /**
     * Static entry point fired when a {@code CellRenderable} finishes
     * initialization (creature/player {@code initialize()} or ground-item
     * constructor). Dispatches {@code CellRenderableInitEvent}.
     */
    public static void fireCellRenderableInitEvent(Object renderable) {
        getInstance().fireCellRenderableInit(renderable);
    }

    /**
     * Static entry point fired when {@code CellRenderable.removed(boolean)}
     * is invoked. Dispatches {@code CellRenderableRemovedEvent}.
     */
    public static void fireCellRenderableRemovedEvent(Object renderable, boolean removeEffects) {
        getInstance().fireCellRenderableRemoved(renderable, removeEffects);
    }

    // ========== CONSOLE / NET ==========

    /**
     * Static entry point for {@code WurmConsole.handleDevInput}. Returns
     * {@code true} if any subscriber cancelled — the patch then returns
     * {@code true} to claim the command.
     */
    public static boolean fireClientConsoleInputEventCancelled(String command, String[] args) {
        return getInstance().fireClientConsoleInput(command, args);
    }

    /**
     * Static entry point for {@code PlayerAction.getName()}. Returns a
     * subscriber-supplied override name, or {@code null} to fall through to
     * the vanilla name.
     */
    public static String fireGetPlayerActionNameEvent(short actionId, String originalName) {
        return getInstance().fireGetPlayerActionName(actionId, originalName);
    }

    /**
     * Static entry point for {@code WurmPopup.rebindPrimary()}. Fires a
     * cancellable {@link com.garward.wurmmodloader.client.api.events.client.QuickActionRebindEvent}
     * and returns {@code true} if any subscriber cancelled — the patch then
     * returns, suppressing vanilla's {@code bind/temporaryBind} write.
     */
    public static boolean fireQuickActionRebindEventCancelled(short actionId, String actionName,
                                                              int rawKey, boolean ctrlDown,
                                                              boolean shiftDown, boolean altDown) {
        return getInstance().fireQuickActionRebind(actionId, actionName, rawKey, ctrlDown, shiftDown, altDown);
    }

    // isDev override — any mod can force-enable dev gating globally (quick
    // keybind dialogs, toggleKey, rebindPrimary, etc.). Atomic so the patched
    // isDev() path stays lock-free.
    private static final java.util.concurrent.atomic.AtomicBoolean DEV_OVERRIDE =
        new java.util.concurrent.atomic.AtomicBoolean(false);

    public static void setDevOverride(boolean enabled) {
        DEV_OVERRIDE.set(enabled);
    }

    public static boolean isDevOverrideActive() {
        return DEV_OVERRIDE.get();
    }

    /**
     * Static entry point for the deed-plan packet handler. Returns
     * {@code true} if any subscriber cancelled — the patch then returns,
     * suppressing the vanilla render path.
     */
    public static boolean fireDeedPlanPacketEventCancelled(java.nio.ByteBuffer buffer) {
        return getInstance().fireDeedPlanPacket(buffer);
    }

    // ========== SERVER CAPABILITIES ==========

    /**
     * Public accessor for server capability event firing.
     * Called by WMLCapabilitiesClientChannel.
     *
     * @param event the event to fire
     */
    public void fireServerCapabilitiesReceived(
            com.garward.wurmmodloader.client.api.events.lifecycle.ServerCapabilitiesReceivedEvent event) {
        super.fireServerCapabilitiesReceived(event);
    }

    // ========== SERVER INFO (wml.serverinfo channel) ==========

    /**
     * Static entry point fired by {@code ServerInfoClientChannel} when the
     * wml.serverinfo ModComm packet arrives.
     */
    public static void fireServerInfoAvailableEvent(String httpUri, String modloaderVersion) {
        getInstance().fireServerInfoAvailable(httpUri, modloaderVersion);
    }

    // ========== FOV CHANGE DETECTION ==========

    /**
     * Static entry point for FOV change detection.
     * Called by bytecode patch in RangeOption.set().
     *
     * <p>This method contains ALL logic for determining if an event should fire:
     * <ul>
     *   <li>Checks if option is fovHorizontal</li>
     *   <li>Checks if value actually changed</li>
     *   <li>Only fires event if both conditions are true</li>
     * </ul>
     *
     * <p><b>Architecture:</b> Logic lives HERE, NOT in the bytecode patch.</p>
     *
     * @param option the RangeOption being modified (reflection type: Object)
     * @param oldValue the value before set() was called
     * @param newValue the value after set() was called
     */
    // Cached "key" field lookup per Option class. Walks the inheritance chain
    // because `key` lives on a base class that may sit several levels above
    // any given RangeOption subclass. null sentinel = class has no "key" at all.
    private static final java.util.concurrent.ConcurrentHashMap<Class<?>, java.lang.reflect.Field> OPTION_KEY_FIELDS =
        new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.lang.reflect.Field NO_KEY_FIELD;
    static {
        java.lang.reflect.Field sentinel = null;
        try { sentinel = ProxyClientHook.class.getDeclaredField("OPTION_KEY_FIELDS"); } catch (NoSuchFieldException ignored) {}
        NO_KEY_FIELD = sentinel;
    }

    private static java.lang.reflect.Field resolveOptionKeyField(Class<?> cls) {
        java.lang.reflect.Field cached = OPTION_KEY_FIELDS.get(cls);
        if (cached != null) return cached == NO_KEY_FIELD ? null : cached;
        for (Class<?> c = cls; c != null && c != Object.class; c = c.getSuperclass()) {
            try {
                java.lang.reflect.Field f = c.getDeclaredField("key");
                f.setAccessible(true);
                OPTION_KEY_FIELDS.put(cls, f);
                return f;
            } catch (NoSuchFieldException ignored) {}
        }
        OPTION_KEY_FIELDS.put(cls, NO_KEY_FIELD);
        return null;
    }

    public static void fireFOVChangedEventIfApplicable(Object option, int oldValue, int newValue) {
        if (oldValue == newValue) return;
        java.lang.reflect.Field keyField = resolveOptionKeyField(option.getClass());
        if (keyField == null) return;
        try {
            if (!"fov_horizontal".equals(keyField.get(option))) return;
            getInstance().fireFOVChanged(oldValue, newValue);
        } catch (IllegalAccessException e) {
            logger.warning("[ProxyClientHook] FOV key read failed: " + e.getMessage());
        }
    }

    // ========== STAMINA ==========

    /**
     * Static entry point fired by the {@code ServerConnectionListenerClass.setStamina}
     * patch on every CMD_STAMINA packet.
     *
     * @param newStamina new stamina value, 0.0–1.0
     */
    public static void fireClientStaminaChangedEvent(float newStamina) {
        try { getInstance().fireClientStaminaChanged(newStamina); }
        catch (Throwable t) { t.printStackTrace(); }
    }

    /**
     * Snapshot accessor — most recent stamina value observed, or {@code Float.NaN}
     * before the first CMD_STAMINA packet of the session.
     */
    public static float getCurrentStamina() {
        return getInstance().getLastStamina();
    }

    // ========== EVENT MESSAGE (textMessage overloads) ==========

    /**
     * Static entry point fired by the {@code ServerConnectionListenerClass.textMessage}
     * patches on both the single-color and multicolor overloads. Returns
     * {@code true} if any subscriber cancelled — the patch then returns,
     * suppressing vanilla display.
     */
    public static boolean fireClientEventMessageReceivedEvent(String window, String text, byte type) {
        try { return getInstance().fireClientEventMessage(window, text, type); }
        catch (Throwable t) { t.printStackTrace(); return false; }
    }
}
