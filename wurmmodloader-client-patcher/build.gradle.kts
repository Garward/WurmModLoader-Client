plugins {
    id("java-library")
    id("application")
    id("com.github.johnrengelman.shadow")
}

description = "WurmModLoader Client Patcher - Bytecode patcher launcher"

application {
    mainClass.set("com.garward.wurmmodloader.client.patcher.ClientPatcher")
}

val wurmClientDir: String by rootProject.extra

dependencies {
    // ALL modules - this creates the uber-JAR
    implementation(project(":wurmmodloader-client-core"))
    implementation(project(":wurmmodloader-client-api"))
    implementation(project(":wurmmodloader-client-legacy"))

    // Bytecode manipulation (already included in core, but explicit here)
    implementation("org.javassist:javassist:${project.property("javassistVersion")}")

    // Wurm Unlimited client JARs (from local files)
    compileOnly(files("$wurmClientDir/client.jar"))
    compileOnly(files("$wurmClientDir/common.jar"))
}

tasks.jar {
    manifest {
        attributes(
            "Implementation-Title" to "WurmModLoader Client Patcher",
            "Implementation-Version" to project.version,
            "Main-Class" to "com.garward.wurmmodloader.client.patcher.ClientPatcher",
            "Premain-Class" to "com.garward.wurmmodloader.client.patcher.ClientPatcher",
            "Can-Redefine-Classes" to "true",
            "Can-Retransform-Classes" to "true"
        )
    }
}

// Configure shadow JAR (UBER-JAR with ALL modules + dependencies)
tasks.shadowJar {
    archiveClassifier.set("")  // No classifier - this is THE main JAR
    archiveBaseName.set("wurmmodloader-client")

    mergeServiceFiles()

    // Relocate dependencies to avoid conflicts
    relocate("javassist", "com.garward.wurmmodloader.client.patcher.shadow.javassist")

    // Exclude Wurm JARs - provided at runtime
    dependencies {
        exclude(dependency(files("$wurmClientDir/client.jar")))
        exclude(dependency(files("$wurmClientDir/common.jar")))
    }

    manifest {
        attributes(mapOf(
            "Implementation-Title" to "WurmModLoader Client (Complete)",
            "Implementation-Version" to project.version,
            "Main-Class" to "com.garward.wurmmodloader.client.patcher.ClientPatcher",
            "Premain-Class" to "com.garward.wurmmodloader.client.patcher.ClientPatcher",
            "Can-Redefine-Classes" to "true",
            "Can-Retransform-Classes" to "true",
            "Built-By" to System.getProperty("user.name"),
            "Multi-Release" to "true"
        ))
    }

    // Exclude signatures from dependencies (causes issues)
    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA")
}

// Make build depend on shadowJar
tasks.build {
    dependsOn(tasks.shadowJar)
}

tasks.assemble {
    dependsOn(tasks.shadowJar)
}

// Disable application plugin distribution tasks - we use shadowJar instead
tasks.named("distTar") {
    enabled = false
}

tasks.named("distZip") {
    enabled = false
}

tasks.named("startScripts") {
    enabled = false
}

// Fix startShadowScripts dependency
tasks.named("startShadowScripts") {
    dependsOn(tasks.jar)
}
