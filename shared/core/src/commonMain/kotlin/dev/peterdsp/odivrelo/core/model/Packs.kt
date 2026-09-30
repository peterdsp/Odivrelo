package dev.peterdsp.odivrelo.core.model

import kotlin.native.ObjCName
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** One content-addressed file in a published release manifest. */
@Serializable
@ObjCName("OdivreloManifestFile")
data class ManifestFile(
    val path: String,
    val sha256: String,
    val bytes: Long,
    val mediaType: String = "application/json",
)

@Serializable
@ObjCName("OdivreloOfflineManifest")
data class OfflineManifest(
    val contractVersion: String,
    val product: String,
    val releaseId: String,
    val publishedAt: String,
    val dataMode: DataMode = DataMode.DEMO,
    val counts: Map<String, Int> = emptyMap(),
    val files: Map<String, ManifestFile> = emptyMap(),
)

/** A pack offered for download, with everything needed to decide. */
@Serializable
@ObjCName("OdivreloAvailablePack")
data class AvailablePack(
    val name: String,
    val path: String,
    val sha256: String,
    val bytes: Long,
    val releaseId: String,
    val installed: Boolean,
    val updateAvailable: Boolean,
    val title: LocalizedText,
    val summary: LocalizedText,
)

@Serializable
@ObjCName("OdivreloInstalledPack")
data class InstalledPack(
    val name: String,
    val releaseId: String,
    val sha256: String,
    val bytes: Long,
    val installedAt: String,
    val publishedAt: String,
    /** Set when a previous release of this pack is still retained for rollback. */
    val previousReleaseId: String? = null,
)

@Serializable
@ObjCName("OdivreloOfflineCatalog")
data class OfflineCatalog(
    val releaseId: String,
    val publishedAt: String,
    val dataMode: DataMode,
    val available: List<AvailablePack> = emptyList(),
    val installed: List<InstalledPack> = emptyList(),
    val totalInstalledBytes: Long = 0,
    val rollbackReleaseId: String? = null,
    /**
     * Honest statement of what offline does and does not include. Map tiles are
     * never bundled, so this is not an optional footnote.
     */
    val mapAvailability: OfflineMapAvailability = OfflineMapAvailability(),
    val manifestReachable: Boolean = true,
)

@Serializable
@ObjCName("OdivreloOfflineMapAvailability")
data class OfflineMapAvailability(
    val stopCoordinatesAvailable: Boolean = true,
    val routeGeometryAvailable: Boolean = true,
    val baseMapTilesAvailable: Boolean = false,
    val searchAvailable: Boolean = true,
)

@Serializable
@ObjCName("OdivreloPackProgress")
data class PackProgress(
    val packName: String,
    val bytesDownloaded: Long,
    val totalBytes: Long,
    val phase: PackPhase,
    val attempt: Int = 1,
) {
    /** 0.0 to 1.0, or null when the server did not declare a length. */
    val fraction: Double?
        get() = if (totalBytes > 0) {
            (bytesDownloaded.toDouble() / totalBytes.toDouble()).coerceIn(0.0, 1.0)
        } else {
            null
        }
}

@Serializable
@ObjCName("OdivreloPackPhase")
enum class PackPhase {
    @SerialName("queued")
    QUEUED,

    @SerialName("downloading")
    DOWNLOADING,

    @SerialName("resuming")
    RESUMING,

    @SerialName("verifying")
    VERIFYING,

    @SerialName("installing")
    INSTALLING,

    @SerialName("done")
    DONE,
}

@Serializable
@ObjCName("OdivreloPackFailure")
enum class PackFailure {
    @SerialName("network")
    NETWORK,

    @SerialName("timeout")
    TIMEOUT,

    @SerialName("digest_mismatch")
    DIGEST_MISMATCH,

    @SerialName("release_mismatch")
    RELEASE_MISMATCH,

    @SerialName("storage_full")
    STORAGE_FULL,

    @SerialName("not_in_manifest")
    NOT_IN_MANIFEST,

    @SerialName("cancelled")
    CANCELLED,

    @SerialName("io")
    IO,
}

/**
 * The terminal outcome of a pack download. A failure is always explicit: a
 * pack is never installed after a digest mismatch, and the previous good copy
 * is never removed until the replacement is verified in place.
 */
@Serializable
@ObjCName("OdivreloPackResult")
data class PackResult(
    val packName: String,
    val succeeded: Boolean,
    val installed: InstalledPack? = null,
    val failure: PackFailure? = null,
    val message: String? = null,
    val attempts: Int = 1,
)
