package org.gotti.wurmunlimited.modloader.interfaces;

/**
 * Legacy interface from Ago's WurmClientModLauncher.
 * Mods implementing this interface use the old lifecycle callbacks.
 *
 * <h2>Lifecycle:</h2>
 * <ol>
 *   <li>{@link #preInit()} - Called before client initialization</li>
 *   <li>{@link #init()} - Called after client initialization</li>
 * </ol>
 *
 * <h2>Compatibility Note:</h2>
 * <p>Legacy mods use a different lifecycle than modern event-based mods.
 * It's not recommended to mix legacy and modern mods in the same installation,
 * as they may conflict with each other's bytecode patches.
 *
 * @since 0.1.0 (legacy compatibility)
 * @see com.garward.wurmmodloader.client.api.events.base.SubscribeEvent for modern event-based mods
 */
public interface WurmClientMod extends Versioned {

    /**
     * Called before client initialization.
     * Use this for early setup that needs to happen before the client starts.
     */
    default void preInit() {
    }

    /**
     * Called after client initialization.
     * Use this for setup that requires the client to be initialized.
     */
    default void init() {
    }
}
