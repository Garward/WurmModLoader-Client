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

sourceSets {
    main {
        resources {
            srcDir(".")
            include("compass.properties")
        }
    }
}

dependencies {
    compileOnly(project(":wurmmodloader-client-api"))

    compileOnly(files("$wurmClientDir/client.jar"))
    compileOnly(files("$wurmClientDir/common.jar"))
}

tasks.jar {
    archiveBaseName.set("compass")
    archiveVersion.set("")

    manifest {
        attributes(
            "Implementation-Title" to "Compass Client Mod",
            "Implementation-Version" to project.version
        )
    }
}

tasks.register<Zip>("modDistribution") {
    archiveBaseName.set("compass")
    archiveVersion.set(project.version.toString())

    from(tasks.jar) {
        into("mods/compass")
    }

    from("compass.properties") {
        into("mods")
    }
}

tasks.build {
    dependsOn(tasks.named("modDistribution"))
}

tasks.register<Copy>("deployMod") {
    dependsOn(tasks.jar)
    from(tasks.jar.get().archiveFile) {
        into("compass")
    }
    from("compass.properties")
    into("$wurmClientDir/mods")
}
