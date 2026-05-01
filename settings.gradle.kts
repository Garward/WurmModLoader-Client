rootProject.name = "wurmmodloader-client"

// --- Local property overrides (gitignored) ---
// `gradle.properties.local` at this directory carries personal paths like
// wurmServerDir / wurmClientDir. Loaded into Gradle project properties so the
// build sees them just like committed gradle.properties entries.
val localProps = file("gradle.properties.local")
if (localProps.exists()) {
    val props = java.util.Properties()
    localProps.inputStream().use { props.load(it) }
    gradle.beforeProject {
        props.forEach { k, v -> extra[k.toString()] = v.toString() }
    }
}


include(
    "wurmmodloader-client-api",
    "wurmmodloader-client-core",
    "wurmmodloader-client-patcher",
    "wurmmodloader-client-legacy"
)

// Example mods
include("examples:hellomod")
project(":examples:hellomod").projectDir = file("examples/hellomod")

// Client mods
include("mods:livemap")
project(":mods:livemap").projectDir = file("mods/livemap")

// declarativeui + serverpacks folded into wurmmodloader-client-core as built-in
// framework services (registered automatically by ProxyClientHook). No client
// mod jars are shipped for either — server-side mods drive UI through the
// com.garward.ui ModComm channel, and pack delivery hooks the
// com.garward.serverpacks / ago.serverpacks channels straight from the
// framework. wurmesp, compass, action moved to WurmModLoader-CommunityMods/client-mods/
