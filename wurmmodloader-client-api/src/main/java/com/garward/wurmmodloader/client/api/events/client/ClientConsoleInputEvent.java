package com.garward.wurmmodloader.client.api.events.client;

import com.garward.wurmmodloader.client.api.events.base.Event;

/**
 * Fired when the user types a console command. Cancellable — handlers may
 * claim a command (via {@link #cancel()}) to suppress the vanilla
 * "unknown command" path. Used by mods that want runtime toggle commands
 * (e.g. ESP feature toggles) without registering a full console handler.
 *
 * @since 0.3.0
 */
public class ClientConsoleInputEvent extends Event {

    private final String command;
    private final String[] args;

    public ClientConsoleInputEvent(String command, String[] args) {
        super(true);
        this.command = command;
        this.args = args;
    }

    public String getCommand() {
        return command;
    }

    public String[] getArgs() {
        return args;
    }

    @Override
    public String toString() {
        return "ClientConsoleInput[" + command + " argc=" + (args == null ? 0 : args.length) + "]";
    }
}
