import Foundation

/// Every failure the interface can present, in the core's own vocabulary.
///
/// The five contract codes come straight from `PoraviaErrorCode`. The pack
/// failures come from `PoraviaPackFailure`. Each case gets its own message and
/// its own next step: there is no catch-all "something went wrong", because a
/// person who cannot search needs to know whether the network is down, the
/// release disagrees, the disk is full or this build has no data core at all.
public enum CoreError: Error, Hashable, Sendable {
    /// `PoraviaErrorCode.notFound`.
    case notFound
    /// `PoraviaErrorCode.invalidRequest`, with the field the core named.
    case invalidRequest(field: String?)
    /// `PoraviaErrorCode.unavailable`.
    case unavailable
    /// `PoraviaErrorCode.releaseMismatch`: packs in hand disagree with the
    /// answer offered.
    case releaseMismatch
    /// `PoraviaErrorCode.unauthorized` on a surface that is meant to be public.
    case unauthorized
    /// A pack download failed. The reason is the core's own enumeration.
    case pack(PackFailure, packName: String, message: String?)
    /// No usable network path.
    case offline
    /// A request the interface issued was cancelled.
    case cancelled
    /// The shared data core is not in this build. Production builds link
    /// `PoraviaCore`; this case exists so a build without it fails visibly
    /// instead of substituting invented results.
    case coreUnavailable(reason: String)
    /// The core threw something outside the contract's vocabulary.
    case unexpected(String)
}

/// A handle that stops work started by the core. Mirrors `PoraviaCancellable`.
public protocol CoreCancellable: AnyObject, Sendable {
    func cancel()
    var isCancelled: Bool { get }
}

/// The single boundary between the Poravia user interface and the shared
/// Kotlin Multiplatform core.
///
/// This protocol mirrors the exported `PoraviaCore` Kotlin API one for one.
/// Everything below it is domain logic the core owns: contract decoding, search
/// coordination, sorting, service-date and `Europe/Athens` rules, midnight
/// crossing, freshness thresholds, favourites, saved trips, and the whole
/// offline-pack lifecycle including download, resume, SHA-256 verification,
/// atomic install and rollback. In particular, no Swift code opens a pack or
/// assumes anything about a pack's contents.
///
/// The production conformance is `PoraviaCoreAdapter`, a thin wrapper over the
/// `PoraviaCore` XCFramework. The only other conformances are a Debug-only
/// in-memory fixture for previews and tests, and `UnavailableCoreClient`, which
/// reports the missing framework rather than pretending to have data.
public protocol PoraviaCoreClient: Sendable {
    // Identity and coverage
    func meta() async throws -> MetaSnapshot
    func coverage() async throws -> CoverageSummary
    func sources() async throws -> SourceList

    // Search
    func searchPlaces(query: String, limit: Int) async throws -> PlaceSearchResult
    func searchJourneys(
        originId: String,
        destinationId: String,
        serviceDate: ServiceDate,
        filters: JourneyFilters
    ) async throws -> JourneySearchResult
    func journeyDetail(journeyId: String, serviceDate: ServiceDate) async throws -> JourneyDetail
    func operatorDetail(operatorId: String) async throws -> OperatorDetail
    func stopDetail(stopId: String, serviceDate: ServiceDate) async throws -> StopDetail

    // Saved state
    /// Registers a release the application shipped inside its own bundle,
    /// after the host has copied it into the packs directory. Returns true
    /// when at least one pack was registered.
    ///
    /// The core verifies every digest before registering anything, so this
    /// grants a bundled file no more trust than a downloaded one.
    func adoptSeededRelease() async throws -> Bool

    func savedTrips() async throws -> [SavedTrip]
    func saveTrip(journeyId: String, serviceDate: ServiceDate) async throws -> SavedTrip
    func removeSavedTrip(savedTripId: String) async throws
    func favorites() async throws -> [FavouritePlace]
    /// Returns the new state: `true` when the place is now a favourite.
    func toggleFavorite(placeId: String) async throws -> Bool
    func recentSearches() async throws -> [RecentSearch]

    // Offline packs
    func offlineCatalog() async throws -> OfflineCatalog
    func downloadPack(
        packName: String,
        onProgress: @Sendable @escaping (PackProgress) -> Void,
        onResult: @Sendable @escaping (PackResult) -> Void
    ) -> any CoreCancellable
    func installedPacks() async throws -> [InstalledPack]
    func removePack(packName: String) async throws
    /// Returns the release the core fell back to.
    func rollbackToPreviousRelease() async throws -> MetaSnapshot

    // Service time. The core owns every one of these decisions.
    func freshnessOf(checkedAt: Date, now: Date) -> Freshness
    /// The service date the given instant falls on, in `Europe/Athens`.
    func currentServiceDate(now: Date) -> ServiceDate
    /// The service date `days` away. Never computed in Swift.
    func shiftServiceDate(_ serviceDate: ServiceDate, byDays days: Int) -> ServiceDate

    func close() async
}

/// The filters the search screen collects. The core applies them; the
/// interface never filters a result list itself.
///
/// Mirrors `PoraviaJourneyFilters`.
public struct JourneyFilters: Hashable, Codable, Sendable {
    /// Restrict to journeys with a reviewed step-free boarding point.
    public var accessibleOnly: Bool
    /// Earliest departure as a local `HH:mm` on the service date.
    public var departAfter: String?
    /// Latest departure as a local `HH:mm` on the service date.
    public var departBefore: String?
    /// Restrict to these operators; empty means no restriction.
    public var operatorIds: [String]
    public var maxDurationMinutes: Int?
    /// Whether journeys that arrive after midnight are included.
    public var includeCrossesMidnight: Bool
    public var sort: JourneySort

    public init(
        accessibleOnly: Bool = false,
        departAfter: String? = nil,
        departBefore: String? = nil,
        operatorIds: [String] = [],
        maxDurationMinutes: Int? = nil,
        includeCrossesMidnight: Bool = true,
        sort: JourneySort = .departure
    ) {
        self.accessibleOnly = accessibleOnly
        self.departAfter = departAfter
        self.departBefore = departBefore
        self.operatorIds = operatorIds
        self.maxDurationMinutes = maxDurationMinutes
        self.includeCrossesMidnight = includeCrossesMidnight
        self.sort = sort
    }

    public static let none = JourneyFilters()

    public var isActive: Bool { self != .none }

    public var activeCount: Int {
        var count = 0
        if accessibleOnly { count += 1 }
        if departAfter != nil || departBefore != nil { count += 1 }
        if !operatorIds.isEmpty { count += 1 }
        if maxDurationMinutes != nil { count += 1 }
        if !includeCrossesMidnight { count += 1 }
        if sort != .departure { count += 1 }
        return count
    }
}

/// The configuration the shared core needs at construction.
/// Mirrors `PoraviaCoreConfig`.
public struct CoreConfig: Hashable, Sendable {
    /// Live API origin, or `nil` for static-pack mode.
    public var apiBaseUrl: String?
    /// Origin serving published manifests and packs.
    public var staticPacksBaseUrl: String?
    /// Writable directory the core owns for packs.
    public var packsDirectory: String
    /// Writable SQLite file path the core owns.
    public var databasePath: String
    public var languageTag: String

    public init(
        apiBaseUrl: String?,
        staticPacksBaseUrl: String?,
        packsDirectory: String,
        databasePath: String,
        languageTag: String
    ) {
        self.apiBaseUrl = apiBaseUrl
        self.staticPacksBaseUrl = staticPacksBaseUrl
        self.packsDirectory = packsDirectory
        self.databasePath = databasePath
        self.languageTag = languageTag
    }

    public var isStaticPackMode: Bool { apiBaseUrl == nil }
}

public extension PoraviaCoreClient {
    /// Most clients have no seeded release to adopt. Only the adapter over the
    /// shared core does, so it is the only one that overrides this.
    func adoptSeededRelease() async throws -> Bool { false }
}
