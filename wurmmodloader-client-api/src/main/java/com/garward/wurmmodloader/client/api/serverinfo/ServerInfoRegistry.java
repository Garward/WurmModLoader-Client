package com.garward.wurmmodloader.client.api.serverinfo;

/**
 * Holds the latest server connection info received from the server via the
 * {@code wml.serverinfo} ModComm channel.
 *
 * <p>Client mods should either subscribe to
 * {@link ServerInfoAvailableEvent} (if they want a push notification) or
 * call {@link #getHttpUri()} after login (if they connected late). The
 * registry is cleared on disconnect.
 */
public final class ServerInfoRegistry {

    private static volatile String httpUri = "";
    private static volatile String modloaderVersion = "";
    private static volatile boolean available = false;

    private ServerInfoRegistry() {}

    public static void update(String httpUri, String modloaderVersion) {
        ServerInfoRegistry.httpUri = httpUri == null ? "" : httpUri;
        ServerInfoRegistry.modloaderVersion = modloaderVersion == null ? "" : modloaderVersion;
        ServerInfoRegistry.available = true;
    }

    public static void clear() {
        httpUri = "";
        modloaderVersion = "";
        available = false;
    }

    public static boolean isAvailable() {
        return available;
    }

    /**
     * @return base URI of the server's HTTP endpoint (e.g. {@code http://1.2.3.4:9090/}),
     *         or empty string if the server doesn't expose an HTTP server or hasn't
     *         reported yet.
     */
    public static String getHttpUri() {
        return httpUri;
    }

    public static String getModloaderVersion() {
        return modloaderVersion;
    }
}
