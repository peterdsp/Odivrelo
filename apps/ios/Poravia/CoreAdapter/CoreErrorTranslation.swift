import Foundation

#if PORAVIA_CORE_AVAILABLE
import PoraviaCore
#endif

/// Turns whatever the core throws into the interface's error vocabulary.
///
/// Compiled in every configuration so the mapping is unit-testable with or
/// without the framework linked.
public enum CoreErrorTranslation {
    /// The domain Kotlin/Native uses when a Kotlin exception crosses into
    /// Objective-C. The thrown `PoraviaException` is carried in the user info.
    public static let kotlinExceptionKey = "KotlinException"

    public static func translate(_ error: any Error) -> CoreError {
        if let core = error as? CoreError { return core }

        let nsError = error as NSError

        #if PORAVIA_CORE_AVAILABLE
        // The core's own exception type carries the contract error code and,
        // for an invalid request, the field that was rejected.
        if let thrown = nsError.userInfo[kotlinExceptionKey] as? PoraviaException {
            return fromContract(
                code: CoreMapping.errorCode(thrown.code),
                field: thrown.field
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
                return .unavailable
            }
        }

        if nsError.domain == NSCocoaErrorDomain, nsError.code == NSFileWriteOutOfSpaceError {
            return .pack(.storageFull, packName: "", message: nil)
        }

        if error is CancellationError { return .cancelled }

        return .unexpected(nsError.localizedDescription)
    }

    /// Maps one of the five contract codes onto a presentable error.
    public static func fromContract(code: ContractErrorCode, field: String?) -> CoreError {
        switch code {
        case .notFound: .notFound
        case .invalidRequest: .invalidRequest(field: field)
        case .unavailable: .unavailable
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
