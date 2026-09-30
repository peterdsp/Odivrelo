import Foundation
import Testing
@testable import Poravia

/// Localisation is a release gate: every key must exist in `el`, `en` and `sq`,
/// and the three must agree on their format specifiers. These tests read the
/// String Catalog that actually shipped in the bundle, so a hand edit to the
/// catalog fails here even though the generator would have refused it.
@Suite("Localisation completeness")
struct LocalisationCompletenessTests {
    static let languages = ["el", "en", "sq"]

    /// The catalog as it exists in the source tree. The compiled form in the
    /// bundle is `.lproj/Localizable.strings`, so the source is what carries
    /// the state and comment fields this suite checks.
    static func catalog() throws -> [String: Any] {
        let url = try #require(catalogURL(), "Localizable.xcstrings was not found")
        let data = try Data(contentsOf: url)
        let object = try JSONSerialization.jsonObject(with: data)
        return try #require(object as? [String: Any])
    }

    /// Every string a localisation carries, whether it is a flat value or a
    /// set of plural variations.
    static func texts(in localization: Any?) -> [String] {
        guard let localization = localization as? [String: Any] else { return [] }
        if let unit = localization["stringUnit"] as? [String: Any],
           let value = unit["value"] as? String {
            return [value]
        }
        guard let plural = ((localization["variations"] as? [String: Any])?["plural"])
            as? [String: Any] else { return [] }
        return plural.values.compactMap {
            (($0 as? [String: Any])?["stringUnit"] as? [String: Any])?["value"] as? String
        }
    }

    static func catalogURL() -> URL? {
        // Walk up from this file to the repository, so the test does not depend
        // on the working directory the runner chose.
        var directory = URL(fileURLWithPath: #filePath).deletingLastPathComponent()
        for _ in 0..<6 {
            let candidate = directory
                .appending(path: "Poravia/Resources/Localizable.xcstrings", directoryHint: .notDirectory)
            if FileManager.default.fileExists(atPath: candidate.path(percentEncoded: false)) {
                return candidate
            }
            directory = directory.deletingLastPathComponent()
        }
        return nil
    }

    @Test("Every key carries every supported language")
    func everyKeyIsTranslated() throws {
        let catalog = try Self.catalog()
        let strings = try #require(catalog["strings"] as? [String: Any])
        #expect(!strings.isEmpty)

        var missing: [String] = []
        for (key, value) in strings {
            guard let entry = value as? [String: Any],
                  let localizations = entry["localizations"] as? [String: Any]
            else {
                missing.append("\(key): no localizations at all")
                continue
            }
            for language in Self.languages {
                let texts = Self.texts(in: localizations[language])
                if texts.isEmpty || texts.contains(where: {
                    $0.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
                }) {
                    missing.append("\(key): \(language)")
                }
            }
        }

        #expect(missing.isEmpty, "missing translations: \(missing.sorted().joined(separator: ", "))")
    }

    @Test("Every key carries a translator comment")
    func everyKeyHasAComment() throws {
        let catalog = try Self.catalog()
        let strings = try #require(catalog["strings"] as? [String: Any])

        let withoutComment = strings.compactMap { key, value -> String? in
            let comment = (value as? [String: Any])?["comment"] as? String
            return (comment?.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ?? true) ? key : nil
        }
        #expect(withoutComment.isEmpty, "no comment: \(withoutComment.sorted().joined(separator: ", "))")
    }

    @Test("Format specifiers agree across languages")
    func formatSpecifiersAgree() throws {
        let catalog = try Self.catalog()
        let strings = try #require(catalog["strings"] as? [String: Any])
        let pattern = try NSRegularExpression(pattern: #"%(?:(\d+)\$)?(lld|ld|d|@|f)"#)

        func shape(_ text: String) -> [String] {
            let range = NSRange(text.startIndex..., in: text)
            var found: [(Int, String)] = []
            for (index, match) in pattern.matches(in: text, range: range).enumerated() {
                let position = Range(match.range(at: 1), in: text).flatMap { Int(text[$0]) } ?? index + 1
                let kind = Range(match.range(at: 2), in: text).map { String(text[$0]) } ?? ""
                found.append((position, kind))
            }
            return found.sorted { $0.0 < $1.0 }.map(\.1)
        }

        var disagreements: [String] = []
        for (key, value) in strings {
            guard let localizations = (value as? [String: Any])?["localizations"] as? [String: Any]
            else { continue }
            var shapes: [String: [String]] = [:]
            for language in Self.languages {
                for (index, text) in Self.texts(in: localizations[language]).enumerated() {
                    shapes["\(language)[\(index)]"] = shape(text)
                }
            }
            if Set(shapes.values.map { $0.joined(separator: ",") }).count > 1 {
                disagreements.append("\(key): \(shapes)")
            }
        }
        #expect(disagreements.isEmpty, "format specifiers differ: \(disagreements.joined(separator: " | "))")
    }

    @Test("Every key the app can ask for exists in the catalog")
    func generatedKeysExistInCatalog() throws {
        let catalog = try Self.catalog()
        let strings = try #require(catalog["strings"] as? [String: Any])
        let absent = L10n.allKeys.filter { strings[$0] == nil }
        #expect(absent.isEmpty, "keys used by the app but absent from the catalog: \(absent)")
    }

    @Test("The catalog carries no key the app cannot ask for")
    func catalogHasNoOrphanKeys() throws {
        let catalog = try Self.catalog()
        let strings = try #require(catalog["strings"] as? [String: Any])
        let known = Set(L10n.allKeys)
        let orphans = strings.keys.filter { !known.contains($0) }
        #expect(orphans.isEmpty, "keys in the catalog with no accessor: \(orphans.sorted())")
    }

    @Test("Each supported language resolves to its own text at runtime",
          arguments: ["el", "en", "sq"])
    func languagesResolveDistinctly(_ language: String) throws {
        L10n.use(languageTag: language)
        defer { L10n.use(languageTag: nil) }

        // A key whose three translations are genuinely different words.
        let resolved = L10n.searchSubmit
        #expect(!resolved.isEmpty)
        #expect(resolved != "search.submit", "the key itself came back, so the bundle was not found")
    }

    @Test("The three languages do not all collapse to one string")
    func languagesDiffer() throws {
        var seen: Set<String> = []
        for language in Self.languages {
            L10n.use(languageTag: language)
            seen.insert(L10n.searchSubmit)
        }
        L10n.use(languageTag: nil)
        #expect(seen.count == Self.languages.count, "expected a distinct translation per language: \(seen)")
    }

    /// A counted string without plural variations renders as "1 journeys".
    @Test("Every counted string carries plural variations in every language")
    func countedStringsArePluralised() throws {
        let catalog = try Self.catalog()
        let strings = try #require(catalog["strings"] as? [String: Any])

        var missing: [String] = []
        for key in L10n.pluralKeys {
            guard let localizations = (strings[key] as? [String: Any])?["localizations"]
                as? [String: Any] else {
                missing.append("\(key): no localizations")
                continue
            }
            for language in Self.languages {
                guard let localization = localizations[language] as? [String: Any],
                      let plural = ((localization["variations"] as? [String: Any])?["plural"])
                        as? [String: Any]
                else {
                    missing.append("\(key)[\(language)]: no plural variations")
                    continue
                }
                for category in ["one", "other"] where plural[category] == nil {
                    missing.append("\(key)[\(language)]: no '\(category)' form")
                }
            }
        }
        #expect(missing.isEmpty, "\(missing.sorted().joined(separator: ", "))")
    }

    @Test("A count of one reads as a singular", arguments: ["el", "en", "sq"])
    func singularReadsCorrectly(_ language: String) {
        L10n.use(languageTag: language)
        defer { L10n.use(languageTag: nil) }

        let one = L10n.resultsCount(1)
        let many = L10n.resultsCount(5)
        #expect(one != many, "\(language): singular and plural must differ, got '\(one)'")
        #expect(one.contains("1"))
        #expect(many.contains("5"))
    }

    @Test("English singular is not the plural word")
    func englishSingularIsCorrect() {
        L10n.use(languageTag: "en")
        defer { L10n.use(languageTag: nil) }
        #expect(L10n.resultsCount(1) == "1 journey")
        #expect(L10n.resultsCount(0) == "0 journeys")
        #expect(L10n.resultsCount(2) == "2 journeys")
        #expect(L10n.resultsIntermediateStops(1) == "1 intermediate stop")
        #expect(L10n.resultsIntermediateStops(3) == "3 intermediate stops")
    }

    @Test("The default language is the one brand.json declares")
    func defaultLanguageMatchesBrand() {
        #expect(Brand.defaultLanguageTag == "el")
        #expect(L10n.supportedLanguages == Brand.languageTags)
    }
}

/// The product's identity comes from `brand.json` and nothing else.
@Suite("Brand identity")
struct BrandIdentityTests {
    static func brandJSON() throws -> [String: Any] {
        var directory = URL(fileURLWithPath: #filePath).deletingLastPathComponent()
        for _ in 0..<8 {
            let candidate = directory.appending(path: "brand.json", directoryHint: .notDirectory)
            if FileManager.default.fileExists(atPath: candidate.path(percentEncoded: false)) {
                let data = try Data(contentsOf: candidate)
                return try #require(try JSONSerialization.jsonObject(with: data) as? [String: Any])
            }
            directory = directory.deletingLastPathComponent()
        }
        Issue.record("brand.json was not found above \(#filePath)")
        return [:]
    }

    @Test("Generated constants match brand.json exactly")
    func generatedConstantsMatchSource() throws {
        let brand = try Self.brandJSON()
        #expect(Brand.name == brand["name"] as? String)
        #expect(Brand.slug == brand["slug"] as? String)
        #expect(Brand.domain == brand["domain"] as? String)
        #expect(Brand.bundleIdentifier == brand["bundleId"] as? String)
        #expect(Brand.urlScheme == brand["urlScheme"] as? String)
        #expect(Brand.contractVersion == brand["contractVersion"] as? String)
        #expect(Brand.languageTags == brand["languages"] as? [String])
    }

    @Test("The running bundle carries the brand identity")
    func bundleMatchesBrand() {
        #expect(Bundle.main.bundleIdentifier?.hasPrefix(Brand.bundleIdentifier) == true)
    }

    /// No legacy product name may appear in any string the app can show, in any
    /// language. The names are read from `brand.json` rather than compiled in,
    /// so the binary itself stays clean.
    @Test("No legacy product name appears in any user-facing string")
    func noLegacyNamesInStrings() throws {
        let brand = try Self.brandJSON()
        let legacy = (brand["legacyNames"] as? [String] ?? []).map { $0.lowercased() }
        #expect(!legacy.isEmpty, "brand.json should list the names to keep out")

        let catalog = try LocalisationCompletenessTests.catalog()
        let strings = try #require(catalog["strings"] as? [String: Any])

        var offences: [String] = []
        for (key, value) in strings {
            guard let localizations = (value as? [String: Any])?["localizations"] as? [String: Any]
            else { continue }
            for (language, localized) in localizations {
                for text in LocalisationCompletenessTests.texts(in: localized) {
                    let lowered = text.lowercased()
                    for name in legacy where lowered.contains(name) {
                        offences.append("\(key)[\(language)] contains \(name)")
                    }
                }
            }
            for name in legacy where key.lowercased().contains(name) {
                offences.append("key \(key) contains \(name)")
            }
        }
        #expect(offences.isEmpty, "\(offences.joined(separator: ", "))")
    }

    @Test("The associated domain entitlement names only the controlled domain")
    func associatedDomainIsTheControlledDomain() {
        #expect(Brand.associatedDomainEntitlement == "applinks:poravia.peterdsp.dev")
        #expect(Brand.universalLinkPrefix == "https://poravia.peterdsp.dev")
    }
}
