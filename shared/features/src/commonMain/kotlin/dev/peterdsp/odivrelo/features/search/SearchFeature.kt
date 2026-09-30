package dev.peterdsp.odivrelo.features.search

import dev.peterdsp.odivrelo.core.OdivreloCore
import dev.peterdsp.odivrelo.core.model.CoverageState
import dev.peterdsp.odivrelo.core.model.DateDataState
import dev.peterdsp.odivrelo.core.model.JourneyFilters
import dev.peterdsp.odivrelo.core.model.JourneyResults
import dev.peterdsp.odivrelo.core.model.Place
import dev.peterdsp.odivrelo.core.model.PlaceKind
import dev.peterdsp.odivrelo.core.model.RecentSearch
import dev.peterdsp.odivrelo.core.time.ServiceTime
import dev.peterdsp.odivrelo.features.EmptyReason
import dev.peterdsp.odivrelo.features.Loadable
import dev.peterdsp.odivrelo.features.runFeature
import kotlin.native.ObjCName
import kotlinx.datetime.Clock
import kotlinx.serialization.Serializable

/**
 * Which endpoint of a search a place picker is filling in.
 */
@ObjCName("OdivreloSearchEndpointKind")
enum class SearchEndpoint { ORIGIN, DESTINATION }

/**
 * A chosen endpoint. The label is kept alongside the id so a restored search
 * can be shown immediately without another lookup, including with no data
 * installed.
 */
@Serializable
@ObjCName("OdivreloChosenPlace")
data class ChosenPlace(
    val id: String,
    val label: String,
    val kind: PlaceKind,
    val terminalLabel: String? = null,
    val bayHint: String? = null,
) {
    companion object {
        fun of(place: Place, languageTag: String, terminalLabel: String? = null): ChosenPlace =
            ChosenPlace(
                id = place.id,
                label = place.name.resolve(languageTag),
                kind = place.kind,
                terminalLabel = terminalLabel,
            )
    }
}

/**
 * Everything a search is, in a form that survives process death.
 *
 * This is the single piece of state the Android application persists into its
 * saved instance state, which is why it is serialisable and holds labels rather
 * than only ids.
 */
@Serializable
@ObjCName("OdivreloSearchQuery")
data class SearchQuery(
    val origin: ChosenPlace? = null,
    val destination: ChosenPlace? = null,
    val serviceDate: String,
    val filters: JourneyFilters = JourneyFilters.NONE,
) {
    val isRunnable: Boolean
        get() = origin != null && destination != null && origin.id != destination.id

    val isSameEndpoint: Boolean
        get() = origin != null && destination != null && origin.id == destination.id

    fun swapped(): SearchQuery = copy(origin = destination, destination = origin)

    fun with(endpoint: SearchEndpoint, place: ChosenPlace?): SearchQuery = when (endpoint) {
        SearchEndpoint.ORIGIN -> copy(origin = place)
        SearchEndpoint.DESTINATION -> copy(destination = place)
    }

    fun shiftDate(days: Int): SearchQuery =
        copy(serviceDate = ServiceTime.shiftServiceDate(serviceDate, days))

    companion object {
        fun startingToday(clock: Clock = Clock.System): SearchQuery =
            SearchQuery(serviceDate = ServiceTime.currentServiceDate(clock.now()))
    }
}

/**
 * A group of places that share a terminal, so a person can see that "Kithra"
 * means a terminal with three bays before they commit to one.
 */
@ObjCName("OdivreloPlaceGroup")
data class PlaceGroup(
    val terminal: Place?,
    val boardingPoints: List<Place>,
) {
    val hasDisambiguation: Boolean get() = boardingPoints.size > 1
}

/**
 * Place lookup and journey search, with the service date handled entirely by the
 * core.
 */
@ObjCName("OdivreloSearchFeature")
class SearchFeature(
    private val core: OdivreloCore,
    private val languageTag: String,
) {

    /**
     * Looks up places and groups boarding points under their terminal.
     *
     * A query shorter than [MINIMUM_QUERY_LENGTH] returns [EmptyReason.NEEDS_INPUT]
     * rather than the whole dataset, so a single keystroke does not look like a
     * result list.
     */
    suspend fun places(query: String): Loadable<List<PlaceGroup>> {
        val trimmed = query.trim()
        if (trimmed.length < MINIMUM_QUERY_LENGTH) {
            return Loadable.Empty(EmptyReason.NEEDS_INPUT)
        }
        val result = runFeature { core.searchPlaces(trimmed, MAX_PLACE_RESULTS) }
        if (result !is Loadable.Ready) return result as Loadable<List<PlaceGroup>>

        val places = result.value.places
        if (places.isEmpty()) return Loadable.Empty(EmptyReason.NO_MATCHING_PLACE)

        val byId = places.associateBy { it.id }
        val terminals = places.filter { it.kind == PlaceKind.STOP_PLACE }
        val grouped = mutableListOf<PlaceGroup>()
        val consumed = mutableSetOf<String>()

        terminals.forEach { terminal ->
            val children = places.filter { it.parentId == terminal.id }
            consumed += terminal.id
            consumed += children.map { it.id }
            grouped += PlaceGroup(terminal, children)
        }
        // Boarding points whose terminal did not match the query still need to
        // be offered, with their terminal named where it is known.
        places.filter { it.kind == PlaceKind.STOP && it.id !in consumed }.forEach { stop ->
            grouped += PlaceGroup(stop.parentId?.let { byId[it] }, listOf(stop))
        }
        return Loadable.Ready(grouped)
    }

    fun chosenPlace(place: Place, parent: Place?): ChosenPlace = ChosenPlace.of(
        place = place,
        languageTag = languageTag,
        terminalLabel = parent?.name?.resolve(languageTag),
    )

    /**
     * Runs a search, mapping every outcome onto a state a screen can render.
     *
     * The two absences are kept apart. A pack was read and nothing matched, which
     * is [EmptyReason.NO_SERVICE_ON_DATE] and is a statement about service; or no
     * pack for the date is installed, which is
     * [EmptyReason.NO_OFFLINE_DATA_FOR_DATE] and is a statement about this device.
     * Showing the first when the truth is the second would invent a certainty the
     * data does not support.
     */
    suspend fun run(query: SearchQuery): Loadable<JourneyResults> {
        val origin = query.origin
        val destination = query.destination
        if (origin == null || destination == null) {
            return Loadable.Empty(EmptyReason.NEEDS_INPUT)
        }
        if (origin.id == destination.id) {
            return Loadable.Empty(EmptyReason.ORIGIN_EQUALS_DESTINATION)
        }

        val outcome = runFeature {
            core.searchJourneys(origin.id, destination.id, query.serviceDate, query.filters)
        }
        if (outcome !is Loadable.Ready) return outcome

        val results = outcome.value
        if (results.results.isNotEmpty()) return Loadable.Ready(results)

        if (results.dateDataState == DateDataState.NO_OFFLINE_PACK) {
            return Loadable.Empty(EmptyReason.NO_OFFLINE_DATA_FOR_DATE, results.coverage)
        }

        val reason = when {
            results.unavailableReason != null -> EmptyReason.of(results.unavailableReason)
            query.filters.isActive -> EmptyReason.FILTERED_OUT
            results.coverage == CoverageState.PARTIAL -> EmptyReason.PARTIAL_COVERAGE
            results.coverage == CoverageState.NOT_COVERED -> EmptyReason.OUTSIDE_COVERAGE
            else -> EmptyReason.NO_SERVICE_ON_DATE
        }
        return Loadable.Empty(reason, results.coverage)
    }

    suspend fun recents(): Loadable<List<RecentSearch>> {
        val outcome = runFeature { core.recentSearches() }
        if (outcome is Loadable.Ready && outcome.value.isEmpty()) {
            return Loadable.Empty(EmptyReason.NOTHING_SAVED)
        }
        return outcome
    }

    /** Rebuilds a search from a recent entry without another place lookup. */
    fun queryFrom(recent: RecentSearch, keepDate: Boolean, today: String): SearchQuery = SearchQuery(
        origin = ChosenPlace(
            id = recent.originId,
            label = recent.originName.resolve(languageTag),
            kind = PlaceKind.STOP_PLACE,
        ),
        destination = ChosenPlace(
            id = recent.destinationId,
            label = recent.destinationName.resolve(languageTag),
            kind = PlaceKind.STOP_PLACE,
        ),
        serviceDate = if (keepDate && recent.serviceDate >= today) recent.serviceDate else today,
    )

    companion object {
        const val MINIMUM_QUERY_LENGTH: Int = 2
        const val MAX_PLACE_RESULTS: Int = 40
    }
}
