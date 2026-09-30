package dev.peterdsp.odivrelo.core

import dev.peterdsp.odivrelo.core.crypto.Sha256
import dev.peterdsp.odivrelo.core.domain.ReleaseLoadFailure
import dev.peterdsp.odivrelo.core.domain.ReleaseLoadResult
import dev.peterdsp.odivrelo.core.domain.ReleaseLoader
import dev.peterdsp.odivrelo.core.io.Paths
import dev.peterdsp.odivrelo.core.io.PlatformFiles
import dev.peterdsp.odivrelo.core.model.PackFailure
import dev.peterdsp.odivrelo.core.packs.PackNames
import dev.peterdsp.odivrelo.core.packs.PackStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Integrity of the offline data path: digests, corrupted packs, release mixing,
 * atomic install and one-release rollback.
 */
class PackIntegrityTest {

    @Test
    fun sha256MatchesTheKnownAnswersFromTheStandard() {
        assertEquals(
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            Sha256.hex(""),
        )
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            Sha256.hex("abc"),
        )
        assertEquals(
            "248d6a61d20638b8e5c026930c3e6039a33ce45964ff2167f6ecedd419db06c1",
            Sha256.hex("abcdbcdecdefdefgefghfghighijhijkijkljklmklmnlmnomnopnopq"),
        )
        // The standard's long vector: one million 'a' characters. This is the case
        // that would break if the length padding were wrong.
        assertEquals(
            "cdc76e5c9914fb9281a1c7e284d73e67f1809a48a497200e046d39ccc7112cd0",
            Sha256.hex("a".repeat(1_000_000)),
        )
    }

    @Test
    fun streamingDigestEqualsSingleShotDigest() {
        val body = Release.packBytes(Release.brokenVariantSource)
        val streamed = Sha256()
        var offset = 0
        while (offset < body.size) {
            val take = minOf(777, body.size - offset)
            streamed.update(body, offset, take)
            offset += take
        }
        assertEquals(Sha256().update(body).hexDigest(), streamed.hexDigest())
    }

    @Test
    fun everyPublishedPackMatchesItsDeclaredDigestAndLength() {
        Release.manifest.files.forEach { (name, file) ->
            val body = Release.packBytes(name)
            assertEquals(file.bytes, body.size.toLong(), "pack '$name' has the wrong length")
            assertEquals(file.sha256, Sha256().update(body).hexDigest(), "pack '$name' digest")
        }
    }

    @Test
    fun digestComparisonIsCaseInsensitiveAndRejectsWrongLengths() {
        val digest = Sha256.hex("abc")
        assertTrue(Sha256.digestsMatch(digest, digest.uppercase()))
        assertFalse(Sha256.digestsMatch(digest, digest.dropLast(1)))
        assertFalse(Sha256.digestsMatch("", ""))
        assertFalse(Sha256.digestsMatch(digest, Sha256.hex("abd")))
    }

    @Test
    fun aCorruptedPackIsRefusedAndTheTemporaryFileIsDeleted() {
        val root = createTestDirectory("corrupt-pack")
        val store = PackStore(root)
        store.prepare()
        store.adoptRelease(Release.manifest)

        val packName = Release.brokenVariantSource
        val entry = Release.manifest.files.getValue(packName)
        val temporary = store.partialPath(packName)
        // The corrupted pack is still valid JSON with a plausible duration. Only
        // the digest can catch it, which is exactly the dangerous case.
        val corrupt = Release.corruptDayText().encodeToByteArray()
        PlatformFiles.writeBytes(temporary, corrupt)
        assertNotEquals(entry.sha256, Sha256().update(corrupt).hexDigest())

        val failure = store.verifyAndInstall(temporary, entry, Release.releaseId)
        assertEquals(PackFailure.DIGEST_MISMATCH, failure)
        assertFalse(PlatformFiles.exists(temporary), "a rejected download must not be kept")
        assertFalse(
            PlatformFiles.exists(store.packPath(entry)),
            "a rejected pack must never reach the installed directory",
        )
    }

    @Test
    fun aPackFromAnotherReleaseIsRefusedEvenWhenItsOwnDigestIsValid() {
        val root = createTestDirectory("mixed-release-download")
        val store = PackStore(root)
        store.prepare()
        store.adoptRelease(Release.manifest)

        val packName = Release.brokenVariantSource
        val body = Release.otherReleaseDayText().encodeToByteArray()
        val digest = Sha256().update(body).hexDigest()
        // A manifest entry that matches this file exactly, so the only thing wrong
        // is the release the file says it belongs to.
        val entry = Release.manifest.files.getValue(packName)
            .copy(sha256 = digest, bytes = body.size.toLong())

        val temporary = store.partialPath(packName)
        PlatformFiles.writeBytes(temporary, body)

        val failure = store.verifyAndInstall(temporary, entry, Release.releaseId)
        assertEquals(PackFailure.RELEASE_MISMATCH, failure)
        assertFalse(PlatformFiles.exists(store.packPath(entry)))
    }

    @Test
    fun aVerifiedPackIsInstalledByRenameAndReadsBackByteForByte() {
        val root = createTestDirectory("install-pack")
        val store = PackStore(root)
        store.prepare()
        store.adoptRelease(Release.manifest)

        val entry = Release.manifest.files.getValue(PackNames.PLACES)
        val body = Release.packText(PackNames.PLACES)
        val temporary = store.partialPath(PackNames.PLACES)
        PlatformFiles.writeBytes(temporary, body.encodeToByteArray())

        assertNull(store.verifyAndInstall(temporary, entry, Release.releaseId))
        assertFalse(PlatformFiles.exists(temporary), "the temporary file must be renamed away")
        assertTrue(store.isInstalled(entry))
        assertEquals(body, store.readPack(entry))
    }

    @Test
    fun aZipPackInstallsWithoutBeingTreatedAsJson() {
        val root = createTestDirectory("install-gtfs")
        val store = PackStore(root)
        store.prepare()
        store.adoptRelease(Release.manifest)

        val entry = Release.manifest.files.getValue(PackNames.GTFS)
        val temporary = store.partialPath(PackNames.GTFS)
        PlatformFiles.writeBytes(temporary, Release.packBytes(PackNames.GTFS))

        // The release check sniffs an envelope out of JSON; a zip has none, and
        // that must not be read as "belongs to another release".
        assertNull(store.verifyAndInstall(temporary, entry, Release.releaseId))
        assertTrue(store.isInstalled(entry))
    }

    @Test
    fun loadingRefusesAReleaseWhoseInstalledPackBelongsToAnotherRelease() {
        val root = createTestDirectory("mixed-release-load")
        // Install the release, then swap in a journeys pack whose envelope declares
        // a different release id, as a stale mirror or a hand copy would produce.
        Release.install(
            root,
            overrides = mapOf(Release.brokenVariantSource to Release.otherReleaseDayText()),
        )
        val store = PackStore(root)

        // The static packs still load; the mixed day pack is refused when read.
        assertIs<ReleaseLoadResult.Loaded>(ReleaseLoader(store).load())

        val date = PackNames.serviceDateOf(Release.brokenVariantSource)!!
        val day = ReleaseLoader(store).loadDay(date)
        val failed = assertIs<dev.peterdsp.odivrelo.core.domain.DayLoadResult.Failed>(day)
        val mismatch = assertIs<ReleaseLoadFailure.ReleaseMismatch>(failed.failure)
        assertEquals(Release.brokenVariantSource, mismatch.packName)
        assertEquals("0000000000000000", mismatch.found)
        assertEquals(Release.releaseId, mismatch.expected)
    }

    @Test
    fun loadingReportsMissingPacksRatherThanServingAPartialRelease() {
        val root = createTestDirectory("missing-pack")
        Release.install(root, omit = setOf(PackNames.STOPS))

        val failed = assertIs<ReleaseLoadResult.Failed>(ReleaseLoader(PackStore(root)).load())
        val missing = assertIs<ReleaseLoadFailure.MissingPacks>(failed.failure)
        assertEquals(listOf(PackNames.STOPS), missing.names)
    }

    @Test
    fun loadingAnEmptyDirectoryReportsNoManifestRatherThanCrashing() {
        val root = createTestDirectory("empty-packs")
        val failed = assertIs<ReleaseLoadResult.Failed>(ReleaseLoader(PackStore(root)).load())
        assertIs<ReleaseLoadFailure.NoManifest>(failed.failure)
    }

    @Test
    fun aValidInstalledReleaseLoadsWithOneReleaseId() {
        val root = createTestDirectory("valid-release")
        Release.install(root)
        val loaded = assertIs<ReleaseLoadResult.Loaded>(ReleaseLoader(PackStore(root)).load())
        val index = loaded.index
        assertEquals(Release.releaseId, index.releaseId)
        assertTrue(index.places.isNotEmpty())
        assertTrue(index.stops.isNotEmpty())
        assertTrue(index.operators.isNotEmpty())
        assertTrue(index.sources.isNotEmpty())
    }

    @Test
    fun aDayPackThatCoversADifferentDateThanItsNameIsRefused() {
        val root = createTestDirectory("day-pack-wrong-date")
        Release.install(root)
        val store = PackStore(root)

        // Put one date's payload behind another date's name. The length is padded
        // to the declared one, so the cheap size guard is not what rejects it: the
        // declared service date inside the pack is.
        val targetName = Release.brokenVariantSource
        val targetDate = PackNames.serviceDateOf(targetName)!!
        val entry = Release.manifest.files.getValue(targetName)
        val otherDate = Release.serviceDates
            .filter { it != targetDate }
            .minByOrNull { Release.manifest.files.getValue(PackNames.journeys(it)).bytes }
            ?: error("the release publishes only one service date")

        val otherBytes = Release.packBytes(PackNames.journeys(otherDate))
        assertTrue(
            otherBytes.size <= entry.bytes,
            "this test needs a smaller pack to pad up to the target length",
        )
        val padded = otherBytes + ByteArray((entry.bytes - otherBytes.size).toInt()) { ' '.code.toByte() }
        assertEquals(entry.bytes, padded.size.toLong())
        PlatformFiles.writeBytes(store.packPath(entry), padded)
        assertTrue(store.isInstalled(entry), "the padded file must pass the size guard")

        val failed = assertIs<dev.peterdsp.odivrelo.core.domain.DayLoadResult.Failed>(
            ReleaseLoader(store).loadDay(targetDate),
        )
        val corrupt = assertIs<ReleaseLoadFailure.Corrupt>(failed.failure)
        assertTrue(
            corrupt.reason.contains(otherDate),
            "expected the wrong-date reason, got: " + corrupt.reason,
        )
    }

    @Test
    fun theLoaderListsExactlyTheServiceDatesTheReleasePublishes() {
        val root = createTestDirectory("service-dates")
        Release.install(root)
        assertEquals(Release.serviceDates, ReleaseLoader(PackStore(root)).serviceDates())
    }

    @Test
    fun adoptingADifferentReleaseArchivesTheOldOneWholesale() {
        val root = createTestDirectory("adopt-release")
        Release.install(root)
        val store = PackStore(root)
        assertFalse(store.hasRollbackTarget())

        val next = Release.manifest.copy(
            releaseId = "ffffffffffffffff",
            publishedAt = "2026-10-10T10:00:00Z",
        )
        assertTrue(store.adoptRelease(next), "adopting a new release must archive the installed one")

        assertEquals("ffffffffffffffff", store.installedManifest()?.releaseId)
        assertEquals(Release.releaseId, store.previousManifest()?.releaseId)
        assertTrue(store.hasRollbackTarget())

        // The new release's directory holds no files from the old one, so two
        // releases cannot be read together even by accident.
        val current = Paths.join(root, "current")
        val leftOver = Release.manifest.files.values.count {
            PlatformFiles.exists(Paths.join(current, it.path))
        }
        assertEquals(0, leftOver)
    }

    @Test
    fun adoptingTheSameReleaseAgainDoesNotArchiveAnything() {
        val root = createTestDirectory("adopt-same")
        Release.install(root)
        val store = PackStore(root)
        assertFalse(store.adoptRelease(Release.manifest))
        assertNull(store.previousManifest())
        assertTrue(store.isInstalled(Release.manifest.files.getValue(PackNames.META)))
    }

    @Test
    fun rollbackRestoresExactlyOnePreviousRelease() {
        val root = createTestDirectory("rollback")
        Release.install(root)
        val store = PackStore(root)

        store.adoptRelease(Release.manifest.copy(releaseId = "ffffffffffffffff"))
        assertEquals("ffffffffffffffff", store.installedManifest()?.releaseId)

        val restored = store.rollback()
        assertNotNull(restored)
        assertEquals(Release.releaseId, restored.releaseId)
        assertEquals(Release.releaseId, store.installedManifest()?.releaseId)
        // The release just rolled back from becomes the rollback target, and there
        // is still only one of them.
        assertEquals("ffffffffffffffff", store.previousManifest()?.releaseId)

        // The restored release loads cleanly, which is the only thing that makes a
        // rollback worth having.
        val loaded = assertIs<ReleaseLoadResult.Loaded>(ReleaseLoader(store).load())
        assertEquals(Release.releaseId, loaded.index.releaseId)
    }

    @Test
    fun rollbackWithNoPreviousReleaseFailsInsteadOfEmptyingTheInstallation() {
        val root = createTestDirectory("rollback-none")
        Release.install(root)
        val store = PackStore(root)
        assertNull(store.rollback())
        assertEquals(Release.releaseId, store.installedManifest()?.releaseId)
    }

    @Test
    fun aPartialFileIsOnlyReusedWhenItProvablyBelongsToTheSameDigest() {
        val root = createTestDirectory("resume")
        val store = PackStore(root)
        store.prepare()
        val packName = Release.brokenVariantSource
        val entry = Release.manifest.files.getValue(packName)

        assertEquals(0, store.reusablePartialBytes(packName, entry))

        val head = Release.packBytes(packName).copyOf(512)
        PlatformFiles.writeBytes(store.partialPath(packName), head)
        store.writeResumeRecord(packName, entry, Release.releaseId)
        assertEquals(512, store.reusablePartialBytes(packName, entry))

        // A partial left over from a different digest is discarded, not appended
        // to, because appending would produce a file that can never verify.
        assertEquals(0, store.reusablePartialBytes(packName, entry.copy(sha256 = "0".repeat(64))))
        assertFalse(PlatformFiles.exists(store.partialPath(packName)))
    }

    @Test
    fun anAlreadyCompletePartialIsDiscardedRatherThanResumed() {
        val root = createTestDirectory("resume-complete")
        val store = PackStore(root)
        store.prepare()
        val entry = Release.manifest.files.getValue(PackNames.SOURCES)
        PlatformFiles.writeBytes(
            store.partialPath(PackNames.SOURCES),
            Release.packBytes(PackNames.SOURCES),
        )
        store.writeResumeRecord(PackNames.SOURCES, entry, Release.releaseId)
        assertEquals(0, store.reusablePartialBytes(PackNames.SOURCES, entry))
    }

    @Test
    fun packNamesFromAManifestCannotEscapeTheDirectory() {
        assertFalse(Paths.isSafeFileName(".."))
        assertFalse(Paths.isSafeFileName("../../etc/passwd"))
        assertFalse(Paths.isSafeFileName("packs/journeys"))
        assertFalse(Paths.isSafeFileName(""))
        assertFalse(Paths.isSafeFileName("a b"))
        assertTrue(Paths.isSafeFileName("journeys-2026-10-02"))
        assertTrue(Paths.isSafeFileName("meta"))
    }

    @Test
    fun installedBytesCountsOnlyWhatIsActuallyOnDisk() {
        val root = createTestDirectory("installed-bytes")
        Release.install(root)
        val store = PackStore(root)
        val total = Release.manifest.files.values.sumOf { it.bytes }
        assertEquals(total, store.installedBytes())

        val removed = Release.manifest.files.getValue(PackNames.SOURCES)
        store.removePack(removed)
        assertEquals(total - removed.bytes, store.installedBytes())
    }

    @Test
    fun packNamesRoundTripServiceDates() {
        assertEquals("journeys-2026-10-02", PackNames.journeys("2026-10-02"))
        assertEquals("2026-10-02", PackNames.serviceDateOf("journeys-2026-10-02"))
        assertNull(PackNames.serviceDateOf("journeys-2026-10"))
        assertNull(PackNames.serviceDateOf("meta"))
        assertNull(PackNames.serviceDateOf("gtfs"))
        assertTrue(PackNames.isJourneys(Release.brokenVariantSource))
    }
}
