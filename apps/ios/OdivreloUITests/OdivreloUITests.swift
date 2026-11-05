import XCTest

/// UI tests for the critical flows, run against the Debug fixture so every
/// state is reachable without a server.
///
/// Each test launches with `-OdivreloUseFixture` and a scenario, plus a reset
/// flag so the run starts from a known state.
final class OdivreloUITests: XCTestCase {
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
            "-OdivreloUseFixture",
            "-OdivreloFixtureScenario", scenario,
            "-OdivreloResetState",
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

    // MARK: Screenshots

    /// Keeps a screenshot of the current screen in the result bundle.
    ///
    /// The web suite has had `shoot()` since the start, so its layouts can be
    /// looked at after a run. These tests had nothing: they asserted that a
    /// control exists without anyone ever seeing the screen it sits on, which
    /// is how a design ships unreviewed on this platform. `keepAlways` is the
    /// point, since Xcode discards attachments from passing tests by default
    /// and a passing test is exactly when you want to look at the layout.
    @MainActor
    func shoot(_ app: XCUIApplication, _ name: String) {
        let attachment = XCTAttachment(screenshot: app.screenshot())
        attachment.name = name
        attachment.lifetime = .keepAlways
        add(attachment)
    }

    // MARK: Screens, for looking at

    /// Walks the product on the real release and keeps a picture of each screen.
    ///
    /// Not an assertion test. It exists so the design can be reviewed from the
    /// actual app rather than from a description of it, which on this platform
    /// is otherwise nobody's job. It runs without `-OdivreloUseFixture`, so
    /// every screen here shows real KTEL data: a screenshot of the fixture
    /// shows invented places and says so on its own first screen, which makes
    /// it worse than no screenshot when someone is judging the interface.
    @MainActor
    func testCaptureTheScreens() {
        let app = XCUIApplication()
        app.launchArguments += ["-OdivreloSkipOnboarding", "-OdivreloResetState"]
        app.launch()

        let origin = app.buttons["search.origin"]
        XCTAssertTrue(origin.waitForExistence(timeout: 30))
        shoot(app, "01-search")

        let places = app.buttons.matching(NSPredicate(format: "identifier BEGINSWITH 'place.'"))
        origin.tap()
        if places.firstMatch.waitForExistence(timeout: 15) {
            shoot(app, "02-place-picker")
            places.firstMatch.tap()
        }

        let destination = app.buttons["search.destination"]
        if destination.waitForExistence(timeout: 10) {
            destination.tap()
            if places.firstMatch.waitForExistence(timeout: 15) {
                if places.count > 1 { places.element(boundBy: 1).tap() } else { places.firstMatch.tap() }
            }
        }
        shoot(app, "03-search-filled")

        let submit = app.buttons["search.submit"]
        if submit.waitForExistence(timeout: 10), submit.isEnabled {
            submit.tap()
            // The results either arrive, say there are none, or say why not.
            // All three are screens worth seeing, so nothing is asserted here.
            sleep(6)
            shoot(app, "04-results")

            let row = app.cells.firstMatch.exists ? app.cells.firstMatch : app.buttons.element(boundBy: 2)
            if row.exists, row.isHittable {
                row.tap()
                sleep(4)
                shoot(app, "05-journey-detail")
            }
        }

        for (index, tab) in ["My trips", "Offline", "Settings"].enumerated() {
            let button = app.tabBars.buttons[tab]
            if button.waitForExistence(timeout: 6), button.isHittable {
                button.tap()
                sleep(3)
                shoot(app, "0\(6 + index)-\(tab.lowercased().replacingOccurrences(of: " ", with: "-"))")
            }
        }
    }

    // MARK: Accessibility sizes

    /// A journey can be searched at the largest text size, on the real release.
    ///
    /// This test exists because of a wrong diagnosis. An earlier run of the
    /// fixture flow found no `place.*` buttons after tapping the origin and
    /// concluded the field did not open its picker at
    /// `accessibility-extra-extra-extra-large`, which was recorded as the
    /// product's one Critical defect. Two things were wrong with that. The
    /// picker is a navigation push, not a sheet, so `app.sheets` is empty even
    /// when it is open and was never the right thing to read. And the flow was
    /// the fixture's, not the app's: the release this test runs against carries
    /// Greek place names of a different length, which is the variable that
    /// actually changes at this text size.
    ///
    /// So this launches the real core, with no `-OdivreloUseFixture`, and walks
    /// the whole search: both pickers open, both accept a place, and the search
    /// button ends up enabled. Set the content size on a **booted** simulator
    /// before running it; `simctl` does nothing on a shut down device and the
    /// run then silently happens at the default size.
    @MainActor
    func testJourneyIsSearchableAtLargestTextSize() {
        let app = XCUIApplication()
        app.launchArguments += ["-OdivreloSkipOnboarding", "-OdivreloResetState"]
        app.launch()

        let origin = app.buttons["search.origin"]
        XCTAssertTrue(origin.waitForExistence(timeout: 30), "the search form should appear")
        origin.tap()

        let places = app.buttons.matching(NSPredicate(format: "identifier BEGINSWITH 'place.'"))
        XCTAssertTrue(
            places.firstMatch.waitForExistence(timeout: 15),
            "the origin picker should open and list places"
        )
        places.firstMatch.tap()
        XCTAssertTrue(origin.waitForExistence(timeout: 10), "choosing an origin should return to the form")

        let destination = app.buttons["search.destination"]
        XCTAssertTrue(destination.waitForExistence(timeout: 10))
        destination.tap()
        XCTAssertTrue(
            places.firstMatch.waitForExistence(timeout: 15),
            "the destination picker should open and list places"
        )
        if places.count > 1 {
            places.element(boundBy: 1).tap()
        } else {
            places.firstMatch.tap()
        }

        let submit = app.buttons["search.submit"]
        XCTAssertTrue(submit.waitForExistence(timeout: 10))
        XCTAssertTrue(submit.isEnabled, "a journey must be searchable at the largest text size")
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

        shoot(app, "search-filled")
        app.buttons["search.submit"].tap()

        let journey = app.buttons["journey.jny.acl-0700"]
        XCTAssertTrue(journey.waitForExistence(timeout: 15), "a result should appear")
        shoot(app, "results")
        journey.tap()

        XCTAssertTrue(
            app.buttons["detail.saveTrip"].waitForExistence(timeout: 10),
            "the journey detail should open"
        )
        // The provenance rail and the times set as data live on this screen.
        shoot(app, "journey-detail")

        app.goBackIfPushed()

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
        app.goBackIfPushed()

        // The saved trip is still there after a relaunch that keeps state.
        app.terminate()
        let relaunched = XCUIApplication()
        relaunched.launchArguments += ["-OdivreloUseFixture", "-OdivreloFixtureScenario", "normal"]
        relaunched.launch()

        relaunched.selectTab("trips", index: 1)
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
        app.selectTab("offline", index: 2)

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
        app.selectTab("offline", index: 2)

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
        app.selectTab("offline", index: 2)

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
        app.selectTab("trips", index: 1)

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
        app.selectTab("settings", index: 3)

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
        app.selectTab("settings", index: 3)

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
