# WurmModLoader Client Patcher

## Overview

The WurmModLoader Client Patcher is a Java Agent that modifies Wurm client classes at runtime using bytecode manipulation. It injects event hooks into the client without modifying the original JAR files.

## How It Works

### Architecture

```
1. Client Startup
   └─> Java Agent loads (ClientPatcher.premain())
       └─> CorePatches.registerAll()
           └─> Registers all bytecode patches

2. Class Loading (for each Wurm class)
   └─> WurmClientTransformer.transform()
       └─> Check if patches exist for this class
           └─> If yes: PatchManager.patchClass()
               └─> Load class with Javassist
               └─> Apply all patches in priority order
               └─> Return modified bytecode

3. Patched Class Loads
   └─> Hooks fire events
       └─> ProxyClientHook.fireXyzEvent()
           └─> ClientHook.fireXyz()
               └─> EventBus.post() (when implemented)
```

### Components

**BytecodePatch Interface** (API)
```java
public interface BytecodePatch {
    String getTargetClassName();
    void apply(CtClass ctClass) throws Exception;
    int getPriority();
}
```

**PatchRegistry** (API)
- Thread-safe storage for all patches
- Organizes by target class name
- Sorts by priority (highest first)

**PatchManager** (Core)
- Uses Javassist ClassPool to load classes
- Applies patches in order
- Tracks success/failure statistics

**CorePatches** (Core)
- Registers all built-in patches
- Called during patcher initialization

**ClientPatcher** (Patcher)
- Java Agent entry point (`premain()`)
- ClassFileTransformer implementation
- Main class for standalone mode

## Current Patches

### ClientInitPatch
- **Target**: `com.wurmonline.client.WurmClientBase`
- **Method**: `init()` or `startClient()`
- **Hook**: Fires `ClientInitEvent` after initialization
- **Priority**: 1000 (high)

### ClientTickPatch
- **Target**: `com.wurmonline.client.LwjglClient`
- **Method**: `run()` or `gameLoop()`
- **Hook**: Fires `ClientTickEvent` every frame
- **Priority**: 500 (medium)

## Usage

### Recommended: Java Agent Mode

**Linux/Mac:**
```bash
java -javaagent:wurmmodloader-client-0.1.0.jar \
     -jar /path/to/client.jar
```

**Windows:**
```cmd
java -javaagent:wurmmodloader-client-0.1.0.jar ^
     -jar C:\path\to\client.jar
```

**With Launcher Scripts:**
```bash
# Linux/Mac
./scripts/launch-client.sh

# Windows
scripts\launch-client.bat
```

### Alternative: Standalone Mode (Future)

```bash
java -jar wurmmodloader-client-0.1.0.jar /path/to/client.jar
```

This will create `client-patched.jar` (not yet implemented).

## Adding New Patches

### 1. Create the Patch Class

```java
package com.garward.wurmmodloader.client.core.bytecode.patches;

import com.garward.wurmmodloader.client.api.bytecode.BytecodePatch;
import javassist.CtClass;
import javassist.CtMethod;

public class MyCustomPatch implements BytecodePatch {

    @Override
    public String getTargetClassName() {
        return "com.wurmonline.client.SomeClass";
    }

    @Override
    public void apply(CtClass ctClass) throws Exception {
        CtMethod method = ctClass.getDeclaredMethod("someMethod");

        // Insert hook after method executes
        method.insertAfter(
            "com.garward.wurmmodloader.client.modloader.ProxyClientHook.fireMyCustomEvent();"
        );
    }

    @Override
    public int getPriority() {
        return 100;  // Default priority
    }

    @Override
    public String getDescription() {
        return "My custom patch description";
    }
}
```

### 2. Create the Event (if needed)

```java
package com.garward.wurmmodloader.client.api.events.custom;

import com.garward.wurmmodloader.client.api.events.base.Event;

public class MyCustomEvent extends Event {
    public MyCustomEvent() {
        super(false);  // Not cancellable
    }
}
```

### 3. Add Hook Method

In `ProxyClientHook.java`:
```java
public static void fireMyCustomEvent() {
    getInstance().fireMyCustom();
}
```

In `ClientHook.java`:
```java
public void fireMyCustom() {
    eventBus.post(new MyCustomEvent());
}
```

### 4. Register the Patch

In `CorePatches.java`:
```java
PatchRegistry.register(new MyCustomPatch());
```

### 5. Rebuild

```bash
./gradlew build
```

## Manifest Attributes

The uber-JAR manifest includes:

```
Main-Class: com.garward.wurmmodloader.client.patcher.ClientPatcher
Premain-Class: com.garward.wurmmodloader.client.patcher.ClientPatcher
Can-Redefine-Classes: true
Can-Retransform-Classes: true
```

These enable the JAR to work both as:
- A Java Agent (`-javaagent`)
- A standalone executable (`java -jar`)

## Debugging

### Enable Verbose Logging

Add to JVM args:
```bash
java -Djava.util.logging.config.file=logging.properties \
     -javaagent:wurmmodloader-client-0.1.0.jar \
     -jar client.jar
```

### Verify Patches Are Applied

The patcher logs when patches are applied:
```
INFO: Patching class: com.wurmonline.client.WurmClientBase with 1 patch(es)
INFO:   Applying patch: Client initialization hook
INFO: Successfully patched com.wurmonline.client.WurmClientBase
```

### Check Patch Count

On startup you should see:
```
INFO: Registered 2 core patches
INFO: Target classes: [com.wurmonline.client.WurmClientBase, com.wurmonline.client.LwjglClient]
```

### Common Issues

**"No patches registered"**
- CorePatches.registerAll() was not called
- Check that premain() executed

**"Failed to patch class"**
- Target class name is wrong
- Method name doesn't exist in that class
- Syntax error in injected code

**"Class already loaded"**
- Patches must be applied before classes load
- Ensure `-javaagent` comes before `-jar`

## Technical Details

### Javassist Usage

We use Javassist for bytecode manipulation because:
- Simpler API than ASM
- Source-level code injection (`insertAfter`, `insertBefore`)
- Good error messages
- Widely used in modding (Minecraft, Wurm server mods)

### Why Java Agent?

**Advantages:**
- ✅ No modification of original JARs
- ✅ Runtime transformation
- ✅ Can patch any class before it loads
- ✅ Easy to enable/disable (remove `-javaagent`)
- ✅ Works with Steam updates

**Disadvantages:**
- ⚠️ Requires command-line argument
- ⚠️ Can't patch classes already loaded

### ClassPool Configuration

```java
ClassPool classPool = ClassPool.getDefault();
classPool.appendClassPath(new LoaderClassPath(ClassLoader.getSystemClassLoader()));
```

This ensures Javassist can find:
- Wurm client classes
- Our modloader classes
- JDK classes

### Patch Ordering

Patches with higher priority are applied first:
```
Priority 1000: ClientInitPatch (critical)
Priority 500:  ClientTickPatch (normal)
Priority 100:  Default priority
```

Use priorities when patches depend on each other.

## Performance

### Startup Impact

- **Patch Registration**: < 1ms
- **Per-Class Transform**: ~10-50ms
- **Total Overhead**: ~100-500ms at startup

Only patched classes incur overhead. Most classes pass through unchanged.

### Runtime Impact

- **Event Firing**: ~0.01ms per event
- **Tick Event**: 60+ times/second, negligible overhead

Bytecode patches add a simple method call, which is extremely fast.

## Safety

### What Can Go Wrong?

1. **Syntax errors in injected code** → Class fails to load
2. **Wrong method name** → Patch fails, class loads normally
3. **Infinite loops in hooks** → Client hangs
4. **Exceptions in hooks** → May crash client

### Best Practices

✅ **Keep patches minimal** - Only inject hook calls
✅ **Test thoroughly** - Bad patches can crash the client
✅ **Use fully qualified names** - Avoid import issues
✅ **Handle failures gracefully** - Log and continue
✅ **Version your patches** - Client updates may break them

### Rollback

If something goes wrong:
1. Remove the `-javaagent` argument
2. Client runs without patches
3. No permanent damage to files

## Future Enhancements

- [ ] Standalone JAR patching mode
- [ ] Patch verification (dry-run mode)
- [ ] Hot-reload patches during development
- [ ] Patch conflict detection
- [ ] Performance profiling for patches
- [ ] GUI for enabling/disabling patches

---

**Last Updated**: 2025-11-19
**Version**: 0.1.0
**Status**: Java Agent mode complete, Standalone mode planned
