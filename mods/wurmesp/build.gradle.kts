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
            include("wurmesp.properties")
        }
    }
}

dependencies {
    compileOnly(project(":wurmmodloader-client-api"))

    compileOnly(files("$wurmClientDir/client.jar"))
    compileOnly(files("$wurmClientDir/common.jar"))
}

tasks.jar {
    archiveBaseName.set("wurmesp")
    archiveVersion.set("")

    manifest {
        attributes(
            "Implementation-Title" to "WurmEsp Client Mod",
            "Implementation-Version" to project.version
        )
    }
}

tasks.register<Zip>("modDistribution") {
    archiveBaseName.set("wurmesp")
    archiveVersion.set(project.version.toString())

    from(tasks.jar) {
        into("mods/wurmesp")
    }

    from("wurmesp.properties") {
        into("mods")
    }
}

tasks.build {
    dependsOn(tasks.named("modDistribution"))
}

tasks.register<Copy>("deployMod") {
    dependsOn(tasks.jar)
    from(tasks.jar.get().archiveFile) {
        into("wurmesp")
    }
    from("wurmesp.properties")
    into("$wurmClientDir/mods")
}
