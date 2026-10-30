import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm") version "2.2.21"
    id("com.gradleup.shadow") version "8.3.6"
}

group = "dev.etran"
version = "1.0.0"

repositories {
    mavenCentral()
    maven("https://hub.spigotmc.org/nexus/content/repositories/snapshots/")
}

dependencies {
    // Compile against the OLDEST supported API (1.8.8). Everything newer than that is reached
    // through reflection (see the compat package), so one jar runs from 1.8 up to 26.x.
    compileOnly("org.spigotmc:spigot-api:1.8.8-R0.1-SNAPSHOT") {
        // The 1.8-SNAPSHOT bungeecord-chat that 1.8.8 asks for no longer exists in any repository.
        exclude(group = "net.md-5", module = "bungeecord-chat")
    }
    // A current release satisfies the few Spigot-API signatures that mention chat components.
    compileOnly("net.md-5:bungeecord-chat:1.20-R0.2")
    implementation(kotlin("stdlib"))
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

kotlin {
    compilerOptions {
        // Java 8 bytecode runs on every server from 1.8 (Java 8) to 26.x (Java 25).
        jvmTarget.set(JvmTarget.JVM_1_8)
        freeCompilerArgs.addAll(
            "-Xno-param-assertions",
            "-Xno-call-assertions",
            "-Xno-receiver-assertions"
        )
    }
}

tasks.processResources {
    filesMatching("plugin.yml") {
        expand("version" to project.version)
    }
}

tasks.jar {
    archiveClassifier.set("plain")
}

tasks.shadowJar {
    archiveBaseName.set("BedWars1058-MobSpawn")
    archiveClassifier.set("")
    // Relocate the Kotlin runtime so it can never clash with another plugin's copy.
    relocate("kotlin", "dev.etran.mobspawn.libs.kotlin")
    relocate("org.jetbrains", "dev.etran.mobspawn.libs.jetbrains")
    relocate("org.intellij", "dev.etran.mobspawn.libs.intellij")
}

tasks.build {
    dependsOn(tasks.shadowJar)
}
