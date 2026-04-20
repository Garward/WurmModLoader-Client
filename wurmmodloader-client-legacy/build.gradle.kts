plugins {
    id("java-library")
}

description = "WurmModLoader Client Legacy - Compatibility layer for Ago's client mods"

val wurmClientDir: String by rootProject.extra

dependencies {
    // API dependency only (avoid circular dependency with core)
    api(project(":wurmmodloader-client-api"))

    // Wurm Unlimited dependencies (from local files)
    compileOnly(files("$wurmClientDir/client.jar"))
    compileOnly(files("$wurmClientDir/common.jar"))

    // Test dependencies
    testImplementation(files("$wurmClientDir/client.jar"))
    testImplementation(files("$wurmClientDir/common.jar"))
}

tasks.jar {
    manifest {
        attributes(
            "Implementation-Title" to "WurmModLoader Client Legacy",
            "Implementation-Version" to project.version,
            "Automatic-Module-Name" to "com.garward.wurmmodloader.client.legacy"
        )
    }
}
