# Automine Client Mod — Design

**Date:** 2026-04-27
**Status:** Approved — ready for implementation plan
**Scope:** New community client mod + supporting framework events / eventlogic

---

## 1. Goal

Provide a client-side automining loop for servers that allow client automation:

1. User opens a popup window (initially via console command).
2. User picks a mine direction (Forward / Up / Down) and a per-batch action count.
3. On Start, the mod fires N mine actions, waits for stamina to refill, and repeats.
4. Loop halts on tile-break server messages, on Pause, or when the window is closed.

This document covers both the new mod and the supporting framework changes that
unblock it. Two of the three framework additions are generic and useful well
beyond automining; this is intentional — we want broad event coverage and we
avoid one-off reflection hacks in mods.

## 2. Non-goals

- Server-side enforcement of any kind. This is purely a client mod.
- Camera control, pathfinding, or any "smart" behavior beyond batch + wait.
- Multi-tile or multi-direction sequencing in one session (one direction at a
  time; user re-opens the window to switch).
- Hotkey binding. The window opens via console command for now; a keybind can
  be wired through the existing `action` mod's keybind UI later if desired.
- Persistent loop state across reconnects. Stop on disconnect.

## 3. Framework additions

All three live in `WurmModLoader-Client`. None are automine-specific.

### 3.1 `ClientEventMessageReceivedEvent` (api + core patch)

Fires whenever the server pushes a line into an event/chat tab on the client.

- **Package:** `com.garward.wurmmodloader.client.api.events.client`
- **Fields:**
  - `String text` — the rendered line (may include color tags; provide a
    `getPlainText()` helper that strips them).
  - `String window` — the chat-tab/window identifier the server targeted (e.g.
    `"Event"`, `"Combat"`, `"GL-Freedom"`).
  - `byte messageType` — vanilla's `MessageServer` type byte if available.
- **Cancellable:** yes. Cancelling suppresses display (future filter mods).
- **Patch target:** the single chokepoint where incoming messages are dispatched
  to the HUD's text log. Candidates to inspect during implementation:
  - `com.wurmonline.client.comm.ServerConnectionListenerClass` (CMD_MESSAGE /
    CMD_NORMAL_MESSAGE handlers), or
  - `com.wurmonline.client.renderer.gui.HeadsUpDisplay.addText`.
  Pick whichever is the single funnel; patch in `wurmmodloader-client-core/.../bytecode/patches/`.

### 3.2 `ClientStaminaChangedEvent` (api + core patch)

Fires whenever the client receives a stamina update from the server.

- **Package:** `com.garward.wurmmodloader.client.api.events.client`
- **Fields:**
  - `float oldStamina` (0.0–1.0)
  - `float newStamina` (0.0–1.0)
- **Cancellable:** no.
- **Patch target:** the `CMD_STAMINA` handler in the client communicator. Capture
  the old value before it's overwritten, then fire the event with both values.

### 3.3 `ProxyClientHook.getCurrentStamina()` accessor

Plain getter (no event) for tick-based polling and watchdogs. Returns the most
recently observed stamina value (0.0–1.0). The event hook is the source of
truth for "changed"; the accessor lets mods read on demand.

### 3.4 `PlayerActionDispatcher` eventlogic

Extract the existing `ActionClientMod.parseAct(short id, String target)` and
its `Reflect` helpers into framework eventlogic so any mod can dispatch a
`PlayerAction` against the same target keywords without depending on the
`action` mod.

- **Path:** `wurmmodloader-client-api/src/main/java/com/garward/wurmmodloader/client/api/events/eventlogic/action/`
  - `PlayerActionDispatcher.java` — `static void dispatch(HeadsUpDisplay hud, short actionId, String target)`
    plus the existing target-keyword switch (`hover`, `body`, `tile`,
    `tile_n/s/e/w/ne/nw/se/sw`, `tool`, `selected`, `area`, `toolbelt`, `@tbN`,
    `@eqN`, `@nearbyR`).
  - `ClientItemReflect.java` — the small reflective accessors currently in
    `action`'s `Reflect` (`getBodyItem`, `getActiveToolItem`, `getSelectedUnit`,
    `getGroundItems`, `getFrameFromSlotnumber`). Fold into
    `PlayerActionDispatcher` if the surface is small enough.
- **`action` mod migration:**
  - `parseAct` becomes a one-line delegate: `PlayerActionDispatcher.dispatch(hud, id, target)`.
  - Local `Reflect.java` deleted (or kept temporarily as a thin re-export only
    if external callers exist; prefer outright deletion).
  - Behavior unchanged from the user's perspective.

## 4. New mod: `automine`

### 4.1 Layout

```
WurmModLoader-CommunityMods/client-mods/automine/
├── build.gradle.kts
└── src/
    ├── dist/
    │   ├── mod.properties
    │   └── automine.properties        # batch size, mine action ids, stop phrases
    └── main/java/com/garward/wurmmodloader/mods/automine/
        ├── AutomineClientMod.java     # entry, console cmd, event subscribers
        ├── AutomineWindow.java        # popup UI
        └── AutomineState.java         # state machine, loop driver
```

No dependency on the `action` mod. Calls `PlayerActionDispatcher` directly.

### 4.2 Console command

`automine` — toggles the popup window open/closed (focuses if already open).
Registered through `ClientConsoleInputEvent`.

### 4.3 UI (popup window)

A small `ModWindow` (framework GUI widgets, *not* declarativeui — purely
client-local).

```
┌─ Auto Mine ────────────────────┐
│ Direction:  (•) Forward         │
│             ( ) Up              │
│             ( ) Down            │
│ Actions per batch: [ 3 ]        │
│                                 │
│ Status: idle                    │
│                                 │
│ [ Start ]   [ Pause ]   [ X ]   │
└─────────────────────────────────┘
```

- **Direction** maps to action IDs `38` (Mine), `39` (Mine Up), `40` (Mine Down).
  Override via `automine.properties` keys `actionId.forward / up / down` for
  servers that re-number actions.
- **Actions per batch:** integer 1–10. Default 3 (matches Wurm's client action
  queue cap).
- **Start / Pause / Close** drive the state machine (see §4.4).
- **Status line** examples: `idle`, `mining (2/3)`, `waiting for stamina (87%)`,
  `stopped: tile broke`, `stopped: paused`.

### 4.4 State machine

```
states: IDLE, DISPATCHING, WAITING_STAMINA, STOPPED

IDLE        --Start-->            DISPATCHING (i = 0)
DISPATCHING --i < N-->            DISPATCHING (fire action; i++)
DISPATCHING --i == N-->           WAITING_STAMINA
WAITING_STAMINA --stam >= 0.99--> DISPATCHING (i = 0)
*           --stop phrase-->      STOPPED
*           --Pause / close-->    STOPPED
STOPPED     --Start-->            DISPATCHING (i = 0)
```

- **Dispatch call:** `PlayerActionDispatcher.dispatch(hud, mineActionId, "tile")`
  per action.
- **Stamina trigger:** subscribe to `ClientStaminaChangedEvent`; advance from
  `WAITING_STAMINA` when `newStamina >= 0.99f`. (0.99 not 1.0 because the
  server clamps near-full stamina at 99.x% of cap.)
- **Watchdog:** subscribe to `ClientTickEvent`; if state has been
  `WAITING_STAMINA` for >60s and `ProxyClientHook.getCurrentStamina() >= 0.99f`
  (event was missed), advance manually and log a warning.
- **Stop trigger:** subscribe to `ClientEventMessageReceivedEvent`; if
  `getPlainText()` matches any configured stop-phrase regex, transition to
  `STOPPED`.

### 4.5 Stop phrases

Defaults (case-insensitive regex; configurable via `automine.properties`):

- `you have mined the (rock|wall) clean`
- `the (rock|wall|vein) (collapses|crumbles|is mined out)`
- `you cannot dig further (up|down)`
- `there is no rock there`

Exact strings will be confirmed against decompiled server source during
implementation and the defaults adjusted accordingly.

### 4.6 Configuration (`automine.properties`)

```properties
# Action IDs (vanilla defaults — override only if server re-numbers).
actionId.forward=38
actionId.up=39
actionId.down=40

# Default batch size on first window open.
defaultBatchSize=3

# Stop phrases (regex, case-insensitive). One per line, suffix index.
stopPhrase.0=you have mined the (rock|wall) clean
stopPhrase.1=the (rock|wall|vein) (collapses|crumbles|is mined out)
stopPhrase.2=you cannot dig further (up|down)
stopPhrase.3=there is no rock there

# Stamina threshold to consider "full" (0.0-1.0).
staminaFullThreshold=0.99

# Watchdog timeout for missed stamina events, ms.
staminaWatchdogMs=60000
```

## 5. Error handling and edge cases

| Case                                            | Behavior                                                             |
| ----------------------------------------------- | -------------------------------------------------------------------- |
| HUD not initialized when console cmd run        | Status `"waiting for HUD"`; auto-open on next `ClientHUDInitializedEvent`. |
| `PlayerActionDispatcher` throws (reflect break) | Log SEVERE once; transition to STOPPED; status `"stopped: dispatch failed"`. |
| Stamina event missed (packet drop)              | Watchdog (§4.4) kicks the next batch.                                |
| Server rejects action (moved off rock)          | No client-side signal; subsequent batches no-op silently. User pauses. |
| Disconnect mid-loop                             | State goes STOPPED on next tick (HUD null check); loop won't resume. |
| Window closed while DISPATCHING                 | STOPPED immediately. Already-sent actions still execute server-side. |

## 6. Testing

Manual on a live server:

1. Mine a rock face manually until a fresh wall remains. Run `automine`,
   pick Forward, batch=3, Start. Confirm status cycles
   `mining → waiting → mining` and the loop halts on the breakthrough message.
2. Pause mid-batch — confirm no further actions sent. Close window mid-batch — same.
3. Test Up on a ceiling, Down on a floor — confirm direction-specific stop
   phrases trigger.
4. Reload the mod (`/reload` or restart) and run again — confirm config
   reloads.

Unit tests (worth writing, isolated from the client):

- `AutomineState` transitions (Start, batch fill, stamina full, stop phrase,
  pause, close) — pure state machine, no HUD dependency.

GUI/integration tests are skipped — manual verification covers them.

## 7. Out-of-scope follow-ups (for later sessions)

- Quality-bump auto-stop (stop when QL crosses a threshold).
- Auto-equip pickaxe from toolbelt slot before each batch.
- Hotkey binding via the existing `action` mod's keybind UI.
- Multi-direction sequences (e.g. forward → up → forward).
- Auto-drop low-QL shards.
