import SwiftUI

/// Place disambiguation.
///
/// A terminal and the boarding points inside it are different places with
/// different identifiers, and choosing the wrong one sends a traveller to the
/// wrong side of a station. The picker shows the terminal first, marks it as a
/// terminal, and nests its boarding points underneath with their bay where the
/// source publishes one.
struct PlacePickerSheet: View {
    @Environment(AppModel.self) private var model
    @Environment(\.dismiss) private var dismiss

    let field: PlaceField
    let onChoose: (Place) -> Void

    @State private var query = ""
    @State private var state: LoadState = .idle
    @State private var searchTask: Task<Void, Never>?

    private enum LoadState: Equatable {
        case idle
        case loading
        case loaded(PlaceSearchResult)
        case failed(CoreError)
    }

    var body: some View {
        NavigationStack {
            Group {
                switch state {
                case .idle, .loading:
                    StateMessageView(kind: .loading, title: L10n.commonLoading)
                case let .failed(error):
                    CoreErrorView(error) { Task { await load() } }
                case let .loaded(result):
                    if result.places.isEmpty {
                        StateMessageView(
                            kind: .empty(systemImage: "mappin.slash"),
                            title: L10n.placeNoMatches,
                            message: L10n.placeDisambiguationHint
                        )
                    } else {
                        list(result)
                    }
                }
            }
            .background(Theme.Palette.background)
            .navigationTitle(field.label)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button(L10n.commonCancel) { dismiss() }
                }
            }
            .searchable(
                text: $query,
                placement: .navigationBarDrawer(displayMode: .always),
                prompt: L10n.placeSearchPlaceholder
            )
            .task { await load() }
            .onChange(of: query) { _, _ in
                searchTask?.cancel()
                searchTask = Task {
                    // A short settle so a fast typist does not issue a request
                    // per keystroke.
                    try? await Task.sleep(for: .milliseconds(180))
                    guard !Task.isCancelled else { return }
                    await load()
                }
            }
        }
    }

    private func list(_ result: PlaceSearchResult) -> some View {
        let terminals = result.places.filter { $0.kind == .stopPlace }
        let orphanStops = result.places.filter { place in
            place.kind == .stop && !terminals.contains { $0.id == place.parentId }
        }

        return List {
            if model.isDemonstrationData {
                Section { DemoDataNotice().listRowInsets(EdgeInsets()) }
                    .listRowBackground(Color.clear)
            }

            Section {
                Text(L10n.placeDisambiguationHint)
                    .font(.footnote)
                    .foregroundStyle(Theme.Palette.textSecondary)
            }

            ForEach(terminals) { terminal in
                Section {
                    row(terminal)
                    ForEach(result.places.filter { $0.parentId == terminal.id }) { child in
                        row(child).padding(.leading, Theme.Space.medium)
                    }
                }
            }

            if !orphanStops.isEmpty {
                Section(L10n.placeBoardingPoint) {
                    ForEach(orphanStops) { place in row(place) }
                }
            }
        }
        .listStyle(.insetGrouped)
        .scrollContentBackground(.hidden)
    }

    private func row(_ place: Place) -> some View {
        Button {
            onChoose(place)
        } label: {
            VStack(alignment: .leading, spacing: Theme.Space.xxSmall) {
                Text(place.name.resolved(for: model.settings.effectiveLanguageTag))
                    .font(.body.weight(place.kind == .stopPlace ? .semibold : .regular))
                    .foregroundStyle(Theme.Palette.textPrimary)
                    .fixedSize(horizontal: false, vertical: true)

                BadgeFlow {
                    StatusBadge(
                        place.kind == .stopPlace ? L10n.placeTerminal : L10n.placeBoardingPoint,
                        systemImage: place.kind == .stopPlace ? "building.2" : "signpost.right",
                        tone: place.kind == .stopPlace ? .brand : .neutral
                    )
                    if place.kind == .stopPlace, place.boardingPointCount > 0 {
                        StatusBadge(
                            L10n.placeBoardingPointCount(place.boardingPointCount),
                            systemImage: "list.bullet",
                            tone: .neutral
                        )
                    }
                    CoverageBadge(place.coverage)
                    if let municipality = place.municipality, !municipality.isEmpty {
                        StatusBadge(municipality, systemImage: "map", tone: .neutral)
                    }
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .frame(minHeight: Theme.Size.touchMinimum)
            .contentShape(.rect)
        }
        .buttonStyle(.plain)
        .accessibilityElement(children: .combine)
        .accessibilityIdentifier("place.\(place.id)")
    }

    private func load() async {
        state = .loading
        do {
            let result = try await model.core.searchPlaces(query: query, limit: 60)
            state = .loaded(result)
        } catch let error as CoreError {
            state = .failed(error)
        } catch {
            state = .failed(CoreErrorTranslation.translate(error))
        }
    }
}

/// The coverage state of a place or a corridor, always with its own icon.
struct CoverageBadge: View {
    private let coverage: CoverageState

    init(_ coverage: CoverageState) {
        self.coverage = coverage
    }

    var body: some View {
        switch coverage {
        case .covered:
            StatusBadge(L10n.coverageCovered, systemImage: "checkmark.seal", tone: .success)
        case .partial:
            StatusBadge(L10n.coveragePartial, systemImage: "circle.lefthalf.filled", tone: .warning)
        case .notCovered:
            StatusBadge(L10n.coverageNotCovered, systemImage: "xmark.seal", tone: .error)
        case .demo:
            StatusBadge(L10n.coverageDemo, systemImage: "theatermasks", tone: .warning)
        }
    }
}
