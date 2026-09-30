import Foundation
import Observation
import UserNotifications
import os

/// Local departure reminders.
///
/// What a reminder payload may contain is fixed here, not by convention: the
/// departure time and the boarding point, both of which are public timetable
/// facts. It never carries a passenger name, a booking reference, a ticket
/// image or a barcode, and the user info carries only a journey identifier and
/// a service date so the app can open the right screen.
@Observable
@MainActor
public final class ReminderScheduler {
    public static let shared = ReminderScheduler()

    public enum Authorisation: Equatable, Sendable {
        case notDetermined
        case denied
        case authorised
        /// Granted once and withdrawn since. Scheduled reminders will not fire.
        case revoked
    }

    public private(set) var authorisation: Authorisation = .notDetermined
    public private(set) var scheduled: [String: Date] = [:]
    /// Set when the device time zone changed and reminders were rebuilt.
    public private(set) var didRescheduleForTimeZoneChange = false

    private let centre: UNUserNotificationCenter
    private let defaults: UserDefaults
    private let log = Logger(subsystem: Brand.bundleIdentifier, category: "reminders")
    private let everGrantedKey = "poravia.notifications.everGranted"
    private let lastTimeZoneKey = "poravia.notifications.lastTimeZone"

    public init(
        centre: UNUserNotificationCenter = .current(),
        defaults: UserDefaults = .standard
    ) {
        self.centre = centre
        self.defaults = defaults
    }

    public static func identifier(journeyId: String, serviceDate: ServiceDate) -> String {
        "poravia.reminder.\(journeyId).\(serviceDate.iso)"
    }

    // MARK: Authorisation

    public func refreshAuthorisation() async {
        let settings = await centre.notificationSettings()
        let everGranted = defaults.bool(forKey: everGrantedKey)
        switch settings.authorizationStatus {
        case .authorized, .provisional, .ephemeral:
            authorisation = .authorised
            defaults.set(true, forKey: everGrantedKey)
        case .denied:
            authorisation = everGranted ? .revoked : .denied
        case .notDetermined:
            authorisation = .notDetermined
        @unknown default:
            authorisation = .notDetermined
        }
        await refreshScheduled()
    }

    /// Asks once. A refusal is final until the person changes it in Settings,
    /// and the interface says so rather than asking again.
    public func requestAuthorisation() async {
        do {
            let granted = try await centre.requestAuthorization(options: [.alert, .sound])
            if granted { defaults.set(true, forKey: everGrantedKey) }
        } catch {
            log.error("notification authorisation request failed")
        }
        await refreshAuthorisation()
    }

    // MARK: Scheduling

    public struct ReminderRequest: Sendable {
        public var journeyId: String
        public var serviceDate: ServiceDate
        public var departure: Date
        public var boardingPointName: String
        public var leadMinutes: Int

        public init(
            journeyId: String,
            serviceDate: ServiceDate,
            departure: Date,
            boardingPointName: String,
            leadMinutes: Int
        ) {
            self.journeyId = journeyId
            self.serviceDate = serviceDate
            self.departure = departure
            self.boardingPointName = boardingPointName
            self.leadMinutes = leadMinutes
        }

        var fireDate: Date {
            departure.addingTimeInterval(-Double(leadMinutes) * 60)
        }
    }

    /// Schedules, or reschedules, exactly one reminder for a journey and date.
    ///
    /// The identifier is derived from the journey and the service date, so
    /// calling this twice replaces the reminder instead of adding a second one.
    /// That is what keeps a geometry change, a relaunch or a re-save from
    /// duplicating a notification.
    @discardableResult
    public func schedule(_ request: ReminderRequest) async -> Bool {
        guard authorisation == .authorised else { return false }
        guard request.fireDate > Date() else { return false }

        let content = UNMutableNotificationContent()
        content.title = L10n.remindersNotificationTitle
        // Departure time and boarding point only. Both are public timetable
        // facts, and neither identifies the traveller.
        content.body = L10n.remindersNotificationBody(
            Formatters.clock(request.departure),
            request.boardingPointName
        )
        content.sound = .default
        content.userInfo = [
            "journeyId": request.journeyId,
            "serviceDate": request.serviceDate.iso,
        ]

        // The trigger is built from an absolute instant, so it stays correct
        // when the device moves between time zones.
        let interval = max(1, request.fireDate.timeIntervalSinceNow)
        let trigger = UNTimeIntervalNotificationTrigger(timeInterval: interval, repeats: false)
        let identifier = Self.identifier(journeyId: request.journeyId, serviceDate: request.serviceDate)

        do {
            centre.removePendingNotificationRequests(withIdentifiers: [identifier])
            try await centre.add(UNNotificationRequest(identifier: identifier, content: content, trigger: trigger))
            scheduled[identifier] = request.fireDate
            log.info("reminder scheduled")
            return true
        } catch {
            log.error("reminder could not be scheduled")
            return false
        }
    }

    public func cancel(journeyId: String, serviceDate: ServiceDate) {
        let identifier = Self.identifier(journeyId: journeyId, serviceDate: serviceDate)
        centre.removePendingNotificationRequests(withIdentifiers: [identifier])
        scheduled.removeValue(forKey: identifier)
        log.info("reminder cancelled")
    }

    public func isScheduled(journeyId: String, serviceDate: ServiceDate) -> Bool {
        scheduled[Self.identifier(journeyId: journeyId, serviceDate: serviceDate)] != nil
    }

    public func fireDate(journeyId: String, serviceDate: ServiceDate) -> Date? {
        scheduled[Self.identifier(journeyId: journeyId, serviceDate: serviceDate)]
    }

    private func refreshScheduled() async {
        let pending = await centre.pendingNotificationRequests()
        var map: [String: Date] = [:]
        for request in pending {
            guard let trigger = request.trigger as? UNTimeIntervalNotificationTrigger,
                  let next = trigger.nextTriggerDate()
            else { continue }
            map[request.identifier] = next
        }
        scheduled = map
    }

    // MARK: Time-zone changes

    /// Rebuilds every pending reminder after the device time zone changed.
    ///
    /// A departure is an absolute instant in `Europe/Athens`; moving the device
    /// to another zone must not move the reminder. Rebuilding from the stored
    /// departure instants keeps the reminder on the same real moment and never
    /// creates a duplicate, because each identifier is replaced in place.
    public func handleTimeZoneChange(rebuilding requests: [ReminderRequest]) async {
        guard authorisation == .authorised else { return }
        let current = TimeZone.current.identifier
        let previous = defaults.string(forKey: lastTimeZoneKey)
        defaults.set(current, forKey: lastTimeZoneKey)
        guard previous != nil, previous != current else { return }

        for request in requests {
            await schedule(request)
        }
        didRescheduleForTimeZoneChange = true
        log.info("reminders rebuilt after a time zone change")
    }

    public func acknowledgeTimeZoneChange() {
        didRescheduleForTimeZoneChange = false
    }
}
