import Foundation

/// Presentation formatting only.
///
/// Nothing here decides what a time *means*. Service dates, midnight crossing
/// and freshness thresholds are the shared core's. These helpers turn values
/// the core already decided into text for the current locale, and they render
/// a journey's clock times in `Europe/Athens`, because that is the zone the
/// timetable is published in and the one a traveller reads at the terminal.
@MainActor
public enum Formatters {
    /// Locale used for presentation: the language the person chose, so a Greek
    /// interface formats dates in Greek even on an English device.
    public static var presentationLocale: Locale {
        Locale(identifier: AppSettings.shared.effectiveLanguageTag)
    }

    // MARK: Clock times

    /// A departure or arrival time, in the timetable's own zone.
    public static func clock(_ date: Date) -> String {
        var style = Date.FormatStyle(date: .omitted, time: .shortened)
        style.timeZone = ServiceDate.zone
        style.locale = presentationLocale
        return date.formatted(style)
    }

    /// A clock time with the calendar day, used where a journey crosses
    /// midnight and the bare time would be ambiguous.
    public static func clockWithDay(_ date: Date) -> String {
        var style = Date.FormatStyle(date: .abbreviated, time: .shortened)
        style.timeZone = ServiceDate.zone
        style.locale = presentationLocale
        return date.formatted(style)
    }

    // MARK: Dates

    public static func serviceDate(_ value: ServiceDate) -> String {
        var style = Date.FormatStyle(date: .complete, time: .omitted)
        style.timeZone = ServiceDate.zone
        style.locale = presentationLocale
        return value.pickerInstant.formatted(style)
    }

    public static func shortServiceDate(_ value: ServiceDate) -> String {
        var style = Date.FormatStyle(date: .abbreviated, time: .omitted)
        style.timeZone = ServiceDate.zone
        style.locale = presentationLocale
        return value.pickerInstant.formatted(style)
    }

    /// A retrieval or verification timestamp, shown in the reader's own zone
    /// because it describes when a person did something, not a departure.
    public static func timestamp(_ date: Date) -> String {
        var style = Date.FormatStyle(date: .abbreviated, time: .shortened)
        style.locale = presentationLocale
        return date.formatted(style)
    }

    // MARK: Durations and ages

    public static func duration(minutes: Int) -> String {
        let clamped = max(0, minutes)
        let hours = clamped / 60
        let remainder = clamped % 60
        // "0h 45m" and "3h 0m" both read badly, so each case gets its own form.
        if hours == 0 { return L10n.resultsDurationMinutesOnly(remainder) }
        if remainder == 0 { return L10n.resultsDurationHoursOnly(hours) }
        return L10n.resultsDurationMinutes(hours, remainder)
    }

    /// Freshness age, in hours below a day and in days above it.
    public static func freshnessAge(hours: Int) -> String {
        let clamped = max(0, hours)
        if clamped < 48 { return L10n.freshnessCheckedHours(clamped) }
        return L10n.freshnessCheckedDays(clamped / 24)
    }

    // MARK: Money and bytes

    public static func fare(_ fare: Fare) -> String {
        let amount = fare.amount.formatted(
            .currency(code: fare.currency).locale(presentationLocale)
        )
        return fare.isIndicative ? L10n.resultsFareIndicative(amount) : amount
    }

    /// Freshness age where the core reported no check time at all.
    public static func freshnessAge(_ freshness: Freshness) -> String {
        guard freshness.checkedAt != nil else { return L10n.freshnessUnknown }
        return freshnessAge(hours: freshness.ageHours)
    }

    public static func bytes(_ value: Int64) -> String {
        let formatter = ByteCountFormatter()
        formatter.countStyle = .file
        formatter.allowedUnits = [.useKB, .useMB, .useGB]
        // The formatter spells zero as "Zero KB", which reads oddly in a size
        // column next to real figures.
        formatter.allowsNonnumericFormatting = false
        return formatter.string(fromByteCount: max(0, value))
    }

    // MARK: Minutes of day, for the departure-window filter

    public static func minuteOfDay(_ minute: Int) -> String {
        var components = DateComponents()
        components.hour = minute / 60
        components.minute = minute % 60
        components.timeZone = ServiceDate.zone
        components.year = 2000
        components.month = 1
        components.day = 1
        guard let date = ServiceDate.calendar.date(from: components) else { return "" }
        return clock(date)
    }
}
