import Foundation
import SwiftUI

/// Preferences that belong to the device rather than to the data release.
///
/// Everything here is stored in `UserDefaults`. Nothing here is personal: a
/// language choice, an appearance choice, a default accessibility filter, and
/// whether onboarding has been seen.
@Observable
@MainActor
public final class AppSettings {
    public static let shared = AppSettings()

    private enum Key {
        static let languageTag = "poravia.language"
        static let appearance = "poravia.appearance"
        static let hasCompletedOnboarding = "poravia.onboarded"
        static let preferAccessibleJourneys = "poravia.filter.accessible"
        static let reminderLeadMinutes = "poravia.reminder.lead"
    }

    public enum Appearance: String, CaseIterable, Sendable {
        case system, light, dark

        public var colorScheme: ColorScheme? {
            switch self {
            case .system: nil
            case .light: .light
            case .dark: .dark
            }
        }

        public var label: String {
            switch self {
            case .system: L10n.settingsAppearanceSystem
            case .light: L10n.settingsAppearanceLight
            case .dark: L10n.settingsAppearanceDark
            }
        }
    }

    private let defaults: UserDefaults

    /// `nil` follows the device language.
    public var languageTag: String? {
        didSet {
            defaults.set(languageTag, forKey: Key.languageTag)
            applyLanguage()
        }
    }

    public var appearance: Appearance {
        didSet { defaults.set(appearance.rawValue, forKey: Key.appearance) }
    }

    public var hasCompletedOnboarding: Bool {
        didSet { defaults.set(hasCompletedOnboarding, forKey: Key.hasCompletedOnboarding) }
    }

    public var preferAccessibleJourneys: Bool {
        didSet { defaults.set(preferAccessibleJourneys, forKey: Key.preferAccessibleJourneys) }
    }

    public var reminderLeadMinutes: Int {
        didSet { defaults.set(reminderLeadMinutes, forKey: Key.reminderLeadMinutes) }
    }

    /// True when the run was launched with `-PoraviaResetState`, used by UI
    /// tests so each scenario starts from a known state. It only ever clears
    /// this app's own preferences.
    public static func resetWasRequested(
        arguments: [String] = ProcessInfo.processInfo.arguments
    ) -> Bool {
        arguments.contains("-PoraviaResetState")
    }

    /// Debug only. Starts past onboarding so a screenshot or UI-test run can
    /// reach a data screen without a tap driver, which is what makes the
    /// evidence capture scriptable on every device. A Release build ignores it.
    public static func skipOnboardingWasRequested(
        arguments: [String] = ProcessInfo.processInfo.arguments
    ) -> Bool {
        #if DEBUG
        return arguments.contains("-PoraviaSkipOnboarding")
        #else
        return false
        #endif
    }

    public init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
        if Self.resetWasRequested() {
            for key in [
                Key.languageTag, Key.appearance, Key.hasCompletedOnboarding,
                Key.preferAccessibleJourneys, Key.reminderLeadMinutes,
                "poravia.session",
            ] {
                defaults.removeObject(forKey: key)
            }
        }
        let storedLanguage = defaults.string(forKey: Key.languageTag)
        self.languageTag = L10n.supportedLanguages.contains(storedLanguage ?? "") ? storedLanguage : nil
        self.appearance = Appearance(rawValue: defaults.string(forKey: Key.appearance) ?? "") ?? .system
        self.hasCompletedOnboarding = defaults.bool(forKey: Key.hasCompletedOnboarding)
            || Self.skipOnboardingWasRequested()
        self.preferAccessibleJourneys = defaults.bool(forKey: Key.preferAccessibleJourneys)
        let lead = defaults.integer(forKey: Key.reminderLeadMinutes)
        self.reminderLeadMinutes = lead > 0 ? lead : 45
        applyLanguage()
    }

    /// Pins string lookup to the language actually in effect, rather than
    /// leaving it to whichever localisation the main bundle happened to pick.
    /// Following the device language still works: `effectiveLanguageTag`
    /// resolves it from `Locale.preferredLanguages` first.
    private func applyLanguage() {
        L10n.use(languageTag: effectiveLanguageTag)
    }

    /// The language actually in effect: the explicit choice, else the best
    /// match between the device's preferences and what the product ships, else
    /// the product default from `brand.json`.
    public var effectiveLanguageTag: String {
        if let languageTag { return languageTag }
        for preferred in Locale.preferredLanguages {
            let base = String(preferred.prefix(2))
            if L10n.supportedLanguages.contains(base) { return base }
        }
        return Brand.defaultLanguageTag
    }
}
