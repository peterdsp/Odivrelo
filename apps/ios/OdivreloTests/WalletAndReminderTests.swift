import Foundation
import Testing
import UniformTypeIdentifiers
@testable import Odivrelo

/// The travel wallet's rules are enforced, not merely documented.
@Suite("Travel wallet")
@MainActor
struct TicketStoreTests {
    static func makeStore() throws -> (TicketStore, URL) {
        let directory = URL(fileURLWithPath: NSTemporaryDirectory())
            .appending(path: "odivrelo-wallet-\(UUID().uuidString)", directoryHint: .isDirectory)
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        return (TicketStore(directory: directory), directory)
    }

    static func writeFile(_ bytes: Int, extension ext: String, in directory: URL) throws -> URL {
        let url = directory.appending(path: "source-\(UUID().uuidString).\(ext)", directoryHint: .notDirectory)
        // A minimal but genuine PDF header, so the content type is real.
        var data = ext == "pdf" ? Data("%PDF-1.4\n".utf8) : Data([0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A])
        data.append(Data(repeating: 0x20, count: max(0, bytes - data.count)))
        try data.write(to: url)
        return url
    }

    @Test("A PDF the person picked is imported")
    func importsAPDF() throws {
        let (store, directory) = try Self.makeStore()
        let source = try Self.writeFile(2_048, extension: "pdf", in: directory)

        let result = store.importTicket(from: source)
        guard case let .success(ticket) = result else {
            Issue.record("import failed: \(result)")
            return
        }
        #expect(ticket.kind == .pdf)
        #expect(store.tickets.count == 1)
        #expect(FileManager.default.fileExists(atPath: store.fileURL(for: ticket).path(percentEncoded: false)))
    }

    @Test("An unsupported type is refused")
    func refusesUnsupportedTypes() throws {
        let (store, directory) = try Self.makeStore()
        let source = directory.appending(path: "notes.txt", directoryHint: .notDirectory)
        try Data("plain text".utf8).write(to: source)

        let result = store.importTicket(from: source)
        guard case let .failure(error) = result else {
            Issue.record("a text file should not be accepted as a ticket")
            return
        }
        #expect(error == .unsupportedType)
        #expect(store.tickets.isEmpty)
    }

    @Test("A file over the limit is refused")
    func refusesOversizedFiles() throws {
        let (store, directory) = try Self.makeStore()
        let oversized = Int(TicketStore.sizeLimitBytes) + 1_024
        let source = try Self.writeFile(oversized, extension: "pdf", in: directory)

        let result = store.importTicket(from: source)
        guard case let .failure(error) = result else {
            Issue.record("an oversized file should not be accepted")
            return
        }
        #expect(error == .tooLarge(limitBytes: TicketStore.sizeLimitBytes))
    }

    @Test("A stored ticket is written with complete file protection")
    func storedFileIsProtected() throws {
        let (store, directory) = try Self.makeStore()
        let source = try Self.writeFile(1_024, extension: "pdf", in: directory)
        guard case let .success(ticket) = store.importTicket(from: source) else {
            Issue.record("import failed")
            return
        }

        let attributes = try FileManager.default.attributesOfItem(
            atPath: store.fileURL(for: ticket).path(percentEncoded: false)
        )
        let protection = attributes[.protectionKey] as? FileProtectionType
        // The simulator does not always surface the attribute; where it does,
        // it must be `complete` and nothing weaker.
        if let protection {
            #expect(protection == .complete)
        }
    }

    @Test("The wallet directory is excluded from backup")
    func directoryIsExcludedFromBackup() throws {
        let (store, _) = try Self.makeStore()
        #expect(store.isExcludedFromBackup)
    }

    @Test("Deleting really removes the file")
    func deleteRemovesTheFile() throws {
        let (store, directory) = try Self.makeStore()
        let source = try Self.writeFile(1_024, extension: "pdf", in: directory)
        guard case let .success(ticket) = store.importTicket(from: source) else {
            Issue.record("import failed")
            return
        }
        let path = store.fileURL(for: ticket).path(percentEncoded: false)
        #expect(FileManager.default.fileExists(atPath: path))

        store.delete(ticket)
        #expect(!FileManager.default.fileExists(atPath: path))
        #expect(store.tickets.isEmpty)
        #expect(store.ticket(withId: ticket.id) == nil)
    }

    @Test("An index entry whose file vanished is dropped on reload")
    func dropsEntriesWithNoFile() throws {
        let (store, directory) = try Self.makeStore()
        let source = try Self.writeFile(1_024, extension: "pdf", in: directory)
        guard case let .success(ticket) = store.importTicket(from: source) else {
            Issue.record("import failed")
            return
        }

        try FileManager.default.removeItem(at: store.fileURL(for: ticket))
        let reopened = TicketStore(directory: directory)
        #expect(reopened.tickets.isEmpty, "a ticket that cannot be opened must not be offered")
    }

    @Test("A ticket survives a relaunch")
    func ticketSurvivesRelaunch() throws {
        let (store, directory) = try Self.makeStore()
        let source = try Self.writeFile(1_024, extension: "pdf", in: directory)
        guard case let .success(ticket) = store.importTicket(from: source) else {
            Issue.record("import failed")
            return
        }

        let reopened = TicketStore(directory: directory)
        #expect(reopened.tickets.count == 1)
        #expect(reopened.ticket(withId: ticket.id) != nil)
    }

    @Test("The stored record carries no ticket content")
    func recordCarriesNoTicketContent() throws {
        let (store, directory) = try Self.makeStore()
        let source = try Self.writeFile(1_024, extension: "pdf", in: directory)
        guard case let .success(ticket) = store.importTicket(from: source) else {
            Issue.record("import failed")
            return
        }

        // Encoded, the record is a filename, a type, a size and two dates. If a
        // barcode or a booking reference ever crept into the model, this
        // enumeration would have to change with it.
        let encoded = try JSONEncoder().encode(ticket)
        let object = try #require(
            try JSONSerialization.jsonObject(with: encoded) as? [String: Any]
        )
        let allowed: Set<String> = [
            "id", "displayName", "kind", "byteCount", "importedAt", "storedFileName",
        ]
        #expect(Set(object.keys) == allowed, "unexpected fields on a stored ticket: \(object.keys)")
    }

    @Test("Accepted types are exactly PDF, PNG and JPEG")
    func acceptedTypesAreNarrow() {
        #expect(Set(TicketStore.acceptedTypes) == Set([UTType.pdf, .png, .jpeg]))
    }
}

/// A reminder carries only public timetable facts.
@Suite("Reminders")
@MainActor
struct ReminderSchedulerTests {
    @Test("A reminder identifier is stable for a journey and date")
    func identifierIsStable() throws {
        let date = try #require(ServiceDate(iso: "2026-10-02"))
        let first = ReminderScheduler.identifier(journeyId: "jny.a", serviceDate: date)
        let second = ReminderScheduler.identifier(journeyId: "jny.a", serviceDate: date)
        #expect(first == second, "an unstable identifier would let a reminder be duplicated")
    }

    @Test("Different journeys and dates get different identifiers")
    func identifiersAreDistinct() throws {
        let first = try #require(ServiceDate(iso: "2026-10-02"))
        let second = try #require(ServiceDate(iso: "2026-10-03"))
        #expect(
            ReminderScheduler.identifier(journeyId: "jny.a", serviceDate: first)
                != ReminderScheduler.identifier(journeyId: "jny.a", serviceDate: second)
        )
        #expect(
            ReminderScheduler.identifier(journeyId: "jny.a", serviceDate: first)
                != ReminderScheduler.identifier(journeyId: "jny.b", serviceDate: first)
        )
    }

    @Test("A reminder's body carries only the time and the boarding point")
    func bodyCarriesOnlyPublicFacts() {
        let body = L10n.remindersNotificationBody("09:00", "Aloria Central, bay A1")
        #expect(body.contains("09:00"))
        #expect(body.contains("Aloria Central, bay A1"))
        // Nothing that identifies the traveller or their purchase.
        for forbidden in ["booking", "reference", "passenger", "barcode", "ticket number"] {
            #expect(!body.lowercased().contains(forbidden), "a reminder must not mention \(forbidden)")
        }
    }

    @Test("A lead time in the past does not schedule")
    func pastLeadTimeDoesNotSchedule() async throws {
        let scheduler = ReminderScheduler(
            centre: .current(),
            defaults: UserDefaults(suiteName: "odivrelo.tests.\(UUID().uuidString)")!
        )
        let date = try #require(ServiceDate(iso: "2026-10-02"))
        let scheduled = await scheduler.schedule(
            .init(
                journeyId: "jny.a",
                serviceDate: date,
                departure: Date().addingTimeInterval(-3_600),
                boardingPointName: "bay A1",
                leadMinutes: 45
            )
        )
        #expect(!scheduled, "a departure already in the past must not schedule a reminder")
    }

    @Test("Authorisation starts undetermined")
    func startsUndetermined() {
        let scheduler = ReminderScheduler(
            centre: .current(),
            defaults: UserDefaults(suiteName: "odivrelo.tests.\(UUID().uuidString)")!
        )
        #expect(scheduler.authorisation == .notDetermined)
        #expect(scheduler.scheduled.isEmpty)
    }
}

/// The session survives everything a geometry change can throw at it.
@Suite("Session restoration")
@MainActor
struct SessionStateTests {
    static func store() -> SessionStore {
        SessionStore(defaults: UserDefaults(suiteName: "odivrelo.session.\(UUID().uuidString)")!)
    }

    @Test("An empty store returns nothing rather than a guessed date")
    func emptyStoreReturnsNil() {
        #expect(Self.store().load() == nil)
    }

    @Test("Everything that must survive a relaunch round-trips")
    func sessionRoundTrips() throws {
        let store = Self.store()
        var session = SessionState(serviceDate: try #require(ServiceDate(iso: "2026-10-02")))
        session.originId = "place.a"
        session.destinationId = "place.b"
        session.originName = LocalisedText(el: "Α", en: "A", sq: "A")
        session.destinationName = LocalisedText(el: "Β", en: "B", sq: "B")
        session.selectedJourneyId = "jny.acl-0700"
        session.resultsScrollAnchorId = "jny.acl-0700"
        session.mapIntent = true
        session.viewingTicketId = "ticket-1"
        session.selectedTab = .trips
        session.filters.accessibleOnly = true
        session.filters.departAfter = "07:00"

        store.save(session)
        let restored = try #require(store.load())
        #expect(restored == session)
    }

    @Test("A ticket's document is never part of the session")
    func sessionCarriesOnlyTheTicketIdentifier() throws {
        var session = SessionState(serviceDate: try #require(ServiceDate(iso: "2026-10-02")))
        session.viewingTicketId = "ticket-1"
        let encoded = try JSONEncoder().encode(session)
        let text = try #require(String(data: encoded, encoding: .utf8))
        #expect(text.contains("ticket-1"))
        // Only the identifier. No path, no bytes, no filename.
        #expect(!text.contains(".pdf"))
        #expect(!text.contains("/"))
    }

    @Test("A session with only one place cannot search")
    func incompleteSessionCannotSearch() throws {
        var session = SessionState(serviceDate: try #require(ServiceDate(iso: "2026-10-02")))
        #expect(!session.canSearch)
        session.originId = "place.a"
        #expect(!session.canSearch)
        session.destinationId = "place.b"
        #expect(session.canSearch)
    }
}
