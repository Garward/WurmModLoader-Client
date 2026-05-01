# WurmModLoader Client Patcher

How the client gets modloader hooks injected, and how to add a new bytecode patch.

## Two modes — pick one

`ClientPatcher` (in `wurmmodloader-client-patcher`) is built so the same uber-JAR can run two ways:

| Mode | When | How |
|---|---|---|
| **Standalone** (canonical, shipping path) | Every end-user install. Bakes hooks + access-widening + cross-pack resolver into `client.jar` on disk so Steam "Play" launches a patched client with no JVM args. | `distribution/scripts/patch-client.sh` / `patch-client.bat` |
| **Java Agent** | Dev iteration only — re-patch on every launch without rewriting the JAR. | `java -javaagent:wurmmodloader-client-0.4.0.jar -jar client.jar` |

Standalone mode is what users get. The agent mode is wired up because both entry points share the same patch list, but **don't** point users at it — Steam doesn't pass `-javaagent` and it's just one more thing to break.

The standalone patcher is idempotent and keeps a `client.jar.backup` next to the patched copy. To roll back, restore the backup or have Steam verify-files.

## What gets patched

Patches live in `wurmmodloader-client-core/.../core/bytecode/patches/`. As of v0.4.0:

**Lifecycle / tick / input**
- `ClientInitPatch`, `ClientTickPatch`, `ClientPlayerTickFramePatch`, `ClientNpcGameTickPatch`
- `ClientMovementKeyPressedPatch`, `ConsoleInputPatch`, `WorldMapTogglePatch`, `FOVChangePatch`
- `ClientAuthoritativePlayerPositionPatch`, `WorldRenderPatch`, `PickRenderPatch`

**Server connection / ModComm**
- `SimpleServerConnectionModCommPatch` — opens the ModComm channel
- `ServerConnectionStaminaPatch`, `ServerConnectionTextMessagePatch`
- `DeedPlanPacketPatch`

**GUI**
- `gui/WurmComponentAccessPatch`, `gui/FlexComponentAccessPatch` — base widget access widening
- `gui/GuiClassWideningPatch` — drains the `CorePatches.GUI_CLASS_WIDENINGS` list (one Javassist run per class — add a class there to widen its fields/ctors for mod use)
- `HeadsUpDisplayInitPatch`, `WurmPopupRebindPatch`, `CompassComponentPatch`
- `PlayerActionNamePatch`, `IsDevOverridePatch`

**Audio**
- `OggInputStreamPathLoggingPatch`, `SoundPlayLoggingPatch`, `SoundResourceLoggingPatch` — diagnostics + missingsound replacement (vanilla `res/missingsound.ogg` is CRC-corrupt)

**Hover / inspection**
- `TilePickerHoverNamePatch`, `CaveWallPickerHoverNamePatch`, `CreatureHoverDescriptionPatch`
- `CellRenderableLifecyclePatch`

**Server packs**
- `serverpacks/...` — bakes the cross-classloader `PackAssetResolver` lookup into the client so mod-loaded packs resolve at the framework, not the mod's URLClassLoader.

For an exact list, run:

```bash
ls wurmmodloader-client-core/src/main/java/com/garward/wurmmodloader/client/core/bytecode/patches/
```

## Adding a new patch (standalone path)

The standalone patcher and the agent share the same registration list, but **the canonical shipped path is the standalone patcher's `patchJarFile()` method** — that's what users run. If you forget to wire your patch in there, it won't apply at install time.

### 1. Write the patch

```java
package com.garward.wurmmodloader.client.core.bytecode.patches;

import com.garward.wurmmodloader.client.api.bytecode.BytecodePatch;
import javassist.CtClass;
import javassist.CtMethod;

public class MyCustomPatch implements BytecodePatch {
    @Override public String getTargetClassName() { return "com.wurmonline.client.SomeClass"; }
    @Override public int getPriority() { return 100; }
    @Override public String getDescription() { return "Hook for SomeClass.someMethod"; }

    @Override
    public void apply(CtClass ctClass) throws Exception {
        CtMethod method = ctClass.getDeclaredMethod("someMethod");
        method.insertAfter(
            "com.garward.wurmmodloader.client.modloader.ProxyClientHook.fireMyCustomEvent();"
        );
    }
}
```

### 2. Add the event + hook plumbing

- **Event** in `wurmmodloader-client-api/.../api/events/...` — extends `Event`, immutable fields
- **ProxyClientHook** static entry point — name **must** end in `Event`
- **ClientHook** instance method — name does **not** end in `Event`, calls `eventBus.post(new MyCustomEvent(...))`

### 3. Wire into the standalone patcher

Edit `ClientPatcher.patchJarFile(...)` — find the `addPatch(...)` block and add yours alongside the others:

```java
addPatch(corePatches, "com/wurmonline/client/SomeClass.class", new MyCustomPatch());
```

For agent mode, also register in `CorePatches.registerAll()` if you want it picked up when running with `-javaagent`.

### 4. Rebuild + reinstall

```bash
./gradlew build
# then in distribution/, run:
./scripts/patch-client.sh
```

The patcher prints `Patching <entry> (N patch(es))…` for each class it touches. If you don't see your class, the `addPatch` line didn't land or the entry path is wrong.

## GUI access widening

Don't write a one-off patch to make a Wurm widget field public. Add the class to `CorePatches.GUI_CLASS_WIDENINGS` and `GuiClassWideningPatch` will:

- strip `private`/`final` from instance fields
- emit a public no-arg constructor if the class only has package-private ones
- leave methods and static state alone

This is the path mods use to subclass / reflect into vanilla widgets. See `docs/guides/widening-and-guiaccess.md`.

## Debugging

Patcher logs to `stderr` (standalone) or the JUL `wurmmodloader.client.patcher.ClientPatcher` logger (agent). Common things to check:

| Symptom | Cause |
|---|---|
| `No patches registered` (agent mode) | `premain` didn't run — `-javaagent` wasn't passed, or came after `-jar` |
| `Failed to patch <class>` | Wrong target class name, or method signature changed across a Wurm update |
| Patched class crashes the client | Syntax error in the injected source; check the Javassist exception trace |
| Standalone patcher exits clean but nothing happens at runtime | `client.jar` wasn't actually rewritten — check the `.backup` timestamp and that you ran the script against the right install |

## Why Javassist (and not ASM)

Javassist takes a source-level fragment (`method.insertAfter("foo();")`) and compiles it for you. ASM is faster and more powerful but every patch becomes a visitor — Javassist keeps the patch list readable and small, which matters because there are 30+ of them and they need to survive Wurm updates without a domain-expert audit.

The shadow build relocates Javassist into `wurmmodloader.javassist` so it can't collide with anything a mod ships.

## Rollback

If a patched client misbehaves:

1. Restore `client.jar.backup` (the standalone patcher writes one) — or have Steam "Verify integrity of game files".
2. Launch through Steam normally; the modloader is no longer present.

No registry / config / save-data side effects — the patcher only rewrites classes inside `client.jar`.

---

**Last updated**: 2026-05-01
**Version**: 0.4.0
