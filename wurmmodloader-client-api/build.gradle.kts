plugins {
    id("java-library")
}

description = "WurmModLoader Client API - Interfaces and events for client-side mods"

val wurmClientDir: String by rootProject.extra

dependencies {
    // Javassist for bytecode manipulation (required by BytecodePatch interface)
    api("org.javassist:javassist:${project.property("javassistVersion")}")

    // Wurm Unlimited client dependencies (compile-only, from local files)
    compileOnly(files("$wurmClientDir/client.jar"))
    compileOnly(files("$wurmClientDir/common.jar"))

    // Test dependencies
    testImplementation(files("$wurmClientDir/client.jar"))
    testImplementation(files("$wurmClientDir/common.jar"))
}

tasks.jar {
    manifest {
        attributes(
            "Implementation-Title" to "WurmModLoader Client API",
            "Implementation-Version" to project.version,
            "Automatic-Module-Name" to "com.garward.wurmmodloader.client.api"
        )
    }
}
