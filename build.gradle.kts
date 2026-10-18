object BuildConfig {
    val FABRIC_LOADER_VERSION: String = "0.19.5"

    // https://semver.org/
    var MOD_VERSION: String = "1.3.8"

    // Minecraft versions to build are listed in settings.gradle.kts
    val TARGETS: Map<String, Target> = listOf(
        Target("1.20.1", 17, "0.92.12+1.20.1"),
        Target("1.20.2", 17, "0.91.6+1.20.2"),
        Target("1.20.3", 17, "0.91.1+1.20.3"),
        Target("1.20.4", 17, "0.97.3+1.20.4"),
        Target("1.20.5", 21, "0.97.8+1.20.5"),
        Target("1.20.6", 21, "0.100.8+1.20.6"),
        Target("1.21", 21, "0.102.0+1.21"),
        Target("1.21.1", 21, "0.116.17+1.21.1"),
        Target("1.21.2", 21, "0.106.1+1.21.2"),
        Target("1.21.3", 21, "0.114.1+1.21.3"),
        Target("1.21.4", 21, "0.119.4+1.21.4"),
        Target("1.21.5", 21, "0.128.2+1.21.5"),
        Target("1.21.6", 21, "0.128.2+1.21.6"),
        Target("1.21.7", 21, "0.129.0+1.21.7"),
        Target("1.21.8", 21, "0.136.1+1.21.8"),
        Target("1.21.9", 21, "0.134.1+1.21.9"),
        Target("1.21.10", 21, "0.138.4+1.21.10"),
        Target("1.21.11", 21, "0.141.6+1.21.11"),
        Target("26.1", 25, "0.145.1+26.1"),
        Target("26.1.1", 25, "0.145.4+26.1.1"),
        Target("26.1.2", 25, "0.155.3+26.1.2"),
        Target("26.2", 25, "0.161.0+26.2"),
        Target("26.3", 25, "0.163.0+26.3")
    ).associateBy { it.MINECRAFT_VERSION }

    class Target(
        val MINECRAFT_VERSION: String,
        val JAVA_VERSION: Int,
        val FABRIC_API_VERSION: String,
        val MINECRAFT_VERSION_RANGE: String = MINECRAFT_VERSION // range: ">=1.20 <=1.20.1"
    )
}

plugins {
    id("java-library")
    id("dev.kikugie.loom-back-compat")
}

val target: BuildConfig.Target = BuildConfig.TARGETS[sc.current.version]
    ?: error("No BuildConfig.TARGETS entry for Minecraft ${sc.current.version}")

base {
    archivesName = "cheese-bridge"
}

// group is not set: Stonecutter version projects must not share a group
version = createVersionString()

repositories {
    mavenCentral()
}

dependencies {
    minecraft("com.mojang:minecraft:${target.MINECRAFT_VERSION}")
    loomx.applyMojangMappings()
    modImplementation("net.fabricmc:fabric-loader:${BuildConfig.FABRIC_LOADER_VERSION}")
    modImplementation("net.fabricmc.fabric-api:fabric-api:${target.FABRIC_API_VERSION}")

    // io.socket
    implementation("io.socket:socket.io-client:1.0.2")
    include("io.socket:socket.io-client:1.0.2")
    include("io.socket:engine.io-client:1.0.2")

    // http
    implementation("com.squareup.okhttp3:okhttp:3.14.9")
    include("com.squareup.okhttp3:okhttp:3.14.9")
    implementation("com.squareup.okio:okio:1.17.2")
    include("com.squareup.okio:okio:1.17.2")

    // json
    implementation("org.json:json:20231013")
    include("org.json:json:20231013")

    // gson
    implementation("com.google.code.gson:gson:2.10.1")
    include("com.google.code.gson:gson:2.10.1")

    // java-websocket
    implementation("org.java-websocket:Java-WebSocket:1.5.3")
    include("org.java-websocket:Java-WebSocket:1.5.3")

    testImplementation(platform("org.junit:junit-bom:6.0.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks {
    processResources {
        val propertiesMap = mapOf(
            "version" to version,
            "java_version" to target.JAVA_VERSION,
            "minecraft_version" to target.MINECRAFT_VERSION_RANGE,
            "fabric_loader_version" to BuildConfig.FABRIC_LOADER_VERSION,
            "fabric_api_version" to target.FABRIC_API_VERSION
        )
        inputs.properties(propertiesMap)
        filesMatching(listOf("fabric.mod.json", "*.mixins.json")) {
            expand(propertiesMap)
        }
    }
}

// remapJar on obfuscated versions, jar on 26.1+
loomx.modJar.configure {
    //from("LICENSE")
    destinationDirectory.set(rootProject.layout.buildDirectory.dir("mods"))
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(target.JAVA_VERSION)

    withSourcesJar()
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.release.set(target.JAVA_VERSION)
}

tasks.test {
    useJUnitPlatform()
}

fun createVersionString(): String {
    val builder = StringBuilder()

    builder.append(BuildConfig.MOD_VERSION)
    builder.append("+mc").append(target.MINECRAFT_VERSION)

    return builder.toString()
}
