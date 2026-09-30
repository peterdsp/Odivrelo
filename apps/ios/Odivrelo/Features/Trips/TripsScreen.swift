import SwiftUI

/// Favourites, saved trips and Trip Ready.
///
/// Everything here works without a connection, because a saved trip carries the
/// journey exactly as it read when it was saved, together with the release it
/// came from. The cached release and its age are always visible, so a traveller
/// knows how old the schedule in their hand is.
struct TripsScreen: View {
    @Environment(AppModel.self) private var model

    let geometry: WindowGeometry

    @State private var trips: [SavedTrip] = []
    @State private var favourites: [FavouritePlace] = []
    @State private var error: CoreError?
    @State private var isLoading = true

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: Theme.Space.medium) {
                if model.isDemonstrationData { DemoDataNotice() }

                if isLoading {
                    StateMessageView(kind: .loading, title: L10n.commonLoading)
                } else if let error, trips.isEmpty, favourites.isEmpty {
                    CoreErrorView(error) { Task { await load() } }
                } else {
                    tripReady
                    savedTrips
                    favouritesSection
                    NavigationLink(value: Destination.wallet) {
                        Label(L10n.walletTitle, systemImage: "wallet.pass")
                            .frame(maxWidth: .infinity)
                            .frame(minHeight: Theme.Size.control)
                    }
                    .buttonStyle(OdivreloSecondaryButtonStyle())
                    .accessibilityIdentifier("trips.wallet")
                }
            }
            .readableColumn(geometry)
            .padding(.vertical, Theme.Space.medium)
        }
        .background(Theme.Palette.background)
        .navigationTitle(L10n.tripsTitle)
        .refreshable { await load() }
        .task { await load() }
    }

    // MARK: Sections

    @ViewBuilder
    private var tripReady: some View {
        if let next = trips.min(by: { $0.departureAt < $1.departureAt }) {
            SectionCard(L10n.tripsReadyTitle, systemImage: "checkmark.circle") {
                VStack(alignment: .leading, spacing: Theme.Space.small) {
                    Text(
                        next.originName.resolved(for: language)
                            + " → " + next.destinationName.resolved(for: language)
                    )
                    .font(.body.weight(.semibold))
                    .fixedSize(horizontal: false, vertical: true)

                    LabelledValue(
                        L10n.detailOperatingDate,
                        value: Formatters.serviceDate(next.serviceDate)
                    )
                    LabelledValue(L10n.searchOrigin, value: Formatters.clock(next.departureAt))
                    LabelledValue(L10n.detailOperator, value: next.operatorName.resolved(for: language))
                    LabelledValue(
                        L10n.detailBoardingPoint,
                        value: next.boardingBay.map(L10n.detailBay) ?? L10n.detailBayUnknown
                    )

                    // The map is the one thing a saved trip cannot carry
                    // offline, and that is said rather than left to be found.
                    InlineNotice(
                        text: L10n.tripsReadyBody,
                        systemImage: "checkmark.seal",
                        tone: .success
                    )
                    InlineNotice(
                        text: L10n.detailMapUnavailableOffline,
                        systemImage: "map",
                        tone: .info
                    )

                    releaseFooter(next)
                }
            }
        }
    }

    @ViewBuilder
    private var savedTrips: some View {
        SectionCard(L10n.tripsSavedHeading, systemImage: "bookmark") {
            if trips.isEmpty {
                Text(L10n.tripsEmpty)
                    .font(.subheadline)
                    .foregroundStyle(Theme.Palette.textSecondary)
                    .frame(maxWidth: .infinity, alignment: .leading)
            } else {
                VStack(alignment: .leading, spacing: Theme.Space.small) {
                    ForEach(trips) { trip in
                        NavigationLink(
                            value: Destination.journey(id: trip.journeyId, serviceDate: trip.serviceDate)
                        ) {
                            VStack(alignment: .leading, spacing: Theme.Space.xxSmall) {
                                Text(
                                    Formatters.clock(trip.departureAt)
                                        + " · " + trip.originName.resolved(for: language)
                                        + " → " + trip.destinationName.resolved(for: language)
                                )
                                .font(.body.weight(.medium))
                                .foregroundStyle(Theme.Palette.textPrimary)
                                .multilineTextAlignment(.leading)
                                .fixedSize(horizontal: false, vertical: true)

                                Text(Formatters.serviceDate(trip.serviceDate))
                                    .font(.caption)
                                    .foregroundStyle(Theme.Palette.textSecondary)

                                BadgeFlow {
                                    StepFreeBadge(trip.boardingStepFree)
                                    if trip.crossesMidnight {
                                        StatusBadge(
                                            L10n.resultsCrossesMidnight,
                                            systemImage: "moon.stars",
                                            tone: .warning
                                        )
                                    }
                                    if model.reminders.isScheduled(
                                        journeyId: trip.journeyId,
                                        serviceDate: trip.serviceDate
                                    ) {
                                        StatusBadge(L10n.remindersTitle, systemImage: "bell", tone: .info)
                                    }
                                }

                                releaseFooter(trip)
                            }
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .contentShape(.rect)
                        }
                        .accessibilityIdentifier("trip.\(trip.id)")
                        .swipeActions {
                            Button(L10n.commonRemove, role: .destructive) {
                                Task { await remove(trip) }
                            }
                        }

                        if trip.id != trips.last?.id { Divider() }
                    }
                }
            }
        }
    }

    @ViewBuilder
    private var favouritesSection: some View {
        SectionCard(L10n.favouritesTitle, systemImage: "star") {
            if favourites.isEmpty {
                Text(L10n.favouritesEmpty)
                    .font(.subheadline)
                    .foregroundStyle(Theme.Palette.textSecondary)
                    .frame(maxWidth: .infinity, alignment: .leading)
            } else {
                VStack(alignment: .leading, spacing: 0) {
                    ForEach(favourites) { favourite in
                        NavigationLink(
                            value: Destination.stop(
                                id: favourite.placeId,
                                serviceDate: model.session.serviceDate
                            )
                        ) {
                            HStack {
                                Text(favourite.name.resolved(for: language))
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

                        if favourite.id != favourites.last?.id { Divider() }
                    }
                }
            }
        }
    }

    /// States the cached release and how old it is. A saved trip is only as
    /// good as the release it came from, so that is never hidden.
    private func releaseFooter(_ trip: SavedTrip) -> some View {
        let days = trip.cachedAgeInDays()
        return BadgeFlow {
            StatusBadge(
                L10n.tripsCachedRelease(String(trip.releaseId.prefix(8))),
                systemImage: "shippingbox",
                tone: .neutral
            )
            StatusBadge(
                L10n.tripsReleaseAgeDays(days),
                systemImage: "calendar",
                tone: days > 7 ? .warning : .neutral
            )
        }
    }

    // MARK: Behaviour

    private var language: String { model.settings.effectiveLanguageTag }

    private func load() async {
        isLoading = true
        defer { isLoading = false }
        do {
            trips = try await model.core.savedTrips()
            favourites = try await model.core.favorites()
            error = nil
        } catch let coreError as CoreError {
            error = coreError
        } catch {
            self.error = CoreErrorTranslation.translate(error)
        }
        await model.reminders.refreshAuthorisation()
    }

    private func remove(_ trip: SavedTrip) async {
        try? await model.core.removeSavedTrip(savedTripId: trip.id)
        model.reminders.cancel(journeyId: trip.journeyId, serviceDate: trip.serviceDate)
        await load()
    }
}
