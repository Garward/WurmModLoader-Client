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
    compileOnly(project(":wurmmodloader-client-api"))

    compileOnly(files("$wurmClientDir/client.jar"))
    compileOnly(files("$wurmClientDir/common.jar"))
}

tasks.jar {
    archiveBaseName.set("livemap")
    archiveVersion.set("")

    manifest {
        attributes(
            "Implementation-Title" to "LiveMap Client Mod",
            "Implementation-Version" to project.version
        )
    }
}

// Distribution zip: canonical layout (mods/<name>/<name>.jar + mods/<name>/mod.properties).
tasks.register<Zip>("modDistribution") {
    archiveBaseName.set("livemap")
    archiveVersion.set(project.version.toString())

    from(tasks.jar) {
        into("mods/livemap")
    }

    from("src/dist") {
        into("mods/livemap")
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
    into("$wurmClientDir/mods/livemap")
}
