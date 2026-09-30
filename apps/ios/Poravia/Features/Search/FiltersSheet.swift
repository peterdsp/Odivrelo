import SwiftUI

/// Search filters.
///
/// The interface only collects these; the shared core applies them, which is
/// why a filter cannot quietly disagree with the timetable rules. The departure
/// window is collected as the local `HH:mm` the core expects, on the service
/// date, in `Europe/Athens`.
struct FiltersSheet: View {
    @Environment(\.dismiss) private var dismiss
    @Environment(AppModel.self) private var model

    @Binding var filters: JourneyFilters
    let operators: [OperatorSummary]

    @State private var useDepartureWindow = false
    @State private var earliest = 0
    @State private var latest = 23 * 60 + 59
    @State private var limitDuration = false
    @State private var maximumDuration = 480

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    Toggle(L10n.filtersAccessible, isOn: $filters.accessibleOnly)
                        .accessibilityIdentifier("filters.accessible")
                    Toggle(
                        L10n.filtersExcludeOvernight,
                        isOn: Binding(
                            get: { !filters.includeCrossesMidnight },
                            set: { filters.includeCrossesMidnight = !$0 }
                        )
                    )
                    .accessibilityIdentifier("filters.excludeOvernight")
                }

                Section(L10n.filtersSortBy) {
                    Picker(L10n.filtersSortBy, selection: $filters.sort) {
                        Text(L10n.filtersSortDeparture).tag(JourneySort.departure)
                        Text(L10n.filtersSortArrival).tag(JourneySort.arrival)
                        Text(L10n.filtersSortDuration).tag(JourneySort.duration)
                    }
                    .pickerStyle(.segmented)
                    .accessibilityIdentifier("filters.sort")
                }

                if !operators.isEmpty {
                    Section(L10n.filtersOperators) {
                        ForEach(operators) { value in
                            Toggle(
                                value.name.resolved(for: model.settings.effectiveLanguageTag),
                                isOn: Binding(
                                    get: { filters.operatorIds.contains(value.id) },
                                    set: { isOn in
                                        if isOn {
                                            if !filters.operatorIds.contains(value.id) {
                                                filters.operatorIds.append(value.id)
                                            }
                                        } else {
                                            filters.operatorIds.removeAll { $0 == value.id }
                                        }
                                    }
                                )
                            )
                        }
                    }
                }

                Section(L10n.filtersDepartureWindow) {
                    Toggle(L10n.filtersDepartureWindow, isOn: $useDepartureWindow)
                        .accessibilityIdentifier("filters.window")
                    if useDepartureWindow {
                        minuteSlider(L10n.filtersEarliest, value: $earliest)
                        minuteSlider(L10n.filtersLatest, value: $latest)
                    }
                }

                Section(L10n.filtersMaxDuration) {
                    Toggle(L10n.filtersMaxDuration, isOn: $limitDuration)
                        .accessibilityIdentifier("filters.maxDuration")
                    if limitDuration {
                        Stepper(value: $maximumDuration, in: 30...1_440, step: 30) {
                            LabelledValue(
                                L10n.filtersMaxDuration,
                                value: Formatters.duration(minutes: maximumDuration)
                            )
                        }
                    }
                }

                Section {
                    Button(L10n.filtersReset, role: .destructive) {
                        filters = .none
                        useDepartureWindow = false
                        limitDuration = false
                        earliest = 0
                        latest = 23 * 60 + 59
                        maximumDuration = 480
                    }
                    .disabled(!filters.isActive)
                    .accessibilityIdentifier("filters.reset")
                }
            }
            .navigationTitle(L10n.filtersTitle)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button(L10n.commonDone) {
                        commit()
                        dismiss()
                    }
                    .accessibilityIdentifier("filters.done")
                }
            }
            .onAppear(perform: restore)
            .onChange(of: useDepartureWindow) { _, _ in commit() }
            .onChange(of: limitDuration) { _, _ in commit() }
            .onChange(of: earliest) { _, new in
                if new > latest { latest = new }
                commit()
            }
            .onChange(of: latest) { _, new in
                if new < earliest { earliest = new }
                commit()
            }
            .onChange(of: maximumDuration) { _, _ in commit() }
        }
    }

    private func minuteSlider(_ label: String, value: Binding<Int>) -> some View {
        VStack(alignment: .leading, spacing: Theme.Space.xxSmall) {
            LabelledValue(label, value: Formatters.minuteOfDay(value.wrappedValue))
            Slider(
                value: Binding(
                    get: { Double(value.wrappedValue) },
                    set: { value.wrappedValue = Int($0.rounded()) }
                ),
                in: 0...Double(23 * 60 + 59),
                step: 15
            )
            .accessibilityLabel(label)
            .accessibilityValue(Formatters.minuteOfDay(value.wrappedValue))
        }
    }

    private func restore() {
        useDepartureWindow = filters.departAfter != nil || filters.departBefore != nil
        earliest = filters.departAfter.flatMap(Self.minutes(from:)) ?? 0
        latest = filters.departBefore.flatMap(Self.minutes(from:)) ?? (23 * 60 + 59)
        limitDuration = filters.maxDurationMinutes != nil
        maximumDuration = filters.maxDurationMinutes ?? 480
    }

    private func commit() {
        filters.departAfter = useDepartureWindow ? Self.clock(from: earliest) : nil
        filters.departBefore = useDepartureWindow ? Self.clock(from: latest) : nil
        filters.maxDurationMinutes = limitDuration ? maximumDuration : nil
    }

    /// The core expects a local `HH:mm`, not a locale-formatted time.
    static func clock(from minutes: Int) -> String {
        String(format: "%02d:%02d", minutes / 60, minutes % 60)
    }

    static func minutes(from clock: String) -> Int? {
        let parts = clock.split(separator: ":")
        guard parts.count == 2, let hour = Int(parts[0]), let minute = Int(parts[1]) else { return nil }
        return hour * 60 + minute
    }
}
