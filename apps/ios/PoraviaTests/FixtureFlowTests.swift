#if DEBUG
import Foundation
import Testing
@testable import Poravia

/// Exercises every flow against the Debug fixture, including the failure
/// scenarios the interface has to present.
@Suite("Fixture flows")
struct FixtureFlowTests {
    static let date = ServiceDate(iso: "2026-10-02")!
    static let origin = "place.kentro-terminal"
    static let destination = "place.vathia-harbour"

    @Test("Meta reports demonstration data")
    func metaIsDemonstrationData() async throws {
        let core = FixtureCoreClient(scenario: .normal)
        let meta = try await core.meta()
        #expect(meta.envelope.dataMode == .demo)
        #expect(meta.envelope.dataMode.requiresDemoNotice)
        #expect(meta.envelope.contractVersion == Brand.contractVersion)
        #expect(meta.languages == Brand.languageTags)
    }

    @Test("Coverage always states what is not covered")
    func coverageStatesWhatIsNotCovered() async throws {
        let core = FixtureCoreClient(scenario: .normal)
        let coverage = try await core.coverage()
        #expect(!coverage.notCovered.isEmpty)
        #expect(
            coverage.notCovered.contains {
                ($0.en ?? "").lowercased().contains("no ticket sales")
            }
        )
        // Each statement is a sentence a person reads, so it has to exist in
        // all three languages. An English-only statement would silently become
        // the Greek and Albanian text too.
        for statement in coverage.notCovered {
            #expect(statement.el?.isEmpty == false)
            #expect(statement.en?.isEmpty == false)
            #expect(statement.sq?.isEmpty == false)
        }
    }

    @Test("A place search finds terminals and their boarding points")
    func placeSearchFindsBoth() async throws {
        let core = FixtureCoreClient(scenario: .normal)
        let result = try await core.searchPlaces(query: "Aloria", limit: 50)
        #expect(!result.places.isEmpty)
        #expect(result.places.contains { $0.kind == .stopPlace })
        #expect(result.places.contains { $0.kind == .stop })
    }

    @Test("Place search is accent and case insensitive")
    func placeSearchFolds() async throws {
        let core = FixtureCoreClient(scenario: .normal)
        let plain = try await core.searchPlaces(query: "μερανθη", limit: 20)
        let accented = try await core.searchPlaces(query: "Μέρανθη", limit: 20)
        #expect(!plain.places.isEmpty)
        #expect(plain.places.map(\.id) == accented.places.map(\.id))
    }

    @Test("A journey search returns sorted results")
    func journeySearchIsSorted() async throws {
        let core = FixtureCoreClient(scenario: .normal)
        let result = try await core.searchJourneys(
            originId: Self.origin, destinationId: Self.destination,
            serviceDate: Self.date, filters: .none
        )
        #expect(!result.results.isEmpty)
        #expect(result.results.map(\.departure.at).sorted() == result.results.map(\.departure.at))
        #expect(result.query.date == Self.date)
    }

    @Test("A journey that crosses midnight keeps the departure service date")
    func overnightKeepsItsServiceDate() async throws {
        let core = FixtureCoreClient(scenario: .normal)
        let detail = try await core.journeyDetail(journeyId: "jny.acl-2340", serviceDate: Self.date)
        #expect(detail.journey.crossesMidnight)
        #expect(detail.journey.serviceDate == Self.date, "the service date must not follow the arrival")
        #expect(detail.journey.arrival.at > detail.journey.departure.at)
        // The arrival really is on the next calendar day.
        let arrivalDate = ServiceDate.fromPicker(detail.journey.arrival.at)
        #expect(arrivalDate > Self.date)
    }

    @Test("An overnight journey states the crossing as a restriction")
    func overnightStatesARestriction() async throws {
        let core = FixtureCoreClient(scenario: .normal)
        let detail = try await core.journeyDetail(journeyId: "jny.acl-2340", serviceDate: Self.date)
        #expect(detail.journey.restrictions.contains { $0.code == "overnight" })
    }

    @Test("Every journey states that live tracking is unavailable")
    func everyJourneyStatesScheduledOnly() async throws {
        let core = FixtureCoreClient(scenario: .normal)
        for id in ["jny.acl-0700", "jny.mrc-1215", "jny.acl-2340"] {
            let detail = try await core.journeyDetail(journeyId: id, serviceDate: Self.date)
            #expect(detail.journey.positionQuality == .scheduled)
            #expect(detail.journey.restrictions.contains { $0.code == "no_live_tracking" })
        }
    }

    @Test("An unreviewed step-free state is nil, never false")
    func unreviewedStepFreeIsNil() async throws {
        let core = FixtureCoreClient(scenario: .normal)
        let unreviewed = try await core.journeyDetail(journeyId: "jny.mrc-1215", serviceDate: Self.date)
        #expect(unreviewed.journey.boardingPoint?.stepFree == nil)
        let reviewedNo = try await core.journeyDetail(journeyId: "jny.acl-1830", serviceDate: Self.date)
        #expect(reviewedNo.journey.boardingPoint?.stepFree == false)
    }

    @Test("Route geometry is never labelled as the real road")
    func geometryIsHonest() async throws {
        let core = FixtureCoreClient(scenario: .normal)
        let detail = try await core.journeyDetail(journeyId: "jny.acl-0700", serviceDate: Self.date)
        let geometry = try #require(detail.journey.geometry)
        #expect(geometry.confidence == .orderedStopsOnly)
        #expect(geometry.confidence != .reviewed)
        #expect(geometry.attribution != nil)
    }

    @Test("The accessibility filter is applied")
    func accessibilityFilterApplies() async throws {
        let core = FixtureCoreClient(scenario: .normal)
        var filters = JourneyFilters.none
        filters.accessibleOnly = true
        let result = try await core.searchJourneys(
            originId: Self.origin, destinationId: "place.orthia-highlands",
            serviceDate: Self.date, filters: filters
        )
        #expect(result.results.allSatisfy { $0.accessibleBoardingPoint == true })
    }

    @Test("Excluding overnight journeys removes them")
    func overnightFilterApplies() async throws {
        let core = FixtureCoreClient(scenario: .normal)
        var filters = JourneyFilters.none
        filters.includeCrossesMidnight = false
        let result = try await core.searchJourneys(
            originId: Self.origin, destinationId: "place.selvia-port",
            serviceDate: Self.date, filters: filters
        )
        #expect(result.results.allSatisfy { !$0.crossesMidnight })
    }

    @Test("Origin equal to destination is its own stated reason")
    func sameOriginAndDestination() async throws {
        let core = FixtureCoreClient(scenario: .normal)
        let result = try await core.searchJourneys(
            originId: Self.origin, destinationId: Self.origin,
            serviceDate: Self.date, filters: .none
        )
        #expect(result.results.isEmpty)
        #expect(result.unavailableReason == .originEqualsDestination)
    }

    @Test("Saving a trip captures the release it came from")
    func savedTripCapturesItsRelease() async throws {
        let core = FixtureCoreClient(scenario: .normal)
        let trip = try await core.saveTrip(journeyId: "jny.acl-0700", serviceDate: Self.date)
        #expect(trip.releaseId == AloriaFixture.releaseId)
        #expect(trip.serviceDate == Self.date)
        #expect(!trip.originName.isEmpty)
        #expect(try await core.savedTrips().count == 1)
    }

    @Test("Saving the same trip twice does not duplicate it")
    func savingTwiceDoesNotDuplicate() async throws {
        let core = FixtureCoreClient(scenario: .normal)
        _ = try await core.saveTrip(journeyId: "jny.acl-0700", serviceDate: Self.date)
        _ = try await core.saveTrip(journeyId: "jny.acl-0700", serviceDate: Self.date)
        #expect(try await core.savedTrips().count == 1)
    }

    @Test("Removing a saved trip removes exactly it")
    func removingASavedTrip() async throws {
        let core = FixtureCoreClient(scenario: .normal)
        let first = try await core.saveTrip(journeyId: "jny.acl-0700", serviceDate: Self.date)
        _ = try await core.saveTrip(journeyId: "jny.acl-1830", serviceDate: Self.date)
        try await core.removeSavedTrip(savedTripId: first.id)
        let remaining = try await core.savedTrips()
        #expect(remaining.count == 1)
        #expect(remaining.first?.journeyId == "jny.acl-1830")
    }

    @Test("Favourites toggle on and off")
    func favouritesToggle() async throws {
        let core = FixtureCoreClient(scenario: .normal)
        #expect(try await core.toggleFavorite(placeId: Self.origin))
        #expect(try await core.favorites().count == 1)
        #expect(try await core.toggleFavorite(placeId: Self.origin) == false)
        #expect(try await core.favorites().isEmpty)
    }

    @Test("A search is recorded as a recent search")
    func searchesBecomeRecents() async throws {
        let core = FixtureCoreClient(scenario: .normal)
        _ = try await core.searchJourneys(
            originId: Self.origin, destinationId: Self.destination,
            serviceDate: Self.date, filters: .none
        )
        let recents = try await core.recentSearches()
        #expect(recents.count == 1)
        #expect(recents.first?.originId == Self.origin)
    }

    @Test("Repeating a search does not add a second recent entry")
    func repeatedSearchesDoNotStack() async throws {
        let core = FixtureCoreClient(scenario: .normal)
        for _ in 0..<3 {
            _ = try await core.searchJourneys(
                originId: Self.origin, destinationId: Self.destination,
                serviceDate: Self.date, filters: .none
            )
        }
        #expect(try await core.recentSearches().count == 1)
    }

    // MARK: Failure scenarios

    @Test("Offline is reported as offline")
    func offlineScenario() async {
        let core = FixtureCoreClient(scenario: .offline)
        await #expect(throws: CoreError.offline) { try await core.meta() }
    }

    @Test("A release mismatch is its own error")
    func releaseMismatchScenario() async {
        let core = FixtureCoreClient(scenario: .releaseMismatch)
        await #expect(throws: CoreError.releaseMismatch) { try await core.meta() }
    }

    @Test("A missing core is reported as a missing core")
    func coreUnavailableScenario() async {
        let core = FixtureCoreClient(scenario: .coreUnavailable)
        do {
            _ = try await core.meta()
            Issue.record("expected the missing-core error")
        } catch let error as CoreError {
            guard case .coreUnavailable = error else {
                Issue.record("expected coreUnavailable, got \(error)")
                return
            }
        } catch {
            Issue.record("unexpected error \(error)")
        }
    }

    @Test("An uncovered corridor says so rather than returning nothing silently")
    func notCoveredScenario() async throws {
        let core = FixtureCoreClient(scenario: .notCovered)
        let result = try await core.searchJourneys(
            originId: Self.origin, destinationId: Self.destination,
            serviceDate: Self.date, filters: .none
        )
        #expect(result.results.isEmpty)
        #expect(result.coverage == .notCovered)
        #expect(result.unavailableReason == .outsideCoverage)
    }

    @Test("Partial coverage is reported as partial")
    func partialCoverageScenario() async throws {
        let core = FixtureCoreClient(scenario: .partialCoverage)
        let result = try await core.searchJourneys(
            originId: Self.origin, destinationId: Self.destination,
            serviceDate: Self.date, filters: .none
        )
        #expect(result.coverage == .partial)
        #expect(!result.results.isEmpty, "partial coverage still returns what was reviewed")
    }

    @Test("A stale release is labelled stale")
    func staleReleaseScenario() async throws {
        let core = FixtureCoreClient(scenario: .staleRelease)
        let result = try await core.searchJourneys(
            originId: Self.origin, destinationId: Self.destination,
            serviceDate: Self.date, filters: .none
        )
        #expect(result.results.allSatisfy { $0.freshness.state == .stale })
    }

    @Test("An answer served from cache says so, with the cached release's age")
    func servedFromCacheScenario() async throws {
        let core = FixtureCoreClient(scenario: .servedFromCache)
        let result = try await core.searchJourneys(
            originId: Self.origin, destinationId: Self.destination,
            serviceDate: Self.date, filters: .none
        )
        #expect(result.envelope.servedFromCache)
        #expect(result.cachedReleasePublishedAt != nil)
        #expect(result.envelope.ageInDays() != nil)
    }

    // MARK: Offline packs

    @Test("The catalog states what is and is not available offline")
    func catalogStatesAvailability() async throws {
        let core = FixtureCoreClient(scenario: .normal)
        let catalog = try await core.offlineCatalog()
        #expect(catalog.mapAvailability.searchAvailable)
        #expect(catalog.mapAvailability.stopCoordinatesAvailable)
        // Base map imagery is not part of a pack, and the catalog says so.
        #expect(!catalog.mapAvailability.baseMapTilesAvailable)
        #expect(catalog.manifestReachable)
        #expect(!catalog.available.isEmpty)
    }

    @Test("A pack download reaches verify, install and done")
    func downloadRunsThroughItsPhases() async throws {
        let core = FixtureCoreClient(scenario: .normal)
        let catalog = try await core.offlineCatalog()
        let pack = try #require(catalog.available.first)

        let phases = Mutex<[PackPhase]>([])
        let outcome: PackResult = try await withCheckedThrowingContinuation { continuation in
            _ = core.downloadPack(
                packName: pack.name,
                onProgress: { progress in phases.withLock { $0.append(progress.phase) } },
                onResult: { continuation.resume(returning: $0) }
            )
        }

        #expect(outcome.succeeded)
        #expect(outcome.installed?.name == pack.name)
        let seen = phases.withLock { Set($0) }
        #expect(seen.contains(.downloading))
        #expect(seen.contains(.verifying), "a verify step must be visible, not hidden")
        #expect(seen.contains(.installing), "an atomic install must be visible")
        #expect(try await core.installedPacks().count == 1)
    }

    @Test("A corrupt download fails its digest check and installs nothing")
    func corruptDownloadInstallsNothing() async throws {
        let core = FixtureCoreClient(scenario: .corruptDownload)
        let catalog = try await core.offlineCatalog()
        let pack = try #require(catalog.available.first)

        let outcome: PackResult = try await withCheckedThrowingContinuation { continuation in
            _ = core.downloadPack(
                packName: pack.name,
                onProgress: { _ in },
                onResult: { continuation.resume(returning: $0) }
            )
        }

        #expect(!outcome.succeeded)
        #expect(outcome.failure == .digestMismatch)
        #expect(outcome.installed == nil)
        #expect(try await core.installedPacks().isEmpty, "a failed digest must install nothing")
    }

    @Test("An interrupted download resumes on the next attempt")
    func interruptedDownloadResumes() async throws {
        let core = FixtureCoreClient(scenario: .interruptedDownload)
        let catalog = try await core.offlineCatalog()
        let pack = try #require(catalog.available.first)

        func run() async -> PackResult {
            await withCheckedContinuation { continuation in
                _ = core.downloadPack(
                    packName: pack.name,
                    onProgress: { _ in },
                    onResult: { continuation.resume(returning: $0) }
                )
            }
        }

        let first = await run()
        #expect(!first.succeeded)
        #expect(first.failure == .network)
        #expect(first.attempts == 1)

        let second = await run()
        #expect(second.succeeded, "the retry must resume rather than start over and fail again")
        #expect(second.attempts == 2)
        #expect(try await core.installedPacks().count == 1)
    }

    @Test("A full disk refuses before downloading anything")
    func insufficientStorageRefusesEarly() async throws {
        let core = FixtureCoreClient(scenario: .insufficientStorage)
        let catalog = try await core.offlineCatalog()
        let pack = try #require(catalog.available.first)

        let outcome: PackResult = await withCheckedContinuation { continuation in
            _ = core.downloadPack(
                packName: pack.name,
                onProgress: { _ in },
                onResult: { continuation.resume(returning: $0) }
            )
        }
        #expect(outcome.failure == .storageFull)
        #expect(try await core.installedPacks().isEmpty)
    }

    @Test("A cancelled download installs nothing")
    func cancellationInstallsNothing() async throws {
        let core = FixtureCoreClient(scenario: .normal)
        let catalog = try await core.offlineCatalog()
        let pack = try #require(catalog.available.first)

        let outcome: PackResult = await withCheckedContinuation { continuation in
            let handle = core.downloadPack(
                packName: pack.name,
                onProgress: { _ in },
                onResult: { continuation.resume(returning: $0) }
            )
            handle.cancel()
        }
        #expect(!outcome.succeeded)
        #expect(outcome.failure == .cancelled)
        #expect(try await core.installedPacks().isEmpty)
    }

    @Test("A pack that is not in the manifest is refused")
    func unknownPackIsRefused() async {
        let core = FixtureCoreClient(scenario: .normal)
        let outcome: PackResult = await withCheckedContinuation { continuation in
            _ = core.downloadPack(
                packName: "not-a-pack",
                onProgress: { _ in },
                onResult: { continuation.resume(returning: $0) }
            )
        }
        #expect(outcome.failure == .notInManifest)
    }

    @Test("Rolling back moves the installed packs to the previous release")
    func rollbackMovesToPreviousRelease() async throws {
        let core = FixtureCoreClient(scenario: .normal)
        let catalog = try await core.offlineCatalog()
        let pack = try #require(catalog.available.first)
        _ = await withCheckedContinuation { continuation in
            _ = core.downloadPack(
                packName: pack.name, onProgress: { _ in },
                onResult: { continuation.resume(returning: $0) }
            )
        }

        #expect(catalog.rollbackReleaseId == AloriaFixture.previousReleaseId)
        _ = try await core.rollbackToPreviousRelease()

        let installed = try await core.installedPacks()
        #expect(installed.allSatisfy { $0.releaseId == AloriaFixture.previousReleaseId })
        let after = try await core.offlineCatalog()
        #expect(after.rollbackReleaseId == nil, "there is no second release to roll back to")
    }

    @Test("Removing a pack removes it from the installed list")
    func removingAPack() async throws {
        let core = FixtureCoreClient(scenario: .normal)
        let catalog = try await core.offlineCatalog()
        let pack = try #require(catalog.available.first)
        _ = await withCheckedContinuation { continuation in
            _ = core.downloadPack(
                packName: pack.name, onProgress: { _ in },
                onResult: { continuation.resume(returning: $0) }
            )
        }
        #expect(try await core.installedPacks().count == 1)
        try await core.removePack(packName: pack.name)
        #expect(try await core.installedPacks().isEmpty)
    }

    // MARK: Freshness

    @Test("Freshness crosses from fresh to aging to stale")
    func freshnessThresholds() {
        let core = FixtureCoreClient(scenario: .normal)
        let now = Date()
        #expect(core.freshnessOf(checkedAt: now.addingTimeInterval(-3_600), now: now).state == .fresh)
        #expect(core.freshnessOf(checkedAt: now.addingTimeInterval(-48 * 3_600), now: now).state == .aging)
        #expect(core.freshnessOf(checkedAt: now.addingTimeInterval(-400 * 3_600), now: now).state == .stale)
    }

    @Test("Freshness reports the age the interface shows")
    func freshnessReportsItsAge() {
        let core = FixtureCoreClient(scenario: .normal)
        let now = Date()
        let freshness = core.freshnessOf(checkedAt: now.addingTimeInterval(-6 * 3_600), now: now)
        #expect(freshness.ageHours == 6)
        #expect(freshness.checkedAt != nil)
    }
}

/// A small mutex, so a callback-driven download can be observed from a test
/// without a data race.
final class Mutex<Value>: @unchecked Sendable {
    private var value: Value
    private let lock = NSLock()

    init(_ value: Value) { self.value = value }

    func withLock<T>(_ body: (inout Value) -> T) -> T {
        lock.lock()
        defer { lock.unlock() }
        return body(&value)
    }
}
#endif

#if DEBUG
/// A journeys pack is published per service date, and only for dates the data
/// actually names. An empty answer served from packs must then say the device
/// has no timetable for that date, not that no service runs.
@Suite("Offline gap versus no service")
@MainActor
struct OfflineGapTests {
    static func result(
        date: ServiceDate,
        servedFromCache: Bool,
        reason: UnavailableReason?
    ) -> JourneySearchResult {
        JourneySearchResult(
            envelope: ReleaseEnvelope(
                contractVersion: Brand.contractVersion,
                releaseId: AloriaFixture.releaseId,
                dataMode: .demo,
                servedFromCache: servedFromCache,
                publishedAt: AloriaFixture.publishedAt
            ),
            query: JourneyQuery(originId: "a", destinationId: "b", date: date),
            coverage: .demo,
            results: [],
            unavailableReason: reason,
            cachedReleasePublishedAt: servedFromCache ? AloriaFixture.publishedAt : nil
        )
    }

    @Test("The pack name follows the published convention")
    func packNameConvention() throws {
        let date = try #require(ServiceDate(iso: "2026-10-02"))
        #expect(SearchViewModel.journeysPackName(for: date) == "journeys-2026-10-02")
    }

    @Test("A cached empty answer with no pack for the date reports the offline gap")
    func cachedEmptyWithNoPackReportsOfflineGap() async throws {
        let core = FixtureCoreClient(scenario: .normal)
        // Nothing is installed, so there is no pack for any date.
        let date = try #require(ServiceDate(iso: "2026-09-30"))
        let corrected = await SearchViewModel.clarifyingOfflineGap(
            Self.result(date: date, servedFromCache: true, reason: .noServiceOnDate),
            core: core
        )
        #expect(corrected.unavailableReason == .noOfflineDataForDate)
    }

    @Test("A live empty answer still means no service")
    func liveEmptyStillMeansNoService() async throws {
        let core = FixtureCoreClient(scenario: .normal)
        let date = try #require(ServiceDate(iso: "2026-09-30"))
        let corrected = await SearchViewModel.clarifyingOfflineGap(
            Self.result(date: date, servedFromCache: false, reason: .noServiceOnDate),
            core: core
        )
        #expect(
            corrected.unavailableReason == .noServiceOnDate,
            "an answer from the network really does mean no service"
        )
    }

    @Test("Another reason is never rewritten")
    func otherReasonsAreLeftAlone() async throws {
        let core = FixtureCoreClient(scenario: .normal)
        let date = try #require(ServiceDate(iso: "2026-09-30"))
        for reason in [UnavailableReason.outsideCoverage, .originEqualsDestination] {
            let corrected = await SearchViewModel.clarifyingOfflineGap(
                Self.result(date: date, servedFromCache: true, reason: reason),
                core: core
            )
            #expect(corrected.unavailableReason == reason)
        }
    }

    @Test("When the pack for the date is installed, no service means no service")
    func installedPackMeansNoServiceIsTrusted() async throws {
        let core = FixtureCoreClient(scenario: .normal)
        let catalog = try await core.offlineCatalog()
        // The fixture publishes a journeys pack for today's date.
        let packName = try #require(
            catalog.available.first { $0.name.hasPrefix("journeys-") }?.name
        )
        _ = await withCheckedContinuation { continuation in
            _ = core.downloadPack(
                packName: packName, onProgress: { _ in },
                onResult: { continuation.resume(returning: $0) }
            )
        }
        let date = try #require(ServiceDate(iso: String(packName.dropFirst("journeys-".count))))
        let corrected = await SearchViewModel.clarifyingOfflineGap(
            Self.result(date: date, servedFromCache: true, reason: .noServiceOnDate),
            core: core
        )
        #expect(
            corrected.unavailableReason == .noServiceOnDate,
            "with the pack installed the core's answer is authoritative"
        )
    }
}
#endif
