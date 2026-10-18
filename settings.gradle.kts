pluginManagement {
    repositories {
        maven("https://maven.fabricmc.net/")
        maven("https://maven.kikugie.dev/releases")
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    // https://stonecutter.kikugie.dev/
    id("dev.kikugie.stonecutter") version("0.9.8")
    // Applies fabric-loom-remap (obfuscated, ~1.21.11) or fabric-loom (unobfuscated, 26.1~) per version
    id("dev.kikugie.loom-back-compat") version("0.4.3")
    // Downloads missing JDKs (17 / 21 / 25) for the toolchains
    id("org.gradle.toolchains.foojay-resolver-convention") version("1.0.0")
}

stonecutter {
    create(rootProject) {
        // Every version listed here must have a matching entry in BuildConfig.TARGETS (build.gradle.kts)
        versions("1.20.1", "1.20.2", "1.20.5", "1.21.5", "1.21.11", "26.1", "26.3")
        vcsVersion = "1.20.1"
    }
}

rootProject.name = "cheese-bridge"
