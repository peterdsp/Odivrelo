package dev.peterdsp.odivrelo.core

import dev.peterdsp.odivrelo.core.model.ErrorCode
import dev.peterdsp.odivrelo.core.model.JourneyFilters
import dev.peterdsp.odivrelo.core.packs.PackDownloader
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.utils.io.ByteReadChannel
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant

/**
 * The state every installation starts in: nothing downloaded, and possibly no
 * network either.
 *
 * This was the untested case that let a fresh iOS install terminate on launch.
 * The first call into the core threw, the exception was not in any `@Throws`
 * list, and the Kotlin/Native runtime killed the process before Swift saw it.
 *
 * The annotations are asserted by [ExportedApiContractTest]. This is the other
 * half of the guarantee: every exported member, on a core in that state, either
 * answers or throws a [OdivreloException] and never any other type. Declaring
 * `@Throws(OdivreloException::class)` is only safe because of this.
 */
class FirstRunTest {

    private val now = Instant.parse("2026-09-30T09:00:00Z")

    /** A core with no packs installed and an origin that answers nothing. */
    private fun freshInstall(
        apiBaseUrl: String? = null,
        staticPacksBaseUrl: String? = "https://packs.invalid/data",
    ): OdivreloCoreImpl = OdivreloCoreImpl(
        config = CoreConfig(
            apiBaseUrl = apiBaseUrl,
            staticPacksBaseUrl = staticPacksBaseUrl,
            packsDirectory = createTestDirectory("first-run"),
            databasePath = ":memory:",
            languageTag = "el",
        ),
        httpClient = HttpClient(
            MockEngine {
                respond(ByteReadChannel(ByteArray(0)), HttpStatusCode.ServiceUnavailable)
            },
        ),
        driver = createTestDriver(),
        clock = FixedClock(now),
        retryDelays = PackDownloader.TEST_RETRY_DELAYS,
    )

    /**
     * Runs an exported call and reports what came back: a value, a typed failure,
     * or something that would terminate the process on iOS.
     */
    private suspend fun outcomeOf(call: suspend () -> Any?): Outcome = try {
        Outcome.Answered(call())
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (expected: OdivreloException) {
        Outcome.Typed(expected)
    } catch (error: Throwable) {
        Outcome.Untyped(error)
    }

    private sealed interface Outcome {
        data class Answered(val value: Any?) : Outcome
        data class Typed(val failure: OdivreloException) : Outcome
        data class Untyped(val error: Throwable) : Outcome
    }

    @Test
    fun metaOnAFreshInstallFailsWithATypedErrorRatherThanKillingTheProcess() {
        runTest {
            val core = freshInstall()
            try {
                val outcome = outcomeOf { core.meta() }
                val typed = outcome as? Outcome.Typed
                assertNotNull(
                    typed,
                    "meta() on a fresh install produced $outcome. Anything but a " +
                        "OdivreloException is outside the @Throws list and terminates " +
                        "the process on Kotlin/Native.",
                )
                assertEquals(ErrorCode.UNAVAILABLE, typed.failure.code)
                assertEquals(OdivreloFailureKind.NO_DATA_INSTALLED, typed.failure.kind)
                assertTrue(typed.failure.message.isNotBlank())
            } finally {
                core.close()
            }
        }
    }

    @Test
    fun searchOnAFreshInstallFailsWithATypedError() {
        runTest {
            val core = freshInstall()
            try {
                val places = outcomeOf { core.searchPlaces("Aloria", 10) }
                assertTrue(
                    places is Outcome.Typed,
                    "searchPlaces on a fresh install produced $places",
                )

                val journeys = outcomeOf {
                    core.searchJourneys("any-origin", "any-destination", "2026-10-02", JourneyFilters.NONE)
                }
                assertTrue(
                    journeys is Outcome.Typed,
                    "searchJourneys on a fresh install produced $journeys",
                )
                assertEquals(
                    OdivreloFailureKind.NO_DATA_INSTALLED,
                    (journeys as Outcome.Typed).failure.kind,
                )
            } finally {
                core.close()
            }
        }
    }

    @Test
    fun noExportedMemberEverThrowsSomethingOutsideItsThrowsList() {
        runTest {
            val core = freshInstall()
            try {
                // Every exported suspending member, called on a core that cannot
                // answer most of them. This is the call pattern a first launch
                // actually produces.
                val calls: List<Pair<String, suspend () -> Any?>> = listOf(
                    "meta" to { core.meta() },
                    "coverage" to { core.coverage() },
                    "sources" to { core.sources() },
                    "searchPlaces" to { core.searchPlaces("a", 10) },
                    "searchJourneys" to {
                        core.searchJourneys("a", "b", "2026-10-02", JourneyFilters.NONE)
                    },
                    "journeyDetail" to { core.journeyDetail("any", "2026-10-02") },
                    "operatorDetail" to { core.operatorDetail("any") },
                    "stopDetail" to { core.stopDetail("any", "2026-10-02") },
                    "savedTrips" to { core.savedTrips() },
                    "saveTrip" to { core.saveTrip("any", "2026-10-02") },
                    "removeSavedTrip" to { core.removeSavedTrip("any") },
                    "favorites" to { core.favorites() },
                    "toggleFavorite" to { core.toggleFavorite("any") },
                    "recentSearches" to { core.recentSearches() },
                    "offlineCatalog" to { core.offlineCatalog() },
                    "installedPacks" to { core.installedPacks() },
                    "removePack" to { core.removePack("meta") },
                    "rollbackToPreviousRelease" to { core.rollbackToPreviousRelease() },
                    "savedTripDetail" to { core.savedTripDetail("any") },
                    "adoptSeededRelease" to { core.adoptSeededRelease() },
                )

                val untyped = mutableListOf<String>()
                calls.forEach { (name, call) ->
                    when (val outcome = outcomeOf(call)) {
                        is Outcome.Untyped ->
                            untyped += name + " threw " + outcome.error::class.simpleName +
                                ": " + outcome.error.message

                        is Outcome.Typed, is Outcome.Answered -> Unit
                    }
                }

                assertTrue(
                    untyped.isEmpty(),
                    "These exported members threw something outside their @Throws " +
                        "list. On iOS each one terminates the process instead of " +
                        "reaching Swift:\n" + untyped.joinToString("\n") { "  " + it },
                )

                // And the number of calls checked must keep pace with the exported
                // surface, so adding a member without covering it here fails.
                assertEquals(
                    ExportedApiFacts.members.count { it.isSuspend },
                    calls.size,
                    "The exported suspending surface changed. Add the new member to " +
                        "this list so a fresh install still exercises all of it.",
                )
            } finally {
                core.close()
            }
        }
    }

    @Test
    fun theCallsThatOwnNoPublicDataStillWorkOnAFreshInstall() {
        runTest {
            val core = freshInstall()
            try {
                // These read only what belongs to the person, so they answer
                // normally even with nothing installed. A first launch must be able
                // to open its saved screens without an error.
                assertTrue(core.savedTrips().isEmpty())
                assertTrue(core.favorites().isEmpty())
                assertTrue(core.recentSearches().isEmpty())
                assertTrue(core.installedPacks().isEmpty())
                assertNull(core.savedTripDetail("any"))
                assertFalse(core.adoptSeededRelease())

                // And the offline screen opens, which is where someone goes to fix
                // the situation. It reports the origin as unreachable rather than
                // pretending.
                val catalogue = core.offlineCatalog()
                assertFalse(catalogue.manifestReachable)
                assertTrue(catalogue.installed.isEmpty())
                assertEquals("", catalogue.releaseId)
            } finally {
                core.close()
            }
        }
    }

    @Test
    fun anUnusableConfigurationIsATypedFailureRatherThanAFailedConstruction() {
        // CoreConfig's constructor does not throw on purpose: a throwing
        // initialiser across the Objective-C boundary terminates the process for
        // the same reason a missing @Throws does. Construction always succeeds and
        // the factory reports the problem.
        val blankPacks = CoreConfig(null, null, "   ", "/tmp/db", "el")
        assertFalse(blankPacks.isValid)
        assertNotNull(blankPacks.validationError)

        val blankDatabase = CoreConfig(null, null, "/tmp/packs", "", "el")
        assertFalse(blankDatabase.isValid)

        val blankLanguage = CoreConfig(null, null, "/tmp/packs", "/tmp/db", "")
        assertFalse(blankLanguage.isValid)

        val usable = CoreConfig(null, null, "/tmp/packs", "/tmp/db", "el")
        assertTrue(usable.isValid)
        assertNull(usable.validationError)

        listOf(blankPacks, blankDatabase, blankLanguage).forEach { config ->
            val failure = kotlin.test.assertFailsWith<OdivreloException> {
                requireValidConfig(config)
            }
            assertEquals(ErrorCode.INVALID_REQUEST, failure.code)
            assertEquals(OdivreloFailureKind.INVALID_REQUEST, failure.kind)
            assertTrue(failure.message.isNotBlank())
        }
        requireValidConfig(usable)
    }

    @Test
    fun aFreshInstallWithNoNetworkAtAllStillReportsHonestlyRatherThanHanging() {
        runTest {
            // No pack origin and no API: the most constrained state there is.
            val core = OdivreloCoreImpl(
                config = CoreConfig(
                    apiBaseUrl = null,
                    staticPacksBaseUrl = null,
                    packsDirectory = createTestDirectory("first-run-no-origin"),
                    databasePath = ":memory:",
                    languageTag = "el",
                ),
                httpClient = HttpClient(
                    MockEngine { error("the core must not reach the network with no origin") },
                ),
                driver = createTestDriver(),
                clock = FixedClock(now),
                retryDelays = PackDownloader.TEST_RETRY_DELAYS,
            )
            try {
                val failure = kotlin.test.assertFailsWith<OdivreloException> { core.meta() }
                assertEquals(OdivreloFailureKind.NO_DATA_INSTALLED, failure.kind)
                // The offline screen still opens and says the origin is unreachable.
                assertFalse(core.offlineCatalog().manifestReachable)
            } finally {
                core.close()
            }
        }
    }

    @Test
    fun cancellationIsNotTurnedIntoAFailure() {
        runTest {
            val core = freshInstall()
            try {
                // Cancellation is declared alongside OdivreloException and must be
                // re-thrown as itself, not swallowed into a typed failure, or the
                // coroutine machinery above it stops working.
                val thrown = kotlin.test.assertFailsWith<CancellationException> {
                    core.savedTripsOrCancel()
                }
                assertTrue(thrown.message?.contains("cancelled") == true)
            } finally {
                core.close()
            }
        }
    }

    /** Cancels inside the guarded region, to prove cancellation crosses it intact. */
    private suspend fun OdivreloCoreImpl.savedTripsOrCancel(): Nothing {
        savedTrips()
        throw CancellationException("cancelled while reading")
    }
}
