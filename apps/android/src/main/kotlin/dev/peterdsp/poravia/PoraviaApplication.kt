package dev.peterdsp.poravia

import android.app.Application
import androidx.work.Configuration
import dev.peterdsp.poravia.app.PoraviaServices
import dev.peterdsp.poravia.reminders.ReminderNotifier

/**
 * The application object.
 *
 * It does three things and nothing else: it hands the shared core its context,
 * it registers the one notification channel, and it configures WorkManager by
 * hand. The last is necessary because the manifest removes
 * `androidx.startup.InitializationProvider`; a content provider that runs
 * arbitrary library initialisers at process start is exactly the kind of
 * invisible dependency this application refuses to carry, so the one library
 * that needed it is wired up explicitly instead.
 *
 * There is no analytics initialiser, no crash reporter and no advertising
 * identifier here, and there is nowhere for one to be added quietly.
 */
class PoraviaApplication : Application(), Configuration.Provider {

    override fun onCreate() {
        super.onCreate()
        PoraviaServices.get(this)
        ReminderNotifier.ensureChannel(this)
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setMinimumLoggingLevel(android.util.Log.WARN)
            .build()
}
