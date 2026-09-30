package dev.peterdsp.poravia.app

import android.content.Context
import dev.peterdsp.poravia.core.Brand
import dev.peterdsp.poravia.core.CoreConfig
import dev.peterdsp.poravia.core.PoraviaAndroid
import dev.peterdsp.poravia.core.PoraviaCore
import dev.peterdsp.poravia.core.createPoraviaCore
import dev.peterdsp.poravia.core.extras
import dev.peterdsp.poravia.features.AboutFeature
import dev.peterdsp.poravia.features.JourneyDetailFeature
import dev.peterdsp.poravia.features.LibraryFeature
import dev.peterdsp.poravia.features.OfflineFeature
import dev.peterdsp.poravia.features.OperatorFeature
import dev.peterdsp.poravia.features.StopFeature
import dev.peterdsp.poravia.features.search.SearchFeature
import dev.peterdsp.poravia.reminders.ReminderScheduler
import dev.peterdsp.poravia.reminders.ReminderStore
import dev.peterdsp.poravia.wallet.TicketStore
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Everything the core and the features need, built once and shared.
 *
 * The bundle is rebuilt only when the language changes, because the core takes
 * its language at construction. Rebuilding closes the previous core first, so
 * there is never more than one SQLite connection to the same file.
 */
class PoraviaBundle(
    val core: PoraviaCore,
    val languageTag: String,
    val search: SearchFeature,
    val journey: JourneyDetailFeature,
    val operator: OperatorFeature,
    val stop: StopFeature,
    val library: LibraryFeature,
    val offline: OfflineFeature,
    val about: AboutFeature,
)

/**
 * The application's single owner of the shared core.
 *
 * Nothing in the user interface constructs a core; a screen asks for the bundle
 * and gets whichever one matches the language in force. That is what keeps a
 * language change from leaving two cores fighting over one database file.
 */
class PoraviaServices(context: Context) {

    private val appContext = context.applicationContext
    private val lock = Mutex()
    private var bundle: PoraviaBundle? = null
    private var seededReleaseId: String? = null

    val settingsStore: SettingsStore = SettingsStore(appContext)
    val ticketStore: TicketStore = TicketStore(appContext)
    val reminderStore: ReminderStore = ReminderStore(appContext)
    val reminders: ReminderScheduler = ReminderScheduler(appContext, reminderStore)

    val packsDirectory: File get() = File(appContext.filesDir, PACKS_DIRECTORY)

    private val databasePath: String
        get() = appContext.getDatabasePath(DATABASE_NAME).absolutePath

    suspend fun bundle(languageTag: String): PoraviaBundle = lock.withLock {
        bundle?.takeIf { it.languageTag == languageTag }?.let { return it }
        bundle?.core?.let { runCatching { it.close() } }

        seedBundledRelease()

        val core = withContext(Dispatchers.IO) {
            createPoraviaCore(
                CoreConfig(
                    // Static-pack mode. There is no live origin in this release, so
                    // the application never pretends a result came from a service.
                    apiBaseUrl = null,
                    staticPacksBaseUrl = Brand.STATIC_PACKS_BASE_URL,
                    packsDirectory = packsDirectory.absolutePath,
                    databasePath = databasePath,
                    languageTag = languageTag,
                ),
            )
        }
        core.extras?.let { extras -> runCatching { extras.adoptSeededRelease() } }

        val built = PoraviaBundle(
            core = core,
            languageTag = languageTag,
            search = SearchFeature(core, languageTag),
            journey = JourneyDetailFeature(core),
            operator = OperatorFeature(core),
            stop = StopFeature(core),
            library = LibraryFeature(core),
            offline = OfflineFeature(core),
            about = AboutFeature(core),
        )
        bundle = built
        built
    }

    /**
     * Copies the release bundled with the application into the core's packs
     * directory the first time this build runs.
     *
     * The core verifies every digest afterwards, so this is a delivery
     * mechanism and not a trust decision: a bundled file that does not match
     * the manifest is discarded exactly as a downloaded one would be. A marker
     * file records which release was seeded so the copy is not repeated on
     * every launch, and so an application update with a newer release seeds
     * again.
     */
    private suspend fun seedBundledRelease() = withContext(Dispatchers.IO) {
        val assets = appContext.assets
        val manifestBytes = runCatching {
            assets.open(ASSET_RELEASE + "/manifest.json").use { it.readBytes() }
        }.getOrNull() ?: return@withContext

        val releaseId = Regex("\"releaseId\"\\s*:\\s*\"([^\"]+)\"")
            .find(manifestBytes.decodeToString())?.groupValues?.get(1)
            ?: return@withContext

        val marker = File(packsDirectory, SEED_MARKER)
        if (seededReleaseId == releaseId) return@withContext
        if (marker.isFile && marker.readText().trim() == releaseId) {
            seededReleaseId = releaseId
            return@withContext
        }

        val current = File(packsDirectory, "current")
        val packs = File(current, "packs")
        packs.mkdirs()

        val names = runCatching { assets.list(ASSET_RELEASE + "/packs") }.getOrNull().orEmpty()
        names.forEach { name ->
            val target = File(packs, name)
            if (target.isFile) return@forEach
            runCatching {
                assets.open(ASSET_RELEASE + "/packs/" + name).use { input ->
                    target.outputStream().use { output -> input.copyTo(output) }
                }
            }.onFailure { target.delete() }
        }
        File(current, "manifest.json").writeBytes(manifestBytes)

        marker.parentFile?.mkdirs()
        marker.writeText(releaseId)
        seededReleaseId = releaseId
    }

    /** Bytes currently held by installed packs, for the settings storage row. */
    suspend fun installedBytes(): Long = withContext(Dispatchers.IO) {
        packsDirectory.walkBottomUp().filter { it.isFile }.sumOf { it.length() }
    }

    /**
     * Removes every installed pack and the cached release state with it. The
     * bundled release is seeded again on the next launch, which is what makes
     * "clear offline storage" recoverable rather than a one-way door.
     */
    suspend fun clearOfflineStorage() = withContext(Dispatchers.IO) {
        lock.withLock {
            bundle?.core?.let { runCatching { it.close() } }
            bundle = null
            seededReleaseId = null
            packsDirectory.deleteRecursively()
            appContext.getDatabasePath(DATABASE_NAME).delete()
            File(appContext.getDatabasePath(DATABASE_NAME).absolutePath + "-wal").delete()
            File(appContext.getDatabasePath(DATABASE_NAME).absolutePath + "-shm").delete()
        }
    }

    companion object {
        const val PACKS_DIRECTORY: String = "packs"
        const val DATABASE_NAME: String = "poravia.db"
        private const val ASSET_RELEASE = "release"
        private const val SEED_MARKER = ".seeded-release"

        @Volatile
        private var instance: PoraviaServices? = null

        fun get(context: Context): PoraviaServices = instance ?: synchronized(this) {
            instance ?: PoraviaServices(context).also {
                PoraviaAndroid.initialize(context.applicationContext)
                instance = it
            }
        }
    }
}
