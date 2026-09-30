import Foundation
import OSLog

/// Copies the release bundled with the application into the core's packs
/// directory, so a fresh install can search offline before downloading
/// anything.
///
/// This is a delivery mechanism, not a trust decision. Everything copied here
/// is verified by the core in `adoptSeededRelease()`, which checks each pack
/// against the manifest digest and discards anything that does not match. A
/// file shipped inside the application bundle gets exactly the same scrutiny as
/// one fetched over the network.
///
/// The layout written here is the one the core reads and the one the Android
/// client writes, so both platforms seed byte-identical files from the same
/// generated release:
///
///     <packs>/current/manifest.json
///     <packs>/current/packs/<pack file>
///     <packs>/.seeded-release      marker holding the release id
///
/// The marker stops the copy repeating on every launch, and makes an
/// application update that carries a newer release seed again.
public enum SeededRelease {
    private static let log = Logger(subsystem: Brand.bundleIdentifier, category: "seed")

    /// The directory inside the application bundle holding the release.
    private static var bundledRelease: URL? {
        Bundle.main.url(forResource: "release", withExtension: nil)
    }

    /// True when this build ships a release at all.
    public static var isBundled: Bool { bundledRelease != nil }

    /// Copies the bundled release into `packsDirectory` if it is not already
    /// there. Returns the release id that was seeded, or nil if there was
    /// nothing to do.
    ///
    /// Every failure is logged and swallowed. A seed that does not arrive
    /// leaves the application exactly as it would be without one: empty, and
    /// able to download. It is never a reason to fail launch.
    @discardableResult
    public static func install(into packsDirectory: URL) -> String? {
        guard let bundled = bundledRelease else { return nil }

        let manifestSource = bundled.appending(path: "manifest.json")
        guard let manifestData = try? Data(contentsOf: manifestSource) else {
            log.error("bundled release has no readable manifest")
            return nil
        }
        guard let releaseId = releaseId(inManifest: manifestData) else {
            log.error("bundled release manifest names no release")
            return nil
        }

        let marker = packsDirectory.appending(path: ".seeded-release")
        if let seeded = try? String(contentsOf: marker, encoding: .utf8),
           seeded.trimmingCharacters(in: .whitespacesAndNewlines) == releaseId {
            return nil
        }

        let fileManager = FileManager.default
        let current = packsDirectory.appending(path: "current")
        let packs = current.appending(path: "packs")
        do {
            try fileManager.createDirectory(at: packs, withIntermediateDirectories: true)
        } catch {
            log.error("could not create the packs directory: \(String(describing: error))")
            return nil
        }

        let sourcePacks = bundled.appending(path: "packs")
        let names = (try? fileManager.contentsOfDirectory(atPath: sourcePacks.path(percentEncoded: false))) ?? []
        for name in names {
            let target = packs.appending(path: name)
            // A pack already on disk was either seeded before or downloaded.
            // Either way it is the core's to manage, so it is left alone.
            guard !fileManager.fileExists(atPath: target.path(percentEncoded: false)) else { continue }
            do {
                try fileManager.copyItem(at: sourcePacks.appending(path: name), to: target)
            } catch {
                // A half-written pack would fail its digest check, but removing
                // it keeps the directory honest about what it holds.
                try? fileManager.removeItem(at: target)
                log.error("could not seed pack \(name, privacy: .public)")
            }
        }

        do {
            try manifestData.write(to: current.appending(path: "manifest.json"), options: .atomic)
            try Data(releaseId.utf8).write(to: marker, options: .atomic)
        } catch {
            log.error("could not record the seeded release: \(String(describing: error))")
            return nil
        }

        log.info("seeded bundled release \(releaseId, privacy: .public)")
        return releaseId
    }

    /// Reads just the release id out of the manifest.
    ///
    /// Only this one field is read, and nothing is decoded into a model type,
    /// because the manifest's shape belongs to the core. Parsing it here would
    /// be a second opinion about a contract this application does not own.
    private static func releaseId(inManifest data: Data) -> String? {
        guard
            let object = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
            let releaseId = object["releaseId"] as? String,
            !releaseId.isEmpty
        else { return nil }
        return releaseId
    }
}
