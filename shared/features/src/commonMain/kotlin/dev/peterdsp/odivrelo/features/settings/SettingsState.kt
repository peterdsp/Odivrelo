package dev.peterdsp.odivrelo.features.settings

import dev.peterdsp.odivrelo.core.Brand
import kotlin.native.ObjCName
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@ObjCName("OdivreloAppearance")
enum class Appearance {
    @SerialName("system")
    FOLLOW_SYSTEM,

    @SerialName("light")
    LIGHT,

    @SerialName("dark")
    DARK,
}

/**
 * Whether the application is showing the invented demonstration dataset or real
 * data. Flipping this removes the demonstration notice and changes nothing else.
 */
@Serializable
@ObjCName("OdivreloAppDataMode")
enum class AppDataMode {
    @SerialName("demo")
    DEMO,

    @SerialName("real")
    REAL,
}

/**
 * Everything the person chose. Held in one serialisable value so it can be
 * written atomically and restored after process death without partial state.
 */
@Serializable
@ObjCName("OdivreloSettings")
data class OdivreloSettings(
    val languageTag: String = Brand.DEFAULT_LANGUAGE,
    val appearance: Appearance = Appearance.FOLLOW_SYSTEM,
    /** Larger touch targets and roomier spacing, independent of system font size. */
    val largerTouchTargets: Boolean = false,
    /** Honour the person's reduced-motion preference even where the system does not. */
    val reduceMotion: Boolean = false,
    /** Show the full ordered stop list expanded by default. */
    val alwaysExpandStops: Boolean = false,
    val remindersEnabled: Boolean = false,
    val reminderLeadMinutes: Int = DEFAULT_REMINDER_LEAD_MINUTES,
    val downloadOverMeteredNetwork: Boolean = false,
    val hasCompletedFirstRun: Boolean = false,
    val dataMode: AppDataMode = AppDataMode.DEMO,
) {
    val resolvedLanguageTag: String
        get() = languageTag.take(2).lowercase()
            .takeIf { it in Brand.LANGUAGES } ?: Brand.DEFAULT_LANGUAGE

    /**
     * True when the persistent demonstration notice must be shown. It is not
     * dismissible, so this is the only thing that controls it.
     */
    val showsDemoNotice: Boolean get() = dataMode == AppDataMode.DEMO

    companion object {
        const val DEFAULT_REMINDER_LEAD_MINUTES: Int = 45
        val REMINDER_LEAD_CHOICES: List<Int> = listOf(15, 30, 45, 60, 90, 120)
    }
}
