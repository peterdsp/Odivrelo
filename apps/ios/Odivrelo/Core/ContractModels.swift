import Foundation

// Swift mirrors of the types the `OdivreloCore` XCFramework exports.
//
// Every shape here matches a Kotlin type in the shared core one to one, so the
// adapter is a straight field-by-field conversion with nowhere to hide a
// decision. These are value types only: no freshness computation, no
// service-date arithmetic, no sorting, no midnight-crossing rule and no pack
// handling. The core owns all of that. In particular nothing in the Swift layer
// knows or assumes the shape of an offline pack's contents.

// MARK: - Localised text

/// `OdivreloLocalizedText`. Each language may be absent, so resolution falls
/// back rather than showing an empty label.
public struct LocalisedText: Hashable, Codable, Sendable {
    public var el: String?
    public var en: String?
    public var sq: String?

    public init(el: String?, en: String?, sq: String?) {
        self.el = el
        self.en = en
        self.sq = sq
    }

    public func resolved(for languageTag: String) -> String {
        let base = String(languageTag.prefix(2))
        let ordered: [String?]
        switch base {
        case "en": ordered = [en, el, sq]
        case "sq": ordered = [sq, el, en]
        default: ordered = [el, en, sq]
        }
        return ordered.compactMap { $0 }.first { !$0.isEmpty } ?? ""
    }

    public var isEmpty: Bool {
        resolved(for: Brand.defaultLanguageTag).isEmpty
    }
}

// MARK: - Enumerations

/// Decodes a contract enumeration without losing a value the core adds later.
public protocol ContractEnum: RawRepresentable, Hashable, Codable, Sendable, CaseIterable
where RawValue == String {
    static var unrecognised: Self { get }
}

/// Folds an enum name so a Kotlin constant and a Swift case compare equal
/// regardless of separators or case: `NOT_ALLOWED` and `notAllowed` both fold to
/// `notallowed`.
enum ContractEnumNameFolding {
    static func fold(_ value: String) -> String {
        value.lowercased().filter { $0 != "_" && $0 != "-" }
    }
}

public extension ContractEnum {
    init(from decoder: any Decoder) throws {
        let raw = try decoder.singleValueContainer().decode(String.self)
        self = Self(rawValue: raw) ?? .unrecognised
    }

    func encode(to encoder: any Encoder) throws {
        var container = encoder.singleValueContainer()
        try container.encode(rawValue)
    }

    /// Maps a Kotlin enum's `name` onto this case.
    ///
    /// Kotlin's exported `Enum.name` is the declared constant, which is
    /// SCREAMING_SNAKE_CASE (for example `NOT_ALLOWED`), while a case here is
    /// lowerCamelCase (`notAllowed`). An exact match is tried first so a value
    /// that already matches is unchanged, then both sides are compared with their
    /// separators dropped and case folded, which maps `NOT_ALLOWED` onto
    /// `notAllowed` without a per-enum table. An unknown value stays
    /// `unrecognised` rather than guessing.
    static func fromKotlinName(_ name: String) -> Self {
        if let direct = Self(rawValue: name) { return direct }
        let folded = ContractEnumNameFolding.fold(name)
        return Self.allCases.first { ContractEnumNameFolding.fold($0.rawValue) == folded } ?? .unrecognised
    }

    var kotlinName: String { rawValue }
}

public enum RightsStatus: String, ContractEnum {
    case allowed
    case permissionPending
    case prohibited
    case unknown
    public static var unrecognised: RightsStatus { .unknown }
}

public enum ReviewState: String, ContractEnum {
    case candidate, verified, published, stale, withdrawn, quarantined
    public static var unrecognised: ReviewState { .candidate }

    /// The core's own rule for whether a record may be shown at all.
    public var isShowable: Bool { self == .verified || self == .published }
}

public enum TimeQuality: String, ContractEnum {
    case scheduled, approximate, unknown
    public static var unrecognised: TimeQuality { .unknown }
}

/// How a position was produced. This release publishes only `scheduled`.
public enum PositionQuality: String, ContractEnum {
    case scheduled, predicted, estimated, live
    public static var unrecognised: PositionQuality { .scheduled }
    public var isRealTime: Bool { self == .live }
}

public enum GeometryConfidence: String, ContractEnum {
    case unverified, orderedStopsOnly, osmCandidate, reviewed, rejected
    public static var unrecognised: GeometryConfidence { .unverified }
    /// Whether the shape may be drawn at all. A rejected shape never is.
    public var isDrawable: Bool { self != .rejected }
}

public enum PurchaseKind: String, ContractEnum {
    case online, ticketOffice, phone, onboard, unavailable
    public static var unrecognised: PurchaseKind { .unavailable }
}

public enum CoverageState: String, ContractEnum {
    case covered, partial, notCovered, demo
    public static var unrecognised: CoverageState { .notCovered }
}

public enum FreshnessState: String, ContractEnum {
    case fresh, aging, stale
    public static var unrecognised: FreshnessState { .stale }
}

/// `OdivreloBoardingRule`. `coordinateWithOperator` means the stop is served only
/// by prior arrangement, which is neither a yes nor a no.
public enum BoardingRule: String, ContractEnum {
    case allowed, notAllowed, onRequest, coordinateWithOperator
    public static var unrecognised: BoardingRule { .notAllowed }
}

/// Where a stop sits relative to the traveller's own leg.
public enum SegmentRole: String, ContractEnum {
    case board, onSegment, alight, beforeBoard, afterAlight
    public static var unrecognised: SegmentRole { .onSegment }
}

public enum Confidence: String, ContractEnum {
    case reviewed, candidate
    public static var unrecognised: Confidence { .candidate }
}

public enum DataMode: String, ContractEnum {
    case real, demo
    public static var unrecognised: DataMode { .demo }
    /// The core's own rule. The persistent notice keys off exactly this.
    public var requiresDemoNotice: Bool { self == .demo }
}

/// Why a search came back with nothing.
///
/// `noServiceOnDate` and `noOfflineDataForDate` are different answers and must
/// never be conflated: the first says the operator runs nothing that day, the
/// second says this device has no timetable for that day to consult. Saying the
/// first when only the second is known would invent certainty the data does not
/// support, so an unrecognised reason maps to `unstated` and is presented as
/// exactly that.
public enum UnavailableReason: String, ContractEnum {
    case noServiceOnDate
    case outsideCoverage
    case originEqualsDestination
    /// No journeys pack is installed for the requested service date.
    case noOfflineDataForDate
    /// The release gave a reason this build does not recognise.
    case unstated
    public static var unrecognised: UnavailableReason { .unstated }
}

public enum PlaceKind: String, ContractEnum {
    case stopPlace, stop
    public static var unrecognised: PlaceKind { .stop }
}

public enum JourneySort: String, ContractEnum {
    case departure, duration, arrival
    public static var unrecognised: JourneySort { .departure }
}

/// `OdivreloPackPhase`. Each phase is shown, so a person waiting on a verify or
/// an atomic install is not looking at a stalled progress bar.
public enum PackPhase: String, ContractEnum {
    case queued, downloading, resuming, verifying, installing, done
    public static var unrecognised: PackPhase { .queued }
}

/// `OdivreloPackFailure`. Each gets its own message and its own next step.
public enum PackFailure: String, ContractEnum {
    case network, timeout, digestMismatch, releaseMismatch
    case storageFull, notInManifest, cancelled, io
    public static var unrecognised: PackFailure { .io }
}

/// `OdivreloErrorCode`, the five codes the contract defines.
public enum ContractErrorCode: String, ContractEnum {
    case notFound, invalidRequest, unavailable, releaseMismatch, unauthorized
    public static var unrecognised: ContractErrorCode { .unavailable }
}

// MARK: - Envelope

/// The release identity every core payload carries.
public struct ReleaseEnvelope: Hashable, Codable, Sendable {
    public var contractVersion: String
    public var releaseId: String
    public var dataMode: DataMode
    /// True when the answer came from installed packs rather than the network.
    public var servedFromCache: Bool
    /// When the release in hand was published, where the core reports it.
    public var publishedAt: Date?

    public init(
        contractVersion: String,
        releaseId: String,
        dataMode: DataMode,
        servedFromCache: Bool,
        publishedAt: Date?
    ) {
        self.contractVersion = contractVersion
        self.releaseId = releaseId
        self.dataMode = dataMode
        self.servedFromCache = servedFromCache
        self.publishedAt = publishedAt
    }

    /// How old the release in hand is, in days, where it can be known.
    public func ageInDays(now: Date = Date()) -> Int? {
        guard let publishedAt else { return nil }
        return max(0, Int(now.timeIntervalSince(publishedAt) / 86_400))
    }
}

// MARK: - Meta and coverage

public struct ProductInfo: Hashable, Codable, Sendable {
    public var name: String
    public var version: String
    public var commit: String
    public var builtAt: Date?

    public init(name: String, version: String, commit: String, builtAt: Date?) {
        self.name = name
        self.version = version
        self.commit = commit
        self.builtAt = builtAt
    }
}

public struct Attribution: Hashable, Codable, Sendable, Identifiable {
    public var name: String
    public var url: URL?
    public var licence: String?
    public var id: String { name + (licence ?? "") }

    public init(name: String, url: URL?, licence: String?) {
        self.name = name
        self.url = url
        self.licence = licence
    }
}

/// The span of service dates a release actually names, as `OdivreloServiceDateRange`.
///
/// Both ends are optional because a release may name no dates at all. An absent
/// range means the release says nothing about which dates it holds, which is not
/// the same as holding none, so it is rendered as "not stated".
public struct ServiceDateRange: Hashable, Codable, Sendable {
    public var from: ServiceDate?
    public var to: ServiceDate?

    public init(from: ServiceDate?, to: ServiceDate?) {
        self.from = from
        self.to = to
    }
}

/// `OdivreloOperatorCoverageSummary`: what one operator contributes to a release.
///
/// Distinct from `OperatorCoverage`, which is one operator's own view of its
/// routes on the operator screen. This one is the release's view of that
/// operator's share of the whole.
public struct OperatorCoverageSummary: Hashable, Codable, Sendable, Identifiable {
    public var operatorId: String?
    public var name: LocalisedText?
    public var journeyCount: Int
    public var routeCount: Int
    public var stopCount: Int
    public var state: CoverageState
    public var id: String { operatorId ?? (name?.en ?? "") }

    public init(
        operatorId: String?,
        name: LocalisedText?,
        journeyCount: Int,
        routeCount: Int,
        stopCount: Int,
        state: CoverageState
    ) {
        self.operatorId = operatorId
        self.name = name
        self.journeyCount = journeyCount
        self.routeCount = routeCount
        self.stopCount = stopCount
        self.state = state
    }
}

/// `OdivreloCoverage`, including the explicit statement of what is not covered.
///
/// `note` and `notCovered` are localised objects rather than strings: they are
/// sentences a person reads, so they exist in each of the three languages and
/// the interface resolves them for the chosen one. Rendering an English string
/// to a Greek reader would be the release speaking a language it was not asked
/// to speak.
public struct CoverageSummary: Hashable, Codable, Sendable {
    public var state: CoverageState
    public var operatorCount: Int
    public var corridorCount: Int
    public var journeyCount: Int
    public var stopCount: Int
    public var note: LocalisedText?
    /// What the release explicitly does not cover. An empty list is rendered as
    /// "not stated", never as "everything is covered".
    public var notCovered: [LocalisedText]
    /// What an absence means, keyed by the kind of absence. The core owns these
    /// words because only the data can say whether a gap means "no service" or
    /// "no data held for that date", and the interface must never guess.
    public var absenceSemantics: [String: String]
    public var operators: [OperatorCoverageSummary]
    public var serviceDates: ServiceDateRange?
    public var freshness: Freshness?
    public var releaseId: String
    public var publishedAt: Date?
    public var dataMode: DataMode

    public init(
        state: CoverageState,
        operatorCount: Int,
        corridorCount: Int,
        journeyCount: Int,
        stopCount: Int,
        note: LocalisedText?,
        notCovered: [LocalisedText],
        absenceSemantics: [String: String],
        operators: [OperatorCoverageSummary],
        serviceDates: ServiceDateRange?,
        freshness: Freshness?,
        releaseId: String,
        publishedAt: Date?,
        dataMode: DataMode
    ) {
        self.state = state
        self.operatorCount = operatorCount
        self.corridorCount = corridorCount
        self.journeyCount = journeyCount
        self.stopCount = stopCount
        self.note = note
        self.notCovered = notCovered
        self.absenceSemantics = absenceSemantics
        self.operators = operators
        self.serviceDates = serviceDates
        self.freshness = freshness
        self.releaseId = releaseId
        self.publishedAt = publishedAt
        self.dataMode = dataMode
    }
}

public struct MetaSnapshot: Hashable, Codable, Sendable {
    public var envelope: ReleaseEnvelope
    public var product: ProductInfo
    public var languages: [String]
    public var coverage: CoverageSummary
    public var offlineManifestUrl: URL?
    public var gtfsUrl: URL?
    public var attribution: [Attribution]

    public init(
        envelope: ReleaseEnvelope,
        product: ProductInfo,
        languages: [String],
        coverage: CoverageSummary,
        offlineManifestUrl: URL?,
        gtfsUrl: URL?,
        attribution: [Attribution]
    ) {
        self.envelope = envelope
        self.product = product
        self.languages = languages
        self.coverage = coverage
        self.offlineManifestUrl = offlineManifestUrl
        self.gtfsUrl = gtfsUrl
        self.attribution = attribution
    }
}

// MARK: - Places

public struct Place: Hashable, Codable, Sendable, Identifiable {
    public var id: String
    public var kind: PlaceKind
    public var name: LocalisedText
    public var parentId: String?
    public var municipality: String?
    public var latitude: Double?
    public var longitude: Double?
    public var coordinateStatus: String
    public var boardingPointCount: Int
    public var operatorIds: [String]
    public var coverage: CoverageState

    public init(
        id: String,
        kind: PlaceKind,
        name: LocalisedText,
        parentId: String?,
        municipality: String?,
        latitude: Double?,
        longitude: Double?,
        coordinateStatus: String,
        boardingPointCount: Int,
        operatorIds: [String],
        coverage: CoverageState
    ) {
        self.id = id
        self.kind = kind
        self.name = name
        self.parentId = parentId
        self.municipality = municipality
        self.latitude = latitude
        self.longitude = longitude
        self.coordinateStatus = coordinateStatus
        self.boardingPointCount = boardingPointCount
        self.operatorIds = operatorIds
        self.coverage = coverage
    }

    /// The core's own rule for whether this place groups boarding points.
    public var isTerminal: Bool { kind == .stopPlace }
}

public struct PlaceSearchResult: Hashable, Codable, Sendable {
    public var envelope: ReleaseEnvelope
    public var places: [Place]
    public var total: Int
    public var query: String

    public init(envelope: ReleaseEnvelope, places: [Place], total: Int, query: String) {
        self.envelope = envelope
        self.places = places
        self.total = total
        self.query = query
    }
}

// MARK: - Journeys

public struct OperatorSummary: Hashable, Codable, Sendable, Identifiable {
    public var id: String
    public var name: LocalisedText
    public var logoAvailable: Bool

    public init(id: String, name: LocalisedText, logoAvailable: Bool) {
        self.id = id
        self.name = name
        self.logoAvailable = logoAvailable
    }
}

/// `OdivreloJourneyEndpoint`.
public struct JourneyEndpoint: Hashable, Codable, Sendable {
    public var at: Date
    public var stopId: String
    public var stopName: LocalisedText
    public var quality: TimeQuality

    public init(at: Date, stopId: String, stopName: LocalisedText, quality: TimeQuality) {
        self.at = at
        self.stopId = stopId
        self.stopName = stopName
        self.quality = quality
    }
}

public struct Fare: Hashable, Codable, Sendable {
    public var amount: Double
    public var currency: String
    public var isIndicative: Bool

    public init(amount: Double, currency: String, isIndicative: Bool) {
        self.amount = amount
        self.currency = currency
        self.isIndicative = isIndicative
    }
}

/// Freshness exactly as the core decided it. The thresholds live in the core's
/// `ServiceTime`; nothing here recomputes a state from `ageHours`.
public struct Freshness: Hashable, Codable, Sendable {
    public var checkedAt: Date?
    public var ageHours: Int
    public var state: FreshnessState

    public init(checkedAt: Date?, ageHours: Int, state: FreshnessState) {
        self.checkedAt = checkedAt
        self.ageHours = ageHours
        self.state = state
    }
}

public struct PurchaseOption: Hashable, Codable, Sendable {
    public var kind: PurchaseKind
    public var url: URL?
    public var phone: String?
    public var address: String?
    public var openingHours: String?
    public var label: LocalisedText?
    public var disclaimer: LocalisedText?

    public init(
        kind: PurchaseKind,
        url: URL?,
        phone: String?,
        address: String?,
        openingHours: String?,
        label: LocalisedText?,
        disclaimer: LocalisedText?
    ) {
        self.kind = kind
        self.url = url
        self.phone = phone
        self.address = address
        self.openingHours = openingHours
        self.label = label
        self.disclaimer = disclaimer
    }

    /// The core's own rule: this option sends the traveller to the operator.
    public var isExternalHandoff: Bool { kind == .online && url != nil }

    /// The core's own rule: this option is usable with no network.
    public var hasOfflineFallback: Bool {
        phone?.isEmpty == false || address?.isEmpty == false
    }
}

/// `OdivreloJourney`: the summary shown in a result list.
public struct Journey: Hashable, Codable, Sendable, Identifiable {
    public var id: String
    public var operatorSummary: OperatorSummary
    public var departure: JourneyEndpoint
    public var arrival: JourneyEndpoint
    public var durationMinutes: Int
    public var intermediateStopCount: Int
    /// The operating date, decided by the core. It is never recomputed here.
    public var serviceDate: ServiceDate
    public var crossesMidnight: Bool
    public var positionQuality: PositionQuality
    public var fare: Fare?
    public var freshness: Freshness
    public var confidence: Confidence
    public var purchase: PurchaseOption
    /// `nil` means step-free boarding has not been reviewed. Never a no.
    public var accessibleBoardingPoint: Bool?

    public init(
        id: String,
        operatorSummary: OperatorSummary,
        departure: JourneyEndpoint,
        arrival: JourneyEndpoint,
        durationMinutes: Int,
        intermediateStopCount: Int,
        serviceDate: ServiceDate,
        crossesMidnight: Bool,
        positionQuality: PositionQuality,
        fare: Fare?,
        freshness: Freshness,
        confidence: Confidence,
        purchase: PurchaseOption,
        accessibleBoardingPoint: Bool?
    ) {
        self.id = id
        self.operatorSummary = operatorSummary
        self.departure = departure
        self.arrival = arrival
        self.durationMinutes = durationMinutes
        self.intermediateStopCount = intermediateStopCount
        self.serviceDate = serviceDate
        self.crossesMidnight = crossesMidnight
        self.positionQuality = positionQuality
        self.fare = fare
        self.freshness = freshness
        self.confidence = confidence
        self.purchase = purchase
        self.accessibleBoardingPoint = accessibleBoardingPoint
    }
}

public struct JourneyQuery: Hashable, Codable, Sendable {
    public var originId: String
    public var destinationId: String
    public var date: ServiceDate

    public init(originId: String, destinationId: String, date: ServiceDate) {
        self.originId = originId
        self.destinationId = destinationId
        self.date = date
    }
}

public struct JourneySearchResult: Hashable, Codable, Sendable {
    public var envelope: ReleaseEnvelope
    public var query: JourneyQuery
    public var coverage: CoverageState
    public var results: [Journey]
    public var unavailableReason: UnavailableReason?
    /// When the cached release was published, where the answer came from cache.
    public var cachedReleasePublishedAt: Date?

    public init(
        envelope: ReleaseEnvelope,
        query: JourneyQuery,
        coverage: CoverageState,
        results: [Journey],
        unavailableReason: UnavailableReason?,
        cachedReleasePublishedAt: Date?
    ) {
        self.envelope = envelope
        self.query = query
        self.coverage = coverage
        self.results = results
        self.unavailableReason = unavailableReason
        self.cachedReleasePublishedAt = cachedReleasePublishedAt
    }
}

public struct BoardingPoint: Hashable, Codable, Sendable {
    public var stopId: String
    public var name: LocalisedText
    public var terminalName: LocalisedText?
    public var bay: String?
    public var latitude: Double?
    public var longitude: Double?
    public var instructions: LocalisedText?
    public var reviewState: ReviewState
    public var reviewedAt: Date?
    /// `nil` means step-free access has not been reviewed. It never means no.
    public var stepFree: Bool?

    public init(
        stopId: String,
        name: LocalisedText,
        terminalName: LocalisedText?,
        bay: String?,
        latitude: Double?,
        longitude: Double?,
        instructions: LocalisedText?,
        reviewState: ReviewState,
        reviewedAt: Date?,
        stepFree: Bool?
    ) {
        self.stopId = stopId
        self.name = name
        self.terminalName = terminalName
        self.bay = bay
        self.latitude = latitude
        self.longitude = longitude
        self.instructions = instructions
        self.reviewState = reviewState
        self.reviewedAt = reviewedAt
        self.stepFree = stepFree
    }
}

public struct JourneyStop: Hashable, Codable, Sendable, Identifiable {
    public var stopId: String
    public var sequence: Int
    public var name: LocalisedText
    public var arrivalAt: Date?
    public var departureAt: Date?
    public var timeQuality: TimeQuality
    public var pickup: BoardingRule
    public var dropoff: BoardingRule
    public var segmentRole: SegmentRole
    public var id: String { "\(sequence)-\(stopId)" }

    public init(
        stopId: String,
        sequence: Int,
        name: LocalisedText,
        arrivalAt: Date?,
        departureAt: Date?,
        timeQuality: TimeQuality,
        pickup: BoardingRule,
        dropoff: BoardingRule,
        segmentRole: SegmentRole = .onSegment
    ) {
        self.stopId = stopId
        self.sequence = sequence
        self.name = name
        self.arrivalAt = arrivalAt
        self.departureAt = departureAt
        self.timeQuality = timeQuality
        self.pickup = pickup
        self.dropoff = dropoff
        self.segmentRole = segmentRole
    }
}

public struct RouteGeometry: Hashable, Codable, Sendable {
    public var type: String
    /// `[longitude, latitude]` pairs, exactly as the contract serialises them.
    public var coordinates: [[Double]]
    public var confidence: GeometryConfidence
    public var method: String?
    public var attribution: String?

    public init(
        type: String,
        coordinates: [[Double]],
        confidence: GeometryConfidence,
        method: String?,
        attribution: String?
    ) {
        self.type = type
        self.coordinates = coordinates
        self.confidence = confidence
        self.method = method
        self.attribution = attribution
    }
}

public struct Restriction: Hashable, Codable, Sendable, Identifiable {
    public var code: String
    public var text: LocalisedText
    public var id: String { code }

    public init(code: String, text: LocalisedText) {
        self.code = code
        self.text = text
    }
}

public struct Provenance: Hashable, Codable, Sendable, Identifiable {
    public var sourceId: String
    public var sourceName: String
    public var sourceUrl: URL?
    public var retrievedAt: Date?
    public var rightsStatus: RightsStatus
    public var licence: String?
    public var id: String { sourceId }

    public init(
        sourceId: String,
        sourceName: String,
        sourceUrl: URL?,
        retrievedAt: Date?,
        rightsStatus: RightsStatus,
        licence: String?
    ) {
        self.sourceId = sourceId
        self.sourceName = sourceName
        self.sourceUrl = sourceUrl
        self.retrievedAt = retrievedAt
        self.rightsStatus = rightsStatus
        self.licence = licence
    }
}

/// `OdivreloJourneyDetailBody`.
public struct JourneyDetailBody: Hashable, Codable, Sendable, Identifiable {
    public var id: String
    public var operatorSummary: OperatorSummary
    public var departure: JourneyEndpoint
    public var arrival: JourneyEndpoint
    public var durationMinutes: Int
    public var intermediateStopCount: Int
    public var serviceDate: ServiceDate
    public var crossesMidnight: Bool
    public var positionQuality: PositionQuality
    public var fare: Fare?
    public var freshness: Freshness
    public var confidence: Confidence
    /// `nil` when no boarding point has been reviewed for this journey.
    public var boardingPoint: BoardingPoint?
    /// The leg this detail headlines: where the traveller boards and alights.
    public var selectedSegment: SelectedSegment?
    public var stops: [JourneyStop]
    public var geometry: RouteGeometry?
    public var restrictions: [Restriction]
    public var provenance: [Provenance]
    public var purchase: PurchaseOption
    public var correctionUrl: URL?

    public init(
        id: String,
        operatorSummary: OperatorSummary,
        departure: JourneyEndpoint,
        arrival: JourneyEndpoint,
        durationMinutes: Int,
        intermediateStopCount: Int,
        serviceDate: ServiceDate,
        crossesMidnight: Bool,
        positionQuality: PositionQuality,
        fare: Fare?,
        freshness: Freshness,
        confidence: Confidence,
        boardingPoint: BoardingPoint?,
        selectedSegment: SelectedSegment? = nil,
        stops: [JourneyStop],
        geometry: RouteGeometry?,
        restrictions: [Restriction],
        provenance: [Provenance],
        purchase: PurchaseOption,
        correctionUrl: URL?
    ) {
        self.id = id
        self.operatorSummary = operatorSummary
        self.departure = departure
        self.arrival = arrival
        self.durationMinutes = durationMinutes
        self.intermediateStopCount = intermediateStopCount
        self.serviceDate = serviceDate
        self.crossesMidnight = crossesMidnight
        self.positionQuality = positionQuality
        self.fare = fare
        self.freshness = freshness
        self.confidence = confidence
        self.boardingPoint = boardingPoint
        self.selectedSegment = selectedSegment
        self.stops = stops
        self.geometry = geometry
        self.restrictions = restrictions
        self.provenance = provenance
        self.purchase = purchase
        self.correctionUrl = correctionUrl
    }
}

/// The leg a journey detail headlines, as a pair of stop ids.
public struct SelectedSegment: Hashable, Codable, Sendable {
    public var boardStopId: String
    public var alightStopId: String

    public init(boardStopId: String, alightStopId: String) {
        self.boardStopId = boardStopId
        self.alightStopId = alightStopId
    }
}

public struct JourneyDetail: Hashable, Codable, Sendable, Identifiable {
    public var envelope: ReleaseEnvelope
    public var journey: JourneyDetailBody
    public var id: String { journey.id }

    public init(envelope: ReleaseEnvelope, journey: JourneyDetailBody) {
        self.envelope = envelope
        self.journey = journey
    }
}

// MARK: - Operators and stops

public struct OperatorContact: Hashable, Codable, Sendable {
    public var phone: String?
    public var email: String?
    public var address: String?

    public init(phone: String?, email: String?, address: String?) {
        self.phone = phone
        self.email = email
        self.address = address
    }
}

public struct OperatorCoverage: Hashable, Codable, Sendable {
    public var state: CoverageState
    public var routeCount: Int
    public var stopCount: Int

    public init(state: CoverageState, routeCount: Int, stopCount: Int) {
        self.state = state
        self.routeCount = routeCount
        self.stopCount = stopCount
    }
}

public struct OperatorBody: Hashable, Codable, Sendable, Identifiable {
    public var id: String
    public var name: LocalisedText
    public var federationNumber: Int?
    public var officialSiteUrl: URL?
    public var directoryUrl: URL?
    public var contact: OperatorContact?
    public var coverage: OperatorCoverage
    public var sources: [Provenance]
    public var verifiedAt: Date?
    public var correctionUrl: URL?

    public init(
        id: String,
        name: LocalisedText,
        federationNumber: Int?,
        officialSiteUrl: URL?,
        directoryUrl: URL?,
        contact: OperatorContact?,
        coverage: OperatorCoverage,
        sources: [Provenance],
        verifiedAt: Date?,
        correctionUrl: URL?
    ) {
        self.id = id
        self.name = name
        self.federationNumber = federationNumber
        self.officialSiteUrl = officialSiteUrl
        self.directoryUrl = directoryUrl
        self.contact = contact
        self.coverage = coverage
        self.sources = sources
        self.verifiedAt = verifiedAt
        self.correctionUrl = correctionUrl
    }
}

public struct OperatorDetail: Hashable, Codable, Sendable, Identifiable {
    public var envelope: ReleaseEnvelope
    public var operatorBody: OperatorBody
    public var id: String { operatorBody.id }

    public init(envelope: ReleaseEnvelope, operatorBody: OperatorBody) {
        self.envelope = envelope
        self.operatorBody = operatorBody
    }
}

public struct StopBody: Hashable, Codable, Sendable, Identifiable {
    public var id: String
    public var name: LocalisedText
    public var kind: PlaceKind
    public var parentId: String?
    public var parentName: LocalisedText?
    public var municipality: String?
    public var latitude: Double?
    public var longitude: Double?
    public var coordinateStatus: String
    /// The bay, where this stop is itself a boarding point.
    public var bay: String?
    public var address: String?
    public var instructions: LocalisedText?
    public var boardingPoints: [BoardingPoint]
    public var operatorIds: [String]
    public var operators: [OperatorSummary]
    /// Departures are full journeys, so a station row carries the same
    /// freshness, confidence and midnight-crossing facts as a result row.
    /// The core dropped its separate departure type; this mirrors that.
    public var departures: [Journey]
    public var provenance: [Provenance]
    public var correctionUrl: URL?
    public var coverage: CoverageState

    public init(
        id: String,
        name: LocalisedText,
        kind: PlaceKind,
        parentId: String?,
        parentName: LocalisedText?,
        municipality: String?,
        latitude: Double?,
        longitude: Double?,
        coordinateStatus: String,
        bay: String?,
        address: String?,
        instructions: LocalisedText?,
        boardingPoints: [BoardingPoint],
        operatorIds: [String],
        operators: [OperatorSummary],
        departures: [Journey],
        provenance: [Provenance],
        correctionUrl: URL?,
        coverage: CoverageState
    ) {
        self.id = id
        self.name = name
        self.kind = kind
        self.parentId = parentId
        self.parentName = parentName
        self.municipality = municipality
        self.latitude = latitude
        self.longitude = longitude
        self.coordinateStatus = coordinateStatus
        self.bay = bay
        self.address = address
        self.instructions = instructions
        self.boardingPoints = boardingPoints
        self.operatorIds = operatorIds
        self.operators = operators
        self.departures = departures
        self.provenance = provenance
        self.correctionUrl = correctionUrl
        self.coverage = coverage
    }
}

public struct StopDetail: Hashable, Codable, Sendable, Identifiable {
    public var envelope: ReleaseEnvelope
    public var stop: StopBody
    public var serviceDate: ServiceDate
    public var id: String { stop.id }

    public init(envelope: ReleaseEnvelope, stop: StopBody, serviceDate: ServiceDate) {
        self.envelope = envelope
        self.stop = stop
        self.serviceDate = serviceDate
    }
}

public struct SourceRecord: Hashable, Codable, Sendable, Identifiable {
    public var id: String
    public var name: String
    public var url: URL?
    public var rightsStatus: RightsStatus
    public var licence: String?
    public var retrievedAt: Date?
    public var note: String?

    public init(
        id: String,
        name: String,
        url: URL?,
        rightsStatus: RightsStatus,
        licence: String?,
        retrievedAt: Date?,
        note: String?
    ) {
        self.id = id
        self.name = name
        self.url = url
        self.rightsStatus = rightsStatus
        self.licence = licence
        self.retrievedAt = retrievedAt
        self.note = note
    }
}

public struct SourceList: Hashable, Codable, Sendable {
    public var envelope: ReleaseEnvelope
    public var sources: [SourceRecord]

    public init(envelope: ReleaseEnvelope, sources: [SourceRecord]) {
        self.envelope = envelope
        self.sources = sources
    }
}

// MARK: - Saved state

/// `OdivreloSavedTrip`: a flat summary the core cached at save time, together
/// with the release it came from. That is what makes a saved trip usable
/// offline and what makes an honest "cached release, N days old" label possible.
public struct SavedTrip: Hashable, Codable, Sendable, Identifiable {
    public var id: String
    public var journeyId: String
    public var serviceDate: ServiceDate
    public var savedAt: Date
    public var originName: LocalisedText
    public var destinationName: LocalisedText
    public var operatorName: LocalisedText
    public var departureAt: Date
    public var arrivalAt: Date
    public var crossesMidnight: Bool
    public var boardingBay: String?
    public var boardingStepFree: Bool?
    public var releaseId: String
    public var cachedAt: Date

    public init(
        id: String,
        journeyId: String,
        serviceDate: ServiceDate,
        savedAt: Date,
        originName: LocalisedText,
        destinationName: LocalisedText,
        operatorName: LocalisedText,
        departureAt: Date,
        arrivalAt: Date,
        crossesMidnight: Bool,
        boardingBay: String?,
        boardingStepFree: Bool?,
        releaseId: String,
        cachedAt: Date
    ) {
        self.id = id
        self.journeyId = journeyId
        self.serviceDate = serviceDate
        self.savedAt = savedAt
        self.originName = originName
        self.destinationName = destinationName
        self.operatorName = operatorName
        self.departureAt = departureAt
        self.arrivalAt = arrivalAt
        self.crossesMidnight = crossesMidnight
        self.boardingBay = boardingBay
        self.boardingStepFree = boardingStepFree
        self.releaseId = releaseId
        self.cachedAt = cachedAt
    }

    /// How old the cached release is, in days.
    public func cachedAgeInDays(now: Date = Date()) -> Int {
        max(0, Int(now.timeIntervalSince(cachedAt) / 86_400))
    }
}

public struct FavouritePlace: Hashable, Codable, Sendable, Identifiable {
    public var placeId: String
    public var name: LocalisedText
    public var kind: PlaceKind
    public var municipality: String?
    public var addedAt: Date
    public var id: String { placeId }

    public init(
        placeId: String,
        name: LocalisedText,
        kind: PlaceKind,
        municipality: String?,
        addedAt: Date
    ) {
        self.placeId = placeId
        self.name = name
        self.kind = kind
        self.municipality = municipality
        self.addedAt = addedAt
    }
}

public struct RecentSearch: Hashable, Codable, Sendable, Identifiable {
    public var originId: String
    public var originName: LocalisedText
    public var destinationId: String
    public var destinationName: LocalisedText
    public var serviceDate: ServiceDate
    public var searchedAt: Date
    public var id: String { originId + "|" + destinationId + "|" + serviceDate.iso }

    public init(
        originId: String,
        originName: LocalisedText,
        destinationId: String,
        destinationName: LocalisedText,
        serviceDate: ServiceDate,
        searchedAt: Date
    ) {
        self.originId = originId
        self.originName = originName
        self.destinationId = destinationId
        self.destinationName = destinationName
        self.serviceDate = serviceDate
        self.searchedAt = searchedAt
    }
}

// MARK: - Offline packs

/// `OdivreloAvailablePack`. The Swift layer treats `name` and `path` as opaque:
/// it never opens a pack and never assumes anything about its contents.
public struct AvailablePack: Hashable, Codable, Sendable, Identifiable {
    public var name: String
    public var path: String
    public var sha256: String
    public var bytes: Int64
    public var releaseId: String
    public var installed: Bool
    public var updateAvailable: Bool
    public var title: LocalisedText
    public var summary: LocalisedText
    public var id: String { name }

    public init(
        name: String,
        path: String,
        sha256: String,
        bytes: Int64,
        releaseId: String,
        installed: Bool,
        updateAvailable: Bool,
        title: LocalisedText,
        summary: LocalisedText
    ) {
        self.name = name
        self.path = path
        self.sha256 = sha256
        self.bytes = bytes
        self.releaseId = releaseId
        self.installed = installed
        self.updateAvailable = updateAvailable
        self.title = title
        self.summary = summary
    }
}

public struct InstalledPack: Hashable, Codable, Sendable, Identifiable {
    public var name: String
    public var releaseId: String
    public var sha256: String
    public var bytes: Int64
    public var installedAt: Date
    public var publishedAt: Date?
    public var previousReleaseId: String?
    public var id: String { name }

    public init(
        name: String,
        releaseId: String,
        sha256: String,
        bytes: Int64,
        installedAt: Date,
        publishedAt: Date?,
        previousReleaseId: String?
    ) {
        self.name = name
        self.releaseId = releaseId
        self.sha256 = sha256
        self.bytes = bytes
        self.installedAt = installedAt
        self.publishedAt = publishedAt
        self.previousReleaseId = previousReleaseId
    }
}

/// `OdivreloOfflineMapAvailability`: the core's own statement of what does and
/// does not work without a connection. The interface repeats it verbatim rather
/// than guessing.
public struct OfflineMapAvailability: Hashable, Codable, Sendable {
    public var stopCoordinatesAvailable: Bool
    public var routeGeometryAvailable: Bool
    public var baseMapTilesAvailable: Bool
    public var searchAvailable: Bool

    public init(
        stopCoordinatesAvailable: Bool,
        routeGeometryAvailable: Bool,
        baseMapTilesAvailable: Bool,
        searchAvailable: Bool
    ) {
        self.stopCoordinatesAvailable = stopCoordinatesAvailable
        self.routeGeometryAvailable = routeGeometryAvailable
        self.baseMapTilesAvailable = baseMapTilesAvailable
        self.searchAvailable = searchAvailable
    }
}

public struct OfflineCatalog: Hashable, Codable, Sendable {
    public var releaseId: String
    public var publishedAt: Date?
    public var dataMode: DataMode
    public var available: [AvailablePack]
    public var installed: [InstalledPack]
    public var totalInstalledBytes: Int64
    /// The release retained for rollback, when one exists.
    public var rollbackReleaseId: String?
    public var mapAvailability: OfflineMapAvailability
    /// False when the published manifest could not be read at all.
    public var manifestReachable: Bool

    public init(
        releaseId: String,
        publishedAt: Date?,
        dataMode: DataMode,
        available: [AvailablePack],
        installed: [InstalledPack],
        totalInstalledBytes: Int64,
        rollbackReleaseId: String?,
        mapAvailability: OfflineMapAvailability,
        manifestReachable: Bool
    ) {
        self.releaseId = releaseId
        self.publishedAt = publishedAt
        self.dataMode = dataMode
        self.available = available
        self.installed = installed
        self.totalInstalledBytes = totalInstalledBytes
        self.rollbackReleaseId = rollbackReleaseId
        self.mapAvailability = mapAvailability
        self.manifestReachable = manifestReachable
    }
}

public struct PackProgress: Hashable, Sendable {
    public var packName: String
    public var bytesDownloaded: Int64
    public var totalBytes: Int64
    public var phase: PackPhase
    /// Which attempt this is. Above one, the download was resumed.
    public var attempt: Int

    public init(
        packName: String,
        bytesDownloaded: Int64,
        totalBytes: Int64,
        phase: PackPhase,
        attempt: Int
    ) {
        self.packName = packName
        self.bytesDownloaded = bytesDownloaded
        self.totalBytes = totalBytes
        self.phase = phase
        self.attempt = attempt
    }

    /// `nil` when the total is not yet known, so the bar stays indeterminate
    /// rather than showing a made-up figure.
    public var fraction: Double? {
        guard totalBytes > 0 else { return nil }
        return min(1, max(0, Double(bytesDownloaded) / Double(totalBytes)))
    }
}

public struct PackResult: Hashable, Sendable {
    public var packName: String
    public var succeeded: Bool
    public var installed: InstalledPack?
    public var failure: PackFailure?
    public var message: String?
    public var attempts: Int

    public init(
        packName: String,
        succeeded: Bool,
        installed: InstalledPack?,
        failure: PackFailure?,
        message: String?,
        attempts: Int
    ) {
        self.packName = packName
        self.succeeded = succeeded
        self.installed = installed
        self.failure = failure
        self.message = message
        self.attempts = attempts
    }
}
