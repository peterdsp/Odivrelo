#if ODIVRELO_CORE_AVAILABLE
import Foundation
import OdivreloCore

/// The production conformance: a thin adapter over the `OdivreloCore`
/// XCFramework produced by `scripts/shared-build-xcframework.sh`.
///
/// Every method forwards to the shared Kotlin core and converts the exported
/// value into the Swift mirror. There is no fallback path, no cache of its own
/// and no rule of its own. Contract decoding, search coordination, sorting,
/// service-date and `Europe/Athens` handling, midnight crossing, freshness,
/// favourites, saved trips and the whole offline-pack lifecycle stay in the
/// core. No pack is ever opened on this side.
///
/// Kotlin `suspend` functions are exported as `…(…, completionHandler:)`, which
/// Swift imports as `async throws`. Kotlin exceptions arrive as `NSError`
/// carrying a `OdivreloException`, translated by `CoreErrorTranslation`.
///
/// This file is compiled only when the framework is linked: the
/// `ODIVRELO_CORE_AVAILABLE` condition is set by `scripts/ios-bootstrap.sh`
/// once it finds `shared/core/build/XCFramework`.
public final class OdivreloCoreAdapter: OdivreloCoreClient, @unchecked Sendable {
    private let core: any OdivreloCore

    /// The factory validates the configuration and opens the database, so it can
    /// fail. It is annotated `@Throws(OdivreloException)` in Kotlin, which is what
    /// lets that failure arrive here as an `NSError` instead of terminating the
    /// process, so the failure is translated like every other core error rather
    /// than being swallowed.
    public init(config: CoreConfig) throws {
        let kotlinConfig = OdivreloCoreConfig(
            apiBaseUrl: config.apiBaseUrl,
            staticPacksBaseUrl: config.staticPacksBaseUrl,
            packsDirectory: config.packsDirectory,
            databasePath: config.databasePath,
            languageTag: config.languageTag
        )
        do {
            // Kotlin declares the factory nullable, but because it also carries
            // an error out-parameter Swift imports it as non-optional: a failure
            // arrives as a thrown error, never as nil.
            self.core = try OdivreloCore_iosKt.OdivreloCoreFactory(config: kotlinConfig)
        } catch let error as CoreError {
            throw error
        } catch {
            throw CoreErrorTranslation.translate(error)
        }
    }

    /// Builds a configuration from the paths the core itself nominates, so the
    /// two never disagree about where the database and packs live.
    public static func defaultConfig(languageTag: String) -> CoreConfig {
        let native = OdivreloIosPaths.shared.defaultConfig(languageTag: languageTag)
        return CoreConfig(
            apiBaseUrl: native.apiBaseUrl,
            staticPacksBaseUrl: native.staticPacksBaseUrl,
            packsDirectory: native.packsDirectory,
            databasePath: native.databasePath,
            languageTag: native.resolvedLanguageTag
        )
    }

    // MARK: Identity and coverage

    public func meta() async throws -> MetaSnapshot {
        try await CoreErrorTranslation.run { CoreMapping.meta(try await self.core.meta()) }
    }

    public func coverage() async throws -> CoverageSummary {
        try await CoreErrorTranslation.run { CoreMapping.coverage(try await self.core.coverage()) }
    }

    public func sources() async throws -> SourceList {
        try await CoreErrorTranslation.run { CoreMapping.sourceList(try await self.core.sources()) }
    }

    // MARK: Search

    public func searchPlaces(query: String, limit: Int) async throws -> PlaceSearchResult {
        try await CoreErrorTranslation.run {
            CoreMapping.placeResults(
                try await self.core.searchPlaces(query: query, limit: Int32(limit))
            )
        }
    }

    public func searchJourneys(
        originId: String,
        destinationId: String,
        serviceDate: ServiceDate,
        filters: JourneyFilters
    ) async throws -> JourneySearchResult {
        try await CoreErrorTranslation.run {
            CoreMapping.journeyResults(
                try await self.core.searchJourneys(
                    originId: originId,
                    destinationId: destinationId,
                    serviceDate: serviceDate.iso,
                    filters: CoreMapping.kotlinFilters(filters)
                )
            )
        }
    }

    public func journeyDetail(journeyId: String, serviceDate: ServiceDate) async throws -> JourneyDetail {
        try await CoreErrorTranslation.run {
            CoreMapping.journeyDetail(
                try await self.core.journeyDetail(journeyId: journeyId, serviceDate: serviceDate.iso)
            )
        }
    }

    public func operatorDetail(operatorId: String) async throws -> OperatorDetail {
        try await CoreErrorTranslation.run {
            CoreMapping.operatorDetail(try await self.core.operatorDetail(operatorId: operatorId))
        }
    }

    public func stopDetail(stopId: String, serviceDate: ServiceDate) async throws -> StopDetail {
        try await CoreErrorTranslation.run {
            CoreMapping.stopDetail(
                try await self.core.stopDetail(stopId: stopId, serviceDate: serviceDate.iso)
            )
        }
    }

    // MARK: Saved state

    /// Delegates to the core's extras. A core built without them reports that
    /// nothing was adopted rather than pretending it succeeded.
    public func adoptSeededRelease() async throws -> Bool {
        guard let extras = OdivreloCoreExtrasKt.extras(core) else { return false }
        return try await CoreErrorTranslation.run {
            try await extras.adoptSeededRelease().boolValue
        }
    }

    public func savedTrips() async throws -> [SavedTrip] {
        try await CoreErrorTranslation.run {
            (try await self.core.savedTrips()).compactMap(CoreMapping.savedTrip)
        }
    }

    public func saveTrip(journeyId: String, serviceDate: ServiceDate) async throws -> SavedTrip {
        try await CoreErrorTranslation.run {
            guard let trip = CoreMapping.savedTrip(
                try await self.core.saveTrip(journeyId: journeyId, serviceDate: serviceDate.iso)
            ) else {
                throw CoreError.unexpected("the core returned a saved trip that could not be read")
            }
            return trip
        }
    }

    public func removeSavedTrip(savedTripId: String) async throws {
        try await CoreErrorTranslation.run {
            try await self.core.removeSavedTrip(savedTripId: savedTripId)
        }
    }

    public func favorites() async throws -> [FavouritePlace] {
        try await CoreErrorTranslation.run {
            (try await self.core.favorites()).map(CoreMapping.favourite)
        }
    }

    public func toggleFavorite(placeId: String) async throws -> Bool {
        try await CoreErrorTranslation.run {
            (try await self.core.toggleFavorite(placeId: placeId)).boolValue
        }
    }

    public func recentSearches() async throws -> [RecentSearch] {
        try await CoreErrorTranslation.run {
            (try await self.core.recentSearches()).compactMap(CoreMapping.recentSearch)
        }
    }

    // MARK: Offline packs

    public func offlineCatalog() async throws -> OfflineCatalog {
        try await CoreErrorTranslation.run {
            CoreMapping.offlineCatalog(try await self.core.offlineCatalog())
        }
    }

    public func downloadPack(
        packName: String,
        onProgress: @Sendable @escaping (PackProgress) -> Void,
        onResult: @Sendable @escaping (PackResult) -> Void
    ) -> any CoreCancellable {
        let handle = core.downloadPack(
            packName: packName,
            onProgress: { progress in onProgress(CoreMapping.packProgress(progress)) },
            onResult: { result in onResult(CoreMapping.packResult(result)) }
        )
        return KotlinCancellableBox(handle)
    }

    public func installedPacks() async throws -> [InstalledPack] {
        try await CoreErrorTranslation.run {
            (try await self.core.installedPacks()).map(CoreMapping.installedPack)
        }
    }

    public func removePack(packName: String) async throws {
        try await CoreErrorTranslation.run {
            try await self.core.removePack(packName: packName)
        }
    }

    public func rollbackToPreviousRelease() async throws -> MetaSnapshot {
        try await CoreErrorTranslation.run {
            CoreMapping.meta(try await self.core.rollbackToPreviousRelease())
        }
    }

    // MARK: Service time

    public func freshnessOf(checkedAt: Date, now: Date) -> Freshness {
        CoreMapping.freshness(
            core.freshnessOf(
                checkedAt: ContractInstant.format(checkedAt),
                now: ContractInstant.format(now)
            )
        )
    }

    public func currentServiceDate(now: Date) -> ServiceDate {
        let iso = OdivreloServiceTime.shared.currentServiceDate(
            now: OdivreloServiceTime.shared.parseInstant(value: ContractInstant.format(now))
        )
        return ServiceDate(iso: iso) ?? ServiceDate.fromPicker(now)
    }

    public func shiftServiceDate(_ serviceDate: ServiceDate, byDays days: Int) -> ServiceDate {
        let iso = OdivreloServiceTime.shared.shiftServiceDate(
            serviceDate: serviceDate.iso,
            days: Int32(days)
        )
        return ServiceDate(iso: iso) ?? serviceDate
    }

    public func close() async {
        core.close()
    }
}

/// Wraps the Kotlin `Cancellable` the core returns from `downloadPack`.
private final class KotlinCancellableBox: CoreCancellable, @unchecked Sendable {
    private let handle: any OdivreloCancellable

    init(_ handle: any OdivreloCancellable) {
        self.handle = handle
    }

    func cancel() { handle.cancel() }
    var isCancelled: Bool { handle.isCancelled }
}
#endif
