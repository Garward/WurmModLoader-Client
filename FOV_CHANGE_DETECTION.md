# FOV Change Detection System

## Overview

The FOV (Field of View) change detection system enables client mods to detect and react when the player's horizontal FOV setting changes.

## Architecture

This implementation follows **clean architecture** - the bytecode patch contains ZERO logic, only routing to the hook layer where all decision-making happens.

```
RangeOption.set(newValue)
  ↓
ProxyClientHook.fireFOVChangedEventIfApplicable(this, oldVal, newVal) [STATIC]
  ↓ Logic checks:
    - Is this the fovHorizontal option?
    - Did the value actually change?
  ↓
ClientHook.fireFOVChanged(oldVal, newVal) [INSTANCE]
  ↓
EventBus.post(new FOVChangedEvent(oldVal, newVal))
  ↓
@SubscribeEvent handlers in mods
```

## Files Created/Modified

### Created Files

1. **`FOVChangedEvent.java`** (API)
   - Immutable event with `oldFOV`, `newFOV`, and `getFOVDelta()`
   - Location: `wurmmodloader-client-api/.../events/client/FOVChangedEvent.java`

2. **`FOVChangePatch.java`** (Core)
   - Surgical bytecode patch targeting `RangeOption.set(int)`
   - Contains ZERO logic - only routing
   - Location: `wurmmodloader-client-core/.../bytecode/patches/FOVChangePatch.java`

### Modified Files

1. **`ClientHook.java`**
   - Added `fireFOVChanged(int oldFOV, int newFOV)` instance method
   - Creates and posts FOVChangedEvent to EventBus

2. **`ProxyClientHook.java`**
   - Added `fireFOVChangedEventIfApplicable(Object option, int oldValue, int newValue)` static method
   - Contains ALL logic:
     - Checks if option is `fov_horizontal` using reflection
     - Checks if value actually changed
     - Only fires event if both conditions are true

3. **`CorePatches.java`**
   - Registered `FOVChangePatch` in `registerAll()`

## Usage Example

```java
import com.garward.wurmmodloader.client.api.events.base.SubscribeEvent;
import com.garward.wurmmodloader.client.api.events.client.FOVChangedEvent;

public class MyCameraMod {

    @SubscribeEvent
    public void onFOVChanged(FOVChangedEvent event) {
        logger.info("FOV changed from " + event.getOldFOV() + " to " + event.getNewFOV());

        // Recalculate camera projection matrix
        updateCameraProjection(event.getNewFOV());

        // Adjust HUD elements based on new FOV
        if (event.getNewFOV() > 90) {
            adjustWideAngleHUD();
        }
    }
}
```

## How FOV Changes are Detected

The patch targets `com.wurmonline.client.options.RangeOption.set(int)` which is called whenever:
- User changes FOV in settings UI
- FOV is loaded from saved settings
- Console command changes FOV
- Any programmatic FOV change

### Original Code

```java
public void set(int newValue) {
    this.value = Math.max(this.low, Math.min(this.high, newValue));
}
```

### Patched Code

```java
public void set(int newValue) {
    int _wmlOldValue = this.value; // Capture BEFORE assignment
    this.value = Math.max(this.low, Math.min(this.high, newValue));
    ProxyClientHook.fireFOVChangedEventIfApplicable(this, _wmlOldValue, this.value);
}
```

## Architecture Compliance

✅ **100% Clean Architecture:**
- Patch contains ZERO logic (no conditionals, loops, calculations)
- All decision-making in ProxyClientHook layer
- Event is immutable data-only DTO
- Follows: Patch → ProxyHook → Hook → EventBus

## Testing

To test FOV change detection:

1. **Build the client modloader:**
   ```bash
   cd <repo-root>   # the WurmModLoader-Client checkout
   ./gradlew :wurmmodloader-client-api:build :wurmmodloader-client-core:build :wurmmodloader-client-patcher:build
   ```

2. **Create a test mod** that subscribes to `FOVChangedEvent`

3. **Launch Wurm client** and change FOV in settings

4. **Verify event fires** by checking logs for:
   ```
   🎥 Firing FOVChangedEvent (oldFOV=80, newFOV=90)
   ```

## Technical Details

### FOV Range
- Default: 80 degrees
- Range: 60-110 degrees (defined in Options.java)

### Event Properties
- `getOldFOV()`: Previous FOV value (60-110)
- `getNewFOV()`: New FOV value (60-110)
- `getFOVDelta()`: Change in FOV (positive = increased, negative = decreased)

### Conflict Key
- `client.options.fov`: Prevents other patches from conflicting with FOV detection

## Version History

- **0.2.0** - Initial implementation of FOV change detection
  - Created FOVChangedEvent
  - Created FOVChangePatch (surgical, no logic)
  - Added hook methods in ClientHook and ProxyClientHook
  - Registered patch in CorePatches

## See Also

- `ClientInitEvent` - Fires when client initializes
- `ClientTickEvent` - Fires every frame
- `ServerCapabilitiesReceivedEvent` - Fires when server capabilities are received
