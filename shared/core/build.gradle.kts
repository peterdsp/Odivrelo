import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework
import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFrameworkTask

private val HEX_DIGITS = "0123456789abcdef"

/** The brand slug, read from brand.json so no module hardcodes the product name. */
fun poraviaSlug(root: Project): String {
    val brand = root.layout.projectDirectory.file("brand.json").asFile.readText()
    return Regex("\"slug\"\\s*:\\s*\"([^\"]+)\"").find(brand)?.groupValues?.get(1)
        ?: error("brand.json has no slug")
}

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.android.library)
    alias(libs.plugins.sqldelight)
}

kotlin {
    androidTarget {
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    // A single static XCFramework carries device and simulator slices so the
    // iOS application links one artifact.
    val xcframework = XCFramework("PoraviaCore")
    listOf(iosArm64(), iosSimulatorArm64(), iosX64()).forEach { target ->
        target.binaries.framework {
            baseName = "PoraviaCore"
            isStatic = true
            binaryOption("bundleId", "dev.peterdsp.poravia.core")
            xcframework.add(this)
        }
    }

    sourceSets {
        all {
            languageSettings {
                optIn("kotlin.experimental.ExperimentalObjCName")
                optIn("kotlinx.cinterop.ExperimentalForeignApi")
            }
        }

        commonMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.datetime)
            implementation(libs.ktor.client.core)
            implementation(libs.sqldelight.runtime)
            implementation(libs.sqldelight.coroutines)
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.ktor.client.mock)
        }

        androidMain.dependencies {
            implementation(libs.ktor.client.okhttp)
            implementation(libs.sqldelight.driver.android)
            implementation(libs.kotlinx.coroutines.android)
        }

        getByName("androidUnitTest").dependencies {
            implementation(libs.sqldelight.driver.jvm)
        }

        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
            implementation(libs.sqldelight.driver.native)
        }
    }
}

// The iOS application consumes shared/core/build/XCFramework/<buildType>/, so the
// default plural directory name is overridden rather than left to drift.
tasks.withType<XCFrameworkTask>().configureEach {
    outputDir = layout.buildDirectory.dir("XCFramework").get().asFile
}

sqldelight {
    databases {
        create("PoraviaDatabase") {
            packageName.set("dev.peterdsp.poravia.core.db")
            generateAsync.set(false)
        }
    }
}

android {
    namespace = "dev.peterdsp.poravia.core"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

// Test fixtures come from the one canonical release, not from a copy kept beside
// the tests. There is exactly one generator (server/api/publicapi/packs.py, driven
// by scripts/api-seed-demo.sh), so a contract change breaks these tests instead of
// silently passing against a stale hand-maintained shape.
//
// Kotlin/Native has no resource loader in commonTest, so the release's files are
// compiled into a generated accessor: text packs as strings, the GTFS zip as hex.
// Two deliberately broken variants of one journeys pack are derived here as well,
// so the digest-rejection and release-mixing paths are exercised against real
// bytes from the real release rather than against a hand-written stub.
val canonicalReleaseDir: Provider<Directory> =
    rootProject.layout.projectDirectory.dir("artifacts/releases/" + poraviaSlug(rootProject))
        .let { providers.provider { it } }

val generateTestFixtures by tasks.registering {
    val releaseDir = canonicalReleaseDir
    val outputDir = layout.buildDirectory.dir("generated/fixtures/kotlin")
    // The release is reproducible build output, so it is an input by path rather
    // than a tracked source tree.
    inputs.files(providers.provider {
        val dir = releaseDir.get().asFile
        if (dir.isDirectory) fileTree(dir) else files()
    }).withPropertyName("canonicalRelease")
    outputs.dir(outputDir)
    doLast {
        val root = releaseDir.get().asFile
        val manifestFile = root.resolve("manifest.json")
        if (!manifestFile.isFile) {
            throw GradleException(
                "No canonical data release at " + root.absolutePath + ".\n" +
                    "Build one first:\n" +
                    "  PY=.venv/bin/python bash scripts/api-seed-demo.sh\n" +
                    "  bash scripts/android-sync-packs.sh",
            )
        }

        val manifestText = manifestFile.readText()
        val named = Regex("\"path\"\\s*:\\s*\"([^\"]+)\"")
            .findAll(manifestText)
            .map { it.groupValues[1] }
            .toList()
        if (named.isEmpty()) {
            throw GradleException("The manifest at " + manifestFile.absolutePath + " names no packs.")
        }

        val entries = LinkedHashMap<String, ByteArray>()
        entries["manifest.json"] = manifestText.encodeToByteArray()
        named.forEach { path ->
            val file = root.resolve(path)
            if (!file.isFile) {
                throw GradleException("The manifest names " + path + " but it is not in the release.")
            }
            entries[path] = file.readBytes()
        }

        // Derive the broken variants from the newest journeys pack in the release.
        val dayPackPath = named.filter { it.substringAfterLast('/').startsWith("journeys-") }
            .maxOrNull()
            ?: throw GradleException("The release publishes no journeys pack.")
        val dayText = entries.getValue(dayPackPath).decodeToString()

        // Both broken variants keep the original byte length, so the cheap size
        // guard in PackStore is never what rejects them: the digest and the
        // declared release id are, which is what the tests are about.
        val corrupted = Regex("\"durationMinutes\"\\s*:\\s*(\\d+)")
            .findAll(dayText)
            .firstNotNullOfOrNull { match ->
                val original = match.groupValues[1]
                val bumped = (original.toLong() + 1).toString()
                if (bumped.length != original.length) {
                    null
                } else {
                    dayText.replaceRange(
                        match.range.first,
                        match.range.last + 1,
                        match.value.replace(original, bumped),
                    )
                }
            }
            ?: throw GradleException("Could not find a length-preserving duration to corrupt in " + dayPackPath + ".")
        if (corrupted.length != dayText.length || corrupted == dayText) {
            throw GradleException("The corrupted variant of " + dayPackPath + " changed length.")
        }
        entries["corrupt/journeys-corrupt.json"] = corrupted.encodeToByteArray()

        val releaseIdMatch = Regex("\"releaseId\"\\s*:\\s*\"([^\"]*)\"").find(dayText)
            ?: throw GradleException("Could not find the release id in " + dayPackPath + ".")
        val foreignId = "0".repeat(releaseIdMatch.groupValues[1].length)
        val otherRelease = dayText.replaceRange(
            releaseIdMatch.range.first,
            releaseIdMatch.range.last + 1,
            releaseIdMatch.value.replace(releaseIdMatch.groupValues[1], foreignId),
        )
        if (otherRelease.length != dayText.length || otherRelease == dayText) {
            throw GradleException("The foreign-release variant of " + dayPackPath + " changed length.")
        }
        entries["corrupt/journeys-other-release.json"] = otherRelease.encodeToByteArray()

        val target = outputDir.get().asFile.resolve("dev/peterdsp/poravia/core/TestFixtures.kt")
        target.parentFile.mkdirs()

        val builder = StringBuilder()
        builder.appendLine("// Generated by the generateTestFixtures Gradle task from the canonical")
        builder.appendLine("// release in artifacts/releases/. Do not edit by hand.")
        builder.appendLine("package dev.peterdsp.poravia.core")
        builder.appendLine()
        builder.appendLine("internal object TestFixtures {")
        builder.appendLine("    private val text: Map<String, String> = mapOf(")
        entries.filterKeys { !it.endsWith(".zip") }.forEach { (name, bytes) ->
            val body = bytes.decodeToString()
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("$", "\${'$'}")
                .replace("\r", "")
                .replace("\n", "\\n")
            builder.appendLine("        \"" + name + "\" to \"" + body + "\",")
        }
        builder.appendLine("    )")
        builder.appendLine()
        builder.appendLine("    private val binary: Map<String, String> = mapOf(")
        entries.filterKeys { it.endsWith(".zip") }.forEach { (name, bytes) ->
            val hex = bytes.joinToString("") { byte ->
                val value = byte.toInt() and 0xFF
                HEX_DIGITS[value ushr 4].toString() + HEX_DIGITS[value and 0x0F]
            }
            builder.appendLine("        \"" + name + "\" to \"" + hex + "\",")
        }
        builder.appendLine("    )")
        builder.appendLine()
        builder.appendLine("    fun read(name: String): String =")
        builder.appendLine("        text[name] ?: error(\"fixture not found: \" + name + \" (have \" + names() + \")\")")
        builder.appendLine()
        builder.appendLine("    fun readBinary(name: String): ByteArray {")
        builder.appendLine("        binary[name]?.let { hex ->")
        builder.appendLine("            val out = ByteArray(hex.length / 2)")
        builder.appendLine("            for (index in out.indices) {")
        builder.appendLine("                val high = hex[index * 2].digitToInt(16)")
        builder.appendLine("                val low = hex[index * 2 + 1].digitToInt(16)")
        builder.appendLine("                out[index] = ((high shl 4) or low).toByte()")
        builder.appendLine("            }")
        builder.appendLine("            return out")
        builder.appendLine("        }")
        builder.appendLine("        return read(name).encodeToByteArray()")
        builder.appendLine("    }")
        builder.appendLine()
        builder.appendLine("    fun names(): Set<String> = text.keys + binary.keys")
        builder.appendLine("}")
        target.writeText(builder.toString())
    }
}

kotlin.sourceSets.commonTest {
    kotlin.srcDir(generateTestFixtures)
}
