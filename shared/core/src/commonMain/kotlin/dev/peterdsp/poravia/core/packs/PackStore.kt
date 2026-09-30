package dev.peterdsp.poravia.core.packs

import dev.peterdsp.poravia.core.crypto.Sha256
import dev.peterdsp.poravia.core.io.Paths
import dev.peterdsp.poravia.core.io.PlatformFiles
import dev.peterdsp.poravia.core.model.ManifestFile
import dev.peterdsp.poravia.core.model.OfflineManifest
import dev.peterdsp.poravia.core.model.PackFailure
import dev.peterdsp.poravia.core.serialization.PoraviaJson

/**
 * The on-disk layout of installed public data.
 *
 * ```
 * <packsDirectory>/current/manifest.json
 * <packsDirectory>/current/packs/<content-addressed file>
 * <packsDirectory>/previous/…          one release kept for rollback
 * <packsDirectory>/tmp/<pack>.part     partially downloaded bytes
 * <packsDirectory>/tmp/<pack>.resume   what those bytes belong to
 * ```
 *
 * Two rules are structural rather than checked after the fact:
 *
 * 1. `current` only ever holds files from one release id. Adopting a manifest
 *    with a different release id archives the whole of `current` into
 *    `previous` first, so packs from two releases can never be read together.
 * 2. A pack file only appears under `current` after its SHA-256 matched the
 *    manifest. Verification happens on the temporary file, and the move into
 *    place is a rename.
 */
internal class PackStore(private val rootDirectory: String) {

    val currentDirectory: String get() = Paths.join(rootDirectory, CURRENT)
    val previousDirectory: String get() = Paths.join(rootDirectory, PREVIOUS)
    val tempDirectory: String get() = Paths.join(rootDirectory, TEMP)

    fun prepare() {
        PlatformFiles.mkdirs(currentDirectory)
        PlatformFiles.mkdirs(tempDirectory)
    }

    // -- Manifests -----------------------------------------------------------

    fun readManifest(directory: String): OfflineManifest? {
        val path = Paths.join(directory, MANIFEST)
        if (!PlatformFiles.exists(path)) return null
        return runCatching {
            PoraviaJson.instance.decodeFromString(
                OfflineManifest.serializer(),
                PlatformFiles.readBytes(path).decodeToString(),
            )
        }.getOrNull()
    }

    fun installedManifest(): OfflineManifest? = readManifest(currentDirectory)

    fun previousManifest(): OfflineManifest? = readManifest(previousDirectory)

    fun writeCurrentManifest(manifest: OfflineManifest) {
        PlatformFiles.mkdirs(currentDirectory)
        PlatformFiles.writeBytes(
            Paths.join(currentDirectory, MANIFEST),
            PoraviaJson.instance.encodeToString(OfflineManifest.serializer(), manifest)
                .encodeToByteArray(),
        )
    }

    /**
     * Makes [manifest] the release that `current` belongs to.
     *
     * When the installed release differs, the whole installed release is moved
     * to `previous` before anything of the new one lands. This is what makes
     * "never mix two releases" true by construction and also what makes a
     * single-release rollback possible.
     */
    fun adoptRelease(manifest: OfflineManifest): Boolean {
        val installed = installedManifest()
        if (installed != null && installed.releaseId == manifest.releaseId) {
            writeCurrentManifest(manifest)
            return false
        }
        if (installed != null) {
            PlatformFiles.deleteRecursively(previousDirectory)
            if (!PlatformFiles.move(currentDirectory, previousDirectory)) {
                // Moving failed (for example a cross-device layout). Rather than
                // risk a mixed directory, drop the old release entirely.
                PlatformFiles.deleteRecursively(currentDirectory)
            }
        }
        PlatformFiles.mkdirs(currentDirectory)
        writeCurrentManifest(manifest)
        return installed != null
    }

    fun hasRollbackTarget(): Boolean = previousManifest() != null

    /** Swaps `current` and `previous`. Returns the manifest now installed. */
    fun rollback(): OfflineManifest? {
        val previous = previousManifest() ?: return null
        val swap = Paths.join(rootDirectory, SWAP)
        PlatformFiles.deleteRecursively(swap)
        if (PlatformFiles.exists(currentDirectory) && !PlatformFiles.move(currentDirectory, swap)) {
            return null
        }
        if (!PlatformFiles.move(previousDirectory, currentDirectory)) {
            // Put the original release back rather than leaving nothing installed.
            PlatformFiles.move(swap, currentDirectory)
            return null
        }
        if (PlatformFiles.exists(swap)) {
            PlatformFiles.move(swap, previousDirectory)
        }
        return installedManifest() ?: previous
    }

    // -- Pack files ----------------------------------------------------------

    fun packPath(manifestFile: ManifestFile): String =
        Paths.join(currentDirectory, manifestFile.path)

    fun isInstalled(manifestFile: ManifestFile): Boolean {
        val path = packPath(manifestFile)
        return PlatformFiles.exists(path) && PlatformFiles.size(path) == manifestFile.bytes
    }

    fun readPack(manifestFile: ManifestFile): String? {
        val path = packPath(manifestFile)
        if (!PlatformFiles.exists(path)) return null
        return runCatching { PlatformFiles.readBytes(path).decodeToString() }.getOrNull()
    }

    /**
     * Verifies a downloaded temporary file and, only if it matches, moves it
     * into place. The previous good copy is replaced by a rename, so a reader
     * either sees the old file or the new one and never a truncated one.
     */
    fun verifyAndInstall(
        temporaryPath: String,
        manifestFile: ManifestFile,
        expectedReleaseId: String,
    ): PackFailure? {
        if (!PlatformFiles.exists(temporaryPath)) return PackFailure.IO

        val bytes = runCatching { PlatformFiles.readBytes(temporaryPath) }.getOrNull()
            ?: return PackFailure.IO

        if (manifestFile.bytes > 0 && bytes.size.toLong() != manifestFile.bytes) {
            PlatformFiles.delete(temporaryPath)
            return PackFailure.DIGEST_MISMATCH
        }

        val digest = Sha256().update(bytes).hexDigest()
        if (!Sha256.digestsMatch(manifestFile.sha256, digest)) {
            PlatformFiles.delete(temporaryPath)
            return PackFailure.DIGEST_MISMATCH
        }

        val declaredRelease = releaseIdOf(bytes.decodeToString())
        if (declaredRelease != null && declaredRelease != expectedReleaseId) {
            PlatformFiles.delete(temporaryPath)
            return PackFailure.RELEASE_MISMATCH
        }

        val target = packPath(manifestFile)
        PlatformFiles.mkdirs(Paths.parentOf(target))
        if (!PlatformFiles.move(temporaryPath, target)) {
            return PackFailure.IO
        }
        return null
    }

    fun removePack(manifestFile: ManifestFile): Boolean =
        PlatformFiles.delete(packPath(manifestFile))

    fun installedBytes(): Long {
        val manifest = installedManifest() ?: return 0
        return manifest.files.values.sumOf { file ->
            val path = packPath(file)
            if (PlatformFiles.exists(path)) PlatformFiles.size(path) else 0L
        }
    }

    // -- Partial downloads ---------------------------------------------------

    fun partialPath(packName: String): String = Paths.join(tempDirectory, "$packName.part")

    fun resumeRecordPath(packName: String): String = Paths.join(tempDirectory, "$packName.resume")

    fun partialBytes(packName: String): Long {
        val path = partialPath(packName)
        return if (PlatformFiles.exists(path)) PlatformFiles.size(path) else 0L
    }

    /**
     * Returns how many already-downloaded bytes may be reused. A partial file
     * is only reusable when the resume record proves it belongs to the same
     * digest; otherwise it is discarded rather than appended to.
     */
    fun reusablePartialBytes(packName: String, manifestFile: ManifestFile): Long {
        val recordPath = resumeRecordPath(packName)
        val partial = partialPath(packName)
        if (!PlatformFiles.exists(partial)) {
            PlatformFiles.delete(recordPath)
            return 0
        }
        val record = runCatching {
            PoraviaJson.instance.decodeFromString(
                ResumeRecord.serializer(),
                PlatformFiles.readBytes(recordPath).decodeToString(),
            )
        }.getOrNull()
        val size = PlatformFiles.size(partial)
        if (record == null ||
            record.sha256 != manifestFile.sha256 ||
            size <= 0 ||
            (manifestFile.bytes > 0 && size >= manifestFile.bytes)
        ) {
            PlatformFiles.delete(partial)
            PlatformFiles.delete(recordPath)
            return 0
        }
        return size
    }

    fun writeResumeRecord(packName: String, manifestFile: ManifestFile, releaseId: String) {
        PlatformFiles.mkdirs(tempDirectory)
        PlatformFiles.writeBytes(
            resumeRecordPath(packName),
            PoraviaJson.instance.encodeToString(
                ResumeRecord.serializer(),
                ResumeRecord(
                    packName = packName,
                    sha256 = manifestFile.sha256,
                    totalBytes = manifestFile.bytes,
                    releaseId = releaseId,
                ),
            ).encodeToByteArray(),
        )
    }

    fun discardPartial(packName: String) {
        PlatformFiles.delete(partialPath(packName))
        PlatformFiles.delete(resumeRecordPath(packName))
    }

    fun usableSpaceBytes(): Long = PlatformFiles.usableSpaceBytes(rootDirectory)

    private fun releaseIdOf(json: String): String? = runCatching {
        PoraviaJson.lenient.decodeFromString(PackEnvelope.serializer(), json).releaseId
    }.getOrNull()

    companion object {
        const val MANIFEST = "manifest.json"
        private const val CURRENT = "current"
        private const val PREVIOUS = "previous"
        private const val TEMP = "tmp"
        private const val SWAP = "swap"
    }
}

@kotlinx.serialization.Serializable
internal data class ResumeRecord(
    val packName: String,
    val sha256: String,
    val totalBytes: Long,
    val releaseId: String,
)
