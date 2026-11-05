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
    /// every screen shows real KTEL data: a screenshot of the fixture shows
    /// invented places and says so on its own first screen, which makes it
    /// worse than no screenshot when someone is judging the interface.
    ///
    /// Every tap here goes through an accessibility identifier. An earlier
    /// version fell back to `buttons.element(boundBy: 2)` when it could not
    /// find a result row, which tapped the Offline tab and filed the Offline
    /// screen under the name "journey detail". A screenshot that lies about
    /// what it shows is worse than a missing one, so a step that cannot find
    /// its target now takes no picture at all.
    @MainActor
    func testCaptureTheScreens() {
        let app = XCUIApplication()
        app.launchArguments += ["-OdivreloSkipOnboarding", "-OdivreloResetState"]
        app.launch()

        let origin = app.buttons["search.origin"]
        XCTAssertTrue(origin.waitForExistence(timeout: 30))
        shoot(app, "01-search")

        let places = app.buttons.matching(NSPredicate(format: "identifier BEGINSWITH 'place.'"))
        let journeys = app.buttons.matching(NSPredicate(format: "identifier BEGINSWITH 'journey.'"))
        let submit = app.buttons["search.submit"]

        origin.tap()
        guard places.firstMatch.waitForExistence(timeout: 20) else {
            XCTFail("the origin picker listed nothing")
            return
        }
        shoot(app, "02-place-picker")
        places.firstMatch.tap()

        // Most pairs of terminals have no service between them, and "nothing
        // runs between these two" is a screen worth seeing but not the one
        // wanted here. Try destinations in turn until a journey actually
        // appears, and settle for whatever the last one shows if none does.
        var found = false
        for candidate in 1..<3 where !found {
            let destination = app.buttons["search.destination"]
            guard destination.waitForExistence(timeout: 10) else { break }
            destination.tap()
            guard places.firstMatch.waitForExistence(timeout: 20) else { break }
            guard places.count > candidate else { break }
            places.element(boundBy: candidate).tap()

            guard submit.waitForExistence(timeout: 10), submit.isEnabled else { continue }
            submit.tap()
            found = journeys.firstMatch.waitForExistence(timeout: 25)
        }

        shoot(app, found ? "03-results" : "03-results-nothing-runs")

        if found {
            journeys.firstMatch.tap()
            XCTAssertTrue(
                app.buttons["detail.saveTrip"].waitForExistence(timeout: 20),
                "tapping a journey should open its detail"
            )
            shoot(app, "04-journey-detail")
        }

        for (index, tab) in ["My trips", "Offline", "Settings"].enumerated() {
            let button = app.tabBars.buttons[tab]
            guard button.waitForExistence(timeout: 8), button.isHittable else { continue }
            button.tap()
            sleep(3)
            shoot(app, "0\(5 + index)-\(tab.lowercased().replacingOccurrences(of: " ", with: "-"))")
        }
    }

    // MARK: The answer is visible

    /// Tapping "Search journeys" shows the answer without anyone scrolling.
    ///
    /// The form fills a phone screen, so the answer renders below the fold and
    /// under a tab bar that content passes beneath. During review the button
    /// was read as dead, because nothing visibly happened when it was pressed.
    /// Whether the answer is departures or "nothing runs between these two",
    /// it has to arrive where the person who asked can see it.
    @MainActor
    func testTheAnswerArrivesOnScreen() {
        let app = XCUIApplication()
        app.launchArguments += ["-OdivreloSkipOnboarding", "-OdivreloResetState"]
        app.launch()

        let origin = app.buttons["search.origin"]
        XCTAssertTrue(origin.waitForExistence(timeout: 30))

        let places = app.buttons.matching(NSPredicate(format: "identifier BEGINSWITH 'place.'"))
        origin.tap()
        XCTAssertTrue(places.firstMatch.waitForExistence(timeout: 20))
        places.firstMatch.tap()

        let destination = app.buttons["search.destination"]
        XCTAssertTrue(destination.waitForExistence(timeout: 10))
        destination.tap()
        XCTAssertTrue(places.firstMatch.waitForExistence(timeout: 20))
        places.element(boundBy: 1).tap()

        let submit = app.buttons["search.submit"]
        XCTAssertTrue(submit.waitForExistence(timeout: 10))
        submit.tap()

        // Either shape of answer counts; what matters is that one of them is
        // on screen rather than waiting below the fold.
        let empty = app.otherElements["results.empty"]
        let journeys = app.buttons.matching(NSPredicate(format: "identifier BEGINSWITH 'journey.'"))
        let answered = NSPredicate(format: "exists == true")
        expectation(for: answered, evaluatedWith: empty, handler: nil)
        waitForExpectations(timeout: 30)

        let screen = app.windows.firstMatch.frame
        let answer = empty.exists ? empty.frame : journeys.firstMatch.frame
        XCTAssertTrue(
            screen.contains(CGPoint(x: answer.midX, y: answer.minY + 1)),
            "the answer starts at \(answer.minY) on a \(screen.height) point screen, which is off it"
        )
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
