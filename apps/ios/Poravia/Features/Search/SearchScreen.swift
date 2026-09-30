import SwiftUI

/// Search, results and detail.
///
/// In a compact window this is one column and the detail is pushed. In a
/// regular or wide window the same content becomes a split view with the
/// results beside the detail, so both panes carry real work. The decision comes
/// from `LayoutResolver` reading the actual container, never from a device.
struct SearchScreen: View {
    @Environment(AppModel.self) private var model
    @State private var search = SearchViewModel()
    @State private var pickingPlace: PlaceField?
    @State private var showingFilters = false

    let geometry: WindowGeometry
    let mode: LayoutMode

    var body: some View {
        content
            .navigationTitle(L10n.tabSearch)
            .background(Theme.Palette.background)
            .sheet(item: $pickingPlace) { field in
                PlacePickerSheet(field: field) { place in
                    apply(place, to: field)
                }
            }
            .sheet(isPresented: $showingFilters) {
                FiltersSheet(
                    filters: Binding(
                        get: { model.session.filters },
                        set: { model.session.filters = $0; model.persistSession() }
                    ),
                    operators: search.knownOperators
                )
            }
            .task(id: taskKey) {
                await search.attach(model)
                await search.runIfReady()
            }
    }

    /// Changes to this key re-run the search. It deliberately excludes every
    /// geometry value, so resizing, rotating, changing posture or entering
    /// Split View can never re-issue the same search.
    private var taskKey: String {
        [
            model.session.originId ?? "",
            model.session.destinationId ?? "",
            model.session.serviceDate.iso,
            String(describing: model.session.filters),
        ].joined(separator: "|")
    }

    @ViewBuilder
    private var content: some View {
        switch mode {
        case .singleColumn:
            singleColumn
        case .twoColumn, .threeColumn:
            splitColumns
        }
    }

    private var singleColumn: some View {
        ScrollViewReader { proxy in
            ScrollView {
                VStack(alignment: .leading, spacing: Theme.Space.medium) {
                    if model.isDemonstrationData { DemoDataNotice() }
                    form
                    resultsSection
                }
                .readableColumn(geometry)
                .padding(.vertical, Theme.Space.medium)
            }
            .scrollDismissesKeyboard(.interactively)
            .onAppear { restoreScroll(proxy) }
        }
    }

    /// Results beside the detail. Both panes stay reachable when only one fits:
    /// the detail column keeps its own scroll view and an empty state that
    /// explains what to pick.
    private var splitColumns: some View {
        HStack(alignment: .top, spacing: Theme.Space.medium) {
            ScrollViewReader { proxy in
                ScrollView {
                    VStack(alignment: .leading, spacing: Theme.Space.medium) {
                        if model.isDemonstrationData { DemoDataNotice() }
                        form
                        resultsSection
                    }
                    .padding(.vertical, Theme.Space.medium)
                }
                .onAppear { restoreScroll(proxy) }
            }
            .frame(maxWidth: primaryPaneWidth)

            Divider()

            detailPane
                .frame(maxWidth: .infinity)
        }
        .padding(.horizontal, LayoutResolver.sidePadding(for: geometry))
        .avoidingReservedRegions(geometry)
    }

    private var primaryPaneWidth: CGFloat {
        let usable = geometry.usableWidth - Theme.Space.medium * 3
        return max(LayoutResolver.minimumPaneWidth, min(usable * 0.42, 520))
    }

    @ViewBuilder
    private var detailPane: some View {
        if let id = model.session.selectedJourneyId {
            JourneyDetailScreen(journeyId: id, serviceDate: model.session.serviceDate, isEmbedded: true)
                .id(id)
        } else {
            StateMessageView(
                kind: .empty(systemImage: "sidebar.right"),
                title: L10n.resultsTitle,
                message: L10n.placeDisambiguationHint
            )
        }
    }

    // MARK: Form

    private var form: some View {
        SectionCard(L10n.searchTitle, systemImage: "magnifyingglass") {
            VStack(spacing: Theme.Space.small) {
                placeField(.origin)
                HStack {
                    Spacer()
                    Button {
                        swapPlaces()
                    } label: {
                        Image(systemName: "arrow.up.arrow.down")
                            .frame(width: Theme.Size.touchMinimum, height: Theme.Size.touchMinimum)
                    }
                    .accessibilityLabel(L10n.searchSwap)
                    .accessibilityIdentifier("search.swap")
                    .disabled(model.session.originId == nil && model.session.destinationId == nil)
                    Spacer()
                }
                placeField(.destination)

                Divider()

                dateField

                HStack(spacing: Theme.Space.small) {
                    Button {
                        showingFilters = true
                    } label: {
                        Label(
                            model.session.filters.isActive
                                ? L10n.filtersActiveCount(model.session.filters.activeCount)
                                : L10n.filtersTitle,
                            systemImage: "line.3.horizontal.decrease.circle"
                        )
                    }
                    .buttonStyle(PoraviaSecondaryButtonStyle())
                    .accessibilityIdentifier("search.filters")

                    Spacer(minLength: 0)
                }

                Button(L10n.searchSubmit) {
                    Task { await search.run(force: true) }
                }
                .buttonStyle(PoraviaPrimaryButtonStyle())
                .disabled(!model.session.canSearch)
                .accessibilityIdentifier("search.submit")

                if !model.session.canSearch {
                    Text(L10n.searchNeedBothPlaces)
                        .font(.footnote)
                        .foregroundStyle(Theme.Palette.textSecondary)
                        .frame(maxWidth: .infinity, alignment: .leading)
                }
            }
        }
    }

    private func placeField(_ field: PlaceField) -> some View {
        Button {
            pickingPlace = field
        } label: {
            HStack(alignment: .firstTextBaseline, spacing: Theme.Space.small) {
                Image(systemName: field == .origin ? "circle" : "mappin.and.ellipse")
                    .foregroundStyle(Theme.Palette.primary)
                    .accessibilityHidden(true)
                VStack(alignment: .leading, spacing: 2) {
                    Text(field.label)
                        .font(.caption.weight(.semibold))
                        .foregroundStyle(Theme.Palette.textSecondary)
                    Text(currentName(field) ?? field.placeholder)
                        .font(.body.weight(.medium))
                        .foregroundStyle(
                            currentName(field) == nil
                                ? Theme.Palette.textSecondary
                                : Theme.Palette.textPrimary
                        )
                        .multilineTextAlignment(.leading)
                        .fixedSize(horizontal: false, vertical: true)
                }
                Spacer(minLength: 0)
                Image(systemName: "chevron.right")
                    .font(.caption.weight(.bold))
                    .foregroundStyle(Theme.Palette.textSecondary)
                    .accessibilityHidden(true)
            }
            .frame(minHeight: Theme.Size.control)
            .padding(.horizontal, Theme.Space.small)
            .background(Theme.Palette.surfaceMuted, in: .rect(cornerRadius: Theme.Radius.medium))
        }
        .buttonStyle(.plain)
        .accessibilityElement(children: .combine)
        .accessibilityLabel(field.label)
        .accessibilityValue(currentName(field) ?? field.placeholder)
        .accessibilityHint(L10n.placeDisambiguationHint)
        .accessibilityIdentifier(field == .origin ? "search.origin" : "search.destination")
    }

    private var dateField: some View {
        VStack(alignment: .leading, spacing: Theme.Space.xSmall) {
            DatePicker(
                L10n.searchDate,
                selection: Binding(
                    get: { model.session.serviceDate.pickerInstant },
                    set: { instant in
                        model.session.serviceDate = ServiceDate.fromPicker(instant)
                        model.persistSession()
                    }
                ),
                displayedComponents: .date
            )
            .environment(\.timeZone, ServiceDate.zone)
            .accessibilityIdentifier("search.date")

            HStack(spacing: Theme.Space.xSmall) {
                quickDate(L10n.searchToday, model.today)
                quickDate(L10n.searchTomorrow, model.serviceDate(model.today, shiftedBy: 1))
                Spacer(minLength: 0)
            }

            Text(L10n.searchDateZoneNote)
                .font(.caption)
                .foregroundStyle(Theme.Palette.textSecondary)
                .fixedSize(horizontal: false, vertical: true)
        }
    }

    private func quickDate(_ title: String, _ value: ServiceDate) -> some View {
        Button(title) {
            model.session.serviceDate = value
            model.persistSession()
        }
        .font(.footnote.weight(.semibold))
        .padding(.horizontal, Theme.Space.small)
        .frame(minHeight: Theme.Size.touchMinimum)
        .background(
            model.session.serviceDate == value ? Theme.Palette.surfaceMuted : Color.clear,
            in: .capsule
        )
        .foregroundStyle(Theme.Palette.primary)
        .accessibilityAddTraits(model.session.serviceDate == value ? .isSelected : [])
    }

    // MARK: Results

    @ViewBuilder
    private var resultsSection: some View {
        if model.session.canSearch {
            ResultsSection(
                state: search.state,
                geometry: geometry,
                selectedId: model.session.selectedJourneyId,
                showsSelection: mode != .singleColumn,
                onSelect: select(_:),
                onRetry: { Task { await search.run(force: true) } }
            )
        } else {
            RecentSearchesSection(recents: search.recents) { recent in
                model.session.originId = recent.originId
                model.session.originName = recent.originName
                model.session.destinationId = recent.destinationId
                model.session.destinationName = recent.destinationName
                model.session.serviceDate = recent.serviceDate
                model.persistSession()
            }
        }
    }

    // MARK: Actions

    private func currentName(_ field: PlaceField) -> String? {
        let value = field == .origin ? model.session.originName : model.session.destinationName
        guard let value else { return nil }
        let resolved = value.resolved(for: model.settings.effectiveLanguageTag)
        return resolved.isEmpty ? nil : resolved
    }

    private func apply(_ place: Place, to field: PlaceField) {
        switch field {
        case .origin:
            model.session.originId = place.id
            model.session.originName = place.name
        case .destination:
            model.session.destinationId = place.id
            model.session.destinationName = place.name
        }
        model.session.selectedJourneyId = nil
        model.persistSession()
        pickingPlace = nil
    }

    private func swapPlaces() {
        let id = model.session.originId
        let name = model.session.originName
        model.session.originId = model.session.destinationId
        model.session.originName = model.session.destinationName
        model.session.destinationId = id
        model.session.destinationName = name
        model.session.selectedJourneyId = nil
        model.persistSession()
    }

    private func select(_ journey: Journey) {
        model.session.selectedJourneyId = journey.id
        model.session.resultsScrollAnchorId = journey.id
        model.persistSession()
        if mode == .singleColumn {
            model.route.searchPath.append(
                .journey(id: journey.id, serviceDate: model.session.serviceDate)
            )
        }
    }

    private func restoreScroll(_ proxy: ScrollViewProxy) {
        guard let anchor = model.session.resultsScrollAnchorId else { return }
        // Restoring position must not animate, or a rotation would look like a
        // fresh load.
        proxy.scrollTo(anchor, anchor: .center)
    }
}

enum PlaceField: String, Identifiable, Sendable {
    case origin, destination
    var id: String { rawValue }

    var label: String {
        switch self {
        case .origin: L10n.searchOrigin
        case .destination: L10n.searchDestination
        }
    }

    var placeholder: String {
        switch self {
        case .origin: L10n.searchOriginPlaceholder
        case .destination: L10n.searchDestinationPlaceholder
        }
    }
}
