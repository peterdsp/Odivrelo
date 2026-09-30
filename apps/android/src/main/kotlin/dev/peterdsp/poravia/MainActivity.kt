package dev.peterdsp.poravia

import android.app.LocaleManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.os.LocaleList
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import dev.peterdsp.poravia.app.PoraviaServices
import dev.peterdsp.poravia.app.SettingsStore
import dev.peterdsp.poravia.ui.PoraviaApp
import java.util.Locale
import kotlinx.coroutines.launch

/**
 * The application's one activity.
 *
 * It declares no `configChanges`, so a rotation, a resize, a fold and an
 * unfold all recreate it. That is deliberately the harder case: everything a
 * traveller cares about is restored from the saved instance state held by the
 * view model, and choosing recreation means that path is exercised constantly
 * rather than only when the system decides to kill the process.
 *
 * Nothing here locks an orientation or a size. `resizeableActivity` is on, so
 * split-screen and freeform windows are first-class.
 */
class MainActivity : ComponentActivity() {

    /**
     * The chosen language has to be in force before the first resource is read.
     *
     * On Android 13 and later the platform owns per-application language, and
     * the system applies it before the activity exists. Below that there is no
     * platform mechanism, so the configuration is overridden here from the
     * synchronous mirror of the person's choice.
     */
    override fun attachBaseContext(newBase: Context) {
        val tag = SettingsStore(newBase).mirroredLanguageTag
        if (tag.isNullOrBlank() || Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            super.attachBaseContext(newBase)
            return
        }
        val configuration = Configuration(newBase.resources.configuration)
        val locale = Locale.forLanguageTag(tag)
        Locale.setDefault(locale)
        configuration.setLocale(locale)
        configuration.setLayoutDirection(locale)
        super.attachBaseContext(newBase.createConfigurationContext(configuration))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        applyPlatformLocale()

        setContent {
            PoraviaApp(
                activity = this,
                initialIntent = intent,
            )
        }

        // Keep the platform's idea of the application language in step with the
        // person's choice while the activity is alive.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                PoraviaServices.get(this@MainActivity).settingsStore.settings.collect {
                    applyPlatformLocale()
                }
            }
        }
    }

    /**
     * `singleTask` means a second link arrives here rather than in a new
     * activity, so the running application handles it in place and keeps the
     * state the person already had.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        deliverLink(intent)
    }

    /**
     * The one way a link reaches the running application.
     *
     * `onNewIntent` is protected, so this exists to be callable from a test that
     * wants to exercise the real path rather than launching a second task and
     * testing the task stack instead of the link handling.
     */
    fun deliverLink(intent: Intent) {
        setIntent(intent)
        pendingIntents.tryEmit(intent)
    }

    private fun applyPlatformLocale() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val tag = SettingsStore(this).mirroredLanguageTag ?: return
        val manager = getSystemService(LocaleManager::class.java) ?: return
        val current = manager.applicationLocales
        if (!current.isEmpty && current[0]?.language == tag) return
        manager.applicationLocales = LocaleList.forLanguageTags(tag)
    }

    companion object {
        /**
         * Links that arrive while the application is already running. A shared
         * flow rather than a callback so the composition can collect it with the
         * lifecycle and never miss one during a recreation.
         */
        // replay is deliberately zero. A recreated activity must not be handed
        // a link it already acted on, or every rotation after opening a deep
        // link would throw away where the person had navigated to since.
        val pendingIntents = kotlinx.coroutines.flow.MutableSharedFlow<Intent>(
            replay = 0,
            extraBufferCapacity = 4,
        )
    }
}
