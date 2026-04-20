# Widening — Patching a Vanilla Widget That Isn't Public Yet

WU's `com.wurmonline.client.renderer.gui` package declares almost every
widget — classes, fields, methods, even constructors — as
**package-private**. Wurm's author never intended modders to touch the
GUI, so it isn't exposed. That one decision is the single biggest
reason Ago-era client mods were rare: you either burned a week on
reflection hell or wrote a Javassist patch per field you wanted to
read.

This framework flips most of the GUI toolkit to `public` via bytecode
patches baked into `client.jar` at patch time. **Mods then
compile against the widened jar like any normal Java API.** This page
explains how widening works and what to do when you hit a widget that
isn't widened yet.

> Background context — see also the
> [`WurmModLoader-Client/GUI_FRAMEWORK.md`](../../GUI_FRAMEWORK.md)
> architecture doc for the rendering pipeline. This page is only about
> the *access-control* aspect.

---

## What's already widened

Three patches cover the current widened surface:

| Patch | Scope |
|---|---|
| `GuiClassWideningPatch` (data-driven, one per class) | Entire class + every constructor, method, and field — but **only if currently package-private**. Private and protected members are left alone. |
| `WurmComponentAccessPatch` | Named members on `WurmComponent` (the root): fields `x`, `y`, `hud`; methods `leftPressed`, `rightPressed`, `leftReleased`, `rightReleased`, `mouseDragged`, `mouseWheeled`. |
| `FlexComponentAccessPatch` | `FlexComponent` — class, all constructors, the `sizeFlags` / `FIXED_WIDTH` / `FIXED_HEIGHT` fields. |

Currently widened via `GuiClassWideningPatch[]` in
[`CorePatches.java`](../../wurmmodloader-client-core/src/main/java/com/garward/wurmmodloader/client/core/bytecode/CorePatches.java)
(the source of truth):

- `WWindow`
- `WButton`, `WTextureButton`
- `WurmBorderPanel`, `WurmArrayPanel`
- `ButtonListener`
- `ContainerComponent`
- `HeadsUpDisplay`

Plus the two custom patches on `WurmComponent` and `FlexComponent`.

If you're extending / subclassing / poking one of those — you can
write plain Java. No reflection, no patches, no
`setAccessible(true)`.

---

## When you hit "X is not public"

You're writing a mod, you type
`new com.wurmonline.client.renderer.gui.SomeWidget()`, and Java tells
you `SomeWidget is not public in com.wurmonline.client.renderer.gui`.

You have three options, in order of preference:

### 1. Use the layout API if you can

First check whether you actually need the vanilla widget. The
framework's own GUI layout API (`ModStackPanel`, `ModBorderPanel`,
`ModImageButton`, etc.) replaces most reasons to touch vanilla
widgets directly. See [`./ui-layout.md`](./ui-layout.md). If the
layout API covers your case, stop here.

### 2. Add the class to `GUI_CLASS_WIDENINGS`

If you need the vanilla widget itself (subclassing, constructing it
directly, calling one of its methods), the fix is to widen it. Four
steps:

```bash
# 1. Append one line to CorePatches.GUI_CLASS_WIDENINGS[]
#    in wurmmodloader-client-core/.../bytecode/CorePatches.java:
#      new GuiClassWideningPatch("com.wurmonline.client.renderer.gui.SomeWidget"),

# 2. Rebuild the patcher
./gradlew :wurmmodloader-client-patcher:shadowJar

# 3. Restore client.jar from backup
cp "$WURM/client.jar.backup" "$WURM/client.jar"

# 4. Re-run the patcher
java -jar wurmmodloader-client-patcher/build/libs/wurmmodloader-client-0.1.0.jar
```

`ClientPatcher.isAlreadyPatched()` short-circuits re-runs — the
restore-from-backup in step 3 is non-optional. If you forget, the
patcher sees the file is already patched and exits without applying
your new target.

Verify it worked:

```bash
javap -p -cp "$WURM/client.jar" \
  com.wurmonline.client.renderer.gui.SomeWidget | head -3
# Expect:  public class com.wurmonline.client.renderer.gui.SomeWidget
```

Then `./gradlew build` on your mod and the compile error goes away.

### 3. Reflection fallback (for private / protected members)

`GuiClassWideningPatch` only widens *package-private* members. A
field declared `private` or `protected` in vanilla is left alone —
widening everything would change the game's internal ABI too
aggressively and break subclasses.

For private members, use reflection. The framework has a centralized
pattern for this — see
[`GuiAccess.java`](../../wurmmodloader-client-api/src/main/java/com/garward/wurmmodloader/client/api/gui/GuiAccess.java)
— cache the `Field` / `Method` once at class-init, funnel every call
site through the helper:

```java
final class GuiAccess {
    private static final Field WIDTH_FIELD = lookupField("width");
    private static final Field HEIGHT_FIELD = lookupField("height");

    static int getWidth(WurmComponent c)  { return readInt(WIDTH_FIELD, c); }
    static int getHeight(WurmComponent c) { return readInt(HEIGHT_FIELD, c); }

    private static Field lookupField(String name) {
        try {
            Field f = WurmComponent.class.getDeclaredField(name);
            f.setAccessible(true);
            return f;
        } catch (NoSuchFieldException e) { return null; }
    }
    // ...
}
```

The pattern:
- `private static final Field` cached once at class-load
- `setAccessible(true)` once
- Null-safe lookups (return 0 / null if the field is gone in a future
  WU update rather than crash)
- One helper class per widget; don't scatter reflection through your
  code

If you find yourself writing this pattern for a lot of members of the
same class, consider writing a proper widening patch for it instead.

---

## Why not just widen everything?

A few reasons widening is per-class and per-visibility-level:

- **ABI stability.** Flipping protected → public changes Java method
  resolution for subclasses inside Wurm itself. `private` → `public`
  exposes fields the game's author deliberately hid (often because
  they mutate state that should only be touched by Wurm code).
- **Surface area.** Every widened class is framework maintenance
  surface. If Wurm renames or removes a method, the patch fails and
  every dependent mod breaks.
- **Opinionated exposure.** `WurmComponentAccessPatch` names specific
  fields because `WurmComponent` has many deliberately-private
  members that mods shouldn't touch.

The result: widening is additive. Hit a missing class, append to the
list, rebuild, done. Don't ask to widen private members without a
reason.

---

## Adding a new custom patch (rare)

If you need something beyond "flip package-private to public on this
class" — e.g., you need to widen exactly two methods on `SomeWidget`
but leave everything else alone — write a custom patch alongside
`WurmComponentAccessPatch` and `FlexComponentAccessPatch`:

- `wurmmodloader-client-core/.../bytecode/patches/gui/` — drop a new
  class implementing `BytecodePatch`
- Register it in `CorePatches.registerAll()` next to the others
- Rebuild patcher + restore + re-run as above

99% of the time the data-driven `GuiClassWideningPatch` is enough.
Custom patches are for the cases where it isn't.

---

## Gotchas

- **Restore-from-backup is required between patcher runs.** The
  idempotence check on `client.jar` will skip any additions you made
  if you forget. Keep `client.jar.backup` safe.
- **Subclassing a widened class across a Wurm update can break.** When
  WU updates, re-run the patcher. Mods compiled against old widened
  signatures may need a recompile if fields were renamed upstream.
- **`GuiAccess`-style reflection survives updates better than patches**
  for fields whose *values* you just want to read. Patches survive
  better when you're constructing or subclassing. Pick per use case.
- **Adding to `GUI_CLASS_WIDENINGS` doesn't affect runtime behavior** —
  no new events fire, no game logic changes. It only changes access
  modifiers. Entirely safe to append to.

---

## See also

- [`./ui-layout.md`](./ui-layout.md) — the layout API that replaces most reasons to touch vanilla widgets
- [`./troubleshooting.md`](./troubleshooting.md) — "mod compiles locally but fails at runtime after a WU update" usually means re-run the patcher
- [`../../GUI_FRAMEWORK.md`](../../GUI_FRAMEWORK.md) — client GUI rendering pipeline architecture
- [`../../PATCHER.md`](../../PATCHER.md) — how `ClientPatcher` works under the hood
- Source:
  [`CorePatches.java`](../../wurmmodloader-client-core/src/main/java/com/garward/wurmmodloader/client/core/bytecode/CorePatches.java) (the list),
  [`GuiClassWideningPatch.java`](../../wurmmodloader-client-core/src/main/java/com/garward/wurmmodloader/client/core/bytecode/patches/gui/GuiClassWideningPatch.java) (the data-driven patch),
  [`GuiAccess.java`](../../wurmmodloader-client-api/src/main/java/com/garward/wurmmodloader/client/api/gui/GuiAccess.java) (reflection fallback pattern)
