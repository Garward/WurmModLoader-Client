package com.garward.wurmmodloader.client.api.events.lifecycle;

import com.garward.wurmmodloader.client.api.events.base.Event;

/**
 * Fired once the client receives server connection info over the
 * {@code wml.serverinfo} ModComm channel. Exposes the server's HTTP base URI
 * so client mods don't need to hardcode a host/port.
 *
 * <p>A mod that needs the HTTP URI synchronously (e.g. after joining late or
 * reacting to some other event) can also read it from
 * {@link com.garward.wurmmodloader.client.api.serverinfo.ServerInfoRegistry}.
 */
public class ServerInfoAvailableEvent extends Event {

    private final String httpUri;
    private final String modloaderVersion;

    public ServerInfoAvailableEvent(String httpUri, String modloaderVersion) {
        super(false);
        this.httpUri = httpUri == null ? "" : httpUri;
        this.modloaderVersion = modloaderVersion == null ? "" : modloaderVersion;
    }

    public String getHttpUri() {
        return httpUri;
    }

    public String getModloaderVersion() {
        return modloaderVersion;
    }

    @Override
    public String toString() {
        return "ServerInfoAvailableEvent{httpUri=" + httpUri + ", modloaderVersion=" + modloaderVersion + "}";
    }
}
