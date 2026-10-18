object BuildConfig {
    val FABRIC_LOADER_VERSION: String = "0.19.5"

    // https://semver.org/
    var MOD_VERSION: String = "1.4.0"

    // Minecraft versions to build are listed in settings.gradle.kts
    // 컴파일 결과(바이트코드)가 같은 버전끼리 묶어 그룹의 가장 낮은 버전으로 빌드한다
    val TARGETS: Map<String, Target> = listOf(
        Target("1.20.1", 17, "0.92.12+1.20.1"),
        Target("1.20.2", 17, "0.91.6+1.20.2", ">=1.20.2 <=1.20.4"),
        Target("1.20.5", 21, "0.97.8+1.20.5", ">=1.20.5 <=1.21.4"),
        Target("1.21.5", 21, "0.128.2+1.21.5", ">=1.21.5 <=1.21.10"),
        Target("1.21.11", 21, "0.141.6+1.21.11"),
        Target("26.1", 25, "0.145.1+26.1", ">=26.1 <=26.2"),
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
