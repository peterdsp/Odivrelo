import SwiftUI

@main
struct PoraviaApp: App {
    @State private var model = AppModel.makeDefault()
    @Environment(\.scenePhase) private var scenePhase

    var body: some Scene {
        WindowGroup {
            RootView()
                .environment(model)
                .environment(model.settings)
                .preferredColorScheme(model.settings.appearance.colorScheme)
                // The chosen language applies immediately, without a relaunch.
                .environment(\.locale, Locale(identifier: model.settings.effectiveLanguageTag))
                .task {
                    // A unit-test run hosts this app, but the unit tests
                    // exercise the core boundary directly and must not reach
                    // for the network or for installed packs on launch.
                    guard !RunMode.isUnitTestRun else { return }
                    // Registering the release shipped with the application has
                    // to finish before anything is read, otherwise a fresh
                    // install would render an empty state it is about to
                    // contradict. A failure here is not fatal: the application
                    // simply has no offline data yet, which is a state it
                    // already knows how to show.
                    _ = try? await model.core.adoptSeededRelease()
                    await model.loadMeta()
                    await model.reminders.refreshAuthorisation()
                }
                .onOpenURL { url in
                    DeepLinkRouter.handle(url, in: model)
                }
                .onContinueUserActivity(NSUserActivityTypeBrowsingWeb) { activity in
                    guard let url = activity.webpageURL else { return }
                    DeepLinkRouter.handle(url, in: model)
                }
        }
        .onChange(of: scenePhase) { _, phase in
            switch phase {
            case .background, .inactive:
                // The session is written when the scene leaves the foreground,
                // so a relaunch comes back to the same query, date, filters,
                // selection and scroll position.
                model.persistSession()
            case .active:
                Task { await model.reminders.refreshAuthorisation() }
            @unknown default:
                break
            }
        }
    }
}
