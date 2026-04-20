# WurmModLoader Client Installation Guide

## 🎯 Overview

The WurmModLoader client system provides a modern, event-driven modding framework for Wurm Unlimited that integrates with the server modloader architecture.

**Key Improvement over Ago's Loader**: Instead of requiring manual JVM arguments in Steam, we patch the client once and it auto-loads mods every time you launch through Steam normally.

## 📋 Installation (Automated)

Simply run the installer script:

```bash
# Linux/macOS
cd /path/to/WurmModLoader-Client
./install-client-modloader.sh
```
```bat
:: Windows
cd C:\path\to\WurmModLoader-Client
install-client-modloader.bat
```

> **Windows users:** every `./foo.sh` and `./gradlew` command in this guide
> has a Windows equivalent — `foo.bat` and `gradlew.bat`. Run them from
> `cmd.exe` or PowerShell in the repo root. Set `WURM_CLIENT_DIR` if your
> Wurm Unlimited install isn't at the Steam default
> (`C:\Program Files (x86)\Steam\steamapps\common\Wurm Unlimited\WurmLauncher\`).

The installer will:
1. Auto-detect your Steam installation
2. Build the modloader
3. Deploy the modloader JAR
4. Patch `client.jar` to bootstrap the modloader
5. Create a backup of the original client

## 📋 Installation (Manual)

If you prefer to install manually:

### 1. Build the Modloader

```bash
./gradlew clean build dist
```

### 2. Deploy to Wurm Unlimited

```bash
# Copy modloader JAR to WurmLauncher directory
cp build/distributions/WurmModloader-Client-*/wurmmodloader-client-*.jar \
   ~/.local/share/Steam/steamapps/common/Wurm\ Unlimited/WurmLauncher/
```

### 3. Patch the Client

```bash
cd ~/.local/share/Steam/steamapps/common/Wurm\ Unlimited/WurmLauncher/
java -jar wurmmodloader-client-0.1.0.jar
```

This will:
- Back up `client.jar` to `client.jar.backup`
- Patch `WurmMain.main()` to initialize the modloader
- Client will now auto-load mods when launched

## 🚀 Usage

### Installing Mods

1. Create mods directory: `~/.local/share/Steam/steamapps/common/Wurm Unlimited/WurmLauncher/mods/`
2. Place mod JARs in the mods directory
3. Launch Wurm Unlimited through Steam normally
4. Mods load automatically at startup

### Launching the Game

**Just launch through Steam as normal!** No special parameters needed.

The patched client will:
1. Initialize ProxyClientHook singleton
2. Set up the event bus
3. Load mods from `mods/` directory
4. Apply bytecode patches
5. Fire lifecycle events

## 🔧 Uninstallation

To remove the modloader and restore vanilla client:

```bash
cd ~/.local/share/Steam/steamapps/common/Wurm\ Unlimited/WurmLauncher/
mv client.jar.backup client.jar
rm wurmmodloader-client-*.jar
```

## 🏗️ Architecture

### How It Works

**Ago's Approach** (old):
- Replaced `client.jar` entirely with a stub
- Required backup as `client-patched.jar`
- Used custom classloader chain

**Our Improved Approach**:
- Patches `client.jar` in-place using Javassist
- Injects bootstrap code into `WurmMain.main()`
- Uses event-driven architecture (not legacy hooks)
- Connects to server modloader design

### Bootstrap Flow

```
Steam launches client.jar
    ↓
WurmMain.main() called
    ↓
wurmModLoaderBootstrap() executes (our injected code)
    ↓
ProxyClientHook.getInstance() initializes modloader
    ↓
Event bus created
    ↓
Mods loaded from mods/ directory
    ↓
Bytecode patches registered
    ↓
Game starts with mods active
```

### Patching Process

The patcher (`ClientPatcher.java`):
1. Reads `client.jar`
2. Loads `WurmMain.class` with Javassist
3. Injects `wurmModLoaderBootstrap()` method
4. Calls bootstrap at start of `main()`
5. Writes patched class back to JAR

Bootstrap code loads `ProxyClientHook` which initializes the full modloader.

## 🎮 Creating Mods

See `wurmmodloader-client-api` for event types and documentation.

Example mod:

```java
package com.example.mymod;

import com.garward.wurmmodloader.client.api.events.base.SubscribeEvent;
import com.garward.wurmmodloader.client.api.events.lifecycle.ClientInitEvent;

public class MyMod {

    @SubscribeEvent
    public void onClientInit(ClientInitEvent event) {
        System.out.println("MyMod initialized!");
    }
}
```

## 📚 Available Events

- `ClientInitEvent` - Fired when client starts (WurmClientBase.run())
- `ClientTickEvent` - Fired every game tick (~20 TPS)
- `ClientWorldLoadedEvent` - Fired when world finishes loading

More events coming soon!

## 🐛 Troubleshooting

**Client won't launch after patching**:
- Restore from backup: `mv client.jar.backup client.jar`
- Try re-running the patcher
- Check that modloader JAR is in WurmLauncher directory

**Mods not loading**:
- Check console output for errors
- Verify mods are in `mods/` subdirectory
- Ensure mod JARs are properly built

**"Already patched" warning**:
- Backup exists from previous installation
- Choose to re-patch or cancel
- Safe to re-patch (will restore from backup first)

## 📖 Related Documentation

- `CLAUDE.md` - Development guidelines and architecture
- `PATCHER.md` - Technical details on bytecode patching
- `BUILD.md` - Build system documentation
- `README.md` - Project overview

## ✅ Verification

To verify installation worked:

1. Launch Wurm Unlimited
2. Check console output for:
   ```
   [WurmModLoader] Initializing client modloader...
   INFO: Initializing ProxyClientHook singleton
   INFO: ClientHook initialized
   [WurmModLoader] Client modloader initialized
   ```

3. Mods should load and fire their event handlers

If you see this output, the modloader is working!
