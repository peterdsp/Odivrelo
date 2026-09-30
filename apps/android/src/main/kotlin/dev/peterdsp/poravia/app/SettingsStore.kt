package dev.peterdsp.poravia.app

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.peterdsp.poravia.features.settings.PoraviaSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "poravia_settings",
)

/**
 * The person's choices, stored as one serialised value.
 *
 * Writing the whole settings object at once is deliberate: a half-applied
 * change (a language written but the appearance not) would be visible on the
 * next launch, and DataStore's edit is atomic for a single key.
 *
 * Nothing here is sensitive, and nothing here is ever sent anywhere. The
 * imported ticket documents live in a different store entirely, encrypted, and
 * this file never learns anything about them.
 */
class SettingsStore(context: Context) {

    private val appContext = context.applicationContext
    private val store = appContext.settingsDataStore

    /**
     * A synchronous mirror of the person's choices.
     *
     * Two things need them before anything can suspend.
     * `Activity.attachBaseContext` has to wrap the configuration with the chosen
     * language, and the first composition has to know whether to draw the
     * welcome screen or the application. Waiting on DataStore for either means
     * drawing an empty window first and hoping the answer arrives, and on a slow
     * device that empty window is what a person sees.
     *
     * DataStore remains the source of truth. This is a cache written on every
     * emission and every update, holding exactly the same value.
     */
    private val mirror = appContext.getSharedPreferences(MIRROR, Context.MODE_PRIVATE)

    val mirroredLanguageTag: String?
        get() = mirror.getString(MIRROR_LANGUAGE, null)

    val mirroredAppearance: String?
        get() = mirror.getString(MIRROR_APPEARANCE, null)

    /**
     * The last settings written, read without suspending, or null on a genuinely
     * first launch where there is nothing to read and the defaults are right.
     */
    fun mirroredSettings(): PoraviaSettings? = mirror.getString(MIRROR_SETTINGS, null)
        ?.let { raw ->
            runCatching { json.decodeFromString(PoraviaSettings.serializer(), raw) }.getOrNull()
        }

    val settings: Flow<PoraviaSettings> = store.data.map { preferences ->
        val value = preferences[KEY]?.let { raw ->
            runCatching { json.decodeFromString(PoraviaSettings.serializer(), raw) }
                .getOrDefault(PoraviaSettings())
        } ?: PoraviaSettings()
        writeMirror(value)
        value
    }

    suspend fun update(transform: (PoraviaSettings) -> PoraviaSettings) {
        store.edit { preferences ->
            val current = preferences[KEY]
                ?.let {
                    runCatching { json.decodeFromString(PoraviaSettings.serializer(), it) }
                        .getOrDefault(PoraviaSettings())
                }
                ?: PoraviaSettings()
            val next = transform(current)
            preferences[KEY] = json.encodeToString(PoraviaSettings.serializer(), next)
            writeMirror(next)
        }
    }

    private fun writeMirror(value: PoraviaSettings) {
        val encoded = json.encodeToString(PoraviaSettings.serializer(), value)
        if (mirror.getString(MIRROR_SETTINGS, null) == encoded) return
        mirror.edit()
            .putString(MIRROR_LANGUAGE, value.resolvedLanguageTag)
            .putString(MIRROR_APPEARANCE, value.appearance.name)
            .putString(MIRROR_SETTINGS, encoded)
            .apply()
    }

    private companion object {
        val KEY = stringPreferencesKey("settings_v1")
        const val MIRROR = "poravia_locale"
        const val MIRROR_LANGUAGE = "language"
        const val MIRROR_APPEARANCE = "appearance"
        const val MIRROR_SETTINGS = "settings"

        /**
         * Unknown keys are ignored so a settings file written by a newer build
         * does not make an older one fall back to defaults and silently forget
         * the person's language.
         */
        val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    }
}
