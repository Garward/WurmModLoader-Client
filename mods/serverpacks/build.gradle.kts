plugins {
    java
}

group = "com.garward.mods"
version = "0.4.0"

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
    archiveBaseName.set("serverpacks")
    archiveVersion.set("")

    manifest {
        attributes(
            "Implementation-Title" to "ServerPacks Client Mod",
            "Implementation-Version" to version
        )
    }
}

// Distribution zip: canonical layout (mods/<name>/<name>.jar + mods/<name>/mod.properties).
tasks.register<Zip>("modDistribution") {
    archiveBaseName.set("serverpacks")
    archiveVersion.set(project.version.toString())

    from(tasks.jar) {
        into("mods/serverpacks")
    }

    from("src/dist") {
        into("mods/serverpacks")
    }
}

tasks.build {
    dependsOn(tasks.named("modDistribution"))
}

// Deploy built JAR + properties directly into the local Wurm client mods dir for quick testing.
tasks.register<Copy>("deployMod") {
    dependsOn(tasks.jar)
    from(tasks.jar.get().archiveFile)
    from("src/dist")
    into("$wurmClientDir/mods/serverpacks")
}
