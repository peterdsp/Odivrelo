import XCTest

/// Performance measurements for the beta budgets.
///
/// These use XCTest's own metrics rather than a stopwatch, so the figures are
/// the ones Xcode reports and can be compared between runs. Every measurement
/// runs against the Debug fixture with a fixed dataset, a fixed service date
/// and no network, so a change in the numbers means a change in the app rather
/// than a change in the data or the connection.
///
/// Run one at a time and record the device and runtime with the result:
///
///     xcodebuild -project apps/ios/Odivrelo.xcodeproj -scheme Odivrelo \
///       -destination 'platform=iOS Simulator,name=iPhone 15' \
///       -only-testing:OdivreloUITests/OdivreloPerformanceTests test
///
/// A simulator figure is not a device figure. Simulator numbers are useful for
/// catching a regression against themselves; they are not a claim about how the
/// app performs on hardware.
final class OdivreloPerformanceTests: XCTestCase {
    override func setUp() {
        super.setUp()
        continueAfterFailure = false
    }

    /// The arguments every measurement shares, so the dataset, clock, service
    /// date, locale and theme are constant and only the thing being measured
    /// varies.
    private static let fixedArguments = [
        "-OdivreloUseFixture",
        "-OdivreloFixtureScenario", "normal",
        "-OdivreloResetState",
        "-OdivreloSkipOnboarding",
        "-AppleLanguages", "(en)",
        "-AppleLocale", "en",
    ]

    @MainActor
    private func makeApp(extraArguments: [String] = []) -> XCUIApplication {
        let app = XCUIApplication()
        app.launchArguments = Self.fixedArguments + extraArguments
        return app
    }

    // MARK: Launch

    /// Cold launch to first frame, which is what a traveller waits for.
    @MainActor
    func testColdLaunchTime() {
        let app = makeApp()
        measure(metrics: [XCTApplicationLaunchMetric()]) {
            app.launch()
            app.terminate()
        }
    }

    /// Launch straight into a populated result list: the slowest realistic
    /// cold start, because the core is queried before the first useful frame.
    @MainActor
    func testColdLaunchIntoResults() {
        let app = makeApp(extraArguments: [
            "-OdivreloSeedSearch", "place.kentro-terminal,place.vathia-harbour",
        ])
        measure(metrics: [XCTApplicationLaunchMetric()]) {
            app.launch()
            app.terminate()
        }
    }

    // MARK: Search latency

    /// From tapping Search to the first result being hittable.
    @MainActor
    func testSearchLatency() {
        let app = makeApp(extraArguments: [
            "-OdivreloSeedSearch", "place.kentro-terminal,place.vathia-harbour",
        ])
        app.launch()

        let submit = app.buttons["search.submit"]
        XCTAssertTrue(submit.waitForExistence(timeout: 20), "the search form should be ready")

        measure(metrics: [XCTClockMetric(), XCTMemoryMetric(application: app)]) {
            submit.tap()
            let journey = app.buttons["journey.jny.acl-0700"]
            XCTAssertTrue(journey.waitForExistence(timeout: 20), "a result should appear")
        }
    }

    // MARK: Scrolling

    /// Scrolling the journey detail, which is the longest and densest screen:
    /// summary, boarding point, stop timeline, map and full provenance.
    @MainActor
    func testJourneyDetailScrollSmoothness() {
        let app = makeApp(extraArguments: [
            "-OdivreloSeedSearch", "place.kentro-terminal,place.vathia-harbour",
        ])
        app.launch()

        app.buttons["search.submit"].tap()
        let journey = app.buttons["journey.jny.acl-0700"]
        XCTAssertTrue(journey.waitForExistence(timeout: 20))
        journey.tap()
        XCTAssertTrue(app.buttons["detail.saveTrip"].waitForExistence(timeout: 20))

        let screen = app.scrollViews.firstMatch
        measure(
            metrics: [
                XCTOSSignpostMetric.scrollDecelerationMetric,
                XCTOSSignpostMetric.scrollDraggingMetric,
                XCTClockMetric(),
            ]
        ) {
            screen.swipeUp(velocity: .fast)
            screen.swipeUp(velocity: .fast)
            screen.swipeDown(velocity: .fast)
            screen.swipeDown(velocity: .fast)
        }
    }

    /// Scrolling the result list.
    @MainActor
    func testResultsScrollSmoothness() {
        let app = makeApp(extraArguments: [
            "-OdivreloSeedSearch", "place.kentro-terminal,place.selvia-port",
        ])
        app.launch()
        app.buttons["search.submit"].tap()
        XCTAssertTrue(app.buttons["search.filters"].waitForExistence(timeout: 20))

        let screen = app.scrollViews.firstMatch
        measure(metrics: [XCTOSSignpostMetric.scrollDecelerationMetric, XCTClockMetric()]) {
            screen.swipeUp(velocity: .fast)
            screen.swipeDown(velocity: .fast)
        }
    }

    // MARK: Memory

    /// Memory after walking search, results, detail and back, which is the
    /// path a traveller actually takes.
    @MainActor
    func testMemoryAcrossTheMainFlow() {
        let app = makeApp(extraArguments: [
            "-OdivreloSeedSearch", "place.kentro-terminal,place.vathia-harbour",
        ])

        measure(metrics: [XCTMemoryMetric(application: app)]) {
            app.launch()
            app.buttons["search.submit"].tap()
            let journey = app.buttons["journey.jny.acl-0700"]
            _ = journey.waitForExistence(timeout: 20)
            journey.tap()
            _ = app.buttons["detail.saveTrip"].waitForExistence(timeout: 20)
            app.navigationBars.buttons.element(boundBy: 0).tap()
            app.terminate()
        }
    }

    /// Opening the map, which is the heaviest single view: MapKit plus the
    /// route overlay.
    @MainActor
    func testMemoryWithTheMapOpen() {
        let app = makeApp(extraArguments: [
            "-OdivreloSeedSearch", "place.kentro-terminal,place.vathia-harbour",
        ])
        app.launch()
        app.buttons["search.submit"].tap()
        let journey = app.buttons["journey.jny.acl-0700"]
        XCTAssertTrue(journey.waitForExistence(timeout: 20))
        journey.tap()
        XCTAssertTrue(app.buttons["detail.saveTrip"].waitForExistence(timeout: 20))

        let screen = app.scrollViews.firstMatch
        measure(metrics: [XCTMemoryMetric(application: app), XCTClockMetric()]) {
            // Scroll the map into view and interact with it.
            screen.swipeUp(velocity: .fast)
            screen.swipeUp(velocity: .fast)
            let map = app.maps.firstMatch
            if map.waitForExistence(timeout: 10) {
                map.pinch(withScale: 1.6, velocity: 1.0)
                map.swipeLeft()
            }
        }
    }
}
