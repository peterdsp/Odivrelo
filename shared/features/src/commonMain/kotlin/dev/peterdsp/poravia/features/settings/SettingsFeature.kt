package dev.peterdsp.poravia.features.settings

import dev.peterdsp.poravia.core.Brand
import kotlin.native.ObjCName

/**
 * The things a person can change, as pure transitions over [PoraviaSettings].
 *
 * Storage is a platform concern: Android writes a DataStore file, iOS writes a
 * preferences file. What must not differ is what the values are allowed to be,
 * so the validation lives here and both platforms get the same answers.
 */
@ObjCName("PoraviaSettingsFeature")
object SettingsFeature {

    /** Falls back to Greek rather than accepting a language with no translation. */
    fun withLanguage(settings: PoraviaSettings, languageTag: String): PoraviaSettings {
        val resolved = languageTag.take(2).lowercase()
            .takeIf { it in Brand.LANGUAGES } ?: Brand.DEFAULT_LANGUAGE
        return settings.copy(languageTag = resolved)
    }

    fun withAppearance(settings: PoraviaSettings, appearance: Appearance): PoraviaSettings =
        settings.copy(appearance = appearance)

    fun withLargerTouchTargets(settings: PoraviaSettings, enabled: Boolean): PoraviaSettings =
        settings.copy(largerTouchTargets = enabled)

    fun withReduceMotion(settings: PoraviaSettings, enabled: Boolean): PoraviaSettings =
        settings.copy(reduceMotion = enabled)

    fun withAlwaysExpandStops(settings: PoraviaSettings, enabled: Boolean): PoraviaSettings =
        settings.copy(alwaysExpandStops = enabled)

    /**
     * Turning reminders off also forgets the lead time choice? No: the choice is
     * kept, so turning them back on does not silently pick a different time from
     * the one the person had set.
     */
    fun withReminders(settings: PoraviaSettings, enabled: Boolean): PoraviaSettings =
        settings.copy(remindersEnabled = enabled)

    /**
     * Only the offered lead times are accepted. An arbitrary number would be a
     * value no interface can render as a chosen option.
     */
    fun withReminderLead(settings: PoraviaSettings, minutes: Int): PoraviaSettings {
        if (minutes !in PoraviaSettings.REMINDER_LEAD_CHOICES) return settings
        return settings.copy(reminderLeadMinutes = minutes)
    }

    fun withMeteredDownloads(settings: PoraviaSettings, enabled: Boolean): PoraviaSettings =
        settings.copy(downloadOverMeteredNetwork = enabled)

    fun completingFirstRun(settings: PoraviaSettings): PoraviaSettings =
        settings.copy(hasCompletedFirstRun = true)

    /**
     * Switching between demonstration and real data changes exactly one thing:
     * whether the permanent notice is shown. Nothing else in the application
     * branches on it, which is what makes the switch safe to flip.
     */
    fun withDataMode(settings: PoraviaSettings, mode: AppDataMode): PoraviaSettings =
        settings.copy(dataMode = mode)

    /**
     * Repairs a settings value read back from storage that has since become
     * invalid, for example a language that was removed or a lead time that is no
     * longer offered. Returns the same instance when nothing needed repairing, so
     * a caller can tell whether it must write anything back.
     */
    fun sanitised(settings: PoraviaSettings): PoraviaSettings {
        var result = settings
        if (result.languageTag != result.resolvedLanguageTag) {
            result = result.copy(languageTag = result.resolvedLanguageTag)
        }
        if (result.reminderLeadMinutes !in PoraviaSettings.REMINDER_LEAD_CHOICES) {
            result = result.copy(
                reminderLeadMinutes = PoraviaSettings.DEFAULT_REMINDER_LEAD_MINUTES,
            )
        }
        return result
    }
}
