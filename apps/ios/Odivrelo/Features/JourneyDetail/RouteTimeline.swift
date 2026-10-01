import SwiftUI

/// The ordered stop list.
///
/// It carries every fact the map carries: sequence, name, arrival, departure,
/// time quality, and the pick-up and drop-off rule at each stop. A reader who
/// never opens the map loses nothing.
struct RouteTimeline: View {
    let calls: [JourneyStop]
    let language: String

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            ForEach(Array(calls.enumerated()), id: \.element.id) { index, call in
                row(call, isFirst: index == 0, isLast: index == calls.count - 1)
            }
        }
        .accessibilityElement(children: .contain)
        .accessibilityLabel(L10n.detailStops)
    }

    private func row(_ call: JourneyStop, isFirst: Bool, isLast: Bool) -> some View {
        let isBoard = call.segmentRole == .board
        let isAlight = call.segmentRole == .alight
        let isOutside = call.segmentRole == .beforeBoard || call.segmentRole == .afterAlight
        return HStack(alignment: .top, spacing: Theme.Space.small) {
            marker(isFirst: isFirst, isLast: isLast, isBoard: isBoard, isAlight: isAlight)

            VStack(alignment: .leading, spacing: Theme.Space.xxSmall) {
                Text(call.name.resolved(for: language))
                    .font(.body.weight(isBoard || isAlight || isFirst || isLast ? .semibold : .regular))
                    .foregroundStyle(Theme.Palette.textPrimary)
                    .fixedSize(horizontal: false, vertical: true)

                times(call)

                BadgeFlow {
                    segmentBadge(call.segmentRole)
                    BoardingRuleBadge(rule: call.pickup, isPickup: true)
                    BoardingRuleBadge(rule: call.dropoff, isPickup: false)
                    if call.timeQuality != .scheduled {
                        StatusBadge(
                            TimeQualityLabel.text(call.timeQuality),
                            systemImage: "clock.badge.questionmark",
                            tone: .warning
                        )
                    }
                }
            }
            .padding(.bottom, isLast ? 0 : Theme.Space.medium)
        }
        .opacity(isOutside ? 0.6 : 1)
        .accessibilityElement(children: .combine)
        .accessibilityLabel(accessibilityLabel(call))
    }

    @ViewBuilder
    private func segmentBadge(_ role: SegmentRole) -> some View {
        switch role {
        case .board:
            StatusBadge(L10n.detailSegmentBoard, systemImage: "figure.walk.arrival", tone: .warning)
        case .alight:
            StatusBadge(L10n.detailSegmentAlight, systemImage: "figure.walk.departure", tone: .info)
        case .beforeBoard, .afterAlight:
            StatusBadge(L10n.detailSegmentOutside, systemImage: "arrow.right.circle", tone: .neutral)
        default:
            EmptyView()
        }
    }

    private func marker(isFirst: Bool, isLast: Bool, isBoard: Bool, isAlight: Bool) -> some View {
        let emphasised = isFirst || isLast || isBoard || isAlight
        return VStack(spacing: 0) {
            Rectangle()
                .fill(isFirst ? Color.clear : Theme.Palette.primary.opacity(0.35))
                .frame(width: 2, height: 6)
            Circle()
                .fill(isBoard ? Theme.Palette.accent : emphasised ? Theme.Palette.primary : Theme.Palette.surface)
                .frame(width: emphasised ? 14 : 10, height: emphasised ? 14 : 10)
                .overlay {
                    Circle().stroke(isBoard ? Theme.Palette.accent : Theme.Palette.primary, lineWidth: 2)
                }
            Rectangle()
                .fill(isLast ? Color.clear : Theme.Palette.primary.opacity(0.35))
                .frame(width: 2)
                .frame(maxHeight: .infinity)
        }
        .frame(width: 16)
        .accessibilityHidden(true)
    }

    private func times(_ call: JourneyStop) -> some View {
        HStack(spacing: Theme.Space.small) {
            if let arrival = call.arrivalAt {
                labelled(L10n.searchDestination, Formatters.clock(arrival))
            }
            if let departure = call.departureAt {
                labelled(L10n.searchOrigin, Formatters.clock(departure))
            }
        }
    }

    private func labelled(_ label: String, _ value: String) -> some View {
        HStack(spacing: 4) {
            Text(label)
                .font(.caption2)
                .foregroundStyle(Theme.Palette.textSecondary)
            Text(value)
                .font(.subheadline.monospacedDigit())
                .foregroundStyle(Theme.Palette.textPrimary)
        }
    }

    private func accessibilityLabel(_ call: JourneyStop) -> String {
        var parts: [String] = ["\(call.sequence). \(call.name.resolved(for: language))"]
        switch call.segmentRole {
        case .board: parts.append(L10n.detailSegmentBoard)
        case .alight: parts.append(L10n.detailSegmentAlight)
        case .beforeBoard, .afterAlight: parts.append(L10n.detailSegmentOutside)
        default: break
        }
        if let arrival = call.arrivalAt {
            parts.append("\(L10n.searchDestination) \(Formatters.clock(arrival))")
        }
        if let departure = call.departureAt {
            parts.append("\(L10n.searchOrigin) \(Formatters.clock(departure))")
        }
        parts.append(BoardingRuleBadge.text(rule: call.pickup, isPickup: true))
        parts.append(BoardingRuleBadge.text(rule: call.dropoff, isPickup: false))
        if call.timeQuality != .scheduled {
            parts.append(TimeQualityLabel.text(call.timeQuality))
        }
        return parts.joined(separator: ", ")
    }
}

struct BoardingRuleBadge: View {
    let rule: BoardingRule
    let isPickup: Bool

    static func text(rule: BoardingRule, isPickup: Bool) -> String {
        switch (rule, isPickup) {
        case (.allowed, true): L10n.detailPickupAllowed
        case (.onRequest, true): L10n.detailPickupOnRequest
        case (.coordinateWithOperator, true): L10n.detailPickupCoordinate
        case (.notAllowed, true): L10n.detailPickupNotAllowed
        case (.allowed, false): L10n.detailDropoffAllowed
        case (.onRequest, false): L10n.detailDropoffOnRequest
        case (.coordinateWithOperator, false): L10n.detailDropoffCoordinate
        case (.notAllowed, false): L10n.detailDropoffNotAllowed
        }
    }

    var body: some View {
        StatusBadge(
            Self.text(rule: rule, isPickup: isPickup),
            systemImage: icon,
            tone: tone
        )
    }

    private var icon: String {
        switch rule {
        case .allowed: isPickup ? "arrow.up.circle" : "arrow.down.circle"
        case .onRequest: "hand.raised.circle"
        case .coordinateWithOperator: "phone.circle"
        case .notAllowed: "minus.circle"
        }
    }

    private var tone: Tone {
        switch rule {
        case .allowed: .success
        case .onRequest, .coordinateWithOperator: .warning
        case .notAllowed: .neutral
        }
    }
}
