# ServerPacks Client Mod

Client-side receiver for server-delivered resource packs. Protocol-compatible
with Ago-hosted servers.

## What it does

Registers the legacy `ago.serverpacks` ModComm channel and installs packs the
server advertises. Wire format (unchanged from upstream tyoda/ago1024):

```
int n;
for n: UTF packId; UTF uri;
```

After installing all advertised packs the mod sends `CMD_REFRESH (0x01)` back
to the server to trigger model reload.

## Pack install pipeline

`PackInstaller` mirrors `org.gotti.wurmunlimited.modsupport.packs.ModPacks` via
direct reflection against the vanilla client — no legacy launcher jar needed.

1. Download pack to `packs/<packId>.jar` (skipped if `force=true` not set and
   the file already exists).
2. Construct `com.wurmonline.client.resources.JarPack` and call its `init`.
3. Splice into `Resources.packs` (prepend if URL has `?prepend=true`).
4. Clear `resolvedResources` / `unresolvedResources` and re-resolve every key.
5. Reload XML-driven subsystems: particles, item colors, tile properties,
   terrain normal maps.

Packs are re-used across sessions — only changed packs re-download.

## In-game console commands

| Command | Purpose |
|---------|---------|
| `sp_packs` | List installed packs in lookup order (owner jar path). |
| `sp_probe <key>` | Resolve a mapping and show which pack owns it. Example: `sp_probe model.draedricdecor.sign.overhead.enter` |
| `sp_reload` | Force-clear resolver caches and reload particles/item-colors/tiles/terrain. Useful when late-arriving packs need to replace already-rendered fallbacks without relogging. |

All output prints to the in-game console and is mirrored to `client.log`.

## URL query flags

- `force=true` / `force=1` — re-download even if a cached jar exists.
- `prepend=true` / `prepend=1` — insert pack at index 0 so its mappings win
  over later packs.

## Dependencies

- WurmModLoader Client API (`ClientInitEvent`, `ClientTickEvent`,
  `ClientHUDInitializedEvent`, `ClientConsoleInputEvent`).
- WurmModLoader Client Core (`ModComm` / `Channel` / `PacketReader` /
  `PacketWriter`).
- Wurm Unlimited `client.jar` + `common.jar` (compileOnly, reflected at
  runtime).

## Version

0.3.0
