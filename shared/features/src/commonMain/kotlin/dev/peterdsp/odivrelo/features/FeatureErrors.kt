package dev.peterdsp.odivrelo.features

import dev.peterdsp.odivrelo.core.OdivreloException
import dev.peterdsp.odivrelo.core.OdivreloFailureKind
import dev.peterdsp.odivrelo.core.model.ErrorCode
import kotlin.coroutines.cancellation.CancellationException

/**
 * Turns whatever the core threw into a state a screen can render.
 *
 * Cancellation is never a failure: someone who left a screen has not hit an
 * error, and showing them one would be a lie.
 */
internal inline fun <T> runFeature(block: () -> T): Loadable<T> = try {
    Loadable.Ready(block())
} catch (cancellation: CancellationException) {
    throw cancellation
} catch (error: OdivreloException) {
    error.toFailed()
} catch (error: Throwable) {
    Loadable.Failed(FailureReason.UNKNOWN, message = error.message)
}

/**
 * Maps the core's own failure kind, not its wording.
 *
 * Contract 1's [ErrorCode] is coarse on purpose: `unavailable` covers several
 * genuinely different situations whose remedies differ. The core carries the
 * distinction in [OdivreloException.kind], so this mapping reads a value rather
 * than matching on a message, which would break the moment the message is
 * translated or reworded.
 */
internal fun OdivreloException.toFailed(): Loadable.Failed = Loadable.Failed(
    reason = when (kind) {
        OdivreloFailureKind.NO_DATA_INSTALLED -> FailureReason.NO_DATA_INSTALLED
        OdivreloFailureKind.INCOMPLETE_DATA -> FailureReason.NO_DATA_INSTALLED
        OdivreloFailureKind.UNREADABLE_DATA -> FailureReason.DATA_UNREADABLE
        OdivreloFailureKind.RELEASE_MISMATCH -> FailureReason.RELEASE_MISMATCH
        OdivreloFailureKind.NO_OFFLINE_PACK_FOR_DATE -> FailureReason.NO_OFFLINE_DATA_FOR_DATE
        OdivreloFailureKind.NETWORK_UNAVAILABLE -> FailureReason.OFFLINE
        OdivreloFailureKind.STORAGE_FULL -> FailureReason.STORAGE_FULL
        OdivreloFailureKind.NOT_FOUND -> FailureReason.NOT_FOUND
        OdivreloFailureKind.INVALID_REQUEST -> FailureReason.INVALID_REQUEST
        OdivreloFailureKind.GENERAL -> when (code) {
            ErrorCode.NOT_FOUND -> FailureReason.NOT_FOUND
            ErrorCode.INVALID_REQUEST -> FailureReason.INVALID_REQUEST
            ErrorCode.RELEASE_MISMATCH -> FailureReason.RELEASE_MISMATCH
            ErrorCode.UNAUTHORIZED -> FailureReason.SERVER_ERROR
            ErrorCode.UNAVAILABLE -> FailureReason.OFFLINE
        }
    },
    code = code,
    message = message,
)
