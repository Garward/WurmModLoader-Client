plugins {
    java
}

group = "com.garward.mods"
version = "0.1.0"

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
    compileOnly(project(":wurmmodloader-client-core"))

    compileOnly(files("$wurmClientDir/client.jar"))
    compileOnly(files("$wurmClientDir/common.jar"))
}

tasks.jar {
    archiveBaseName.set("declarativeui")
    archiveVersion.set("")

    manifest {
        attributes(
            "Implementation-Title" to "Declarative UI Client Mod",
            "Implementation-Version" to version
        )
    }
}

tasks.register<Zip>("modDistribution") {
    archiveBaseName.set("declarativeui")
    archiveVersion.set(project.version.toString())

    from(tasks.jar) {
        into("mods/declarativeui")
    }

    from("src/dist") {
        into("mods/declarativeui")
    }
}

tasks.build {
    dependsOn(tasks.named("modDistribution"))
}

tasks.register<Copy>("deployMod") {
    dependsOn(tasks.jar)
    from(tasks.jar.get().archiveFile)
    from("src/dist")
    into("$wurmClientDir/mods/declarativeui")
}
