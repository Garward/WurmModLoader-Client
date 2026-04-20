# Legacy Client-Mod Compatibility

Ago's original `WurmClientModLauncher` exposed a `WurmClientMod`
interface. This framework can still load those — but in practice,
almost nobody will ever need this page.

## The landscape, honestly

Client mods were rare even at the peak of Ago-era modding. Writing one
meant manual bytecode patches against obfuscated client internals,
with no event system and no GUI helpers. Ago wrote most of the client
mods that ever shipped; the community ecosystem that exists around
server mods never really materialized for the client.

So when you're porting, you're almost certainly porting:

- A mod **you** wrote
- A mod **Ago** wrote that you want to keep running
- One of a handful of community client mods (config loaders, chat
  tweaks, camera fiddles)

If that's you, keep reading. Otherwise just write a modern
`@SubscribeEvent`-based mod and move on.

---

## What the bridge supports

`wurmmodloader-client-legacy/` ships the two interfaces needed to
compile an Ago-style mod:

| Interface | Package | What it does |
|---|---|---|
| `WurmClientMod` | `org.gotti.wurmunlimited.modloader.interfaces` | `preInit()` / `init()` lifecycle hooks |
| `Versioned` | `org.gotti.wurmunlimited.modloader.interfaces` | `getVersion()` default reads `Implementation-Version` from jar manifest |

Both have default method bodies — you only override the ones you need.

That's the entire API surface. The server-side legacy bridge is
chunky because Ago's server modloader had a dozen listener interfaces;
client-side there were only two.

---

## Running an unmodified Ago client mod

### 1. Drop it into `WurmLauncher/mods/`

Same layout as a modern mod — a `.jar` plus a `.properties` descriptor
(or the jar alone, in which case the loader scans it for
`WurmClientMod` implementations).

Typical descriptor (unchanged from Ago):

```properties
classname=net.example.oldclientmod.Main
classpath=oldmod.jar
sharedClassLoader=true
```

### 2. Launch the patched client

`LegacyModLoader` scans `mods/` at startup, finds any
`WurmClientMod` implementation (via the descriptor's `classname=` or by
jar scan), loads it in its own `URLClassLoader`, then calls `preInit()`
on everything followed by `init()` on everything.

### 3. Tail the log

```
Loading LEGACY mods from mods/ directory...
(Using Ago's WurmClientMod interface)
  preInit: net.example.oldclientmod.Main v1.2.0
  init:    net.example.oldclientmod.Main
Loaded 1 legacy mod(s) successfully
```

If it doesn't show up, jump to
[`troubleshooting.md`](./troubleshooting.md) — the usual suspects
(deploy, jar filename, descriptor path) apply unchanged.

---

## The "legacy + modern" mixing rule

**Don't.** Legacy mods do their own Javassist patches against vanilla
client classes; the modern framework installs its own patches from
`CoreBytecodePatches` and friends. When both target the same class:

- Whichever runs second either fails with `cannot modify frozen class`,
  or silently overwrites the other's patch.
- Debugging is miserable because the log tells you *both* applied
  successfully right before the behavior goes sideways.

If you're running a legacy mod, run it **alone** or alongside modern
mods that don't touch the same vanilla classes it does. The safest
posture: port the legacy mod instead of mixing.

---

## Porting a legacy client mod to the modern API

Usually a one-evening job because Ago client mods are small.

### The interface swap

```java
// Before
public class OldMod implements WurmClientMod {
    @Override public void preInit() { /* Javassist patches */ }
    @Override public void init()    { /* post-init wiring */ }
    @Override public String getVersion() { return "1.0"; }
}

// After
public class NewMod {
    @SubscribeEvent
    public void onClientInit(ClientInitEvent event) {
        // everything that used to be in init() goes here
    }
}
```

The descriptor loses its `classpath=` subfolder requirement
(client descriptors are flat) but is otherwise unchanged.

### Replacing Javassist with events — first, check the catalog

Before rewriting a patch as an event, see if the event already exists.
[`./lifecycle-events.md`](./lifecycle-events.md) lists every
`com.garward.wurmmodloader.client.api.events.*` event, grouped by
subsystem (lifecycle, GUI, movement, combat, map, etc.). If your patch
was hooking player update, HUD init, a mouse click, or a server
packet, there's probably already an event for it.

If there isn't — and your patch is simple — check whether the modern
framework has an API for what you actually want. The layout API
replaces most custom GUI bytecode; `ServerInfoRegistry` / `ModComm`
replace hand-rolled server integration. See
[`./ui-layout.md`](./ui-layout.md) and
[`./client-server-bridge.md`](./client-server-bridge.md).

### If you still need a bytecode patch

Nothing stops you from calling Javassist yourself from a handler —
but at that point, file an issue / open a PR to add a proper event
to the framework. Client-side bytecode patches are centrally owned
(`wurmmodloader-client-core/.../bytecode/patches/`); the long-term
home for a new patch is there, not inside a mod.

---

## Quick decision table

| Situation | Do this |
|---|---|
| I'm writing a new client mod | Modern API. Don't look at this page again. |
| I have one Ago client mod and it works | Drop it in `mods/`, test, move on. |
| I have an Ago client mod + modern mods that patch the same classes | Port the Ago mod. Not worth debugging the conflict. |
| I have an Ago client mod I want to keep developing | Port it. The modern API is better on every axis and most Ago client mods are <500 LOC. |

---

## See also

- [`../getting-started/index.md`](../getting-started/index.md) — what a modern client mod looks like
- [`./lifecycle-events.md`](./lifecycle-events.md) — event catalog (check before writing a patch)
- [`./troubleshooting.md`](./troubleshooting.md) — when the bridge doesn't load your mod
- `wurmmodloader-client-legacy/` source — the whole bridge is two interfaces + one loader class; readable end-to-end in under ten minutes
- Server-side equivalent:
  [`../../../WurmModLoader/docs/guides/legacy-mod-compatibility.md`](../../../WurmModLoader/docs/guides/legacy-mod-compatibility.md)
