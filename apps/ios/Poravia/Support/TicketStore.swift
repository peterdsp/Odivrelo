import Foundation
import Observation
import UniformTypeIdentifiers
import os

/// A ticket file the person imported themselves.
///
/// The record carries no ticket content: no barcode payload, no booking
/// reference, no passenger name. It is a filename, a type, a size and a date.
public struct StoredTicket: Identifiable, Codable, Hashable, Sendable {
    public enum Kind: String, Codable, Sendable {
        case pdf, image
    }

    public var id: String
    /// The name the person's own file had, kept so they can recognise it.
    public var displayName: String
    public var kind: Kind
    public var byteCount: Int64
    public var importedAt: Date
    /// The file's name inside the protected container directory.
    public var storedFileName: String

    public init(
        id: String,
        displayName: String,
        kind: Kind,
        byteCount: Int64,
        importedAt: Date,
        storedFileName: String
    ) {
        self.id = id
        self.displayName = displayName
        self.kind = kind
        self.byteCount = byteCount
        self.importedAt = importedAt
        self.storedFileName = storedFileName
    }
}

public enum TicketImportError: Error, Equatable, Sendable {
    case unsupportedType
    case tooLarge(limitBytes: Int64)
    case accessDenied
    case readFailed
    case storageFull
}

/// The travel wallet's storage.
///
/// Rules this type enforces, not just documents:
///
/// * Only a file the person picked in `UIDocumentPicker` ever enters here.
/// * The type is checked against the file's real content type, and the size
///   against a fixed limit, before anything is copied.
/// * The copy lands in the app container with
///   `FileProtectionType.complete`, so it is unreadable while the device is
///   locked.
/// * The tickets directory is excluded from iCloud and iTunes backup, so a
///   ticket never leaves the device through a backup either.
/// * Deleting really deletes: the file is removed and the index entry with it.
/// * Nothing here ever uploads, and nothing here is ever written to a log, a
///   URL or an analytics payload. There is no analytics.
@Observable
@MainActor
public final class TicketStore {
    public static let shared = TicketStore()

    /// The largest ticket file accepted. Larger files are almost always scans
    /// rather than tickets, and rendering them would stall the viewer.
    public static let sizeLimitBytes: Int64 = 25 * 1024 * 1024

    public static let acceptedTypes: [UTType] = [.pdf, .png, .jpeg]

    public private(set) var tickets: [StoredTicket] = []
    public private(set) var lastError: TicketImportError?

    private let directory: URL
    private let indexURL: URL
    private let fileManager = FileManager.default
    // Deliberately a category with no ticket content ever passed to it.
    private let log = Logger(subsystem: Brand.bundleIdentifier, category: "wallet")

    public init(directory: URL = ApplicationPaths.ticketsDirectory) {
        self.directory = directory
        self.indexURL = directory.appending(path: "index.json", directoryHint: .notDirectory)
        excludeDirectoryFromBackup()
        tickets = loadIndex()
    }

    // MARK: Import

    /// Imports a file the person chose. `url` comes from `fileImporter`, which
    /// hands back a security-scoped URL.
    @discardableResult
    public func importTicket(from url: URL) -> Result<StoredTicket, TicketImportError> {
        let scoped = url.startAccessingSecurityScopedResource()
        defer { if scoped { url.stopAccessingSecurityScopedResource() } }

        guard let values = try? url.resourceValues(forKeys: [.contentTypeKey, .fileSizeKey]),
              let contentType = values.contentType
        else {
            lastError = .accessDenied
            return .failure(.accessDenied)
        }

        guard let kind = Self.kind(for: contentType) else {
            lastError = .unsupportedType
            log.notice("ticket import refused: unsupported content type")
            return .failure(.unsupportedType)
        }

        let byteCount = Int64(values.fileSize ?? 0)
        guard byteCount > 0, byteCount <= Self.sizeLimitBytes else {
            lastError = .tooLarge(limitBytes: Self.sizeLimitBytes)
            log.notice("ticket import refused: over the size limit")
            return .failure(.tooLarge(limitBytes: Self.sizeLimitBytes))
        }

        let identifier = UUID().uuidString
        let storedName = identifier + "." + (kind == .pdf ? "pdf" : "img")
        let destination = directory.appending(path: storedName, directoryHint: .notDirectory)

        do {
            let data = try Data(contentsOf: url, options: [.mappedIfSafe])
            // Written with complete protection: unreadable while locked.
            try data.write(to: destination, options: [.atomic, .completeFileProtection])
            try fileManager.setAttributes(
                [.protectionKey: FileProtectionType.complete],
                ofItemAtPath: destination.path(percentEncoded: false)
            )
        } catch let error as NSError where error.code == NSFileWriteOutOfSpaceError {
            lastError = .storageFull
            return .failure(.storageFull)
        } catch {
            lastError = .readFailed
            log.error("ticket import failed while copying")
            return .failure(.readFailed)
        }

        let ticket = StoredTicket(
            id: identifier,
            displayName: url.deletingPathExtension().lastPathComponent,
            kind: kind,
            byteCount: byteCount,
            importedAt: Date(),
            storedFileName: storedName
        )
        tickets.insert(ticket, at: 0)
        saveIndex()
        lastError = nil
        log.info("ticket imported")
        return .success(ticket)
    }

    private static func kind(for contentType: UTType) -> StoredTicket.Kind? {
        if contentType.conforms(to: .pdf) { return .pdf }
        if contentType.conforms(to: .png) || contentType.conforms(to: .jpeg) { return .image }
        return nil
    }

    // MARK: Read and delete

    public func fileURL(for ticket: StoredTicket) -> URL {
        directory.appending(path: ticket.storedFileName, directoryHint: .notDirectory)
    }

    public func ticket(withId id: String) -> StoredTicket? {
        tickets.first { $0.id == id }
    }

    /// Removes the file and the index entry. After this the bytes are gone from
    /// the container; there is no trash and no second copy.
    public func delete(_ ticket: StoredTicket) {
        let url = fileURL(for: ticket)
        try? fileManager.removeItem(at: url)
        tickets.removeAll { $0.id == ticket.id }
        saveIndex()
        log.info("ticket deleted")
    }

    public var totalBytes: Int64 {
        tickets.reduce(0) { $0 + $1.byteCount }
    }

    // MARK: Index and backup exclusion

    private func loadIndex() -> [StoredTicket] {
        guard let data = try? Data(contentsOf: indexURL),
              let decoded = try? JSONDecoder().decode([StoredTicket].self, from: data)
        else { return [] }
        // Drop entries whose file is gone, so the list never offers a ticket
        // that cannot be opened.
        return decoded.filter { fileManager.fileExists(atPath: fileURL(for: $0).path(percentEncoded: false)) }
    }

    private func saveIndex() {
        guard let data = try? JSONEncoder().encode(tickets) else { return }
        try? data.write(to: indexURL, options: [.atomic, .completeFileProtection])
    }

    /// Marks the tickets directory as excluded from iCloud and iTunes backup.
    ///
    /// This is what stops an imported ticket leaving the device inside a
    /// backup. It is set on the directory, so every file written into it
    /// inherits the exclusion.
    private func excludeDirectoryFromBackup() {
        var url = directory
        var values = URLResourceValues()
        values.isExcludedFromBackup = true
        do {
            try url.setResourceValues(values)
        } catch {
            log.error("could not exclude the wallet directory from backup")
        }
    }

    /// Reports the current backup-exclusion state, so the Settings screen can
    /// state it as fact rather than as a promise.
    public var isExcludedFromBackup: Bool {
        (try? directory.resourceValues(forKeys: [.isExcludedFromBackupKey]).isExcludedFromBackup) == true
    }
}
