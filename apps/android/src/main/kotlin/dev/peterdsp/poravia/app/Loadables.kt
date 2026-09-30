package dev.peterdsp.poravia.app

import dev.peterdsp.poravia.core.PoraviaException
import dev.peterdsp.poravia.core.PoraviaFailureKind
import dev.peterdsp.poravia.features.FailureReason
import dev.peterdsp.poravia.features.Loadable
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
 * * `PoraviaFailureKind` is richer than the message-matching the feature layer
 *   falls back on, and it is a public part of the core.
 *
 * Cancellation is never a failure. A person who left a screen has not hit an
 * error and must not be shown one.
 */
suspend fun <T> loadable(block: suspend () -> T): Loadable<T> = try {
    Loadable.Ready(block())
} catch (cancellation: CancellationException) {
    throw cancellation
} catch (error: PoraviaException) {
    Loadable.Failed(error.kind.toFailureReason(), error.code, error.message)
} catch (error: Throwable) {
    Loadable.Failed(FailureReason.UNKNOWN, message = error.message)
}

fun PoraviaFailureKind.toFailureReason(): FailureReason = when (this) {
    PoraviaFailureKind.NO_DATA_INSTALLED -> FailureReason.NO_DATA_INSTALLED
    PoraviaFailureKind.INCOMPLETE_DATA -> FailureReason.NO_DATA_INSTALLED
    PoraviaFailureKind.UNREADABLE_DATA -> FailureReason.DATA_UNREADABLE
    PoraviaFailureKind.RELEASE_MISMATCH -> FailureReason.RELEASE_MISMATCH
    PoraviaFailureKind.NO_OFFLINE_PACK_FOR_DATE -> FailureReason.NO_OFFLINE_DATA_FOR_DATE
    PoraviaFailureKind.NETWORK_UNAVAILABLE -> FailureReason.OFFLINE
    PoraviaFailureKind.STORAGE_FULL -> FailureReason.STORAGE_FULL
    PoraviaFailureKind.NOT_FOUND -> FailureReason.NOT_FOUND
    PoraviaFailureKind.INVALID_REQUEST -> FailureReason.INVALID_REQUEST
    PoraviaFailureKind.GENERAL -> FailureReason.UNKNOWN
}

