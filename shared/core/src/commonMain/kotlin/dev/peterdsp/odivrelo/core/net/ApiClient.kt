package dev.peterdsp.odivrelo.core.net

import dev.peterdsp.odivrelo.core.model.ContractError
import dev.peterdsp.odivrelo.core.model.ContractErrorEnvelope
import dev.peterdsp.odivrelo.core.model.ErrorCode
import dev.peterdsp.odivrelo.core.model.JourneyDetail
import dev.peterdsp.odivrelo.core.model.JourneyResults
import dev.peterdsp.odivrelo.core.model.OfflineManifest
import dev.peterdsp.odivrelo.core.serialization.OdivreloJson
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.serialization.DeserializationStrategy

/** What the caller gets back from a network read. */
internal sealed interface ApiOutcome<out T> {
    data class Success<T>(val value: T) : ApiOutcome<T>
    data class Failed(val error: ContractError, val cause: Throwable? = null) : ApiOutcome<Nothing>
}

/**
 * The thin HTTP read surface.
 *
 * It holds no credentials: every endpoint it touches is public. The
 * administrative surface described by contract 1 is deliberately unreachable
 * from a client, so nothing here could carry a bearer token by accident.
 *
 * Two origins are supported and they serve the same bytes. A static pack origin
 * serves `manifest.json` and `packs/<file>` directly; an API origin serves
 * `/v1/offline/manifest` and `/v1/offline/packs/<file>`. Because a pack is
 * exactly what the matching endpoint returns, the core needs no separate live
 * read path and therefore cannot end up with two disagreeing timetable engines.
 */
internal class ApiClient(
    private val httpClient: HttpClient,
    private val apiBaseUrl: String?,
    private val staticPacksBaseUrl: String?,
) {

    val hasOrigin: Boolean
        get() = !staticPacksBaseUrl.isNullOrBlank() || !apiBaseUrl.isNullOrBlank()

    /** Where a manifest file path from the manifest is resolved against. */
    fun packBaseUrl(): String? = when {
        !staticPacksBaseUrl.isNullOrBlank() -> staticPacksBaseUrl.trimEnd('/')
        !apiBaseUrl.isNullOrBlank() -> apiBaseUrl.trimEnd('/') + "/v1/offline"
        else -> null
    }

    private fun manifestUrl(): String? = when {
        !staticPacksBaseUrl.isNullOrBlank() -> staticPacksBaseUrl.trimEnd('/') + "/manifest.json"
        !apiBaseUrl.isNullOrBlank() -> apiBaseUrl.trimEnd('/') + "/v1/offline/manifest"
        else -> null
    }

    val hasApi: Boolean get() = !apiBaseUrl.isNullOrBlank()

    /**
     * Resolves a journey search the installed release has no pack for.
     *
     * A release only materialises a journeys pack for the dates its data names.
     * A weekday-recurring service on some other date has to be asked for, and
     * this is the only read that goes to a computed endpoint rather than to a
     * published pack. The caller checks the release id before using the answer.
     */
    suspend fun journeys(
        originId: String,
        destinationId: String,
        serviceDate: String,
        languageTag: String,
        accessibleOnly: Boolean,
    ): ApiOutcome<JourneyResults> {
        val base = apiBaseUrl ?: return noApi()
        return request(
            base.trimEnd('/') + "/v1/journeys",
            JourneyResults.serializer(),
            buildMap {
                put("origin", originId)
                put("destination", destinationId)
                put("date", serviceDate)
                put("lang", languageTag)
                if (accessibleOnly) put("accessible", "true")
            },
        )
    }

    suspend fun journeyDetail(
        journeyId: String,
        serviceDate: String,
        languageTag: String,
    ): ApiOutcome<JourneyDetail> {
        val base = apiBaseUrl ?: return noApi()
        return request(
            base.trimEnd('/') + "/v1/journeys/" + journeyId,
            JourneyDetail.serializer(),
            mapOf("date" to serviceDate, "lang" to languageTag),
        )
    }

    private fun noApi(): ApiOutcome.Failed = ApiOutcome.Failed(
        ContractError(ErrorCode.UNAVAILABLE, "No live service is configured."),
    )

    /** Reads the published release manifest from whichever origin is configured. */
    suspend fun manifest(): ApiOutcome<OfflineManifest> {
        val url = manifestUrl() ?: return ApiOutcome.Failed(
            ContractError(ErrorCode.UNAVAILABLE, "no pack origin configured"),
        )
        return request(url, OfflineManifest.serializer())
    }

    private suspend fun <T> request(
        url: String,
        serializer: DeserializationStrategy<T>,
        parameters: Map<String, String> = emptyMap(),
    ): ApiOutcome<T> = try {
        val response = httpClient.get(url) {
            parameters.forEach { (key, value) -> parameter(key, value) }
        }
        val text = response.bodyAsText()
        if (response.status.isSuccess()) {
            ApiOutcome.Success(OdivreloJson.instance.decodeFromString(serializer, text))
        } else {
            val decoded = runCatching {
                OdivreloJson.instance
                    .decodeFromString(ContractErrorEnvelope.serializer(), text).error
            }.getOrNull()
            ApiOutcome.Failed(
                decoded ?: ContractError(
                    code = when (response.status.value) {
                        400 -> ErrorCode.INVALID_REQUEST
                        401 -> ErrorCode.UNAUTHORIZED
                        404 -> ErrorCode.NOT_FOUND
                        409 -> ErrorCode.RELEASE_MISMATCH
                        else -> ErrorCode.UNAVAILABLE
                    },
                    message = "http " + response.status.value,
                ),
            )
        }
    } catch (error: Throwable) {
        if (error is kotlin.coroutines.cancellation.CancellationException) throw error
        ApiOutcome.Failed(
            ContractError(ErrorCode.UNAVAILABLE, error.message ?: "network unavailable"),
            error,
        )
    }
}
