import SwiftUI

/// The application shell.
///
/// It measures the real container once, at the root, and every screen below
/// reads the resulting `WindowGeometry` and `LayoutMode` from the environment.
/// Nothing below asks the device what it is.
public struct RootView: View {
    @Environment(AppModel.self) private var model
    @Environment(AppSettings.self) private var settings

    public init() {}

    public var body: some View {
        @Bindable var model = model

        WindowGeometryReader { geometry, mode in
            Group {
                if settings.hasCompletedOnboarding {
                    MainShell(geometry: geometry, mode: mode)
                } else {
                    OnboardingView()
                }
            }
            .background(Theme.Palette.background)
            .animation(
                UIAccessibility.isReduceMotionEnabled ? nil : .easeOut(duration: Theme.Motion.standard),
                value: settings.hasCompletedOnboarding
            )
        }
        .sheet(item: $model.route.unresolvedLink) { link in
            UnresolvedLinkView(link: link)
        }
    }
}

/// Tabs plus a per-tab navigation stack or split view.
private struct MainShell: View {
    @Environment(AppModel.self) private var model

    let geometry: WindowGeometry
    let mode: LayoutMode

    var body: some View {
        @Bindable var model = model

        TabView(selection: $model.session.selectedTab) {
            ForEach(MainTab.allCases) { tab in
                TabContainer(tab: tab, geometry: geometry, mode: mode)
                    .tabItem {
                        Label(tab.title, systemImage: tab.systemImage)
                            // A stable, language-independent handle for the tab
                            // control. It carries no user-visible change: the
                            // label and glyph above are what a person sees and
                            // what a screen reader announces. It lets a UI test
                            // find the tab whether the platform draws a bottom
                            // bar, a top bar or a sidebar.
                            .accessibilityIdentifier("tab-\(tab.rawValue)")
                    }
                    .tag(tab)
            }
        }
        .tint(Theme.Palette.primary)
        .onChange(of: model.session.selectedTab) { _, _ in
            model.persistSession()
        }
    }
}

/// One tab's content, in the layout the window can actually carry.
private struct TabContainer: View {
    @Environment(AppModel.self) private var model

    let tab: MainTab
    let geometry: WindowGeometry
    let mode: LayoutMode

    var body: some View {
        @Bindable var model = model

        let path = Binding(
            get: { model.route.path(for: tab) },
            set: { model.route.setPath($0, for: tab) }
        )

        NavigationStack(path: path) {
            root
                .navigationDestination(for: Destination.self) { destination in
                    DestinationView(destination: destination)
                }
        }
    }

    @ViewBuilder
    private var root: some View {
        switch tab {
        case .search: SearchScreen(geometry: geometry, mode: mode)
        case .trips: TripsScreen(geometry: geometry)
        case .offline: OfflineScreen(geometry: geometry)
        case .settings: SettingsScreen(geometry: geometry)
        }
    }
}

/// Resolves a pushed destination.
struct DestinationView: View {
    let destination: Destination

    var body: some View {
        switch destination {
        case let .journey(id, serviceDate):
            JourneyDetailScreen(journeyId: id, serviceDate: serviceDate)
        case let .operatorPage(id):
            OperatorScreen(operatorId: id)
        case let .stop(id, serviceDate):
            StopScreen(stopId: id, serviceDate: serviceDate)
        case .wallet:
            WalletScreen()
        case .sources:
            SourcesScreen()
        case .coverage:
            CoverageScreen()
        case .licences:
            LicencesScreen()
        }
    }
}

/// The honest dead end for a link that could not be resolved.
struct UnresolvedLinkView: View {
    @Environment(AppModel.self) private var model
    @Environment(\.dismiss) private var dismiss

    let link: UnresolvedLink

    var body: some View {
        NavigationStack {
            StateMessageView(
                kind: .failure(systemImage: reason.systemImage),
                title: reason.title,
                message: reason.message,
                retryTitle: L10n.deeplinkSearchInstead
            ) {
                model.session.selectedTab = .search
                model.session.serviceDate = model.today
                model.route.searchPath = []
                model.persistSession()
                dismiss()
            }
            .navigationTitle(reason.title)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button(L10n.commonClose) { dismiss() }
                }
            }
        }
    }

    private var reason: (title: String, message: String, systemImage: String) {
        switch link.reason {
        case .unknown:
            (L10n.deeplinkUnknownTitle, L10n.deeplinkUnknownBody, "link.badge.plus")
        case let .expired(date):
            (
                L10n.deeplinkExpiredTitle,
                L10n.deeplinkExpiredBody(Formatters.serviceDate(date)),
                "calendar.badge.exclamationmark"
            )
        }
    }
}
