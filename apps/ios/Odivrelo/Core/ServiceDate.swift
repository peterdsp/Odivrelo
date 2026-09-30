import Foundation

/// A service date: `YYYY-MM-DD`, interpreted in `Europe/Athens`.
///
/// This type carries and validates the contract's string form. It decides
/// nothing. Which journeys belong to a service date, what today's service date
/// is, what happens across midnight, and what a daylight-saving transition
/// does are all the shared core's, reached through
/// `OdivreloCoreClient.currentServiceDate(now:)` and
/// `shiftServiceDate(_:byDays:)`. Deliberately there is no arithmetic here.
public struct ServiceDate: Hashable, Codable, Sendable, Comparable, CustomStringConvertible {
    public let iso: String

    /// Fails on anything that is not a well-formed `YYYY-MM-DD`.
    public init?(iso: String) {
        let parts = iso.split(separator: "-", omittingEmptySubsequences: false)
        guard parts.count == 3,
              parts[0].count == 4, parts[1].count == 2, parts[2].count == 2,
              let year = Int(parts[0]), year > 0,
              let month = Int(parts[1]), (1...12).contains(month),
              let day = Int(parts[2]), (1...31).contains(day)
        else { return nil }
        self.iso = iso
    }

    /// Builds a service date from components, for the date picker.
    public init?(year: Int, month: Int, day: Int) {
        self.init(iso: String(format: "%04d-%02d-%02d", year, month, day))
    }

    public var description: String { iso }

    public var year: Int { Int(iso.prefix(4)) ?? 0 }
    public var month: Int { Int(iso.dropFirst(5).prefix(2)) ?? 0 }
    public var day: Int { Int(iso.suffix(2)) ?? 0 }

    /// The time zone the contract defines for service dates. Used only for
    /// presentation and for reading the date picker.
    public static let zone: TimeZone = {
        guard let zone = TimeZone(identifier: "Europe/Athens") else {
            preconditionFailure("Europe/Athens is required by the data contract")
        }
        return zone
    }()

    public static var calendar: Calendar {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = zone
        return calendar
    }

    /// Local noon on this date, used to drive the date picker. Noon avoids the
    /// two moments a year when midnight itself is ambiguous or absent, but it
    /// is a presentation convenience, not a timetable rule.
    public var pickerInstant: Date {
        var components = DateComponents()
        components.year = year
        components.month = month
        components.day = day
        components.hour = 12
        components.timeZone = ServiceDate.zone
        return ServiceDate.calendar.date(from: components) ?? Date(timeIntervalSince1970: 0)
    }

    /// Reads a date the picker produced back into the contract's form.
    public static func fromPicker(_ instant: Date) -> ServiceDate {
        let components = calendar.dateComponents([.year, .month, .day], from: instant)
        return ServiceDate(
            year: components.year ?? 1970,
            month: components.month ?? 1,
            day: components.day ?? 1
        ) ?? ServiceDate(iso: "1970-01-01")!
    }

    public static func < (lhs: ServiceDate, rhs: ServiceDate) -> Bool {
        lhs.iso < rhs.iso // ISO-8601 dates sort lexicographically
    }

    public init(from decoder: any Decoder) throws {
        let raw = try decoder.singleValueContainer().decode(String.self)
        guard let value = ServiceDate(iso: raw) else {
            throw DecodingError.dataCorrupted(
                .init(
                    codingPath: decoder.codingPath,
                    debugDescription: "not a YYYY-MM-DD service date: \(raw)"
                )
            )
        }
        self = value
    }

    public func encode(to encoder: any Encoder) throws {
        var container = encoder.singleValueContainer()
        try container.encode(iso)
    }
}

/// Parses and formats the ISO-8601 instants the core exchanges.
///
/// The core emits instants with an explicit offset, exactly as the contract
/// requires. This only converts between that text and `Date`; it never shifts
/// a value into another zone or reinterprets one.
public enum ContractInstant {
    // `ISO8601DateFormatter` is documented as safe for concurrent use once
    // configured, and neither instance is mutated after creation.
    private nonisolated(unsafe) static let withFractional: ISO8601DateFormatter = {
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        return formatter
    }()

    private nonisolated(unsafe) static let plain: ISO8601DateFormatter = {
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime]
        return formatter
    }()

    public static func parse(_ value: String?) -> Date? {
        guard let value, !value.isEmpty else { return nil }
        return plain.date(from: value) ?? withFractional.date(from: value)
    }

    public static func format(_ date: Date) -> String {
        plain.string(from: date)
    }
}
