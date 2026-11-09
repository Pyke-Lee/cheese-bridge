object BuildConfig {
    val JAVA_VERSION: Int = 21

    val MINECRAFT_VERSION: String = "1.21.1"
    val MINECRAFT_VERSION_RANGE: String = "[1.21.1,1.21.2)"
    val NEOFORGE_VERSION: String = "21.1.265"
    val NEOFORGE_VERSION_RANGE: String = "[21.1,)"
    val LOADER_VERSION_RANGE: String = "[4,)"
    val PARCHMENT_VERSION: String = "2024.11.17"

    // https://semver.org/
    var MOD_VERSION: String = "1.4.0"
}

plugins {
    id("java-library")
    id("net.neoforged.moddev") version("2.0.148")
}

base {
    archivesName = "cheese-bridge"
}

group = "kr.pyke"
version = createVersionString()

repositories {
    mavenCentral()
}

neoForge {
    version = BuildConfig.NEOFORGE_VERSION

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
        // NeoForge loads mods on the module path, so dev runs need the libraries added explicitly
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
            "neoforge_version_range" to BuildConfig.NEOFORGE_VERSION_RANGE,
            "loader_version_range" to BuildConfig.LOADER_VERSION_RANGE
        )
        inputs.properties(propertiesMap)
        filesMatching(listOf("META-INF/neoforge.mods.toml")) {
            expand(propertiesMap)
        }
    }

    named<Jar>("jar") {
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
    builder.append("+neoforge").append(BuildConfig.MINECRAFT_VERSION)

    return builder.toString()
}
