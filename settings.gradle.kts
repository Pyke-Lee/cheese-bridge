pluginManagement {
    repositories {
        maven("https://maven.neoforged.net/releases")
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    // Downloads a missing JDK 17 for the toolchain
    id("org.gradle.toolchains.foojay-resolver-convention") version("1.0.0")
}

rootProject.name = "cheese-bridge"
