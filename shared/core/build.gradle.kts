import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework
import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFrameworkTask

private val HEX_DIGITS = "0123456789abcdef"

/**
 * The number of suspending members PoraviaCore and PoraviaCoreExtras export
 * between them. It is a floor, not an exact count: adding a member is fine,
 * losing eighteen of them silently is not.
 */
private val MINIMUM_EXPORTED_SUSPEND_MEMBERS = 20

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

    // A static framework carries no link dependencies of its own, so a consumer
    // that just drags it in gets an undefined-symbol error for sqlite3, which the
    // embedded SQLDelight native driver needs. Rather than make every consumer
    // discover that by link failure and add -lsqlite3 by hand, the requirement is
    // declared in the module map: clang autolinking then passes -lsqlite3 for
    // anything that imports PoraviaCore, and Swift honours it.
    doLast {
        val root = outputDir
        if (!root.isDirectory) return@doLast
        var patched = 0
        root.walkTopDown()
            .filter { it.isFile && it.name == "module.modulemap" }
            .forEach { moduleMap ->
                val text = moduleMap.readText()
                if (text.contains("link \"sqlite3\"")) return@forEach
                val closing = text.lastIndexOf('}')
                if (closing < 0) {
                    throw GradleException("Unexpected module map at " + moduleMap.absolutePath)
                }
                moduleMap.writeText(
                    text.substring(0, closing) +
                        "\n    // SQLDelight's native driver is compiled into this static\n" +
                        "    // framework and needs the system SQLite. Declared here so a\n" +
                        "    // consumer does not have to add -lsqlite3 by hand.\n" +
                        "    link \"sqlite3\"\n" +
                        text.substring(closing),
                )
                patched++
            }
        if (patched == 0) {
            throw GradleException(
                "No module map was found under " + root.absolutePath +
                    ", so the sqlite3 link requirement was not declared.",
            )
        }
        logger.lifecycle("Declared the sqlite3 link requirement in " + patched + " module map(s).")
    }
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

// The exported Objective-C surface is parsed out of its own source and handed to
// commonTest as data, so a test can assert that every exported suspending member
// carries @Throws.
//
// This cannot be checked from the generated header: a suspending function's
// completion handler always has an NSError parameter whether or not the function
// declares @Throws, so the header looks identical either way. What @Throws changes
// is whether the Kotlin/Native runtime converts an exception into that NSError or
// terminates the process first. The annotation is therefore only visible in the
// source, and that is what this reads.
val generateExportedApiFacts by tasks.registering {
    val sources = listOf(
        layout.projectDirectory.file(
            "src/commonMain/kotlin/dev/peterdsp/poravia/core/PoraviaCore.kt",
        ),
        layout.projectDirectory.file(
            "src/commonMain/kotlin/dev/peterdsp/poravia/core/PoraviaCoreExtras.kt",
        ),
    )
    val outputDir = layout.buildDirectory.dir("generated/exportedApi/kotlin")
    inputs.files(sources)
    outputs.dir(outputDir)
    doLast {
        data class Member(
            val owner: String,
            val name: String,
            val isSuspend: Boolean,
            val throwsTypes: List<String>,
        )

        val throwsStart = Regex("""^\s*@Throws\(""")
        val declaration = Regex("""^\s*(?:@\w+(?:\([^)]*\))?\s*)*(suspend\s+)?fun\s+(\w+)""")
        val ownerStart = Regex("""^(?:expect\s+|actual\s+)?interface\s+(\w+)""")
        val topLevelFun = Regex("""^expect\s+fun\s+(\w+)""")

        val members = mutableListOf<Member>()
        sources.forEach { source ->
            val file = source.asFile
            if (!file.isFile) throw GradleException("Missing exported source " + file.absolutePath)
            var owner = "<top level>"
            var pending = mutableListOf<String>()
            var collecting = false
            val buffer = StringBuilder()

            file.readLines().forEach line@{ rawLine ->
                val line = rawLine.substringBefore("//")

                if (collecting) {
                    buffer.append(line)
                    if (line.contains(")")) {
                        collecting = false
                        pending += Regex("""(\w+)::class""")
                            .findAll(buffer.toString())
                            .map { it.groupValues[1] }
                        buffer.clear()
                    }
                    return@line
                }

                if (throwsStart.containsMatchIn(line)) {
                    buffer.append(line)
                    if (line.trimEnd().endsWith(")")) {
                        pending += Regex("""(\w+)::class""")
                            .findAll(buffer.toString())
                            .map { it.groupValues[1] }
                        buffer.clear()
                    } else {
                        collecting = true
                    }
                    return@line
                }

                ownerStart.find(line)?.let {
                    owner = it.groupValues[1]
                    pending = mutableListOf()
                    return@line
                }

                topLevelFun.find(line)?.let { match ->
                    members += Member("<top level>", match.groupValues[1], false, pending.toList())
                    pending = mutableListOf()
                    return@line
                }

                declaration.find(line)?.let { match ->
                    members += Member(
                        owner = owner,
                        name = match.groupValues[2],
                        isSuspend = match.groupValues[1].isNotBlank(),
                        throwsTypes = pending.toList(),
                    )
                    pending = mutableListOf()
                    return@line
                }

                if (line.isBlank()) pending = mutableListOf()
            }
        }

        // A parse that finds nothing must fail loudly rather than let an empty
        // list satisfy every assertion downstream.
        if (members.count { it.isSuspend } < MINIMUM_EXPORTED_SUSPEND_MEMBERS) {
            throw GradleException(
                "Parsed only " + members.count { it.isSuspend } +
                    " suspending exported members, which cannot be right. " +
                    "generateExportedApiFacts needs updating.",
            )
        }

        val target = outputDir.get().asFile
            .resolve("dev/peterdsp/poravia/core/ExportedApiFacts.kt")
        target.parentFile.mkdirs()
        val builder = StringBuilder()
        builder.appendLine("// Generated by the generateExportedApiFacts Gradle task from")
        builder.appendLine("// PoraviaCore.kt and PoraviaCoreExtras.kt. Do not edit by hand.")
        builder.appendLine("package dev.peterdsp.poravia.core")
        builder.appendLine()
        builder.appendLine("internal data class ExportedMember(")
        builder.appendLine("    val owner: String,")
        builder.appendLine("    val name: String,")
        builder.appendLine("    val isSuspend: Boolean,")
        builder.appendLine("    val declaredThrows: List<String>,")
        builder.appendLine(")")
        builder.appendLine()
        builder.appendLine("internal object ExportedApiFacts {")
        builder.appendLine("    const val MINIMUM_SUSPEND_MEMBERS: Int = " + MINIMUM_EXPORTED_SUSPEND_MEMBERS)
        builder.appendLine("    val members: List<ExportedMember> = listOf(")
        members.forEach { member ->
            val types = member.throwsTypes.joinToString(", ") { "\"" + it + "\"" }
            builder.appendLine(
                "        ExportedMember(\"" + member.owner + "\", \"" + member.name +
                    "\", " + member.isSuspend + ", listOf(" + types + ")),",
            )
        }
        builder.appendLine("    )")
        builder.appendLine("}")
        target.writeText(builder.toString())
        logger.lifecycle(
            "Exported API facts: " + members.size + " members, " +
                members.count { it.isSuspend } + " suspending.",
        )
    }
}

kotlin.sourceSets.commonTest {
    kotlin.srcDir(generateTestFixtures)
    kotlin.srcDir(generateExportedApiFacts)
}
