package dev.peterdsp.odivrelo.core

import app.cash.sqldelight.db.SqlDriver
import dev.peterdsp.odivrelo.core.crypto.Sha256
import dev.peterdsp.odivrelo.core.db.OdivreloDatabase
import dev.peterdsp.odivrelo.core.domain.DayLoadResult
import dev.peterdsp.odivrelo.core.domain.ReleaseIndex
import dev.peterdsp.odivrelo.core.domain.ReleaseLoadFailure
import dev.peterdsp.odivrelo.core.domain.ReleaseLoadResult
import dev.peterdsp.odivrelo.core.domain.ReleaseLoader
import dev.peterdsp.odivrelo.core.io.PlatformFiles
import dev.peterdsp.odivrelo.core.model.AvailablePack
import dev.peterdsp.odivrelo.core.model.ContractError
import dev.peterdsp.odivrelo.core.model.Coverage
import dev.peterdsp.odivrelo.core.model.DataMode
import dev.peterdsp.odivrelo.core.model.DateDataState
import dev.peterdsp.odivrelo.core.model.ErrorCode
import dev.peterdsp.odivrelo.core.model.FavoritePlace
import dev.peterdsp.odivrelo.core.model.Freshness
import dev.peterdsp.odivrelo.core.model.InstalledPack
import dev.peterdsp.odivrelo.core.model.JourneyDetail
import dev.peterdsp.odivrelo.core.model.JourneyFilters
import dev.peterdsp.odivrelo.core.model.JourneyQuery
import dev.peterdsp.odivrelo.core.model.JourneyResults
import dev.peterdsp.odivrelo.core.model.LocalizedText
import dev.peterdsp.odivrelo.core.model.Meta
import dev.peterdsp.odivrelo.core.model.OfflineCatalog
import dev.peterdsp.odivrelo.core.model.OfflineManifest
import dev.peterdsp.odivrelo.core.model.OfflineMapAvailability
import dev.peterdsp.odivrelo.core.model.OperatorDetail
import dev.peterdsp.odivrelo.core.model.PackFailure
import dev.peterdsp.odivrelo.core.model.PackProgress
import dev.peterdsp.odivrelo.core.model.PackResult
import dev.peterdsp.odivrelo.core.model.PlaceKind
import dev.peterdsp.odivrelo.core.model.PlaceResults
import dev.peterdsp.odivrelo.core.model.ProductInfo
import dev.peterdsp.odivrelo.core.model.RecentSearch
import dev.peterdsp.odivrelo.core.model.SavedTrip
import dev.peterdsp.odivrelo.core.model.SourceList
import dev.peterdsp.odivrelo.core.model.StopDetail
import dev.peterdsp.odivrelo.core.model.UnavailableReason
import dev.peterdsp.odivrelo.core.net.ApiClient
import dev.peterdsp.odivrelo.core.net.ApiOutcome
import dev.peterdsp.odivrelo.core.packs.JourneysPack
import dev.peterdsp.odivrelo.core.packs.PackDownloader
import dev.peterdsp.odivrelo.core.packs.PackNames
import dev.peterdsp.odivrelo.core.packs.PackStore
import dev.peterdsp.odivrelo.core.serialization.OdivreloJson
import dev.peterdsp.odivrelo.core.time.ServiceTime
import io.ktor.client.HttpClient
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

/**
 * What went wrong, in a form a user interface can branch on.
 *
 * Contract 1's [ErrorCode] is deliberately coarse: `unavailable` covers several
 * genuinely different situations whose remedies differ. A screen needs to know
 * whether to offer "install data", "reinstall data", "download this date" or
 * "retry", so the distinction is carried here rather than inferred from wording.
 */
@kotlin.native.ObjCName("OdivreloFailureKind")
enum class OdivreloFailureKind {
    GENERAL,

    /** No release is installed and none could be fetched. */
    NO_DATA_INSTALLED,

    /** A release is installed but packs are missing from it. */
    INCOMPLETE_DATA,

    /** Installed data could not be decoded and needs reinstalling. */
    UNREADABLE_DATA,

    /** Installed packs, or an API answer, disagree about the release. */
    RELEASE_MISMATCH,

    /**
     * The installed release publishes no timetable for the requested date, and
     * no API was reachable to resolve it. This is not "no service runs".
     */
    NO_OFFLINE_PACK_FOR_DATE,

    NETWORK_UNAVAILABLE,

    STORAGE_FULL,

    NOT_FOUND,

    INVALID_REQUEST,
}

/**
 * Thrown when the core genuinely cannot answer. The message is safe to show; it
 * never contains a path, a token or anything from a ticket.
 */
class OdivreloException(
    val code: ErrorCode,
    override val message: String,
    val field: String? = null,
    val kind: OdivreloFailureKind = OdivreloFailureKind.GENERAL,
) : Exception(message) {
    internal constructor(error: ContractError) : this(
        error.code,
        error.message,
        error.field,
        when (error.code) {
            ErrorCode.NOT_FOUND -> OdivreloFailureKind.NOT_FOUND
            ErrorCode.INVALID_REQUEST -> OdivreloFailureKind.INVALID_REQUEST
            ErrorCode.RELEASE_MISMATCH -> OdivreloFailureKind.RELEASE_MISMATCH
            ErrorCode.UNAVAILABLE -> OdivreloFailureKind.NETWORK_UNAVAILABLE
            ErrorCode.UNAUTHORIZED -> OdivreloFailureKind.GENERAL
        },
    )
}

/**
 * The working core.
 *
 * A pack contains exactly what the matching `/v1/...` endpoint returns, so this
 * implementation has one read path with two transports rather than two read
 * paths. A pack is obtained from the installed release if it is there, otherwise
 * fetched from the configured origin, verified against the manifest digest and
 * installed. Whichever way the bytes arrived, the same decoder and the same
 * query code produce the answer, so a bundled pack, a downloaded pack and an
 * endpoint response cannot disagree.
 *
 * Everything to do with service dates, `Europe/Athens` and midnight crossings is
 * resolved here. A user interface never recomputes any of it.
 */
internal class OdivreloCoreImpl(
    private val config: CoreConfig,
    private val httpClient: HttpClient,
    private val driver: SqlDriver,
    private val clock: Clock = Clock.System,
    retryDelays: List<Duration> = PackDownloader.DEFAULT_RETRY_DELAYS,
    private val ownsHttpClient: Boolean = true,
) : OdivreloCore, OdivreloCoreExtras {

    // The driver arrives with the schema already created or migrated: that is the
    // platform factory's job, because only it knows whether the underlying SQLite
    // layer runs the callbacks itself.
    private val database: OdivreloDatabase = OdivreloDatabase(driver)
    private val queries = database.odivreloQueries
    private val store = PackStore(config.packsDirectory)
    private val loader = ReleaseLoader(store)
    private val downloader = PackDownloader(httpClient, store, retryDelays)
    private val apiClient = ApiClient(
        httpClient = httpClient,
        apiBaseUrl = config.apiBaseUrl,
        staticPacksBaseUrl = config.staticPacksBaseUrl,
    )

    private val readLock = Mutex()
    private val writeLock = Mutex()
    private var cachedIndex: ReleaseIndex? = null

    /**
     * The few service dates a person is actually looking at. A release can cover
     * hundreds of dates and loading all of them would be slow and pointless.
     */
    private val cachedDays = LinkedHashMap<String, JourneysPack>()
    private var cachedManifest: OfflineManifest? = null

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var closed = false

    init {
        store.prepare()
    }

    /**
     * Runs one exported operation.
     *
     * Everything the core exports declares `@Throws(OdivreloException, ...)`. On
     * Kotlin/Native an exception outside that list is not converted into an
     * NSError, it terminates the process, so the declaration is only half the
     * guarantee. This is the other half: whatever a driver, a decoder or a file
     * system throws is translated into a OdivreloException here, at the boundary,
     * before it can cross it.
     *
     * Cancellation is re-thrown untouched: someone who left a screen has not hit
     * an error, and the coroutine machinery needs the real exception.
     */
    private suspend fun <T> onCore(block: suspend () -> T): T = try {
        withContext(Dispatchers.Default) { block() }
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (expected: OdivreloException) {
        throw expected
    } catch (error: Throwable) {
        throw OdivreloException(
            code = ErrorCode.UNAVAILABLE,
            message = "Odivrelo could not complete that request.",
            kind = OdivreloFailureKind.GENERAL,
        )
    }

    // -- Release access ------------------------------------------------------

    private suspend fun index(): ReleaseIndex {
        readLock.withLock { cachedIndex }?.let { return it }

        // Nothing usable is installed. If an origin is configured, fetch the
        // release's static packs before giving up, so a first run with a network
        // works without the person hunting for a download button.
        if (loader.load() is ReleaseLoadResult.Failed && apiClient.hasOrigin) {
            fetchRequiredPacks()
        }

        return readLock.withLock {
            cachedIndex?.let { return@withLock it }
            when (val result = loader.load()) {
                is ReleaseLoadResult.Loaded -> {
                    cachedIndex = result.index
                    result.index
                }

                is ReleaseLoadResult.Failed -> throw failureFor(result.failure)
            }
        }
    }

    private suspend fun indexOrNull(): ReleaseIndex? = try {
        index()
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: OdivreloException) {
        null
    }

    /**
     * What is known about one service date.
     *
     * The two absences are kept apart deliberately. [DayOutcome.NoPackForDate]
     * means this device holds no timetable for that date; the release only
     * materialises packs for dates its data names, so a weekday-recurring
     * service on some other date is simply not in any pack. That is a statement
     * about coverage. "No service runs that date" is a statement about service,
     * and only a pack or the API can make it.
     */
    private sealed interface DayOutcome {
        data class Pack(val day: JourneysPack) : DayOutcome
        data object NoPackForDate : DayOutcome
    }

    /**
     * One service date's journeys, from disk when installed and from the origin
     * otherwise. The same bytes and the same decoder either way.
     */
    private suspend fun day(serviceDate: String): DayOutcome {
        readLock.withLock { cachedDays[serviceDate] }?.let { return DayOutcome.Pack(it) }

        when (val first = loader.loadDay(serviceDate)) {
            is DayLoadResult.Loaded -> return DayOutcome.Pack(remember(serviceDate, first.day))

            is DayLoadResult.Failed -> throw failureFor(first.failure)

            DayLoadResult.NotInRelease -> return DayOutcome.NoPackForDate

            is DayLoadResult.NotInstalled -> {
                val failure = fetchPack(first.packName, onProgress = {})
                if (failure != null) {
                    throw OdivreloException(
                        code = when (failure) {
                            PackFailure.RELEASE_MISMATCH -> ErrorCode.RELEASE_MISMATCH
                            PackFailure.DIGEST_MISMATCH -> ErrorCode.RELEASE_MISMATCH
                            else -> ErrorCode.UNAVAILABLE
                        },
                        message = describeDownload(first.packName, failure),
                        kind = when (failure) {
                            PackFailure.RELEASE_MISMATCH, PackFailure.DIGEST_MISMATCH ->
                                OdivreloFailureKind.RELEASE_MISMATCH

                            PackFailure.STORAGE_FULL -> OdivreloFailureKind.STORAGE_FULL
                            PackFailure.NOT_IN_MANIFEST -> OdivreloFailureKind.NO_OFFLINE_PACK_FOR_DATE
                            else -> OdivreloFailureKind.NETWORK_UNAVAILABLE
                        },
                    )
                }
                return when (val second = loader.loadDay(serviceDate)) {
                    is DayLoadResult.Loaded -> DayOutcome.Pack(remember(serviceDate, second.day))
                    is DayLoadResult.Failed -> throw failureFor(second.failure)
                    else -> DayOutcome.NoPackForDate
                }
            }
        }
    }

    private suspend fun remember(serviceDate: String, day: JourneysPack): JourneysPack =
        readLock.withLock {
            cachedDays[serviceDate] = day
            while (cachedDays.size > MAX_CACHED_DAYS) {
                val oldest = cachedDays.keys.first()
                cachedDays.remove(oldest)
            }
            day
        }

    private fun failureFor(failure: ReleaseLoadFailure): OdivreloException = OdivreloException(
        code = if (failure is ReleaseLoadFailure.ReleaseMismatch) {
            ErrorCode.RELEASE_MISMATCH
        } else {
            ErrorCode.UNAVAILABLE
        },
        message = describe(failure),
        kind = when (failure) {
            is ReleaseLoadFailure.NoManifest -> OdivreloFailureKind.NO_DATA_INSTALLED
            is ReleaseLoadFailure.MissingPacks -> OdivreloFailureKind.INCOMPLETE_DATA
            is ReleaseLoadFailure.ReleaseMismatch -> OdivreloFailureKind.RELEASE_MISMATCH
            is ReleaseLoadFailure.Corrupt -> OdivreloFailureKind.UNREADABLE_DATA
        },
    )

    private fun describe(failure: ReleaseLoadFailure): String = when (failure) {
        is ReleaseLoadFailure.NoManifest -> "No offline data is installed yet."

        is ReleaseLoadFailure.MissingPacks ->
            "Offline data is incomplete: " + failure.names.joinToString(", ") + "."

        is ReleaseLoadFailure.ReleaseMismatch ->
            "Offline data mixes releases and was refused: " + failure.packName +
                " belongs to " + failure.found + ", not " + failure.expected + "."

        is ReleaseLoadFailure.Corrupt -> "Offline data is unreadable: " + failure.packName + "."
    }

    private fun describeDownload(packName: String, failure: PackFailure): String = when (failure) {
        PackFailure.NETWORK, PackFailure.TIMEOUT ->
            "There is no connection to fetch " + packName + "."

        PackFailure.DIGEST_MISMATCH ->
            "The downloaded " + packName + " did not match its published checksum and was discarded."

        PackFailure.RELEASE_MISMATCH ->
            "The downloaded " + packName + " belongs to a different data release and was refused."

        PackFailure.STORAGE_FULL -> "There is not enough free space to install " + packName + "."
        PackFailure.NOT_IN_MANIFEST -> "This data release does not publish " + packName + "."
        PackFailure.CANCELLED -> "The download of " + packName + " was cancelled."
        PackFailure.IO -> "The download of " + packName + " could not be written to storage."
    }

    private suspend fun invalidateRelease() = readLock.withLock {
        cachedIndex = null
        cachedDays.clear()
    }

    private fun now(): Instant = clock.now()

    private fun nowText(): String = now().toString()

    // -- Contract reads ------------------------------------------------------

    override suspend fun meta(): Meta = onCore {
        val index = index()
        index.meta.copy(
            contractVersion = Brand.CONTRACT_VERSION,
            releaseId = index.releaseId,
            publishedAt = index.publishedAt,
            product = index.meta.product.takeIf { it.name.isNotBlank() } ?: ProductInfo(),
        )
    }

    override suspend fun coverage(): Coverage = onCore {
        index().coverage
    }

    override suspend fun searchPlaces(query: String, limit: Int): PlaceResults =
        onCore {
            val index = index()
            val found = index.searchPlaces(query, limit.coerceIn(0, MAX_PLACE_RESULTS))
            PlaceResults(
                contractVersion = Brand.CONTRACT_VERSION,
                releaseId = index.releaseId,
                dataMode = index.dataMode,
                places = found,
                total = found.size,
                query = query,
            )
        }

    override suspend fun searchJourneys(
        originId: String,
        destinationId: String,
        serviceDate: String,
        filters: JourneyFilters,
    ): JourneyResults = onCore {
        // Request validation comes first. A malformed date is malformed whether or
        // not a release is installed, and reporting "no data" for it would send
        // someone to the offline screen to fix a typo.
        ServiceTime.parseServiceDateOrNull(serviceDate)
            ?: throw OdivreloException(
                ErrorCode.INVALID_REQUEST,
                "Service date must be YYYY-MM-DD.",
                "date",
                OdivreloFailureKind.INVALID_REQUEST,
            )
        val index = index()

        when (val outcome = day(serviceDate)) {
            is DayOutcome.Pack -> {
                val found = index.journeysBetween(
                    outcome.day,
                    originId,
                    destinationId,
                    filters,
                    now(),
                )
                if (found.results.isNotEmpty()) {
                    recordRecentSearch(index, originId, destinationId, serviceDate)
                }
                JourneyResults(
                    contractVersion = Brand.CONTRACT_VERSION,
                    releaseId = index.releaseId,
                    dataMode = index.dataMode,
                    query = JourneyQuery(originId, destinationId, serviceDate),
                    coverage = index.coverageFor(originId, destinationId),
                    results = found.results,
                    unavailableReason = found.unavailableReason,
                    servedFromCache = true,
                    cachedReleasePublishedAt = index.publishedAt,
                    dateDataState = DateDataState.FROM_INSTALLED_PACK,
                )
            }

            DayOutcome.NoPackForDate -> {
                // The release names no timetable for this date. Ask the service if
                // one is configured; otherwise say plainly that this device has no
                // data for the date, which is not the same as saying nothing runs.
                val live = resolveLive(index, originId, destinationId, serviceDate, filters)
                live ?: JourneyResults(
                    contractVersion = Brand.CONTRACT_VERSION,
                    releaseId = index.releaseId,
                    dataMode = index.dataMode,
                    query = JourneyQuery(originId, destinationId, serviceDate),
                    coverage = index.coverageFor(originId, destinationId),
                    results = emptyList(),
                    // Deliberately null: there is no claim about service to make.
                    unavailableReason = null,
                    servedFromCache = true,
                    cachedReleasePublishedAt = index.publishedAt,
                    dateDataState = DateDataState.NO_OFFLINE_PACK,
                )
            }
        }
    }

    /**
     * Asks the live service for a date no pack covers.
     *
     * The answer is refused if it belongs to a different release than the one
     * installed, because merging the two would be exactly the mixing contract 1
     * forbids. Returns null when no service is configured or it did not answer.
     */
    private suspend fun resolveLive(
        index: ReleaseIndex,
        originId: String,
        destinationId: String,
        serviceDate: String,
        filters: JourneyFilters,
    ): JourneyResults? {
        if (!apiClient.hasApi) return null
        val outcome = apiClient.journeys(
            originId = originId,
            destinationId = destinationId,
            serviceDate = serviceDate,
            languageTag = config.resolvedLanguageTag,
            accessibleOnly = filters.accessibleOnly,
        )
        val answer = when (outcome) {
            is ApiOutcome.Success -> outcome.value
            is ApiOutcome.Failed -> return null
        }
        if (answer.releaseId != index.releaseId) {
            throw OdivreloException(
                code = ErrorCode.RELEASE_MISMATCH,
                message = "The service answered for data release " + answer.releaseId +
                    " while this device holds " + index.releaseId + ".",
                kind = OdivreloFailureKind.RELEASE_MISMATCH,
            )
        }
        if (answer.results.isNotEmpty()) {
            recordRecentSearch(index, originId, destinationId, serviceDate)
        }
        // Filtering and ordering are applied by the same code either way, so a
        // live answer and a pack answer cannot be sorted or filtered differently.
        val narrowed = index.applyFiltersAndOrder(answer.results, filters, now())
        return answer.copy(
            results = narrowed,
            unavailableReason = if (narrowed.isEmpty()) {
                answer.unavailableReason ?: UnavailableReason.NO_SERVICE_ON_DATE
            } else {
                null
            },
            servedFromCache = false,
            cachedReleasePublishedAt = index.publishedAt,
            dateDataState = DateDataState.FROM_API,
        )
    }

    override suspend fun journeyDetail(journeyId: String, serviceDate: String): JourneyDetail =
        onCore {
            ServiceTime.parseServiceDateOrNull(serviceDate)
                ?: throw OdivreloException(
                    ErrorCode.INVALID_REQUEST,
                    "Service date must be YYYY-MM-DD.",
                    "date",
                    OdivreloFailureKind.INVALID_REQUEST,
                )
            val index = index()

            when (val outcome = day(serviceDate)) {
                is DayOutcome.Pack -> {
                    val body = index.journeyDetail(outcome.day, journeyId, now())
                        ?: throw OdivreloException(
                            ErrorCode.NOT_FOUND,
                            "That journey does not run on the selected date.",
                            kind = OdivreloFailureKind.NOT_FOUND,
                        )
                    JourneyDetail(
                        contractVersion = Brand.CONTRACT_VERSION,
                        releaseId = index.releaseId,
                        dataMode = index.dataMode,
                        journey = body,
                        servedFromCache = true,
                    )
                }

                DayOutcome.NoPackForDate -> {
                    val live = if (apiClient.hasApi) {
                        apiClient.journeyDetail(journeyId, serviceDate, config.resolvedLanguageTag)
                    } else {
                        null
                    }
                    when (live) {
                        is ApiOutcome.Success -> {
                            if (live.value.releaseId != index.releaseId) {
                                throw OdivreloException(
                                    ErrorCode.RELEASE_MISMATCH,
                                    "The service answered for data release " +
                                        live.value.releaseId + " while this device holds " +
                                        index.releaseId + ".",
                                    kind = OdivreloFailureKind.RELEASE_MISMATCH,
                                )
                            }
                            live.value.copy(servedFromCache = false)
                        }

                        else -> throw OdivreloException(
                            ErrorCode.UNAVAILABLE,
                            "This device holds no timetable for " + serviceDate +
                                ". That is not the same as no service running.",
                            "date",
                            OdivreloFailureKind.NO_OFFLINE_PACK_FOR_DATE,
                        )
                    }
                }
            }
        }

    override suspend fun operatorDetail(operatorId: String): OperatorDetail =
        onCore {
            val index = index()
            val operator = index.operator(operatorId)
                ?: throw OdivreloException(ErrorCode.NOT_FOUND, "Unknown operator.")
            OperatorDetail(
                contractVersion = Brand.CONTRACT_VERSION,
                releaseId = index.releaseId,
                dataMode = index.dataMode,
                operator = operator,
                servedFromCache = true,
            )
        }

    override suspend fun stopDetail(stopId: String, serviceDate: String): StopDetail =
        onCore {
            ServiceTime.parseServiceDateOrNull(serviceDate)
                ?: throw OdivreloException(
                    ErrorCode.INVALID_REQUEST,
                    "Service date must be YYYY-MM-DD.",
                    "date",
                    OdivreloFailureKind.INVALID_REQUEST,
                )
            val index = index()
            val stop = index.stop(stopId)
                ?: throw OdivreloException(
                    ErrorCode.NOT_FOUND,
                    "Unknown stop.",
                    kind = OdivreloFailureKind.NOT_FOUND,
                )
            val outcome = day(serviceDate)
            val departures = when (outcome) {
                is DayOutcome.Pack -> index.departuresFrom(outcome.day, stopId, now())
                DayOutcome.NoPackForDate -> emptyList()
            }
            StopDetail(
                contractVersion = Brand.CONTRACT_VERSION,
                releaseId = index.releaseId,
                dataMode = index.dataMode,
                stop = stop.copy(departures = departures),
                serviceDate = serviceDate,
                servedFromCache = true,
                dateDataState = when (outcome) {
                    is DayOutcome.Pack -> DateDataState.FROM_INSTALLED_PACK
                    DayOutcome.NoPackForDate -> DateDataState.NO_OFFLINE_PACK
                },
            )
        }

    override suspend fun sources(): SourceList = onCore {
        val index = index()
        SourceList(
            contractVersion = Brand.CONTRACT_VERSION,
            releaseId = index.releaseId,
            dataMode = index.dataMode,
            sources = index.allSources(),
        )
    }

    // -- Person-owned state --------------------------------------------------

    override suspend fun savedTrips(): List<SavedTrip> = onCore {
        queries.selectSavedTrips().executeAsList().map { row ->
            SavedTrip(
                id = row.id,
                journeyId = row.journey_id,
                serviceDate = row.service_date,
                savedAt = row.saved_at,
                originName = decodeName(row.origin_name_json),
                destinationName = decodeName(row.destination_name_json),
                operatorName = decodeName(row.operator_name_json),
                departureAt = row.departure_at,
                arrivalAt = row.arrival_at,
                crossesMidnight = row.crosses_midnight != 0L,
                boardingBay = row.boarding_bay,
                boardingStepFree = row.boarding_step_free?.let { it != 0L },
                releaseId = row.release_id,
                cachedAt = row.cached_at,
            )
        }
    }

    override suspend fun saveTrip(journeyId: String, serviceDate: String): SavedTrip =
        onCore {
            val detail = journeyDetail(journeyId, serviceDate)
            val body = detail.journey
            val savedAt = nowText()
            val trip = SavedTrip(
                id = Sha256.hex(journeyId + "|" + serviceDate).take(SAVED_TRIP_ID_LENGTH),
                journeyId = journeyId,
                serviceDate = serviceDate,
                savedAt = savedAt,
                originName = body.departure.stopName,
                destinationName = body.arrival.stopName,
                operatorName = body.operator.name,
                departureAt = body.departure.at,
                arrivalAt = body.arrival.at,
                crossesMidnight = body.crossesMidnight,
                boardingBay = body.boardingPoint?.bay,
                boardingStepFree = body.boardingPoint?.stepFree,
                releaseId = detail.releaseId,
                cachedAt = savedAt,
            )
            writeLock.withLock {
                queries.upsertSavedTrip(
                    id = trip.id,
                    journey_id = trip.journeyId,
                    service_date = trip.serviceDate,
                    saved_at = trip.savedAt,
                    origin_name_json = encodeName(trip.originName),
                    destination_name_json = encodeName(trip.destinationName),
                    operator_name_json = encodeName(trip.operatorName),
                    departure_at = trip.departureAt,
                    arrival_at = trip.arrivalAt,
                    crosses_midnight = if (trip.crossesMidnight) 1L else 0L,
                    boarding_bay = trip.boardingBay,
                    release_id = trip.releaseId,
                    cached_at = trip.cachedAt,
                    detail_json = OdivreloJson.instance.encodeToString(
                        JourneyDetail.serializer(),
                        detail,
                    ),
                    boarding_step_free = trip.boardingStepFree?.let { if (it) 1L else 0L },
                )
            }
            trip
        }

    override suspend fun removeSavedTrip(savedTripId: String) {
        onCore {
            writeLock.withLock { queries.deleteSavedTrip(savedTripId) }
        }
    }

    override suspend fun savedTripDetail(savedTripId: String): JourneyDetail? =
        onCore {
            val row = queries.selectSavedTripById(savedTripId).executeAsOneOrNull()
                ?: return@onCore null
            runCatching {
                OdivreloJson.instance.decodeFromString(JourneyDetail.serializer(), row.detail_json)
            }.getOrNull()
        }

    override suspend fun favorites(): List<FavoritePlace> = onCore {
        queries.selectFavorites().executeAsList().map { row ->
            FavoritePlace(
                placeId = row.place_id,
                name = decodeName(row.name_json),
                kind = if (row.kind == PlaceKind.STOP_PLACE.name) {
                    PlaceKind.STOP_PLACE
                } else {
                    PlaceKind.STOP
                },
                municipality = row.municipality,
                addedAt = row.added_at,
            )
        }
    }

    override suspend fun toggleFavorite(placeId: String): Boolean =
        onCore {
            val existing = queries.selectFavorite(placeId).executeAsOneOrNull()
            if (existing != null) {
                writeLock.withLock { queries.deleteFavorite(placeId) }
                return@onCore false
            }
            val place = indexOrNull()?.place(placeId)
                ?: throw OdivreloException(
                    ErrorCode.NOT_FOUND,
                    "That place is not in the installed data.",
                )
            writeLock.withLock {
                queries.insertFavorite(
                    place_id = place.id,
                    name_json = encodeName(place.name),
                    kind = place.kind.name,
                    municipality = place.municipality,
                    added_at = nowText(),
                )
            }
            true
        }

    override suspend fun recentSearches(): List<RecentSearch> = onCore {
        queries.selectRecentSearches(MAX_RECENT_SEARCHES.toLong()).executeAsList().map { row ->
            RecentSearch(
                originId = row.origin_id,
                originName = decodeName(row.origin_name_json),
                destinationId = row.destination_id,
                destinationName = decodeName(row.destination_name_json),
                serviceDate = row.service_date,
                searchedAt = row.searched_at,
            )
        }
    }

    private suspend fun recordRecentSearch(
        index: ReleaseIndex,
        originId: String,
        destinationId: String,
        serviceDate: String,
    ) {
        val origin = index.place(originId) ?: return
        val destination = index.place(destinationId) ?: return
        writeLock.withLock {
            queries.upsertRecentSearch(
                origin_id = origin.id,
                origin_name_json = encodeName(origin.name),
                destination_id = destination.id,
                destination_name_json = encodeName(destination.name),
                service_date = serviceDate,
                searched_at = nowText(),
            )
            queries.trimRecentSearches(MAX_RECENT_SEARCHES.toLong())
        }
    }

    // -- Offline packs -------------------------------------------------------

    override suspend fun offlineCatalog(): OfflineCatalog = onCore {
        val installedManifest = store.installedManifest()
        val remote = remoteManifest()
        val reference = remote ?: installedManifest
        val installedRows = installedPacks()

        val available = reference?.files.orEmpty().entries
            .sortedBy { sortKeyFor(it.key) }
            .map { (name, file) ->
                val row = installedRows.firstOrNull { it.name == name }
                AvailablePack(
                    name = name,
                    path = file.path,
                    sha256 = file.sha256,
                    bytes = file.bytes,
                    releaseId = reference!!.releaseId,
                    installed = row != null &&
                        row.sha256 == file.sha256 &&
                        row.releaseId == reference.releaseId,
                    updateAvailable = row != null && row.sha256 != file.sha256,
                    title = packTitle(name),
                    summary = packSummary(name),
                )
            }

        OfflineCatalog(
            releaseId = reference?.releaseId ?: "",
            publishedAt = reference?.publishedAt ?: "",
            dataMode = reference?.dataMode ?: DataMode.DEMO,
            available = available,
            installed = installedRows,
            totalInstalledBytes = store.installedBytes(),
            rollbackReleaseId = store.previousManifest()?.releaseId,
            mapAvailability = OfflineMapAvailability(
                stopCoordinatesAvailable = true,
                routeGeometryAvailable = true,
                // Odivrelo bundles no base-map tiles. Saying so is not a footnote:
                // someone planning to rely on this offline needs to know the map
                // background will not draw.
                baseMapTilesAvailable = false,
                searchAvailable = true,
            ),
            manifestReachable = remote != null,
        )
    }

    /** Journeys packs sort after the static ones, then by service date. */
    private fun sortKeyFor(packName: String): String {
        val date = PackNames.serviceDateOf(packName)
        return if (date != null) "1-" + date else "0-" + packName
    }

    override fun downloadPack(
        packName: String,
        onProgress: (PackProgress) -> Unit,
        onResult: (PackResult) -> Unit,
    ): Cancellable {
        val job = scope.launch {
            val result = runCatching { runDownload(packName, onProgress) }
                .getOrElse { error ->
                    if (error is CancellationException) {
                        PackResult(
                            packName = packName,
                            succeeded = false,
                            failure = PackFailure.CANCELLED,
                            message = "cancelled",
                        )
                    } else {
                        PackResult(
                            packName = packName,
                            succeeded = false,
                            failure = PackFailure.IO,
                            message = error.message,
                        )
                    }
                }
            onResult(result)
        }
        return JobCancellable(job)
    }

    /** Cached for the life of one screen visit so a six-pack install fetches it once. */
    private suspend fun remoteManifest(): OfflineManifest? {
        readLock.withLock { cachedManifest }?.let { return it }
        return when (val outcome = apiClient.manifest()) {
            is ApiOutcome.Success -> readLock.withLock {
                cachedManifest = outcome.value
                outcome.value
            }

            is ApiOutcome.Failed -> null
        }
    }

    private suspend fun runDownload(
        packName: String,
        onProgress: (PackProgress) -> Unit,
    ): PackResult {
        val manifest = remoteManifest() ?: return PackResult(
            packName = packName,
            succeeded = false,
            failure = PackFailure.NETWORK,
            message = "The published data release could not be reached.",
        )
        val file = manifest.files[packName] ?: return PackResult(
            packName = packName,
            succeeded = false,
            failure = PackFailure.NOT_IN_MANIFEST,
            message = "Pack '" + packName + "' is not in release " + manifest.releaseId + ".",
        )
        val base = apiClient.packBaseUrl() ?: return PackResult(
            packName = packName,
            succeeded = false,
            failure = PackFailure.NETWORK,
            message = "No pack origin is configured.",
        )

        writeLock.withLock {
            if (store.adoptRelease(manifest)) {
                // The installed release changed wholesale, so the packs that
                // belonged to the old one are no longer installed.
                queries.deleteAllInstalledPacks()
            }
        }
        invalidateRelease()

        val result = downloader.download(
            packName = packName,
            manifest = manifest,
            manifestFile = file,
            baseUrl = base,
            onProgress = onProgress,
        )

        if (result.succeeded && result.installed != null) {
            val previous = queries.selectInstalledPack(packName).executeAsOneOrNull()
            val record = result.installed.copy(
                installedAt = nowText(),
                previousReleaseId = previous?.release_id?.takeIf { it != manifest.releaseId },
            )
            writeLock.withLock {
                queries.upsertInstalledPack(
                    name = record.name,
                    release_id = record.releaseId,
                    sha256 = record.sha256,
                    bytes = record.bytes,
                    installed_at = record.installedAt,
                    published_at = record.publishedAt,
                    file_name = file.path,
                    previous_release_id = record.previousReleaseId,
                    previous_file_name = previous?.file_name,
                    previous_sha256 = previous?.sha256,
                )
                upsertReleaseState(manifest)
            }
            invalidateRelease()
            return result.copy(installed = record)
        }
        return result
    }

    /** Fetches one pack, returning null on success. Used by the implicit read path. */
    private suspend fun fetchPack(
        packName: String,
        onProgress: (PackProgress) -> Unit,
    ): PackFailure? {
        val result = runDownload(packName, onProgress)
        return if (result.succeeded) null else (result.failure ?: PackFailure.IO)
    }

    private suspend fun fetchRequiredPacks() {
        PackNames.required.forEach { name ->
            if (fetchPack(name, onProgress = {}) != null) return
        }
    }

    private fun upsertReleaseState(manifest: OfflineManifest) {
        val existing = queries.selectReleaseState().executeAsOneOrNull()
        val previousReleaseId = existing?.release_id?.takeIf { it != manifest.releaseId }
        queries.upsertReleaseState(
            release_id = manifest.releaseId,
            published_at = manifest.publishedAt,
            data_mode = manifest.dataMode.name,
            manifest_json = OdivreloJson.instance.encodeToString(
                OfflineManifest.serializer(),
                manifest,
            ),
            updated_at = nowText(),
            previous_release_id = previousReleaseId,
            previous_published_at = previousReleaseId?.let { existing?.published_at },
            previous_manifest_json = previousReleaseId?.let { existing?.manifest_json },
        )
    }

    override suspend fun installedPacks(): List<InstalledPack> = onCore {
        queries.selectInstalledPacks().executeAsList().map { row ->
            InstalledPack(
                name = row.name,
                releaseId = row.release_id,
                sha256 = row.sha256,
                bytes = row.bytes,
                installedAt = row.installed_at,
                publishedAt = row.published_at,
                previousReleaseId = row.previous_release_id,
            )
        }
    }

    override suspend fun removePack(packName: String) {
        onCore {
            val file = store.installedManifest()?.files?.get(packName)
            writeLock.withLock {
                if (file != null) store.removePack(file)
                store.discardPartial(packName)
                queries.deleteInstalledPack(packName)
            }
            invalidateRelease()
        }
    }

    override suspend fun rollbackToPreviousRelease(): Meta = onCore {
        val restored = writeLock.withLock {
            val manifest = store.rollback()
                ?: throw OdivreloException(
                    ErrorCode.NOT_FOUND,
                    "There is no previous data release to go back to.",
                )
            queries.deleteAllInstalledPacks()
            manifest.files.forEach { (name, file) ->
                if (store.isInstalled(file)) {
                    queries.upsertInstalledPack(
                        name = name,
                        release_id = manifest.releaseId,
                        sha256 = file.sha256,
                        bytes = file.bytes,
                        installed_at = nowText(),
                        published_at = manifest.publishedAt,
                        file_name = file.path,
                        previous_release_id = null,
                        previous_file_name = null,
                        previous_sha256 = null,
                    )
                }
            }
            upsertReleaseState(manifest)
            manifest
        }
        invalidateRelease()
        // A rolled-back release must not be quietly replaced by the newer one the
        // origin is still advertising, so the cached remote manifest is dropped.
        readLock.withLock { cachedManifest = null }

        val index = index()
        if (index.releaseId != restored.releaseId) {
            throw OdivreloException(
                ErrorCode.RELEASE_MISMATCH,
                "The restored release did not load cleanly.",
            )
        }
        meta()
    }

    override suspend fun adoptSeededRelease(): Boolean = onCore {
        val manifest = store.installedManifest() ?: return@onCore false
        var registered = 0
        writeLock.withLock {
            manifest.files.forEach { (name, file) ->
                if (!store.isInstalled(file)) return@forEach
                val bytes = runCatching { PlatformFiles.readBytes(store.packPath(file)) }.getOrNull()
                    ?: return@forEach
                // A bundled file gets no more trust than a downloaded one.
                if (!Sha256.digestsMatch(file.sha256, Sha256().update(bytes).hexDigest())) {
                    store.removePack(file)
                    return@forEach
                }
                queries.upsertInstalledPack(
                    name = name,
                    release_id = manifest.releaseId,
                    sha256 = file.sha256,
                    bytes = file.bytes,
                    installed_at = nowText(),
                    published_at = manifest.publishedAt,
                    file_name = file.path,
                    previous_release_id = null,
                    previous_file_name = null,
                    previous_sha256 = null,
                )
                registered++
            }
            upsertReleaseState(manifest)
        }
        invalidateRelease()
        registered > 0
    }

    // -- Misc ----------------------------------------------------------------

    override fun freshnessOf(checkedAt: String, now: String): Freshness =
        ServiceTime.freshness(checkedAt, now)

    override fun close() {
        if (closed) return
        closed = true
        scope.cancel()
        runCatching { driver.close() }
        if (ownsHttpClient) runCatching { httpClient.close() }
    }

    private fun encodeName(value: LocalizedText): String =
        OdivreloJson.instance.encodeToString(LocalizedText.serializer(), value)

    private fun decodeName(value: String): LocalizedText = runCatching {
        OdivreloJson.instance.decodeFromString(LocalizedText.serializer(), value)
    }.getOrElse { LocalizedText.of(value) }

    private fun packTitle(name: String): LocalizedText {
        PackNames.serviceDateOf(name)?.let { date ->
            return LocalizedText(
                "Δρομολόγια " + date,
                "Journeys for " + date,
                "Udhëtimet për " + date,
            )
        }
        return when (name) {
            PackNames.META -> LocalizedText("Έκδοση δεδομένων", "Data release", "Publikimi i të dhënave")
            PackNames.COVERAGE -> LocalizedText("Κάλυψη", "Coverage", "Mbulimi")
            PackNames.PLACES -> LocalizedText("Τόποι", "Places", "Vendet")
            PackNames.STOPS -> LocalizedText("Στάσεις", "Stops", "Ndalesat")
            PackNames.OPERATORS -> LocalizedText("Μεταφορείς", "Operators", "Operatorët")
            PackNames.SOURCES -> LocalizedText("Πηγές", "Sources", "Burimet")
            PackNames.GTFS -> LocalizedText("Αρχείο GTFS", "GTFS archive", "Arkivi GTFS")
            else -> LocalizedText.of(name)
        }
    }

    private fun packSummary(name: String): LocalizedText {
        PackNames.serviceDateOf(name)?.let {
            return LocalizedText(
                "Προγραμματισμένα δρομολόγια και στάσεις για αυτή την ημέρα.",
                "Scheduled journeys and their stops for that day.",
                "Udhëtimet e planifikuara dhe ndalesat për atë ditë.",
            )
        }
        return when (name) {
            PackNames.META -> LocalizedText(
                "Ταυτότητα έκδοσης και αποδόσεις.",
                "Release identity and attribution.",
                "Identiteti i publikimit dhe atribuimi.",
            )

            PackNames.COVERAGE -> LocalizedText(
                "Τι καλύπτεται και τι δεν καλύπτεται.",
                "What is covered and what is not.",
                "Çfarë mbulohet dhe çfarë nuk mbulohet.",
            )

            PackNames.PLACES -> LocalizedText(
                "Τερματικοί σταθμοί και σημεία επιβίβασης για αναζήτηση χωρίς δίκτυο.",
                "Terminals and boarding points for search without a network.",
                "Terminalet dhe pikat e hipjes për kërkim pa rrjet.",
            )

            PackNames.STOPS -> LocalizedText(
                "Στάσεις με συντεταγμένες και σημεία επιβίβασης.",
                "Stops with coordinates and boarding points.",
                "Ndalesat me koordinata dhe pika hipjeje.",
            )

            PackNames.OPERATORS -> LocalizedText(
                "Στοιχεία μεταφορέων και επικοινωνία.",
                "Operator details and contact information.",
                "Detajet e operatorëve dhe kontakti.",
            )

            PackNames.SOURCES -> LocalizedText(
                "Μητρώο πηγών με δικαιώματα και άδειες.",
                "Source registry with rights and licences.",
                "Regjistri i burimeve me të drejta dhe licenca.",
            )

            PackNames.GTFS -> LocalizedText(
                "Το αρχείο GTFS της έκδοσης. Δεν χρησιμοποιείται από την εφαρμογή.",
                "The release's GTFS archive. The application itself does not read it.",
                "Arkivi GTFS i publikimit. Aplikacioni nuk e lexon.",
            )

            else -> LocalizedText.of(name)
        }
    }

    private class JobCancellable(private val job: Job) : Cancellable {
        private var cancelled = false

        override fun cancel() {
            if (cancelled) return
            cancelled = true
            job.cancel()
        }

        override val isCancelled: Boolean get() = cancelled || job.isCancelled
    }

    companion object {
        const val MAX_PLACE_RESULTS: Int = 50
        const val MAX_RECENT_SEARCHES: Int = 10
        const val MAX_CACHED_DAYS: Int = 3
        private const val SAVED_TRIP_ID_LENGTH = 24
    }
}
