import Foundation
import Observation
import SwiftUI
import os

/// The application's long-lived state.
///
/// It owns the one `PoraviaCoreClient` and the navigation state that has to
/// survive a resize, a rotation, a posture change, multitasking, a trip to the
/// background and a relaunch. It deliberately does not own timetable logic:
/// every question about journeys goes to the core.
@Observable
@MainActor
public final class AppModel {
    public let core: any PoraviaCoreClient
    public let coreSelection: CoreClientFactory.Selection

    public private(set) var meta: MetaSnapshot?
    public private(set) var metaError: CoreError?
    public private(set) var isLoadingMeta = false

    public var session: SessionState
    public var route: RouteState

    public let settings: AppSettings
    public let wallet: TicketStore
    public let reminders: ReminderScheduler

    private let log = Logger(subsystem: Brand.bundleIdentifier, category: "app")
    private let sessionStore: SessionStore

    /// `true` while the release in hand is demonstration data. The notice keys
    /// off exactly this, so flipping the release to `real` removes it and
    /// changes nothing else.
    public var isDemonstrationData: Bool {
        meta?.envelope.dataMode == .demo
    }

    public var releaseId: String? { meta?.envelope.releaseId }

    public init(
        core: any PoraviaCoreClient,
        coreSelection: CoreClientFactory.Selection,
        settings: AppSettings = .shared,
        wallet: TicketStore = .shared,
        reminders: ReminderScheduler = .shared,
        sessionStore: SessionStore = SessionStore()
    ) {
        self.core = core
        self.coreSelection = coreSelection
        self.settings = settings
        self.wallet = wallet
        self.reminders = reminders
        self.sessionStore = sessionStore
        // A restored session keeps the reader's service date. A fresh one asks
        // the core what today's service date is, rather than computing one.
        self.session = sessionStore.load()
            ?? SessionState(serviceDate: core.currentServiceDate(now: Date()))
        self.route = RouteState()
    }

    #if DEBUG
    /// Debug only. Seeds the search with two place identifiers so a screenshot
    /// or UI-test run can reach a populated results screen without a tap
    /// driver. Used as `-PoraviaSeedSearch <originId>,<destinationId>`.
    /// A Release build has no fixture and ignores this entirely.
    static func seededSearch(
        arguments: [String] = ProcessInfo.processInfo.arguments
    ) -> (origin: String, destination: String)? {
        guard let index = arguments.firstIndex(of: "-PoraviaSeedSearch"),
              arguments.count > index + 1
        else { return nil }
        let parts = arguments[index + 1].split(separator: ",").map(String.init)
        guard parts.count == 2 else { return nil }
        return (parts[0], parts[1])
    }
    #endif

    public static func makeDefault() -> AppModel {
        let settings = AppSettings.shared
        let config = CoreConfig(
            apiBaseUrl: Brand.websiteURLString + "/v1",
            staticPacksBaseUrl: Brand.websiteURLString + "/v1/offline",
            packsDirectory: ApplicationPaths.packsDirectory.path(percentEncoded: false),
            databasePath: ApplicationPaths.databaseFile.path(percentEncoded: false),
            languageTag: settings.effectiveLanguageTag
        )
        // A Debug run can be launched on the fixture so every state can be
        // exercised on a simulator with no server. A Release build ignores it,
        // because it has no fixture to select.
        let (client, selection) = CoreClientFactory.make(
            config: config,
            preferFixture: CoreClientFactory.fixtureWasRequested()
        )
        let model = AppModel(core: client, coreSelection: selection, settings: settings)
        #if DEBUG
        if let seed = seededSearch() {
            model.session.originId = seed.origin
            model.session.destinationId = seed.destination
        }
        #endif
        return model
    }

    // MARK: Meta

    public func loadMeta(force: Bool = false) async {
        guard force || (meta == nil && metaError == nil) else { return }
        isLoadingMeta = true
        defer { isLoadingMeta = false }
        do {
            meta = try await core.meta()
            metaError = nil
        } catch let error as CoreError {
            metaError = error
            log.error("meta failed: \(String(describing: error))")
        } catch {
            metaError = CoreErrorTranslation.translate(error)
        }
    }

    // MARK: Session persistence

    /// Writes the restorable session. Called when a value that must survive a
    /// relaunch changes, never on every keystroke.
    public func persistSession() {
        sessionStore.save(session)
    }

    public func clearRecentsAndSession() {
        session = SessionState(serviceDate: core.currentServiceDate(now: Date()))
        persistSession()
    }

    /// Today's service date, as the core defines it in `Europe/Athens`.
    public var today: ServiceDate { core.currentServiceDate(now: Date()) }

    /// The service date `days` from the one in hand. The core decides.
    public func serviceDate(_ date: ServiceDate, shiftedBy days: Int) -> ServiceDate {
        core.shiftServiceDate(date, byDays: days)
    }
}

// MARK: - Session state

/// Everything that must survive a resize, rotation, posture change,
/// multitasking transition, background trip or relaunch.
///
/// A geometry change never touches this structure, which is why changing the
/// window cannot re-issue a search, restart a download, duplicate a reminder or
/// reset a purchase handoff.
public struct SessionState: Codable, Equatable, Sendable {
    public var originId: String?
    public var destinationId: String?
    public var originName: LocalisedText?
    public var destinationName: LocalisedText?
    public var serviceDate: ServiceDate
    public var filters: JourneyFilters
    /// The journey the reader last opened, so it comes back selected.
    public var selectedJourneyId: String?
    /// The stop the results list was scrolled to, restored as a scroll anchor.
    public var resultsScrollAnchorId: String?
    /// Whether the reader had asked for the map, so the same pane comes back.
    public var mapIntent: Bool
    /// The ticket that was open. Only the identifier is kept: the document
    /// itself is never persisted outside its protected container file.
    public var viewingTicketId: String?
    public var selectedTab: MainTab

    public init(
        originId: String? = nil,
        destinationId: String? = nil,
        originName: LocalisedText? = nil,
        destinationName: LocalisedText? = nil,
        serviceDate: ServiceDate,
        filters: JourneyFilters = .none,
        selectedJourneyId: String? = nil,
        resultsScrollAnchorId: String? = nil,
        mapIntent: Bool = false,
        viewingTicketId: String? = nil,
        selectedTab: MainTab = .search
    ) {
        self.originId = originId
        self.destinationId = destinationId
        self.originName = originName
        self.destinationName = destinationName
        self.serviceDate = serviceDate
        self.filters = filters
        self.selectedJourneyId = selectedJourneyId
        self.resultsScrollAnchorId = resultsScrollAnchorId
        self.mapIntent = mapIntent
        self.viewingTicketId = viewingTicketId
        self.selectedTab = selectedTab
    }

    public var canSearch: Bool {
        originId != nil && destinationId != nil
    }
}

public enum MainTab: String, Codable, CaseIterable, Sendable, Identifiable {
    case search, trips, offline, settings
    public var id: String { rawValue }

    public var title: String {
        switch self {
        case .search: L10n.tabSearch
        case .trips: L10n.tabTrips
        case .offline: L10n.tabOffline
        case .settings: L10n.tabSettings
        }
    }

    public var systemImage: String {
        switch self {
        case .search: "magnifyingglass"
        case .trips: "bookmark"
        case .offline: "arrow.down.circle"
        case .settings: "gearshape"
        }
    }
}

/// Navigation destinations pushed on top of a tab.
public enum Destination: Hashable, Codable, Sendable {
    case journey(id: String, serviceDate: ServiceDate)
    case operatorPage(id: String)
    case stop(id: String, serviceDate: ServiceDate)
    case wallet
    case sources
    case coverage
    case licences
}

/// The navigation stacks. Kept separate from `SessionState` because a path is
/// rebuilt from the session on launch rather than trusted verbatim.
@Observable
@MainActor
public final class RouteState {
    public var searchPath: [Destination] = []
    public var tripsPath: [Destination] = []
    public var offlinePath: [Destination] = []
    public var settingsPath: [Destination] = []
    /// A link that could not be resolved, shown as an honest dead end.
    public var unresolvedLink: UnresolvedLink?

    public init() {}

    public func path(for tab: MainTab) -> [Destination] {
        switch tab {
        case .search: searchPath
        case .trips: tripsPath
        case .offline: offlinePath
        case .settings: settingsPath
        }
    }

    public func setPath(_ path: [Destination], for tab: MainTab) {
        switch tab {
        case .search: searchPath = path
        case .trips: tripsPath = path
        case .offline: offlinePath = path
        case .settings: settingsPath = path
        }
    }
}

public struct UnresolvedLink: Equatable, Identifiable, Sendable {
    /// Conforms to `Error` so link resolution can be expressed as a `Result`.
    public enum Reason: Equatable, Sendable, Error {
        case unknown
        case expired(ServiceDate)
    }

    public let id = UUID()
    public var url: URL
    public var reason: Reason

    public init(url: URL, reason: Reason) {
        self.url = url
        self.reason = reason
    }
}

// MARK: - Persistence

/// Stores the session in `UserDefaults`. Small, non-personal and easy to
/// discard: a query, a date, filters and a few identifiers.
/// `UserDefaults` is thread-safe but not `Sendable`, so the guarantee is
/// asserted here rather than propagated through every caller.
public struct SessionStore: @unchecked Sendable {
    private let defaults: UserDefaults
    private let key = "poravia.session"

    public init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
    }

    /// Returns `nil` when nothing usable was stored, so the caller can ask the
    /// core for today's service date instead of assuming one.
    public func load() -> SessionState? {
        guard let data = defaults.data(forKey: key) else { return nil }
        return try? JSONDecoder().decode(SessionState.self, from: data)
    }

    public func save(_ state: SessionState) {
        guard let data = try? JSONEncoder().encode(state) else { return }
        defaults.set(data, forKey: key)
    }
}

/// Where the application keeps its own files.
public enum ApplicationPaths {
    public static var applicationSupport: URL {
        let base = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
        let directory = base.appending(path: "Poravia", directoryHint: .isDirectory)
        try? FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        return directory
    }

    public static var packsDirectory: URL {
        let directory = applicationSupport.appending(path: "packs", directoryHint: .isDirectory)
        try? FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        return directory
    }

    public static var databaseFile: URL {
        applicationSupport.appending(path: "poravia.sqlite", directoryHint: .notDirectory)
    }

    /// Imported ticket files. Protected and excluded from backup by
    /// `TicketStore`.
    public static var ticketsDirectory: URL {
        let directory = applicationSupport.appending(path: "tickets", directoryHint: .isDirectory)
        try? FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        return directory
    }
}
