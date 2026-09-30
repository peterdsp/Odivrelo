import Foundation

/// The conformance used when the build did not link the `OdivreloCore`
/// XCFramework.
///
/// It answers every call with `CoreError.coreUnavailable`, and the interface
/// shows that as its own state, distinct from a network failure. That is
/// deliberate: substituting fixture data here would be a fabricated success,
/// and reimplementing the timetable rules in Swift is an explicit product rule
/// against.
public final class UnavailableCoreClient: OdivreloCoreClient {
    private let reason: String

    public init(reason: String) {
        self.reason = reason
    }

    private var failure: CoreError { .coreUnavailable(reason: reason) }

    public func meta() async throws -> MetaSnapshot { throw failure }
    public func coverage() async throws -> CoverageSummary { throw failure }
    public func sources() async throws -> SourceList { throw failure }

    public func searchPlaces(query: String, limit: Int) async throws -> PlaceSearchResult { throw failure }

    public func searchJourneys(
        originId: String,
        destinationId: String,
        serviceDate: ServiceDate,
        filters: JourneyFilters
    ) async throws -> JourneySearchResult { throw failure }

    public func journeyDetail(journeyId: String, serviceDate: ServiceDate) async throws -> JourneyDetail {
        throw failure
    }

    public func operatorDetail(operatorId: String) async throws -> OperatorDetail { throw failure }
    public func stopDetail(stopId: String, serviceDate: ServiceDate) async throws -> StopDetail { throw failure }

    public func savedTrips() async throws -> [SavedTrip] { throw failure }
    public func saveTrip(journeyId: String, serviceDate: ServiceDate) async throws -> SavedTrip { throw failure }
    public func removeSavedTrip(savedTripId: String) async throws { throw failure }
    public func favorites() async throws -> [FavouritePlace] { throw failure }
    public func toggleFavorite(placeId: String) async throws -> Bool { throw failure }
    public func recentSearches() async throws -> [RecentSearch] { throw failure }

    public func offlineCatalog() async throws -> OfflineCatalog { throw failure }

    public func downloadPack(
        packName: String,
        onProgress: @Sendable @escaping (PackProgress) -> Void,
        onResult: @Sendable @escaping (PackResult) -> Void
    ) -> any CoreCancellable {
        onResult(PackResult(
            packName: packName,
            succeeded: false,
            installed: nil,
            failure: .io,
            message: reason,
            attempts: 0
        ))
        return NoopCancellable()
    }

    public func installedPacks() async throws -> [InstalledPack] { throw failure }
    public func removePack(packName: String) async throws { throw failure }
    public func rollbackToPreviousRelease() async throws -> MetaSnapshot { throw failure }

    /// Even here the answer is honest: with no core there is no freshness rule,
    /// so the state is reported as stale with an unknown check time rather than
    /// as fresh.
    public func freshnessOf(checkedAt: Date, now: Date) -> Freshness {
        Freshness(checkedAt: nil, ageHours: 0, state: .stale)
    }

    public func currentServiceDate(now: Date) -> ServiceDate {
        ServiceDate.fromPicker(now)
    }

    public func shiftServiceDate(_ serviceDate: ServiceDate, byDays days: Int) -> ServiceDate {
        let shifted = ServiceDate.calendar.date(byAdding: .day, value: days, to: serviceDate.pickerInstant)
        return ServiceDate.fromPicker(shifted ?? serviceDate.pickerInstant)
    }

    public func close() async {}
}

/// A cancellable for work that never started.
public final class NoopCancellable: CoreCancellable {
    public init() {}
    public func cancel() {}
    public var isCancelled: Bool { true }
}
