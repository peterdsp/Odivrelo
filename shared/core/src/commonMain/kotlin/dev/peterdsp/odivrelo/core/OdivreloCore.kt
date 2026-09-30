package dev.peterdsp.odivrelo.core

import dev.peterdsp.odivrelo.core.model.Coverage
import dev.peterdsp.odivrelo.core.model.FavoritePlace
import dev.peterdsp.odivrelo.core.model.Freshness
import dev.peterdsp.odivrelo.core.model.InstalledPack
import dev.peterdsp.odivrelo.core.model.JourneyDetail
import dev.peterdsp.odivrelo.core.model.JourneyFilters
import dev.peterdsp.odivrelo.core.model.JourneyResults
import dev.peterdsp.odivrelo.core.model.Meta
import dev.peterdsp.odivrelo.core.model.OfflineCatalog
import dev.peterdsp.odivrelo.core.model.OperatorDetail
import dev.peterdsp.odivrelo.core.model.PackProgress
import dev.peterdsp.odivrelo.core.model.PackResult
import dev.peterdsp.odivrelo.core.model.PlaceResults
import dev.peterdsp.odivrelo.core.model.RecentSearch
import dev.peterdsp.odivrelo.core.model.SavedTrip
import dev.peterdsp.odivrelo.core.model.SourceList
import dev.peterdsp.odivrelo.core.model.StopDetail
import kotlin.coroutines.cancellation.CancellationException
import kotlin.native.ObjCName

/**
 * Everything the core needs to run. A client supplies writable locations; the
 * core never guesses a path and never writes outside the two it is given.
 *
 * The values are checked by [createOdivreloCore] rather than by this constructor.
 * A constructor that throws is hostile across the Objective-C boundary, where an
 * unconverted Kotlin exception terminates the process instead of surfacing as an
 * `NSError`, so construction always succeeds and [validationError] says what is
 * wrong.
 *
 * @param apiBaseUrl live API origin, or `null` for static-pack mode.
 * @param staticPacksBaseUrl origin serving published manifests and packs.
 * @param packsDirectory writable directory owned by the core for packs.
 * @param databasePath writable SQLite file path owned by the core.
 * @param languageTag `el`, `en` or `sq`.
 */
@ObjCName("OdivreloCoreConfig")
data class CoreConfig(
    val apiBaseUrl: String?,
    val staticPacksBaseUrl: String?,
    val packsDirectory: String,
    val databasePath: String,
    val languageTag: String,
) {
    /** True when no live API is configured and only published packs are read. */
    val isStaticPackMode: Boolean get() = apiBaseUrl.isNullOrBlank()

    val resolvedLanguageTag: String
        get() = languageTag.take(2).lowercase()
            .takeIf { it in Brand.LANGUAGES } ?: Brand.DEFAULT_LANGUAGE

    /**
     * A description of the first problem with this configuration, or null when it
     * is usable. The message is safe to show and names no secret.
     */
    val validationError: String?
        get() = when {
            packsDirectory.isBlank() ->
                "packsDirectory must be a writable directory path."

            databasePath.isBlank() ->
                "databasePath must be a writable file path."

            languageTag.isBlank() ->
                "languageTag must be one of " + Brand.LANGUAGES.joinToString(", ") + "."

            else -> null
        }

    val isValid: Boolean get() = validationError == null
}

/**
 * A handle to work started by the core. Cancelling is idempotent and always
 * safe; a cancelled download leaves no partially installed pack behind.
 */
@ObjCName("OdivreloCancellable")
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
 * ## Why every suspending member carries `@Throws`
 *
 * On Kotlin/Native an exception that is not in a function's `@Throws` list is
 * never converted into an `NSError`. The Kotlin runtime terminates the process
 * instead, before Swift has any chance to catch it. That is not a Swift problem
 * and cannot be fixed from Swift: it has to be declared here.
 *
 * Every suspending member below can fail for an ordinary, expected reason, the
 * commonest being that no data release is installed yet, which is the state every
 * fresh installation starts in. So each one declares [OdivreloException] and
 * [CancellationException], and the implementation guarantees that nothing else
 * escapes: `ExportedApiContractTest` fails the build if a member is missing the
 * annotation, and `FirstRunTest` fails if any of them throws some other type.
 *
 * The core owns every service-date, `Europe/Athens` and midnight-crossing
 * decision. A user interface must never recompute them.
 */
@ObjCName("OdivreloCore")
interface OdivreloCore {
    @Throws(OdivreloException::class, CancellationException::class)
    suspend fun meta(): Meta

    @Throws(OdivreloException::class, CancellationException::class)
    suspend fun coverage(): Coverage

    @Throws(OdivreloException::class, CancellationException::class)
    suspend fun searchPlaces(query: String, limit: Int): PlaceResults

    @Throws(OdivreloException::class, CancellationException::class)
    suspend fun searchJourneys(
        originId: String,
        destinationId: String,
        serviceDate: String,
        filters: JourneyFilters,
    ): JourneyResults

    @Throws(OdivreloException::class, CancellationException::class)
    suspend fun journeyDetail(journeyId: String, serviceDate: String): JourneyDetail

    @Throws(OdivreloException::class, CancellationException::class)
    suspend fun operatorDetail(operatorId: String): OperatorDetail

    @Throws(OdivreloException::class, CancellationException::class)
    suspend fun stopDetail(stopId: String, serviceDate: String): StopDetail

    @Throws(OdivreloException::class, CancellationException::class)
    suspend fun sources(): SourceList

    @Throws(OdivreloException::class, CancellationException::class)
    suspend fun savedTrips(): List<SavedTrip>

    @Throws(OdivreloException::class, CancellationException::class)
    suspend fun saveTrip(journeyId: String, serviceDate: String): SavedTrip

    @Throws(OdivreloException::class, CancellationException::class)
    suspend fun removeSavedTrip(savedTripId: String)

    @Throws(OdivreloException::class, CancellationException::class)
    suspend fun favorites(): List<FavoritePlace>

    /** Returns the new state: true when the place is now a favourite. */
    @Throws(OdivreloException::class, CancellationException::class)
    suspend fun toggleFavorite(placeId: String): Boolean

    @Throws(OdivreloException::class, CancellationException::class)
    suspend fun recentSearches(): List<RecentSearch>

    @Throws(OdivreloException::class, CancellationException::class)
    suspend fun offlineCatalog(): OfflineCatalog

    /** Progress is delivered on the caller's context; returns a handle that cancels the download. */
    fun downloadPack(
        packName: String,
        onProgress: (PackProgress) -> Unit,
        onResult: (PackResult) -> Unit,
    ): Cancellable

    @Throws(OdivreloException::class, CancellationException::class)
    suspend fun installedPacks(): List<InstalledPack>

    @Throws(OdivreloException::class, CancellationException::class)
    suspend fun removePack(packName: String)

    @Throws(OdivreloException::class, CancellationException::class)
    suspend fun rollbackToPreviousRelease(): Meta

    fun freshnessOf(checkedAt: String, now: String): Freshness

    fun close()
}

/**
 * Builds a core bound to the platform's HTTP engine, SQLite driver and file
 * system. Called once per process; the returned instance is safe to share.
 *
 * Throws [OdivreloException] rather than terminating when the configuration is
 * unusable or the database cannot be opened, so a host application can report it.
 */
@Throws(OdivreloException::class)
@ObjCName("OdivreloCoreFactory")
expect fun createOdivreloCore(config: CoreConfig): OdivreloCore
