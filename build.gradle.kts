@file:Suppress("UnstableApiUsage")

import java.text.SimpleDateFormat
import java.util.*

val modId: String by project
val modName: String by project
val modVersion: String by project
val modBase: String by project
val modAuthor: String by project
val modDescription: String by project
val modCredits: String by project
val license: String by project
val extraModsDirectory: String by project
val recipeViewer: String by project
val reiVersion: String by project
val mcVersion: String by project
val mcVersionRange: String by project
val forgeVersion: String by project
val forgeVersionRange: String by project
val mappingsChannel: String by project
val mappingsVersion: String by project
val githubUser: String by project
val githubRepo: String by project
val jeiVersion: String by project
val jeiVersionRange: String by project
val kubeVersion: String by project
val kubeVersionRange: String by project

plugins {
    id("dev.architectury.loom") version "0.12.0-SNAPSHOT"
    id("io.github.juuxel.loom-quiltflower") version "1.7.4"
    id("com.github.gmazzo.buildconfig") version "3.0.3"
    java
    idea
    eclipse
}

base {
    version = "$mcVersion-$modVersion"
    group = "$modBase.$modId"
    archivesName.set(modId)
}

loom {
    silentMojangMappingsLicense()

    runs {
        named("client") {
            client()
            // property("fabric.log.level", "debug")
            vmArgs("-XX:+IgnoreUnrecognizedVMOptions", "-XX:+AllowEnhancedClassRedefinition")
        }
        named("server") {
            server()
            // property("fabric.log.level", "debug")
            vmArgs("-XX:+IgnoreUnrecognizedVMOptions", "-XX:+AllowEnhancedClassRedefinition")
        }
    }

    forge {
        mixinConfig("$modId.mixins.json")
    }

    mixin {
        defaultRefmapName.set("$modId.refmap.json")
    }
}

repositories {
    maven("https://maven.parchmentmc.org/")
    maven("https://dvs1.progwml6.com/files/maven/")
    maven("https://maven.saps.dev/minecraft")
    flatDir {
        name = extraModsDirectory
        dir(file("$extraModsDirectory-$mcVersion"))
    }
}

dependencies {
    minecraft("com.mojang:minecraft:$mcVersion")
    forge("net.minecraftforge:forge:$mcVersion-$forgeVersion")
    mappings(loom.layered {
        officialMojangMappings()
        parchment("org.parchmentmc.data:$mappingsChannel-$mcVersion:$mappingsVersion@zip")
    })

    modCompileOnlyApi("mezz.jei:jei-$mcVersion:$jeiVersion:api") { isTransitive = false }
    modCompileOnly(modLocalRuntime("dev.latvian.mods:kubejs-forge:$kubeVersion")!!)

    when (recipeViewer) {
        "rei" -> modLocalRuntime("me.shedaniel:RoughlyEnoughItems-forge:$reiVersion")
        "jei" -> modLocalRuntime("mezz.jei:jei-$mcVersion:$jeiVersion") { isTransitive = false }
        else -> throw GradleException("Invalid recipeViewer value: $recipeViewer")
    }

    fileTree("$extraModsDirectory-$mcVersion") { include("**/*.jar") }
        .forEach { f ->
            val sepIndex = f.nameWithoutExtension.lastIndexOf('-')
            if (sepIndex == -1) {
                throw IllegalArgumentException("Invalid mod name: ${f.nameWithoutExtension}")
            }
            val mod = f.nameWithoutExtension.substring(0, sepIndex)
            val version = f.nameWithoutExtension.substring(sepIndex + 1)
            println("Extra mod $mod with version $version detected")
            modLocalRuntime("$extraModsDirectory:$mod:$version")
        }
}

tasks {
    jar {
        manifest {
            attributes(
                "Specification-Title" to modName,
                "Specification-Vendor" to modAuthor,
                "Specification-Version" to archiveVersion,
                "Implementation-Title" to name,
                "Implementation-Version" to archiveVersion,
                "Implementation-Vendor" to modAuthor,
                "Implementation-Timestamp" to SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ").format(Date()),
                "Timestamp" to System.currentTimeMillis(),
                "Built-On-Java" to "${System.getProperty("java.vm.version")} (${System.getProperty("java.vm.vendor")})",
                "Build-On-Minecraft" to mcVersion
            )
        }
    }
    processResources {
        val resourceTargets = listOf("META-INF/mods.toml", "pack.mcmeta")
        val replaceProperties = mapOf(
            "version" to version as String,
            "modId" to modId,
            "modName" to modName,
            "modAuthor" to modAuthor,
            "modDescription" to modDescription,
            "modCredits" to modCredits,
            "license" to license,
            "mcVersionRange" to mcVersionRange,
            "forgeVersionRange" to forgeVersionRange,
            "githubUser" to githubUser,
            "githubRepo" to githubRepo,
            "jeiVersionRange" to jeiVersionRange,
            "kubeVersionRange" to kubeVersionRange
        )

        inputs.properties(replaceProperties)
        filesMatching(resourceTargets) {
            expand(replaceProperties)
        }
    }
    withType<JavaCompile> {
        options.encoding = "UTF-8"
        options.release.set(17)
    }
    withType<GenerateModuleMetadata> {
        enabled = false
    }
}

extensions.configure<JavaPluginExtension> {
    toolchain.languageVersion.set(JavaLanguageVersion.of(17))
}

buildConfig {
    buildConfigField("String", "MOD_ID", "\"${modId}\"")
    buildConfigField("String", "MOD_VERSION", "\"${version}\"")
    buildConfigField("String", "MOD_NAME", "\"${modName}\"")
    packageName(group as String)
}
