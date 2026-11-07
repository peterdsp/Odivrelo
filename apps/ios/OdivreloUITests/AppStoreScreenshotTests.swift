import XCTest

/// Captures the App Store screenshots from the running app on the real
/// release, never the fixture: a screenshot of invented places would
/// misrepresent the product.
///
/// Skipped unless `ODIVRELO_SHOT_LANGUAGE` is set, so it adds nothing to an
/// ordinary test run. `scripts/ios-app-store-screenshots.sh` sets it (as
/// `TEST_RUNNER_ODIVRELO_SHOT_LANGUAGE`) and exports the attachments.
///
/// Every tap goes through an accessibility identifier or a plain drag, so the
/// same test captures every language. Deep links are not used: opening one from
/// outside the app puts a system "Open in Odivrelo?" prompt over the screen.
final class AppStoreScreenshotTests: XCTestCase {
    /// Athens Kifisos to Patras, the busiest pair in the release.
    private let origin = "ks_981ad5612757337fb6654902"
    private let destination = "ks_9aa9fbd42f9c171ee8b90642"

    @MainActor
    func testAppStoreScreenshots() throws {
        let environment = ProcessInfo.processInfo.environment
        guard let language = environment["ODIVRELO_SHOT_LANGUAGE"] else {
            throw XCTSkip("set ODIVRELO_SHOT_LANGUAGE to capture the App Store screenshots")
        }
        let locale = language == "el" ? "el_GR" : "en_GB"
        continueAfterFailure = false

        func launch(_ extra: [String]) -> XCUIApplication {
            let app = XCUIApplication()
            app.launchArguments = [
                "-OdivreloSkipOnboarding", "-OdivreloResetState",
                "-AppleLanguages", "(\(language))", "-AppleLocale", locale,
            ] + extra
            app.launch()
            return app
        }

        // The search as a first launch finds it.
        var app = launch([])
        dismissSystemAlert()
        XCTAssertTrue(app.buttons["search.origin"].waitForExistence(timeout: 60))
        sleep(3)
        shoot(app, "4-search")
        app.terminate()

        // A seeded search answers on its own, for today's service date.
        app = launch(["-OdivreloSeedSearch", "\(origin),\(destination)"])
        let journeys = app.buttons.matching(NSPredicate(format: "identifier BEGINSWITH 'journey.'"))
        XCTAssertTrue(journeys.firstMatch.waitForExistence(timeout: 60), "the seeded search found no departures")
        sleep(3)
        // On a phone the board sits under the form; bring its head into view.
        let phone = app.windows.firstMatch.frame.width < 600
        if phone {
            scroll(app, by: 0.42)
        }
        shoot(app, "1-results")

        journeys.element(boundBy: min(2, journeys.count - 1)).tap()
        XCTAssertTrue(app.buttons["detail.saveTrip"].waitForExistence(timeout: 30), "the journey did not open")
        sleep(3)
        shoot(app, "2-journey")

        // Further down the same journey: the boarding stop and the call list.
        scroll(app, by: phone ? 0.55 : 0.45)
        shoot(app, "3-route")
    }

    /// Drags the content up by a fraction of the window height, slowly enough
    /// that it stops where it is put.
    @MainActor
    private func scroll(_ app: XCUIApplication, by fraction: CGFloat) {
        let start = app.windows.firstMatch.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.75))
        let end = start.withOffset(CGVector(dx: 0, dy: -app.windows.firstMatch.frame.height * fraction))
        start.press(forDuration: 0.1, thenDragTo: end, withVelocity: .slow, thenHoldForDuration: 0.5)
        sleep(2)
    }

    /// A prompt left over from an earlier session would sit over every shot.
    @MainActor
    private func dismissSystemAlert() {
        let springboard = XCUIApplication(bundleIdentifier: "com.apple.springboard")
        let cancel = springboard.alerts.firstMatch.buttons.element(boundBy: 0)
        if springboard.alerts.firstMatch.waitForExistence(timeout: 3), cancel.exists {
            cancel.tap()
        }
    }

    @MainActor
    private func shoot(_ app: XCUIApplication, _ name: String) {
        let attachment = XCTAttachment(screenshot: app.screenshot())
        attachment.name = name
        attachment.lifetime = .keepAlways
        add(attachment)
    }
}
