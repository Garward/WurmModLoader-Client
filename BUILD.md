# Build System Guide

## Overview

This project uses Gradle with the Shadow plugin to create a **single uber-JAR** that contains all modules and dependencies combined into one file for easy deployment.

## Build Outputs

### Regular Build (`./gradlew build`)

Creates **ONE uber-JAR** containing all 4 modules + dependencies:

```bash
./gradlew build
```

**Output:**
- `wurmmodloader-client-patcher/build/libs/wurmmodloader-client-0.1.0.jar` **(2.3 MB)**
  - ✅ **wurmmodloader-client-api** (events, interfaces)
  - ✅ **wurmmodloader-client-core** (hooks, event bus)
  - ✅ **wurmmodloader-client-patcher** (bytecode patcher)
  - ✅ **wurmmodloader-client-legacy** (Ago mod compatibility)
  - ✅ **javassist** (bytecode manipulation)
  - ✅ **gson** (JSON parsing)
  - ✅ **snakeyaml** (YAML config)
  - ✅ **slf4j + logback** (logging)

**Dependencies are relocated** to avoid conflicts:
- `javassist` → `com.garward.wurmmodloader.client.patcher.shadow.javassist`

**Main-Class:** `com.garward.wurmmodloader.client.patcher.ClientPatcher`

### Distribution Build (`./gradlew dist`)

Creates a **ready-to-deploy ZIP** with the uber-JAR and documentation:

```bash
./gradlew dist
```

**Output:** `build/distributions/WurmModloader-Client-0.1.0.zip` (2.1 MB)

**Contents:**
```
WurmModloader-Client-0.1.0/
├── wurmmodloader-client-0.1.0.jar  (2.36 MB - THE uber-JAR)
├── README.md
├── BUILD.md
└── CLAUDE.md
```

**For end users:** Extract the ZIP and run the JAR to patch the Wurm client.

## Architecture

### Module Structure (Development)

During development, the code is organized into 4 modules:

| Module | Purpose | Size |
|--------|---------|------|
| `wurmmodloader-client-api` | Public API & events | 5.8 KB |
| `wurmmodloader-client-core` | Hook implementation | 3.0 KB |
| `wurmmodloader-client-patcher` | Bytecode patcher | 359 B |
| `wurmmodloader-client-legacy` | Ago compatibility | 359 B |

### Build Process (Production)

At build time, all modules are combined:

```
wurmmodloader-client-patcher (module)
    ├── depends on: wurmmodloader-client-core
    ├── depends on: wurmmodloader-client-api
    ├── depends on: wurmmodloader-client-legacy
    └── shadowJar task combines all into:
        → wurmmodloader-client-0.1.0.jar (2.3 MB)
```

## Build Commands

```bash
# Build the uber-JAR (recommended)
./gradlew build

# Create distribution ZIP with uber-JAR + docs
./gradlew dist

# Clean and rebuild everything
./gradlew clean build dist

# Build specific module (for development)
./gradlew :wurmmodloader-client-core:build

# List all tasks
./gradlew tasks
```

## What Gets Built

### After `./gradlew build`:

```
wurmmodloader-client-api/build/libs/
  └── wurmmodloader-client-api-0.1.0.jar         (5.8 KB - thin JAR)

wurmmodloader-client-core/build/libs/
  └── wurmmodloader-client-core-0.1.0.jar        (3.0 KB - thin JAR)

wurmmodloader-client-patcher/build/libs/
  ├── wurmmodloader-client-0.1.0.jar             (2.3 MB - ★ UBER-JAR ★)
  └── wurmmodloader-client-patcher-0.1.0.jar     (359 B - thin JAR)

wurmmodloader-client-legacy/build/libs/
  └── wurmmodloader-client-legacy-0.1.0.jar      (359 B - thin JAR)
```

**For deployment:** Use only `wurmmodloader-client-0.1.0.jar` from the patcher module.

### After `./gradlew dist`:

```
build/distributions/
  └── WurmModloader-Client-0.1.0.zip             (2.1 MB compressed)
      ├── wurmmodloader-client-0.1.0.jar         (2.36 MB uncompressed)
      └── *.md documentation files
```

## Verification

Check the uber-JAR contains all modules:

```bash
jar tf wurmmodloader-client-patcher/build/libs/wurmmodloader-client-0.1.0.jar | grep "^com/garward"
```

Expected output:
```
com/garward/wurmmodloader/client/api/...          (API module)
com/garward/wurmmodloader/client/modloader/...    (Core module)
com/garward/wurmmodloader/client/patcher/...      (Patcher module)
```

Check dependencies are included:

```bash
jar tf wurmmodloader-client-patcher/build/libs/wurmmodloader-client-0.1.0.jar | grep -E "(javassist|gson|snakeyaml|logback)"
```

## Gradle Properties

Key settings in `gradle.properties`:

```properties
version=0.1.0                    # Project version
java.toolchain.version=17        # Build with Java 17
wurmVersion=4596061             # Wurm Unlimited version
javassistVersion=3.30.2-GA      # Bytecode manipulation
slf4jVersion=2.0.9              # Logging API
logbackVersion=1.3.14           # Logging implementation
```

Note: Bytecode is compiled for **Java 8 compatibility** (Wurm client runtime requirement).

## Shadow Plugin Configuration

The Shadow plugin in the **patcher module** creates the uber-JAR with:

1. **All project modules included:**
   - API, Core, Patcher, Legacy all merged into one JAR

2. **Dependencies relocated** to avoid conflicts:
   - `javassist` → `com.garward.wurmmodloader.client.patcher.shadow.javassist`

3. **Wurm JARs excluded** (provided at client runtime):
   - `client.jar` (compile-only)
   - `common.jar` (compile-only)

4. **Service files merged** (SPI/ServiceLoader compatibility)

5. **Signatures excluded** (META-INF/*.SF, *.DSA, *.RSA)

## Deployment

### For End Users (Recommended)

1. Download `WurmModloader-Client-0.1.0.zip`
2. Extract the ZIP
3. Run the patcher:

```bash
java -jar wurmmodloader-client-0.1.0.jar
```

### For Developers

Use the uber-JAR directly from the build:

```bash
cp wurmmodloader-client-patcher/build/libs/wurmmodloader-client-0.1.0.jar \
   ~/.local/share/Steam/steamapps/common/Wurm\ Unlimited/
```

## Troubleshooting

### "Could not find org.gotti.wurmunlimited:client"

The build uses local Wurm JARs. Set `WURM_CLIENT_DIR` (or `wurmClientDir` in
`~/.gradle/gradle.properties`) to your `Wurm Unlimited/WurmLauncher` install
directory and ensure these files exist inside it:
- `<wurm-client-dir>/client.jar`
- `<wurm-client-dir>/common.jar`

Defaults:
- **Windows:** `C:\Program Files (x86)\Steam\steamapps\common\Wurm Unlimited\WurmLauncher\`
- **Linux:**   `~/.local/share/Steam/steamapps/common/Wurm Unlimited/WurmLauncher/`

### Uber-JAR is missing classes

Make sure the patcher module declares ALL modules as dependencies:

```kotlin
implementation(project(":wurmmodloader-client-core"))
implementation(project(":wurmmodloader-client-api"))
implementation(project(":wurmmodloader-client-legacy"))
```

### Shadow JAR task warnings

These are resolved by explicit task dependencies in `build.gradle.kts`. Application plugin tasks are disabled since we use shadowJar exclusively.

## Development Workflow

1. **Make changes** to any module (api, core, patcher, legacy)
2. **Build:** `./gradlew build`
3. **Test:** The uber-JAR is ready at `wurmmodloader-client-patcher/build/libs/wurmmodloader-client-0.1.0.jar`
4. **Distribute:** `./gradlew dist` creates the release ZIP

## Size Breakdown

**Uber-JAR (2.3 MB):**
- Javassist: ~1.8 MB (bytecode manipulation)
- Logback + SLF4J: ~400 KB (logging)
- SnakeYAML: ~350 KB (YAML parsing)
- Gson: ~250 KB (JSON)
- Our code: ~10 KB (all 4 modules)

## Continuous Integration

For CI/CD pipelines:

```bash
# Build and test
./gradlew clean build test --no-daemon --stacktrace

# Create release
./gradlew clean dist --no-daemon

# Release artifact
build/distributions/WurmModloader-Client-0.1.0.zip
```

## Why One JAR?

**Benefits:**
- ✅ **Easy for users** - Single file to download and run
- ✅ **No classpath issues** - Everything is bundled
- ✅ **No missing dependencies** - All libraries included
- ✅ **Conflict prevention** - Dependencies are relocated
- ✅ **Simpler deployment** - Just distribute one file

**For modular development:**
- Individual modules are still built as thin JARs
- Developers can depend on specific modules
- The uber-JAR is created only for distribution

---

**Last Updated**: 2025-11-19
**Gradle Version**: 8.5
**Shadow Plugin**: 8.1.1
