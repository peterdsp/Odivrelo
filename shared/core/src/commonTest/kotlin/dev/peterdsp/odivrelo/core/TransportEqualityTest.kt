package dev.peterdsp.odivrelo.core

import dev.peterdsp.odivrelo.core.model.DateDataState
import dev.peterdsp.odivrelo.core.model.JourneyFilters
import dev.peterdsp.odivrelo.core.model.PlaceKind
import dev.peterdsp.odivrelo.core.packs.PackDownloader
import dev.peterdsp.odivrelo.core.packs.PackNames
import dev.peterdsp.odivrelo.core.serialization.OdivreloJson
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant

/**
 * A pack contains exactly what the matching `/v1/...` endpoint returns, so the
 * core must produce identical answers however the bytes arrived.
 *
 * Three transports are compared for the same query against the same release:
 *
 *  1. a release bundled with the application and adopted from disk;
 *  2. the same release downloaded over HTTP, verified and installed;
 *  3. a live `/v1/journeys` answer for a date the release publishes no pack for.
 *
 * The first two must be equal in every field but the transport marker. The third
 * goes through the same filtering and ordering, which is what proves there is one
 * timetable engine in the core rather than one per transport.
 */
class TransportEqualityTest {

    private val clockInstant = Instant.parse("2026-09-30T09:00:00Z")

    private val terminalId: String get() = Release.terminalWithBays.id

    private val destinationId: String by lazy {
        val boardingIds = Release.boardingPointsOf(terminalId).map { it.id }.toSet() + terminalId
        Release.dayPack(Release.busiestDate).journeys.values
            .mapNotNull { detail ->
                detail.stops.sortedBy { it.sequence }.lastOrNull { it.stopId !in boardingIds }?.stopId
            }
            .groupingBy { it }
            .eachCount()
            .maxByOrNull { it.value }
            ?.key
            ?: error("no destination stop found in the release")
    }

    /**
     * Serves the whole release the way a static pack origin does, plus the
     * `/v1/offline/...` spelling an API origin uses.
     */
    private fun releaseServingEngine(
        onRequest: (String) -> Unit = {},
        extra: Map<String, Pair<String, HttpStatusCode>> = emptyMap(),
    ): MockEngine = MockEngine { request ->
        val url = request.url.toString()
        onRequest(url)

        extra.entries.firstOrNull { url.contains(it.key) }?.let { (_, response) ->
            val (body, status) = response
            return@MockEngine respond(
                content = ByteReadChannel(body.encodeToByteArray()),
                status = status,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }

        if (url.endsWith("/manifest.json") || url.endsWith("/v1/offline/manifest")) {
            val body = TestFixtures.read(Release.MANIFEST).encodeToByteArray()
            return@MockEngine respond(
                content = ByteReadChannel(body),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentLength, body.size.toString()),
            )
        }

        val served = Release.manifest.files.values.firstOrNull { url.endsWith(it.path) }
        if (served != null) {
            val body = TestFixtures.readBinary(served.path)
            return@MockEngine respond(
                content = ByteReadChannel(body),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentLength, body.size.toString()),
            )
        }

        respond(content = ByteReadChannel(ByteArray(0)), status = HttpStatusCode.NotFound)
    }

    private fun core(
        packsDirectory: String,
        engine: MockEngine,
        apiBaseUrl: String? = null,
        staticPacksBaseUrl: String? = null,
    ): OdivreloCoreImpl = OdivreloCoreImpl(
        config = CoreConfig(
            apiBaseUrl = apiBaseUrl,
            staticPacksBaseUrl = staticPacksBaseUrl,
            packsDirectory = packsDirectory,
            databasePath = ":memory:",
            languageTag = "el",
        ),
        httpClient = HttpClient(engine),
        driver = createTestDriver(),
        clock = FixedClock(clockInstant),
        retryDelays = PackDownloader.TEST_RETRY_DELAYS,
    )

    @Test
    fun aBundledReleaseAndTheSameReleaseDownloadedGiveIdenticalAnswers() = runTest {
        val date = Release.busiestDate

        // Transport 1: bundled with the application, adopted from disk.
        val bundledDirectory = createTestDirectory("equality-bundled")
        Release.install(bundledDirectory)
        val bundled = core(
            packsDirectory = bundledDirectory,
            engine = MockEngine {
                respond(ByteReadChannel(ByteArray(0)), HttpStatusCode.ServiceUnavailable)
            },
            staticPacksBaseUrl = "https://packs.invalid/data",
        )

        // Transport 2: nothing installed; fetched, verified and installed over HTTP.
        val downloadedDirectory = createTestDirectory("equality-downloaded")
        val requested = mutableListOf<String>()
        val downloaded = core(
            packsDirectory = downloadedDirectory,
            engine = releaseServingEngine(onRequest = { requested += it }),
            staticPacksBaseUrl = "https://packs.example/data",
        )

        try {
            assertTrue(bundled.adoptSeededRelease())

            val fromBundle = bundled.searchJourneys(
                terminalId,
                destinationId,
                date,
                JourneyFilters.NONE,
            )
            val fromNetwork = downloaded.searchJourneys(
                terminalId,
                destinationId,
                date,
                JourneyFilters.NONE,
            )

            assertTrue(requested.isNotEmpty(), "the second core must actually have downloaded")
            assertTrue(
                requested.any { it.contains("manifest") },
                "the manifest must be read before any pack: $requested",
            )
            assertEquals(Release.releaseId, fromNetwork.releaseId)
            assertEquals(DateDataState.FROM_INSTALLED_PACK, fromBundle.dateDataState)
            assertEquals(DateDataState.FROM_INSTALLED_PACK, fromNetwork.dateDataState)

            // Every field, not just the ids: a downloaded pack is the same bytes.
            assertEquals(fromBundle, fromNetwork)

            // And the same for a detail, a station page and the static reads.
            val journeyId = fromBundle.results.first().id
            assertEquals(
                bundled.journeyDetail(journeyId, date),
                downloaded.journeyDetail(journeyId, date),
            )
            val stopId = fromBundle.results.first().departure.stopId
            assertEquals(bundled.stopDetail(stopId, date), downloaded.stopDetail(stopId, date))
            assertEquals(bundled.meta(), downloaded.meta())
            assertEquals(bundled.coverage(), downloaded.coverage())
            assertEquals(bundled.sources(), downloaded.sources())
            assertEquals(
                bundled.searchPlaces("a", 20),
                downloaded.searchPlaces("a", 20),
            )
        } finally {
            bundled.close()
            downloaded.close()
        }
    }

    @Test
    fun theSameReleaseServedFromAnApiOriginInstallsIdentically() = runTest {
        // An API origin serves /v1/offline/manifest and /v1/offline/packs/<file>.
        // Those are the same bytes as a static origin's, so the answer must match.
        val date = Release.busiestDate

        val staticDirectory = createTestDirectory("equality-static-origin")
        val apiDirectory = createTestDirectory("equality-api-origin")

        val fromStatic = core(
            packsDirectory = staticDirectory,
            engine = releaseServingEngine(),
            staticPacksBaseUrl = "https://packs.example/data",
        )
        val fromApi = core(
            packsDirectory = apiDirectory,
            engine = releaseServingEngine(),
            apiBaseUrl = "https://api.example",
        )

        try {
            val a = fromStatic.searchJourneys(terminalId, destinationId, date, JourneyFilters.NONE)
            val b = fromApi.searchJourneys(terminalId, destinationId, date, JourneyFilters.NONE)
            assertEquals(a, b)
            assertEquals(Release.releaseId, a.releaseId)
            assertTrue(a.results.isNotEmpty())
        } finally {
            fromStatic.close()
            fromApi.close()
        }
    }

    @Test
    fun aLiveAnswerForAnUnpublishedDateGoesThroughTheSameFilteringAndOrdering() = runTest {
        // The release publishes no pack for this date, which is normal: only dates
        // the data names are materialised. With an API configured the core asks,
        // and the answer is filtered and ordered by the same code as a pack answer.
        val publishedDate = Release.busiestDate
        val unpublishedDate = Release.unpublishedDate

        val reference = createTestDirectory("equality-live-reference")
        Release.install(reference)
        val referenceCore = core(
            packsDirectory = reference,
            engine = MockEngine {
                respond(ByteReadChannel(ByteArray(0)), HttpStatusCode.ServiceUnavailable)
            },
            staticPacksBaseUrl = "https://packs.invalid/data",
        )

        val packAnswer = try {
            referenceCore.searchJourneys(
                terminalId,
                destinationId,
                publishedDate,
                JourneyFilters.NONE,
            )
        } finally {
            referenceCore.close()
        }

        // The service answers for the unpublished date with the same journeys,
        // restamped onto that date, exactly as /v1/journeys would.
        val liveBody = OdivreloJson.instance.encodeToString(
            dev.peterdsp.odivrelo.core.model.JourneyResults.serializer(),
            packAnswer.copy(
                query = packAnswer.query.copy(date = unpublishedDate),
                results = packAnswer.results.map { it.copy(serviceDate = unpublishedDate) },
                servedFromCache = false,
                cachedReleasePublishedAt = null,
                dateDataState = DateDataState.FROM_INSTALLED_PACK,
            ),
        )

        val liveDirectory = createTestDirectory("equality-live")
        Release.install(liveDirectory)
        val liveCore = core(
            packsDirectory = liveDirectory,
            engine = releaseServingEngine(
                extra = mapOf("/v1/journeys" to (liveBody to HttpStatusCode.OK)),
            ),
            apiBaseUrl = "https://api.example",
        )

        try {
            val live = liveCore.searchJourneys(
                terminalId,
                destinationId,
                unpublishedDate,
                JourneyFilters.NONE,
            )
            assertEquals(DateDataState.FROM_API, live.dateDataState)
            assertFalse(live.servedFromCache)
            assertEquals(packAnswer.results.map { it.id }, live.results.map { it.id })

            // The same filter narrows a live answer exactly as it narrows a pack one.
            val accessibleFromPack = packAnswer.results.filter { it.accessibleBoardingPoint == true }
            val accessibleLive = liveCore.searchJourneys(
                terminalId,
                destinationId,
                unpublishedDate,
                JourneyFilters(accessibleOnly = true),
            )
            assertEquals(
                accessibleFromPack.map { it.id },
                accessibleLive.results.map { it.id },
            )

            // And the same ordering.
            val byDuration = liveCore.searchJourneys(
                terminalId,
                destinationId,
                unpublishedDate,
                JourneyFilters(sort = dev.peterdsp.odivrelo.core.model.JourneySort.DURATION),
            ).results
            assertEquals(
                byDuration.map { it.durationMinutes }.sorted(),
                byDuration.map { it.durationMinutes },
            )
        } finally {
            liveCore.close()
        }
    }

    @Test
    fun aLiveAnswerFromAnotherReleaseIsRefusedRatherThanMixedIn() = runTest {
        val publishedDate = Release.busiestDate
        val unpublishedDate = Release.unpublishedDate

        val reference = createTestDirectory("mixing-reference")
        Release.install(reference)
        val referenceCore = core(
            packsDirectory = reference,
            engine = MockEngine {
                respond(ByteReadChannel(ByteArray(0)), HttpStatusCode.ServiceUnavailable)
            },
            staticPacksBaseUrl = "https://packs.invalid/data",
        )
        val packAnswer = try {
            referenceCore.searchJourneys(terminalId, destinationId, publishedDate, JourneyFilters.NONE)
        } finally {
            referenceCore.close()
        }

        val foreignBody = OdivreloJson.instance.encodeToString(
            dev.peterdsp.odivrelo.core.model.JourneyResults.serializer(),
            packAnswer.copy(
                releaseId = "ffffffffffffffff",
                query = packAnswer.query.copy(date = unpublishedDate),
            ),
        )

        val directory = createTestDirectory("mixing-live")
        Release.install(directory)
        val liveCore = core(
            packsDirectory = directory,
            engine = releaseServingEngine(
                extra = mapOf("/v1/journeys" to (foreignBody to HttpStatusCode.OK)),
            ),
            apiBaseUrl = "https://api.example",
        )

        try {
            val error = kotlin.test.assertFailsWith<OdivreloException> {
                liveCore.searchJourneys(
                    terminalId,
                    destinationId,
                    unpublishedDate,
                    JourneyFilters.NONE,
                )
            }
            assertEquals(OdivreloFailureKind.RELEASE_MISMATCH, error.kind)
            assertTrue(error.message.contains("ffffffffffffffff"))
            assertTrue(error.message.contains(Release.releaseId))
        } finally {
            liveCore.close()
        }
    }

    @Test
    fun withNoApiAnUnpublishedDateSaysNoOfflineDataRatherThanAskingTheNetworkForever() = runTest {
        val directory = createTestDirectory("no-api-unpublished")
        Release.install(directory)
        var requests = 0
        val core = core(
            packsDirectory = directory,
            engine = MockEngine {
                requests++
                respond(ByteReadChannel(ByteArray(0)), HttpStatusCode.ServiceUnavailable)
            },
            staticPacksBaseUrl = "https://packs.invalid/data",
        )
        try {
            val results = core.searchJourneys(
                terminalId,
                destinationId,
                Release.unpublishedDate,
                JourneyFilters.NONE,
            )
            assertEquals(DateDataState.NO_OFFLINE_PACK, results.dateDataState)
            assertTrue(results.results.isEmpty())
            kotlin.test.assertNull(results.unavailableReason)
            // The manifest does not list the date, so there is nothing to download
            // and the core must not go looking for a pack that cannot exist.
            assertEquals(0, requests, "no request should be made for a date the release omits")
        } finally {
            core.close()
        }
    }

    @Test
    fun aFirstRunWithNothingInstalledFetchesTheReleaseAndThenWorksOffline() = runTest {
        val directory = createTestDirectory("first-run-download")
        val engine = releaseServingEngine()
        val first = core(
            packsDirectory = directory,
            engine = engine,
            staticPacksBaseUrl = "https://packs.example/data",
        )
        try {
            val meta = first.meta()
            assertEquals(Release.releaseId, meta.releaseId)
            val installed = first.installedPacks().map { it.name }.toSet()
            assertTrue(
                PackNames.required.all { it in installed },
                "a first run must install every required pack, got $installed",
            )
            // Journeys packs are fetched per date, on demand, so a first search
            // installs the day it needs and nothing else.
            assertTrue(
                Release.serviceDates.none { PackNames.journeys(it) in installed },
                "a first run must not download every published date up front",
            )
            assertTrue(
                first.searchJourneys(
                    terminalId,
                    destinationId,
                    Release.busiestDate,
                    JourneyFilters.NONE,
                ).results.isNotEmpty(),
            )
            val afterSearch = first.installedPacks().map { it.name }.toSet()
            assertTrue(PackNames.journeys(Release.busiestDate) in afterSearch)
            assertEquals(
                1,
                Release.serviceDates.count { PackNames.journeys(it) in afterSearch },
                "only the searched date should have been downloaded",
            )
        } finally {
            first.close()
        }

        // A second core over a dead network reads the release the first one left.
        val offline = core(
            packsDirectory = directory,
            engine = MockEngine {
                respond(ByteReadChannel(ByteArray(0)), HttpStatusCode.ServiceUnavailable)
            },
            staticPacksBaseUrl = "https://packs.example/data",
        )
        try {
            assertEquals(Release.releaseId, offline.meta().releaseId)
            val terminals = offline.searchPlaces("", 10).places
            assertTrue(terminals.isNotEmpty())
            assertTrue(terminals.all { it.kind == PlaceKind.STOP_PLACE })
            assertNotNull(
                offline.searchJourneys(
                    terminalId,
                    destinationId,
                    Release.busiestDate,
                    JourneyFilters.NONE,
                ).results.firstOrNull(),
            )
        } finally {
            offline.close()
        }
    }
}
