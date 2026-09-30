import Foundation

/// Facts about this build, for the Settings screen and the diagnostics report.
/// Nothing here is a secret and nothing here identifies a person.
public enum BuildInfo: Sendable {
    public static var marketingVersion: String {
        Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String
            ?? Brand.marketingVersion
    }

    public static var buildNumber: String {
        Bundle.main.object(forInfoDictionaryKey: "CFBundleVersion") as? String ?? "0"
    }

    /// The source commit, injected at build time by `scripts/ios-bootstrap.sh`.
    /// `unknown` when the project was generated outside a git checkout.
    public static var commit: String {
        Bundle.main.object(forInfoDictionaryKey: "OdivreloSourceCommit") as? String ?? "unknown"
    }

    public static var configuration: String {
        #if DEBUG
        "Debug"
        #else
        "Release"
        #endif
    }

    public static var bundleIdentifier: String {
        Bundle.main.bundleIdentifier ?? Brand.bundleIdentifier
    }
}
