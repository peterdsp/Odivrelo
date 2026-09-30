import SwiftUI

/// One journey in the result list.
///
/// Information order follows the design system: times first, then places, then
/// operator, then duration and stops, then source freshness and confidence, and
/// a fare only when the source published one.
struct JourneyCard: View {
    @Environment(AppModel.self) private var model
    @Environment(\.dynamicTypeSize) private var dynamicTypeSize

    let journey: Journey
    let isSelected: Bool
    let onOpen: () -> Void

    var body: some View {
        Button(action: onOpen) {
            VStack(alignment: .leading, spacing: Theme.Space.small) {
                times
                places
                operatorAndDuration
                BadgeFlow { badges }
                if let fare = journey.fare {
                    Text(Formatters.fare(fare))
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(Theme.Palette.textPrimary)
                } else {
                    Text(L10n.resultsNoFare)
                        .font(.footnote)
                        .foregroundStyle(Theme.Palette.textSecondary)
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(Theme.Space.medium)
            .background(Theme.Palette.surface, in: .rect(cornerRadius: Theme.Radius.large))
            .overlay {
                RoundedRectangle(cornerRadius: Theme.Radius.large)
                    .stroke(
                        isSelected ? Theme.Palette.primary : Theme.Palette.border,
                        lineWidth: isSelected ? 2 : 1
                    )
            }
            .contentShape(.rect)
        }
        .buttonStyle(.plain)
        .accessibilityElement(children: .combine)
        .accessibilityLabel(accessibilityLabel)
        .accessibilityValue(accessibilityValue)
        .accessibilityAddTraits(isSelected ? [.isButton, .isSelected] : .isButton)
        .accessibilityIdentifier("journey.\(journey.id)")
    }

    // MARK: Parts

    private var times: some View {
        ViewThatFits(in: .horizontal) {
            HStack(alignment: .firstTextBaseline, spacing: Theme.Space.small) {
                timeBlock(journey.departure, isArrival: false)
                Image(systemName: "arrow.right")
                    .font(.footnote.weight(.bold))
                    .foregroundStyle(Theme.Palette.textSecondary)
                    .accessibilityHidden(true)
                timeBlock(journey.arrival, isArrival: true)
                Spacer(minLength: 0)
            }
            VStack(alignment: .leading, spacing: Theme.Space.xxSmall) {
                timeBlock(journey.departure, isArrival: false)
                timeBlock(journey.arrival, isArrival: true)
            }
        }
    }

    private func timeBlock(_ call: JourneyEndpoint, isArrival: Bool) -> some View {
        VStack(alignment: .leading, spacing: 0) {
            Text(
                // A journey that crosses midnight shows the arrival with its
                // day, because the bare clock time would be ambiguous.
                isArrival && journey.crossesMidnight
                    ? Formatters.clockWithDay(call.at)
                    : Formatters.clock(call.at)
            )
            .font(.title2.weight(.bold).monospacedDigit())
            .foregroundStyle(Theme.Palette.textPrimary)

            if call.quality != .scheduled {
                Text(TimeQualityLabel.text(call.quality))
                    .font(.caption2.weight(.semibold))
                    .foregroundStyle(Theme.Palette.warningText)
            }
        }
    }

    private var places: some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(journey.departure.stopName.resolved(for: language))
                .font(.subheadline)
                .foregroundStyle(Theme.Palette.textSecondary)
                .fixedSize(horizontal: false, vertical: true)
            Text(journey.arrival.stopName.resolved(for: language))
                .font(.subheadline)
                .foregroundStyle(Theme.Palette.textSecondary)
                .fixedSize(horizontal: false, vertical: true)
        }
    }

    private var operatorAndDuration: some View {
        ViewThatFits(in: .horizontal) {
            HStack(spacing: Theme.Space.small) {
                operatorName
                Spacer(minLength: Theme.Space.small)
                durationAndStops
            }
            VStack(alignment: .leading, spacing: Theme.Space.xxSmall) {
                operatorName
                durationAndStops
            }
        }
    }

    private var operatorName: some View {
        // A neutral text badge. Operator artwork is never reproduced.
        Text(journey.operatorSummary.name.resolved(for: language))
            .font(.subheadline.weight(.semibold))
            .foregroundStyle(Theme.Palette.textPrimary)
            .fixedSize(horizontal: false, vertical: true)
    }

    private var durationAndStops: some View {
        HStack(spacing: Theme.Space.xSmall) {
            Text(Formatters.duration(minutes: journey.durationMinutes))
            Text("·").foregroundStyle(Theme.Palette.border).accessibilityHidden(true)
            Text(L10n.resultsIntermediateStops(journey.intermediateStopCount))
        }
        .font(.footnote)
        .foregroundStyle(Theme.Palette.textSecondary)
    }

    @ViewBuilder
    private var badges: some View {
        if journey.crossesMidnight {
            StatusBadge(L10n.resultsCrossesMidnight, systemImage: "moon.stars", tone: .warning)
        }
        FreshnessBadge(journey.freshness)
        ConfidenceBadge(journey.confidence)
        PositionQualityBadge(journey.positionQuality)
        if journey.arrival.quality == .approximate {
            StatusBadge(L10n.qualityApproximate, systemImage: "clock.badge.questionmark", tone: .warning)
        }
    }

    // MARK: Accessibility

    private var language: String { model.settings.effectiveLanguageTag }

    private var accessibilityLabel: String {
        L10n.a11yJourneyCard(
            Formatters.clock(journey.departure.at),
            journey.departure.stopName.resolved(for: language),
            journey.crossesMidnight
                ? Formatters.clockWithDay(journey.arrival.at)
                : Formatters.clock(journey.arrival.at),
            journey.arrival.stopName.resolved(for: language),
            journey.operatorSummary.name.resolved(for: language)
        )
    }

    /// The facts a sighted reader picks up from the badges, spoken as one
    /// value so the rotor does not have to walk five separate labels.
    private var accessibilityValue: String {
        var parts: [String] = [
            Formatters.duration(minutes: journey.durationMinutes),
            L10n.resultsIntermediateStops(journey.intermediateStopCount),
        ]
        if journey.crossesMidnight { parts.append(L10n.resultsArrivesNextDay) }
        parts.append(FreshnessBadge.text(journey.freshness))
        parts.append(Formatters.freshnessAge(journey.freshness))
        parts.append(ConfidenceBadge.text(journey.confidence))
        if let fare = journey.fare {
            parts.append(Formatters.fare(fare))
        } else {
            parts.append(L10n.resultsNoFare)
        }
        return parts.joined(separator: ", ")
    }
}

// MARK: - Shared badges

struct FreshnessBadge: View {
    private let freshness: Freshness

    init(_ freshness: Freshness) {
        self.freshness = freshness
    }

    static func text(_ freshness: Freshness) -> String {
        switch freshness.state {
        case .fresh: L10n.freshnessFresh
        case .aging: L10n.freshnessAging
        case .stale: L10n.freshnessStale
        }
    }

    var body: some View {
        StatusBadge(
            "\(Self.text(freshness)) · \(Formatters.freshnessAge(freshness))",
            systemImage: icon,
            tone: tone
        )
    }

    private var icon: String {
        switch freshness.state {
        case .fresh: "clock.badge.checkmark"
        case .aging: "clock.badge"
        case .stale: "clock.badge.exclamationmark"
        }
    }

    private var tone: Tone {
        switch freshness.state {
        case .fresh: .success
        case .aging: .warning
        case .stale: .error
        }
    }
}

struct ConfidenceBadge: View {
    private let confidence: Confidence

    init(_ confidence: Confidence) {
        self.confidence = confidence
    }

    static func text(_ confidence: Confidence) -> String {
        switch confidence {
        case .reviewed: L10n.confidenceReviewed
        case .candidate: L10n.confidenceCandidate
        }
    }

    var body: some View {
        StatusBadge(
            Self.text(confidence),
            systemImage: confidence == .reviewed ? "checkmark.shield" : "questionmark.circle",
            tone: confidence == .reviewed ? .success : .warning
        )
    }
}

/// Keeps scheduled, predicted, estimated and live visibly distinct. This
/// release publishes only `scheduled`, and nothing here animates a position.
struct PositionQualityBadge: View {
    private let quality: PositionQuality

    init(_ quality: PositionQuality) {
        self.quality = quality
    }

    var body: some View {
        switch quality {
        case .scheduled:
            StatusBadge(L10n.qualityScheduled, systemImage: "calendar", tone: .neutral)
        case .predicted:
            StatusBadge(L10n.qualityPredicted, systemImage: "chart.line.uptrend.xyaxis", tone: .info)
        case .estimated:
            StatusBadge(L10n.qualityEstimated, systemImage: "wand.and.stars", tone: .info)
        case .live:
            StatusBadge(L10n.qualityLive, systemImage: "dot.radiowaves.left.and.right", tone: .success)
        }
    }
}

enum TimeQualityLabel {
    static func text(_ quality: TimeQuality) -> String {
        switch quality {
        case .scheduled: L10n.qualityScheduled
        case .approximate: L10n.qualityApproximate
        case .unknown: L10n.qualityUnknownTime
        }
    }
}
