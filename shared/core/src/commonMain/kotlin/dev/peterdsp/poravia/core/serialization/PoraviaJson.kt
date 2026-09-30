package dev.peterdsp.poravia.core.serialization

import kotlinx.serialization.json.Json

/**
 * The single JSON configuration used for every contract payload.
 *
 * `ignoreUnknownKeys` is on because contract 1 may gain additive fields, and an
 * installed beta must keep working when it does. `explicitNulls` is off so a
 * re-encoded payload stays the size the publisher produced.
 */
internal object PoraviaJson {
    val instance: Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
        isLenient = false
        allowStructuredMapKeys = false
    }

    /**
     * Used only to sniff the release id out of a pack before trusting it. It is
     * deliberately separate so a lenient read can never become the decoder for
     * real payloads.
     */
    val lenient: Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        isLenient = true
        coerceInputValues = true
    }
}
