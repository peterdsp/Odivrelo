package dev.peterdsp.odivrelo.state

import android.net.Uri
import dev.peterdsp.odivrelo.core.Brand
import dev.peterdsp.odivrelo.core.time.ServiceTime

/**
 * The two link surfaces Odivrelo accepts.
 *
 * * `https://odivrelo.peterdsp.dev/...` App Links.
 * * The product's own `odivrelo://` scheme.
 *
 * Both are validated rather than trusted. A host that is not the controlled
 * domain, a scheme that is not the product's own, an unknown path, a malformed
 * identifier or a service date in the past each produce a stated outcome
 * instead of a blank screen or, worse, the wrong journey.
 *
 * App Links are declared without `autoVerify` on purpose: Digital Asset Links
 * verification needs `/.well-known/assetlinks.json` published with this
 * application's release signing fingerprint, and there is no release signing
 * identity yet. Declaring verification that cannot succeed would produce links
 * that silently fail. Without it the https links still open through the
 * ordinary chooser, and the custom scheme always works.
 */
sealed interface DeepLink {
    data class Search(
        val originId: String?,
        val destinationId: String?,
        val serviceDate: String?,
    ) : DeepLink

    data class Journey(val id: String, val serviceDate: String) : DeepLink
    data class Operator(val id: String) : DeepLink
    data class Stop(val id: String, val serviceDate: String?) : DeepLink
    data object Offline : DeepLink
    data object Trips : DeepLink
    data object Settings : DeepLink
    data object Coverage : DeepLink
    data object Sources : DeepLink
    data object Wallet : DeepLink
}

sealed interface DeepLinkResult {
    data class Resolved(val link: DeepLink) : DeepLinkResult
    data class Unresolved(val reason: UnresolvedLink) : DeepLinkResult
}

object DeepLinkParser {

    /**
     * Parses a link without touching any application state, which is what makes
     * it testable on its own. [today] decides whether a link naming a service
     * date has expired; it comes from the core, never from the device clock
     * interpreted locally.
     */
    fun parse(uri: Uri, today: String): DeepLinkResult {
        val raw = uri.toString()
        val segments: List<String> = when (uri.scheme?.lowercase()) {
            "https", "http" -> {
                if (!uri.host.equals(Brand.DOMAIN, ignoreCase = true)) {
                    return DeepLinkResult.Unresolved(UnresolvedLink.ForeignHost(raw))
                }
                uri.pathSegments.orEmpty().filter { it.isNotEmpty() }
            }

            Brand.URL_SCHEME -> buildList {
                // odivrelo://journey/<id>?date=… puts the first segment in the host.
                uri.host?.takeIf { it.isNotEmpty() }?.let { add(it) }
                addAll(uri.pathSegments.orEmpty().filter { it.isNotEmpty() })
            }

            else -> return DeepLinkResult.Unresolved(UnresolvedLink.ForeignHost(raw))
        }

        val date = queryParameter(uri, "date")?.let(::serviceDateOrNull)
        fun expired(value: String): DeepLinkResult? =
            if (value < today) DeepLinkResult.Unresolved(UnresolvedLink.Expired(raw, value)) else null

        val head = segments.firstOrNull()?.lowercase()
            ?: return DeepLinkResult.Resolved(DeepLink.Search(null, null, date))

        return when (head) {
            "search", "journeys", "results" -> {
                date?.let { expired(it) }?.let { return it }
                DeepLinkResult.Resolved(
                    DeepLink.Search(
                        originId = identifier(
                            queryParameter(uri, "origin") ?: queryParameter(uri, "from"),
                        ),
                        destinationId = identifier(
                            queryParameter(uri, "destination") ?: queryParameter(uri, "to"),
                        ),
                        serviceDate = date,
                    ),
                )
            }

            "journey", "j" -> {
                val id = segments.getOrNull(1)?.let(::identifier)
                    ?: return DeepLinkResult.Unresolved(UnresolvedLink.Unknown(raw))
                val value = date ?: return DeepLinkResult.Unresolved(UnresolvedLink.Unknown(raw))
                expired(value)?.let { return it }
                DeepLinkResult.Resolved(DeepLink.Journey(id, value))
            }

            "operator", "operators" -> {
                val id = segments.getOrNull(1)?.let(::identifier)
                    ?: return DeepLinkResult.Unresolved(UnresolvedLink.Unknown(raw))
                DeepLinkResult.Resolved(DeepLink.Operator(id))
            }

            "stop", "stops", "station", "stations" -> {
                val id = segments.getOrNull(1)?.let(::identifier)
                    ?: return DeepLinkResult.Unresolved(UnresolvedLink.Unknown(raw))
                date?.let { expired(it) }?.let { return it }
                DeepLinkResult.Resolved(DeepLink.Stop(id, date))
            }

            "offline" -> DeepLinkResult.Resolved(DeepLink.Offline)
            "trips", "saved" -> DeepLinkResult.Resolved(DeepLink.Trips)
            "wallet", "tickets" -> DeepLinkResult.Resolved(DeepLink.Wallet)
            "settings" -> DeepLinkResult.Resolved(DeepLink.Settings)
            "coverage" -> DeepLinkResult.Resolved(DeepLink.Coverage)
            "sources" -> DeepLinkResult.Resolved(DeepLink.Sources)
            else -> DeepLinkResult.Unresolved(UnresolvedLink.Unknown(raw))
        }
    }

    /**
     * A malformed query string throws inside [Uri.getQueryParameter] rather than
     * returning null, and a link is attacker-supplied input, so it is caught.
     */
    private fun queryParameter(uri: Uri, name: String): String? =
        runCatching { uri.getQueryParameter(name) }.getOrNull()?.takeIf { it.isNotBlank() }

    private fun serviceDateOrNull(value: String): String? =
        ServiceTime.parseServiceDateOrNull(value)?.toString()

    /**
     * Identifiers are opaque under contract 1, so they are not parsed, only
     * checked for a shape that cannot smuggle a path separator or a control
     * character into a later file or database lookup.
     *
     * A dot is legitimate inside an identifier such as `place.kentro-terminal`,
     * so the rule is not "no dots": it is that a run of dots that could climb a
     * path, and any separator, are refused.
     */
    fun identifier(raw: String?): String? {
        if (raw.isNullOrEmpty() || raw.length > MAX_IDENTIFIER_LENGTH) return null
        if (raw == "." || raw == "..") return null
        if (raw.contains("..")) return null
        if (raw.contains('/') || raw.contains('\\')) return null
        if (!raw.all { it.isLetterOrDigit() && it.code < 128 || it in ".-_:~" }) return null
        return raw
    }

    private const val MAX_IDENTIFIER_LENGTH = 128
}
