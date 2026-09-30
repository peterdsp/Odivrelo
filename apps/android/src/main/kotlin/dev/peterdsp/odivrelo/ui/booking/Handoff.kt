package dev.peterdsp.odivrelo.ui.booking

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb

/** What a handoff attempt did, so the screen can say something true about it. */
enum class HandoffResult { OPENED, NO_APPLICATION, REFUSED }

/**
 * Leaving Odivrelo, deliberately and visibly.
 *
 * Odivrelo sells nothing. A purchase is always the operator's own website, its
 * ticket office, or its telephone line, and the handoff is a Custom Tab so the
 * person can see whose site they are on and come back to exactly the state they
 * left. Nothing about them is attached to the request: no identifier, no
 * referrer Odivrelo invented, no query parameter added on the way out.
 */
object Handoff {

    fun openUrl(
        context: Context,
        url: String,
        toolbarColor: Color,
        onToolbarColorDark: Color,
    ): HandoffResult {
        val uri = safeHttpUri(url) ?: return HandoffResult.REFUSED
        val intent = CustomTabsIntent.Builder()
            .setShowTitle(true)
            .setUrlBarHidingEnabled(false)
            .setDefaultColorSchemeParams(
                CustomTabColorSchemeParams.Builder()
                    .setToolbarColor(toolbarColor.toArgb())
                    .build(),
            )
            .setColorSchemeParams(
                CustomTabsIntent.COLOR_SCHEME_DARK,
                CustomTabColorSchemeParams.Builder()
                    .setToolbarColor(onToolbarColorDark.toArgb())
                    .build(),
            )
            .build()
        return try {
            intent.launchUrl(context, uri)
            HandoffResult.OPENED
        } catch (_: ActivityNotFoundException) {
            // No browser and no Custom Tabs provider. Rather than fail silently,
            // the screen falls back to the verified ticket office details.
            HandoffResult.NO_APPLICATION
        }
    }

    fun dial(context: Context, phone: String): HandoffResult {
        val trimmed = phone.trim()
        if (trimmed.isEmpty()) return HandoffResult.REFUSED
        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(trimmed)))
        return try {
            context.startActivity(intent)
            HandoffResult.OPENED
        } catch (_: ActivityNotFoundException) {
            HandoffResult.NO_APPLICATION
        }
    }

    /**
     * Opens a coordinate in whatever maps application the device has.
     *
     * There are no offline map tiles in this release, so Odivrelo does not draw a
     * map it cannot draw without a connection. It hands the coordinate to an
     * application that owns one, and says so on the screen.
     */
    fun openMap(
        context: Context,
        latitude: Double,
        longitude: Double,
        label: String,
    ): HandoffResult {
        val geo = "geo:" + latitude + "," + longitude + "?q=" + latitude + "," + longitude +
            "(" + Uri.encode(label) + ")"
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(geo))
        return try {
            context.startActivity(intent)
            HandoffResult.OPENED
        } catch (_: ActivityNotFoundException) {
            HandoffResult.NO_APPLICATION
        }
    }

    fun email(context: Context, address: String, subject: String): HandoffResult {
        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:" + Uri.encode(address)))
            .putExtra(Intent.EXTRA_SUBJECT, subject)
        return try {
            context.startActivity(intent)
            HandoffResult.OPENED
        } catch (_: ActivityNotFoundException) {
            HandoffResult.NO_APPLICATION
        }
    }

    fun openSystemNotificationSettings(context: Context): HandoffResult {
        val intent = Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
        return try {
            context.startActivity(intent)
            HandoffResult.OPENED
        } catch (_: ActivityNotFoundException) {
            HandoffResult.NO_APPLICATION
        }
    }

    /**
     * Only http and https are ever opened. A purchase link comes from published
     * data, and data is not trusted to name a scheme: an `intent://` or a
     * `javascript:` URL in a source file must not become an action on someone's
     * phone.
     */
    private fun safeHttpUri(url: String): Uri? {
        val uri = runCatching { Uri.parse(url.trim()) }.getOrNull() ?: return null
        val scheme = uri.scheme?.lowercase()
        if (scheme != "https" && scheme != "http") return null
        if (uri.host.isNullOrBlank()) return null
        return uri
    }
}
