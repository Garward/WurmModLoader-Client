plugins {
    java
}

group = "com.garward.mods"
version = "0.2.0"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

repositories {
    mavenCentral()
}

val wurmClientDir: String by rootProject.extra

dependencies {
    // Client modloader API
    compileOnly(project(":wurmmodloader-client-api"))
    // Needed for ModComm (channel registry for legacy ago.serverpacks protocol)
    compileOnly(project(":wurmmodloader-client-core"))

    // Wurm client JARs
    compileOnly(files("$wurmClientDir/client.jar"))
    compileOnly(files("$wurmClientDir/common.jar"))
}

tasks.jar {
    manifest {
        attributes(
            "Implementation-Title" to "ServerPacks Client Mod",
            "Implementation-Version" to version
        )
    }
}

// Copy built JAR and properties to mods directory for testing
tasks.register<Copy>("deployMod") {
    dependsOn(tasks.jar)
    from(tasks.jar.get().archiveFile)
    from("ServerPacksClientMod.properties")
    from("README.md")
    into("$wurmClientDir/mods")
    rename { fileName ->
        when {
            fileName.endsWith(".jar") -> "ServerPacksClientMod.jar"
            fileName == "README.md" -> "ServerPacksClientMod.README.md"
            else -> fileName
        }
    }
}
