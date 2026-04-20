plugins {
    id("java-library")
    // Shadow plugin removed - uber-JAR is built in patcher module
}

description = "WurmModLoader Client Core Implementation"

val wurmClientDir: String by rootProject.extra

dependencies {
    // API dependency
    api(project(":wurmmodloader-client-api"))

    // Legacy mod support
    api(project(":wurmmodloader-client-legacy"))

    // Bytecode manipulation
    implementation("org.javassist:javassist:${project.property("javassistVersion")}")
    implementation("com.google.code.gson:gson:2.10.1")

    // YAML configuration parsing
    implementation("org.yaml:snakeyaml:2.2")

    // Logging
    implementation("org.slf4j:slf4j-api:${project.property("slf4jVersion")}")
    implementation("ch.qos.logback:logback-classic:${project.property("logbackVersion")}")

    // Wurm Unlimited dependencies (compile-only, from local files)
    compileOnly(files("$wurmClientDir/client.jar"))
    compileOnly(files("$wurmClientDir/common.jar"))

    // Test dependencies
    testImplementation(files("$wurmClientDir/client.jar"))
    testImplementation(files("$wurmClientDir/common.jar"))
}

tasks.jar {
    manifest {
        attributes(
            "Implementation-Title" to "WurmModLoader Client Core",
            "Implementation-Version" to project.version,
            "Automatic-Module-Name" to "com.garward.wurmmodloader.client.core"
        )
    }
}

// Note: Uber-JAR is created in wurmmodloader-client-patcher module
// This module only builds the thin core JAR
