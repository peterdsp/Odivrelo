import AppIntents
import Foundation

/**
 App Intents for the one thing a traveller asks Odivrelo most often: when the
 next coach leaves a terminal.

 Everything here answers from the release already on the device, so it works in
 a tunnel and on a train, which is the whole point of shipping the timetables
 rather than calling a server for them.

 Three contracts are fixed from the moment this ships and must not be edited
 casually. A saved shortcut replays an intent by its **type name** carrying the
 entity ids it stored, so renaming `NextCoachIntent` or changing what
 `TerminalEntity.id` means breaks every shortcut someone has built. The spoken
 **phrases** are a separate contract: renaming one breaks voice invocation but
 not saved shortcuts. Adding is always safe.

 The non-Siri path is the app itself, which is the complete fallback: Stations
 lists every terminal, and a terminal's own screen shows the same departures
 this intent reads, from the same call. Nothing here is reachable only by voice.
 */

// MARK: - The terminal

/// A place a coach leaves from, as Shortcuts and Siri see it.
///
/// The id is the release's own stop id (`ks_…`), which is stable across
/// launches, devices and releases, so a shortcut saved today still resolves
/// next month. It is never a row number or an array index.
struct TerminalEntity: AppEntity {
    let id: String
    @Property(title: "Name") var name: String

    init(id: String, name: String) {
        self.id = id
        self.name = name
    }

    static var typeDisplayRepresentation: TypeDisplayRepresentation {
        TypeDisplayRepresentation(name: "Terminal")
    }

    var displayRepresentation: DisplayRepresentation {
        DisplayRepresentation(title: "\(name)")
    }

    static var defaultQuery: TerminalQuery { TerminalQuery() }
}

/// Finds terminals by id and by what someone typed or said.
struct TerminalQuery: EntityStringQuery {
    /// Batched on purpose: Shortcuts asks for several ids at once when it
    /// restores a saved shortcut, and one call per id would be a round trip per
    /// id against the pack store.
    func entities(for identifiers: [TerminalEntity.ID]) async throws -> [TerminalEntity] {
        let core = try IntentCore.make()
        let wanted = Set(identifiers)
        // The places query is the only lookup the core offers, so the ids are
        // matched against a single search rather than fetched one by one.
        let found = try await core.searchPlaces(query: "", limit: 500)
        return found.places
            .filter { wanted.contains($0.id) }
            .map { TerminalEntity(id: $0.id, name: $0.name.resolved(for: IntentCore.languageTag)) }
    }

    /// `EntityStringQuery` does not filter for you: whatever this returns is
    /// what the picker shows, so the match happens here.
    func entities(matching string: String) async throws -> [TerminalEntity] {
        let core = try IntentCore.make()
        let found = try await core.searchPlaces(query: string, limit: 25)
        let needle = string.folded()
        return found.places
            .filter { needle.isEmpty || $0.name.resolved(for: IntentCore.languageTag).folded().contains(needle) }
            .map { TerminalEntity(id: $0.id, name: $0.name.resolved(for: IntentCore.languageTag)) }
    }

    /// Offered before anyone types, and ranked by how much service a place has.
    ///
    /// Filtering to covered places is necessary but was not sufficient: the
    /// pack is in name order, so reading the first sixty and keeping the
    /// covered ones offered "11.500", "1ο ΓΥΜΝΑΣΙΟ" and "21 ΧΛΜ Δ/Σ" as the
    /// terminals to ask about. Ranking by boarding points offers Tripoli,
    /// Kerkyra, Kiato and Trikala instead. PlaceRanking holds the reasoning.
    func suggestedEntities() async throws -> [TerminalEntity] {
        let core = try IntentCore.make()
        let found = try await core.searchPlaces(query: "", limit: PlaceRanking.scanLimit)
        return PlaceRanking.suggestions(from: found.places, limit: 12)
            .map { TerminalEntity(id: $0.id, name: $0.name.resolved(for: IntentCore.languageTag)) }
    }
}

// MARK: - The intent

/// "When does the next coach leave ⟨terminal⟩?"
struct NextCoachIntent: AppIntent {
    static let title: LocalizedStringResource = "Next Coach"
    static let description = IntentDescription(
        "Reads the next departures from a terminal, with the time each one was last checked."
    )

    /// This answers out loud and in the Shortcuts result. It does not need the
    /// app in the foreground, so it does not ask for it.
    static let openAppWhenRun: Bool = false

    @Parameter(title: "Terminal")
    var terminal: TerminalEntity

    static var parameterSummary: some ParameterSummary {
        Summary("Next coach from \(\.$terminal)")
    }

    /// Not `@MainActor`: the core is an async boundary of its own and nothing
    /// here touches view state. This can also be re-run from the top by the
    /// system, which is safe because it only reads.
    func perform() async throws -> some IntentResult & ProvidesDialog {
        let core = try IntentCore.make()
        let today = ServiceDate.fromPicker(Date())
        let detail = try await core.stopDetail(stopId: terminal.id, serviceDate: today)

        let now = Date()
        let upcoming = detail.stop.departures
            .filter { $0.departure.at >= now }
            .sorted { $0.departure.at < $1.departure.at }

        guard let next = upcoming.first else {
            // A terminal with no departures left today is an ordinary answer,
            // not a failure, so it is spoken rather than thrown.
            return .result(
                dialog: IntentDialog("No more coaches leave \(terminal.name) today.")
            )
        }

        let time = next.departure.at.formatted(date: .omitted, time: .shortened)
        let destination = next.arrival.stopName.resolved(for: IntentCore.languageTag)
        let operatorName = next.operatorSummary.name.resolved(for: IntentCore.languageTag)

        if upcoming.count > 1 {
            let following = upcoming[1].departure.at
                .formatted(date: .omitted, time: .shortened)
            return .result(
                dialog: IntentDialog(
                    "The next coach from \(terminal.name) leaves at \(time) for \(destination), run by \(operatorName). The one after that leaves at \(following)."
                )
            )
        }

        return .result(
            dialog: IntentDialog(
                "The next coach from \(terminal.name) leaves at \(time) for \(destination), run by \(operatorName). It is the last one today."
            )
        )
    }
}

// MARK: - The shortcut

struct OdivreloShortcuts: AppShortcutsProvider {
    static var appShortcuts: [AppShortcut] {
        AppShortcut(
            intent: NextCoachIntent(),
            // Every phrase carries the application name, because the runtime
            // silently drops a phrase that does not. The spellings differ so a
            // speaker is not made to guess one exact sentence.
            phrases: [
                "Next coach in \(.applicationName)",
                "When is the next coach in \(.applicationName)",
                "Next departure in \(.applicationName)",
            ],
            shortTitle: "Next Coach",
            systemImageName: "bus.fill"
        )
    }
}

// MARK: - Support

/// Builds the core the intents read from.
///
/// Deliberately not an `@Dependency`: that must be registered at `App.init()`
/// and is a `fatalError` when it is not, and an intent can be run by the system
/// when the app has never been launched in the foreground. Building it here
/// costs a little and cannot be unregistered.
enum IntentCore {
    /// The system's language rather than the app's stored preference, which
    /// lives on the main actor and may not have been read when a shortcut runs.
    static var languageTag: String { Locale.current.identifier }

    static func make() throws -> any OdivreloCoreClient {
        let config = CoreConfig(
            apiBaseUrl: Brand.websiteURLString + "/v1",
            staticPacksBaseUrl: Brand.websiteURLString + "/v1/offline",
            packsDirectory: ApplicationPaths.packsDirectory.path(percentEncoded: false),
            databasePath: ApplicationPaths.databaseFile.path(percentEncoded: false),
            languageTag: Self.languageTag
        )
        let (client, _) = CoreClientFactory.make(config: config)
        return client
    }
}

private extension String {
    /// Accent- and case-insensitive, and tolerant of final sigma, so "λαρισα"
    /// finds "ΛΑΡΙΣΑ".
    func folded() -> String {
        folding(options: [.diacriticInsensitive, .caseInsensitive], locale: Locale(identifier: "el"))
            .replacingOccurrences(of: "ς", with: "σ")
    }
}
