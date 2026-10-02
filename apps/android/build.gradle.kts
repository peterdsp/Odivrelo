import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.compose)
}

/**
 * Product identity comes from brand.json. Nothing in this module spells out the
 * application id, the domain or the product name, so a rename is one edit.
 */
val brand: Map<String, String> = run {
    val text = rootProject.layout.projectDirectory.file("brand.json").asFile.readText()
    fun field(name: String): String =
        Regex("\"" + name + "\"\\s*:\\s*\"([^\"]+)\"").find(text)?.groupValues?.get(1)
            ?: error("brand.json has no string field '" + name + "'")
    mapOf(
        "name" to field("name"),
        "slug" to field("slug"),
        "domain" to field("domain"),
        "url" to field("url"),
        "applicationId" to field("androidApplicationId"),
        "urlScheme" to field("urlScheme"),
        "version" to field("version"),
    )
}

/**
 * The release the application bundles, synced by scripts/android-sync-packs.sh from
 * the one canonical generator. It is build output, so it is gitignored and
 * regenerated rather than committed.
 */
val bundledReleaseDir = layout.projectDirectory.dir("src/main/assets/release")

/**
 * The sync script copies the whole release directory, which accumulates
 * content-addressed files from every release it has ever held. Only the files the
 * current manifest names belong in the application, so the rest are pruned before
 * the assets are merged. Without this the application would carry several
 * megabytes of superseded packs it can never read.
 */
val prunePackAssets by tasks.registering {
    val releaseDir = bundledReleaseDir
    outputs.upToDateWhen { false }
    doLast {
        val root = releaseDir.asFile
        val manifest = root.resolve("manifest.json")
        if (!manifest.isFile) {
            logger.warn(
                "No bundled data release at " + root.absolutePath + ". " +
                    "Run: PY=.venv/bin/python bash scripts/api-seed-demo.sh && " +
                    "bash scripts/android-sync-packs.sh",
            )
            return@doLast
        }
        val named = Regex("\"path\"\\s*:\\s*\"([^\"]+)\"")
            .findAll(manifest.readText())
            .map { it.groupValues[1].substringAfterLast('/') }
            .toSet()
        val packs = root.resolve("packs")
        if (!packs.isDirectory) return@doLast
        var removed = 0
        var removedBytes = 0L
        packs.listFiles()?.forEach { file ->
            if (file.isFile && file.name !in named) {
                removedBytes += file.length()
                if (file.delete()) removed++
            }
        }
        if (removed > 0) {
            logger.lifecycle(
                "Pruned " + removed + " superseded pack file(s), " + removedBytes + " bytes.",
            )
        }
    }
}

tasks.named("preBuild") { dependsOn(prunePackAssets) }

/**
 * A release keystore is loaded from keystore.properties when one exists. When it
 * does not, the release build is left unsigned on purpose: a disposable key
 * invented by a build script is not a production signing identity and must never
 * be presented as one. See apps/android/SIGNING.md.
 */
val keystoreProperties: Properties? = rootProject.layout.projectDirectory
    .file("keystore.properties").asFile
    .takeIf { it.isFile }
    ?.let { file -> Properties().apply { file.inputStream().use { load(it) } } }

// Build outputs are named after the product rather than after the directory, so a
// delivered artifact says what it is: odivrelo-debug.apk, odivrelo-release.aab.
base.archivesName.set(brand.getValue("slug"))

android {
    namespace = brand.getValue("applicationId")
    compileSdk = 36

    defaultConfig {
        applicationId = brand.getValue("applicationId")
        minSdk = 26
        targetSdk = 36
        // The release workflow passes a unique, monotonically increasing code so
        // no build number is ever uploaded to Play twice. Local and CI builds
        // keep the stable default.
        versionCode = System.getenv("ODIVRELO_VERSION_CODE")?.toIntOrNull() ?: 20260930
        versionName = brand.getValue("version")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Only the three languages the product is complete in are shipped, so a
        // fourth cannot half-appear through a transitive dependency.
        resourceConfigurations += setOf("el", "en", "sq")

        buildConfigField("String", "BRAND_NAME", "\"" + brand.getValue("name") + "\"")
        buildConfigField("String", "BRAND_DOMAIN", "\"" + brand.getValue("domain") + "\"")
        buildConfigField("String", "BRAND_URL", "\"" + brand.getValue("url") + "\"")
        buildConfigField("String", "URL_SCHEME", "\"" + brand.getValue("urlScheme") + "\"")
        buildConfigField("String", "GIT_COMMIT", "\"" + resolveGitCommit() + "\"")

        manifestPlaceholders["appLinkHost"] = brand.getValue("domain")
        manifestPlaceholders["urlScheme"] = brand.getValue("urlScheme")
    }

    signingConfigs {
        if (keystoreProperties != null) {
            create("release") {
                storeFile = rootProject.layout.projectDirectory
                    .file(keystoreProperties.getProperty("storeFile")).asFile
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            isShrinkResources = false
            isPseudoLocalesEnabled = true
        }

        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = keystoreProperties?.let { signingConfigs.getByName("release") }
            ndk { debugSymbolLevel = "SYMBOL_TABLE" }
        }

        // The release configuration, signed with the ordinary Android debug key so
        // the shrunk and obfuscated build can actually be installed and exercised
        // on an emulator. It is a debug-signed build and its version name says so;
        // it is not a distributable artifact.
        create("releaseSmoke") {
            initWith(getByName("release"))
            matchingFallbacks += listOf("release")
            signingConfig = signingConfigs.getByName("debug")
            versionNameSuffix = "-releasesmoke"
            isMinifyEnabled = true
            isShrinkResources = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
            freeCompilerArgs.addAll(
                "-opt-in=kotlin.RequiresOptIn",
            )
        }
    }

    sourceSets {
        getByName("main") {
            kotlin.srcDir("src/main/kotlin")
        }
        getByName("test") {
            kotlin.srcDir("src/test/kotlin")
        }
        getByName("androidTest") {
            kotlin.srcDir("src/androidTest/kotlin")
        }
    }

    packaging {
        resources {
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "/META-INF/DEPENDENCIES",
                "/META-INF/*.kotlin_module",
            )
        }
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
    }

    lint {
        warningsAsErrors = false
        abortOnError = false
        disable += setOf("MissingTranslation")
    }
}

dependencies {
    implementation(projects.shared.core)
    implementation(projects.shared.features)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.datetime)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.splashscreen)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.viewmodel.savedstate)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.window)
    implementation(libs.androidx.window.core)
    implementation(libs.androidx.browser)
    implementation(libs.androidx.security.crypto)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.documentfile)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.foundation)
    implementation(libs.compose.animation)
    implementation(libs.compose.material3)
    implementation(libs.compose.material3.window.size)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)

    testImplementation(libs.junit4)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.junit)

    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.core.ktx)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.androidx.test.uiautomator)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.window.testing)
    androidTestImplementation(libs.kotlinx.coroutines.test)
}

/**
 * The commit the build came from, shown in settings and diagnostics. Diagnostics
 * must be able to identify a build exactly without carrying anything secret.
 */
fun resolveGitCommit(): String {
    val head = rootProject.layout.projectDirectory.dir(".git").asFile
    if (!head.isDirectory) return "unknown"
    return runCatching {
        val process = ProcessBuilder("git", "rev-parse", "--short=12", "HEAD")
            .directory(rootProject.layout.projectDirectory.asFile)
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().readText().trim()
        if (process.waitFor() == 0 && output.isNotEmpty()) output else "unknown"
    }.getOrDefault("unknown")
}
