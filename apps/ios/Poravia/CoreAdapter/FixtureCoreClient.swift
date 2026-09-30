#if DEBUG
import Foundation
import os

// This entire file is inside `#if DEBUG`. A Release build cannot name
// `FixtureCoreClient` or `FixtureScenario`, which is the compile-time guard
// required of the fixture conformance. `ReleaseGuardTests` asserts the absence
// at runtime and `scripts/ios-verify-release.sh` proves the symbol is not in
// the archived binary.

/// Scenarios the fixture can be driven into, so every failure state in the
/// interface can be exercised on a simulator without a server.
///
/// Selected with `-PoraviaFixtureScenario <value>` or
/// `PORAVIA_FIXTURE_SCENARIO`.
public enum FixtureScenario: String, CaseIterable, Sendable {
    case normal
    case offline
    case serverError
    case emptyResults
    case notCovered
    case partialCoverage
    case releaseMismatch
    case corruptDownload
    case interruptedDownload
    case insufficientStorage
    case staleRelease
    case coreUnavailable
    case servedFromCache
    case noOfflineDataForDate

    public static func fromEnvironment(
        arguments: [String] = ProcessInfo.processInfo.arguments,
        environment: [String: String] = ProcessInfo.processInfo.environment
    ) -> FixtureScenario {
        if let index = arguments.firstIndex(of: "-PoraviaFixtureScenario"),
           arguments.count > index + 1,
           let value = FixtureScenario(rawValue: arguments[index + 1]) {
            return value
        }
        if let raw = environment["PORAVIA_FIXTURE_SCENARIO"],
           let value = FixtureScenario(rawValue: raw) {
            return value
        }
        return .normal
    }
}

/// An in-memory conformance backed by the invented Aloria dataset.
public final class FixtureCoreClient: PoraviaCoreClient {
    private let state: OSAllocatedUnfairLock<MutableState>
    private let scenario: FixtureScenario
    private let log = Logger(subsystem: Brand.bundleIdentifier, category: "fixture")

    private struct MutableState: Sendable {
        var favourites: [FavouritePlace] = []
        var savedTrips: [SavedTrip] = []
        var recents: [RecentSearch] = []
        var installed: [InstalledPack] = []
        var rolledBack = false
        var interruptedOnce: Set<String> = []
    }

    public init(scenario: FixtureScenario = FixtureScenario.fromEnvironment()) {
        self.scenario = scenario
        self.state = OSAllocatedUnfairLock(initialState: MutableState())
    }

    // MARK: Failure injection

    private func gate() throws {
        switch scenario {
        case .offline: throw CoreError.offline
        case .serverError: throw CoreError.unavailable
        case .releaseMismatch: throw CoreError.releaseMismatch
        case .coreUnavailable:
            throw CoreError.coreUnavailable(reason: CoreClientFactory.missingCoreReason)
        default: break
        }
    }

    private var coverageState: CoverageState {
        switch scenario {
        case .notCovered: .notCovered
        case .partialCoverage: .partial
        default: .demo
        }
    }

    private var servedFromCache: Bool {
        scenario == .servedFromCache || scenario == .noOfflineDataForDate
    }

    // MARK: Identity and coverage

    public func meta() async throws -> MetaSnapshot {
        try gate()
        return AloriaFixture.meta(coverageState: coverageState)
    }

    public func coverage() async throws -> CoverageSummary {
        try gate()
        return AloriaFixture.coverage(state: coverageState)
    }

    public func sources() async throws -> SourceList {
        try gate()
        return AloriaFixture.sourceList
    }

    // MARK: Search

    public func searchPlaces(query: String, limit: Int) async throws -> PlaceSearchResult {
        try gate()
        let trimmed = query.trimmingCharacters(in: .whitespacesAndNewlines)
        let matches: [Place]
        if trimmed.isEmpty {
            matches = AloriaFixture.places
        } else {
            let needle = AloriaFixture.fold(trimmed)
            matches = AloriaFixture.places.filter { place in
                [place.name.el, place.name.en, place.name.sq, place.municipality]
                    .compactMap { $0 }
                    .contains { AloriaFixture.fold($0).contains(needle) }
            }
        }
        return PlaceSearchResult(
            envelope: AloriaFixture.envelope(servedFromCache: servedFromCache),
            places: Array(matches.prefix(max(0, limit))),
            total: matches.count,
            query: query
        )
    }

    public func searchJourneys(
        originId: String,
        destinationId: String,
        serviceDate: ServiceDate,
        filters: JourneyFilters
    ) async throws -> JourneySearchResult {
        try gate()

        let query = JourneyQuery(originId: originId, destinationId: destinationId, date: serviceDate)

        func empty(_ reason: UnavailableReason, coverage: CoverageState) -> JourneySearchResult {
            JourneySearchResult(
                envelope: AloriaFixture.envelope(servedFromCache: servedFromCache),
                query: query,
                coverage: coverage,
                results: [],
                unavailableReason: reason,
                cachedReleasePublishedAt: servedFromCache ? AloriaFixture.publishedAt : nil
            )
        }

        if originId == destinationId { return empty(.originEqualsDestination, coverage: .demo) }
        if scenario == .notCovered { return empty(.outsideCoverage, coverage: .notCovered) }
        if scenario == .emptyResults { return empty(.noServiceOnDate, coverage: .demo) }
        // The core has only three reasons today, so a date with no journeys
        // pack comes back as "no service". `SearchViewModel.clarifyingOfflineGap`
        // corrects that from the installed pack list.
        if scenario == .noOfflineDataForDate { return empty(.noServiceOnDate, coverage: .demo) }

        var results = AloriaFixture.journeys(
            originId: originId,
            destinationId: destinationId,
            serviceDate: serviceDate,
            stale: scenario == .staleRelease
        )

        // The fixture applies the filters it is handed so the interface can be
        // exercised. In production the core does this and the interface only
        // collects the values.
        if filters.accessibleOnly {
            results = results.filter { $0.accessibleBoardingPoint == true }
        }
        if !filters.includeCrossesMidnight {
            results = results.filter { !$0.crossesMidnight }
        }
        if !filters.operatorIds.isEmpty {
            results = results.filter { filters.operatorIds.contains($0.operatorSummary.id) }
        }
        if let maximum = filters.maxDurationMinutes {
            results = results.filter { $0.durationMinutes <= maximum }
        }
        if let after = filters.departAfter {
            results = results.filter { localTime($0.departure.at) >= after }
        }
        if let before = filters.departBefore {
            results = results.filter { localTime($0.departure.at) <= before }
        }
        switch filters.sort {
        case .departure: results.sort { $0.departure.at < $1.departure.at }
        case .arrival: results.sort { $0.arrival.at < $1.arrival.at }
        case .duration: results.sort { $0.durationMinutes < $1.durationMinutes }
        }

        if let origin = AloriaFixture.place(id: originId),
           let destination = AloriaFixture.place(id: destinationId) {
            state.withLock { current in
                current.recents.removeAll {
                    $0.originId == originId && $0.destinationId == destinationId
                }
                current.recents.insert(
                    RecentSearch(
                        originId: origin.id, originName: origin.name,
                        destinationId: destination.id, destinationName: destination.name,
                        serviceDate: serviceDate, searchedAt: Date()
                    ),
                    at: 0
                )
                current.recents = Array(current.recents.prefix(8))
            }
        }

        return JourneySearchResult(
            envelope: AloriaFixture.envelope(servedFromCache: servedFromCache),
            query: query,
            coverage: coverageState,
            results: results,
            unavailableReason: results.isEmpty ? .noServiceOnDate : nil,
            cachedReleasePublishedAt: servedFromCache ? AloriaFixture.publishedAt : nil
        )
    }

    private func localTime(_ date: Date) -> String {
        let components = ServiceDate.calendar.dateComponents([.hour, .minute], from: date)
        return String(format: "%02d:%02d", components.hour ?? 0, components.minute ?? 0)
    }

    public func journeyDetail(journeyId: String, serviceDate: ServiceDate) async throws -> JourneyDetail {
        try gate()
        guard let detail = AloriaFixture.journeyDetail(
            journeyId: journeyId, serviceDate: serviceDate, stale: scenario == .staleRelease
        ) else { throw CoreError.notFound }
        return detail
    }

    public func operatorDetail(operatorId: String) async throws -> OperatorDetail {
        try gate()
        guard let detail = AloriaFixture.operatorDetail(id: operatorId) else { throw CoreError.notFound }
        return detail
    }

    public func stopDetail(stopId: String, serviceDate: ServiceDate) async throws -> StopDetail {
        try gate()
        guard let detail = AloriaFixture.stopDetail(id: stopId, serviceDate: serviceDate) else {
            throw CoreError.notFound
        }
        return detail
    }

    // MARK: Saved state

    public func savedTrips() async throws -> [SavedTrip] {
        state.withLock { $0.savedTrips }
    }

    public func saveTrip(journeyId: String, serviceDate: ServiceDate) async throws -> SavedTrip {
        guard let detail = AloriaFixture.journeyDetail(
            journeyId: journeyId, serviceDate: serviceDate, stale: scenario == .staleRelease
        ) else { throw CoreError.notFound }

        let body = detail.journey
        let trip = SavedTrip(
            id: "\(journeyId)|\(serviceDate.iso)",
            journeyId: journeyId,
            serviceDate: serviceDate,
            savedAt: Date(),
            originName: body.departure.stopName,
            destinationName: body.arrival.stopName,
            operatorName: body.operatorSummary.name,
            departureAt: body.departure.at,
            arrivalAt: body.arrival.at,
            crossesMidnight: body.crossesMidnight,
            boardingBay: body.boardingPoint?.bay,
            boardingStepFree: body.boardingPoint?.stepFree,
            releaseId: detail.envelope.releaseId,
            cachedAt: Date()
        )
        state.withLock { current in
            current.savedTrips.removeAll { $0.id == trip.id }
            current.savedTrips.insert(trip, at: 0)
        }
        return trip
    }

    public func removeSavedTrip(savedTripId: String) async throws {
        state.withLock { $0.savedTrips.removeAll { $0.id == savedTripId } }
    }

    public func favorites() async throws -> [FavouritePlace] {
        state.withLock { $0.favourites }
    }

    public func toggleFavorite(placeId: String) async throws -> Bool {
        guard let place = AloriaFixture.place(id: placeId) else { throw CoreError.notFound }
        return state.withLock { current in
            if let index = current.favourites.firstIndex(where: { $0.placeId == placeId }) {
                current.favourites.remove(at: index)
                return false
            }
            current.favourites.insert(
                FavouritePlace(
                    placeId: place.id, name: place.name, kind: place.kind,
                    municipality: place.municipality, addedAt: Date()
                ),
                at: 0
            )
            return true
        }
    }

    public func recentSearches() async throws -> [RecentSearch] {
        state.withLock { $0.recents }
    }

    // MARK: Offline packs

    public func offlineCatalog() async throws -> OfflineCatalog {
        try gate()
        return state.withLock { current in
            let installedNames = Set(current.installed.map(\.name))
            return OfflineCatalog(
                releaseId: current.rolledBack ? AloriaFixture.previousReleaseId : AloriaFixture.releaseId,
                publishedAt: AloriaFixture.publishedAt,
                dataMode: .demo,
                available: AloriaFixture.availablePacks(installed: installedNames),
                installed: current.installed,
                totalInstalledBytes: current.installed.reduce(0) { $0 + $1.bytes },
                rollbackReleaseId: current.rolledBack ? nil : AloriaFixture.previousReleaseId,
                mapAvailability: AloriaFixture.mapAvailability,
                manifestReachable: true
            )
        }
    }

    public func downloadPack(
        packName: String,
        onProgress: @Sendable @escaping (PackProgress) -> Void,
        onResult: @Sendable @escaping (PackResult) -> Void
    ) -> any CoreCancellable {
        let installedNames = state.withLock { Set($0.installed.map(\.name)) }
        guard let pack = AloriaFixture.availablePacks(installed: installedNames)
            .first(where: { $0.name == packName })
        else {
            onResult(PackResult(
                packName: packName, succeeded: false, installed: nil,
                failure: .notInManifest, message: nil, attempts: 1
            ))
            return NoopCancellable()
        }

        if scenario == .insufficientStorage {
            onResult(PackResult(
                packName: packName, succeeded: false, installed: nil,
                failure: .storageFull,
                message: "Needs \(pack.bytes) bytes", attempts: 1
            ))
            return NoopCancellable()
        }

        let box = FixtureDownload()
        let lock = state
        let currentScenario = scenario

        Task.detached { [box] in
            let attempt = lock.withLock { $0.interruptedOnce.contains(packName) ? 2 : 1 }
            onProgress(PackProgress(
                packName: packName, bytesDownloaded: 0, totalBytes: pack.bytes,
                phase: attempt > 1 ? .resuming : .queued, attempt: attempt
            ))

            let steps = 18
            for step in 1...steps {
                if box.isCancelled {
                    onResult(PackResult(
                        packName: packName, succeeded: false, installed: nil,
                        failure: .cancelled, message: nil, attempts: attempt
                    ))
                    return
                }
                try? await Task.sleep(for: .milliseconds(85))
                onProgress(PackProgress(
                    packName: packName,
                    bytesDownloaded: pack.bytes * Int64(step) / Int64(steps),
                    totalBytes: pack.bytes,
                    phase: .downloading,
                    attempt: attempt
                ))

                // An interrupted download stops part way the first time and
                // resumes on the next attempt, so recovery is exercised.
                if currentScenario == .interruptedDownload, step == 8, attempt == 1 {
                    lock.withLock { _ = $0.interruptedOnce.insert(packName) }
                    onResult(PackResult(
                        packName: packName, succeeded: false, installed: nil,
                        failure: .network, message: nil, attempts: attempt
                    ))
                    return
                }
            }

            onProgress(PackProgress(
                packName: packName, bytesDownloaded: pack.bytes, totalBytes: pack.bytes,
                phase: .verifying, attempt: attempt
            ))
            try? await Task.sleep(for: .milliseconds(200))

            if currentScenario == .corruptDownload {
                onResult(PackResult(
                    packName: packName, succeeded: false, installed: nil,
                    failure: .digestMismatch,
                    message: "expected \(pack.sha256.prefix(16))", attempts: attempt
                ))
                return
            }

            onProgress(PackProgress(
                packName: packName, bytesDownloaded: pack.bytes, totalBytes: pack.bytes,
                phase: .installing, attempt: attempt
            ))
            try? await Task.sleep(for: .milliseconds(150))

            let installed = InstalledPack(
                name: pack.name, releaseId: pack.releaseId, sha256: pack.sha256,
                bytes: pack.bytes, installedAt: Date(), publishedAt: AloriaFixture.publishedAt,
                previousReleaseId: AloriaFixture.previousReleaseId
            )
            lock.withLock { current in
                current.installed.removeAll { $0.name == installed.name }
                current.installed.append(installed)
                current.interruptedOnce.remove(packName)
            }
            onProgress(PackProgress(
                packName: packName, bytesDownloaded: pack.bytes, totalBytes: pack.bytes,
                phase: .done, attempt: attempt
            ))
            onResult(PackResult(
                packName: packName, succeeded: true, installed: installed,
                failure: nil, message: nil, attempts: attempt
            ))
        }
        return box
    }

    public func installedPacks() async throws -> [InstalledPack] {
        state.withLock { $0.installed }
    }

    public func removePack(packName: String) async throws {
        state.withLock { $0.installed.removeAll { $0.name == packName } }
    }

    public func rollbackToPreviousRelease() async throws -> MetaSnapshot {
        try gate()
        state.withLock { current in
            current.rolledBack = true
            current.installed = current.installed.map { pack in
                InstalledPack(
                    name: pack.name, releaseId: AloriaFixture.previousReleaseId,
                    sha256: pack.sha256, bytes: pack.bytes, installedAt: pack.installedAt,
                    publishedAt: pack.publishedAt, previousReleaseId: nil
                )
            }
        }
        return AloriaFixture.meta(coverageState: coverageState)
    }

    // MARK: Service time

    public func freshnessOf(checkedAt: Date, now: Date) -> Freshness {
        AloriaFixture.freshness(checkedAt: checkedAt, now: now)
    }

    public func currentServiceDate(now: Date) -> ServiceDate {
        ServiceDate.fromPicker(now)
    }

    public func shiftServiceDate(_ serviceDate: ServiceDate, byDays days: Int) -> ServiceDate {
        let shifted = ServiceDate.calendar.date(
            byAdding: .day, value: days, to: serviceDate.pickerInstant
        )
        return ServiceDate.fromPicker(shifted ?? serviceDate.pickerInstant)
    }

    public func close() async {}
}

private final class FixtureDownload: CoreCancellable, @unchecked Sendable {
    private let flag = OSAllocatedUnfairLock(initialState: false)
    var isCancelled: Bool { flag.withLock { $0 } }
    func cancel() { flag.withLock { $0 = true } }
}
#endif
