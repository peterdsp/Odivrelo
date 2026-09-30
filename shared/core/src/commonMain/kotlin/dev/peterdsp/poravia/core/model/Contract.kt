package dev.peterdsp.poravia.core.model

import dev.peterdsp.poravia.core.Brand
import kotlin.native.ObjCName
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Localised text as carried by contract version 1. Greek is the product's
 * default language; a missing translation falls back to Greek and then to
 * English rather than showing an empty label.
 */
@Serializable
@ObjCName("PoraviaLocalizedText")
data class LocalizedText(
    val el: String? = null,
    val en: String? = null,
    val sq: String? = null,
) {
    fun resolve(languageTag: String): String {
        val exact = when (languageTag.take(2).lowercase()) {
            "el" -> el
            "en" -> en
            "sq" -> sq
            else -> null
        }
        return exact ?: el ?: en ?: sq ?: ""
    }

    companion object {
        fun of(value: String): LocalizedText = LocalizedText(el = value, en = value, sq = value)
    }
}

@Serializable
@ObjCName("PoraviaProductInfo")
data class ProductInfo(
    val name: String = Brand.NAME,
    val version: String = Brand.VERSION,
    val commit: String = "unknown",
    val builtAt: String? = null,
)

@Serializable
@ObjCName("PoraviaAttribution")
data class Attribution(
    val name: String,
    val url: String? = null,
    val licence: String? = null,
)

@Serializable
@ObjCName("PoraviaOperatorCoverageSummary")
data class OperatorCoverageSummary(
    val operatorId: String? = null,
    val name: LocalizedText? = null,
    val journeyCount: Int = 0,
    val routeCount: Int = 0,
    val stopCount: Int = 0,
    val state: CoverageState = CoverageState.NOT_COVERED,
)

/**
 * The span of service dates a release describes.
 *
 * This is a span, not a guarantee. A release publishes a journeys pack only for
 * the dates its data actually names, so a date inside this range may still have
 * no pack. The range says what the release is about; only the manifest says what
 * it holds.
 */
@Serializable
@ObjCName("PoraviaServiceDateRange")
data class ServiceDateRange(
    val from: String? = null,
    val to: String? = null,
) {
    fun contains(serviceDate: String): Boolean {
        val start = from ?: return false
        val end = to ?: return false
        return serviceDate in start..end
    }
}

/**
 * What a release covers and, just as importantly, what it does not.
 *
 * [note] and [notCovered] are localised, not plain strings: they are sentences a
 * traveller reads, and this product ships in three languages. A coverage
 * statement that only exists in English is not a coverage statement for most of
 * the people it is about.
 *
 * [absenceSemantics] is the publisher's own statement of what the absence of a
 * journey means, keyed by the machine term it explains. A client must never turn
 * "we have no record" into "there is no service", so the wording comes from the
 * release rather than from the application.
 */
@Serializable
@ObjCName("PoraviaCoverage")
data class Coverage(
    val state: CoverageState,
    val operatorCount: Int = 0,
    val corridorCount: Int = 0,
    val journeyCount: Int = 0,
    val stopCount: Int = 0,
    val note: LocalizedText? = null,
    /** Explicit statement of what is not covered. Never inferred by a client. */
    val notCovered: List<LocalizedText> = emptyList(),
    val absenceSemantics: Map<String, String> = emptyMap(),
    val operators: List<OperatorCoverageSummary> = emptyList(),
    /**
     * The span of dates this release describes. Not every date inside it has a
     * published timetable, which is why the core answers "no offline data for
     * this date" rather than "no service" when a pack is absent.
     */
    val serviceDates: ServiceDateRange? = null,
    val freshness: Freshness? = null,
    val releaseId: String = "",
    val publishedAt: String? = null,
    val dataMode: DataMode = DataMode.DEMO,
)

@Serializable
@ObjCName("PoraviaMeta")
data class Meta(
    val contractVersion: String,
    val releaseId: String,
    val publishedAt: String,
    val dataMode: DataMode,
    val product: ProductInfo = ProductInfo(),
    val languages: List<String> = Brand.LANGUAGES,
    val coverage: Coverage,
    val offlineManifestUrl: String? = null,
    val gtfsUrl: String? = null,
    val attribution: List<Attribution> = emptyList(),
) {
    val isDemo: Boolean get() = dataMode.requiresDemoNotice
}

@Serializable
@ObjCName("PoraviaPlace")
data class Place(
    val id: String,
    val kind: PlaceKind,
    val name: LocalizedText,
    val parentId: String? = null,
    val municipality: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val coordinateStatus: String = "unknown",
    val boardingPointCount: Int = 0,
    val operatorIds: List<String> = emptyList(),
    val coverage: CoverageState = CoverageState.NOT_COVERED,
) {
    /**
     * True when this place is a terminal that contains boarding points, which
     * is the disambiguation a traveller must be able to see before choosing.
     */
    val isTerminal: Boolean get() = kind == PlaceKind.STOP_PLACE
}

@Serializable
@ObjCName("PoraviaPlaceResults")
data class PlaceResults(
    val contractVersion: String,
    val releaseId: String,
    val dataMode: DataMode,
    val places: List<Place>,
    val total: Int,
    val query: String = "",
)

@Serializable
@ObjCName("PoraviaOperatorSummary")
data class OperatorSummary(
    val id: String,
    val name: LocalizedText,
    val logoAvailable: Boolean = false,
)

@Serializable
@ObjCName("PoraviaJourneyEndpoint")
data class JourneyEndpoint(
    val at: String,
    val stopId: String,
    val stopName: LocalizedText,
    val quality: TimeQuality,
)

@Serializable
@ObjCName("PoraviaFare")
data class Fare(
    val amount: Double,
    val currency: String,
    val isIndicative: Boolean = true,
)

@Serializable
@ObjCName("PoraviaFreshness")
data class Freshness(
    val checkedAt: String,
    val ageHours: Int,
    val state: FreshnessState,
)

@Serializable
@ObjCName("PoraviaPurchaseOption")
data class PurchaseOption(
    val kind: PurchaseKind,
    val url: String? = null,
    val phone: String? = null,
    val address: String? = null,
    val openingHours: String? = null,
    val label: LocalizedText? = null,
    val disclaimer: LocalizedText? = null,
) {
    /**
     * Poravia never sells or issues a ticket. An online option is a handoff to
     * the operator's own site and nothing else.
     */
    val isExternalHandoff: Boolean get() = kind == PurchaseKind.ONLINE && !url.isNullOrBlank()

    val hasOfflineFallback: Boolean
        get() = !phone.isNullOrBlank() || !address.isNullOrBlank()
}

@Serializable
@ObjCName("PoraviaJourney")
data class Journey(
    val id: String,
    val operator: OperatorSummary,
    val departure: JourneyEndpoint,
    val arrival: JourneyEndpoint,
    val durationMinutes: Int,
    val intermediateStopCount: Int,
    val serviceDate: String,
    val crossesMidnight: Boolean,
    val positionQuality: PositionQuality = PositionQuality.SCHEDULED,
    val fare: Fare? = null,
    val freshness: Freshness,
    val confidence: Confidence,
    val purchase: PurchaseOption,
    val accessibleBoardingPoint: Boolean? = null,
)

@Serializable
@ObjCName("PoraviaJourneyQuery")
data class JourneyQuery(
    val originId: String,
    val destinationId: String,
    val date: String,
)

/**
 * Where a service date's journeys came from.
 *
 * A release only materialises a journeys pack for the dates its data actually
 * names: date-specific service dates, calendar template dates, and dates an
 * added exception names. A weekday-recurring service on some other date is
 * resolved by the API on request and is deliberately not in any pack. So
 * [NO_OFFLINE_PACK] means "this device holds no timetable for that date", which
 * is a completely different statement from "no service runs that date".
 * Conflating the two would be exactly the invented certainty this product exists
 * to avoid.
 */
@Serializable
@ObjCName("PoraviaDateDataState")
enum class DateDataState {
    @SerialName("from_installed_pack")
    FROM_INSTALLED_PACK,

    @SerialName("from_api")
    FROM_API,

    @SerialName("no_offline_pack")
    NO_OFFLINE_PACK,
    ;

    /** True when the result is an answer about service, not about coverage. */
    val isAnswer: Boolean get() = this != NO_OFFLINE_PACK
}

@Serializable
@ObjCName("PoraviaJourneyResults")
data class JourneyResults(
    val contractVersion: String,
    val releaseId: String,
    val dataMode: DataMode,
    val query: JourneyQuery,
    val coverage: CoverageState,
    val results: List<Journey>,
    val unavailableReason: UnavailableReason? = null,
    /** Set when results come from an installed pack rather than the network. */
    val servedFromCache: Boolean = false,
    val cachedReleasePublishedAt: String? = null,
    val dateDataState: DateDataState = DateDataState.FROM_INSTALLED_PACK,
)

@Serializable
@ObjCName("PoraviaBoardingPoint")
data class BoardingPoint(
    val stopId: String,
    val name: LocalizedText,
    val terminalName: LocalizedText? = null,
    val bay: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val instructions: LocalizedText? = null,
    val reviewState: ReviewState = ReviewState.CANDIDATE,
    val reviewedAt: String? = null,
    val stepFree: Boolean? = null,
)

@Serializable
@ObjCName("PoraviaJourneyStop")
data class JourneyStop(
    val stopId: String,
    val sequence: Int,
    val name: LocalizedText,
    val arrivalAt: String? = null,
    val departureAt: String? = null,
    val timeQuality: TimeQuality = TimeQuality.SCHEDULED,
    val pickup: BoardingRule = BoardingRule.ALLOWED,
    val dropoff: BoardingRule = BoardingRule.ALLOWED,
)

@Serializable
@ObjCName("PoraviaGeometry")
data class Geometry(
    val type: String = "LineString",
    val coordinates: List<List<Double>> = emptyList(),
    val confidence: GeometryConfidence = GeometryConfidence.UNVERIFIED,
    val method: String? = null,
    val attribution: String? = null,
)

@Serializable
@ObjCName("PoraviaRestriction")
data class Restriction(
    val code: String,
    val text: LocalizedText,
)

@Serializable
@ObjCName("PoraviaProvenance")
data class Provenance(
    val sourceId: String,
    val sourceName: String,
    val sourceUrl: String? = null,
    val retrievedAt: String? = null,
    val rightsStatus: RightsStatus = RightsStatus.UNKNOWN,
    val licence: String? = null,
)

@Serializable
@ObjCName("PoraviaJourneyDetailBody")
data class JourneyDetailBody(
    val id: String,
    val operator: OperatorSummary,
    val departure: JourneyEndpoint,
    val arrival: JourneyEndpoint,
    val durationMinutes: Int,
    val intermediateStopCount: Int,
    val serviceDate: String,
    val crossesMidnight: Boolean,
    val positionQuality: PositionQuality = PositionQuality.SCHEDULED,
    val fare: Fare? = null,
    val freshness: Freshness,
    val confidence: Confidence,
    val boardingPoint: BoardingPoint? = null,
    val stops: List<JourneyStop> = emptyList(),
    val geometry: Geometry? = null,
    val restrictions: List<Restriction> = emptyList(),
    val provenance: List<Provenance> = emptyList(),
    val purchase: PurchaseOption,
    val correctionUrl: String? = null,
)

@Serializable
@ObjCName("PoraviaJourneyDetail")
data class JourneyDetail(
    val contractVersion: String,
    val releaseId: String,
    val dataMode: DataMode,
    val journey: JourneyDetailBody,
    val servedFromCache: Boolean = false,
) {
    /** Convenience summary used by result lists and saved trips. */
    val summary: Journey
        get() = Journey(
            id = journey.id,
            operator = journey.operator,
            departure = journey.departure,
            arrival = journey.arrival,
            durationMinutes = journey.durationMinutes,
            intermediateStopCount = journey.intermediateStopCount,
            serviceDate = journey.serviceDate,
            crossesMidnight = journey.crossesMidnight,
            positionQuality = journey.positionQuality,
            fare = journey.fare,
            freshness = journey.freshness,
            confidence = journey.confidence,
            purchase = journey.purchase,
            accessibleBoardingPoint = journey.boardingPoint?.stepFree,
        )
}

@Serializable
@ObjCName("PoraviaOperatorContact")
data class OperatorContact(
    val phone: String? = null,
    val email: String? = null,
    val address: String? = null,
)

@Serializable
@ObjCName("PoraviaOperatorCoverage")
data class OperatorCoverage(
    val state: CoverageState,
    val routeCount: Int = 0,
    val stopCount: Int = 0,
)

@Serializable
@ObjCName("PoraviaOperatorBody")
data class OperatorBody(
    val id: String,
    val name: LocalizedText,
    val federationNumber: Int? = null,
    val officialSiteUrl: String? = null,
    val directoryUrl: String? = null,
    val contact: OperatorContact? = null,
    val coverage: OperatorCoverage,
    val sources: List<Provenance> = emptyList(),
    val verifiedAt: String? = null,
    val correctionUrl: String? = null,
)

@Serializable
@ObjCName("PoraviaOperatorDetail")
data class OperatorDetail(
    val contractVersion: String,
    val releaseId: String,
    val dataMode: DataMode,
    val operator: OperatorBody,
    val servedFromCache: Boolean = false,
)

@Serializable
@ObjCName("PoraviaStopBody")
data class StopBody(
    val id: String,
    val name: LocalizedText,
    val kind: PlaceKind,
    val parentId: String? = null,
    val parentName: LocalizedText? = null,
    val municipality: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val coordinateStatus: String = "unknown",
    /** The bay label at the parent terminal, when this stop is one. */
    val bay: String? = null,
    val address: String? = null,
    val instructions: LocalizedText? = null,
    val boardingPoints: List<BoardingPoint> = emptyList(),
    val operatorIds: List<String> = emptyList(),
    val operators: List<OperatorSummary> = emptyList(),
    /**
     * The journeys that board here on the requested service date, each as the
     * segment from this stop onwards. Contract 1 carries these in the same shape
     * as a journey result, so a station page and a search result can never
     * disagree about a departure.
     */
    val departures: List<Journey> = emptyList(),
    val provenance: List<Provenance> = emptyList(),
    val correctionUrl: String? = null,
    val coverage: CoverageState = CoverageState.NOT_COVERED,
)

@Serializable
@ObjCName("PoraviaStopDetail")
data class StopDetail(
    val contractVersion: String,
    val releaseId: String,
    val dataMode: DataMode,
    val stop: StopBody,
    val serviceDate: String,
    val servedFromCache: Boolean = false,
    val dateDataState: DateDataState = DateDataState.FROM_INSTALLED_PACK,
)

@Serializable
@ObjCName("PoraviaSource")
data class Source(
    val id: String,
    val name: String,
    val url: String? = null,
    val rightsStatus: RightsStatus,
    val licence: String? = null,
    val retrievedAt: String? = null,
    val note: String? = null,
)

@Serializable
@ObjCName("PoraviaSourceList")
data class SourceList(
    val contractVersion: String,
    val releaseId: String,
    val dataMode: DataMode,
    val sources: List<Source> = emptyList(),
)

/** Search filters applied by the core, never re-implemented by a client. */
@Serializable
@ObjCName("PoraviaJourneyFilters")
data class JourneyFilters(
    /** Only journeys with a reviewed step-free boarding point. */
    val accessibleOnly: Boolean = false,
    /** Inclusive lower bound on local departure time, `HH:mm`, or null. */
    val departAfter: String? = null,
    /** Inclusive upper bound on local departure time, `HH:mm`, or null. */
    val departBefore: String? = null,
    val operatorIds: List<String> = emptyList(),
    val maxDurationMinutes: Int? = null,
    val includeCrossesMidnight: Boolean = true,
    val sort: JourneySort = JourneySort.DEPARTURE,
) {
    companion object {
        val NONE: JourneyFilters = JourneyFilters()
    }

    val isActive: Boolean
        get() = accessibleOnly || departAfter != null || departBefore != null ||
            operatorIds.isNotEmpty() || maxDurationMinutes != null ||
            !includeCrossesMidnight || sort != JourneySort.DEPARTURE

    val activeCount: Int
        get() = listOf(
            accessibleOnly,
            departAfter != null,
            departBefore != null,
            operatorIds.isNotEmpty(),
            maxDurationMinutes != null,
            !includeCrossesMidnight,
        ).count { it }
}

@Serializable
@ObjCName("PoraviaJourneySort")
enum class JourneySort {
    @SerialName("departure")
    DEPARTURE,

    @SerialName("duration")
    DURATION,

    @SerialName("arrival")
    ARRIVAL,
}

@Serializable
@ObjCName("PoraviaSavedTrip")
data class SavedTrip(
    val id: String,
    val journeyId: String,
    val serviceDate: String,
    val savedAt: String,
    val originName: LocalizedText,
    val destinationName: LocalizedText,
    val operatorName: LocalizedText,
    val departureAt: String,
    val arrivalAt: String,
    val crossesMidnight: Boolean,
    val boardingBay: String? = null,
    val boardingStepFree: Boolean? = null,
    val releaseId: String,
    val cachedAt: String,
)

@Serializable
@ObjCName("PoraviaFavoritePlace")
data class FavoritePlace(
    val placeId: String,
    val name: LocalizedText,
    val kind: PlaceKind,
    val municipality: String? = null,
    val addedAt: String,
)

@Serializable
@ObjCName("PoraviaRecentSearch")
data class RecentSearch(
    val originId: String,
    val originName: LocalizedText,
    val destinationId: String,
    val destinationName: LocalizedText,
    val serviceDate: String,
    val searchedAt: String,
)

@Serializable
@ObjCName("PoraviaError")
data class ContractError(
    val code: ErrorCode,
    val message: String,
    val field: String? = null,
)

@Serializable
@ObjCName("PoraviaErrorEnvelope")
internal data class ContractErrorEnvelope(
    @SerialName("error") val error: ContractError,
)
