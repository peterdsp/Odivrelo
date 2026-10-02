import XCTest

/// Navigation helpers that work across the layouts the app adapts to.
///
/// iPhone presents the `TabView` as a bottom tab bar and pushes detail onto a
/// `NavigationStack`. iPadOS 26 presents the same `TabView` as a top bar or a
/// sidebar, and the search tab shows results beside the detail in a
/// `NavigationSplitView`, so there is no detail to pop. A test written against
/// one presentation must not silently fail on the other: a missing tab button
/// or an absent back button is a layout difference, not proof that the
/// behaviour behind the step is broken.
extension XCUIApplication {
    /// Selects a main tab by its stable identifier, independent of language and
    /// of how the platform draws the tab control. Falls back to the bottom tab
    /// bar by index so the compact layout keeps working even if a future SDK
    /// stops forwarding the identifier to the tab item.
    @MainActor
    func selectTab(
        _ tab: String,
        index: Int,
        file: StaticString = #filePath,
        line: UInt = #line
    ) {
        let id = "tab-\(tab)"
        // iPad can expose the same tab identifier more than once (the tab
        // control plus, in some presentations, a mirror), so a plain
        // subscript tap fails with "multiple matching elements". Take the
        // first hittable match instead, trying each container in turn.
        let queries = [
            tabBars.buttons.matching(identifier: id),
            buttons.matching(identifier: id),
            cells.matching(identifier: id),
        ]
        for query in queries where query.firstMatch.waitForExistence(timeout: 3) {
            for i in 0 ..< query.count {
                let element = query.element(boundBy: i)
                if element.isHittable {
                    element.tap()
                    return
                }
            }
        }
        let byIndex = tabBars.buttons.element(boundBy: index)
        if byIndex.waitForExistence(timeout: 2), byIndex.isHittable {
            byIndex.tap()
            return
        }
        XCTFail(
            "could not find a hittable \(tab) tab under any navigation presentation",
            file: file,
            line: line
        )
    }

    /// Pops a pushed detail when the layout is a navigation stack. On a
    /// split-view layout the detail sits beside the list, so there is nothing to
    /// pop and the list is already on screen; this is then a no-op.
    @MainActor
    func goBackIfPushed() {
        let back = navigationBars.buttons.element(boundBy: 0)
        if back.exists, back.isHittable {
            back.tap()
        }
    }
}
