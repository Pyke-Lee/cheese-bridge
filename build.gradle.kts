object BuildConfig {
    val JAVA_VERSION: Int = 17

    val MINECRAFT_VERSION: String = "1.20.1"
    val MINECRAFT_VERSION_RANGE: String = "[1.20.1,1.20.2)"
    val FORGE_VERSION: String = "47.4.28"
    val FORGE_VERSION_RANGE: String = "[47,)"
    val PARCHMENT_VERSION: String = "2023.09.03"

    // https://semver.org/
    var MOD_VERSION: String = "1.3.8"
}

plugins {
    id("java-library")
    id("net.neoforged.moddev.legacyforge") version("2.0.148")
}

base {
    archivesName = "cheese-bridge"
}

group = "kr.pyke"
version = createVersionString()

repositories {
    mavenCentral()
}

legacyForge {
    version = "${BuildConfig.MINECRAFT_VERSION}-${BuildConfig.FORGE_VERSION}"

    parchment {
        minecraftVersion = BuildConfig.MINECRAFT_VERSION
        mappingsVersion = BuildConfig.PARCHMENT_VERSION
    }

    runs {
        register("client") {
            client()
        }
        register("server") {
            server()
        }
    }

    mods {
        register("cheese_bridge") {
            sourceSet(sourceSets.main.get())
        }
    }
}

// Libraries bundled into the mod jar (Jar-in-Jar). Gson is already provided by Minecraft.
val bundledLibraries: List<String> = listOf(
    "io.socket:socket.io-client:1.0.2",
    "io.socket:engine.io-client:1.0.2",
    "com.squareup.okhttp3:okhttp:3.14.9",
    "com.squareup.okio:okio:1.17.2",
    "org.json:json:20231013",
    "org.java-websocket:Java-WebSocket:1.5.3"
)

dependencies {
    for (library in bundledLibraries) {
        jarJar(implementation(library)!!)
        // Forge loads mods on the module path, so dev runs need the libraries added explicitly
        "additionalRuntimeClasspath"(library)
    }

    testImplementation(platform("org.junit:junit-bom:6.0.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks {
    processResources {
        val propertiesMap = mapOf(
            "version" to version,
            "minecraft_version_range" to BuildConfig.MINECRAFT_VERSION_RANGE,
            "forge_version_range" to BuildConfig.FORGE_VERSION_RANGE
        )
        inputs.properties(propertiesMap)
        filesMatching(listOf("META-INF/mods.toml")) {
            expand(propertiesMap)
        }
    }

    // reobfJar (SRG) is the jar to distribute; the plain jar is moved to build/devlibs
    named<Jar>("reobfJar") {
        //from("LICENSE")
        destinationDirectory.set(layout.buildDirectory.dir("mods"))
    }
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(BuildConfig.JAVA_VERSION)

    withSourcesJar()
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.release.set(BuildConfig.JAVA_VERSION)
}

tasks.test {
    useJUnitPlatform()
}

fun createVersionString(): String {
    val builder = StringBuilder()

    builder.append(BuildConfig.MOD_VERSION)
    builder.append("+forge").append(BuildConfig.MINECRAFT_VERSION)

    return builder.toString()
}
