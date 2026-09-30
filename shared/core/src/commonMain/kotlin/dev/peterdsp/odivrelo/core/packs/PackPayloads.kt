package dev.peterdsp.odivrelo.core.packs

import dev.peterdsp.odivrelo.core.model.Coverage
import dev.peterdsp.odivrelo.core.model.CoverageState
import dev.peterdsp.odivrelo.core.model.DataMode
import dev.peterdsp.odivrelo.core.model.Journey
import dev.peterdsp.odivrelo.core.model.JourneyDetailBody
import dev.peterdsp.odivrelo.core.model.OperatorBody
import dev.peterdsp.odivrelo.core.model.StopBody
import dev.peterdsp.odivrelo.core.model.UnavailableReason
import kotlinx.serialization.Serializable

/**
 * The logical pack names of a published release.
 *
 * A pack contains exactly what the matching `/v1/...` endpoint returns, so the
 * core decodes a bundled pack, a downloaded pack and an endpoint response with
 * the same decoder. There is no separate "offline shape": that is what stops a
 * client drifting into its own timetable engine.
 *
 * `meta`, `coverage`, `sources`, `places`, `operators` and `stops` are one file
 * each. Journeys are split one file per service date, named
 * `journeys-<YYYY-MM-DD>`, so a client downloads only the days it needs. `gtfs`
 * is a zip that the core never reads; it is listed so the offline screen can
 * offer it and account for its size.
 */
internal object PackNames {
    const val META = "meta"
    const val COVERAGE = "coverage"
    const val SOURCES = "sources"
    const val PLACES = "places"
    const val OPERATORS = "operators"
    const val STOPS = "stops"
    const val GTFS = "gtfs"

    const val JOURNEYS_PREFIX = "journeys-"

    /** Packs without which no screen can be answered honestly. */
    val required: List<String> = listOf(META, COVERAGE, SOURCES, PLACES, OPERATORS, STOPS)

    fun journeys(serviceDate: String): String = JOURNEYS_PREFIX + serviceDate

    /** The service date a journeys pack covers, or null for any other pack. */
    fun serviceDateOf(packName: String): String? =
        if (packName.startsWith(JOURNEYS_PREFIX)) {
            packName.removePrefix(JOURNEYS_PREFIX).takeIf { it.length == SERVICE_DATE_LENGTH }
        } else {
            null
        }

    fun isJourneys(packName: String): Boolean = serviceDateOf(packName) != null

    private const val SERVICE_DATE_LENGTH = 10
}

/**
 * The fields every pack repeats, read before a pack is trusted so a file from
 * another release can never be attached to this one.
 */
@Serializable
internal data class PackEnvelope(
    val contractVersion: String,
    val releaseId: String,
    val publishedAt: String? = null,
    val dataMode: DataMode = DataMode.DEMO,
)

/**
 * The `coverage` pack: the `/v1/coverage` response, whose body is nested under
 * `coverage` alongside the envelope.
 */
@Serializable
internal data class CoveragePack(
    val contractVersion: String,
    val releaseId: String,
    val publishedAt: String? = null,
    val dataMode: DataMode = DataMode.DEMO,
    val coverage: Coverage,
)

/**
 * The `operators` pack: every operator in the `/v1/operators/{id}` body shape,
 * keyed by operator id.
 */
@Serializable
internal data class OperatorsPack(
    val contractVersion: String,
    val releaseId: String,
    val publishedAt: String,
    val dataMode: DataMode,
    val operators: Map<String, OperatorBody> = emptyMap(),
)

/**
 * The `stops` pack: every stop in the `/v1/stops/{id}` body shape, keyed by stop
 * id. Per-date departures are not in here; they come from the journeys pack for
 * the date being shown, so a station page and a journey list can never disagree.
 */
@Serializable
internal data class StopsPack(
    val contractVersion: String,
    val releaseId: String,
    val publishedAt: String,
    val dataMode: DataMode,
    val stops: Map<String, StopBody> = emptyMap(),
)

/**
 * One service date's journeys: the `/v1/journeys` result list for the whole day,
 * plus the `/v1/journeys/{id}` detail body for each of them keyed by journey id.
 *
 * The day list is what a station page and a coverage answer are built from. The
 * details carry the ordered stop list with pickup and dropoff rules, which is
 * what lets the core answer an origin-to-destination query by taking the segment
 * between two stops of the same journey.
 */
@Serializable
internal data class JourneysPack(
    val contractVersion: String,
    val releaseId: String,
    val publishedAt: String,
    val dataMode: DataMode,
    val serviceDate: String,
    val coverage: CoverageState = CoverageState.DEMO,
    val results: List<Journey> = emptyList(),
    val journeys: Map<String, JourneyDetailBody> = emptyMap(),
    val unavailableReason: UnavailableReason? = null,
)
