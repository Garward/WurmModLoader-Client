# Automine Client Mod Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship a client-side automining loop (popup window opens via console command, fires N mine actions, waits for stamina to refill, repeats; stops on tile-break server messages, Pause, or window close).

**Architecture:** Two reusable framework events (`ClientStaminaChangedEvent`, `ClientEventMessageReceivedEvent`) plus an extracted dispatcher (`PlayerActionDispatcher` eventlogic) land in the framework. The existing `action` mod migrates to the dispatcher. A new standalone `automine` mod consumes the events and dispatcher with no inter-mod dependency.

**Tech Stack:** Java 8 bytecode (Java 17 toolchain), Javassist 3.30, Gradle (Kotlin DSL), JUnit 5 (state machine tests), framework GUI widgets (`ModWindow`, `ModStackPanel`, `ModButton`, `ModLabel`).

**Spec:** `WurmModLoader-Client/docs/superpowers/specs/2026-04-27-automine-client-mod-design.md`

**Repos touched:**
- `~/Scripts/Games/WurmUnlimited/WurmModLoader-Client/` — framework: events, patches, dispatcher, version bump.
- `~/Scripts/Games/WurmUnlimited/WurmModLoader-CommunityMods/` — `action` mod migration, new `automine` mod.

---

## File Structure

**Framework (`WurmModLoader-Client`):**
- Create `wurmmodloader-client-api/.../events/eventlogic/action/PlayerActionDispatcher.java` — public static dispatch entry + target-keyword switch.
- Create `wurmmodloader-client-api/.../events/eventlogic/action/ClientItemReflect.java` — reflective accessors used by the dispatcher.
- Create `wurmmodloader-client-api/.../events/eventlogic/action/package-info.java` — module javadoc.
- Create `wurmmodloader-client-api/.../events/client/ClientStaminaChangedEvent.java`.
- Create `wurmmodloader-client-api/.../events/client/ClientEventMessageReceivedEvent.java`.
- Create `wurmmodloader-client-core/.../bytecode/patches/ServerConnectionStaminaPatch.java`.
- Create `wurmmodloader-client-core/.../bytecode/patches/ServerConnectionTextMessagePatch.java`.
- Modify `wurmmodloader-client-core/.../modloader/ClientHook.java` — add `fireClientStaminaChanged`, `fireClientEventMessage`, stamina cache.
- Modify `wurmmodloader-client-core/.../modloader/ProxyClientHook.java` — add `fireClientStaminaChangedEvent`, `fireClientEventMessageReceivedEvent`, `getCurrentStamina()`.
- Modify `wurmmodloader-client-patcher/.../ClientPatcher.java` — register the two new patches.

**Mod migration (`WurmModLoader-CommunityMods/client-mods/action`):**
- Modify `src/main/java/com/garward/wurmmodloader/mods/action/ActionClientMod.java` — replace `parseAct` body with delegation to `PlayerActionDispatcher.dispatch`.
- Delete `src/main/java/com/garward/wurmmodloader/mods/action/Reflect.java` (after confirming dispatcher covers all callers).

**New mod (`WurmModLoader-CommunityMods/client-mods/automine`):**
- Create `client-mods/automine/build.gradle.kts`.
- Create `client-mods/automine/src/dist/mod.properties`.
- Create `client-mods/automine/src/dist/automine.properties`.
- Create `client-mods/automine/src/main/java/com/garward/wurmmodloader/mods/automine/AutomineClientMod.java`.
- Create `client-mods/automine/src/main/java/com/garward/wurmmodloader/mods/automine/AutomineWindow.java`.
- Create `client-mods/automine/src/main/java/com/garward/wurmmodloader/mods/automine/AutomineState.java`.
- Create `client-mods/automine/src/main/java/com/garward/wurmmodloader/mods/automine/AutomineConfig.java`.
- Create `client-mods/automine/src/test/java/com/garward/wurmmodloader/mods/automine/AutomineStateTest.java`.
- Modify `WurmModLoader-CommunityMods/settings.gradle.kts` — register the new module.

**Live-test phrase research artifact:**
- Create `WurmModLoader-Client/docs/research/mining-stop-phrases.md` — one-time finding doc; lists exact server strings emitted on mining errors / breakthroughs, with citation lines into the decompiled server source.

---

## Phase 1 — Framework: extract `PlayerActionDispatcher`

### Task 1: Create `ClientItemReflect` and `PlayerActionDispatcher`

**Files:**
- Create: `~/Scripts/Games/WurmUnlimited/WurmModLoader-Client/wurmmodloader-client-api/src/main/java/com/garward/wurmmodloader/client/api/events/eventlogic/action/package-info.java`
- Create: `~/Scripts/Games/WurmUnlimited/WurmModLoader-Client/wurmmodloader-client-api/src/main/java/com/garward/wurmmodloader/client/api/events/eventlogic/action/ClientItemReflect.java`
- Create: `~/Scripts/Games/WurmUnlimited/WurmModLoader-Client/wurmmodloader-client-api/src/main/java/com/garward/wurmmodloader/client/api/events/eventlogic/action/PlayerActionDispatcher.java`
- Reference: existing `~/Scripts/Games/WurmUnlimited/WurmModLoader-CommunityMods/client-mods/action/src/main/java/com/garward/wurmmodloader/mods/action/Reflect.java` and `ActionClientMod.parseAct`.

- [ ] **Step 1: Create `package-info.java`**

```java
/**
 * Reusable client-side action-dispatch utilities. Call
 * {@link com.garward.wurmmodloader.client.api.events.eventlogic.action.PlayerActionDispatcher#dispatch}
 * from any mod that needs to send a {@code PlayerAction} against a target
 * keyword (hover, tile, tool, body, selected, area, tile_n…sw, toolbelt,
 * @tbN, @eqN, @nearbyR). The original implementation lived in
 * {@code com.garward.wurmmodloader.mods.action.ActionClientMod#parseAct} and
 * was lifted to the api so other mods (automine, future autochop, …) can
 * dispatch without depending on the action mod.
 *
 * @since 0.4.0
 */
package com.garward.wurmmodloader.client.api.events.eventlogic.action;
```

- [ ] **Step 2: Create `ClientItemReflect.java`**

Copy the full body of `Reflect.java` (used by the action mod) into this new class. Inline path: `~/Scripts/Games/WurmUnlimited/WurmModLoader-CommunityMods/client-mods/action/src/main/java/com/garward/wurmmodloader/mods/action/Reflect.java`. Adjust:

1. Package: `com.garward.wurmmodloader.client.api.events.eventlogic.action`.
2. Class name: `ClientItemReflect`.
3. All public-static methods unchanged (`setup`, `getBodyItem`, `getActiveToolItem`, `getSelectedUnit`, `getGroundItems`, `getFrameFromSlotnumber`).
4. Class-level Javadoc: state that this is the framework-public extraction and that mods should depend on this rather than copying Reflect helpers.

- [ ] **Step 3: Create `PlayerActionDispatcher.java`**

```java
package com.garward.wurmmodloader.client.api.events.eventlogic.action;

import com.wurmonline.client.comm.ServerConnectionListenerClass;
import com.wurmonline.client.game.inventory.InventoryMetaItem;
import com.wurmonline.client.renderer.PickableUnit;
import com.wurmonline.client.renderer.cell.CellRenderable;
import com.wurmonline.client.renderer.cell.CreatureCellRenderable;
import com.wurmonline.client.renderer.cell.GroundItemCellRenderable;
import com.wurmonline.client.renderer.gui.HeadsUpDisplay;
import com.wurmonline.client.renderer.gui.PaperDollSlot;
import com.wurmonline.mesh.Tiles;
import com.wurmonline.shared.constants.PlayerAction;

import java.util.Collection;
import java.util.stream.Stream;

/**
 * Reusable dispatcher for {@link PlayerAction} against the standard target
 * keywords. Lifted from the action mod so any client mod can invoke the
 * same dispatch path without an inter-mod dependency.
 *
 * <p>Target keywords (all lowercase):
 * <ul>
 *   <li>{@code hover} — currently hovered pickable unit.</li>
 *   <li>{@code body} — the player's body item (paper-doll slot).</li>
 *   <li>{@code tile} — the tile under the player.</li>
 *   <li>{@code tile_n / s / e / w / ne / nw / se / sw} — neighbouring tile.</li>
 *   <li>{@code tool} — the currently active toolbelt item.</li>
 *   <li>{@code selected} — the active select-bar unit.</li>
 *   <li>{@code area} — every tile in a 3x3 around the player.</li>
 *   <li>{@code toolbelt} — switch active tool slot to id (1-10).</li>
 *   <li>{@code @tbN} — fire on the item in toolbelt slot N (1-10).</li>
 *   <li>{@code @eqN} — fire on the equipped item in paper-doll slot N.</li>
 *   <li>{@code @nearbyR} — fire on every ground item / creature within R metres.</li>
 * </ul>
 *
 * <p>Always call {@link ClientItemReflect#setup()} once after the HUD has
 * initialized before invoking {@link #dispatch}.
 *
 * @since 0.4.0
 */
public final class PlayerActionDispatcher {

    private PlayerActionDispatcher() {}

    /**
     * Send {@code actionId} against {@code target}. Writes a console message
     * to the HUD on bad target / slot. Throws on reflection failure (mods
     * should log and stop).
     */
    public static void dispatch(HeadsUpDisplay hud, short actionId, String target)
            throws ReflectiveOperationException {
        if (hud == null) return;
        PlayerAction act = new PlayerAction(actionId, PlayerAction.ANYTHING, "", false);
        switch (target) {
            case "hover":
                hud.getWorld().sendHoveredAction(act);
                break;
            case "body": {
                InventoryMetaItem body = ClientItemReflect.getBodyItem(hud.getPaperDollInventory());
                if (body != null) hud.sendAction(act, body.getId());
                break;
            }
            case "tile":
                hud.getWorld().sendLocalAction(act);
                break;
            case "tile_n": sendLocal(hud, act, 0, -1); break;
            case "tile_s": sendLocal(hud, act, 0, 1);  break;
            case "tile_e": sendLocal(hud, act, 1, 0);  break;
            case "tile_w": sendLocal(hud, act, -1, 0); break;
            case "tile_ne": sendLocal(hud, act, 1, -1); break;
            case "tile_nw": sendLocal(hud, act, -1, -1); break;
            case "tile_se": sendLocal(hud, act, 1, 1); break;
            case "tile_sw": sendLocal(hud, act, -1, 1); break;
            case "tool": {
                InventoryMetaItem t = ClientItemReflect.getActiveToolItem(hud);
                if (t != null) hud.sendAction(act, t.getId());
                else hud.consoleOutput("dispatch: tool target requires an active tool selected");
                break;
            }
            case "selected": {
                PickableUnit p = ClientItemReflect.getSelectedUnit(hud.getSelectBar());
                if (p != null) hud.sendAction(act, p.getId());
                break;
            }
            case "area":
                for (int dx = -1; dx <= 1; dx++)
                    for (int dy = -1; dy <= 1; dy++)
                        sendLocal(hud, act, dx, dy);
                break;
            case "toolbelt":
                if (actionId >= 1 && actionId <= 10) hud.setActiveTool(actionId - 1);
                else hud.consoleOutput("dispatch: invalid toolbelt slot '" + actionId + "'");
                break;
            default:
                if (target.startsWith("@tb")) {
                    int slot = Integer.parseInt(target.substring(3));
                    if (slot >= 1 && slot <= 10 && hud.getToolBelt().getItemInSlot(slot - 1) != null) {
                        hud.sendAction(act, hud.getToolBelt().getItemInSlot(slot - 1).getId());
                    } else {
                        hud.consoleOutput("dispatch: invalid toolbelt slot '" + slot + "'");
                    }
                } else if (target.startsWith("@eq")) {
                    byte slot = Byte.parseByte(target.substring(3));
                    PaperDollSlot obj = ClientItemReflect.getFrameFromSlotnumber(hud.getPaperDollInventory(), slot);
                    if (obj == null) {
                        hud.consoleOutput("dispatch: invalid equipment slot " + slot);
                    } else if (obj.getEquippedItem() == null) {
                        hud.consoleOutput("dispatch: no item in equipment slot " + slot);
                    } else {
                        hud.sendAction(act, obj.getEquippedItem().getId());
                    }
                } else if (target.startsWith("@nearby")) {
                    float range = Float.parseFloat(target.substring(7));
                    final float rangeSq = range * range;
                    ServerConnectionListenerClass conn =
                            hud.getWorld().getServerConnection().getServerConnectionListener();
                    Collection<GroundItemCellRenderable> items = ClientItemReflect.getGroundItems(conn).values();
                    Collection<CreatureCellRenderable> creatures = conn.getCreatures().values();
                    Stream.concat(items.stream(), creatures.stream())
                            .filter(x -> x.getSquaredLengthFromPlayer() < rangeSq)
                            .mapToLong(CellRenderable::getId)
                            .forEach(tid -> hud.sendAction(act, tid));
                } else {
                    hud.consoleOutput("dispatch: invalid target keyword '" + target + "'");
                }
        }
    }

    private static void sendLocal(HeadsUpDisplay hud, PlayerAction action, int xo, int yo) {
        int x = hud.getWorld().getPlayerCurrentTileX();
        int y = hud.getWorld().getPlayerCurrentTileY();
        hud.sendAction(action, Tiles.getTileId(x + xo, y + yo, 0));
    }
}
```

- [ ] **Step 4: Build the api module**

Run: `cd ~/Scripts/Games/WurmUnlimited/WurmModLoader-Client && ./gradlew :wurmmodloader-client-api:compileJava`
Expected: `BUILD SUCCESSFUL`. If it fails complaining about Wurm symbols, check that the api module's `build.gradle.kts` already has `compileOnly(files("$wurmClientDir/client.jar"))` — every existing eventlogic class compiles against client.jar already, so this should just work.

- [ ] **Step 5: Commit**

```bash
cd ~/Scripts/Games/WurmUnlimited/WurmModLoader-Client
git add wurmmodloader-client-api/src/main/java/com/garward/wurmmodloader/client/api/events/eventlogic/action/
git commit -m "feat(client-api): extract PlayerActionDispatcher to eventlogic"
```

---

## Phase 2 — Framework: `ClientStaminaChangedEvent`

### Task 2: Add the event class and patch

**Files:**
- Create: `~/Scripts/Games/WurmUnlimited/WurmModLoader-Client/wurmmodloader-client-api/src/main/java/com/garward/wurmmodloader/client/api/events/client/ClientStaminaChangedEvent.java`
- Create: `~/Scripts/Games/WurmUnlimited/WurmModLoader-Client/wurmmodloader-client-core/src/main/java/com/garward/wurmmodloader/client/core/bytecode/patches/ServerConnectionStaminaPatch.java`
- Modify: `~/Scripts/Games/WurmUnlimited/WurmModLoader-Client/wurmmodloader-client-core/src/main/java/com/garward/wurmmodloader/client/modloader/ClientHook.java`
- Modify: `~/Scripts/Games/WurmUnlimited/WurmModLoader-Client/wurmmodloader-client-core/src/main/java/com/garward/wurmmodloader/client/modloader/ProxyClientHook.java`
- Modify: `~/Scripts/Games/WurmUnlimited/WurmModLoader-Client/wurmmodloader-client-patcher/src/main/java/com/garward/wurmmodloader/client/patcher/ClientPatcher.java`

- [ ] **Step 1: Create `ClientStaminaChangedEvent.java`**

```java
package com.garward.wurmmodloader.client.api.events.client;

import com.garward.wurmmodloader.client.api.events.base.Event;

/**
 * Fires whenever the client receives a stamina update from the server
 * (CMD_STAMINA). Both values are in the 0.0–1.0 range vanilla uses internally.
 *
 * <p>The damage parameter the server also sends with this packet is ignored
 * — separate event if a future mod needs it.
 *
 * @since 0.4.0
 */
public class ClientStaminaChangedEvent extends Event {

    private final float oldStamina;
    private final float newStamina;

    public ClientStaminaChangedEvent(float oldStamina, float newStamina) {
        this.oldStamina = oldStamina;
        this.newStamina = newStamina;
    }

    /** Stamina before the update, 0.0–1.0. {@code Float.NaN} on the first event of a session. */
    public float getOldStamina() { return oldStamina; }

    /** Stamina after the update, 0.0–1.0. */
    public float getNewStamina() { return newStamina; }

    /** Convenience: did this update bring stamina to >= the threshold (e.g. "full")? */
    public boolean isAtLeast(float threshold) { return newStamina >= threshold; }

    @Override
    public String toString() {
        return "ClientStaminaChangedEvent{old=" + oldStamina + ", new=" + newStamina + "}";
    }
}
```

- [ ] **Step 2: Add hook plumbing — `ClientHook` instance method + stamina cache**

Open `wurmmodloader-client-core/src/main/java/com/garward/wurmmodloader/client/modloader/ClientHook.java`. Add the import and the new method + field. Insert after the existing `fireClientWorldLoaded` method (or any logical sibling location):

```java
import com.garward.wurmmodloader.client.api.events.client.ClientStaminaChangedEvent;

// …existing code…

private volatile float lastStamina = Float.NaN;

public void fireClientStaminaChanged(float newStamina) {
    float old = this.lastStamina;
    this.lastStamina = newStamina;
    postEvent(new ClientStaminaChangedEvent(old, newStamina));
}

public float getLastStamina() {
    return lastStamina;
}
```

- [ ] **Step 3: Add `ProxyClientHook` static entry + accessor**

In `ProxyClientHook.java`, add:

```java
public static void fireClientStaminaChangedEvent(float newStamina) {
    try { getInstance().fireClientStaminaChanged(newStamina); }
    catch (Throwable t) { t.printStackTrace(); }
}

public static float getCurrentStamina() {
    return getInstance().getLastStamina();
}
```

- [ ] **Step 4: Create `ServerConnectionStaminaPatch.java`**

```java
package com.garward.wurmmodloader.client.core.bytecode.patches;

import com.garward.wurmmodloader.client.api.bytecode.BytecodePatch;
import javassist.CtClass;
import javassist.CtMethod;

import java.util.Collection;
import java.util.Collections;
import java.util.logging.Logger;

/**
 * Patches {@code ServerConnectionListenerClass.setStamina(float, float)} to
 * fire {@code ClientStaminaChangedEvent} on every CMD_STAMINA packet.
 *
 * <p>{@code setStamina} signature is {@code (float stamina, float damage)}.
 * We capture {@code $1} (stamina) and ignore {@code $2}.
 *
 * @since 0.4.0
 */
public class ServerConnectionStaminaPatch implements BytecodePatch {

    private static final Logger logger = Logger.getLogger(ServerConnectionStaminaPatch.class.getName());
    private static final String PROXY = "com.garward.wurmmodloader.client.modloader.ProxyClientHook";

    @Override
    public String getTargetClassName() {
        return "com.wurmonline.client.comm.ServerConnectionListenerClass";
    }

    @Override
    public void apply(CtClass ctClass) throws Exception {
        CtMethod m = ctClass.getDeclaredMethod("setStamina");
        m.insertAfter(
            "{ try { " + PROXY + ".fireClientStaminaChangedEvent($1); } " +
            "catch (Throwable t) { t.printStackTrace(); } }"
        );
        logger.info("[ServerConnectionStaminaPatch] Patched ServerConnectionListenerClass.setStamina");
    }

    @Override
    public Collection<String> getConflictKeys() {
        return Collections.singleton("client.stamina.changed");
    }

    @Override
    public String getDescription() {
        return "Fire ClientStaminaChangedEvent from ServerConnectionListenerClass.setStamina";
    }
}
```

- [ ] **Step 5: Register patch in `ClientPatcher`**

In `ClientPatcher.java`, find the block where other `ServerConnectionListenerClass` / `SimpleServerConnectionClass` patches are added (~line 305 onward) and append:

```java
addPatch(accessWideningPatches,
    "com/wurmonline/client/comm/ServerConnectionListenerClass.class",
    new com.garward.wurmmodloader.client.core.bytecode.patches.ServerConnectionStaminaPatch()
);
```

- [ ] **Step 6: Build core + patcher**

Run: `cd ~/Scripts/Games/WurmUnlimited/WurmModLoader-Client && ./gradlew :wurmmodloader-client-api:compileJava :wurmmodloader-client-core:compileJava :wurmmodloader-client-patcher:compileJava`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 7: Commit**

```bash
cd ~/Scripts/Games/WurmUnlimited/WurmModLoader-Client
git add wurmmodloader-client-api/src/main/java/com/garward/wurmmodloader/client/api/events/client/ClientStaminaChangedEvent.java \
        wurmmodloader-client-core/src/main/java/com/garward/wurmmodloader/client/core/bytecode/patches/ServerConnectionStaminaPatch.java \
        wurmmodloader-client-core/src/main/java/com/garward/wurmmodloader/client/modloader/ClientHook.java \
        wurmmodloader-client-core/src/main/java/com/garward/wurmmodloader/client/modloader/ProxyClientHook.java \
        wurmmodloader-client-patcher/src/main/java/com/garward/wurmmodloader/client/patcher/ClientPatcher.java
git commit -m "feat(client): add ClientStaminaChangedEvent + setStamina patch"
```

---

## Phase 3 — Framework: `ClientEventMessageReceivedEvent`

### Task 3: Inspect message-dispatch chokepoint

**Files:**
- Reference (read only): `/tmp/wuclient/com/wurmonline/client/comm/ServerConnectionListenerClass.class`, `HeadsUpDisplay.class`.

- [ ] **Step 1: Confirm the funnel**

Run:

```bash
javap -p -c "/tmp/wuclient/com/wurmonline/client/comm/ServerConnectionListenerClass.class" \
  | grep -A2 -E "textMessage" | head -40
```

We already know two overloads exist:

- `public void textMessage(String, float, float, float, String, byte)` — color-tagged single-line.
- `void textMessage(String, java.util.List<MulticolorLineSegment>, byte)` — multicolor segments.

Plan: patch **both** — single-arg overload joins `$5` directly; multicolor overload joins `$2` (the segment list) by calling `MulticolorLineSegment.getText()` on each. Both call the same proxy entry that takes `(String window, String text, byte type)`.

If during implementation it turns out only one overload is used in practice (vanilla pipes everything through the first), drop the unused patch. Note this in the patch class Javadoc.

- [ ] **Step 2: No code change — proceed to Task 4 with this finding**

(This task is research only; the next task lands the code.)

---

### Task 4: Add the event class, patch, and proxy plumbing

**Files:**
- Create: `~/Scripts/Games/WurmUnlimited/WurmModLoader-Client/wurmmodloader-client-api/src/main/java/com/garward/wurmmodloader/client/api/events/client/ClientEventMessageReceivedEvent.java`
- Create: `~/Scripts/Games/WurmUnlimited/WurmModLoader-Client/wurmmodloader-client-core/src/main/java/com/garward/wurmmodloader/client/core/bytecode/patches/ServerConnectionTextMessagePatch.java`
- Modify: `ClientHook.java`, `ProxyClientHook.java`, `ClientPatcher.java`.

- [ ] **Step 1: Create `ClientEventMessageReceivedEvent.java`**

```java
package com.garward.wurmmodloader.client.api.events.client;

import com.garward.wurmmodloader.client.api.events.base.Event;

/**
 * Fires whenever the server pushes a line into the client's event/chat tabs
 * (vanilla calls {@code ServerConnectionListenerClass.textMessage}). Provides
 * the destination window, the plain text (multicolor segments concatenated),
 * and the message-type byte.
 *
 * <p>Cancellable: cancelling stops the message from being added to the HUD's
 * text log. Useful for filter / mute mods.
 *
 * @since 0.4.0
 */
public class ClientEventMessageReceivedEvent extends Event {

    private final String window;
    private final String text;
    private final byte messageType;

    public ClientEventMessageReceivedEvent(String window, String text, byte messageType) {
        this.window = window;
        this.text = text;
        this.messageType = messageType;
    }

    /** The chat window / tab the server targeted (e.g. "Event", "Combat"). */
    public String getWindow() { return window; }

    /** The plain-text payload (multicolor segments are pre-joined). */
    public String getText() { return text; }

    /** Vanilla {@code MessageServer} type byte. */
    public byte getMessageType() { return messageType; }

    @Override
    public String toString() {
        return "ClientEventMessageReceivedEvent{window=" + window
                + ", text=" + text + ", type=" + messageType + "}";
    }
}
```

The event is cancellable because the base `Event` class supports cancellation — confirm by checking `wurmmodloader-client-api/src/main/java/com/garward/wurmmodloader/client/api/events/base/Event.java`. If `Event` doesn't have `cancel()`/`isCancelled()` already, leave the event uncancellable for now (note the limitation in the Javadoc) — adding cancellation is out of scope for this plan.

- [ ] **Step 2: `ClientHook` instance method**

Add to `ClientHook.java`:

```java
import com.garward.wurmmodloader.client.api.events.client.ClientEventMessageReceivedEvent;

// …

/**
 * @return {@code true} if a subscriber cancelled the message (i.e. the patch
 *         should suppress vanilla display); {@code false} to let it through.
 */
public boolean fireClientEventMessage(String window, String text, byte type) {
    ClientEventMessageReceivedEvent event = new ClientEventMessageReceivedEvent(window, text, type);
    postEvent(event);
    return event.isCancelled();
}
```

If `Event` doesn't expose `isCancelled()`, drop the return-value plumbing and `return false;` — the patch will simply never suppress messages.

- [ ] **Step 3: `ProxyClientHook` static entry**

```java
public static boolean fireClientEventMessageReceivedEvent(String window, String text, byte type) {
    try { return getInstance().fireClientEventMessage(window, text, type); }
    catch (Throwable t) { t.printStackTrace(); return false; }
}
```

- [ ] **Step 4: Create `ServerConnectionTextMessagePatch.java`**

```java
package com.garward.wurmmodloader.client.core.bytecode.patches;

import com.garward.wurmmodloader.client.api.bytecode.BytecodePatch;
import javassist.CtClass;
import javassist.CtMethod;
import javassist.CtMethod.ConstParameter;
import javassist.CtPrimitiveType;
import javassist.NotFoundException;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.logging.Logger;

/**
 * Patches {@code ServerConnectionListenerClass.textMessage} (both overloads)
 * to fire {@code ClientEventMessageReceivedEvent} so mods can observe / filter
 * server messages.
 *
 * <ul>
 *   <li>Overload A: {@code textMessage(String window, float r, float g, float b, String text, byte type)}
 *       — plain string in {@code $5}.</li>
 *   <li>Overload B: {@code textMessage(String window, List<MulticolorLineSegment> segments, byte type)}
 *       — concatenate segments via {@code getText()}.</li>
 * </ul>
 *
 * If a subscriber cancels, return early so the message is suppressed.
 *
 * @since 0.4.0
 */
public class ServerConnectionTextMessagePatch implements BytecodePatch {

    private static final Logger logger = Logger.getLogger(ServerConnectionTextMessagePatch.class.getName());
    private static final String PROXY = "com.garward.wurmmodloader.client.modloader.ProxyClientHook";

    @Override
    public String getTargetClassName() {
        return "com.wurmonline.client.comm.ServerConnectionListenerClass";
    }

    @Override
    public void apply(CtClass ctClass) throws Exception {
        // Overload A — String text in $5, byte type in $6
        CtMethod a = findOverload(ctClass,
                "java.lang.String", "float", "float", "float", "java.lang.String", "byte");
        a.insertBefore(
            "{ try { " +
            "if (" + PROXY + ".fireClientEventMessageReceivedEvent($1, $5, $6)) return; " +
            "} catch (Throwable t) { t.printStackTrace(); } }"
        );

        // Overload B — List<MulticolorLineSegment> in $2, byte type in $3
        CtMethod b = findOverload(ctClass,
                "java.lang.String", "java.util.List", "byte");
        b.insertBefore(
            "{ try { " +
            "StringBuilder _sb = new StringBuilder(); " +
            "if ($2 != null) { " +
            "  java.util.Iterator _it = $2.iterator(); " +
            "  while (_it.hasNext()) { " +
            "    Object _seg = _it.next(); " +
            "    if (_seg != null) _sb.append(((com.wurmonline.shared.util.MulticolorLineSegment) _seg).getText()); " +
            "  } " +
            "} " +
            "if (" + PROXY + ".fireClientEventMessageReceivedEvent($1, _sb.toString(), $3)) return; " +
            "} catch (Throwable t) { t.printStackTrace(); } }"
        );

        logger.info("[ServerConnectionTextMessagePatch] Patched ServerConnectionListenerClass.textMessage (both overloads)");
    }

    private static CtMethod findOverload(CtClass cc, String... paramTypeNames) throws NotFoundException {
        outer:
        for (CtMethod m : cc.getDeclaredMethods("textMessage")) {
            CtClass[] params = m.getParameterTypes();
            if (params.length != paramTypeNames.length) continue;
            for (int i = 0; i < params.length; i++) {
                if (!params[i].getName().equals(paramTypeNames[i])) continue outer;
            }
            return m;
        }
        throw new NotFoundException("textMessage" + Arrays.toString(paramTypeNames));
    }

    @Override
    public Collection<String> getConflictKeys() {
        return Collections.singleton("client.event-message.received");
    }

    @Override
    public String getDescription() {
        return "Fire ClientEventMessageReceivedEvent from ServerConnectionListenerClass.textMessage (both overloads)";
    }
}
```

- [ ] **Step 5: Register the patch in `ClientPatcher.java`**

Append next to the stamina patch registration:

```java
addPatch(accessWideningPatches,
    "com/wurmonline/client/comm/ServerConnectionListenerClass.class",
    new com.garward.wurmmodloader.client.core.bytecode.patches.ServerConnectionTextMessagePatch()
);
```

- [ ] **Step 6: Build**

Run: `cd ~/Scripts/Games/WurmUnlimited/WurmModLoader-Client && ./gradlew build`
Expected: `BUILD SUCCESSFUL`. If javassist complains about the multicolor overload (parameter type erasure on `List`), confirm via:

```bash
javap -p -s "/tmp/wuclient/com/wurmonline/client/comm/ServerConnectionListenerClass.class" | grep textMessage
```

The `-s` signature should show `(Ljava/lang/String;Ljava/util/List;B)V`. Javassist resolves `java.util.List` (the erased type) — no generics needed.

- [ ] **Step 7: Commit**

```bash
cd ~/Scripts/Games/WurmUnlimited/WurmModLoader-Client
git add wurmmodloader-client-api/src/main/java/com/garward/wurmmodloader/client/api/events/client/ClientEventMessageReceivedEvent.java \
        wurmmodloader-client-core/src/main/java/com/garward/wurmmodloader/client/core/bytecode/patches/ServerConnectionTextMessagePatch.java \
        wurmmodloader-client-core/src/main/java/com/garward/wurmmodloader/client/modloader/ClientHook.java \
        wurmmodloader-client-core/src/main/java/com/garward/wurmmodloader/client/modloader/ProxyClientHook.java \
        wurmmodloader-client-patcher/src/main/java/com/garward/wurmmodloader/client/patcher/ClientPatcher.java
git commit -m "feat(client): add ClientEventMessageReceivedEvent + textMessage patch"
```

---

## Phase 4 — Build framework jars and refresh CommunityMods libs

### Task 5: Publish framework jars locally and bump version

**Files:**
- Modify (if needed): `~/Scripts/Games/WurmUnlimited/WurmModLoader-Client/build.gradle.kts` or per-module version refs (look for `0.3.0` strings).
- Copy: built `wurmmodloader-client-api-*.jar`, `wurmmodloader-client-core-*.jar` into `~/Scripts/Games/WurmUnlimited/WurmModLoader-CommunityMods/libs/`.

- [ ] **Step 1: Bump framework version**

Run:

```bash
cd ~/Scripts/Games/WurmUnlimited/WurmModLoader-Client
grep -rn '"0.3.0"' --include="*.kts" | head
```

Update every match to `"0.4.0"` (this is a feature-bump release that adds public API).

- [ ] **Step 2: Build distribution**

```bash
cd ~/Scripts/Games/WurmUnlimited/WurmModLoader-Client
./gradlew clean build dist
```

Expected: succeeds and produces `wurmmodloader-client-api-0.4.0.jar`, `wurmmodloader-client-core-0.4.0.jar` under each module's `build/libs/`.

- [ ] **Step 3: Refresh CommunityMods `libs/`**

```bash
cp ~/Scripts/Games/WurmUnlimited/WurmModLoader-Client/wurmmodloader-client-api/build/libs/wurmmodloader-client-api-0.4.0.jar \
   ~/Scripts/Games/WurmUnlimited/WurmModLoader-CommunityMods/libs/
cp ~/Scripts/Games/WurmUnlimited/WurmModLoader-Client/wurmmodloader-client-core/build/libs/wurmmodloader-client-core-0.4.0.jar \
   ~/Scripts/Games/WurmUnlimited/WurmModLoader-CommunityMods/libs/
```

Then update every `0.3.0` reference in `client-mods/*/build.gradle.kts` to `0.4.0`:

```bash
cd ~/Scripts/Games/WurmUnlimited/WurmModLoader-CommunityMods
grep -rln 'wurmmodloader-client-.*-0.3.0' client-mods/
# For each match, edit the version string to 0.4.0.
```

(Keep the old `0.3.0` jars in place if other branches still reference them — only update what we touch.)

- [ ] **Step 4: Commit framework version bump and jar refresh separately**

```bash
cd ~/Scripts/Games/WurmUnlimited/WurmModLoader-Client
git add -A
git commit -m "chore(client): bump version to 0.4.0"

cd ~/Scripts/Games/WurmUnlimited/WurmModLoader-CommunityMods
git add libs/wurmmodloader-client-api-0.4.0.jar libs/wurmmodloader-client-core-0.4.0.jar client-mods/
git commit -m "chore(client-mods): refresh framework libs to 0.4.0"
```

---

## Phase 5 — Migrate the `action` mod onto the dispatcher

### Task 6: Replace `parseAct` body with delegation; delete local `Reflect`

**Files:**
- Modify: `~/Scripts/Games/WurmUnlimited/WurmModLoader-CommunityMods/client-mods/action/src/main/java/com/garward/wurmmodloader/mods/action/ActionClientMod.java`
- Delete: `~/Scripts/Games/WurmUnlimited/WurmModLoader-CommunityMods/client-mods/action/src/main/java/com/garward/wurmmodloader/mods/action/Reflect.java`

- [ ] **Step 1: Confirm `Reflect` callers**

```bash
cd ~/Scripts/Games/WurmUnlimited/WurmModLoader-CommunityMods/client-mods/action
grep -rn "Reflect\." src/
```

Expected: matches only inside `ActionClientMod.java` and nothing else. `ActionMacroUI` doesn't reference `Reflect`. If grep returns matches outside these two files, stop and reassess — those callers must migrate first.

- [ ] **Step 2: Edit `ActionClientMod.java`**

In the imports block, remove the seven `com.wurmonline.*` imports that are now only needed by the dispatcher (everything except `PlayerAction`, since the source still references `PlayerActionNameResolvedEvent`'s related stuff). Specifically delete (only if no remaining caller in the file uses them):

```
com.wurmonline.client.comm.ServerConnectionListenerClass
com.wurmonline.client.game.inventory.InventoryMetaItem
com.wurmonline.client.renderer.PickableUnit
com.wurmonline.client.renderer.cell.CellRenderable
com.wurmonline.client.renderer.cell.CreatureCellRenderable
com.wurmonline.client.renderer.cell.GroundItemCellRenderable
com.wurmonline.client.renderer.gui.PaperDollSlot
com.wurmonline.mesh.Tiles
java.util.Arrays
java.util.Collection
java.util.stream.Stream
```

Add the new dispatcher import:

```java
import com.garward.wurmmodloader.client.api.events.eventlogic.action.ClientItemReflect;
import com.garward.wurmmodloader.client.api.events.eventlogic.action.PlayerActionDispatcher;
```

Rewrite `parseAct` to a single delegate, keeping its existing public signature intact (other code paths may call it; the macro UI does not, but downstream mods may have):

```java
/**
 * Run one action. Public so the macro-builder UI / other mods can reuse the
 * exact same dispatch path.
 */
public static void parseAct(short id, String target) throws ReflectiveOperationException {
    PlayerActionDispatcher.dispatch(hud, id, target);
}
```

Replace the old `handleAct` parser to keep it intact (it still tokenises the console form), but its body now just calls the new delegate (already does — no change needed inside `handleAct`). Delete the now-orphaned `parseAct` body, `sendAreaAction`, and `sendLocalAction` helpers.

In `onHudInit`, change `Reflect.setup();` to `ClientItemReflect.setup();`.

- [ ] **Step 3: Delete the old `Reflect.java`**

```bash
rm ~/Scripts/Games/WurmUnlimited/WurmModLoader-CommunityMods/client-mods/action/src/main/java/com/garward/wurmmodloader/mods/action/Reflect.java
```

- [ ] **Step 4: Build the action mod**

```bash
cd ~/Scripts/Games/WurmUnlimited/WurmModLoader-CommunityMods
./gradlew :client-mods:action:build
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```bash
cd ~/Scripts/Games/WurmUnlimited/WurmModLoader-CommunityMods
git add client-mods/action/src/
git commit -m "refactor(action): delegate to PlayerActionDispatcher; remove local Reflect"
```

---

## Phase 6 — New mod: `automine`

### Task 7: Scaffold the gradle module

**Files:**
- Create: `~/Scripts/Games/WurmUnlimited/WurmModLoader-CommunityMods/client-mods/automine/build.gradle.kts`
- Create: `~/Scripts/Games/WurmUnlimited/WurmModLoader-CommunityMods/client-mods/automine/src/dist/mod.properties`
- Create: `~/Scripts/Games/WurmUnlimited/WurmModLoader-CommunityMods/client-mods/automine/src/dist/automine.properties`
- Modify: `~/Scripts/Games/WurmUnlimited/WurmModLoader-CommunityMods/settings.gradle.kts`

- [ ] **Step 1: `build.gradle.kts`** (mirrors `client-mods/action/build.gradle.kts`)

```kotlin
group = "com.garward.mods"
version = "0.1.0"

val wurmClientDir: String by rootProject.extra
if (wurmClientDir.isEmpty()) {
    error(
        "Wurm Unlimited client directory not set. Add `wurmClientDir=/path/to/Wurm Unlimited/WurmLauncher` " +
        "to ~/.gradle/gradle.properties, or set the WURM_CLIENT_DIR environment variable. " +
        "See gradle.properties.example."
    )
}

dependencies {
    compileOnly(files("${rootProject.projectDir}/libs/wurmmodloader-client-api-0.4.0.jar"))
    compileOnly(files("${rootProject.projectDir}/libs/wurmmodloader-client-core-0.4.0.jar"))

    compileOnly(files("$wurmClientDir/client.jar"))
    compileOnly(files("$wurmClientDir/common.jar"))
}

tasks.jar {
    archiveBaseName.set("automine")
    archiveVersion.set("")

    manifest {
        attributes(
            "Implementation-Title" to "Automine Client Mod",
            "Implementation-Version" to project.version
        )
    }
}

tasks.register<Copy>("modDistribution") {
    dependsOn(tasks.jar)
    from(tasks.jar.get().archiveFile) {
        into("mods/automine")
    }
    from("src/dist") {
        into("mods/automine")
    }
    into("${projectDir}/dist")
}

tasks.build {
    dependsOn(tasks.named("modDistribution"))
}

tasks.register<Copy>("deployMod") {
    dependsOn(tasks.jar)
    from(tasks.jar.get().archiveFile)
    from("src/dist")
    into("$wurmClientDir/mods/automine")
}
```

- [ ] **Step 2: Register in `settings.gradle.kts`** — add after the action mod entry:

```kotlin
include("client-mods:automine")
project(":client-mods:automine").projectDir = file("client-mods/automine")
```

- [ ] **Step 3: `src/dist/mod.properties`**

```properties
classname=com.garward.wurmmodloader.mods.automine.AutomineClientMod
sharedClassLoader=true
```

- [ ] **Step 4: `src/dist/automine.properties`**

```properties
# Action IDs (vanilla defaults — override only if server re-numbers).
actionId.forward=38
actionId.up=39
actionId.down=40

# Default batch size on first window open (1-10).
defaultBatchSize=3

# Stamina threshold to consider "full" (0.0-1.0). Server clamps near full.
staminaFullThreshold=0.99

# Watchdog: if waiting for stamina event longer than this (ms) and the
# accessor reports >= threshold, kick the next batch anyway.
staminaWatchdogMs=60000

# Stop phrases (regex, case-insensitive). One per line, suffix index.
# Defaults seeded from decompiled server source — see
# WurmModLoader-Client/docs/research/mining-stop-phrases.md for citations.
stopPhrase.0=you are too unskilled to mine
stopPhrase.1=the topology here makes it impossible
stopPhrase.2=the water is too deep to mine
stopPhrase.3=this tile is protected by the gods
stopPhrase.4=the surrounding area needs to be rock
stopPhrase.5=you cannot mine
```

- [ ] **Step 5: Verify gradle picks up the module**

```bash
cd ~/Scripts/Games/WurmUnlimited/WurmModLoader-CommunityMods
./gradlew :client-mods:automine:tasks | head -20
```

Expected: lists `build`, `deployMod`, `modDistribution` etc.

- [ ] **Step 6: Commit**

```bash
cd ~/Scripts/Games/WurmUnlimited/WurmModLoader-CommunityMods
git add settings.gradle.kts client-mods/automine/
git commit -m "feat(automine): scaffold gradle module + properties"
```

---

### Task 8: Implement `AutomineConfig` (loader)

**Files:**
- Create: `~/Scripts/Games/WurmUnlimited/WurmModLoader-CommunityMods/client-mods/automine/src/main/java/com/garward/wurmmodloader/mods/automine/AutomineConfig.java`

- [ ] **Step 1: Create the file**

```java
package com.garward.wurmmodloader.mods.automine;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * Loads {@code automine.properties} from the deployed mod folder and exposes
 * typed accessors. Falls back to hardcoded defaults if the file is missing.
 */
public final class AutomineConfig {

    private static final Logger logger = Logger.getLogger(AutomineConfig.class.getName());

    public final short actionForward;
    public final short actionUp;
    public final short actionDown;
    public final int defaultBatchSize;
    public final float staminaFullThreshold;
    public final long staminaWatchdogMs;
    public final List<Pattern> stopPhrases;

    private AutomineConfig(short fwd, short up, short down, int batch,
                           float thr, long watchdog, List<Pattern> phrases) {
        this.actionForward = fwd;
        this.actionUp = up;
        this.actionDown = down;
        this.defaultBatchSize = batch;
        this.staminaFullThreshold = thr;
        this.staminaWatchdogMs = watchdog;
        this.stopPhrases = Collections.unmodifiableList(phrases);
    }

    public static AutomineConfig load(File propertiesFile) {
        Properties p = new Properties();
        if (propertiesFile != null && propertiesFile.isFile()) {
            try (FileInputStream in = new FileInputStream(propertiesFile)) {
                p.load(in);
            } catch (IOException e) {
                logger.log(Level.WARNING, "[automine] failed to read " + propertiesFile + " — using defaults", e);
            }
        }
        short fwd  = (short) parseInt(p, "actionId.forward", 38);
        short up   = (short) parseInt(p, "actionId.up", 39);
        short down = (short) parseInt(p, "actionId.down", 40);
        int batch  = clamp(parseInt(p, "defaultBatchSize", 3), 1, 10);
        float thr  = (float) parseDouble(p, "staminaFullThreshold", 0.99);
        long wd    = parseLong(p, "staminaWatchdogMs", 60_000);

        List<Pattern> phrases = new ArrayList<>();
        for (int i = 0; ; i++) {
            String key = "stopPhrase." + i;
            String v = p.getProperty(key);
            if (v == null) break;
            try { phrases.add(Pattern.compile(v, Pattern.CASE_INSENSITIVE)); }
            catch (Exception e) { logger.warning("[automine] bad regex at " + key + ": " + v); }
        }
        if (phrases.isEmpty()) {
            for (String def : new String[]{
                    "you are too unskilled to mine",
                    "the topology here makes it impossible",
                    "the water is too deep to mine",
                    "this tile is protected by the gods",
                    "the surrounding area needs to be rock",
                    "you cannot mine"}) {
                phrases.add(Pattern.compile(def, Pattern.CASE_INSENSITIVE));
            }
        }
        return new AutomineConfig(fwd, up, down, batch, thr, wd, phrases);
    }

    private static int parseInt(Properties p, String k, int d) {
        try { return Integer.parseInt(p.getProperty(k, Integer.toString(d)).trim()); }
        catch (Exception e) { return d; }
    }
    private static long parseLong(Properties p, String k, long d) {
        try { return Long.parseLong(p.getProperty(k, Long.toString(d)).trim()); }
        catch (Exception e) { return d; }
    }
    private static double parseDouble(Properties p, String k, double d) {
        try { return Double.parseDouble(p.getProperty(k, Double.toString(d)).trim()); }
        catch (Exception e) { return d; }
    }
    private static int clamp(int v, int lo, int hi) { return Math.max(lo, Math.min(hi, v)); }

    public boolean isStopPhrase(String text) {
        if (text == null) return false;
        for (Pattern pat : stopPhrases) if (pat.matcher(text).find()) return true;
        return false;
    }
}
```

- [ ] **Step 2: Compile**

```bash
cd ~/Scripts/Games/WurmUnlimited/WurmModLoader-CommunityMods
./gradlew :client-mods:automine:compileJava
```

Expected: succeeds.

- [ ] **Step 3: Commit**

```bash
git add client-mods/automine/src/main/java/com/garward/wurmmodloader/mods/automine/AutomineConfig.java
git commit -m "feat(automine): config loader"
```

---

### Task 9: Implement `AutomineState` (TDD)

**Files:**
- Create: `~/Scripts/Games/WurmUnlimited/WurmModLoader-CommunityMods/client-mods/automine/src/test/java/com/garward/wurmmodloader/mods/automine/AutomineStateTest.java`
- Create: `~/Scripts/Games/WurmUnlimited/WurmModLoader-CommunityMods/client-mods/automine/src/main/java/com/garward/wurmmodloader/mods/automine/AutomineState.java`

The state machine must be HUD-free so it's unit-testable. The mod's runtime layer (Task 11) wires the dispatcher / event bus / clock to it.

State machine spec (from design §4.4):

```
states: IDLE, DISPATCHING, WAITING_STAMINA, STOPPED

IDLE         --start(N)-->            DISPATCHING (sentBatch=0)
DISPATCHING  --tickDispatch()-->      DISPATCHING (sentBatch++, until == N)
                                       then → WAITING_STAMINA, mark startWaitingAt = now
WAITING_STAMINA --staminaUpdate(s)
                  if s >= threshold-->  DISPATCHING (sentBatch=0)
WAITING_STAMINA --watchdog(now)
                  if (now-startWaitingAt) >= watchdogMs && staminaProvider.get() >= threshold
                                       --> DISPATCHING (sentBatch=0)
*            --message(text)
                  if isStopPhrase(text) --> STOPPED
*            --pause() / windowClosed()  --> STOPPED
STOPPED      --start(N)-->            DISPATCHING (sentBatch=0)
```

- [ ] **Step 1: Write the failing tests**

`src/test/java/com/garward/wurmmodloader/mods/automine/AutomineStateTest.java`:

```java
package com.garward.wurmmodloader.mods.automine;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

class AutomineStateTest {

    private final List<Short> dispatched = new ArrayList<>();
    private final AtomicReference<Float> stamina = new AtomicReference<>(1.0f);
    private final List<Pattern> stops =
            java.util.Collections.singletonList(Pattern.compile("rock crumbles", Pattern.CASE_INSENSITIVE));

    private AutomineState newState() {
        return new AutomineState(
                actionId -> dispatched.add(actionId),
                stamina::get,
                stops,
                0.99f,
                60_000L
        );
    }

    @Test
    void start_sendsFirstBatch_thenWaitsForStamina() {
        AutomineState s = newState();
        s.start((short) 38, 3);
        // pump three dispatch ticks
        s.onTick(0L);
        s.onTick(0L);
        s.onTick(0L);
        assertEquals(AutomineState.Phase.WAITING_STAMINA, s.getPhase());
        assertEquals(java.util.Arrays.asList((short) 38, (short) 38, (short) 38), dispatched);
    }

    @Test
    void staminaFull_kicksNextBatch() {
        AutomineState s = newState();
        s.start((short) 38, 2);
        s.onTick(0L); s.onTick(0L);
        assertEquals(AutomineState.Phase.WAITING_STAMINA, s.getPhase());
        s.onStaminaChanged(0.50f);     // not full yet
        assertEquals(AutomineState.Phase.WAITING_STAMINA, s.getPhase());
        s.onStaminaChanged(0.99f);     // full
        assertEquals(AutomineState.Phase.DISPATCHING, s.getPhase());
        s.onTick(0L); s.onTick(0L);
        assertEquals(4, dispatched.size());
    }

    @Test
    void stopPhrase_haltsLoop() {
        AutomineState s = newState();
        s.start((short) 38, 5);
        s.onTick(0L);
        s.onMessage("Crash! The rock crumbles.");
        assertEquals(AutomineState.Phase.STOPPED, s.getPhase());
        s.onTick(0L);                  // no further dispatch after stop
        assertEquals(1, dispatched.size());
    }

    @Test
    void pause_stops() {
        AutomineState s = newState();
        s.start((short) 38, 5);
        s.onTick(0L);
        s.pause();
        assertEquals(AutomineState.Phase.STOPPED, s.getPhase());
    }

    @Test
    void watchdog_kicksNextBatchIfStaminaEventMissed() {
        AutomineState s = newState();
        s.start((short) 38, 1);
        s.onTick(1_000L);
        assertEquals(AutomineState.Phase.WAITING_STAMINA, s.getPhase());
        // no onStaminaChanged event arrives. Provider says full.
        stamina.set(1.0f);
        s.onTick(1_000L + 60_000L);
        assertEquals(AutomineState.Phase.DISPATCHING, s.getPhase());
    }

    @Test
    void restartFromStopped() {
        AutomineState s = newState();
        s.start((short) 38, 1);
        s.pause();
        assertEquals(AutomineState.Phase.STOPPED, s.getPhase());
        s.start((short) 38, 1);
        assertEquals(AutomineState.Phase.DISPATCHING, s.getPhase());
    }
}
```

- [ ] **Step 2: Wire JUnit 5 into the mod's `build.gradle.kts`**

The parent build already injects JUnit 5 into every subproject's `testImplementation` (see `WurmModLoader-CommunityMods/build.gradle.kts` lines 71-77 — `subprojects { … testImplementation("org.junit.jupiter:junit-jupiter:…") }`). Confirm this still applies to `client-mods` modules:

```bash
cd ~/Scripts/Games/WurmUnlimited/WurmModLoader-CommunityMods
./gradlew :client-mods:automine:dependencies --configuration testCompileClasspath | grep junit
```

Expected: lists `junit-jupiter`. If not (because `client-mods` lives under a different `subprojects` block), append explicitly to `client-mods/automine/build.gradle.kts`:

```kotlin
dependencies {
    // existing entries...
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.1")
}

tasks.test { useJUnitPlatform() }
```

- [ ] **Step 3: Run the tests — expect failure**

```bash
./gradlew :client-mods:automine:test
```

Expected: compile error (`AutomineState` does not exist).

- [ ] **Step 4: Implement `AutomineState.java`**

`src/main/java/com/garward/wurmmodloader/mods/automine/AutomineState.java`:

```java
package com.garward.wurmmodloader.mods.automine;

import java.util.List;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/**
 * Pure state machine driving the automine loop. HUD-free so it can be unit
 * tested in isolation. Wire into the live HUD via {@link AutomineClientMod}.
 */
public final class AutomineState {

    public enum Phase { IDLE, DISPATCHING, WAITING_STAMINA, STOPPED }

    @FunctionalInterface
    public interface ActionDispatcher {
        void dispatch(short actionId);
    }

    private final ActionDispatcher dispatcher;
    private final Supplier<Float> staminaProvider;
    private final List<Pattern> stopPhrases;
    private final float fullThreshold;
    private final long watchdogMs;

    private volatile Phase phase = Phase.IDLE;
    private short actionId;
    private int batchSize;
    private int sentInBatch;
    private long waitingSince;
    private String stopReason = "";

    public AutomineState(ActionDispatcher dispatcher,
                         Supplier<Float> staminaProvider,
                         List<Pattern> stopPhrases,
                         float fullThreshold,
                         long watchdogMs) {
        this.dispatcher = dispatcher;
        this.staminaProvider = staminaProvider;
        this.stopPhrases = stopPhrases;
        this.fullThreshold = fullThreshold;
        this.watchdogMs = watchdogMs;
    }

    public Phase getPhase() { return phase; }
    public String getStopReason() { return stopReason; }
    public int getSentInBatch() { return sentInBatch; }
    public int getBatchSize() { return batchSize; }

    public synchronized void start(short actionId, int batchSize) {
        this.actionId = actionId;
        this.batchSize = Math.max(1, batchSize);
        this.sentInBatch = 0;
        this.stopReason = "";
        this.phase = Phase.DISPATCHING;
    }

    public synchronized void pause() {
        this.phase = Phase.STOPPED;
        this.stopReason = "paused";
    }

    public synchronized void onTick(long nowMs) {
        switch (phase) {
            case DISPATCHING:
                if (sentInBatch < batchSize) {
                    dispatcher.dispatch(actionId);
                    sentInBatch++;
                    if (sentInBatch >= batchSize) {
                        phase = Phase.WAITING_STAMINA;
                        waitingSince = nowMs;
                    }
                }
                break;
            case WAITING_STAMINA:
                if (watchdogMs > 0 && (nowMs - waitingSince) >= watchdogMs) {
                    Float s = staminaProvider.get();
                    if (s != null && s >= fullThreshold) {
                        phase = Phase.DISPATCHING;
                        sentInBatch = 0;
                    }
                }
                break;
            default:
                break;
        }
    }

    public synchronized void onStaminaChanged(float newStamina) {
        if (phase == Phase.WAITING_STAMINA && newStamina >= fullThreshold) {
            phase = Phase.DISPATCHING;
            sentInBatch = 0;
        }
    }

    public synchronized void onMessage(String text) {
        if (phase == Phase.STOPPED || phase == Phase.IDLE) return;
        for (Pattern p : stopPhrases) {
            if (p.matcher(text).find()) {
                phase = Phase.STOPPED;
                stopReason = "tile broke / can't mine";
                return;
            }
        }
    }
}
```

- [ ] **Step 5: Run the tests — expect pass**

```bash
./gradlew :client-mods:automine:test
```

Expected: all 6 tests pass.

- [ ] **Step 6: Commit**

```bash
git add client-mods/automine/src/main/java/com/garward/wurmmodloader/mods/automine/AutomineState.java \
        client-mods/automine/src/test/java/com/garward/wurmmodloader/mods/automine/AutomineStateTest.java \
        client-mods/automine/build.gradle.kts
git commit -m "feat(automine): state machine + tests"
```

---

### Task 10: Implement `AutomineWindow` (UI)

**Files:**
- Create: `~/Scripts/Games/WurmUnlimited/WurmModLoader-CommunityMods/client-mods/automine/src/main/java/com/garward/wurmmodloader/mods/automine/AutomineWindow.java`

Reference for widgets: `~/Scripts/Games/WurmUnlimited/WurmModLoader-CommunityMods/client-mods/action/src/main/java/com/garward/wurmmodloader/mods/action/ActionMacroUI.java`. Same `ModWindow + ModStackPanel + ModButton + ModLabel` toolkit.

UI elements (from design §4.3):
- Three direction buttons (Forward / Up / Down) — radio behavior emulated with three buttons that update a label.
- Batch size: render as `[ - ]  3  [ + ]` (two ModButtons flanking a ModLabel) — saves us implementing a numeric input. Range 1–10.
- Status label.
- Bottom row: `[ Start ] [ Pause ]`. (Window's built-in close button stops the loop via lifecycle hook.)

- [ ] **Step 1: Create `AutomineWindow.java`**

```java
package com.garward.wurmmodloader.mods.automine;

import com.garward.wurmmodloader.client.api.gui.ArrayDirection;
import com.garward.wurmmodloader.client.api.gui.Insets;
import com.garward.wurmmodloader.client.api.gui.ModButton;
import com.garward.wurmmodloader.client.api.gui.ModLabel;
import com.garward.wurmmodloader.client.api.gui.ModStackPanel;
import com.garward.wurmmodloader.client.api.gui.ModWindow;

/**
 * Popup window for the automine controls. Direction buttons / batch +/- /
 * Start / Pause. Wires user actions into {@link AutomineWindow.Listener}.
 */
public final class AutomineWindow extends ModWindow {

    public enum Direction { FORWARD, UP, DOWN }

    public interface Listener {
        void onStart(Direction direction, int batchSize);
        void onPause();
        void onClose();
    }

    private final Listener listener;
    private final AutomineConfig config;
    private final ModLabel statusLabel;
    private final ModLabel directionLabel;
    private final ModLabel batchLabel;

    private Direction direction = Direction.FORWARD;
    private int batchSize;

    public AutomineWindow(AutomineConfig config, Listener listener) {
        super("Auto Mine");
        this.config = config;
        this.listener = listener;
        this.batchSize = config.defaultBatchSize;
        this.statusLabel = new ModLabel("Status: idle");
        this.directionLabel = new ModLabel("Direction: Forward");
        this.batchLabel = new ModLabel("Actions per batch: " + batchSize);
        lockSize();
        installContent(buildRoot(), 240, 200);
    }

    private ModStackPanel buildRoot() {
        ModStackPanel root = new ModStackPanel("Automine root", ArrayDirection.VERTICAL)
                .setBackgroundPainted(true)
                .setPadding(Insets.uniform(6))
                .setGap(4);
        root.addChild(directionLabel);

        ModStackPanel dirRow = new ModStackPanel("dir", ArrayDirection.HORIZONTAL).setGap(2);
        dirRow.addChild(new ModButton("Forward", "Mine Forward", () -> setDirection(Direction.FORWARD)));
        dirRow.addChild(new ModButton("Up",      "Mine Up",      () -> setDirection(Direction.UP)));
        dirRow.addChild(new ModButton("Down",    "Mine Down",    () -> setDirection(Direction.DOWN)));
        root.addChild(dirRow);

        root.addChild(batchLabel);
        ModStackPanel batchRow = new ModStackPanel("batch", ArrayDirection.HORIZONTAL).setGap(2);
        batchRow.addChild(new ModButton("-", "Decrease batch size", () -> changeBatch(-1)));
        batchRow.addChild(new ModButton("+", "Increase batch size", () -> changeBatch(+1)));
        root.addChild(batchRow);

        root.addChild(statusLabel);

        ModStackPanel controls = new ModStackPanel("controls", ArrayDirection.HORIZONTAL).setGap(4);
        controls.addChild(new ModButton("Start", "Start automining",
                () -> listener.onStart(direction, batchSize)));
        controls.addChild(new ModButton("Pause", "Stop the loop", listener::onPause));
        root.addChild(controls);
        return root;
    }

    private void setDirection(Direction d) {
        this.direction = d;
        directionLabel.setText("Direction: " + label(d));
    }

    private void changeBatch(int delta) {
        this.batchSize = Math.max(1, Math.min(10, batchSize + delta));
        batchLabel.setText("Actions per batch: " + batchSize);
    }

    public void setStatus(String text) {
        statusLabel.setText("Status: " + text);
    }

    private static String label(Direction d) {
        switch (d) { case UP: return "Up"; case DOWN: return "Down"; default: return "Forward"; }
    }

    @Override
    public void closePressed() {
        super.closePressed();
        listener.onClose();
    }
}
```

If `ModLabel` does not have `setText`, peek at `~/Scripts/Games/WurmUnlimited/WurmModLoader-Client/wurmmodloader-client-api/src/main/java/com/garward/wurmmodloader/client/api/gui/ModLabel.java` and use whatever the public mutation method is (likely `setText` based on idiomatic naming; fall back to recreating the label and reattaching if read-only — confirm with the source before writing).

- [ ] **Step 2: Compile**

```bash
cd ~/Scripts/Games/WurmUnlimited/WurmModLoader-CommunityMods
./gradlew :client-mods:automine:compileJava
```

Expected: `BUILD SUCCESSFUL`. If `ModLabel.setText` doesn't exist, edit accordingly and rebuild.

- [ ] **Step 3: Commit**

```bash
git add client-mods/automine/src/main/java/com/garward/wurmmodloader/mods/automine/AutomineWindow.java
git commit -m "feat(automine): popup window UI"
```

---

### Task 11: Implement `AutomineClientMod` (entry point + glue)

**Files:**
- Create: `~/Scripts/Games/WurmUnlimited/WurmModLoader-CommunityMods/client-mods/automine/src/main/java/com/garward/wurmmodloader/mods/automine/AutomineClientMod.java`

- [ ] **Step 1: Create the entry class**

```java
package com.garward.wurmmodloader.mods.automine;

import com.garward.wurmmodloader.client.api.events.base.SubscribeEvent;
import com.garward.wurmmodloader.client.api.events.client.ClientConsoleInputEvent;
import com.garward.wurmmodloader.client.api.events.client.ClientEventMessageReceivedEvent;
import com.garward.wurmmodloader.client.api.events.client.ClientStaminaChangedEvent;
import com.garward.wurmmodloader.client.api.events.eventlogic.action.PlayerActionDispatcher;
import com.garward.wurmmodloader.client.api.events.lifecycle.ClientTickEvent;
import com.garward.wurmmodloader.client.api.events.map.ClientHUDInitializedEvent;
import com.garward.wurmmodloader.client.api.gui.ModHud;
import com.garward.wurmmodloader.client.modloader.ProxyClientHook;

import com.wurmonline.client.renderer.gui.HeadsUpDisplay;

import java.io.File;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Entry point for the automine mod. Owns the window, the state machine, and
 * the event subscriptions. Console command {@code automine} opens / focuses
 * the window.
 */
public class AutomineClientMod {

    private static final Logger logger = Logger.getLogger("AutomineMod");

    private volatile HeadsUpDisplay hud;
    private volatile AutomineConfig config;
    private volatile AutomineState state;
    private volatile AutomineWindow window;
    private volatile boolean pendingOpen;

    @SubscribeEvent
    public void onHudInit(ClientHUDInitializedEvent event) {
        try {
            this.hud = (HeadsUpDisplay) event.getHud();
            File props = new File("mods/automine/automine.properties");
            this.config = AutomineConfig.load(props);
            this.state = new AutomineState(
                    this::dispatchAction,
                    ProxyClientHook::getCurrentStamina,
                    config.stopPhrases,
                    config.staminaFullThreshold,
                    config.staminaWatchdogMs);
            logger.info("[automine] initialised — batch=" + config.defaultBatchSize
                    + " threshold=" + config.staminaFullThreshold);
        } catch (Throwable t) {
            logger.log(Level.SEVERE, "[automine] HUD init failed", t);
        }
    }

    @SubscribeEvent
    public void onConsoleInput(ClientConsoleInputEvent event) {
        if (!"automine".equals(event.getCommand())) return;
        event.cancel();
        if (hud == null || state == null) {
            logger.warning("[automine] HUD not ready yet — try again after login");
            return;
        }
        // Defer window creation/registration to the next tick to mirror the
        // CME-safe pattern from ActionClientMod.
        pendingOpen = true;
    }

    @SubscribeEvent
    public void onTick(ClientTickEvent event) {
        if (pendingOpen) {
            pendingOpen = false;
            ensureWindow();
        }
        if (state != null) state.onTick(System.currentTimeMillis());
        if (window != null) window.setStatus(describe(state));
    }

    @SubscribeEvent
    public void onStamina(ClientStaminaChangedEvent event) {
        if (state != null) state.onStaminaChanged(event.getNewStamina());
    }

    @SubscribeEvent
    public void onEventMessage(ClientEventMessageReceivedEvent event) {
        if (state != null) state.onMessage(event.getText());
    }

    private void ensureWindow() {
        if (window != null) return;
        try {
            window = new AutomineWindow(config, new AutomineWindow.Listener() {
                @Override public void onStart(AutomineWindow.Direction dir, int batchSize) {
                    short id;
                    switch (dir) {
                        case UP:   id = config.actionUp; break;
                        case DOWN: id = config.actionDown; break;
                        default:   id = config.actionForward;
                    }
                    state.start(id, batchSize);
                }
                @Override public void onPause() { state.pause(); }
                @Override public void onClose() { state.pause(); window = null; }
            });
            ModHud.get().register(window);
        } catch (Throwable t) {
            logger.log(Level.WARNING, "[automine] failed to open window", t);
            if (hud != null) hud.consoleOutput("automine: window failed (see log)");
        }
    }

    private void dispatchAction(short actionId) {
        try {
            PlayerActionDispatcher.dispatch(hud, actionId, "tile");
        } catch (Throwable t) {
            logger.log(Level.WARNING, "[automine] dispatch failed", t);
            state.pause();
        }
    }

    private static String describe(AutomineState s) {
        if (s == null) return "idle";
        switch (s.getPhase()) {
            case IDLE: return "idle";
            case DISPATCHING: return "mining (" + s.getSentInBatch() + "/" + s.getBatchSize() + ")";
            case WAITING_STAMINA: {
                Float now = ProxyClientHook.getCurrentStamina();
                int pct = now == null || Float.isNaN(now) ? -1 : (int) (now * 100);
                return pct < 0 ? "waiting for stamina" : "waiting for stamina (" + pct + "%)";
            }
            case STOPPED: return "stopped: " + s.getStopReason();
            default: return "?";
        }
    }
}
```

- [ ] **Step 2: Build and run all module checks**

```bash
cd ~/Scripts/Games/WurmUnlimited/WurmModLoader-CommunityMods
./gradlew :client-mods:automine:build
```

Expected: succeeds. Tests still pass.

- [ ] **Step 3: Commit**

```bash
git add client-mods/automine/src/main/java/com/garward/wurmmodloader/mods/automine/AutomineClientMod.java
git commit -m "feat(automine): mod entry, console command, event glue"
```

---

## Phase 7 — Deploy and live-test

### Task 12: Apply patched client jar

The framework's two new bytecode patches change `client.jar`, so the patched jar must be regenerated.

- [ ] **Step 1: Run the canonical client patcher**

Per memory, `patch-client.sh` is the canonical entry — it wires patches into `ClientPatcher.patchJarFile`. Verify the path and invoke:

```bash
ls ~/Scripts/Games/WurmUnlimited/WurmModLoader-Client/patch-client.sh
~/Scripts/Games/WurmUnlimited/WurmModLoader-Client/patch-client.sh
```

Expected: outputs `Patched client.jar at <path>` or similar; the patched jar replaces the file in `WurmLauncher/`.

- [ ] **Step 2: Deploy the action + automine mod jars manually**

Per memory, client-side `build-and-deploy.sh` doesn't copy mod jars. Use the gradle `deployMod` tasks:

```bash
cd ~/Scripts/Games/WurmUnlimited/WurmModLoader-CommunityMods
./gradlew :client-mods:action:deployMod :client-mods:automine:deployMod
```

Verify the mod folders ended up where the launcher reads them:

```bash
ls "/home/garward/.local/share/Steam/steamapps/common/Wurm Unlimited/WurmLauncher/mods/automine/"
ls "/home/garward/.local/share/Steam/steamapps/common/Wurm Unlimited/WurmLauncher/mods/action/"
```

Expected: both directories contain a `.jar` and a `mod.properties`.

- [ ] **Step 3: Launch the client**

Per memory, the bundled JRE crashes on modern glibc. Use the system JRE script:

```bash
~/Scripts/Games/WurmUnlimited/WurmModLoader-Client/launch-client-systemjre.sh
```

Expected: client window opens and reaches the login screen. If the patcher / loader logged any SEVERE during startup, capture them now (in a separate terminal: `tail -F ~/.local/share/Steam/steamapps/common/Wurm\ Unlimited/WurmLauncher/PlayerFiles/logs/_wurmclient.txt`).

- [ ] **Step 4: Smoke-test the manual command**

In the client console (default `F1`):

```
automine
```

Expected: a small `Auto Mine` window appears. Console line `[automine] initialised — …` should be in the log.

- [ ] **Step 5: Confirm `act` still works (regression check)**

```
act_show on
act 38 tile
```

Expected: action ids appear in right-click menus; `act 38 tile` runs whatever action #38 is (mine forward) on the current tile.

If either `automine` or `act` fail at this point, stop here — the dispatcher / patch wiring needs investigation before Task 13.

- [ ] **Step 6: No commits required for Task 12**

(Pure deploy/test. The next task confirms the loop end-to-end and writes the research doc.)

---

### Task 13: Live-test the loop, refine stop phrases, write the research doc

**Files:**
- Create: `~/Scripts/Games/WurmUnlimited/WurmModLoader-Client/docs/research/mining-stop-phrases.md`
- Possibly modify: `~/Scripts/Games/WurmUnlimited/WurmModLoader-CommunityMods/client-mods/automine/src/dist/automine.properties`

- [ ] **Step 1: Find a rock face**

In-game: stand next to a cave wall. Equip a pickaxe in the active toolbelt slot.

- [ ] **Step 2: Run the loop**

`F1 → automine`. In the popup: pick `Forward`, batch `3`, click `Start`.

Expected:
- Status cycles `mining (1/3) → mining (2/3) → mining (3/3) → waiting for stamina (NN%) → mining (1/3) …`.
- Mine actions visibly fire at the wall; shards land in the inventory.

- [ ] **Step 3: Provoke a stop**

Continue mining until the wall changes / vein exhausts. Note the **exact text** the server emits in the event tab when this happens — open `~/.local/share/Steam/steamapps/common/Wurm Unlimited/WurmLauncher/PlayerFiles/<account>/logs/_Event.<date>.txt` after the session, or watch the client window.

If the loop did **not** stop on the message you saw, append a new pattern to `automine.properties`:

```properties
stopPhrase.6=<your new pattern as case-insensitive regex>
```

Re-run `./gradlew :client-mods:automine:deployMod` and reload (`#reloadmods automine` in the server console — but this is a client mod, so easier: relaunch client).

- [ ] **Step 4: Test other directions**

Confirm `Up` works on a ceiling and `Down` works on a floor (server may emit "topology" rejection — already in the default stop list).

- [ ] **Step 5: Test pause + close**

- Click `Pause` mid-batch → status should read `stopped: paused`. No more actions sent.
- Close the window mid-loop → status irrelevant (window gone), but the next tick must show no further dispatches in the action queue.

- [ ] **Step 6: Write `mining-stop-phrases.md`**

Capture exact server strings, with citations. Skeleton:

```markdown
# Mining stop-phrase research

Verbatim event-log text emitted by the server for terminal mining outcomes.
Used to seed `automine.properties` defaults.

| Phrase                                              | Trigger                  | Source                                          |
| --------------------------------------------------- | ------------------------ | ----------------------------------------------- |
| You are too unskilled to mine here.                 | Skill gate               | TileRockBehaviour.java:2176                     |
| The topology here makes it impossible to mine in a good way. | Slope / mine-up-on-floor | TileRockBehaviour.java:529, 1579, 2128 (verify) |
| The water is too deep to mine.                      | Underwater               | TileRockBehaviour.java:345, 354, 2190           |
| This tile is protected by the gods. You can not mine here. | Protected           | TileRockBehaviour.java:349, 2194                |
| The surrounding area needs to be rock before you mine. | Wrong tile type        | TileRockBehaviour.java:2226                     |
| You cannot mine in a building.                      | Building                 | TileRockBehaviour.java:2236                     |
| You cannot mine next to a fence.                    | Fence                    | TileRockBehaviour.java:2255, 2262, 2269         |

## Live-discovered (Task 13)

- _<add any phrases you actually saw the server emit in playtesting>_
```

- [ ] **Step 7: Commit research and any property tweaks**

```bash
cd ~/Scripts/Games/WurmUnlimited/WurmModLoader-Client
git add docs/research/mining-stop-phrases.md
git commit -m "docs(research): mining stop phrases for automine"

cd ~/Scripts/Games/WurmUnlimited/WurmModLoader-CommunityMods
git add client-mods/automine/src/dist/automine.properties
git commit -m "chore(automine): tune stop phrases from live testing" \
   || echo "(no property changes — clean)"
```

---

## Phase 8 — Code index refresh

### Task 14: Regenerate the code index

Per the project CLAUDE.md, regenerate after any structural change so future searches are accurate.

- [ ] **Step 1: Server-side framework index**

```bash
cd ~/Scripts/Games/WurmUnlimited/WurmModLoader
python3 index_code_index.py
```

(If the index also covers WurmModLoader-Client — check for a similar script there. If a separate one exists, run it too.)

- [ ] **Step 2: Verify the new symbols are searchable**

```bash
codeindex search "PlayerActionDispatcher" || true
codeindex search "ClientStaminaChangedEvent" || true
codeindex search "AutomineState" || true
```

Expected: each query returns one or more hits.

- [ ] **Step 3: Commit index updates if any**

```bash
cd ~/Scripts/Games/WurmUnlimited/WurmModLoader
git status
# If the index files changed, stage and commit them.
```

---

## Self-Review

**Spec coverage check** (against `2026-04-27-automine-client-mod-design.md`):

- §3.1 `ClientEventMessageReceivedEvent` → Task 4 ✓
- §3.2 `ClientStaminaChangedEvent` → Task 2 ✓
- §3.3 `ProxyClientHook.getCurrentStamina()` accessor → Task 2 step 3 ✓
- §3.4 `PlayerActionDispatcher` eventlogic + helpers → Task 1 ✓
- §4.1 New mod layout → Task 7 ✓
- §4.2 Console command → Task 11 (`onConsoleInput`) ✓
- §4.3 UI (direction radio, batch ±, status, Start/Pause, close) → Task 10 ✓
- §4.4 State machine + watchdog + stamina event + stop trigger → Task 9 (states + tests) + Task 11 (event wiring) ✓
- §4.5 Stop phrases (default list, configurable) → Task 7 step 4, Task 8 (`AutomineConfig`), Task 13 (refinement) ✓
- §4.6 Configuration (`automine.properties`) → Task 7 step 4, Task 8 ✓
- §5 Error handling (HUD-not-ready, dispatcher throws, missed-stamina watchdog, disconnect, mid-batch close) → Tasks 9 (watchdog test) + 11 (HUD-null guards, dispatch try/catch, close→pause) ✓
- §6 Testing (state machine unit tests + manual playthrough) → Task 9 (unit) + Task 13 (manual) ✓
- §7 Out-of-scope items not in plan ✓ (intentional)

**Placeholder scan:** No "TBD", "implement later" left. Two **research-then-act** steps are explicitly tasks (Task 3, Task 13) rather than placeholders. The `ModLabel.setText` fallback in Task 10 is documented inline as "verify and adjust" — acceptable because the fallback path is concrete.

**Type consistency:**
- `AutomineState.start(short, int)` — Task 9 ✓ matches Task 11 caller ✓
- `AutomineState.onMessage(String)` — Task 9 ✓ matches Task 11 caller ✓
- `AutomineState.onStaminaChanged(float)` — Task 9 ✓ matches Task 11 caller ✓
- `AutomineState.onTick(long)` — Task 9 ✓ matches Task 11 caller ✓
- `AutomineWindow.Listener` interface — Task 10 ✓ matches anonymous impl in Task 11 ✓
- `AutomineConfig.actionForward / actionUp / actionDown / defaultBatchSize / staminaFullThreshold / staminaWatchdogMs / stopPhrases` — Task 8 ✓ matches consumers ✓
- `PlayerActionDispatcher.dispatch(HeadsUpDisplay, short, String)` — Task 1 ✓ matches consumers ✓
- `ProxyClientHook.fireClientStaminaChangedEvent(float)` / `getCurrentStamina()` / `fireClientEventMessageReceivedEvent(String, String, byte)` — Tasks 2/4 ✓

No mismatches found.
