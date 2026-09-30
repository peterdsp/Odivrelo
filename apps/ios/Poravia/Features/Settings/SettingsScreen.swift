import SwiftUI
import UIKit

/// Language, appearance, accessibility, offline storage, privacy,
/// notifications, data sources, support, licences, version, commit, data
/// release identifier, and diagnostics that carry no secret.
struct SettingsScreen: View {
    @Environment(AppModel.self) private var model
    @Environment(AppSettings.self) private var settings

    let geometry: WindowGeometry

    @State private var copiedDiagnostics = false

    var body: some View {
        @Bindable var settings = settings

        Form {
            if model.isDemonstrationData {
                Section { DemoDataNotice().listRowInsets(EdgeInsets()) }
                    .listRowBackground(Color.clear)
            }

            Section(L10n.settingsLanguage) {
                Picker(L10n.settingsLanguage, selection: $settings.languageTag) {
                    Text(L10n.settingsLanguageSystem).tag(String?.none)
                    ForEach(L10n.supportedLanguages, id: \.self) { tag in
                        Text(OnboardingView.languageName(tag)).tag(String?.some(tag))
                    }
                }
                .accessibilityIdentifier("settings.language")
            }

            Section(L10n.settingsAppearance) {
                Picker(L10n.settingsAppearance, selection: $settings.appearance) {
                    ForEach(AppSettings.Appearance.allCases, id: \.self) { value in
                        Text(value.label).tag(value)
                    }
                }
                .pickerStyle(.segmented)
                .accessibilityIdentifier("settings.appearance")
            }

            Section(L10n.settingsAccessibility) {
                Toggle(L10n.settingsPreferAccessibleJourneys, isOn: $settings.preferAccessibleJourneys)
                Text(L10n.settingsReduceMotionNote)
                    .font(.footnote)
                    .foregroundStyle(Theme.Palette.textSecondary)
            }

            Section(L10n.settingsNotifications) {
                notificationRow
                Stepper(
                    value: $settings.reminderLeadMinutes,
                    in: 5...240,
                    step: 5
                ) {
                    LabelledValue(
                        L10n.remindersLeadTime,
                        value: L10n.remindersLeadMinutes(settings.reminderLeadMinutes)
                    )
                }
                .accessibilityValue(L10n.remindersLeadTimeValue(settings.reminderLeadMinutes))
                Text(L10n.remindersPrivacyNote)
                    .font(.footnote)
                    .foregroundStyle(Theme.Palette.textSecondary)
                if model.reminders.didRescheduleForTimeZoneChange {
                    InlineNotice(
                        text: L10n.remindersTimeZoneChanged,
                        systemImage: "globe",
                        tone: .info
                    )
                }
            }

            Section(L10n.settingsStorage) {
                NavigationLink(value: Destination.wallet) {
                    LabelledValue(
                        L10n.walletTitle,
                        value: Formatters.bytes(model.wallet.totalBytes)
                    )
                }
            }

            Section(L10n.settingsPrivacy) {
                Text(L10n.settingsPrivacyBody)
                    .font(.footnote)
                    .foregroundStyle(Theme.Palette.textPrimary)
                    .fixedSize(horizontal: false, vertical: true)
            }

            Section(L10n.settingsDataSources) {
                NavigationLink(L10n.sourcesTitle, value: Destination.sources)
                NavigationLink(L10n.settingsCoverage, value: Destination.coverage)
                NavigationLink(L10n.licencesTitle, value: Destination.licences)
            }

            Section(L10n.settingsAbout) {
                Text(L10n.settingsNameOrigin)
                    .font(.footnote)
                    .foregroundStyle(Theme.Palette.textPrimary)
                    .fixedSize(horizontal: false, vertical: true)
                HStack(alignment: .top, spacing: Theme.Space.small) {
                    BrandMarkView(size: 44)
                    Text(L10n.settingsAboutMark)
                        .font(.footnote)
                        .foregroundStyle(Theme.Palette.textSecondary)
                        .fixedSize(horizontal: false, vertical: true)
                }
                Link(Brand.domain, destination: Brand.websiteURL)
                Link(Brand.supportEmail, destination: Brand.supportMailURL)
            }

            Section(L10n.settingsDiagnostics) {
                LabelledValue(
                    L10n.settingsVersionLabel,
                    value: "\(BuildInfo.marketingVersion) (\(BuildInfo.buildNumber))"
                )
                LabelledValue(L10n.settingsCommitLabel, value: BuildInfo.commit, monospaced: true)
                LabelledValue(
                    L10n.offlineReleaseLabel,
                    value: model.releaseId.map { String($0.prefix(16)) } ?? L10n.commonNotStated,
                    monospaced: true
                )
                LabelledValue(L10n.settingsContractLabel, value: Brand.contractVersion, monospaced: true)
                coreStatus
                Text(L10n.settingsDiagnosticsNote)
                    .font(.footnote)
                    .foregroundStyle(Theme.Palette.textSecondary)
                    .fixedSize(horizontal: false, vertical: true)
                Button(copiedDiagnostics ? L10n.commonDone : L10n.settingsCopyDiagnostics) {
                    UIPasteboard.general.string = diagnosticsReport
                    copiedDiagnostics = true
                }
                .accessibilityIdentifier("settings.copyDiagnostics")

                #if DEBUG
                // Shown only in Debug, so the layout a device actually reports
                // can be read off a screenshot rather than inferred. This is
                // how the iPad and foldable geometry evidence is captured.
                DisclosureGroup("Live geometry") {
                    Text(geometryReport)
                        .font(.caption.monospaced())
                        .foregroundStyle(Theme.Palette.textSecondary)
                        .textSelection(.enabled)
                        .fixedSize(horizontal: false, vertical: true)
                }
                .accessibilityIdentifier("settings.geometry")
                #endif
            }
        }
        .scrollContentBackground(.hidden)
        .background(Theme.Palette.background)
        .navigationTitle(L10n.settingsTitle)
        .task { await model.reminders.refreshAuthorisation() }
    }

    @ViewBuilder
    private var notificationRow: some View {
        switch model.reminders.authorisation {
        case .authorised:
            LabelledValue(L10n.remindersTitle, value: L10n.commonDone)
        case .notDetermined:
            Button(L10n.remindersEnable) {
                Task { await model.reminders.requestAuthorisation() }
            }
        case .denied, .revoked:
            VStack(alignment: .leading, spacing: Theme.Space.xxSmall) {
                Text(
                    model.reminders.authorisation == .revoked
                        ? L10n.remindersRevoked : L10n.remindersDenied
                )
                .font(.subheadline)
                .foregroundStyle(Theme.Palette.errorText)
                Text(L10n.remindersDeniedHint)
                    .font(.footnote)
                    .foregroundStyle(Theme.Palette.textSecondary)
                Button(L10n.commonOpenSettings) { SystemSettings.open() }
            }
        }
    }

    @ViewBuilder
    private var coreStatus: some View {
        switch model.coreSelection {
        case .sharedCore:
            InlineNotice(text: L10n.settingsDataCoreLinked, systemImage: "shippingbox", tone: .success)
        case .fixture:
            InlineNotice(text: L10n.settingsDataCoreFixture, systemImage: "theatermasks", tone: .warning)
        case .unavailable:
            InlineNotice(
                text: L10n.settingsDataCoreMissing,
                systemImage: "exclamationmark.triangle",
                tone: .error
            )
        }
    }

    #if DEBUG
    /// The container this scene was actually given, and what the resolver made
    /// of it. Debug only.
    private var geometryReport: String {
        let mode = LayoutResolver.mode(for: geometry)
        var lines = [
            "window      \(Int(geometry.size.width)) x \(Int(geometry.size.height)) pt",
            "safeArea    t\(Int(geometry.safeArea.top)) l\(Int(geometry.safeArea.leading))"
                + " b\(Int(geometry.safeArea.bottom)) r\(Int(geometry.safeArea.trailing))",
            "usable      \(Int(geometry.usableWidth)) x \(Int(geometry.usableHeight)) pt",
            "keyboard    \(Int(geometry.keyboardInset)) pt",
            "sizeClass   h=\(geometry.horizontalSizeClass.map(String.init(describing:)) ?? "nil")"
                + " v=\(geometry.verticalSizeClass.map(String.init(describing:)) ?? "nil")",
            "dynamicType \(geometry.dynamicTypeSize)",
            "layout      \(mode.rawValue)",
            "reserved    \(geometry.reserved.count) region(s)",
            "asymmetry   \(geometry.asymmetry)",
        ]
        for region in geometry.reserved {
            lines.append("  \(region.kind.rawValue) \(region.rect.debugDescription)")
        }
        return lines.joined(separator: "\n")
    }
    #endif

    /// Versions, data state and sizes. No personal detail, no ticket, no
    /// secret, and nothing that identifies a device.
    private var diagnosticsReport: String {
        [
            "product: \(Brand.name) \(BuildInfo.marketingVersion) (\(BuildInfo.buildNumber))",
            "configuration: \(BuildInfo.configuration)",
            "commit: \(BuildInfo.commit)",
            "bundle: \(BuildInfo.bundleIdentifier)",
            "contract: \(Brand.contractVersion)",
            "release: \(model.releaseId ?? "none")",
            "dataMode: \(model.meta?.envelope.dataMode.rawValue ?? "unknown")",
            "core: \(model.coreSelection.rawValue)",
            "language: \(settings.effectiveLanguageTag)",
            "appearance: \(settings.appearance.rawValue)",
            "notifications: \(String(describing: model.reminders.authorisation))",
            "wallet: \(model.wallet.tickets.count) files, \(Formatters.bytes(model.wallet.totalBytes))",
            "walletBackupExcluded: \(model.wallet.isExcludedFromBackup)",
            "window: \(Int(geometry.size.width))x\(Int(geometry.size.height))",
            "usable: \(Int(geometry.usableWidth))x\(Int(geometry.usableHeight))",
            "reservedRegions: \(geometry.reserved.count)",
            "layout: \(LayoutResolver.mode(for: geometry).rawValue)",
            "dynamicType: \(geometry.dynamicTypeSize)",
        ].joined(separator: "\n")
    }
}

/// The public source registry, with rights status and licence per source.
struct SourcesScreen: View {
    @Environment(AppModel.self) private var model
    @Environment(\.windowGeometry) private var geometry

    @State private var state: LoadState = .loading

    private enum LoadState: Equatable {
        case loading
        case loaded(SourceList)
        case failed(CoreError)
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: Theme.Space.medium) {
                if model.isDemonstrationData { DemoDataNotice() }

                switch state {
                case .loading:
                    StateMessageView(kind: .loading, title: L10n.commonLoading)
                case let .failed(error):
                    CoreErrorView(error) { Task { await load() } }
                case let .loaded(list):
                    if list.sources.isEmpty {
                        StateMessageView(
                            kind: .empty(systemImage: "doc.text.magnifyingglass"),
                            title: L10n.sourcesEmpty
                        )
                    } else {
                        ForEach(list.sources) { source in
                            SectionCard(source.name, systemImage: "doc.text") {
                                VStack(alignment: .leading, spacing: Theme.Space.xSmall) {
                                    LabelledValue(
                                        L10n.detailRetrievedAtLabel,
                                        value: source.retrievedAt.map(Formatters.timestamp)
                                            ?? L10n.commonNotStated
                                    )
                                    LabelledValue(
                                        L10n.detailLicenceLabel,
                                        value: source.licence ?? L10n.commonNotStated
                                    )
                                    if let note = source.note {
                                        Text(note)
                                            .font(.footnote)
                                            .foregroundStyle(Theme.Palette.textSecondary)
                                            .fixedSize(horizontal: false, vertical: true)
                                    }
                                    BadgeFlow {
                                        RightsBadge(source.rightsStatus)
                                        if let url = source.url {
                                            StatusBadge(
                                                url.host() ?? url.absoluteString,
                                                systemImage: "link",
                                                tone: .neutral
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            .readableColumn(geometry)
            .padding(.vertical, Theme.Space.medium)
        }
        .background(Theme.Palette.background)
        .navigationTitle(L10n.sourcesTitle)
        .task { await load() }
    }

    private func load() async {
        do {
            state = .loaded(try await model.core.sources())
        } catch let error as CoreError {
            state = .failed(error)
        } catch {
            state = .failed(CoreErrorTranslation.translate(error))
        }
    }
}

/// Coverage and freshness, including the explicit statement of what is not
/// covered.
struct CoverageScreen: View {
    @Environment(AppModel.self) private var model
    @Environment(\.windowGeometry) private var geometry

    @State private var state: LoadState = .loading

    /// The language the release's own sentences are resolved for.
    private var language: String { model.settings.effectiveLanguageTag }

    private enum LoadState: Equatable {
        case loading
        case loaded(CoverageSummary)
        case failed(CoreError)
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: Theme.Space.medium) {
                if model.isDemonstrationData { DemoDataNotice() }

                switch state {
                case .loading:
                    StateMessageView(kind: .loading, title: L10n.commonLoading)
                case let .failed(error):
                    CoreErrorView(error) { Task { await load() } }
                case let .loaded(coverage):
                    SectionCard(L10n.coverageTitle, systemImage: "map") {
                        VStack(alignment: .leading, spacing: Theme.Space.small) {
                            BadgeFlow { CoverageBadge(coverage.state) }
                            LabelledValue(
                                L10n.coverageTitle,
                                value: L10n.coverageOperatorCount(coverage.operatorCount)
                                    + " · " + L10n.coverageCorridorCount(coverage.corridorCount)
                            )
                            LabelledValue(
                                L10n.offlineReleaseLabel,
                                value: String(coverage.releaseId.prefix(16)),
                                monospaced: true
                            )
                            if let publishedAt = coverage.publishedAt {
                                LabelledValue(
                                    L10n.detailOperatingDate,
                                    value: Formatters.timestamp(publishedAt)
                                )
                            }
                            // The release states this in each language. It is
                            // resolved for the one in use so a Greek reader is
                            // not handed the English sentence.
                            if let note = coverage.note?
                                .resolved(for: language),
                                !note.isEmpty {
                                Text(note)
                                    .font(.subheadline)
                                    .foregroundStyle(Theme.Palette.textPrimary)
                                    .fixedSize(horizontal: false, vertical: true)
                            }
                        }
                    }

                    SectionCard(L10n.coverageNotCoveredHeading, systemImage: "exclamationmark.triangle") {
                        if coverage.notCovered.isEmpty {
                            // An empty list is itself a claim, so it is never
                            // rendered as full coverage.
                            InlineNotice(
                                text: L10n.coverageNotCoveredEmpty,
                                systemImage: "questionmark.circle",
                                tone: .warning
                            )
                        } else {
                            VStack(alignment: .leading, spacing: Theme.Space.xSmall) {
                                ForEach(Array(coverage.notCovered.enumerated()), id: \.offset) { _, line in
                                    HStack(alignment: .firstTextBaseline, spacing: Theme.Space.xSmall) {
                                        Image(systemName: "xmark.circle")
                                            .foregroundStyle(Theme.Palette.errorText)
                                            .accessibilityHidden(true)
                                        Text(line.resolved(for: language))
                                            .font(.subheadline)
                                            .fixedSize(horizontal: false, vertical: true)
                                        Spacer(minLength: 0)
                                    }
                                    .accessibilityElement(children: .combine)
                                }
                            }
                        }
                    }
                }
            }
            .readableColumn(geometry)
            .padding(.vertical, Theme.Space.medium)
        }
        .background(Theme.Palette.background)
        .navigationTitle(L10n.coverageTitle)
        .task { await load() }
    }

    private func load() async {
        do {
            state = .loaded(try await model.core.coverage())
        } catch let error as CoreError {
            state = .failed(error)
        } catch {
            state = .failed(CoreErrorTranslation.translate(error))
        }
    }
}

/// Licences and attribution for everything the release carries.
struct LicencesScreen: View {
    @Environment(AppModel.self) private var model
    @Environment(\.windowGeometry) private var geometry

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: Theme.Space.medium) {
                SectionCard(L10n.licencesTitle, systemImage: "c.circle") {
                    VStack(alignment: .leading, spacing: Theme.Space.small) {
                        if let attribution = model.meta?.attribution, !attribution.isEmpty {
                            ForEach(attribution) { entry in
                                VStack(alignment: .leading, spacing: 2) {
                                    Text(entry.name)
                                        .font(.subheadline.weight(.semibold))
                                        .fixedSize(horizontal: false, vertical: true)
                                    LabelledValue(
                                        L10n.detailLicenceLabel,
                                        value: entry.licence ?? L10n.commonNotStated
                                    )
                                    if let url = entry.url {
                                        Link(url.host() ?? url.absoluteString, destination: url)
                                            .font(.footnote)
                                    }
                                }
                                .frame(maxWidth: .infinity, alignment: .leading)
                                .accessibilityElement(children: .combine)

                                if entry.id != attribution.last?.id { Divider() }
                            }
                        } else {
                            Text(L10n.commonNotStated)
                                .font(.subheadline)
                                .foregroundStyle(Theme.Palette.textSecondary)
                        }
                    }
                }

                SectionCard(Brand.name, systemImage: "info.circle") {
                    VStack(alignment: .leading, spacing: Theme.Space.xSmall) {
                        Text(L10n.settingsNameOrigin)
                            .font(.footnote)
                            .fixedSize(horizontal: false, vertical: true)
                        Link(Brand.repository, destination: URL(string: Brand.repository) ?? Brand.websiteURL)
                            .font(.footnote)
                    }
                }
            }
            .readableColumn(geometry)
            .padding(.vertical, Theme.Space.medium)
        }
        .background(Theme.Palette.background)
        .navigationTitle(L10n.licencesTitle)
    }
}
