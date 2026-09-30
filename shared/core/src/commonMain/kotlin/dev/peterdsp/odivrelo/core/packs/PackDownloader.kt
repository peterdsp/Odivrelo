package dev.peterdsp.odivrelo.core.packs

import dev.peterdsp.odivrelo.core.io.Paths
import dev.peterdsp.odivrelo.core.io.PlatformFiles
import dev.peterdsp.odivrelo.core.model.ManifestFile
import dev.peterdsp.odivrelo.core.model.OfflineManifest
import dev.peterdsp.odivrelo.core.model.PackFailure
import dev.peterdsp.odivrelo.core.model.PackPhase
import dev.peterdsp.odivrelo.core.model.PackProgress
import dev.peterdsp.odivrelo.core.model.PackResult
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.request.header
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentLength
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readAvailable
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.currentCoroutineContext

/**
 * Downloads one pack with bounded retry, resume and integrity verification.
 *
 * The rules this class exists to keep:
 *
 * - At most [MAX_ATTEMPTS] attempts, with a bounded backoff, so a phone on a
 *   dead network stops instead of retrying for ever.
 * - A partial file is resumed with a `Range` request when the server allows it
 *   and the resume record proves the bytes belong to the same digest.
 * - Nothing is installed until SHA-256 matches the manifest, and a corrupted
 *   download deletes the partial rather than keeping it to "fix later".
 * - A pack whose own envelope carries a different release id is refused, so a
 *   stale mirror cannot mix releases.
 * - Cancellation is honoured between chunks and leaves the temporary file for a
 *   later resume, never a half-written installed pack.
 */
internal class PackDownloader(
    private val httpClient: HttpClient,
    private val store: PackStore,
    private val retryDelays: List<Duration> = DEFAULT_RETRY_DELAYS,
) {

    suspend fun download(
        packName: String,
        manifest: OfflineManifest,
        manifestFile: ManifestFile,
        baseUrl: String,
        onProgress: (PackProgress) -> Unit,
    ): PackResult {
        if (!Paths.isSafeFileName(packName)) {
            return failure(packName, PackFailure.NOT_IN_MANIFEST, "unsafe pack name", 0)
        }
        store.prepare()

        val required = if (manifestFile.bytes > 0) manifestFile.bytes else MINIMUM_FREE_BYTES
        val usable = store.usableSpaceBytes()
        if (usable in 0 until (required + MINIMUM_FREE_BYTES)) {
            return failure(packName, PackFailure.STORAGE_FULL, "not enough free space", 0)
        }

        onProgress(
            PackProgress(packName, store.partialBytes(packName), manifestFile.bytes, PackPhase.QUEUED),
        )

        var lastFailure = PackFailure.NETWORK
        var lastMessage: String? = null

        for (attempt in 1..MAX_ATTEMPTS) {
            currentCoroutineContext().ensureActive()
            val outcome = try {
                attemptDownload(packName, manifest, manifestFile, baseUrl, attempt, onProgress)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (timeout: HttpRequestTimeoutException) {
                Attempt.Retryable(PackFailure.TIMEOUT, timeout.message)
            } catch (error: Throwable) {
                Attempt.Retryable(PackFailure.NETWORK, error.message)
            }

            when (outcome) {
                is Attempt.Installed -> {
                    store.discardPartial(packName)
                    onProgress(
                        PackProgress(
                            packName,
                            manifestFile.bytes,
                            manifestFile.bytes,
                            PackPhase.DONE,
                            attempt,
                        ),
                    )
                    return PackResult(
                        packName = packName,
                        succeeded = true,
                        installed = outcome.installed,
                        attempts = attempt,
                    )
                }

                is Attempt.Fatal -> {
                    return failure(packName, outcome.failure, outcome.message, attempt)
                }

                is Attempt.Retryable -> {
                    lastFailure = outcome.failure
                    lastMessage = outcome.message
                    if (attempt < MAX_ATTEMPTS) {
                        delay(retryDelays[(attempt - 1).coerceAtMost(retryDelays.lastIndex)])
                    }
                }
            }
        }

        return failure(packName, lastFailure, lastMessage, MAX_ATTEMPTS)
    }

    private suspend fun attemptDownload(
        packName: String,
        manifest: OfflineManifest,
        manifestFile: ManifestFile,
        baseUrl: String,
        attempt: Int,
        onProgress: (PackProgress) -> Unit,
    ): Attempt {
        val alreadyHave = store.reusablePartialBytes(packName, manifestFile)
        val partialPath = store.partialPath(packName)
        store.writeResumeRecord(packName, manifestFile, manifest.releaseId)

        val url = joinUrl(baseUrl, manifestFile.path)
        var written = alreadyHave

        val statement = httpClient.prepareGet(url) {
            if (alreadyHave > 0) {
                header("Range", "bytes=$alreadyHave-")
            }
        }

        val fatal = statement.execute { response: HttpResponse ->
            when (response.status) {
                HttpStatusCode.OK -> {
                    if (alreadyHave > 0) {
                        // Server ignored the range; start again from zero.
                        store.discardPartial(packName)
                        store.writeResumeRecord(packName, manifestFile, manifest.releaseId)
                        written = 0
                    }
                }

                HttpStatusCode.PartialContent -> Unit

                HttpStatusCode.NotFound, HttpStatusCode.Gone ->
                    return@execute Attempt.Fatal(PackFailure.NOT_IN_MANIFEST, "pack not served at $url")

                HttpStatusCode.RequestedRangeNotSatisfiable -> {
                    store.discardPartial(packName)
                    return@execute Attempt.Retryable(PackFailure.NETWORK, "range rejected")
                }

                else -> return@execute Attempt.Retryable(
                    PackFailure.NETWORK,
                    "http ${response.status.value}",
                )
            }

            val declaredTotal = when {
                manifestFile.bytes > 0 -> manifestFile.bytes
                else -> (response.contentLength() ?: 0L) + written
            }

            onProgress(
                PackProgress(
                    packName,
                    written,
                    declaredTotal,
                    if (written > 0) PackPhase.RESUMING else PackPhase.DOWNLOADING,
                    attempt,
                ),
            )

            val channel: ByteReadChannel = response.bodyAsChannel()
            val buffer = ByteArray(CHUNK_BYTES)
            var sinceLastReport = 0L

            while (true) {
                currentCoroutineContext().ensureActive()
                val read = channel.readAvailable(buffer, 0, buffer.size)
                if (read == -1) break
                if (read == 0) continue

                if (manifestFile.bytes > 0 && written + read > manifestFile.bytes) {
                    // The server is sending more than the manifest promised.
                    store.discardPartial(packName)
                    return@execute Attempt.Fatal(
                        PackFailure.DIGEST_MISMATCH,
                        "response longer than the manifest length",
                    )
                }

                PlatformFiles.appendBytes(
                    partialPath,
                    if (read == buffer.size) buffer else buffer.copyOf(read),
                )
                written += read
                sinceLastReport += read

                if (sinceLastReport >= PROGRESS_STEP_BYTES) {
                    sinceLastReport = 0
                    onProgress(
                        PackProgress(packName, written, declaredTotal, PackPhase.DOWNLOADING, attempt),
                    )
                }
            }

            if (manifestFile.bytes > 0 && written != manifestFile.bytes) {
                return@execute Attempt.Retryable(
                    PackFailure.NETWORK,
                    "truncated at $written of ${manifestFile.bytes} bytes",
                )
            }
            null
        }

        if (fatal != null) return fatal

        onProgress(
            PackProgress(packName, written, manifestFile.bytes, PackPhase.VERIFYING, attempt),
        )

        val verificationFailure = store.verifyAndInstall(
            temporaryPath = partialPath,
            manifestFile = manifestFile,
            expectedReleaseId = manifest.releaseId,
        )
        if (verificationFailure != null) {
            store.discardPartial(packName)
            return when (verificationFailure) {
                // A digest mismatch is not a transport problem: retrying the
                // same mirror will produce the same bad bytes.
                PackFailure.DIGEST_MISMATCH,
                PackFailure.RELEASE_MISMATCH,
                -> Attempt.Fatal(verificationFailure, "verification failed")

                else -> Attempt.Retryable(verificationFailure, "install failed")
            }
        }

        onProgress(
            PackProgress(packName, written, manifestFile.bytes, PackPhase.INSTALLING, attempt),
        )

        return Attempt.Installed(
            dev.peterdsp.odivrelo.core.model.InstalledPack(
                name = packName,
                releaseId = manifest.releaseId,
                sha256 = manifestFile.sha256,
                bytes = written,
                installedAt = "",
                publishedAt = manifest.publishedAt,
            ),
        )
    }

    private fun failure(
        packName: String,
        failure: PackFailure,
        message: String?,
        attempts: Int,
    ): PackResult = PackResult(
        packName = packName,
        succeeded = false,
        failure = failure,
        message = message,
        attempts = attempts,
    )

    private sealed interface Attempt {
        data class Installed(val installed: dev.peterdsp.odivrelo.core.model.InstalledPack) : Attempt
        data class Retryable(val failure: PackFailure, val message: String?) : Attempt
        data class Fatal(val failure: PackFailure, val message: String?) : Attempt
    }

    companion object {
        const val MAX_ATTEMPTS: Int = 3
        private const val CHUNK_BYTES = 64 * 1024
        private const val PROGRESS_STEP_BYTES = 128L * 1024L
        private const val MINIMUM_FREE_BYTES = 8L * 1024L * 1024L

        val DEFAULT_RETRY_DELAYS: List<Duration> = listOf(1.seconds, 3.seconds)

        /** Used by tests so retry bounds are exercised without real waiting. */
        val TEST_RETRY_DELAYS: List<Duration> = listOf(1.milliseconds, 1.milliseconds)

        fun joinUrl(baseUrl: String, path: String): String =
            baseUrl.trimEnd('/') + "/" + path.trimStart('/')
    }
}
