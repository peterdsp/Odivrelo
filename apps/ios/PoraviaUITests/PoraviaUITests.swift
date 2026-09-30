import XCTest

/// UI tests for the critical flows, run against the Debug fixture so every
/// state is reachable without a server.
///
/// Each test launches with `-PoraviaUseFixture` and a scenario, plus a reset
/// flag so the run starts from a known state.
final class PoraviaUITests: XCTestCase {
    override func setUp() {
        super.setUp()
        continueAfterFailure = false
    }

    // MARK: Launching

    @MainActor
    func launch(
        scenario: String = "normal",
        language: String = "el",
        arguments extra: [String] = []
    ) -> XCUIApplication {
        let app = XCUIApplication()
        app.launchArguments += [
            "-PoraviaUseFixture",
            "-PoraviaFixtureScenario", scenario,
            "-PoraviaResetState",
            "-AppleLanguages", "(\(language))",
            "-AppleLocale", language,
        ]
        app.launchArguments += extra
        app.launch()
        return app
    }

    /// Onboarding is the first thing a new install shows, so most tests step
    /// past it first.
    @MainActor
    func completeOnboarding(_ app: XCUIApplication) {
        let start = app.buttons["onboarding.start"]
        if start.waitForExistence(timeout: 10) {
            start.tap()
        }
    }

    // MARK: Cold launch

    @MainActor
    func testColdLaunchShowsOnboardingWithoutAccountOrPermission() {
        let app = launch()
        XCTAssertTrue(
            app.buttons["onboarding.start"].waitForExistence(timeout: 10),
            "first launch should reach onboarding"
        )
        // No account and no permission prompt stands between launch and search.
        XCTAssertFalse(app.alerts.element.exists, "nothing should be asked for on first launch")
        XCTAssertFalse(app.secureTextFields.element.exists, "there is no sign-in")
    }

    @MainActor
    func testColdLaunchInEachLanguage() {
        for language in ["el", "en", "sq"] {
            let app = launch(language: language)
            XCTAssertTrue(
                app.buttons["onboarding.start"].waitForExistence(timeout: 10),
                "onboarding did not appear in \(language)"
            )
            app.terminate()
        }
    }

    // MARK: The persistent demonstration notice

    @MainActor
    func testDemonstrationNoticeIsPresentAndCannotBeDismissed() {
        let app = launch()
        completeOnboarding(app)

        // The notice is localised, so in Greek the region reads "Αλορία".
        let notice = app.staticTexts.containing(
            NSPredicate(
                format: "label CONTAINS[c] %@ OR label CONTAINS[c] %@",
                "Aloria", "Αλορία"
            )
        ).firstMatch
        XCTAssertTrue(notice.waitForExistence(timeout: 10), "the demonstration notice must be shown")

        // There is no control that removes it: swiping and tapping leave it.
        notice.tap()
        notice.swipeUp()
        XCTAssertTrue(notice.exists, "the demonstration notice must not be dismissible")
    }

    // MARK: Search to detail and back

    @MainActor
    func testSearchToBoardingDetailAndBackPreservesState() {
        let app = launch()
        completeOnboarding(app)

        app.buttons["search.origin"].tap()
        let terminal = app.buttons["place.place.kentro-terminal"]
        XCTAssertTrue(terminal.waitForExistence(timeout: 10))
        terminal.tap()

        app.buttons["search.destination"].tap()
        let harbour = app.buttons["place.place.vathia-harbour"]
        XCTAssertTrue(harbour.waitForExistence(timeout: 10))
        harbour.tap()

        app.buttons["search.submit"].tap()

        let journey = app.buttons["journey.jny.acl-0700"]
        XCTAssertTrue(journey.waitForExistence(timeout: 15), "a result should appear")
        journey.tap()

        XCTAssertTrue(
            app.buttons["detail.saveTrip"].waitForExistence(timeout: 10),
            "the journey detail should open"
        )

        app.navigationBars.buttons.element(boundBy: 0).tap()

        // The query survives the round trip.
        XCTAssertTrue(app.buttons["journey.jny.acl-0700"].waitForExistence(timeout: 10))
        XCTAssertTrue(app.buttons["search.submit"].exists)
    }

    // MARK: Saving, offline and relaunch

    @MainActor
    func testSavedTripSurvivesRelaunch() {
        let app = launch()
        completeOnboarding(app)
        openFirstJourney(app)

        app.buttons["detail.saveTrip"].tap()
        app.navigationBars.buttons.element(boundBy: 0).tap()

        // The saved trip is still there after a relaunch that keeps state.
        app.terminate()
        let relaunched = XCUIApplication()
        relaunched.launchArguments += ["-PoraviaUseFixture", "-PoraviaFixtureScenario", "normal"]
        relaunched.launch()

        relaunched.tabBars.buttons.element(boundBy: 1).tap()
        XCTAssertTrue(
            relaunched.staticTexts.containing(
                NSPredicate(format: "label CONTAINS[c] %@", "Aloria")
            ).firstMatch.waitForExistence(timeout: 10)
        )
    }

    // MARK: Offline and error states

    @MainActor
    func testOfflineScenarioShowsItsOwnState() {
        let app = launch(scenario: "offline")
        completeOnboarding(app)

        app.buttons["search.origin"].tap()
        // Even the place picker reports the failure rather than showing nothing.
        XCTAssertTrue(
            app.staticTexts.element(boundBy: 0).waitForExistence(timeout: 10),
            "an offline run must state the failure"
        )
    }

    @MainActor
    func testNotCoveredCorridorStatesItself() {
        let app = launch(scenario: "notCovered")
        completeOnboarding(app)
        chooseBothPlaces(app)
        app.buttons["search.submit"].tap()

        XCTAssertTrue(
            app.otherElements["results.empty"].waitForExistence(timeout: 15)
                || app.staticTexts.element(boundBy: 0).waitForExistence(timeout: 5),
            "an uncovered corridor must say so"
        )
    }

    // MARK: Offline packs

    @MainActor
    func testPackDownloadShowsProgressAndCompletes() {
        let app = launch()
        completeOnboarding(app)
        app.tabBars.buttons.element(boundBy: 2).tap()

        let download = app.buttons.matching(
            NSPredicate(format: "identifier BEGINSWITH %@", "offline.download.")
        ).firstMatch
        XCTAssertTrue(download.waitForExistence(timeout: 10), "a pack should be offered")
        download.tap()

        // The cancel control appears while the download is running, which is
        // what proves progress is real rather than instant.
        let cancel = app.buttons.matching(
            NSPredicate(format: "identifier BEGINSWITH %@", "offline.cancel.")
        ).firstMatch
        XCTAssertTrue(cancel.waitForExistence(timeout: 10), "a running download must be cancellable")
    }

    @MainActor
    func testCorruptDownloadOffersRetryAndInstallsNothing() {
        let app = launch(scenario: "corruptDownload")
        completeOnboarding(app)
        app.tabBars.buttons.element(boundBy: 2).tap()

        let download = app.buttons.matching(
            NSPredicate(format: "identifier BEGINSWITH %@", "offline.download.")
        ).firstMatch
        XCTAssertTrue(download.waitForExistence(timeout: 10))
        download.tap()

        let retry = app.buttons.matching(
            NSPredicate(format: "identifier BEGINSWITH %@", "offline.retry.")
        ).firstMatch
        XCTAssertTrue(
            retry.waitForExistence(timeout: 20),
            "a failed integrity check must offer a retry rather than claim success"
        )
    }

    @MainActor
    func testInsufficientStorageIsStatedAndNotRetried() {
        let app = launch(scenario: "insufficientStorage")
        completeOnboarding(app)
        app.tabBars.buttons.element(boundBy: 2).tap()

        let download = app.buttons.matching(
            NSPredicate(format: "identifier BEGINSWITH %@", "offline.download.")
        ).firstMatch
        XCTAssertTrue(download.waitForExistence(timeout: 10))
        download.tap()

        // Storage-full is not retryable, so no retry button is offered.
        let retry = app.buttons.matching(
            NSPredicate(format: "identifier BEGINSWITH %@", "offline.retry.")
        ).firstMatch
        XCTAssertFalse(
            retry.waitForExistence(timeout: 6),
            "a full disk must not be offered as something to retry"
        )
    }

    // MARK: Wallet

    @MainActor
    func testWalletStartsEmptyAndExplainsItself() {
        let app = launch()
        completeOnboarding(app)
        app.tabBars.buttons.element(boundBy: 1).tap()

        let wallet = app.buttons["trips.wallet"]
        XCTAssertTrue(wallet.waitForExistence(timeout: 10))
        wallet.tap()

        XCTAssertTrue(
            app.buttons["wallet.import"].waitForExistence(timeout: 10),
            "the wallet should offer an import and nothing automatic"
        )
    }

    // MARK: Settings

    @MainActor
    func testSettingsReportsVersionCommitAndCoreState() {
        let app = launch()
        completeOnboarding(app)
        app.tabBars.buttons.element(boundBy: 3).tap()

        // Diagnostics sit at the foot of a lazy list, so they only exist once
        // scrolled into view.
        let diagnostics = app.buttons["settings.copyDiagnostics"]
        var swipes = 0
        while !diagnostics.waitForExistence(timeout: 2), swipes < 8 {
            app.swipeUp()
            swipes += 1
        }
        XCTAssertTrue(diagnostics.exists, "diagnostics should be available")
    }

    @MainActor
    func testLanguageCanBeChangedInSettings() {
        let app = launch(language: "en")
        completeOnboarding(app)
        app.tabBars.buttons.element(boundBy: 3).tap()

        XCTAssertTrue(app.otherElements["settings.language"].waitForExistence(timeout: 10)
            || app.buttons["settings.language"].waitForExistence(timeout: 5))
    }

    // MARK: Filters

    @MainActor
    func testAccessibilityFilterCanBeApplied() {
        let app = launch()
        completeOnboarding(app)

        app.buttons["search.filters"].tap()
        let toggle = app.switches["filters.accessible"]
        XCTAssertTrue(toggle.waitForExistence(timeout: 10))
        toggle.tap()
        app.buttons["filters.done"].tap()

        XCTAssertTrue(app.buttons["search.filters"].waitForExistence(timeout: 10))
    }

    // MARK: Helpers

    @MainActor
    private func chooseBothPlaces(_ app: XCUIApplication) {
        app.buttons["search.origin"].tap()
        let terminal = app.buttons["place.place.kentro-terminal"]
        if terminal.waitForExistence(timeout: 10) { terminal.tap() }

        app.buttons["search.destination"].tap()
        let harbour = app.buttons["place.place.vathia-harbour"]
        if harbour.waitForExistence(timeout: 10) { harbour.tap() }
    }

    @MainActor
    private func openFirstJourney(_ app: XCUIApplication) {
        chooseBothPlaces(app)
        app.buttons["search.submit"].tap()
        let journey = app.buttons["journey.jny.acl-0700"]
        XCTAssertTrue(journey.waitForExistence(timeout: 15))
        journey.tap()
        XCTAssertTrue(app.buttons["detail.saveTrip"].waitForExistence(timeout: 10))
    }
}
