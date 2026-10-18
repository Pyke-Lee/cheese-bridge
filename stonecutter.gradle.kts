plugins {
    id("dev.kikugie.stonecutter")
    id("net.fabricmc.fabric-loom") version("1.19-SNAPSHOT") apply false
    id("net.fabricmc.fabric-loom-remap") version("1.19-SNAPSHOT") apply false
}

stonecutter active "1.20.1"

stonecutter parameters {
    replacements {
        string(current.parsed >= "1.21.11") {
            replace("ResourceLocation", "Identifier")
        }
    }
}
