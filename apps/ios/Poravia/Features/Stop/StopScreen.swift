import SwiftUI

/// The station page: names, boarding points with their bays and step-free
/// state, the operators that serve it, the next departures for the chosen
/// service date, provenance and the correction path.
struct StopScreen: View {
    @Environment(AppModel.self) private var model
    @Environment(\.windowGeometry) private var geometry

    let stopId: String
    let serviceDate: ServiceDate

    @State private var state: State = .loading
    @State private var isFavourite = false

    private enum State: Equatable {
        case loading
        case loaded(StopDetail)
        case failed(CoreError)
    }

    var body: some View {
        Group {
            switch state {
            case .loading:
                StateMessageView(kind: .loading, title: L10n.commonLoading)
            case let .failed(error):
                CoreErrorView(error) { Task { await load() } }
            case let .loaded(detail):
                content(detail)
            }
        }
        .background(Theme.Palette.background)
        .navigationTitle(L10n.stopTitle)
        .task(id: "\(stopId)|\(serviceDate.iso)") { await load() }
    }

    private func content(_ detail: StopDetail) -> some View {
        let stop = detail.stop
        return ScrollView {
            VStack(alignment: .leading, spacing: Theme.Space.medium) {
                if model.isDemonstrationData { DemoDataNotice() }

                SectionCard(stop.name.resolved(for: language), systemImage: "building.2") {
                    VStack(alignment: .leading, spacing: Theme.Space.small) {
                        if let parentName = stop.parentName {
                            LabelledValue(L10n.placeTerminal, value: parentName.resolved(for: language))
                        }
                        LabelledValue(
                            L10n.placeTerminal,
                            value: stop.municipality ?? L10n.commonNotStated
                        )
                        BadgeFlow {
                            StatusBadge(
                                stop.kind == .stopPlace ? L10n.placeTerminal : L10n.placeBoardingPoint,
                                systemImage: stop.kind == .stopPlace ? "building.2" : "signpost.right",
                                tone: .brand
                            )
                            CoverageBadge(stop.coverage)
                            if let bay = stop.bay {
                                StatusBadge(L10n.detailBay(bay), systemImage: "number", tone: .neutral)
                            }
                        }

                        if let address = stop.address, !address.isEmpty {
                            LabelledValue(L10n.purchaseAddress, value: address)
                        }
                        if let instructions = stop.instructions {
                            LabelledValue(
                                L10n.detailInstructions,
                                value: instructions.resolved(for: language)
                            )
                        }

                        Button(isFavourite ? L10n.favouritesRemove : L10n.favouritesAdd) {
                            Task {
                                isFavourite = (try? await model.core.toggleFavorite(placeId: stop.id))
                                    ?? isFavourite
                            }
                        }
                        .buttonStyle(PoraviaSecondaryButtonStyle())
                        .accessibilityIdentifier("stop.favourite")
                    }
                }

                if !stop.boardingPoints.isEmpty {
                    SectionCard(L10n.stopBoardingPoints, systemImage: "signpost.right.and.left") {
                        VStack(alignment: .leading, spacing: Theme.Space.small) {
                            ForEach(stop.boardingPoints, id: \.stopId) { point in
                                VStack(alignment: .leading, spacing: Theme.Space.xxSmall) {
                                    Text(point.name.resolved(for: language))
                                        .font(.body.weight(.medium))
                                        .fixedSize(horizontal: false, vertical: true)
                                    BadgeFlow {
                                        StatusBadge(
                                            point.bay.map(L10n.detailBay) ?? L10n.detailBayUnknown,
                                            systemImage: "number",
                                            tone: .neutral
                                        )
                                        StepFreeBadge(point.stepFree)
                                        ReviewStateBadge(point.reviewState)
                                    }
                                }
                                .frame(maxWidth: .infinity, alignment: .leading)
                                .accessibilityElement(children: .combine)

                                if point.stopId != stop.boardingPoints.last?.stopId { Divider() }
                            }
                        }
                    }
                }

                if !stop.operators.isEmpty {
                    SectionCard(L10n.stopOperators, systemImage: "bus") {
                        VStack(alignment: .leading, spacing: 0) {
                            ForEach(stop.operators) { value in
                                NavigationLink(value: Destination.operatorPage(id: value.id)) {
                                    HStack {
                                        Text(value.name.resolved(for: language))
                                            .foregroundStyle(Theme.Palette.textPrimary)
                                            .multilineTextAlignment(.leading)
                                            .fixedSize(horizontal: false, vertical: true)
                                        Spacer()
                                        Image(systemName: "chevron.right")
                                            .font(.caption.weight(.bold))
                                            .foregroundStyle(Theme.Palette.textSecondary)
                                    }
                                    .frame(minHeight: Theme.Size.touchMinimum)
                                    .contentShape(.rect)
                                }
                                if value.id != stop.operators.last?.id { Divider() }
                            }
                        }
                    }
                }

                SectionCard(L10n.stopNextDepartures, systemImage: "clock") {
                    if stop.departures.isEmpty {
                        Text(L10n.stopNoDepartures)
                            .font(.subheadline)
                            .foregroundStyle(Theme.Palette.textSecondary)
                            .frame(maxWidth: .infinity, alignment: .leading)
                    } else {
                        VStack(alignment: .leading, spacing: Theme.Space.small) {
                            LabelledValue(
                                L10n.searchDate,
                                value: Formatters.serviceDate(detail.serviceDate)
                            )
                            ForEach(stop.departures) { departure in
                                NavigationLink(
                                    value: Destination.journey(
                                        id: departure.id,
                                        serviceDate: departure.serviceDate
                                    )
                                ) {
                                    departureRow(departure)
                                }
                                .accessibilityIdentifier("stop.departure.\(departure.id)")
                            }
                        }
                    }
                }

                ProvenanceSection(entries: stop.provenance, correctionUrl: stop.correctionUrl)
            }
            .readableColumn(geometry)
            .padding(.vertical, Theme.Space.medium)
        }
    }

    /// A departure is a full journey, so the row can carry the same freshness
    /// and confidence facts a result row does rather than a thinner summary.
    private func departureRow(_ departure: Journey) -> some View {
        HStack(alignment: .firstTextBaseline, spacing: Theme.Space.small) {
            Text(Formatters.clock(departure.departure.at))
                .font(.body.weight(.semibold).monospacedDigit())
                .foregroundStyle(Theme.Palette.textPrimary)
            VStack(alignment: .leading, spacing: 2) {
                Text(departure.arrival.stopName.resolved(for: language))
                    .foregroundStyle(Theme.Palette.textPrimary)
                    .multilineTextAlignment(.leading)
                    .fixedSize(horizontal: false, vertical: true)
                Text(departure.operatorSummary.name.resolved(for: language))
                    .font(.caption)
                    .foregroundStyle(Theme.Palette.textSecondary)
                BadgeFlow {
                    if departure.crossesMidnight {
                        StatusBadge(
                            L10n.stopCrossesMidnight,
                            systemImage: "moon.stars",
                            tone: .warning
                        )
                    }
                    FreshnessBadge(departure.freshness)
                    ConfidenceBadge(departure.confidence)
                    if departure.departure.quality != .scheduled {
                        StatusBadge(
                            TimeQualityLabel.text(departure.departure.quality),
                            systemImage: "clock.badge.questionmark",
                            tone: .warning
                        )
                    }
                }
            }
            Spacer(minLength: 0)
            Image(systemName: "chevron.right")
                .font(.caption.weight(.bold))
                .foregroundStyle(Theme.Palette.textSecondary)
        }
        .frame(minHeight: Theme.Size.touchMinimum)
        .contentShape(.rect)
        .accessibilityElement(children: .combine)
    }

    private var language: String { model.settings.effectiveLanguageTag }

    private func load() async {
        state = .loading
        do {
            state = .loaded(try await model.core.stopDetail(stopId: stopId, serviceDate: serviceDate))
            let favourites = (try? await model.core.favorites()) ?? []
            isFavourite = favourites.contains { $0.placeId == stopId }
        } catch let error as CoreError {
            state = .failed(error)
        } catch {
            state = .failed(CoreErrorTranslation.translate(error))
        }
    }
}
