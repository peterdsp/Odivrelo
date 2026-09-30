package dev.peterdsp.odivrelo.features

import dev.peterdsp.odivrelo.core.OdivreloException
import dev.peterdsp.odivrelo.core.OdivreloFailureKind
import dev.peterdsp.odivrelo.core.model.ErrorCode
import dev.peterdsp.odivrelo.core.model.JourneyFilters
import dev.peterdsp.odivrelo.core.model.PackFailure
import dev.peterdsp.odivrelo.core.model.PackPhase
import dev.peterdsp.odivrelo.core.model.PackProgress
import dev.peterdsp.odivrelo.core.model.PackResult
import dev.peterdsp.odivrelo.core.model.PlaceKind
import dev.peterdsp.odivrelo.core.model.UnavailableReason
import dev.peterdsp.odivrelo.core.Cancellable
import dev.peterdsp.odivrelo.features.offline.DownloadState
import dev.peterdsp.odivrelo.features.offline.DownloadTracker
import dev.peterdsp.odivrelo.features.search.ChosenPlace
import dev.peterdsp.odivrelo.features.search.SearchEndpoint
import dev.peterdsp.odivrelo.features.search.SearchQuery
import dev.peterdsp.odivrelo.features.settings.AppDataMode
import dev.peterdsp.odivrelo.features.settings.Appearance
import dev.peterdsp.odivrelo.features.settings.OdivreloSettings
import dev.peterdsp.odivrelo.features.settings.SettingsFeature
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FeatureStateTest {

    // -- Failure mapping -----------------------------------------------------

    @Test
    fun aCoreFailureIsMappedByItsKindNotByItsWording() {
        // The message is translated and reworded over time; the kind is not.
        fun failed(kind: OdivreloFailureKind, code: ErrorCode = ErrorCode.UNAVAILABLE) =
            OdivreloException(code, "any wording at all", null, kind).toFailed().reason

        assertEquals(FailureReason.NO_DATA_INSTALLED, failed(OdivreloFailureKind.NO_DATA_INSTALLED))
        assertEquals(FailureReason.NO_DATA_INSTALLED, failed(OdivreloFailureKind.INCOMPLETE_DATA))
        assertEquals(FailureReason.DATA_UNREADABLE, failed(OdivreloFailureKind.UNREADABLE_DATA))
        assertEquals(FailureReason.RELEASE_MISMATCH, failed(OdivreloFailureKind.RELEASE_MISMATCH))
        assertEquals(
            FailureReason.NO_OFFLINE_DATA_FOR_DATE,
            failed(OdivreloFailureKind.NO_OFFLINE_PACK_FOR_DATE),
        )
        assertEquals(FailureReason.OFFLINE, failed(OdivreloFailureKind.NETWORK_UNAVAILABLE))
        assertEquals(FailureReason.STORAGE_FULL, failed(OdivreloFailureKind.STORAGE_FULL))
        assertEquals(FailureReason.NOT_FOUND, failed(OdivreloFailureKind.NOT_FOUND))
        assertEquals(FailureReason.INVALID_REQUEST, failed(OdivreloFailureKind.INVALID_REQUEST))
    }

    @Test
    fun aGeneralFailureFallsBackToTheContractCode() {
        fun failed(code: ErrorCode) =
            OdivreloException(code, "message", null, OdivreloFailureKind.GENERAL).toFailed().reason

        assertEquals(FailureReason.NOT_FOUND, failed(ErrorCode.NOT_FOUND))
        assertEquals(FailureReason.INVALID_REQUEST, failed(ErrorCode.INVALID_REQUEST))
        assertEquals(FailureReason.RELEASE_MISMATCH, failed(ErrorCode.RELEASE_MISMATCH))
        assertEquals(FailureReason.SERVER_ERROR, failed(ErrorCode.UNAUTHORIZED))
        assertEquals(FailureReason.OFFLINE, failed(ErrorCode.UNAVAILABLE))
    }

    @Test
    fun onlyFailuresWorthRetryingOfferARetry() {
        assertTrue(FailureReason.OFFLINE.isRetryable)
        assertTrue(FailureReason.SERVER_ERROR.isRetryable)
        assertTrue(FailureReason.DATA_UNREADABLE.isRetryable)

        // Retrying these produces the same answer, so offering a retry would waste
        // someone's time and their battery.
        assertFalse(FailureReason.NO_OFFLINE_DATA_FOR_DATE.isRetryable)
        assertFalse(FailureReason.RELEASE_MISMATCH.isRetryable)
        assertFalse(FailureReason.NOT_FOUND.isRetryable)
        assertFalse(FailureReason.PERMISSION_DENIED.isRetryable)
        assertFalse(FailureReason.EXPIRED_SERVICE.isRetryable)
    }

    @Test
    fun theTwoAbsencesAreDifferentEmptyReasons() {
        // "This device holds no timetable for that date" and "no service runs that
        // date" are different statements and must stay different states.
        assertTrue(EmptyReason.NO_OFFLINE_DATA_FOR_DATE != EmptyReason.NO_SERVICE_ON_DATE)

        assertEquals(
            EmptyReason.NO_SERVICE_ON_DATE,
            EmptyReason.of(UnavailableReason.NO_SERVICE_ON_DATE),
        )
        assertEquals(
            EmptyReason.OUTSIDE_COVERAGE,
            EmptyReason.of(UnavailableReason.OUTSIDE_COVERAGE),
        )
        assertEquals(
            EmptyReason.ORIGIN_EQUALS_DESTINATION,
            EmptyReason.of(UnavailableReason.ORIGIN_EQUALS_DESTINATION),
        )
        // No stated reason with an empty list means the filters removed everything.
        assertEquals(EmptyReason.FILTERED_OUT, EmptyReason.of(null))
    }

    @Test
    fun loadableKeepsThePreviousValueWhileRefreshing() {
        val ready = Loadable.Ready(listOf("a", "b"))
        assertEquals(listOf("a", "b"), ready.valueOrNull)

        val refreshing = Loadable.Loading(previous = listOf("a", "b"))
        assertTrue(refreshing.isLoading)
        assertEquals(
            listOf("a", "b"),
            refreshing.valueOrNull,
            "a list must not blink away while it refreshes",
        )

        assertNull(Loadable.Idle.valueOrNull)
        assertNull(Loadable.Empty(EmptyReason.NEEDS_INPUT).valueOrNull)
        assertNull(Loadable.Failed(FailureReason.OFFLINE).valueOrNull)
        assertTrue(Loadable.Failed(FailureReason.OFFLINE).isRetryable)
    }

    // -- Search query --------------------------------------------------------

    private fun place(id: String, label: String) =
        ChosenPlace(id = id, label = label, kind = PlaceKind.STOP_PLACE)

    @Test
    fun aSearchIsOnlyRunnableWithTwoDifferentEndpoints() {
        val empty = SearchQuery(serviceDate = "2026-10-02")
        assertFalse(empty.isRunnable)

        val halfFilled = empty.with(SearchEndpoint.ORIGIN, place("a", "A"))
        assertFalse(halfFilled.isRunnable)

        val same = halfFilled.with(SearchEndpoint.DESTINATION, place("a", "A"))
        assertFalse(same.isRunnable)
        assertTrue(same.isSameEndpoint)

        val ready = halfFilled.with(SearchEndpoint.DESTINATION, place("b", "B"))
        assertTrue(ready.isRunnable)
        assertFalse(ready.isSameEndpoint)
    }

    @Test
    fun swappingEndpointsKeepsTheDateAndTheFilters() {
        val query = SearchQuery(
            origin = place("a", "A"),
            destination = place("b", "B"),
            serviceDate = "2026-10-02",
            filters = JourneyFilters(accessibleOnly = true),
        )
        val swapped = query.swapped()
        assertEquals("b", swapped.origin?.id)
        assertEquals("a", swapped.destination?.id)
        assertEquals("2026-10-02", swapped.serviceDate)
        assertTrue(swapped.filters.accessibleOnly)
    }

    @Test
    fun shiftingTheDateGoesThroughTheCoresCalendarNotStringArithmetic() {
        val query = SearchQuery(serviceDate = "2026-10-31")
        assertEquals("2026-11-01", query.shiftDate(1).serviceDate)
        assertEquals("2026-10-30", query.shiftDate(-1).serviceDate)
        assertEquals("2027-01-01", SearchQuery(serviceDate = "2026-12-31").shiftDate(1).serviceDate)
        // A leap day is a calendar fact, not a string one.
        assertEquals("2028-02-29", SearchQuery(serviceDate = "2028-02-28").shiftDate(1).serviceDate)
    }

    @Test
    fun clearingAnEndpointIsAllowedAndMakesTheSearchUnrunnable() {
        val query = SearchQuery(
            origin = place("a", "A"),
            destination = place("b", "B"),
            serviceDate = "2026-10-02",
        )
        assertTrue(query.isRunnable)
        assertFalse(query.with(SearchEndpoint.ORIGIN, null).isRunnable)
    }

    // -- Settings ------------------------------------------------------------

    @Test
    fun anUnsupportedLanguageFallsBackToGreekRatherThanShowingKeys() {
        val settings = OdivreloSettings()
        assertEquals("el", SettingsFeature.withLanguage(settings, "de").languageTag)
        assertEquals("en", SettingsFeature.withLanguage(settings, "en-GB").languageTag)
        assertEquals("sq", SettingsFeature.withLanguage(settings, "SQ").languageTag)
        assertEquals("el", SettingsFeature.withLanguage(settings, "").languageTag)
    }

    @Test
    fun onlyAnOfferedReminderLeadTimeIsAccepted() {
        val settings = OdivreloSettings()
        assertEquals(60, SettingsFeature.withReminderLead(settings, 60).reminderLeadMinutes)
        // An arbitrary number is no option any interface can show as chosen.
        assertEquals(
            settings.reminderLeadMinutes,
            SettingsFeature.withReminderLead(settings, 7).reminderLeadMinutes,
        )
    }

    @Test
    fun turningRemindersOffKeepsTheChosenLeadTime() {
        val chosen = SettingsFeature.withReminderLead(OdivreloSettings(), 90)
        val off = SettingsFeature.withReminders(chosen, false)
        assertFalse(off.remindersEnabled)
        assertEquals(90, off.reminderLeadMinutes)
        assertTrue(SettingsFeature.withReminders(off, true).remindersEnabled)
        assertEquals(90, SettingsFeature.withReminders(off, true).reminderLeadMinutes)
    }

    @Test
    fun settingsReadBackFromStorageAreRepairedRatherThanTrusted() {
        val broken = OdivreloSettings(languageTag = "de", reminderLeadMinutes = 7)
        val repaired = SettingsFeature.sanitised(broken)
        assertEquals("el", repaired.languageTag)
        assertEquals(OdivreloSettings.DEFAULT_REMINDER_LEAD_MINUTES, repaired.reminderLeadMinutes)

        val fine = OdivreloSettings(languageTag = "sq", reminderLeadMinutes = 30)
        assertEquals(fine, SettingsFeature.sanitised(fine))
    }

    @Test
    fun onlyTheDataModeControlsTheDemonstrationNotice() {
        val demo = OdivreloSettings(dataMode = AppDataMode.DEMO)
        assertTrue(demo.showsDemoNotice)

        val real = SettingsFeature.withDataMode(demo, AppDataMode.REAL)
        assertFalse(real.showsDemoNotice)
        // Flipping the mode changes nothing else at all.
        assertEquals(demo.copy(dataMode = AppDataMode.REAL), real)

        assertEquals(
            demo,
            SettingsFeature.withDataMode(real, AppDataMode.DEMO),
            "flipping back must restore exactly the same settings",
        )
    }

    @Test
    fun appearanceAndAccessibilityTogglesAreIndependent() {
        var settings = OdivreloSettings()
        settings = SettingsFeature.withAppearance(settings, Appearance.DARK)
        settings = SettingsFeature.withLargerTouchTargets(settings, true)
        settings = SettingsFeature.withReduceMotion(settings, true)
        settings = SettingsFeature.withAlwaysExpandStops(settings, true)
        settings = SettingsFeature.completingFirstRun(settings)

        assertEquals(Appearance.DARK, settings.appearance)
        assertTrue(settings.largerTouchTargets)
        assertTrue(settings.reduceMotion)
        assertTrue(settings.alwaysExpandStops)
        assertTrue(settings.hasCompletedFirstRun)
        assertEquals("el", settings.resolvedLanguageTag)
    }

    // -- Download tracking ---------------------------------------------------

    private class FakeCancellable : Cancellable {
        override var isCancelled: Boolean = false
        override fun cancel() {
            isCancelled = true
        }
    }

    @Test
    fun aRebuildOfTheScreenDoesNotStartASecondDownload() {
        val tracker = DownloadTracker()
        var launches = 0
        val handle = FakeCancellable()

        fun start(): Boolean = tracker.start(
            packName = "stops",
            totalBytes = 1000,
            onChanged = {},
            launch = { _, _ ->
                launches++
                handle
            },
        )

        assertTrue(start(), "the first tap starts a download")
        assertEquals(1, launches)

        // A rotation, a fold or a split-screen resize rebuilds the screen. It must
        // find the running download rather than start another one.
        assertFalse(start(), "a rebuild must not start a second download")
        assertEquals(1, launches)
        assertTrue(tracker.isRunning("stops"))
    }

    @Test
    fun progressAndResultFlowThroughToTheTrackedState() {
        val tracker = DownloadTracker()
        val seen = mutableListOf<DownloadState>()
        var progress: ((PackProgress) -> Unit)? = null
        var result: ((PackResult) -> Unit)? = null

        tracker.start(
            packName = "places",
            totalBytes = 2466,
            onChanged = { seen += it },
            launch = { onProgress, onResult ->
                progress = onProgress
                result = onResult
                FakeCancellable()
            },
        )

        progress!!(PackProgress("places", 1233, 2466, PackPhase.DOWNLOADING, 1))
        val half = tracker.stateOf("places")!!
        assertEquals(50, half.percent)
        assertTrue(half.isRunning)
        assertFalse(half.finished)

        progress!!(PackProgress("places", 2466, 2466, PackPhase.VERIFYING, 1))
        assertEquals(PackPhase.VERIFYING, tracker.stateOf("places")!!.phase)

        result!!(PackResult("places", succeeded = true, attempts = 1))
        val done = tracker.stateOf("places")!!
        assertTrue(done.finished)
        assertTrue(done.succeeded)
        assertEquals(100, done.percent)
        assertFalse(tracker.isRunning("places"))
        assertTrue(seen.size >= 4)
    }

    @Test
    fun cancellingIsIdempotentAndReportedAsCancelledNotAsAnError() {
        val tracker = DownloadTracker()
        val handle = FakeCancellable()
        tracker.start("stops", 100, {}, { _, _ -> handle })

        tracker.cancel("stops")
        assertTrue(handle.isCancelled)
        val state = tracker.stateOf("stops")!!
        assertTrue(state.wasCancelled)
        assertTrue(state.finished)
        assertFalse(state.succeeded)
        // Cancelling is not an error, so there is nothing to apologise for.
        assertNull(state.failureReason)
        // And it can be worth resuming.
        assertTrue(state.isRetryable)

        tracker.cancel("stops")
        assertFalse(tracker.isRunning("stops"))
    }

    @Test
    fun aDigestFailureIsNotOfferedAsSomethingToRetry() {
        val bad = DownloadState.from(
            PackResult("journeys-2026-10-02", succeeded = false, failure = PackFailure.DIGEST_MISMATCH),
            totalBytes = 15_883,
        )
        assertFalse(bad.isRetryable, "the same mirror would return the same bad bytes")
        assertEquals(FailureReason.DATA_UNREADABLE, bad.failureReason)

        val mixed = DownloadState.from(
            PackResult("stops", succeeded = false, failure = PackFailure.RELEASE_MISMATCH),
            totalBytes = 1,
        )
        assertFalse(mixed.isRetryable)
        assertEquals(FailureReason.RELEASE_MISMATCH, mixed.failureReason)

        val network = DownloadState.from(
            PackResult("stops", succeeded = false, failure = PackFailure.NETWORK, attempts = 3),
            totalBytes = 1,
        )
        assertTrue(network.isRetryable)
        assertEquals(FailureReason.OFFLINE, network.failureReason)
        assertEquals(3, network.attempt)
    }

    @Test
    fun aResumeIsVisibleAsAResumeNotAsAFreshDownload() {
        val resuming = DownloadState.from(
            PackProgress("stops", 5_000, 11_153, PackPhase.RESUMING, attempt = 2),
        )
        assertTrue(resuming.isResuming)
        assertTrue(resuming.isRetrying)
        assertEquals(44, resuming.percent)
    }

    @Test
    fun progressWithNoDeclaredLengthHasNoPercentage() {
        val unknown = DownloadState.from(PackProgress("stops", 5_000, 0, PackPhase.DOWNLOADING))
        assertNull(unknown.fraction)
        assertNull(unknown.percent)
    }

    @Test
    fun finishedDownloadsCanBeClearedButRunningOnesCannot() {
        val tracker = DownloadTracker()
        tracker.start("stops", 100, {}, { _, _ -> FakeCancellable() })
        tracker.clear("stops")
        assertTrue(
            tracker.stateOf("stops") != null,
            "a running download must not be cleared out from under itself",
        )

        tracker.cancel("stops")
        tracker.clearFinished()
        assertNull(tracker.stateOf("stops"))
    }
}
