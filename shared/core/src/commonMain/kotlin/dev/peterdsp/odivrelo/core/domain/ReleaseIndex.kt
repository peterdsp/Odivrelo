package dev.peterdsp.odivrelo.core.domain

import dev.peterdsp.odivrelo.core.model.BoardingPoint
import dev.peterdsp.odivrelo.core.model.BoardingRule
import dev.peterdsp.odivrelo.core.model.Coverage
import dev.peterdsp.odivrelo.core.model.CoverageState
import dev.peterdsp.odivrelo.core.model.DataMode
import dev.peterdsp.odivrelo.core.model.Journey
import dev.peterdsp.odivrelo.core.model.JourneyDetailBody
import dev.peterdsp.odivrelo.core.model.JourneyEndpoint
import dev.peterdsp.odivrelo.core.model.JourneyFilters
import dev.peterdsp.odivrelo.core.model.JourneySort
import dev.peterdsp.odivrelo.core.model.JourneyStop
import dev.peterdsp.odivrelo.core.model.LocalizedText
import dev.peterdsp.odivrelo.core.model.Meta
import dev.peterdsp.odivrelo.core.model.OperatorBody
import dev.peterdsp.odivrelo.core.model.OperatorSummary
import dev.peterdsp.odivrelo.core.model.Place
import dev.peterdsp.odivrelo.core.model.PlaceKind
import dev.peterdsp.odivrelo.core.model.PositionQuality
import dev.peterdsp.odivrelo.core.model.ReviewState
import dev.peterdsp.odivrelo.core.model.SegmentRole
import dev.peterdsp.odivrelo.core.model.SelectedSegment
import dev.peterdsp.odivrelo.core.model.Source
import dev.peterdsp.odivrelo.core.model.StopBody
import dev.peterdsp.odivrelo.core.model.UnavailableReason
import dev.peterdsp.odivrelo.core.packs.JourneysPack
import dev.peterdsp.odivrelo.core.time.ServiceTime
import kotlinx.datetime.Instant

/**
 * The read model of exactly one published release.
 *
 * It holds the release's static packs. A service date's journeys are passed in
 * per query, because a release covers many dates and loading all of them would
 * be both slow and pointless.
 *
 * The index never invents a journey or a calendar. A journey exists on a date
 * only if the publisher's `journeys-<date>` pack contains it.
 */
internal class ReleaseIndex(
    val releaseId: String,
    val publishedAt: String,
    val dataMode: DataMode,
    val meta: Meta,
    val coverage: Coverage,
    val places: List<Place>,
    val stops: Map<String, StopBody>,
    val operators: Map<String, OperatorBody>,
    val sources: List<Source>,
) {

    private val placeById: Map<String, Place> = places.associateBy { it.id }

    private val childrenOf: Map<String, List<String>> =
        places.filter { it.parentId != null }.groupBy({ it.parentId!! }, { it.id })

    private val searchKeys: Map<String, List<String>> = places.associate { place ->
        place.id to buildList {
            add(SearchText.fold(place.name.el.orEmpty()))
            add(SearchText.fold(place.name.en.orEmpty()))
            add(SearchText.fold(place.name.sq.orEmpty()))
            place.municipality?.let { add(SearchText.fold(it)) }
        }.filter { it.isNotEmpty() }
    }

    fun place(placeId: String): Place? = placeById[placeId]

    fun stop(stopId: String): StopBody? = stops[stopId]

    fun operator(operatorId: String): OperatorBody? = operators[operatorId]

    fun allSources(): List<Source> = sources.sortedBy { it.id }

    // -- Places --------------------------------------------------------------

    /**
     * Deterministic place search: exact name match first, then prefix, then
     * containment; inside each tier a terminal comes before its boarding points,
     * then folded Greek name, then id. The same query always gives the same list.
     */
    fun searchPlaces(query: String, limit: Int): List<Place> {
        val folded = SearchText.fold(query)
        val safeLimit = limit.coerceAtLeast(0)
        if (folded.isEmpty()) {
            return places
                .filter { it.kind == PlaceKind.STOP_PLACE }
                .sortedWith(PLACE_ORDER)
                .take(safeLimit)
        }
        return places
            .mapNotNull { place ->
                val tier = searchKeys[place.id].orEmpty().minOfOrNull { key ->
                    when {
                        key == folded -> 0
                        key.startsWith(folded) -> 1
                        key.contains(folded) -> 2
                        else -> Int.MAX_VALUE
                    }
                } ?: Int.MAX_VALUE
                if (tier == Int.MAX_VALUE) null else place to tier
            }
            .sortedWith(
                compareBy<Pair<Place, Int>> { it.second }.then(compareBy(PLACE_ORDER) { it.first }),
            )
            .map { it.first }
            .take(safeLimit)
    }

    /**
     * Expands a place id to the stop ids a traveller could board at. A terminal
     * expands to itself plus its boarding points, which is the disambiguation
     * contract 1 asks a client to present.
     */
    fun expandToStopIds(placeId: String): Set<String> {
        val place = placeById[placeId] ?: return setOf(placeId)
        return when (place.kind) {
            PlaceKind.STOP_PLACE -> buildSet {
                add(place.id)
                addAll(childrenOf[place.id].orEmpty())
            }

            PlaceKind.STOP -> setOf(place.id)
        }
    }

    fun boardingPointsOf(placeId: String): List<Place> =
        childrenOf[placeId].orEmpty().mapNotNull { placeById[it] }

    fun coverageFor(originId: String, destinationId: String): CoverageState {
        if (dataMode == DataMode.DEMO) return CoverageState.DEMO
        val origin = placeById[originId]
        val destination = placeById[destinationId]
        return when {
            origin == null || destination == null -> CoverageState.NOT_COVERED
            origin.coverage == CoverageState.COVERED &&
                destination.coverage == CoverageState.COVERED -> CoverageState.COVERED

            origin.coverage == CoverageState.NOT_COVERED ||
                destination.coverage == CoverageState.NOT_COVERED -> CoverageState.NOT_COVERED

            else -> CoverageState.PARTIAL
        }
    }

    // -- Journeys ------------------------------------------------------------

    /**
     * Answers an origin-to-destination query from one service date's journeys.
     *
     * A journey in the day pack runs from its own first stop to its own last
     * stop. A traveller asking Kithra to Vello wants the segment between two of
     * those stops, so the segment is taken from the detail's ordered stop list,
     * honouring pickup and dropoff rules. Times come from the published
     * timestamps, so no clock arithmetic is redone here; duration and the
     * midnight crossing are derived through [ServiceTime] from those instants.
     */
    fun journeysBetween(
        day: JourneysPack,
        originId: String,
        destinationId: String,
        filters: JourneyFilters,
        now: Instant,
    ): JourneySearchOutcome {
        if (originId == destinationId) {
            return JourneySearchOutcome(emptyList(), UnavailableReason.ORIGIN_EQUALS_DESTINATION)
        }
        if (placeById[originId] == null || placeById[destinationId] == null) {
            return JourneySearchOutcome(emptyList(), UnavailableReason.OUTSIDE_COVERAGE)
        }

        val originStops = expandToStopIds(originId)
        val destinationStops = expandToStopIds(destinationId)

        val segments = day.results.mapNotNull { summary ->
            val detail = day.journeys[summary.id] ?: return@mapNotNull null
            segmentOf(summary, detail, originStops, destinationStops, day.serviceDate, now)
        }

        if (segments.isEmpty()) {
            val servesBoth = day.journeys.values.any { detail ->
                detail.stops.any { it.stopId in originStops } &&
                    detail.stops.any { it.stopId in destinationStops }
            }
            val reason = when {
                day.unavailableReason != null -> day.unavailableReason
                servesBoth -> UnavailableReason.NO_SERVICE_ON_DATE
                else -> UnavailableReason.OUTSIDE_COVERAGE
            }
            return JourneySearchOutcome(emptyList(), reason)
        }

        val filtered = segments.filter { matches(it, filters) }
        return JourneySearchOutcome(
            results = filtered.sortedWith(comparatorFor(filters.sort)),
            unavailableReason = if (filtered.isEmpty()) {
                UnavailableReason.NO_SERVICE_ON_DATE
            } else {
                null
            },
        )
    }

    /**
     * The journey a detail screen shows, headlined by the leg the id names.
     *
     * The whole run is kept, with each stop marked board, alight or served
     * outside the leg, but the headline departure, arrival and duration are the
     * traveller's own leg, so a detail agrees with the search result that opened
     * it. A bare id, or a leg that runs the whole way, returns the published body
     * unchanged apart from freshness and the stop roles. Freshness is recomputed
     * against the current clock so an old cached copy cannot look new.
     */
    fun journeyDetail(day: JourneysPack, journeyId: String, now: Instant): JourneyDetailBody? {
        val tripId = tripIdOf(journeyId)
        val detail = day.journeys[tripId] ?: day.journeys[journeyId] ?: return null
        val checkedAt = detail.freshness.checkedAt
        val fresh = detail.copy(freshness = ServiceTime.freshness(checkedAt, now.toString()))

        val ordered = fresh.stops.sortedBy { it.sequence }
        if (ordered.isEmpty()) return fresh

        val (boardIndex, alightIndex) = segmentIndices(ordered, journeyId)
        val board = ordered[boardIndex]
        val alight = ordered[alightIndex]
        val marked = ordered.mapIndexed { index, stop ->
            stop.copy(segmentRole = segmentRoleOf(index, boardIndex, alightIndex))
        }
        val selectedSegment = SelectedSegment(board.stopId, alight.stopId)

        if (boardIndex == 0 && alightIndex == ordered.size - 1) {
            return fresh.copy(stops = marked, selectedSegment = selectedSegment)
        }

        val departureAt = board.departureAt ?: board.arrivalAt ?: fresh.departure.at
        val arrivalAt = alight.arrivalAt ?: alight.departureAt ?: fresh.arrival.at
        val departureInstant = ServiceTime.parseInstantOrNull(departureAt)
        val arrivalInstant = ServiceTime.parseInstantOrNull(arrivalAt)
        val duration = if (departureInstant != null && arrivalInstant != null) {
            ServiceTime.durationMinutes(departureInstant, arrivalInstant)
        } else {
            fresh.durationMinutes
        }
        val crosses = if (departureInstant != null && arrivalInstant != null) {
            ServiceTime.crossesMidnight(departureInstant, arrivalInstant)
        } else {
            fresh.crossesMidnight
        }
        return fresh.copy(
            id = composeJourneyId(tripId, board.stopId, alight.stopId),
            departure = JourneyEndpoint(
                at = departureAt,
                stopId = board.stopId,
                stopName = nameOf(board),
                quality = board.timeQuality,
            ),
            arrival = JourneyEndpoint(
                at = arrivalAt,
                stopId = alight.stopId,
                stopName = nameOf(alight),
                quality = alight.timeQuality,
            ),
            durationMinutes = duration,
            intermediateStopCount = marked.count { it.sequence > board.sequence && it.sequence < alight.sequence },
            crossesMidnight = crosses,
            boardingPoint = boardingPointFor(board.stopId, fresh),
            selectedSegment = selectedSegment,
            stops = marked,
        )
    }

    /** Resolve the board and alight positions named by a journey id, whole as a fallback. */
    private fun segmentIndices(ordered: List<JourneyStop>, journeyId: String): Pair<Int, Int> {
        val whole = 0 to ordered.size - 1
        val (_, boardStopId, alightStopId) = parseJourneyIdParts(journeyId) ?: return whole
        if (boardStopId == null || alightStopId == null) return whole
        val boardIndex = ordered.indexOfFirst { it.stopId == boardStopId }
        if (boardIndex < 0) return whole
        val alightIndex = ordered.indexOfLast { it.stopId == alightStopId }
        if (alightIndex <= boardIndex) return whole
        return boardIndex to alightIndex
    }

    /** The boarding point for the leg: the body's own when it matches, else the stop's. */
    private fun boardingPointFor(stopId: String, detail: JourneyDetailBody): BoardingPoint? {
        detail.boardingPoint?.takeIf { it.stopId == stopId }?.let { return it }
        val stop = stops[stopId] ?: return detail.boardingPoint
        val point = stop.boardingPoints.firstOrNull()
        return BoardingPoint(
            stopId = stopId,
            name = stop.name,
            terminalName = stop.parentName,
            bay = point?.bay ?: stop.bay,
            latitude = stop.latitude,
            longitude = stop.longitude,
            instructions = point?.instructions ?: stop.instructions,
            reviewState = point?.reviewState ?: ReviewState.CANDIDATE,
            reviewedAt = point?.reviewedAt,
            stepFree = point?.stepFree,
        )
    }

    /**
     * The journeys that board at one stop on this service date, each as the
     * segment from that stop to the end of its own run.
     *
     * This goes through the same segment code as a search, so a station page and
     * a search result cannot disagree about a departure time, a duration or a
     * midnight crossing.
     */
    fun departuresFrom(day: JourneysPack, stopId: String, now: Instant): List<Journey> =
        day.results.mapNotNull { summary ->
            val detail = day.journeys[summary.id] ?: return@mapNotNull null
            val ordered = detail.stops.sortedBy { it.sequence }
            val boarding = ordered.firstOrNull {
                it.stopId == stopId && it.pickup != BoardingRule.NOT_ALLOWED
            } ?: return@mapNotNull null
            val terminus = ordered.lastOrNull {
                it.sequence > boarding.sequence && it.dropoff != BoardingRule.NOT_ALLOWED
            } ?: return@mapNotNull null
            segmentOf(summary, detail, setOf(boarding.stopId), setOf(terminus.stopId), day.serviceDate, now)
        }.sortedWith(compareBy({ it.departure.at }, { it.id }))

    private fun segmentOf(
        summary: Journey,
        detail: JourneyDetailBody,
        originStops: Set<String>,
        destinationStops: Set<String>,
        serviceDate: String,
        now: Instant,
    ): Journey? {
        val ordered = detail.stops.sortedBy { it.sequence }
        val boarding = ordered.firstOrNull {
            it.stopId in originStops && it.pickup != BoardingRule.NOT_ALLOWED
        } ?: return null
        val alighting = ordered.firstOrNull {
            it.stopId in destinationStops &&
                it.dropoff != BoardingRule.NOT_ALLOWED &&
                it.sequence > boarding.sequence
        } ?: return null

        val departureAt = boarding.departureAt ?: boarding.arrivalAt ?: return null
        val arrivalAt = alighting.arrivalAt ?: alighting.departureAt ?: return null
        val departureInstant = ServiceTime.parseInstantOrNull(departureAt) ?: return null
        val arrivalInstant = ServiceTime.parseInstantOrNull(arrivalAt) ?: return null

        val wholeJourney = boarding.sequence == ordered.first().sequence &&
            alighting.sequence == ordered.last().sequence

        return Journey(
            id = composeJourneyId(tripIdOf(summary.id), boarding.stopId, alighting.stopId),
            operator = summary.operator,
            departure = JourneyEndpoint(
                at = departureAt,
                stopId = boarding.stopId,
                stopName = nameOf(boarding),
                quality = boarding.timeQuality,
            ),
            arrival = JourneyEndpoint(
                at = arrivalAt,
                stopId = alighting.stopId,
                stopName = nameOf(alighting),
                quality = alighting.timeQuality,
            ),
            durationMinutes = ServiceTime.durationMinutes(departureInstant, arrivalInstant),
            intermediateStopCount = ordered.count {
                it.sequence > boarding.sequence && it.sequence < alighting.sequence
            },
            // The service date of the day pack, never recomputed from arrival.
            serviceDate = serviceDate,
            crossesMidnight = ServiceTime.crossesMidnight(departureInstant, arrivalInstant),
            // Release 1.0.0 publishes schedules only. Nothing here is observed.
            positionQuality = PositionQuality.SCHEDULED,
            fare = if (wholeJourney) summary.fare else summary.fare?.copy(isIndicative = true),
            freshness = ServiceTime.freshness(summary.freshness.checkedAt, now.toString()),
            confidence = summary.confidence,
            purchase = summary.purchase,
            accessibleBoardingPoint = accessibilityOf(detail, boarding.stopId),
        )
    }

    /**
     * Whether the boarding point for this segment is reviewed step free. Null
     * means nobody has reviewed it, which is a different answer from "no" and is
     * kept different all the way to the screen.
     */
    private fun accessibilityOf(detail: JourneyDetailBody, boardingStopId: String): Boolean? {
        detail.boardingPoint?.takeIf { it.stopId == boardingStopId }?.let { return it.stepFree }
        val stop = stops[boardingStopId] ?: return null
        val reviewed = stop.boardingPoints.firstOrNull { it.reviewState.isShowable }
        return (reviewed ?: stop.boardingPoints.firstOrNull())?.stepFree
    }

    private fun nameOf(stop: JourneyStop): LocalizedText =
        stops[stop.stopId]?.name ?: placeById[stop.stopId]?.name ?: stop.name

    private fun nameOfStop(stopId: String): LocalizedText =
        stops[stopId]?.name ?: placeById[stopId]?.name ?: LocalizedText.of(stopId)

    private fun matches(journey: Journey, filters: JourneyFilters): Boolean {
        if (filters.accessibleOnly && journey.accessibleBoardingPoint != true) return false
        if (!filters.includeCrossesMidnight && journey.crossesMidnight) return false
        if (filters.operatorIds.isNotEmpty() && journey.operator.id !in filters.operatorIds) {
            return false
        }
        filters.maxDurationMinutes?.let { if (journey.durationMinutes > it) return false }

        val departure = ServiceTime.parseInstantOrNull(journey.departure.at) ?: return true
        val local = ServiceTime.localTimeOf(departure)
        val minutes = local.hour * 60 + local.minute
        filters.departAfter?.let { bound ->
            val boundMinutes = parseClock(bound)
            if (boundMinutes != null && minutes < boundMinutes) return false
        }
        filters.departBefore?.let { bound ->
            val boundMinutes = parseClock(bound)
            if (boundMinutes != null && minutes > boundMinutes) return false
        }
        return true
    }

    private fun parseClock(value: String): Int? {
        val parts = value.split(':')
        if (parts.size != 2) return null
        val hour = parts[0].toIntOrNull() ?: return null
        val minute = parts[1].toIntOrNull() ?: return null
        if (hour !in 0..23 || minute !in 0..59) return null
        return hour * 60 + minute
    }

    /**
     * Applies the same filtering and ordering to a result list that did not come
     * from a pack, so a live answer cannot be sorted or filtered differently from
     * a pack-derived one.
     */
    fun applyFiltersAndOrder(
        results: List<Journey>,
        filters: JourneyFilters,
        now: Instant,
    ): List<Journey> = results
        .map { it.copy(freshness = ServiceTime.freshness(it.freshness.checkedAt, now.toString())) }
        .filter { matches(it, filters) }
        .sortedWith(comparatorFor(filters.sort))

    /** One ordering for the whole product, broken down to the id so it is total. */
    private fun comparatorFor(sort: JourneySort): Comparator<Journey> {
        val base = when (sort) {
            JourneySort.DEPARTURE -> compareBy<Journey> { it.departure.at }
            JourneySort.DURATION -> compareBy<Journey> { it.durationMinutes }
            JourneySort.ARRIVAL -> compareBy<Journey> { it.arrival.at }
        }
        return base
            .thenBy { it.departure.at }
            .thenBy { it.durationMinutes }
            .thenBy { it.operator.id }
            .thenBy { it.id }
    }

    private companion object {
        val PLACE_ORDER: Comparator<Place> =
            compareBy<Place> { if (it.kind == PlaceKind.STOP_PLACE) 0 else 1 }
                .thenBy { SearchText.fold(it.name.el ?: it.name.en.orEmpty()) }
                .thenBy { it.id }
    }
}

internal data class JourneySearchOutcome(
    val results: List<Journey>,
    val unavailableReason: UnavailableReason?,
)

/**
 * Accent- and case-insensitive folding for Greek, Latin and Albanian text.
 *
 * Greek search has to work whether or not the traveller typed the accents, and
 * final sigma has to match medial sigma. It is done explicitly rather than with
 * a locale-dependent platform call so Android and iOS agree exactly.
 */
internal object SearchText {

    private val greekFolding: Map<Char, Char> = mapOf(
        'ά' to 'α', 'έ' to 'ε', 'ή' to 'η', 'ί' to 'ι', 'ό' to 'ο', 'ύ' to 'υ', 'ώ' to 'ω',
        'ΐ' to 'ι', 'ΰ' to 'υ', 'ϊ' to 'ι', 'ϋ' to 'υ', 'ς' to 'σ',
    )

    private val latinFolding: Map<Char, Char> = mapOf(
        'á' to 'a', 'à' to 'a', 'â' to 'a', 'ä' to 'a', 'ã' to 'a', 'å' to 'a',
        'é' to 'e', 'è' to 'e', 'ê' to 'e', 'ë' to 'e',
        'í' to 'i', 'ì' to 'i', 'î' to 'i', 'ï' to 'i',
        'ó' to 'o', 'ò' to 'o', 'ô' to 'o', 'ö' to 'o', 'õ' to 'o',
        'ú' to 'u', 'ù' to 'u', 'û' to 'u', 'ü' to 'u',
        'ç' to 'c', 'ñ' to 'n', 'ý' to 'y', 'ë' to 'e',
    )

    fun fold(value: String): String {
        if (value.isEmpty()) return ""
        val builder = StringBuilder(value.length)
        for (raw in value) {
            val lower = raw.lowercaseChar()
            val folded = greekFolding[lower] ?: latinFolding[lower] ?: lower
            if (folded.isLetterOrDigit()) {
                builder.append(folded)
            } else if (folded.isWhitespace() && builder.isNotEmpty() && builder.last() != ' ') {
                builder.append(' ')
            }
        }
        return builder.toString().trim()
    }
}

private const val JOURNEY_ID_SEPARATOR = "~"

/** A journey id that carries the boarded leg: trip, boarding stop, alighting stop. */
internal fun composeJourneyId(tripId: String, boardStopId: String, alightStopId: String): String =
    "$tripId$JOURNEY_ID_SEPARATOR$boardStopId$JOURNEY_ID_SEPARATOR$alightStopId"

/** The trip part of a journey id, whether or not it carries a leg. */
internal fun tripIdOf(journeyId: String): String = journeyId.substringBefore(JOURNEY_ID_SEPARATOR)

/**
 * Split a journey id into its trip and, when present, its leg. A bare id is the
 * whole run. Any other shape returns null so the caller falls back rather than
 * reading a malformed id as a different journey.
 */
internal fun parseJourneyIdParts(journeyId: String): Triple<String, String?, String?>? {
    val parts = journeyId.split(JOURNEY_ID_SEPARATOR)
    return when {
        parts.size == 1 && parts[0].isNotEmpty() -> Triple(parts[0], null, null)
        parts.size == 3 && parts.all { it.isNotEmpty() } -> Triple(parts[0], parts[1], parts[2])
        else -> null
    }
}

internal fun segmentRoleOf(index: Int, boardIndex: Int, alightIndex: Int): SegmentRole = when {
    index == boardIndex -> SegmentRole.BOARD
    index == alightIndex -> SegmentRole.ALIGHT
    index in (boardIndex + 1) until alightIndex -> SegmentRole.ON_SEGMENT
    index < boardIndex -> SegmentRole.BEFORE_BOARD
    else -> SegmentRole.AFTER_ALIGHT
}
