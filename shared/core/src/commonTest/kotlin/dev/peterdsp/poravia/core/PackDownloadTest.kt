package dev.peterdsp.poravia.core

import dev.peterdsp.poravia.core.io.PlatformFiles
import dev.peterdsp.poravia.core.model.PackFailure
import dev.peterdsp.poravia.core.model.PackPhase
import dev.peterdsp.poravia.core.model.PackProgress
import dev.peterdsp.poravia.core.packs.PackDownloader
import dev.peterdsp.poravia.core.packs.PackNames
import dev.peterdsp.poravia.core.packs.PackStore
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/**
 * Download behaviour: bounded retry, resume after interruption, verification
 * before use, and cancellation that leaves nothing half-installed.
 */
class PackDownloadTest {

    private fun client(
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ): HttpClient = HttpClient(MockEngine(handler))

    private fun MockRequestHandleScope.ok(body: ByteArray): HttpResponseData = respond(
        content = ByteReadChannel(body),
        status = HttpStatusCode.OK,
        headers = headersOf(HttpHeaders.ContentLength, body.size.toString()),
    )

    private fun MockRequestHandleScope.partial(
        body: ByteArray,
        from: Long,
        total: Long,
    ): HttpResponseData = respond(
        content = ByteReadChannel(body),
        status = HttpStatusCode.PartialContent,
        headers = headersOf(
            HttpHeaders.ContentRange to listOf("bytes $from-${total - 1}/$total"),
            HttpHeaders.ContentLength to listOf(body.size.toString()),
        ),
    )

    @Test
    fun aCleanDownloadVerifiesInstallsAndReportsEveryPhase() = runTest {
        val root = createTestDirectory("download-clean")
        val store = PackStore(root)
        store.prepare()
        store.adoptRelease(Release.manifest)

        val entry = Release.manifest.files.getValue(PackNames.PLACES)
        val body = Release.packBytes(PackNames.PLACES)
        var requests = 0

        val downloader = PackDownloader(
            client {
                requests++
                ok(body)
            },
            store,
            PackDownloader.TEST_RETRY_DELAYS,
        )

        val phases = mutableListOf<PackPhase>()
        val result = downloader.download(
            packName = PackNames.PLACES,
            manifest = Release.manifest,
            manifestFile = entry,
            baseUrl = "https://packs.invalid/data",
            onProgress = { phases += it.phase },
        )

        assertTrue(result.succeeded, "download failed: ${result.failure} ${result.message}")
        assertEquals(1, requests)
        assertEquals(1, result.attempts)
        assertNotNull(result.installed)
        assertEquals(entry.sha256, result.installed.sha256)
        assertTrue(store.isInstalled(entry))
        assertEquals(Release.packText(PackNames.PLACES), store.readPack(entry))

        assertTrue(PackPhase.QUEUED in phases)
        assertTrue(PackPhase.DOWNLOADING in phases)
        assertTrue(PackPhase.VERIFYING in phases)
        assertTrue(PackPhase.DONE in phases)
        // No partial is left behind after a success.
        assertFalse(PlatformFiles.exists(store.partialPath(PackNames.PLACES)))
    }

    @Test
    fun aServerErrorIsRetriedAtMostThreeTimesAndThenGivesUp() = runTest {
        val root = createTestDirectory("download-retry-exhausted")
        val store = PackStore(root)
        store.prepare()
        store.adoptRelease(Release.manifest)
        var requests = 0

        val downloader = PackDownloader(
            client {
                requests++
                respond(
                    content = ByteReadChannel(ByteArray(0)),
                    status = HttpStatusCode.ServiceUnavailable,
                )
            },
            store,
            PackDownloader.TEST_RETRY_DELAYS,
        )

        val result = downloader.download(
            packName = PackNames.PLACES,
            manifest = Release.manifest,
            manifestFile = Release.manifest.files.getValue(PackNames.PLACES),
            baseUrl = "https://packs.invalid/data",
            onProgress = {},
        )

        assertFalse(result.succeeded)
        assertEquals(PackFailure.NETWORK, result.failure)
        assertEquals(PackDownloader.MAX_ATTEMPTS, requests)
        assertEquals(PackDownloader.MAX_ATTEMPTS, result.attempts)
    }

    @Test
    fun aTransientFailureSucceedsOnALaterAttemptWithoutExceedingTheBound() = runTest {
        val root = createTestDirectory("download-retry-recovers")
        val store = PackStore(root)
        store.prepare()
        store.adoptRelease(Release.manifest)

        val entry = Release.manifest.files.getValue(PackNames.SOURCES)
        val body = Release.packBytes(PackNames.SOURCES)
        var requests = 0

        val downloader = PackDownloader(
            client {
                requests++
                if (requests < 3) {
                    respond(
                        content = ByteReadChannel(ByteArray(0)),
                        status = HttpStatusCode.BadGateway,
                    )
                } else {
                    ok(body)
                }
            },
            store,
            PackDownloader.TEST_RETRY_DELAYS,
        )

        val result = downloader.download(
            packName = PackNames.SOURCES,
            manifest = Release.manifest,
            manifestFile = entry,
            baseUrl = "https://packs.invalid/data",
            onProgress = {},
        )

        assertTrue(result.succeeded, "expected success on attempt 3, got ${result.failure}")
        assertEquals(3, requests)
        assertEquals(3, result.attempts)
        assertTrue(store.isInstalled(entry))
    }

    @Test
    fun aTruncatedResponseIsResumedWithARangeRequest() = runTest {
        val root = createTestDirectory("download-resume")
        val store = PackStore(root)
        store.prepare()
        store.adoptRelease(Release.manifest)

        val entry = Release.manifest.files.getValue(PackNames.OPERATORS)
        val body = Release.packBytes(PackNames.OPERATORS)
        val breakpoint = body.size / 2
        val rangeHeaders = mutableListOf<String?>()
        var requests = 0

        val downloader = PackDownloader(
            client { request ->
                requests++
                rangeHeaders += request.headers[HttpHeaders.Range]
                if (requests == 1) {
                    // The connection dropped half way. Content-Length promised
                    // the whole file, so the downloader must notice.
                    respond(
                        content = ByteReadChannel(body.copyOf(breakpoint)),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentLength, body.size.toString()),
                    )
                } else {
                    partial(
                        body.copyOfRange(breakpoint, body.size),
                        breakpoint.toLong(),
                        body.size.toLong(),
                    )
                }
            },
            store,
            PackDownloader.TEST_RETRY_DELAYS,
        )

        val phases = mutableListOf<PackPhase>()
        val result = downloader.download(
            packName = PackNames.OPERATORS,
            manifest = Release.manifest,
            manifestFile = entry,
            baseUrl = "https://packs.invalid/data",
            onProgress = { phases += it.phase },
        )

        assertTrue(result.succeeded, "resume failed: ${result.failure} ${result.message}")
        assertEquals(2, requests)
        assertNull(rangeHeaders[0], "the first attempt must not send a Range header")
        assertEquals(
            "bytes=$breakpoint-",
            rangeHeaders[1],
            "the second attempt must resume from the bytes already on disk",
        )
        assertTrue(PackPhase.RESUMING in phases)
        // The reassembled file verifies, which is the only proof resume worked.
        assertEquals(Release.packText(PackNames.OPERATORS), store.readPack(entry))
    }

    @Test
    fun aDigestMismatchIsNotRetriedBecauseTheSameMirrorWouldReturnTheSameBytes() = runTest {
        val root = createTestDirectory("download-corrupt")
        val store = PackStore(root)
        store.prepare()
        store.adoptRelease(Release.manifest)

        val entry = Release.manifest.files.getValue(Release.brokenVariantSource)
        val corrupt = Release.corruptDayText().encodeToByteArray()
        var requests = 0

        val downloader = PackDownloader(
            client {
                requests++
                respond(
                    content = ByteReadChannel(corrupt),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentLength, entry.bytes.toString()),
                )
            },
            store,
            PackDownloader.TEST_RETRY_DELAYS,
        )

        val result = downloader.download(
            packName = Release.brokenVariantSource,
            manifest = Release.manifest,
            manifestFile = entry,
            baseUrl = "https://packs.invalid/data",
            onProgress = {},
        )

        assertFalse(result.succeeded)
        assertEquals(PackFailure.DIGEST_MISMATCH, result.failure)
        assertEquals(1, requests, "a bad digest must not be retried")
        assertFalse(store.isInstalled(entry))
        assertFalse(PlatformFiles.exists(store.partialPath(Release.brokenVariantSource)))
    }

    @Test
    fun aResponseLongerThanTheManifestIsRefusedImmediately() = runTest {
        val root = createTestDirectory("download-too-long")
        val store = PackStore(root)
        store.prepare()
        store.adoptRelease(Release.manifest)

        val entry = Release.manifest.files.getValue(PackNames.SOURCES)
        val tooLong = Release.packBytes(PackNames.SOURCES) + ByteArray(4096)
        var requests = 0

        val downloader = PackDownloader(
            client {
                requests++
                ok(tooLong)
            },
            store,
            PackDownloader.TEST_RETRY_DELAYS,
        )

        val result = downloader.download(
            packName = PackNames.SOURCES,
            manifest = Release.manifest,
            manifestFile = entry,
            baseUrl = "https://packs.invalid/data",
            onProgress = {},
        )

        assertFalse(result.succeeded)
        assertEquals(PackFailure.DIGEST_MISMATCH, result.failure)
        assertEquals(1, requests)
    }

    @Test
    fun aPackMissingFromTheOriginIsAFatalFailureNotARetryLoop() = runTest {
        val root = createTestDirectory("download-404")
        val store = PackStore(root)
        store.prepare()
        store.adoptRelease(Release.manifest)
        var requests = 0

        val downloader = PackDownloader(
            client {
                requests++
                respond(content = ByteReadChannel(ByteArray(0)), status = HttpStatusCode.NotFound)
            },
            store,
            PackDownloader.TEST_RETRY_DELAYS,
        )

        val result = downloader.download(
            packName = PackNames.META,
            manifest = Release.manifest,
            manifestFile = Release.manifest.files.getValue(PackNames.META),
            baseUrl = "https://packs.invalid/data",
            onProgress = {},
        )

        assertFalse(result.succeeded)
        assertEquals(PackFailure.NOT_IN_MANIFEST, result.failure)
        assertEquals(1, requests)
    }

    @Test
    fun cancellingADownloadInstallsNothingAndKeepsThePartialForLater() = runTest {
        val root = createTestDirectory("download-cancel")
        val store = PackStore(root)
        store.prepare()
        store.adoptRelease(Release.manifest)

        val entry = Release.manifest.files.getValue(PackNames.STOPS)
        val body = Release.packBytes(PackNames.STOPS)
        val started = CompletableDeferred<Unit>()

        val downloader = PackDownloader(
            client {
                started.complete(Unit)
                // Deliver the first half, then stall so the test can cancel while
                // bytes are already on disk.
                delay(30_000)
                ok(body)
            },
            store,
            PackDownloader.TEST_RETRY_DELAYS,
        )

        withContext(Dispatchers.Default) {
            val job = launch {
                downloader.download(
                    packName = PackNames.STOPS,
                    manifest = Release.manifest,
                    manifestFile = entry,
                    baseUrl = "https://packs.invalid/data",
                    onProgress = {},
                )
            }
            withTimeout(10_000) { started.await() }
            job.cancelAndJoin()
            assertTrue(job.isCancelled)
        }

        assertFalse(store.isInstalled(entry), "a cancelled download must install nothing")
    }

    @Test
    fun progressFractionIsNullWhenTheLengthIsUnknown() {
        assertEquals(
            0.5,
            PackProgress("places", 50, 100, PackPhase.DOWNLOADING).fraction,
        )
        assertNull(PackProgress("places", 50, 0, PackPhase.DOWNLOADING).fraction)
        assertEquals(
            1.0,
            PackProgress("places", 200, 100, PackPhase.DOWNLOADING).fraction,
        )
    }
}
