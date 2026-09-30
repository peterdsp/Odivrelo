import SwiftUI

/// The result list and every state it can be in.
struct ResultsSection: View {
    @Environment(AppModel.self) private var model

    let state: SearchViewModel.State
    let geometry: WindowGeometry
    let selectedId: String?
    let showsSelection: Bool
    let onSelect: (Journey) -> Void
    let onRetry: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: Theme.Space.small) {
            switch state {
            case .idle:
                EmptyView()

            case .loading:
                StateMessageView(kind: .loading, title: L10n.commonLoading)
                    .accessibilityIdentifier("results.loading")

            case let .failed(error):
                CoreErrorView(error, retry: onRetry)
                    .accessibilityIdentifier("results.error")

            case let .loaded(result):
                loaded(result)
            }
        }
    }

    @ViewBuilder
    private func loaded(_ result: JourneySearchResult) -> some View {
        if result.results.isEmpty {
            StateMessageView(
                kind: .empty(systemImage: emptyIcon(result.unavailableReason)),
                title: emptyTitle(result.unavailableReason),
                message: emptyMessage(result.unavailableReason, result.coverage),
                retry: onRetry
            )
            .accessibilityIdentifier("results.empty")
        } else {
            HStack {
                Text(L10n.resultsCount(result.results.count))
                    .font(.headline)
                    .accessibilityAddTraits(.isHeader)
                Spacer()
                CoverageBadge(result.coverage)
            }

            if result.envelope.servedFromCache {
                InlineNotice(
                    text: L10n.resultsServedFromCache(
                        result.envelope.ageInDays().map(L10n.tripsReleaseAgeDays)
                            ?? L10n.tripsReleaseAgeUnknown
                    ),
                    systemImage: "shippingbox",
                    tone: .info
                )
            }

            if result.coverage == .partial {
                InlineNotice(
                    text: L10n.resultsPartialWarning,
                    systemImage: "circle.lefthalf.filled",
                    tone: .warning
                )
            }

            InlineNotice(
                text: L10n.resultsScheduledOnly,
                systemImage: "calendar.badge.clock",
                tone: .info
            )

            ForEach(result.results) { journey in
                JourneyCard(
                    journey: journey,
                    isSelected: showsSelection && journey.id == selectedId
                ) {
                    onSelect(journey)
                }
                .id(journey.id)
            }
        }
    }

    private func emptyIcon(_ reason: UnavailableReason?) -> String {
        switch reason {
        case .outsideCoverage: "map.circle"
        case .originEqualsDestination: "arrow.triangle.2.circlepath"
        case .noOfflineDataForDate: "arrow.down.circle.dotted"
        case .noServiceOnDate: "calendar.badge.exclamationmark"
        case .unstated, .none: "questionmark.circle"
        }
    }

    /// Each reason gets its own words. "No service on this date" and "no
    /// offline data for this date" are different answers, and an unrecognised
    /// reason is reported as unstated rather than as either of them.
    private func emptyTitle(_ reason: UnavailableReason?) -> String {
        switch reason {
        case .outsideCoverage: L10n.resultsEmptyOutsideCoverage
        case .originEqualsDestination: L10n.resultsEmptySamePlace
        case .noOfflineDataForDate: L10n.resultsEmptyNoOfflineData
        case .noServiceOnDate: L10n.resultsEmptyNoService
        case .unstated, .none: L10n.resultsEmptyReasonUnstated
        }
    }

    private func emptyMessage(_ reason: UnavailableReason?, _ coverage: CoverageState) -> String? {
        switch reason {
        case .noOfflineDataForDate: L10n.resultsEmptyNoOfflineDataHint
        case .unstated, .none: L10n.errorOfflineHint
        case .outsideCoverage: L10n.resultsEmptyOutsideCoverageHint
        default: coverage == .notCovered ? L10n.resultsEmptyOutsideCoverageHint : nil
        }
    }
}

/// A short banner inside a scrolling column. Always icon plus text.
struct InlineNotice: View {
    let text: String
    let systemImage: String
    let tone: Tone

    var body: some View {
        HStack(alignment: .firstTextBaseline, spacing: Theme.Space.xSmall) {
            Image(systemName: systemImage)
                .foregroundStyle(tone.text)
                .accessibilityHidden(true)
            Text(text)
                .font(.footnote)
                .foregroundStyle(tone.text)
                .fixedSize(horizontal: false, vertical: true)
        }
        .padding(Theme.Space.small)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(tone.surface, in: .rect(cornerRadius: Theme.Radius.medium))
        .accessibilityElement(children: .combine)
    }
}

/// Recent searches, shown before a search has been set up.
struct RecentSearchesSection: View {
    @Environment(AppModel.self) private var model

    let recents: [RecentSearch]
    let onChoose: (RecentSearch) -> Void

    var body: some View {
        SectionCard(L10n.searchRecent, systemImage: "clock.arrow.circlepath") {
            if recents.isEmpty {
                Text(L10n.searchRecentEmpty)
                    .font(.subheadline)
                    .foregroundStyle(Theme.Palette.textSecondary)
                    .frame(maxWidth: .infinity, alignment: .leading)
            } else {
                VStack(spacing: 0) {
                    ForEach(recents) { recent in
                        Button {
                            onChoose(recent)
                        } label: {
                            VStack(alignment: .leading, spacing: 2) {
                                Text(
                                    "\(recent.originName.resolved(for: language)) → "
                                        + recent.destinationName.resolved(for: language)
                                )
                                .font(.body.weight(.medium))
                                .foregroundStyle(Theme.Palette.textPrimary)
                                .multilineTextAlignment(.leading)
                                .fixedSize(horizontal: false, vertical: true)

                                Text(Formatters.shortServiceDate(recent.serviceDate))
                                    .font(.caption)
                                    .foregroundStyle(Theme.Palette.textSecondary)
                            }
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .frame(minHeight: Theme.Size.touchMinimum)
                            .contentShape(.rect)
                        }
                        .buttonStyle(.plain)
                        .accessibilityElement(children: .combine)

                        if recent.id != recents.last?.id { Divider() }
                    }
                }
            }
        }
    }

    private var language: String { model.settings.effectiveLanguageTag }
}
