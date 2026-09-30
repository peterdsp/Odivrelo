package dev.peterdsp.poravia.ui

import android.net.Uri

/**
 * Every destination, named once.
 *
 * Identifiers are percent-encoded on the way into a route because the contract
 * says they are opaque strings: nothing here may assume they contain no slash,
 * and a route is a path.
 */
object Routes {
    const val SEARCH = "search"
    const val RESULTS = "results"
    const val PLACE_PICKER = "place"

    const val JOURNEY_PATTERN = "journey/{id}?date={date}"
    const val OPERATOR_PATTERN = "operator/{id}"
    const val STOP_PATTERN = "stop/{id}?date={date}"

    const val SAVED = "saved"
    const val OFFLINE = "offline"
    const val WALLET = "wallet"

    const val SETTINGS = "settings"
    const val SETTINGS_PRIVACY = "settings/privacy"
    const val SETTINGS_SUPPORT = "settings/support"
    const val SETTINGS_SOURCES = "settings/sources"
    const val SETTINGS_LICENCES = "settings/licences"
    const val SETTINGS_DIAGNOSTICS = "settings/diagnostics"
    const val SETTINGS_COVERAGE = "settings/coverage"
    const val SETTINGS_NOTIFICATIONS = "settings/notifications"

    fun journey(id: String, serviceDate: String): String =
        "journey/" + Uri.encode(id) + "?date=" + Uri.encode(serviceDate)

    fun operator(id: String): String = "operator/" + Uri.encode(id)

    fun stop(id: String, serviceDate: String): String =
        "stop/" + Uri.encode(id) + "?date=" + Uri.encode(serviceDate)

    /** The settings sub-pages, in the order the settings list shows them. */
    val settingsDetails: List<String> = listOf(
        SETTINGS_NOTIFICATIONS,
        SETTINGS_PRIVACY,
        SETTINGS_SOURCES,
        SETTINGS_COVERAGE,
        SETTINGS_SUPPORT,
        SETTINGS_LICENCES,
        SETTINGS_DIAGNOSTICS,
    )
}
