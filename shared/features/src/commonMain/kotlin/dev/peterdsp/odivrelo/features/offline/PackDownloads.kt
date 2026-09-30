package dev.peterdsp.odivrelo.features.offline

import dev.peterdsp.odivrelo.core.Cancellable
import dev.peterdsp.odivrelo.core.model.PackFailure
import dev.peterdsp.odivrelo.core.model.PackPhase
import dev.peterdsp.odivrelo.core.model.PackProgress
import dev.peterdsp.odivrelo.core.model.PackResult
import dev.peterdsp.odivrelo.features.FailureReason
import kotlin.native.ObjCName

/**
 * What one pack download looks like on screen.
 *
 * The core deals in callbacks so Swift needs no coroutine bridge. This turns
 * those callbacks into a value a list row can render, and keeps the rule that a
 * cancelled or failed download is reported honestly rather than disappearing.
 */
@ObjCName("OdivreloDownloadState")
data class DownloadState(
    val packName: String,
    val phase: PackPhase,
    val bytesDownloaded: Long,
    val totalBytes: Long,
    val attempt: Int,
    val failure: PackFailure? = null,
    val finished: Boolean = false,
    val succeeded: Boolean = false,
) {
    /** 0.0 to 1.0, or null when the origin declared no length. */
    val fraction: Double?
        get() = if (totalBytes > 0) {
            (bytesDownloaded.toDouble() / totalBytes.toDouble()).coerceIn(0.0, 1.0)
        } else {
            null
        }

    val percent: Int? get() = fraction?.let { (it * 100).toInt() }

    val isRunning: Boolean get() = !finished

    /** True while bytes are being reused from an interrupted attempt. */
    val isResuming: Boolean get() = phase == PackPhase.RESUMING

    val isRetrying: Boolean get() = attempt > 1 && !finished

    /**
     * A digest or release failure is not worth retrying against the same origin:
     * the same bytes would come back. Everything else is.
     */
    val isRetryable: Boolean
        get() = when (failure) {
            null -> false
            PackFailure.DIGEST_MISMATCH, PackFailure.RELEASE_MISMATCH,
            PackFailure.NOT_IN_MANIFEST,
            -> false

            PackFailure.NETWORK, PackFailure.TIMEOUT, PackFailure.STORAGE_FULL,
            PackFailure.CANCELLED, PackFailure.IO,
            -> true
        }

    val failureReason: FailureReason?
        get() = when (failure) {
            null -> null
            PackFailure.NETWORK, PackFailure.TIMEOUT -> FailureReason.OFFLINE
            PackFailure.DIGEST_MISMATCH -> FailureReason.DATA_UNREADABLE
            PackFailure.RELEASE_MISMATCH -> FailureReason.RELEASE_MISMATCH
            PackFailure.STORAGE_FULL -> FailureReason.STORAGE_FULL
            PackFailure.NOT_IN_MANIFEST -> FailureReason.NOT_FOUND
            PackFailure.CANCELLED -> null
            PackFailure.IO -> FailureReason.UNKNOWN
        }

    val wasCancelled: Boolean get() = failure == PackFailure.CANCELLED

    companion object {
        fun queued(packName: String, totalBytes: Long): DownloadState = DownloadState(
            packName = packName,
            phase = PackPhase.QUEUED,
            bytesDownloaded = 0,
            totalBytes = totalBytes,
            attempt = 1,
        )

        fun from(progress: PackProgress): DownloadState = DownloadState(
            packName = progress.packName,
            phase = progress.phase,
            bytesDownloaded = progress.bytesDownloaded,
            totalBytes = progress.totalBytes,
            attempt = progress.attempt,
        )

        fun from(result: PackResult, totalBytes: Long): DownloadState = DownloadState(
            packName = result.packName,
            phase = if (result.succeeded) PackPhase.DONE else PackPhase.QUEUED,
            bytesDownloaded = if (result.succeeded) totalBytes else 0,
            totalBytes = totalBytes,
            attempt = result.attempts.coerceAtLeast(1),
            failure = result.failure,
            finished = true,
            succeeded = result.succeeded,
        )
    }
}

/**
 * Tracks the downloads that are in flight.
 *
 * It exists so a geometry change cannot restart a download. The handles live
 * here, outside any screen, so rebuilding the screen finds the same running
 * download rather than starting a second one.
 *
 * It is not thread safe by itself; the platform owns it from a single place, the
 * way a view model or an observable object does.
 */
@ObjCName("OdivreloDownloadTracker")
class DownloadTracker {

    private val running = mutableMapOf<String, Cancellable>()
    private val states = mutableMapOf<String, DownloadState>()

    fun snapshot(): Map<String, DownloadState> = states.toMap()

    fun stateOf(packName: String): DownloadState? = states[packName]

    fun isRunning(packName: String): Boolean =
        running[packName]?.isCancelled == false

    /**
     * Starts a download unless one for the same pack is already running.
     *
     * Returns true when a download was actually started, so a caller can tell a
     * fresh tap from a rebuild after a rotation.
     */
    fun start(
        packName: String,
        totalBytes: Long,
        onChanged: (DownloadState) -> Unit,
        launch: (
            onProgress: (PackProgress) -> Unit,
            onResult: (PackResult) -> Unit,
        ) -> Cancellable,
    ): Boolean {
        if (isRunning(packName)) return false

        states[packName] = DownloadState.queued(packName, totalBytes)
        onChanged(states.getValue(packName))

        val handle = launch(
            { progress ->
                val next = DownloadState.from(progress)
                    .copy(totalBytes = if (progress.totalBytes > 0) progress.totalBytes else totalBytes)
                states[packName] = next
                onChanged(next)
            },
            { result ->
                val next = DownloadState.from(result, totalBytes)
                states[packName] = next
                running.remove(packName)
                onChanged(next)
            },
        )
        running[packName] = handle
        return true
    }

    /**
     * Cancels a running download. Cancelling is idempotent and always safe: the
     * core installs nothing on cancellation and keeps whatever arrived for a
     * later resume.
     */
    fun cancel(packName: String, onChanged: (DownloadState) -> Unit = {}) {
        val handle = running.remove(packName) ?: return
        handle.cancel()
        val previous = states[packName]
        val next = (previous ?: DownloadState.queued(packName, 0)).copy(
            failure = PackFailure.CANCELLED,
            finished = true,
            succeeded = false,
        )
        states[packName] = next
        onChanged(next)
    }

    fun cancelAll() {
        running.keys.toList().forEach { cancel(it) }
    }

    /** Forgets a finished download so its row goes back to its resting state. */
    fun clear(packName: String) {
        if (isRunning(packName)) return
        states.remove(packName)
    }

    fun clearFinished() {
        states.filterValues { it.finished }.keys.forEach { states.remove(it) }
    }
}
