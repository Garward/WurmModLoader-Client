package com.garward.wurmmodloader.examples.hellomod;

import com.garward.wurmmodloader.client.api.events.base.SubscribeEvent;
import com.garward.wurmmodloader.client.api.events.lifecycle.ClientInitEvent;
import com.garward.wurmmodloader.client.api.events.lifecycle.ClientWorldLoadedEvent;

import java.util.logging.Logger;

/**
 * Smallest possible WurmModLoader-Client mod.
 *
 * <p>One class, two event handlers, no Wurm imports. Logs when the client
 * initializes and when the player enters a world. That's it.
 *
 * <p>For a full mod that connects to a server-side counterpart and ships
 * custom UI, see {@code mods/livemap/}.
 */
public class HelloMod {

    private static final Logger logger = Logger.getLogger(HelloMod.class.getName());

    @SubscribeEvent
    public void onClientInit(ClientInitEvent event) {
        logger.info("[HelloMod] Client initialized — modloader is alive.");
    }

    @SubscribeEvent
    public void onWorldLoaded(ClientWorldLoadedEvent event) {
        logger.info("[HelloMod] World loaded — welcome in.");
    }
}
