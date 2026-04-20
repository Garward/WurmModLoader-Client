rootProject.name = "wurmmodloader-client"

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
include("mods:serverpacks")
project(":mods:serverpacks").projectDir = file("mods/serverpacks")

include("mods:livemap")
project(":mods:livemap").projectDir = file("mods/livemap")
