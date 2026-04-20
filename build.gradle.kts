plugins {
    id("java-library")
    id("maven-publish")
    id("com.github.johnrengelman.shadow") version "8.1.1" apply false
}

// Path to the player's WurmLauncher directory (containing client.jar + common.jar).
// Set via gradle property `wurmClientDir` in ~/.gradle/gradle.properties OR the
// WURM_CLIENT_DIR environment variable. See gradle.properties.example.
val wurmClientDir: String by extra(
    (project.findProperty("wurmClientDir") as String?)
        ?: System.getenv("WURM_CLIENT_DIR")
        ?: error(
            "Wurm client directory not set. Add `wurmClientDir=/path/to/WurmLauncher` to " +
            "~/.gradle/gradle.properties, or set the WURM_CLIENT_DIR environment variable. " +
            "See gradle.properties.example."
        )
)

allprojects {
    group = "com.garward.wurmmodloader.client"
    version = "0.2.0"

    repositories {
        mavenCentral()
        maven {
            url = uri("https://gotti.no-ip.org/maven/repository")
            name = "WurmUnlimited"
        }
        maven {
            url = uri("https://jitpack.io")
            name = "JitPack"
        }
    }
}

subprojects {
    apply(plugin = "java-library")
    apply(plugin = "maven-publish")

    java {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(17))
        }
        withSourcesJar()
        withJavadocJar()
    }

    tasks.withType<JavaCompile> {
        options.encoding = "UTF-8"
        // Target Java 8 bytecode for compatibility with Wurm client
        options.release.set(8)
    }

    dependencies {
        // Common test dependencies
        testImplementation("junit:junit:4.13.2")
        testImplementation("org.assertj:assertj-core:3.24.2")
        testImplementation("org.junit.jupiter:junit-jupiter:5.10.1")
        testRuntimeOnly("org.junit.vintage:junit-vintage-engine:5.10.1")
    }

    tasks.withType<Test> {
        useJUnitPlatform()

        testLogging {
            events("passed", "skipped", "failed")
            exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
            showStandardStreams = false
        }
    }

    tasks.javadoc {
        if (JavaVersion.current().isJava9Compatible) {
            (options as StandardJavadocDocletOptions).apply {
                addBooleanOption("html5", true)
                addStringOption("Xdoclint:none", "-quiet")
                encoding = "UTF-8"
                docEncoding = "UTF-8"
                charSet = "UTF-8"

                links(
                    "https://docs.oracle.com/en/java/javase/17/docs/api/"
                )
            }
        }
    }

    publishing {
        publications {
            create<MavenPublication>("maven") {
                from(components["java"])

                pom {
                    name.set("WurmModLoader Client ${project.name}")
                    description.set("Modern client-side modding framework for Wurm Unlimited")
                    url.set("https://github.com/Garward/WurmModLoader-Client")

                    licenses {
                        license {
                            name.set("MIT License")
                            url.set("https://opensource.org/licenses/MIT")
                        }
                    }

                    developers {
                        developer {
                            id.set("garward")
                            name.set("Garward")
                        }
                    }

                    scm {
                        connection.set("scm:git:git://github.com/Garward/WurmModLoader-Client.git")
                        developerConnection.set("scm:git:ssh://github.com:garward/WurmModLoader-Client.git")
                        url.set("https://github.com/Garward/WurmModLoader-Client")
                    }
                }
            }
        }
    }
}

// Root project tasks
tasks.register("cleanAll") {
    dependsOn(subprojects.map { it.tasks.named("clean") })
    description = "Clean all subprojects"
    group = "build"
}

tasks.register("buildAll") {
    dependsOn(subprojects.map { it.tasks.named("build") })
    description = "Build all subprojects"
    group = "build"
}

tasks.register("testAll") {
    dependsOn(subprojects.map { it.tasks.named("test") })
    description = "Run tests in all subprojects"
    group = "verification"
}

// Distribution ZIP for client patcher
tasks.register<Zip>("dist") {
    group = "distribution"
    description = "Creates complete client patcher distribution with uber-JAR and scripts"

    dependsOn(":wurmmodloader-client-patcher:shadowJar")

    archiveBaseName.set("WurmModloader-Client")
    archiveVersion.set(version.toString())
    destinationDirectory.set(layout.buildDirectory.dir("distributions"))

    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

    // The ONE uber-JAR containing all modules
    from(project(":wurmmodloader-client-patcher").tasks.named("shadowJar")) {
        into(".")
    }

    // Documentation
    from(".") {
        include("README.md", "LICENSE", "BUILD.md", "PATCHER.md", "INSTALLATION.md")
        into(".")
    }

    // Launcher scripts
    from("distribution/scripts") {
        include("launch-client.sh", "launch-client.bat")
        into("scripts")
        fileMode = Integer.parseInt("755", 8)
    }
}
