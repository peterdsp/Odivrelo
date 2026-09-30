package dev.peterdsp.poravia.core

import dev.peterdsp.poravia.core.model.Coverage
import dev.peterdsp.poravia.core.model.FavoritePlace
import dev.peterdsp.poravia.core.model.Freshness
import dev.peterdsp.poravia.core.model.InstalledPack
import dev.peterdsp.poravia.core.model.JourneyDetail
import dev.peterdsp.poravia.core.model.JourneyFilters
import dev.peterdsp.poravia.core.model.JourneyResults
import dev.peterdsp.poravia.core.model.Meta
import dev.peterdsp.poravia.core.model.OfflineCatalog
import dev.peterdsp.poravia.core.model.OperatorDetail
import dev.peterdsp.poravia.core.model.PackProgress
import dev.peterdsp.poravia.core.model.PackResult
import dev.peterdsp.poravia.core.model.PlaceResults
import dev.peterdsp.poravia.core.model.RecentSearch
import dev.peterdsp.poravia.core.model.SavedTrip
import dev.peterdsp.poravia.core.model.SourceList
import dev.peterdsp.poravia.core.model.StopDetail
import kotlin.native.ObjCName

/**
 * Everything the core needs to run. A client supplies writable locations; the
 * core never guesses a path and never writes outside the two it is given.
 *
 * @param apiBaseUrl live API origin, or `null` for static-pack mode.
 * @param staticPacksBaseUrl origin serving published manifests and packs.
 * @param packsDirectory writable directory owned by the core for packs.
 * @param databasePath writable SQLite file path owned by the core.
 * @param languageTag `el`, `en` or `sq`.
 */
@ObjCName("PoraviaCoreConfig")
data class CoreConfig(
    val apiBaseUrl: String?,
    val staticPacksBaseUrl: String?,
    val packsDirectory: String,
    val databasePath: String,
    val languageTag: String,
) {
    init {
        require(packsDirectory.isNotBlank()) { "packsDirectory must be a writable directory path" }
        require(databasePath.isNotBlank()) { "databasePath must be a writable file path" }
        require(languageTag.isNotBlank()) { "languageTag must be one of el, en, sq" }
    }

    /** True when no live API is configured and only published packs are read. */
    val isStaticPackMode: Boolean get() = apiBaseUrl.isNullOrBlank()

    val resolvedLanguageTag: String
        get() = languageTag.take(2).lowercase()
            .takeIf { it in Brand.LANGUAGES } ?: Brand.DEFAULT_LANGUAGE
}

/**
 * A handle to work started by the core. Cancelling is idempotent and always
 * safe; a cancelled download leaves no partially installed pack behind.
 */
@ObjCName("PoraviaCancellable")
interface Cancellable {
    fun cancel()
    val isCancelled: Boolean
}

/**
 * The single entry point shared by the iOS and Android applications.
 *
 * Suspending members are projected into Swift as `async` functions through the
 * Objective-C interop. [downloadPack] intentionally takes callbacks and returns
 * a [Cancellable] instead of a `Flow`, so Swift needs no coroutine bridge.
 *
 * The core owns every service-date, `Europe/Athens` and midnight-crossing
 * decision. A user interface must never recompute them.
 */
@ObjCName("PoraviaCore")
interface PoraviaCore {
    suspend fun meta(): Meta

    suspend fun coverage(): Coverage

    suspend fun searchPlaces(query: String, limit: Int): PlaceResults

    suspend fun searchJourneys(
        originId: String,
        destinationId: String,
        serviceDate: String,
        filters: JourneyFilters,
    ): JourneyResults

    suspend fun journeyDetail(journeyId: String, serviceDate: String): JourneyDetail

    suspend fun operatorDetail(operatorId: String): OperatorDetail

    suspend fun stopDetail(stopId: String, serviceDate: String): StopDetail

    suspend fun sources(): SourceList

    suspend fun savedTrips(): List<SavedTrip>

    suspend fun saveTrip(journeyId: String, serviceDate: String): SavedTrip

    suspend fun removeSavedTrip(savedTripId: String)

    suspend fun favorites(): List<FavoritePlace>

    /** Returns the new state: true when the place is now a favourite. */
    suspend fun toggleFavorite(placeId: String): Boolean

    suspend fun recentSearches(): List<RecentSearch>

    suspend fun offlineCatalog(): OfflineCatalog

    /** Progress is delivered on the caller's context; returns a handle that cancels the download. */
    fun downloadPack(
        packName: String,
        onProgress: (PackProgress) -> Unit,
        onResult: (PackResult) -> Unit,
    ): Cancellable

    suspend fun installedPacks(): List<InstalledPack>

    suspend fun removePack(packName: String)

    suspend fun rollbackToPreviousRelease(): Meta

    fun freshnessOf(checkedAt: String, now: String): Freshness

    fun close()
}

/**
 * Builds a core bound to the platform's HTTP engine, SQLite driver and file
 * system. Called once per process; the returned instance is safe to share.
 */
@ObjCName("PoraviaCoreFactory")
expect fun createPoraviaCore(config: CoreConfig): PoraviaCore
