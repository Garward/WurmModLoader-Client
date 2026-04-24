plugins {
    java
}

group = "com.garward.wurmmodloader.examples"
version = "1.0.0"

java {
    toolchain { languageVersion.set(JavaLanguageVersion.of(17)) }
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

repositories { mavenCentral() }

val wurmClientDir: String by rootProject.extra

dependencies {
    compileOnly(project(":wurmmodloader-client-api"))

    compileOnly(files("$wurmClientDir/client.jar"))
    compileOnly(files("$wurmClientDir/common.jar"))
}

tasks.jar {
    archiveBaseName.set("hellomod")
    archiveVersion.set("")
}

tasks.register<Copy>("deployMod") {
    dependsOn(tasks.jar)
    from(tasks.jar.get().archiveFile)
    from("src/dist")
    into("$wurmClientDir/mods/hellomod")
}
