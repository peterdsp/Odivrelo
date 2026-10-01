package dev.peterdsp.odivrelo.core

import app.cash.sqldelight.db.SqlDriver
import dev.peterdsp.odivrelo.core.db.OdivreloDatabase
import dev.peterdsp.odivrelo.core.model.CoverageState
import dev.peterdsp.odivrelo.core.model.DataMode
import dev.peterdsp.odivrelo.core.model.DateDataState
import dev.peterdsp.odivrelo.core.model.ErrorCode
import dev.peterdsp.odivrelo.core.model.FreshnessState
import dev.peterdsp.odivrelo.core.model.JourneyFilters
import dev.peterdsp.odivrelo.core.model.JourneySort
import dev.peterdsp.odivrelo.core.model.PlaceKind
import dev.peterdsp.odivrelo.core.model.PositionQuality
import dev.peterdsp.odivrelo.core.model.UnavailableReason
import dev.peterdsp.odivrelo.core.packs.PackDownloader
import dev.peterdsp.odivrelo.core.packs.PackNames
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.utils.io.ByteReadChannel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant

/**
 * The core's public behaviour against the one canonical release: search,
 * filtering, deterministic ordering, the difference between "no offline data for
 * this date" and "no service on this date", person-owned state, and persistence
 * across a schema migration.
 */
class OdivreloCoreTest {

    private val now = Instant.parse("2026-09-30T09:00:00Z")

    /** The terminal the release gives several boarding points, and its bays. */
    private val terminalId: String get() = Release.terminalWithBays.id

    /**
     * A destination stop the busiest day's journeys all reach. Discovered from the
     * release, because identifiers are opaque by contract.
     */
    private val destinationId: String by lazy {
        val day = Release.dayPack(Release.busiestDate)
        val boardingIds = Release.boardingPointsOf(terminalId).map { it.id }.toSet() + terminalId
        day.journeys.values
            .mapNotNull { detail ->
                detail.stops.sortedBy { it.sequence }
                    .lastOrNull { it.stopId !in boardingIds }
                    ?.stopId
            }
            .groupingBy { it }
            .eachCount()
            .maxByOrNull { it.value }
            ?.key
            ?: error("no destination stop found in the release")
    }

    private fun core(
        packsDirectory: String = createTestDirectory("core").also { Release.install(it) },
        clock: FixedClock = FixedClock(now),
        driver: SqlDriver = createTestDriver(),
        apiBaseUrl: String? = null,
        engine: MockEngine = MockEngine {
            respond(ByteReadChannel(ByteArray(0)), HttpStatusCode.ServiceUnavailable)
        },
    ): OdivreloCoreImpl = OdivreloCoreImpl(
        config = CoreConfig(
            apiBaseUrl = apiBaseUrl,
            staticPacksBaseUrl = if (apiBaseUrl == null) "https://packs.invalid/data" else null,
            packsDirectory = packsDirectory,
            databasePath = ":memory:",
            languageTag = "el",
        ),
        httpClient = HttpClient(engine),
        driver = driver,
        clock = clock,
        retryDelays = PackDownloader.TEST_RETRY_DELAYS,
    )

    // -- Configuration -------------------------------------------------------

    @Test
    fun configurationRejectsUnusableLocationsWithoutThrowingFromTheConstructor() {
        // Construction must not throw: a throwing initialiser across the
        // Objective-C boundary terminates the process. See FirstRunTest.
        assertFalse(CoreConfig(null, null, "", "/tmp/db", "el").isValid)
        assertFalse(CoreConfig(null, null, "/tmp/packs", "", "el").isValid)
        assertTrue(CoreConfig(null, null, "/tmp/packs", "/tmp/db", "el").isValid)
    }

    @Test
    fun configurationFallsBackToGreekForAnUnsupportedLanguage() {
        val config = CoreConfig(null, null, "/tmp/packs", "/tmp/db", "de")
        assertEquals("el", config.resolvedLanguageTag)
        assertTrue(config.isStaticPackMode)
        assertEquals("sq", config.copy(languageTag = "sq-AL").resolvedLanguageTag)
        assertEquals("en", config.copy(languageTag = "en-GB").resolvedLanguageTag)
    }

    // -- Meta and coverage ---------------------------------------------------

    @Test
    fun metaReportsTheInstalledReleaseAndDemoMode() = runTest {
        val core = core()
        try {
            val meta = core.meta()
            assertEquals("1.0.0", meta.contractVersion)
            assertEquals(Release.releaseId, meta.releaseId)
            assertEquals(DataMode.DEMO, meta.dataMode)
            assertTrue(meta.isDemo)
            assertEquals("Odivrelo", meta.product.name)

            val coverage = core.coverage()
            assertEquals(CoverageState.DEMO, coverage.state)
            assertEquals(Release.releaseId, coverage.releaseId)
            assertTrue(coverage.notCovered.isNotEmpty())
            assertTrue(coverage.absenceSemantics.isNotEmpty())
        } finally {
            core.close()
        }
    }

    @Test
    fun readingWithNoInstalledReleaseFailsWithAnHonestMessageAndKind() = runTest {
        val core = core(packsDirectory = createTestDirectory("core-empty"))
        try {
            val error = assertFailsWith<OdivreloException> { core.meta() }
            assertEquals(ErrorCode.UNAVAILABLE, error.code)
            assertEquals(OdivreloFailureKind.NO_DATA_INSTALLED, error.kind)
        } finally {
            core.close()
        }
    }

    @Test
    fun readingAnIncompleteReleaseAsksForAReinstallRatherThanFailingVaguely() = runTest {
        val directory = createTestDirectory("core-incomplete")
        Release.install(directory, omit = setOf(PackNames.OPERATORS))
        val core = core(packsDirectory = directory)
        try {
            val error = assertFailsWith<OdivreloException> { core.meta() }
            assertEquals(OdivreloFailureKind.INCOMPLETE_DATA, error.kind)
            assertTrue(error.message.contains(PackNames.OPERATORS))
        } finally {
            core.close()
        }
    }

    // -- Place search --------------------------------------------------------

    @Test
    fun placeSearchIsAccentAndCaseInsensitiveInGreek() = runTest {
        val core = core()
        try {
            val greekName = Release.terminalWithBays.name.resolve("el")
            val plain = greekName.lowercase()
            val stripped = plain
                .replace('ά', 'α').replace('έ', 'ε').replace('ή', 'η')
                .replace('ί', 'ι').replace('ό', 'ο').replace('ύ', 'υ').replace('ώ', 'ω')

            val exact = core.searchPlaces(greekName, 20).places.map { it.id }
            val lower = core.searchPlaces(plain, 20).places.map { it.id }
            val unaccented = core.searchPlaces(stripped, 20).places.map { it.id }
            val upper = core.searchPlaces(greekName.uppercase(), 20).places.map { it.id }

            assertTrue(terminalId in exact, "the terminal must be found by its own Greek name")
            assertEquals(exact, lower)
            assertEquals(exact, unaccented)
            assertEquals(exact, upper)
        } finally {
            core.close()
        }
    }

    @Test
    fun placeSearchWorksInAllThreeLanguages() = runTest {
        val core = core()
        try {
            listOf("el", "en", "sq").forEach { language ->
                val name = Release.terminalWithBays.name.resolve(language)
                assertTrue(
                    core.searchPlaces(name, 20).places.any { it.id == terminalId },
                    "searching the $language name '$name' did not find the terminal",
                )
            }
        } finally {
            core.close()
        }
    }

    @Test
    fun placeSearchPutsTerminalsBeforeTheirBoardingPoints() = runTest {
        val core = core()
        try {
            val name = Release.terminalWithBays.name.resolve("en")
            val results = core.searchPlaces(name, 20).places
            val terminalIndex = results.indexOfFirst { it.kind == PlaceKind.STOP_PLACE }
            val boardingIndex = results.indexOfFirst { it.kind == PlaceKind.STOP }
            assertTrue(terminalIndex >= 0, "the terminal was not offered")
            assertTrue(boardingIndex >= 0, "its boarding points were not offered")
            assertTrue(
                terminalIndex < boardingIndex,
                "a traveller should be offered the terminal before a single bay",
            )
        } finally {
            core.close()
        }
    }

    @Test
    fun placeSearchIsDeterministicAndRespectsTheLimit() = runTest {
        val core = core()
        try {
            val first = core.searchPlaces("a", 3)
            val second = core.searchPlaces("a", 3)
            assertEquals(first.places.map { it.id }, second.places.map { it.id })
            assertTrue(first.places.size <= 3)
            assertEquals(first.places.size, first.total)

            assertTrue(core.searchPlaces("zzzzzzzzzz", 10).places.isEmpty())
            assertTrue(core.searchPlaces("", 5).places.all { it.kind == PlaceKind.STOP_PLACE })
        } finally {
            core.close()
        }
    }

    // -- Journey search ------------------------------------------------------

    @Test
    fun aTerminalSearchFindsJourneysFromAnyOfItsBoardingPoints() = runTest {
        val core = core()
        try {
            val date = Release.busiestDate
            val results = core.searchJourneys(terminalId, destinationId, date, JourneyFilters.NONE)
            assertEquals(DateDataState.FROM_INSTALLED_PACK, results.dateDataState)
            assertEquals(CoverageState.DEMO, results.coverage)
            assertNull(results.unavailableReason)
            assertTrue(results.servedFromCache)
            assertEquals(Release.manifest.publishedAt, results.cachedReleasePublishedAt)

            val bays = Release.boardingPointsOf(terminalId).map { it.id }.toSet()
            val boardedFrom = results.results.map { it.departure.stopId }.toSet()
            assertTrue(boardedFrom.isNotEmpty())
            assertTrue(
                boardedFrom.all { it in bays || it == terminalId },
                "a terminal search must only board at that terminal: $boardedFrom",
            )
            assertTrue(
                boardedFrom.size > 1,
                "the release should exercise a terminal search spanning several bays",
            )
        } finally {
            core.close()
        }
    }

    @Test
    fun searchingOneBayOnlyFindsJourneysFromThatBay() = runTest {
        val core = core()
        try {
            val date = Release.busiestDate
            val all = core.searchJourneys(terminalId, destinationId, date, JourneyFilters.NONE).results
            val bay = all.first().departure.stopId

            val narrowed = core.searchJourneys(bay, destinationId, date, JourneyFilters.NONE).results
            assertTrue(narrowed.isNotEmpty())
            assertTrue(narrowed.all { it.departure.stopId == bay })
            assertTrue(
                narrowed.size < all.size,
                "choosing one bay should be narrower than choosing the whole terminal",
            )
        } finally {
            core.close()
        }
    }

    @Test
    fun resultsAreOrderedByDepartureAndTheOrderIsStable() = runTest {
        val core = core()
        try {
            val date = Release.busiestDate
            val first = core.searchJourneys(terminalId, destinationId, date, JourneyFilters.NONE)
                .results.map { it.id }
            val second = core.searchJourneys(terminalId, destinationId, date, JourneyFilters.NONE)
                .results.map { it.id }
            assertEquals(first, second, "the same search must produce the same order")

            val departures = core.searchJourneys(terminalId, destinationId, date, JourneyFilters.NONE)
                .results.map { it.departure.at }
            assertEquals(departures.sorted(), departures)
        } finally {
            core.close()
        }
    }

    @Test
    fun anOvernightResultIsMarkedAndKeepsTheEarlierServiceDate() = runTest {
        val core = core()
        try {
            val date = Release.busiestDate
            val results = core.searchJourneys(terminalId, destinationId, date, JourneyFilters.NONE)
                .results
            val overnight = results.firstOrNull { it.crossesMidnight }
            assertNotNull(overnight, "the release should include a journey crossing midnight")

            assertEquals(date, overnight.serviceDate, "an overnight journey keeps the earlier date")
            val departure = ServiceTimeProbe.instant(overnight.departure.at)!!
            val arrival = ServiceTimeProbe.instant(overnight.arrival.at)!!
            assertTrue(
                dev.peterdsp.odivrelo.core.time.ServiceTime.crossesMidnight(departure, arrival),
            )
            assertEquals(
                dev.peterdsp.odivrelo.core.time.ServiceTime.durationMinutes(departure, arrival),
                overnight.durationMinutes,
            )
            // The arrival's local date is the next day, and that did not move the
            // service date.
            assertEquals(
                dev.peterdsp.odivrelo.core.time.ServiceTime.shiftServiceDate(date, 1),
                dev.peterdsp.odivrelo.core.time.ServiceTime.localDateTimeOf(arrival).date.toString(),
            )
        } finally {
            core.close()
        }
    }

    @Test
    fun aSegmentBetweenTwoIntermediateStopsIsComputedFromTheOrderedStopList() = runTest {
        val core = core()
        try {
            val date = Release.busiestDate
            val day = Release.dayPack(date)
            // Find a journey with an intermediate stop that both allows pickup and
            // is followed by a stop that allows dropoff.
            val candidate = day.journeys.values.firstNotNullOfOrNull { detail ->
                val ordered = detail.stops.sortedBy { it.sequence }
                val middle = ordered.drop(1).dropLast(1)
                    .firstOrNull { it.pickup != dev.peterdsp.odivrelo.core.model.BoardingRule.NOT_ALLOWED }
                    ?: return@firstNotNullOfOrNull null
                val later = ordered.lastOrNull {
                    it.sequence > middle.sequence &&
                        it.dropoff != dev.peterdsp.odivrelo.core.model.BoardingRule.NOT_ALLOWED
                } ?: return@firstNotNullOfOrNull null
                Triple(detail, middle, later)
            }
            assertNotNull(candidate, "the release should exercise an intermediate boarding point")
            val (detail, middle, later) = candidate

            val results = core.searchJourneys(middle.stopId, later.stopId, date, JourneyFilters.NONE)
                .results
            val segment = results.firstOrNull { it.id.substringBefore("~") == detail.id.substringBefore("~") }
            assertNotNull(segment, "the segment search did not find the journey")

            assertEquals(middle.stopId, segment.departure.stopId)
            assertEquals(later.stopId, segment.arrival.stopId)
            assertEquals(middle.departureAt ?: middle.arrivalAt, segment.departure.at)
            assertEquals(later.arrivalAt ?: later.departureAt, segment.arrival.at)
            // The segment is shorter than the whole journey, which is the point.
            assertTrue(segment.durationMinutes < detail.durationMinutes)

            // The detail for that result follows the same leg: it does not snap
            // back to the end of the run. This is the defect that was reported.
            val scoped = core.journeyDetail(segment.id, date).journey
            assertEquals(segment.id, scoped.id)
            assertEquals(later.stopId, scoped.arrival.stopId)
            assertEquals(segment.arrival.at, scoped.arrival.at)
            assertEquals(segment.durationMinutes, scoped.durationMinutes)
            assertEquals(middle.stopId, scoped.selectedSegment?.boardStopId)
            assertEquals(later.stopId, scoped.selectedSegment?.alightStopId)
            // The whole run is still present, with the leg marked.
            assertEquals(detail.stops.size, scoped.stops.size)
            assertEquals(
                dev.peterdsp.odivrelo.core.model.SegmentRole.BOARD,
                scoped.stops.first { it.stopId == middle.stopId }.segmentRole,
            )
            assertEquals(
                dev.peterdsp.odivrelo.core.model.SegmentRole.ALIGHT,
                scoped.stops.first { it.stopId == later.stopId }.segmentRole,
            )
        } finally {
            core.close()
        }
    }

    @Test
    fun boardingWhereBoardingIsNotAllowedIsNeverOffered() = runTest {
        val core = core()
        try {
            val date = Release.busiestDate
            val day = Release.dayPack(date)
            val detail = day.journeys.values.first()
            val ordered = detail.stops.sortedBy { it.sequence }
            val terminus = ordered.last()
            assertEquals(
                dev.peterdsp.odivrelo.core.model.BoardingRule.NOT_ALLOWED,
                terminus.pickup,
                "the fixture should have a terminus that cannot be boarded at",
            )

            val results = core.searchJourneys(
                terminus.stopId,
                ordered.first().stopId,
                date,
                JourneyFilters.NONE,
            )
            assertTrue(
                results.results.none { it.id.substringBefore("~") == detail.id.substringBefore("~") },
                "a journey must never be offered boarding at a stop that forbids pickup",
            )
        } finally {
            core.close()
        }
    }

    @Test
    fun everyResultIsScheduledBecauseNothingInThisReleaseIsObserved() = runTest {
        val core = core()
        try {
            val results = core.searchJourneys(
                terminalId,
                destinationId,
                Release.busiestDate,
                JourneyFilters.NONE,
            ).results
            assertTrue(results.isNotEmpty())
            results.forEach {
                assertEquals(PositionQuality.SCHEDULED, it.positionQuality)
                assertFalse(it.positionQuality.isRealTime)
            }
        } finally {
            core.close()
        }
    }

    @Test
    fun originEqualToDestinationIsItsOwnState() = runTest {
        val core = core()
        try {
            val results = core.searchJourneys(
                terminalId,
                terminalId,
                Release.busiestDate,
                JourneyFilters.NONE,
            )
            assertEquals(UnavailableReason.ORIGIN_EQUALS_DESTINATION, results.unavailableReason)
            assertTrue(results.results.isEmpty())
        } finally {
            core.close()
        }
    }

    @Test
    fun anUnknownPlaceIsReportedAsOutsideCoverage() = runTest {
        val core = core()
        try {
            val results = core.searchJourneys(
                "place-that-does-not-exist",
                destinationId,
                Release.busiestDate,
                JourneyFilters.NONE,
            )
            assertEquals(UnavailableReason.OUTSIDE_COVERAGE, results.unavailableReason)
        } finally {
            core.close()
        }
    }

    @Test
    fun aMalformedServiceDateIsRejectedWithTheOffendingField() = runTest {
        val core = core()
        try {
            val error = assertFailsWith<OdivreloException> {
                core.searchJourneys(terminalId, destinationId, "02/10/2026", JourneyFilters.NONE)
            }
            assertEquals(ErrorCode.INVALID_REQUEST, error.code)
            assertEquals(OdivreloFailureKind.INVALID_REQUEST, error.kind)
            assertEquals("date", error.field)
        } finally {
            core.close()
        }
    }

    // -- The two different absences ------------------------------------------

    @Test
    fun aDateWithNoPublishedPackIsNoOfflineDataAndNeverNoService() = runTest {
        // A release only materialises journeys packs for the dates its data names.
        // A weekday-recurring service on some other date is resolved by the API on
        // request. So with no pack and no service, the honest answer is "this device
        // holds no timetable for that date", not "nothing runs that date".
        val core = core()
        try {
            val date = Release.unpublishedDate
            assertFalse(date in Release.serviceDates)

            val results = core.searchJourneys(terminalId, destinationId, date, JourneyFilters.NONE)
            assertEquals(DateDataState.NO_OFFLINE_PACK, results.dateDataState)
            assertFalse(results.dateDataState.isAnswer)
            assertTrue(results.results.isEmpty())
            assertNull(
                results.unavailableReason,
                "with no pack there is no claim to make about service on that date",
            )
            assertEquals(date, results.query.date)
        } finally {
            core.close()
        }
    }

    @Test
    fun aDateWithAPackAndNoMatchingJourneyIsNoServiceOnThatDate() = runTest {
        val core = core()
        try {
            // A published date whose pack does not connect this pair.
            val thin = Release.serviceDates.firstOrNull { date ->
                date != Release.busiestDate &&
                    Release.dayPack(date).journeys.values.none { detail ->
                        detail.stops.any { it.stopId == destinationId }
                    }
            }
            val date = thin ?: Release.busiestDate
            val unreachable = Release.places
                .last { it.kind == PlaceKind.STOP && it.id != destinationId }.id

            val results = core.searchJourneys(terminalId, unreachable, date, JourneyFilters.NONE)
            // There is a pack, so the answer really is about service.
            assertEquals(DateDataState.FROM_INSTALLED_PACK, results.dateDataState)
            assertTrue(results.dateDataState.isAnswer)
            if (results.results.isEmpty()) {
                assertNotNull(
                    results.unavailableReason,
                    "with a pack installed, an empty result must say why",
                )
            }
        } finally {
            core.close()
        }
    }

    @Test
    fun journeyDetailForAnUnpublishedDateSaysThereIsNoOfflineTimetable() = runTest {
        val core = core()
        try {
            val day = Release.dayPack(Release.busiestDate)
            val journeyId = day.results.first().id
            val error = assertFailsWith<OdivreloException> {
                core.journeyDetail(journeyId, Release.unpublishedDate)
            }
            assertEquals(OdivreloFailureKind.NO_OFFLINE_PACK_FOR_DATE, error.kind)
            assertTrue(
                error.message.contains("not the same as no service"),
                "the message must not imply that nothing runs: ${error.message}",
            )
        } finally {
            core.close()
        }
    }

    @Test
    fun aStationPageForAnUnpublishedDateSaysSoRatherThanShowingAnEmptyTimetable() = runTest {
        val core = core()
        try {
            val stopId = Release.boardingPointsOf(terminalId).first().id
            val detail = core.stopDetail(stopId, Release.unpublishedDate)
            assertEquals(DateDataState.NO_OFFLINE_PACK, detail.dateDataState)
            assertTrue(detail.stop.departures.isEmpty())
            // The stop itself is still fully readable; only the day is unknown.
            assertTrue(detail.stop.name.resolve("el").isNotBlank())
        } finally {
            core.close()
        }
    }

    // -- Filters -------------------------------------------------------------

    @Test
    fun theAccessibleFilterKeepsOnlyReviewedStepFreeBoardingPoints() = runTest {
        val core = core()
        try {
            val date = Release.busiestDate
            val all = core.searchJourneys(terminalId, destinationId, date, JourneyFilters.NONE).results
            val accessible = core.searchJourneys(
                terminalId,
                destinationId,
                date,
                JourneyFilters(accessibleOnly = true),
            ).results

            assertTrue(accessible.size < all.size, "the release should exercise the filter biting")
            assertTrue(accessible.isNotEmpty())
            accessible.forEach {
                assertEquals(
                    true,
                    it.accessibleBoardingPoint,
                    "the accessible filter must not pass an unreviewed boarding point",
                )
            }
            // A boarding point nobody reviewed is excluded, and it is excluded for
            // being unreviewed rather than for being known to have steps.
            assertTrue(all.any { it.accessibleBoardingPoint == null })
        } finally {
            core.close()
        }
    }

    @Test
    fun theCrossesMidnightFilterAndTimeWindowsNarrowResultsPredictably() = runTest {
        val core = core()
        try {
            val date = Release.busiestDate
            val all = core.searchJourneys(terminalId, destinationId, date, JourneyFilters.NONE).results
            assertTrue(all.any { it.crossesMidnight })

            val daytime = core.searchJourneys(
                terminalId,
                destinationId,
                date,
                JourneyFilters(includeCrossesMidnight = false),
            ).results
            assertTrue(daytime.none { it.crossesMidnight })
            assertTrue(daytime.size < all.size)

            val afterNoon = core.searchJourneys(
                terminalId,
                destinationId,
                date,
                JourneyFilters(departAfter = "12:00"),
            ).results
            afterNoon.forEach {
                val local = dev.peterdsp.odivrelo.core.time.ServiceTime
                    .localTimeOf(ServiceTimeProbe.instant(it.departure.at)!!)
                assertTrue(local.hour >= 12, "departAfter let through ${it.departure.at}")
            }

            val beforeNoon = core.searchJourneys(
                terminalId,
                destinationId,
                date,
                JourneyFilters(departBefore = "12:00"),
            ).results
            beforeNoon.forEach {
                val local = dev.peterdsp.odivrelo.core.time.ServiceTime
                    .localTimeOf(ServiceTimeProbe.instant(it.departure.at)!!)
                assertTrue(local.hour <= 12, "departBefore let through ${it.departure.at}")
            }
        } finally {
            core.close()
        }
    }

    @Test
    fun filteringEverythingOutIsAnEmptyStateNotAnError() = runTest {
        val core = core()
        try {
            val results = core.searchJourneys(
                terminalId,
                destinationId,
                Release.busiestDate,
                JourneyFilters(maxDurationMinutes = 1),
            )
            assertTrue(results.results.isEmpty())
            assertEquals(UnavailableReason.NO_SERVICE_ON_DATE, results.unavailableReason)
            // Still a real answer: a pack was read.
            assertEquals(DateDataState.FROM_INSTALLED_PACK, results.dateDataState)
        } finally {
            core.close()
        }
    }

    @Test
    fun sortingByDurationAndArrivalBothStayDeterministic() = runTest {
        val core = core()
        try {
            val date = Release.busiestDate
            val byDuration = core.searchJourneys(
                terminalId,
                destinationId,
                date,
                JourneyFilters(sort = JourneySort.DURATION),
            ).results
            assertEquals(
                byDuration.map { it.durationMinutes }.sorted(),
                byDuration.map { it.durationMinutes },
            )

            val byArrival = core.searchJourneys(
                terminalId,
                destinationId,
                date,
                JourneyFilters(sort = JourneySort.ARRIVAL),
            ).results
            assertEquals(byArrival.map { it.arrival.at }.sorted(), byArrival.map { it.arrival.at })

            assertEquals(
                byDuration.map { it.id },
                core.searchJourneys(
                    terminalId,
                    destinationId,
                    date,
                    JourneyFilters(sort = JourneySort.DURATION),
                ).results.map { it.id },
            )
        } finally {
            core.close()
        }
    }

    @Test
    fun filterStateReportsWhetherItIsActive() {
        assertFalse(JourneyFilters.NONE.isActive)
        assertEquals(0, JourneyFilters.NONE.activeCount)
        assertTrue(JourneyFilters(accessibleOnly = true).isActive)
        assertEquals(1, JourneyFilters(accessibleOnly = true).activeCount)
        assertEquals(
            3,
            JourneyFilters(
                accessibleOnly = true,
                departAfter = "08:00",
                maxDurationMinutes = 240,
            ).activeCount,
        )
    }

    // -- Freshness -----------------------------------------------------------

    @Test
    fun freshnessIsRecomputedAgainstTheClockNotTakenFromThePack() = runTest {
        val clock = FixedClock(Instant.parse("2026-09-20T12:00:00Z"))
        val core = core(clock = clock)
        try {
            val date = Release.busiestDate
            val fresh = core.searchJourneys(terminalId, destinationId, date, JourneyFilters.NONE)
                .results.first()
            assertEquals(FreshnessState.FRESH, fresh.freshness.state)

            clock.advanceTo(Instant.parse("2026-11-20T12:00:00Z"))
            val stale = core.searchJourneys(terminalId, destinationId, date, JourneyFilters.NONE)
                .results.first()
            assertEquals(FreshnessState.STALE, stale.freshness.state)
            assertTrue(stale.freshness.ageHours > 24 * 7)
            // The publisher's checked-at is unchanged; only the age moved.
            assertEquals(fresh.freshness.checkedAt, stale.freshness.checkedAt)
        } finally {
            core.close()
        }
    }

    @Test
    fun freshnessOfIsAvailableToAUserInterfaceWithoutRecomputingIt() = runTest {
        val core = core()
        try {
            assertEquals(
                FreshnessState.FRESH,
                core.freshnessOf("2026-09-30T00:00:00Z", "2026-09-30T09:00:00Z").state,
            )
            assertEquals(
                FreshnessState.STALE,
                core.freshnessOf("2026-01-01T00:00:00Z", "2026-09-30T09:00:00Z").state,
            )
        } finally {
            core.close()
        }
    }

    // -- Journey detail ------------------------------------------------------

    @Test
    fun journeyDetailCarriesTheReviewedBoardingPointStopsAndProvenance() = runTest {
        val core = core()
        try {
            val date = Release.busiestDate
            val journeyId = Release.dayPack(date).results.first().id
            val body = core.journeyDetail(journeyId, date).journey

            assertEquals(date, body.serviceDate)
            assertTrue(body.stops.size >= 2)
            assertEquals(body.stops.map { it.sequence }.sorted(), body.stops.map { it.sequence })

            val boarding = assertNotNull(body.boardingPoint)
            assertTrue(boarding.stopId.isNotBlank())
            assertTrue(body.provenance.isNotEmpty())
            assertNotNull(body.correctionUrl)
            assertNotNull(body.purchase.label)
            assertEquals(PositionQuality.SCHEDULED, body.positionQuality)
        } finally {
            core.close()
        }
    }

    @Test
    fun journeyDetailForAJourneyThatDoesNotRunThatDayIsNotFound() = runTest {
        val core = core()
        try {
            val date = Release.busiestDate
            val other = Release.serviceDates.first { it != date }
            val onlyOnBusiest = Release.dayPack(date).results.map { it.id }
                .firstOrNull { it !in Release.dayPack(other).results.map { row -> row.id } }
            assertNotNull(onlyOnBusiest, "the release should have a journey unique to one date")

            val error = assertFailsWith<OdivreloException> { core.journeyDetail(onlyOnBusiest, other) }
            assertEquals(ErrorCode.NOT_FOUND, error.code)
            assertEquals(OdivreloFailureKind.NOT_FOUND, error.kind)
        } finally {
            core.close()
        }
    }

    @Test
    fun theReleaseExercisesBothAnOnlineHandoffAndAnOfflineTicketFallback() = runTest {
        val core = core()
        try {
            val date = Release.busiestDate
            val purchases = Release.dayPack(date).results.map { summary ->
                core.journeyDetail(summary.id, date).journey.purchase
            }
            assertTrue(
                purchases.any { it.isExternalHandoff },
                "a booking handoff must be exercised",
            )
            val office = purchases.firstOrNull { !it.isExternalHandoff }
            assertNotNull(office, "a service with no online sale must be exercised")
            assertTrue(
                office.hasOfflineFallback,
                "a service with no online sale needs a verified phone or address",
            )
            purchases.forEach {
                assertNotNull(it.label, "every purchase option needs a label a person can read")
            }
        } finally {
            core.close()
        }
    }

    // -- Operators and stops -------------------------------------------------

    @Test
    fun operatorDetailCarriesAttributionAndACorrectionPath() = runTest {
        val core = core()
        try {
            val operatorId = Release.dayPack(Release.busiestDate).results.first().operator.id
            val operator = core.operatorDetail(operatorId).operator
            assertEquals(CoverageState.DEMO, operator.coverage.state)
            assertTrue(operator.sources.isNotEmpty())
            assertNotNull(operator.correctionUrl)

            assertFailsWith<OdivreloException> { core.operatorDetail("op-does-not-exist") }
        } finally {
            core.close()
        }
    }

    @Test
    fun stopDetailListsDeparturesForTheRequestedServiceDateInOrder() = runTest {
        val core = core()
        try {
            val date = Release.busiestDate
            val stopId = Release.dayPack(date).results.first().departure.stopId
            val stop = core.stopDetail(stopId, date).stop
            assertTrue(stop.departures.isNotEmpty())
            assertEquals(
                stop.departures.map { it.departure.at }.sorted(),
                stop.departures.map { it.departure.at },
            )
            assertTrue(
                stop.departures.all { it.departure.stopId == stopId },
                "a station page must only list departures from that stop",
            )
            assertTrue(stop.departures.all { it.serviceDate == date })
            assertTrue(stop.provenance.isNotEmpty())
            assertNotNull(stop.correctionUrl)

            assertFailsWith<OdivreloException> { core.stopDetail("stop-does-not-exist", date) }
        } finally {
            core.close()
        }
    }

    @Test
    fun sourcesAreListedWithRightsStatus() = runTest {
        val core = core()
        try {
            val sources = core.sources()
            assertTrue(sources.sources.isNotEmpty())
            assertEquals(Release.releaseId, sources.releaseId)
            assertEquals(sources.sources.map { it.id }.sorted(), sources.sources.map { it.id })
        } finally {
            core.close()
        }
    }

    // -- Favourites, recents, saved trips ------------------------------------

    @Test
    fun favouritesToggleAndPersist() = runTest {
        val core = core()
        try {
            assertTrue(core.favorites().isEmpty())
            assertTrue(core.toggleFavorite(terminalId))
            val favourites = core.favorites()
            assertEquals(1, favourites.size)
            assertEquals(terminalId, favourites.single().placeId)
            assertEquals(
                Release.terminalWithBays.name.resolve("el"),
                favourites.single().name.resolve("el"),
            )
            assertEquals(PlaceKind.STOP_PLACE, favourites.single().kind)

            assertFalse(core.toggleFavorite(terminalId))
            assertTrue(core.favorites().isEmpty())
        } finally {
            core.close()
        }
    }

    @Test
    fun favouritingAnUnknownPlaceFailsRatherThanStoringAnEmptyRow() = runTest {
        val core = core()
        try {
            assertFailsWith<OdivreloException> { core.toggleFavorite("place-nope") }
            assertTrue(core.favorites().isEmpty())
        } finally {
            core.close()
        }
    }

    @Test
    fun recentSearchesAreRecordedNewestFirstAndBounded() = runTest {
        val clock = FixedClock(now)
        val core = core(clock = clock)
        try {
            assertTrue(core.recentSearches().isEmpty())
            val date = Release.busiestDate

            core.searchJourneys(terminalId, destinationId, date, JourneyFilters.NONE)
            clock.advanceTo(Instant.parse("2026-09-30T10:00:00Z"))
            val bay = Release.boardingPointsOf(terminalId).first().id
            core.searchJourneys(bay, destinationId, date, JourneyFilters.NONE)

            val recents = core.recentSearches()
            assertEquals(2, recents.size)
            assertEquals(bay, recents.first().originId)
            assertEquals(terminalId, recents[1].originId)
            assertTrue(recents.size <= OdivreloCoreImpl.MAX_RECENT_SEARCHES)
        } finally {
            core.close()
        }
    }

    @Test
    fun aSearchWithNoResultsIsNotRecordedAsARecentSearch() = runTest {
        val core = core()
        try {
            core.searchJourneys(
                terminalId,
                destinationId,
                Release.unpublishedDate,
                JourneyFilters.NONE,
            )
            assertTrue(core.recentSearches().isEmpty())
        } finally {
            core.close()
        }
    }

    @Test
    fun savingATripKeepsEnoughToReadItOfflineAndRemovingItWorks() = runTest {
        val core = core()
        try {
            val date = Release.busiestDate
            val overnight = Release.dayPack(date).results.first { it.crossesMidnight }

            val trip = core.saveTrip(overnight.id, date)
            assertEquals(overnight.id, trip.journeyId)
            assertEquals(date, trip.serviceDate)
            assertTrue(trip.crossesMidnight)
            assertEquals(Release.releaseId, trip.releaseId)
            assertEquals(overnight.departure.at, trip.departureAt)
            assertTrue(trip.operatorName.resolve("el").isNotBlank())

            assertEquals(listOf(trip.id), core.savedTrips().map { it.id })

            // The full detail is cached, which is what makes Trip Ready honest when
            // there is no network and no installed release.
            val cached = assertNotNull(core.savedTripDetail(trip.id))
            assertEquals(overnight.id, cached.journey.id.substringBefore("~"))
            assertTrue(cached.journey.stops.isNotEmpty())
            assertNotNull(cached.journey.boardingPoint)

            core.removeSavedTrip(trip.id)
            assertTrue(core.savedTrips().isEmpty())
            assertNull(core.savedTripDetail(trip.id))
        } finally {
            core.close()
        }
    }

    @Test
    fun aSavedTripStaysReadableAfterItsReleaseIsRemoved() = runTest {
        val directory = createTestDirectory("saved-trip-offline")
        Release.install(directory)
        val core = core(packsDirectory = directory)
        try {
            val date = Release.busiestDate
            val journeyId = Release.dayPack(date).results.first().id
            val trip = core.saveTrip(journeyId, date)

            // The data release goes away, as it would after a failed update.
            core.removePack(PackNames.STOPS)
            assertFailsWith<OdivreloException> { core.meta() }

            // The trip and its cached detail survive, and say which release they
            // came from and when they were cached.
            val saved = core.savedTrips().single()
            assertEquals(trip.id, saved.id)
            assertEquals(Release.releaseId, saved.releaseId)
            assertTrue(saved.cachedAt.isNotBlank())
            val cached = assertNotNull(core.savedTripDetail(trip.id))
            assertEquals(journeyId, cached.journey.id.substringBefore("~"))
            assertTrue(cached.journey.stops.isNotEmpty())
        } finally {
            core.close()
        }
    }

    @Test
    fun savingTheSameTripTwiceDoesNotDuplicateIt() = runTest {
        val core = core()
        try {
            val date = Release.busiestDate
            val journeyId = Release.dayPack(date).results.first().id
            val first = core.saveTrip(journeyId, date)
            val second = core.saveTrip(journeyId, date)
            assertEquals(first.id, second.id)
            assertEquals(1, core.savedTrips().size)
        } finally {
            core.close()
        }
    }

    @Test
    fun savedTripsSurviveTheSchemaMigrationFromVersionOne() = runTest {
        // A beta tester's phone holds the version 1 schema with a saved trip in it.
        // After the upgrade the trip must still be there, and the column added in
        // version 2 must read as "not reviewed" rather than "not step free".
        val driver = createDriver(SchemaVersion1)
        val detail = """{"contractVersion":"1.0.0","releaseId":"old-release","dataMode":"demo",""" +
            """"journey":{"id":"legacy-journey","operator":{"id":"legacy-operator",""" +
            """"name":{"el":"Π","en":"A","sq":"L"}},"departure":{"at":"2026-10-02T09:00:00+03:00",""" +
            """"stopId":"legacy-origin","stopName":{"el":"Κ"},"quality":"scheduled"},""" +
            """"arrival":{"at":"2026-10-02T12:10:00+03:00","stopId":"legacy-destination",""" +
            """"stopName":{"el":"Β"},"quality":"scheduled"},"durationMinutes":190,""" +
            """"intermediateStopCount":1,"serviceDate":"2026-10-02","crossesMidnight":false,""" +
            """"freshness":{"checkedAt":"2026-09-29T18:30:00Z","ageHours":6,"state":"fresh"},""" +
            """"confidence":"reviewed","purchase":{"kind":"online","url":"https://example.invalid"}}}"""

        driver.execute(
            null,
            """
            INSERT INTO saved_trip (
              id, journey_id, service_date, saved_at, origin_name_json,
              destination_name_json, operator_name_json, departure_at, arrival_at,
              crosses_midnight, boarding_bay, release_id, cached_at, detail_json
            ) VALUES (
              'legacy-trip', 'legacy-journey', '2026-10-02', '2026-09-20T08:00:00Z',
              '{"el":"Κ"}', '{"el":"Β"}', '{"el":"Π"}',
              '2026-10-02T09:00:00+03:00', '2026-10-02T12:10:00+03:00',
              0, 'A', 'old-release', '2026-09-20T08:00:00Z', '$detail'
            )
            """.trimIndent(),
            0,
        )

        OdivreloDatabase.Schema.migrate(driver, 1, OdivreloDatabase.Schema.version).value

        val core = core(driver = driver)
        try {
            val trips = core.savedTrips()
            assertEquals(1, trips.size)
            val trip = trips.single()
            assertEquals("legacy-trip", trip.id)
            assertEquals("legacy-journey", trip.journeyId)
            assertEquals("A", trip.boardingBay)
            assertEquals("old-release", trip.releaseId)
            assertNull(
                trip.boardingStepFree,
                "a trip saved before the column existed must read as not reviewed",
            )
            assertNotNull(core.savedTripDetail("legacy-trip"))

            // And the migrated database still accepts a new trip using the column.
            val date = Release.busiestDate
            val fresh = core.saveTrip(Release.dayPack(date).results.first().id, date)
            assertEquals(2, core.savedTrips().size)
            assertTrue(fresh.id != "legacy-trip")
        } finally {
            core.close()
        }
    }

    @Test
    fun theCurrentSchemaVersionIsTheOneTheMigrationTargets() {
        assertEquals(2L, OdivreloDatabase.Schema.version)
    }

    // -- Offline catalogue ---------------------------------------------------

    @Test
    fun theOfflineCatalogueReportsInstalledPacksAndAnHonestMapStatement() = runTest {
        val directory = createTestDirectory("catalog")
        Release.install(directory)
        val core = core(packsDirectory = directory)
        try {
            assertTrue(core.adoptSeededRelease(), "a bundled release must register itself")

            val catalogue = core.offlineCatalog()
            assertEquals(Release.releaseId, catalogue.releaseId)
            assertEquals(DataMode.DEMO, catalogue.dataMode)
            assertFalse(
                catalogue.manifestReachable,
                "the test origin is unreachable, and that must be reported not hidden",
            )
            assertEquals(Release.manifest.files.size, catalogue.installed.size)
            assertEquals(
                Release.manifest.files.values.sumOf { it.bytes },
                catalogue.totalInstalledBytes,
            )
            // Odivrelo ships no base-map tiles, and says so.
            assertFalse(catalogue.mapAvailability.baseMapTilesAvailable)
            assertTrue(catalogue.mapAvailability.stopCoordinatesAvailable)
            assertTrue(catalogue.mapAvailability.searchAvailable)
            assertNull(catalogue.rollbackReleaseId)

            // Journeys packs are listed with their date so a person can choose days.
            val journeyPacks = catalogue.available.filter {
                PackNames.serviceDateOf(it.name) != null
            }
            assertEquals(Release.serviceDates.size, journeyPacks.size)
            journeyPacks.forEach { pack ->
                val date = PackNames.serviceDateOf(pack.name)!!
                assertTrue(pack.title.resolve("el").contains(date))
                assertTrue(pack.installed)
            }
        } finally {
            core.close()
        }
    }

    @Test
    fun aBundledPackWithAWrongDigestIsRejectedRatherThanTrustedForBeingLocal() = runTest {
        val directory = createTestDirectory("catalog-bad-seed")
        Release.install(
            directory,
            overrides = mapOf(PackNames.STOPS to Release.corruptDayText()),
        )
        val core = core(packsDirectory = directory)
        try {
            core.adoptSeededRelease()
            // The corrupted pack is removed, so the release no longer loads and the
            // failure is explicit rather than a wrong departure time.
            val error = assertFailsWith<OdivreloException> { core.meta() }
            assertTrue(
                error.kind == OdivreloFailureKind.INCOMPLETE_DATA ||
                    error.kind == OdivreloFailureKind.UNREADABLE_DATA,
                "expected an explicit data failure, got ${error.kind}",
            )
            assertFalse(core.installedPacks().any { it.name == PackNames.STOPS })
        } finally {
            core.close()
        }
    }

    @Test
    fun removingAPackMakesTheReleaseUnreadableInsteadOfPartiallyWrong() = runTest {
        val directory = createTestDirectory("remove-pack")
        Release.install(directory)
        val core = core(packsDirectory = directory)
        try {
            core.adoptSeededRelease()
            assertEquals(Release.releaseId, core.meta().releaseId)

            core.removePack(PackNames.STOPS)
            assertFalse(core.installedPacks().any { it.name == PackNames.STOPS })
            val error = assertFailsWith<OdivreloException> { core.meta() }
            assertEquals(OdivreloFailureKind.INCOMPLETE_DATA, error.kind)
        } finally {
            core.close()
        }
    }

    @Test
    fun rollingBackWithNothingToRollBackToFailsClearly() = runTest {
        val directory = createTestDirectory("rollback-empty")
        Release.install(directory)
        val core = core(packsDirectory = directory)
        try {
            val error = assertFailsWith<OdivreloException> { core.rollbackToPreviousRelease() }
            assertEquals(ErrorCode.NOT_FOUND, error.code)
        } finally {
            core.close()
        }
    }

    @Test
    fun closingTwiceIsSafe() = runTest {
        val core = core()
        core.close()
        core.close()
    }
}

/** A tiny indirection so the tests do not import the time module wholesale. */
private object ServiceTimeProbe {
    fun instant(value: String) = dev.peterdsp.odivrelo.core.time.ServiceTime.parseInstantOrNull(value)
}
