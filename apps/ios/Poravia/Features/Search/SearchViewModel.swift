import Foundation
import Observation
import SwiftUI

/// Runs the journey search against the shared core and holds its result.
///
/// It decides nothing about timetables. It knows only whether a request is in
/// flight, what came back, and how to avoid issuing the same request twice.
@Observable
@MainActor
final class SearchViewModel {
    enum State: Equatable {
        case idle
        case loading
        case loaded(JourneySearchResult)
        case failed(CoreError)
    }

    private(set) var state: State = .idle
    private(set) var recents: [RecentSearch] = []
    private(set) var knownOperators: [OperatorSummary] = []

    private weak var model: AppModel?
    private var inFlight: Task<Void, Never>?
    /// The request that produced the current state. A repeat of the same
    /// request is ignored, which is what stops a rotation, a resize or a
    /// posture change from re-issuing an identical search.
    private var lastRequest: RequestKey?

    private struct RequestKey: Equatable {
        var originId: String
        var destinationId: String
        var serviceDate: ServiceDate
        var filters: JourneyFilters
    }

    func attach(_ model: AppModel) async {
        self.model = model
        await refreshRecents()
    }

    func refreshRecents() async {
        guard let model else { return }
        recents = (try? await model.core.recentSearches()) ?? []
    }

    /// Runs the search when the session names both places and the request is
    /// not the one already showing.
    func runIfReady() async {
        await run(force: false)
    }

    func run(force: Bool) async {
        guard let model,
              let originId = model.session.originId,
              let destinationId = model.session.destinationId
        else {
            state = .idle
            return
        }

        let key = RequestKey(
            originId: originId,
            destinationId: destinationId,
            serviceDate: model.session.serviceDate,
            filters: model.session.filters
        )

        if !force, key == lastRequest, case .loaded = state { return }
        if !force, key == lastRequest, case .loading = state { return }

        inFlight?.cancel()
        lastRequest = key
        state = .loading

        inFlight = Task { [weak self] in
            guard let self else { return }
            do {
                let result = try await model.core.searchJourneys(
                    originId: key.originId,
                    destinationId: key.destinationId,
                    serviceDate: key.serviceDate,
                    filters: key.filters
                )
                guard !Task.isCancelled else { return }
                state = .loaded(await Self.clarifyingOfflineGap(result, core: model.core))
                knownOperators = Self.operators(in: result)
                AccessibilityAnnouncer.announce(L10n.a11yLoadedAnnouncement(result.results.count))
                await refreshRecents()
            } catch let error as CoreError {
                guard !Task.isCancelled else { return }
                state = .failed(error)
            } catch {
                guard !Task.isCancelled else { return }
                state = .failed(CoreErrorTranslation.translate(error))
            }
        }
        await inFlight?.value
    }

    /// Separates "no service on this date" from "no timetable for this date on
    /// this device".
    ///
    /// A journeys pack is published per service date, named `journeys-<date>`,
    /// and only for dates the data actually names. When an empty answer came
    /// from installed packs and no pack exists for the date asked about, the
    /// honest statement is that this device has nothing to consult, not that
    /// nothing runs. The installed pack list is authoritative about what is on
    /// the device, so this reads a fact rather than inferring one.
    static func clarifyingOfflineGap(
        _ result: JourneySearchResult,
        core: any PoraviaCoreClient
    ) async -> JourneySearchResult {
        guard result.results.isEmpty,
              result.envelope.servedFromCache,
              result.unavailableReason == .noServiceOnDate
        else { return result }

        guard let installed = try? await core.installedPacks() else { return result }
        let packName = journeysPackName(for: result.query.date)
        guard !installed.contains(where: { $0.name == packName }) else { return result }

        var corrected = result
        corrected.unavailableReason = .noOfflineDataForDate
        return corrected
    }

    /// The canonical name of the journeys pack for a service date.
    static func journeysPackName(for date: ServiceDate) -> String {
        "journeys-\(date.iso)"
    }

    private static func operators(in result: JourneySearchResult) -> [OperatorSummary] {
        var seen: Set<String> = []
        var list: [OperatorSummary] = []
        for journey in result.results where !seen.contains(journey.operatorSummary.id) {
            seen.insert(journey.operatorSummary.id)
            list.append(journey.operatorSummary)
        }
        return list
    }
}
