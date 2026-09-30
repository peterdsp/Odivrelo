import Foundation
import os

/// Chooses the `PoraviaCoreClient` conformance for a build.
///
/// There is exactly one production conformance: `PoraviaCoreAdapter` over the
/// `PoraviaCore` XCFramework. When that framework is not linked, this factory
/// returns `UnavailableCoreClient`, which reports the missing core rather than
/// inventing results.
///
/// The Debug-only fixture is not referenced here at all. `FixtureCoreClient`
/// exists inside `#if DEBUG`, so a Release build cannot name the type: the
/// guard is the compiler, not a runtime flag. `PoraviaCoreReleaseGuardTests`
/// asserts the property below, and `scripts/ios-verify-release.sh` proves the
/// symbol is absent from the archived binary.
public enum CoreClientFactory {
    public enum Selection: String, Sendable {
        /// The shared Kotlin core, linked and in use.
        case sharedCore
        /// The shared core is not in this build.
        case unavailable
        /// The Debug-only in-memory fixture, for previews, tests and the
        /// simulator walkthrough. Never produced by a Release build.
        case fixture
    }

    private static let log = Logger(subsystem: Brand.bundleIdentifier, category: "core")

    /// Whether this build is capable of constructing a fixture conformance.
    /// False in every Release build, by construction.
    public static var fixturesAreReachable: Bool {
        #if DEBUG
        true
        #else
        false
        #endif
    }

    /// Whether this build linked the shared Kotlin core.
    public static var sharedCoreIsLinked: Bool {
        #if PORAVIA_CORE_AVAILABLE
        true
        #else
        false
        #endif
    }

    /// The reason shown to a person when no shared core is present.
    public static let missingCoreReason =
        "The shared Poravia data core was not linked into this build."

    /// True when this run was launched asking for the fixture, with
    /// `-PoraviaUseFixture` or `PORAVIA_USE_FIXTURE=1`. Only a Debug build can
    /// act on it; a Release build has no fixture to select.
    public static func fixtureWasRequested(
        arguments: [String] = ProcessInfo.processInfo.arguments,
        environment: [String: String] = ProcessInfo.processInfo.environment
    ) -> Bool {
        arguments.contains("-PoraviaUseFixture") || environment["PORAVIA_USE_FIXTURE"] == "1"
    }

    public static func make(config: CoreConfig, preferFixture: Bool = false) -> (any PoraviaCoreClient, Selection) {
        #if PORAVIA_CORE_AVAILABLE
        if !preferFixture {
            do {
                let adapter = try PoraviaCoreAdapter(config: config)
                log.info("Poravia core adapter created")
                return (adapter, .sharedCore)
            } catch {
                log.error("Poravia core adapter could not be created: \(String(describing: error))")
                return (UnavailableCoreClient(reason: String(describing: error)), .unavailable)
            }
        }
        #endif

        #if DEBUG
        if preferFixture || !sharedCoreIsLinked {
            return (FixtureCoreClient(), .fixture)
        }
        #endif

        return (UnavailableCoreClient(reason: missingCoreReason), .unavailable)
    }
}
