import Foundation
import OSLog

#if ODIVRELO_CORE_AVAILABLE
import OdivreloCore
#endif

/// Turns whatever the core throws into the interface's error vocabulary.
///
/// Compiled in every configuration so the mapping is unit-testable with or
/// without the framework linked.
public enum CoreErrorTranslation {
    private static let log = Logger(subsystem: Brand.bundleIdentifier, category: "core")

    /// The domain Kotlin/Native uses when a Kotlin exception crosses into
    /// Objective-C. The thrown `OdivreloException` is carried in the user info.
    public static let kotlinExceptionKey = "KotlinException"

    public static func translate(_ error: any Error) -> CoreError {
        if let core = error as? CoreError { return core }

        let nsError = error as NSError

        #if ODIVRELO_CORE_AVAILABLE
        // The core's own exception type carries the contract error code and,
        // for an invalid request, the field that was rejected.
        if let thrown = nsError.userInfo[kotlinExceptionKey] as? OdivreloException {
            // The contract code alone does not say what went wrong, and the
            // core's own sentence is the only place that detail exists. It is a
            // technical message about data, never about a person, so recording
            // it costs nothing and not recording it makes a failure like
            // "unavailable" impossible to act on.
            log.error(
                """
                core raised \(String(describing: thrown.code), privacy: .public):                 \(thrown.message, privacy: .public)                \(thrown.field.map { " (field: \($0))" } ?? "", privacy: .public)
                """
            )
            return fromContract(
                code: CoreMapping.errorCode(thrown.code),
                field: thrown.field,
                kind: CoreMapping.failureKind(thrown.kind)
            )
        }
        #endif

        if nsError.domain == NSURLErrorDomain {
            switch nsError.code {
            case NSURLErrorNotConnectedToInternet,
                 NSURLErrorNetworkConnectionLost,
                 NSURLErrorDataNotAllowed,
                 NSURLErrorInternationalRoamingOff,
                 NSURLErrorCannotConnectToHost,
                 NSURLErrorCannotFindHost,
                 NSURLErrorDNSLookupFailed:
                return .offline
            case NSURLErrorCancelled:
                return .cancelled
            default:
                // A transport failure the core never saw, so it has no kind to
                // offer. It is not a statement about installed data.
                return .unavailable(kind: .networkUnavailable)
            }
        }

        if nsError.domain == NSCocoaErrorDomain, nsError.code == NSFileWriteOutOfSpaceError {
            return .pack(.storageFull, packName: "", message: nil)
        }

        if error is CancellationError { return .cancelled }

        return .unexpected(nsError.localizedDescription)
    }

    /// Maps one of the five contract codes onto a presentable error, keeping
    /// the core's account of the failure so the interface can act on it.
    public static func fromContract(
        code: ContractErrorCode,
        field: String?,
        kind: CoreFailureKind = .general
    ) -> CoreError {
        switch code {
        case .notFound: .notFound
        case .invalidRequest: .invalidRequest(field: field)
        case .unavailable: .unavailable(kind: kind)
        case .releaseMismatch: .releaseMismatch
        case .unauthorized: .unauthorized
        }
    }

    /// Maps a failed pack download onto a presentable error.
    public static func fromPack(_ result: PackResult) -> CoreError {
        guard let failure = result.failure else {
            return .unexpected("the core reported a failed download with no reason")
        }
        if failure == .cancelled { return .cancelled }
        if failure == .network || failure == .timeout {
            return .offline
        }
        return .pack(failure, packName: result.packName, message: result.message)
    }

    static func run<T>(_ body: () async throws -> T) async throws -> T {
        do {
            return try await body()
        } catch {
            throw translate(error)
        }
    }
}
