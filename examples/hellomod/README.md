# HelloMod (client)

The smallest possible WurmModLoader-Client mod. One class, two event
handlers, no `com.wurmonline.*` imports. Logs when the client initializes
and when the player enters a world.

Use this as a copy-and-rename starting skeleton. For a fully-featured client
mod with custom UI and a server-side counterpart, see
[`../../mods/livemap/`](../../mods/livemap/).

## Files

```
hellomod/
├── build.gradle.kts                            # Gradle build (compileOnly client.jar/common.jar)
├── HelloMod.properties                         # Mod descriptor (classname=…, classpath=hellomod.jar)
└── src/main/java/.../HelloMod.java             # The mod itself
```

## Build + deploy

```bash
./gradlew :examples:hellomod:deployMod
```

Drops `hellomod.jar` + `HelloMod.properties` into
`~/.local/share/Steam/steamapps/common/Wurm Unlimited/WurmLauncher/mods/`.

Then launch the patched client and watch its log for:

```
[HelloMod] Client initialized — modloader is alive.
[HelloMod] World loaded — welcome in.
```
