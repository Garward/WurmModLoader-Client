# Troubleshooting — Client Mods

Common problems with client mods, ordered from most-common (didn't
deploy, didn't load) to rarer (classloader / render / ModComm issues).

The server side has its own guide at
[`../../../WurmModLoader/docs/guides/troubleshooting.md`](../../../WurmModLoader/docs/guides/troubleshooting.md) —
patterns carry over, but the client has its own quirks (manual deploy,
patched `client.jar`, HUD lifecycle).

---

## Where to look first

The client log lives in Wurm's launcher directory (`<wurm-client-dir>`):

```
<wurm-client-dir>/console.*.log
```

Default Steam locations for `<wurm-client-dir>`:

- **Windows:** `C:\Program Files (x86)\Steam\steamapps\common\Wurm Unlimited\WurmLauncher\`
- **Linux:**   `~/.local/share/Steam/steamapps/common/Wurm Unlimited/WurmLauncher/`

Tail it while testing:

```bash
# Linux/macOS
tail -f "<wurm-client-dir>/console."*.log
```
```powershell
# Windows PowerShell
Get-Content "<wurm-client-dir>\console.*.log" -Wait -Tail 50
```

If you don't see your mod's startup line, the mod isn't loaded — start
with the "didn't deploy" and "didn't load" sections below.

---

## Mod didn't deploy

### Symptom: changes don't appear after rebuild

The client `build-and-deploy.sh` **builds and patches `client.jar` but
does not copy mod jars**. This bites everyone once. Either:

- Add a `deployMod` task to your mod's `build.gradle.kts` (copy the
  block from [`examples/hellomod/build.gradle.kts`](../../examples/hellomod/build.gradle.kts))
  and run `./gradlew :examples:yourmod:deployMod`, or
- `cp` the jar + `.properties` into `~/.../WurmLauncher/mods/`
  yourself after each build.

Verify deployment:

```bash
# Linux/macOS
ls -la "<wurm-client-dir>/mods/"
```
```powershell
# Windows
dir "<wurm-client-dir>\mods\"
```

The jar timestamp should update after each build.

### Symptom: client launches vanilla (no modloader at all)

You probably launched `WurmLauncher.jar` directly. The **patched**
client needs to be launched via the patcher or the modded launcher
script — see [`../../INSTALLATION.md`](../../INSTALLATION.md) and
[`../../PATCHER.md`](../../PATCHER.md).

Indicator in the log: if you don't see a `[WurmModLoader-Client]`
banner near the top, you launched unpatched.

---

## Mod deployed but doesn't load

### Symptom: `ClassNotFoundException` on your mod class

- `classname=` in the `.properties` is misspelled or the package path
  is wrong.
- `classpath=yourmod.jar` doesn't match the actual jar filename in
  `WurmLauncher/mods/`. The client loader uses a flat layout (no
  `yourmod/` subfolder unless you explicitly put one in `classpath=`).
- Jar is corrupt or empty — `jar tf yourmod.jar` should list your
  class.

### Symptom: mod loads but `@SubscribeEvent` methods never fire

Check, in order:

1. **Annotation present.** Missing `@SubscribeEvent` = silent no-op.
2. **Method is `public` and takes exactly one parameter** that extends
   `Event`.
3. **The event actually fires.** `ClientInitEvent` always fires;
   `ClientWorldLoadedEvent` only fires after you log into a server;
   `ServerInfoAvailableEvent` only fires on WML-enabled servers.
4. **The descriptor points at the class with your handlers.** The
   framework only auto-registers the class in `classname=`. Handlers
   on other classes need to be registered by you.

See [`./lifecycle-events.md`](./lifecycle-events.md) for which event
fires when.

---

## Build problems

### `archiveVersion.set("")` missing

Same rule as server: the jar filename must be exactly what the
`.properties` `classpath=` points at. A `-1.0.0` suffix breaks it.

### Using `implementation` instead of `compileOnly`

Wurm's `client.jar` / `common.jar` and the client API must be
`compileOnly`. Shipping copies inside your mod jar causes
`LinkageError` or `NoClassDefFoundError` at runtime because the mod
classloader sees two copies of the same class.

Correct template (from
[`examples/hellomod/build.gradle.kts`](../../examples/hellomod/build.gradle.kts)):

```kotlin
dependencies {
    compileOnly(project(":wurmmodloader-client-api"))
    compileOnly(files("$wurm/client.jar"))
    compileOnly(files("$wurm/common.jar"))
}
```

### Wrong Java target

Wurm runs Java 8 bytecode. Java 17 toolchain is fine for building, but
`sourceCompatibility`/`targetCompatibility` must be 1.8 or you'll hit
`UnsupportedClassVersionError` at runtime.

---

## Patcher / `client.jar` issues

### Patched client won't start

- Wurm updated since you last patched. Re-run the patcher — stale
  patches target classes that don't exist.
- The patcher log (stdout when you ran it) will show `NotFoundException`
  on a vanilla class. That's your patch target being renamed upstream.
- Keep a backup of the unpatched `client.jar` — if the patcher corrupts
  it, you'll want to restore from the Steam cache (`steam://validate/366220`).

### A bytecode patch fails but others succeed

The patcher supports `--continue-on-patch-error` equivalent behavior —
failed patches are logged, others still apply. But any event that
depended on the failed patch will never fire. Grep the patcher output
for `Failed to apply` to see which.

---

## HUD / UI problems

### Window registered in `ClientInitEvent` doesn't appear

The HUD doesn't exist yet at `ClientInitEvent`. Register HUD
components from `ClientHUDInitializedEvent`:

```java
@SubscribeEvent
public void onHUDInit(ClientHUDInitializedEvent event) {
    MyWindow win = new MyWindow(event.getWorld(),
                                event.getScreenWidth(),
                                event.getScreenHeight());
    event.getMainMenu().registerComponent("My Mod", win);
    ((HeadsUpDisplay) event.getHud()).addComponent(win);
}
```

See [`./lifecycle-events.md`](./lifecycle-events.md) for the full
event timeline.

### Window appears but position/size is wrong

- Layout API constraints are resolved top-down. A child asking for
  `LayoutHints.fill()` under a parent with no defined size gets zero.
  Give the root window an explicit size or pin it to screen edges
  via `ModBorderPanel`.
- Pixel math against a stale `ComponentRenderEvent` size. Screen resize
  fires the event; don't cache dimensions across renders.

See [`./ui-layout.md`](./ui-layout.md) for layout API semantics.

### `ModHud`-registered window vanishes on world reload

You registered it inline (each `ClientHUDInitializedEvent` creates a
new one). Use `ModHud.registerPersistent(...)` so the same window
instance survives reloads and remembers its saved position.

---

## ModComm / server-side integration issues

### `ServerInfoAvailableEvent` never fires

- The server isn't running WurmModLoader, or the WML version predates
  the `wml.serverinfo` channel. Read the HTTP URI from
  `ServerInfoRegistry.getHttpUri()` after a delay; if it's still null,
  the server isn't advertising.
- Your mod's `ModComm` channel wasn't registered before
  `ClientWorldLoadedEvent`. Register channels in `ClientInitEvent`.

### `ServerCapabilitiesReceivedEvent` fires with empty list

The server is running WML but has no server-side mods that declare
capabilities. This is normal for vanilla-adjacent servers — your
client mod should gracefully degrade (hide features that need server
support) rather than crashing.

---

## Render-loop problems

### `ClientTickEvent` tanks FPS

This event fires 60+ times per second. Common offenders:

- **Allocation in the hot path** — `new ArrayList<>()` per frame is
  garbage-collector fuel.
- **Synchronous I/O** — HTTP, file reads, anything blocking.
- **Per-frame logging** at `INFO`. Move to `FINE` or gate behind a
  debug flag.
- **Expensive computation without accumulator gating.** Use the
  accumulator pattern from [`./lifecycle-events.md`](./lifecycle-events.md)
  if the work only needs to happen every N seconds.

### Mouse/input events don't fire

GUI events (`MouseClickEvent`, `MouseDragEvent`, `MouseScrollEvent`)
fire from the client's input dispatch, which only runs while the
client has focus. If you're alt-tabbed out, they don't fire — that's
expected, not a bug.

---

## Classloader problems

### `ClassCastException` between your mod and Wurm types

Your mod has `sharedClassLoader=false` and you're passing a
`com.wurmonline.*` object to code outside your isolated loader. Set
`sharedClassLoader=true` unless you have a specific reason for
isolation.

### Two client mods can't see each other's types

Each mod has its own classloader. Cross-mod type sharing is intentional
hard — use events or a third mod with shared API types that both
depend on. For casual cross-mod coordination, prefer events over
direct references.

---

## Legacy (Ago) client mods

### Legacy mod doesn't load

The legacy bridge lives in `wurmmodloader-client-legacy/`. It wraps
old `WurmClientMod`-style mods. If a legacy mod doesn't load:

- Check whether the bridge even saw it (`grep legacy` in the client log).
- Legacy mods with their own bytecode patches against vanilla client
  classes may conflict with WML's patches. In that case, port the mod
  to the new API — see [`../../README.md`](../../README.md) for the
  legacy bridge scope.

---

## Quick log patterns

| Symptom in log | Likely cause |
|---|---|
| `ClassNotFoundException` on your mod class | `.properties` wrong or jar not deployed |
| `NoClassDefFoundError` on `com.wurmonline.*` | you launched unpatched `client.jar`, or `compileOnly` leak |
| `UnsupportedClassVersionError` | mod compiled for wrong Java target |
| `LinkageError: loader constraint violated` | duplicate Wurm classes shipped in your jar |
| Silent handler no-op | missing `@SubscribeEvent` / wrong method signature |
| FPS drop after mod loads | allocation or I/O in `ClientTickEvent` |
| HUD window missing | registered too early (use `ClientHUDInitializedEvent`) |

---

## When you really can't figure it out

1. Tail the console log from cold boot: `tail -f ~/.../WurmLauncher/console.*.log`,
   then launch the client. First error wins.
2. Reproduce against [`examples/hellomod/`](../../examples/hellomod/) —
   if hellomod also breaks, your patcher/install is broken.
3. Compare your build + descriptor against the real reference:
   [`mods/livemap/`](../../mods/livemap/). Livemap exercises HUD
   registration, `ModComm`, `ServerInfoRegistry`, and the layout API —
   if your issue is in any of those, livemap's code is the
   ground-truth example.

---

## See also

- [`../getting-started/index.md`](../getting-started/index.md) — client onramp + hello-mod walkthrough
- [`./lifecycle-events.md`](./lifecycle-events.md) — event timeline (what fires when)
- [`./ui-layout.md`](./ui-layout.md) — layout API semantics
- [`../../INSTALLATION.md`](../../INSTALLATION.md), [`../../PATCHER.md`](../../PATCHER.md) — first-time setup + patcher internals
- Server side: [`../../../WurmModLoader/docs/guides/troubleshooting.md`](../../../WurmModLoader/docs/guides/troubleshooting.md)
