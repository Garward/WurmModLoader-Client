package com.garward.wurmmodloader.client.api.events.serverpacks;

import com.garward.wurmmodloader.client.api.events.base.Event;

/**
 * Fired when the server sends information about a server pack via ModComm.
 *
 * <p>This event allows mods to download and install server packs (texture/model packs)
 * sent by the server. The typical flow is:
 * <ol>
 *   <li>Server sends pack ID and URI via ModComm</li>
 *   <li>ServerPackReceivedEvent fires</li>
 *   <li>Mod downloads pack from URI</li>
 *   <li>Mod installs pack to packs/ directory</li>
 *   <li>Mod reloads resources</li>
 * </ol>
 *
 * <p>Example usage:
 * <pre>{@code
 * @SubscribeEvent
 * public void onServerPack(ServerPackReceivedEvent event) {
 *     String packId = event.getPackId();
 *     String packUri = event.getPackUri();
 *
 *     // Download pack in background thread
 *     PackDownloader.download(packId, packUri, (tempFile) -> {
 *         // Install on main thread
 *         ModClient.runTask(() -> installPack(packId, tempFile));
 *     });
 * }
 * }</pre>
 *
 * @since 0.2.0
 */
public class ServerPackReceivedEvent extends Event {
    private final String packId;
    private final String packUri;

    public ServerPackReceivedEvent(String packId, String packUri) {
        super(false); // Not cancellable - server dictates packs
        this.packId = packId;
        this.packUri = packUri;
    }

    /**
     * Get the unique identifier for this pack (e.g., "custom-textures").
     */
    public String getPackId() {
        return packId;
    }

    /**
     * Get the download URI for this pack (HTTP/HTTPS URL).
     */
    public String getPackUri() {
        return packUri;
    }

    @Override
    public String toString() {
        return String.format("ServerPackReceived[packId=%s, uri=%s]", packId, packUri);
    }
}
