import Foundation

/// How this process was started.
///
/// A unit-test run hosts the application, so the app launches before any test
/// executes. The unit tests exercise `OdivreloCoreClient` directly and must not
/// depend on a network or on installed offline packs, so the app skips its
/// launch work in that case. UI tests are the opposite: they drive the real
/// interface, and they supply their own data with `-OdivreloUseFixture`.
public enum RunMode {
    /// True when XCTest is hosting this process for a unit-test bundle.
    public static var isUnitTestRun: Bool {
        NSClassFromString("XCTestCase") != nil
            && ProcessInfo.processInfo.environment["XCTestConfigurationFilePath"] != nil
    }

    /// True when a UI test launched the app as a separate process. The app runs
    /// normally; only the data source is supplied by the test.
    public static var isUITestRun: Bool {
        ProcessInfo.processInfo.arguments.contains("-OdivreloUseFixture")
            && NSClassFromString("XCTestCase") == nil
    }
}
