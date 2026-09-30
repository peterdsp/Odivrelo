package dev.peterdsp.odivrelo.app

import dev.peterdsp.odivrelo.core.OdivreloException
import dev.peterdsp.odivrelo.core.OdivreloFailureKind
import dev.peterdsp.odivrelo.features.FailureReason
import dev.peterdsp.odivrelo.features.Loadable
import kotlin.coroutines.cancellation.CancellationException

/**
 * Runs a core call and turns whatever it threw into a state a screen can draw.
 *
 * This exists alongside the feature layer's own mapper rather than replacing
 * it. Two of the application's answers depend on a field the feature layer's
 * `Loadable.Empty` cannot carry:
 *
 * * `JourneyResults.dateDataState` distinguishes "this device holds no
 *   timetable for that date" from "no service runs that date". They are
 *   different answers and the product exists not to conflate them, so the
 *   journey search and the stop page keep the whole `JourneyResults` and
 *   `StopDetail` and decide from those fields.
 * * `OdivreloFailureKind` is richer than the message-matching the feature layer
 *   falls back on, and it is a public part of the core.
 *
 * Cancellation is never a failure. A person who left a screen has not hit an
 * error and must not be shown one.
 */
suspend fun <T> loadable(block: suspend () -> T): Loadable<T> = try {
    Loadable.Ready(block())
} catch (cancellation: CancellationException) {
    throw cancellation
} catch (error: OdivreloException) {
    Loadable.Failed(error.kind.toFailureReason(), error.code, error.message)
} catch (error: Throwable) {
    Loadable.Failed(FailureReason.UNKNOWN, message = error.message)
}

fun OdivreloFailureKind.toFailureReason(): FailureReason = when (this) {
    OdivreloFailureKind.NO_DATA_INSTALLED -> FailureReason.NO_DATA_INSTALLED
    OdivreloFailureKind.INCOMPLETE_DATA -> FailureReason.NO_DATA_INSTALLED
    OdivreloFailureKind.UNREADABLE_DATA -> FailureReason.DATA_UNREADABLE
    OdivreloFailureKind.RELEASE_MISMATCH -> FailureReason.RELEASE_MISMATCH
    OdivreloFailureKind.NO_OFFLINE_PACK_FOR_DATE -> FailureReason.NO_OFFLINE_DATA_FOR_DATE
    OdivreloFailureKind.NETWORK_UNAVAILABLE -> FailureReason.OFFLINE
    OdivreloFailureKind.STORAGE_FULL -> FailureReason.STORAGE_FULL
    OdivreloFailureKind.NOT_FOUND -> FailureReason.NOT_FOUND
    OdivreloFailureKind.INVALID_REQUEST -> FailureReason.INVALID_REQUEST
    OdivreloFailureKind.GENERAL -> FailureReason.UNKNOWN
}

