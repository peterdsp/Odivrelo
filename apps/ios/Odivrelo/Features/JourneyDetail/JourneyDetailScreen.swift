import SwiftUI

/// The full journey record: operating date, operator, the exact reviewed
/// boarding point, the ordered stop list with its pick-up and drop-off rules,
/// a map with a complete list alternative, restrictions, full provenance and
/// the official purchase handoff.
struct JourneyDetailScreen: View {
    @Environment(AppModel.self) private var model
    @Environment(\.windowGeometry) private var geometry
    @Environment(\.layoutMode) private var layoutMode

    let journeyId: String
    let serviceDate: ServiceDate
    var isEmbedded = false

    @State private var state: LoadState = .loading
    /// The saved trip the core returned, not a flag. The core owns the
    /// identifier format of a saved trip, so removal must use the id the core
    /// gave us rather than one Swift reassembles from a journey id and a date.
    @State private var savedTrip: SavedTrip?
    @State private var reminderOn = false
    @State private var showingReminderDenied = false
    /// Set when saving or removing a trip failed, so the button can stay
    /// truthful and the person is told why rather than left guessing.
    @State private var saveFailure: CoreError?
    @State private var purchaseURL: URL?

    private enum LoadState: Equatable {
        case loading
        case loaded(JourneyDetail)
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
        .navigationTitle(L10n.detailTitle)
        .navigationBarTitleDisplayMode(isEmbedded ? .inline : .large)
        .task(id: "\(journeyId)|\(serviceDate.iso)") { await load() }
        .sheet(item: Binding(get: { purchaseURL.map(IdentifiedURL.init) }, set: { purchaseURL = $0?.url })) { value in
            // The operator's own page, inside the app, returning to exactly
            // this screen with the same state.
            SafariSheet(url: value.url)
        }
        .alert(L10n.remindersDenied, isPresented: $showingReminderDenied) {
            Button(L10n.commonOpenSettings) { SystemSettings.open() }
            Button(L10n.commonCancel, role: .cancel) {}
        } message: {
            Text(L10n.remindersDeniedHint)
        }
        .alert(
            ErrorPresentation.of(saveFailure ?? .unexpected("")).title,
            isPresented: Binding(
                get: { saveFailure != nil },
                set: { if !$0 { saveFailure = nil } }
            ),
            presenting: saveFailure
        ) { _ in
            Button(L10n.commonClose, role: .cancel) { saveFailure = nil }
        } message: { error in
            Text(ErrorPresentation.of(error).message)
        }
    }

    private func content(_ detail: JourneyDetail) -> some View {
        ScrollView {
            VStack(alignment: .leading, spacing: Theme.Space.medium) {
                if model.isDemonstrationData { DemoDataNotice() }

                summary(detail)
                boarding(detail)
                actions(detail)
                PurchaseSection(
                    purchase: detail.journey.purchase,
                    operatorName: operatorName(detail)
                ) { url in
                    purchaseURL = url
                }
                stops(detail)
                RouteMapSection(detail: detail)
                restrictions(detail)
                ProvenanceSection(entries: detail.journey.provenance, correctionUrl: detail.journey.correctionUrl)
            }
            .readableColumn(geometry)
            .padding(.vertical, Theme.Space.medium)
        }
    }

    // MARK: Sections

    private func summary(_ detail: JourneyDetail) -> some View {
        let journey = detail.journey
        return SectionCard(L10n.detailOperatingDate, systemImage: "calendar") {
            VStack(alignment: .leading, spacing: Theme.Space.small) {
                LabelledValue(L10n.detailOperatingDate, value: Formatters.serviceDate(journey.serviceDate))
                LabelledValue(L10n.detailOperator, value: operatorName(detail))

                Divider()

                LabelledValue(
                    L10n.searchOrigin,
                    value: "\(Formatters.clock(journey.departure.at)) · "
                        + journey.departure.stopName.resolved(for: language)
                )
                LabelledValue(
                    L10n.searchDestination,
                    value: (journey.crossesMidnight
                        ? Formatters.clockWithDay(journey.arrival.at)
                        : Formatters.clock(journey.arrival.at))
                        + " · " + journey.arrival.stopName.resolved(for: language)
                )
                LabelledValue(
                    L10n.detailDuration,
                    value: Formatters.duration(minutes: journey.durationMinutes)
                        + " · " + L10n.resultsIntermediateStops(journey.intermediateStopCount)
                )

                if journey.crossesMidnight {
                    InlineNotice(
                        text: L10n.resultsArrivesNextDay,
                        systemImage: "moon.stars",
                        tone: .warning
                    )
                }

                if detail.envelope.servedFromCache {
                    InlineNotice(
                        text: L10n.resultsServedFromCache(
                            detail.envelope.ageInDays().map(L10n.tripsReleaseAgeDays)
                                ?? L10n.tripsReleaseAgeUnknown
                        ),
                        systemImage: "shippingbox",
                        tone: .info
                    )
                }

                BadgeFlow {
                    FreshnessBadge(journey.freshness)
                    ConfidenceBadge(journey.confidence)
                    PositionQualityBadge(journey.positionQuality)
                }

                InlineNotice(
                    text: L10n.resultsScheduledOnly,
                    systemImage: "calendar.badge.clock",
                    tone: .info
                )

                if let fare = journey.fare {
                    LabelledValue(L10n.detailFare, value: Formatters.fare(fare))
                } else {
                    LabelledValue(L10n.detailFare, value: L10n.resultsNoFare)
                }
            }
        }
    }

    @ViewBuilder
    private func boarding(_ detail: JourneyDetail) -> some View {
        SectionCard(L10n.detailBoardingPoint, systemImage: "signpost.right.and.left") {
            if let point = detail.journey.boardingPoint {
                VStack(alignment: .leading, spacing: Theme.Space.small) {
                    Text(point.name.resolved(for: language))
                        .font(.body.weight(.semibold))
                        .foregroundStyle(Theme.Palette.textPrimary)
                        .fixedSize(horizontal: false, vertical: true)

                    if let terminal = point.terminalName {
                        LabelledValue(L10n.placeTerminal, value: terminal.resolved(for: language))
                    }
                    LabelledValue(
                        L10n.detailBoardingPoint,
                        value: point.bay.map(L10n.detailBay) ?? L10n.detailBayUnknown
                    )

                    BadgeFlow {
                        StepFreeBadge(point.stepFree)
                        ReviewStateBadge(point.reviewState)
                        if let reviewedAt = point.reviewedAt {
                            StatusBadge(
                                L10n.purchaseVerifiedAt(Formatters.timestamp(reviewedAt)),
                                systemImage: "checkmark.seal",
                                tone: .success
                            )
                        }
                    }

                    if let instructions = point.instructions {
                        VStack(alignment: .leading, spacing: 2) {
                            Text(L10n.detailInstructions)
                                .font(.caption.weight(.semibold))
                                .foregroundStyle(Theme.Palette.textSecondary)
                            Text(instructions.resolved(for: language))
                                .font(.subheadline)
                                .foregroundStyle(Theme.Palette.textPrimary)
                                .fixedSize(horizontal: false, vertical: true)
                        }
                    }
                }
            } else {
                // No reviewed boarding point exists for this journey. Saying so
                // is more useful than showing the terminal and letting a
                // traveller assume it is the right door.
                InlineNotice(
                    text: L10n.commonNotReviewed,
                    systemImage: "questionmark.circle",
                    tone: .warning
                )
            }
        }
    }

    private func actions(_ detail: JourneyDetail) -> some View {
        SectionCard(L10n.tripsTitle, systemImage: "bookmark") {
            VStack(alignment: .leading, spacing: Theme.Space.small) {
                Button(savedTrip != nil ? L10n.commonRemove : L10n.tripsSave) {
                    Task { await toggleSaved(detail) }
                }
                .buttonStyle(OdivreloPrimaryButtonStyle())
                .accessibilityIdentifier("detail.saveTrip")

                Toggle(L10n.remindersEnable, isOn: Binding(
                    get: { reminderOn },
                    set: { wanted in Task { await setReminder(wanted, detail: detail) } }
                ))
                .disabled(savedTrip == nil)
                .accessibilityIdentifier("detail.reminder")

                if reminderOn,
                   let fire = model.reminders.fireDate(journeyId: journeyId, serviceDate: serviceDate) {
                    Text(L10n.remindersScheduled(Formatters.timestamp(fire)))
                        .font(.footnote)
                        .foregroundStyle(Theme.Palette.textSecondary)
                }

                if model.reminders.authorisation == .revoked {
                    InlineNotice(text: L10n.remindersRevoked, systemImage: "bell.slash", tone: .warning)
                }

                Text(L10n.remindersPrivacyNote)
                    .font(.caption)
                    .foregroundStyle(Theme.Palette.textSecondary)
                    .fixedSize(horizontal: false, vertical: true)
            }
        }
    }

    private func stops(_ detail: JourneyDetail) -> some View {
        SectionCard(L10n.detailStops, systemImage: "list.bullet.indent") {
            VStack(alignment: .leading, spacing: Theme.Space.small) {
                Text(L10n.detailStopsListAlternative)
                    .font(.caption)
                    .foregroundStyle(Theme.Palette.textSecondary)
                    .fixedSize(horizontal: false, vertical: true)

                RouteTimeline(calls: detail.journey.stops, language: language)
            }
        }
    }

    @ViewBuilder
    private func restrictions(_ detail: JourneyDetail) -> some View {
        if !detail.journey.restrictions.isEmpty {
            SectionCard(L10n.detailRestrictions, systemImage: "exclamationmark.triangle") {
                VStack(alignment: .leading, spacing: Theme.Space.small) {
                    ForEach(detail.journey.restrictions) { restriction in
                        InlineNotice(
                            text: restriction.text.resolved(for: language),
                            systemImage: "info.circle",
                            tone: .warning
                        )
                    }
                }
            }
        }
    }

    // MARK: Behaviour

    private var language: String { model.settings.effectiveLanguageTag }

    private func operatorName(_ detail: JourneyDetail) -> String {
        detail.journey.operatorSummary.name.resolved(for: language)
    }

    private func load() async {
        state = .loading
        do {
            let detail = try await model.core.journeyDetail(journeyId: journeyId, serviceDate: serviceDate)
            state = .loaded(detail)
            let saved = (try? await model.core.savedTrips()) ?? []
            savedTrip = saved.first { $0.journeyId == journeyId && $0.serviceDate == serviceDate }
            reminderOn = model.reminders.isScheduled(journeyId: journeyId, serviceDate: serviceDate)
        } catch let error as CoreError {
            state = .failed(error)
        } catch {
            state = .failed(CoreErrorTranslation.translate(error))
        }
    }

    private func toggleSaved(_ detail: JourneyDetail) async {
        // The button must only change state once the core has agreed. Setting it
        // optimistically and swallowing the error would leave the label saying
        // "Remove" for a trip that is still saved, or the reverse.
        do {
            if let trip = savedTrip {
                try await model.core.removeSavedTrip(savedTripId: trip.id)
                model.reminders.cancel(journeyId: journeyId, serviceDate: serviceDate)
                savedTrip = nil
                reminderOn = false
            } else {
                savedTrip = try await model.core.saveTrip(
                    journeyId: journeyId,
                    serviceDate: serviceDate
                )
            }
        } catch {
            saveFailure = CoreErrorTranslation.translate(error)
        }
    }

    private func setReminder(_ wanted: Bool, detail: JourneyDetail) async {
        guard wanted else {
            model.reminders.cancel(journeyId: journeyId, serviceDate: serviceDate)
            reminderOn = false
            return
        }

        if model.reminders.authorisation == .notDetermined {
            await model.reminders.requestAuthorisation()
        }
        guard model.reminders.authorisation == .authorised else {
            showingReminderDenied = true
            reminderOn = false
            return
        }

        let scheduled = await model.reminders.schedule(
            .init(
                journeyId: journeyId,
                serviceDate: serviceDate,
                departure: detail.journey.departure.at,
                boardingPointName: detail.journey.boardingPoint?.name.resolved(for: language)
                    ?? detail.journey.departure.stopName.resolved(for: language),
                leadMinutes: model.settings.reminderLeadMinutes
            )
        )
        reminderOn = scheduled
    }
}

/// `URL` is not `Identifiable`; this wrapper lets it drive a sheet.
struct IdentifiedURL: Identifiable {
    let url: URL
    var id: String { url.absoluteString }

    init(_ url: URL) { self.url = url }
}

struct StepFreeBadge: View {
    private let stepFree: Bool?

    init(_ stepFree: Bool?) {
        self.stepFree = stepFree
    }

    var body: some View {
        switch stepFree {
        case .some(true):
            StatusBadge(L10n.detailStepFreeYes, systemImage: "figure.roll", tone: .success)
        case .some(false):
            StatusBadge(L10n.detailStepFreeNo, systemImage: "figure.stairs", tone: .error)
        case .none:
            // Never rendered as a no. Unreviewed is its own answer.
            StatusBadge(L10n.detailStepFreeUnreviewed, systemImage: "questionmark.circle", tone: .warning)
        }
    }
}

struct ReviewStateBadge: View {
    private let state: ReviewState

    init(_ state: ReviewState) {
        self.state = state
    }

    var body: some View {
        switch state {
        case .published, .verified:
            StatusBadge(L10n.confidenceReviewed, systemImage: "checkmark.shield", tone: .success)
        case .candidate:
            StatusBadge(L10n.confidenceCandidate, systemImage: "questionmark.circle", tone: .warning)
        case .stale:
            StatusBadge(L10n.freshnessStale, systemImage: "clock.badge.exclamationmark", tone: .error)
        case .withdrawn, .quarantined:
            StatusBadge(L10n.commonNotReviewed, systemImage: "exclamationmark.triangle", tone: .error)
        }
    }
}
