package dev.peterdsp.poravia.core.domain

import dev.peterdsp.poravia.core.model.Meta
import dev.peterdsp.poravia.core.model.OfflineManifest
import dev.peterdsp.poravia.core.model.PlaceResults
import dev.peterdsp.poravia.core.model.SourceList
import dev.peterdsp.poravia.core.packs.CoveragePack
import dev.peterdsp.poravia.core.packs.JourneysPack
import dev.peterdsp.poravia.core.packs.OperatorsPack
import dev.peterdsp.poravia.core.packs.PackNames
import dev.peterdsp.poravia.core.packs.PackStore
import dev.peterdsp.poravia.core.packs.StopsPack
import dev.peterdsp.poravia.core.serialization.PoraviaJson
import kotlinx.serialization.DeserializationStrategy

/** Why a release could not be assembled from what is on disk. */
internal sealed interface ReleaseLoadFailure {
    data object NoManifest : ReleaseLoadFailure
    data class MissingPacks(val names: List<String>) : ReleaseLoadFailure
    data class ReleaseMismatch(val packName: String, val found: String, val expected: String) :
        ReleaseLoadFailure

    data class Corrupt(val packName: String, val reason: String) : ReleaseLoadFailure
}

internal sealed interface ReleaseLoadResult {
    data class Loaded(val index: ReleaseIndex) : ReleaseLoadResult
    data class Failed(val failure: ReleaseLoadFailure) : ReleaseLoadResult
}

/**
 * Builds a [ReleaseIndex] from the installed static packs, and decodes one
 * service date's journeys on request.
 *
 * The loader is the second half of the release-mixing rule. `PackStore` makes it
 * impossible for two releases to share a directory; the loader also reads the
 * release id declared inside each pack and refuses the whole release if any of
 * them disagrees with the manifest. A single stale file copied in by hand fails
 * loudly instead of quietly pairing one release's departure with another's stops.
 */
internal class ReleaseLoader(private val store: PackStore) {

    fun load(): ReleaseLoadResult {
        val manifest = store.installedManifest()
            ?: return ReleaseLoadResult.Failed(ReleaseLoadFailure.NoManifest)

        val missing = PackNames.required.filter { name ->
            val file = manifest.files[name]
            file == null || !store.isInstalled(file)
        }
        if (missing.isNotEmpty()) {
            return ReleaseLoadResult.Failed(ReleaseLoadFailure.MissingPacks(missing))
        }

        val meta = decode(manifest, PackNames.META, Meta.serializer()) { it.releaseId }
        if (meta is Decoded.Failure) return ReleaseLoadResult.Failed(meta.failure)
        val coverage = decode(manifest, PackNames.COVERAGE, CoveragePack.serializer()) { it.releaseId }
        if (coverage is Decoded.Failure) return ReleaseLoadResult.Failed(coverage.failure)
        val places = decode(manifest, PackNames.PLACES, PlaceResults.serializer()) { it.releaseId }
        if (places is Decoded.Failure) return ReleaseLoadResult.Failed(places.failure)
        val stops = decode(manifest, PackNames.STOPS, StopsPack.serializer()) { it.releaseId }
        if (stops is Decoded.Failure) return ReleaseLoadResult.Failed(stops.failure)
        val operators = decode(manifest, PackNames.OPERATORS, OperatorsPack.serializer()) { it.releaseId }
        if (operators is Decoded.Failure) return ReleaseLoadResult.Failed(operators.failure)
        val sources = decode(manifest, PackNames.SOURCES, SourceList.serializer()) { it.releaseId }
        if (sources is Decoded.Failure) return ReleaseLoadResult.Failed(sources.failure)

        val metaBody = (meta as Decoded.Success).value
        val coverageBody = (coverage as Decoded.Success).value.coverage
        return ReleaseLoadResult.Loaded(
            ReleaseIndex(
                releaseId = manifest.releaseId,
                publishedAt = manifest.publishedAt,
                dataMode = metaBody.dataMode,
                meta = metaBody.copy(
                    coverage = coverageBody.copy(
                        releaseId = manifest.releaseId,
                        publishedAt = manifest.publishedAt,
                        dataMode = metaBody.dataMode,
                    ),
                ),
                coverage = coverageBody.copy(
                    releaseId = manifest.releaseId,
                    publishedAt = manifest.publishedAt,
                    dataMode = metaBody.dataMode,
                ),
                places = (places as Decoded.Success).value.places,
                // The stops pack stamps its departures with one service date. The
                // core always recomputes them for the date being shown, so the
                // published ones are dropped rather than left to be shown by
                // accident on some other day.
                stops = (stops as Decoded.Success).value.stops
                    .mapValues { (_, stop) -> stop.copy(departures = emptyList()) },
                operators = (operators as Decoded.Success).value.operators,
                sources = (sources as Decoded.Success).value.sources,
            ),
        )
    }

    /**
     * Decodes one service date's journeys pack, refusing a file that belongs to
     * another release or that does not cover the date it claims to.
     */
    fun loadDay(serviceDate: String): DayLoadResult {
        val manifest = store.installedManifest() ?: return DayLoadResult.Failed(
            ReleaseLoadFailure.NoManifest,
        )
        val packName = PackNames.journeys(serviceDate)
        val file = manifest.files[packName] ?: return DayLoadResult.NotInRelease
        if (!store.isInstalled(file)) return DayLoadResult.NotInstalled(packName)

        return when (val decoded = decode(manifest, packName, JourneysPack.serializer()) { it.releaseId }) {
            is Decoded.Failure -> DayLoadResult.Failed(decoded.failure)
            is Decoded.Success -> {
                val day = decoded.value
                if (day.serviceDate != serviceDate) {
                    DayLoadResult.Failed(
                        ReleaseLoadFailure.Corrupt(
                            packName,
                            "pack covers ${day.serviceDate}, not $serviceDate",
                        ),
                    )
                } else {
                    DayLoadResult.Loaded(day)
                }
            }
        }
    }

    /** Service dates this release publishes, in order. */
    fun serviceDates(): List<String> = store.installedManifest()
        ?.files
        ?.keys
        ?.mapNotNull { PackNames.serviceDateOf(it) }
        ?.sorted()
        .orEmpty()

    private fun <T> decode(
        manifest: OfflineManifest,
        packName: String,
        serializer: DeserializationStrategy<T>,
        releaseIdOf: (T) -> String,
    ): Decoded<T> {
        val file = manifest.files[packName]
            ?: return Decoded.Failure(ReleaseLoadFailure.MissingPacks(listOf(packName)))
        val text = store.readPack(file)
            ?: return Decoded.Failure(ReleaseLoadFailure.MissingPacks(listOf(packName)))
        return decodePackText(packName, text, manifest.releaseId, serializer, releaseIdOf)
    }

}

internal sealed interface Decoded<out T> {
    data class Success<T>(val value: T) : Decoded<T>
    data class Failure(val failure: ReleaseLoadFailure) : Decoded<Nothing>
}

/**
 * Decodes a pack body that has already been verified, wherever it came from.
 *
 * Disk and network share this one function. That is what makes a bundled pack, a
 * downloaded pack and an endpoint response produce the same value: there is one
 * decoder and one release check, not one per transport.
 */
internal fun <T> decodePackText(
    packName: String,
    text: String,
    expectedReleaseId: String,
    serializer: DeserializationStrategy<T>,
    releaseIdOf: (T) -> String,
): Decoded<T> {
    val value = runCatching { PoraviaJson.instance.decodeFromString(serializer, text) }
        .getOrElse { error ->
            return Decoded.Failure(
                ReleaseLoadFailure.Corrupt(packName, error.message ?: "decode failed"),
            )
        }
    val found = releaseIdOf(value)
    if (found != expectedReleaseId) {
        return Decoded.Failure(
            ReleaseLoadFailure.ReleaseMismatch(packName, found, expectedReleaseId),
        )
    }
    return Decoded.Success(value)
}

internal sealed interface DayLoadResult {
    data class Loaded(val day: JourneysPack) : DayLoadResult

    /** The release does not publish this service date at all. */
    data object NotInRelease : DayLoadResult

    /** The release publishes it, but the pack has not been downloaded yet. */
    data class NotInstalled(val packName: String) : DayLoadResult

    data class Failed(val failure: ReleaseLoadFailure) : DayLoadResult
}
