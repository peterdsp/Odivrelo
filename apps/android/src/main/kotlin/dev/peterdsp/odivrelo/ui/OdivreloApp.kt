package dev.peterdsp.odivrelo.ui

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.systemBars
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ConfirmationNumber
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dev.peterdsp.odivrelo.MainActivity
import dev.peterdsp.odivrelo.R
import dev.peterdsp.odivrelo.state.OdivreloViewModel
import dev.peterdsp.odivrelo.state.Tab
import dev.peterdsp.odivrelo.state.UnresolvedLink
import dev.peterdsp.odivrelo.theme.OdivreloTheme
import dev.peterdsp.odivrelo.ui.adaptive.LocalOdivreloWindow
import dev.peterdsp.odivrelo.ui.adaptive.rememberOdivreloWindow
import dev.peterdsp.odivrelo.ui.welcome.WelcomeScreen
import dev.peterdsp.odivrelo.features.settings.Appearance
import androidx.compose.runtime.CompositionLocalProvider
import java.util.Locale

/**
 * The root of the application.
 *
 * It owns three things: the theme, the window measurement and the navigation
 * host. Everything else is a screen that reads one state object.
 */
@Composable
fun OdivreloApp(activity: Activity, initialIntent: Intent?) {
    val viewModel: OdivreloViewModel = viewModel(factory = OdivreloViewModel.Factory)
    val state by viewModel.state.collectAsStateWithLifecycle()
    val window = rememberOdivreloWindow(activity)

    val dark = when (state.settings.appearance) {
        Appearance.FOLLOW_SYSTEM -> isSystemInDarkTheme()
        Appearance.LIGHT -> false
        Appearance.DARK -> true
    }

    // The platform's own reduced-motion setting, plus the person's choice in
    // Odivrelo's settings. Either one switches transitions off.
    val context = LocalContext.current
    val systemReduceMotion = remember(context) {
        android.provider.Settings.Global.getFloat(
            context.contentResolver,
            android.provider.Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        ) == 0f
    }

    OdivreloTheme(
        darkTheme = dark,
        reduceMotion = state.settings.reduceMotion || systemReduceMotion,
        largerTouchTargets = state.settings.largerTouchTargets,
    ) {
        CompositionLocalProvider(LocalOdivreloWindow provides window) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background,
            ) {
                if (!state.settings.hasCompletedFirstRun) {
                    WelcomeScreen(
                        state = state,
                        onLanguage = viewModel::setLanguage,
                        onStart = viewModel::completeFirstRun,
                    )
                } else {
                    OdivreloShell(viewModel = viewModel, initialIntent = initialIntent)
                }
            }
        }
    }
}

@Composable
private fun OdivreloShell(viewModel: OdivreloViewModel, initialIntent: Intent?) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val navController = rememberNavController()
    val window = LocalOdivreloWindow.current
    val snackbarHost = remember { SnackbarHostState() }

    // Links that arrived before the composition existed, and links that arrive
    // while it is running.
    LaunchedEffect(Unit) {
        viewModel.handleInitialLink(initialIntent?.data)
    }
    LaunchedEffect(Unit) {
        MainActivity.pendingIntents.collect { intent ->
            intent.data?.let(viewModel::handleDeepLink)
        }
    }

    // Returning from a booking handoff, a maps application or the system
    // settings. Nothing is reset: the marker is cleared and the ticket, if one
    // was open, is decrypted again rather than having been kept in memory.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.endPurchaseHandoff()
        viewModel.refreshNotificationPermission()
        viewModel.reopenTicketIfAny()
    }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        viewModel.closeTicket(keepSelection = true)
    }

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(snackbarHost) },
        bottomBar = {
            if (!window.usesRail) {
                OdivreloNavigationBar(
                    selected = state.session.tab,
                    onSelect = { tab ->
                        viewModel.selectTab(tab)
                        navController.navigateToTab(tab)
                    },
                )
            }
        },
    ) { padding ->
        Row(Modifier.fillMaxSize().padding(padding)) {
            if (window.usesRail) {
                OdivreloNavigationRail(
                    selected = state.session.tab,
                    onSelect = { tab ->
                        viewModel.selectTab(tab)
                        navController.navigateToTab(tab)
                    },
                )
            }
            Box(Modifier.fillMaxSize()) {
                OdivreloNavHost(
                    navController = navController,
                    viewModel = viewModel,
                    snackbarHost = snackbarHost,
                )
            }
        }
    }

    // A link that could not be resolved says so, and offers the one thing that
    // always works: starting from search. It is never silently swallowed and it
    // never lands the person on the wrong journey.
    state.unresolvedLink?.let { unresolved ->
        val copy = when (unresolved) {
            is UnresolvedLink.Expired ->
                stringResource(R.string.deeplink_expired_title) to
                    stringResource(R.string.deeplink_expired_body)

            is UnresolvedLink.ForeignHost ->
                stringResource(R.string.deeplink_foreign_host_title) to
                    stringResource(R.string.deeplink_unknown_body)

            is UnresolvedLink.Unknown ->
                stringResource(R.string.deeplink_unknown_title) to
                    stringResource(R.string.deeplink_unknown_body)
        }
        AlertDialog(
            onDismissRequest = viewModel::clearUnresolvedLink,
            title = { Text(copy.first) },
            text = { Text(copy.second) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearUnresolvedLink()
                        viewModel.selectTab(Tab.SEARCH)
                        navController.navigateToTab(Tab.SEARCH)
                    },
                    modifier = Modifier.testTag("deeplink-unresolved-search"),
                ) {
                    Text(stringResource(R.string.action_search))
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::clearUnresolvedLink) {
                    Text(stringResource(R.string.action_close))
                }
            },
            modifier = Modifier.testTag("deeplink-unresolved"),
        )
    }

    // A back press at the root of a tab that is not search returns to search
    // rather than leaving the application, which is what a person expects from
    // a tabbed application and what keeps their query alive.
    BackHandler(enabled = currentRoute != null && currentRoute == state.session.tab.route &&
        state.session.tab != Tab.SEARCH) {
        viewModel.selectTab(Tab.SEARCH)
        navController.navigateToTab(Tab.SEARCH)
    }
}

/**
 * Tab navigation that keeps each tab's own back stack and scroll position.
 *
 * `saveState` and `restoreState` are what make leaving the search tab, reading
 * a saved trip and coming back land exactly where the person was, rather than
 * at a blank search form.
 */
fun NavHostController.navigateToTab(tab: Tab) {
    navigate(tab.route) {
        popUpTo(graph.startDestinationId) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

val Tab.route: String
    get() = when (this) {
        Tab.SEARCH -> Routes.SEARCH
        Tab.SAVED -> Routes.SAVED
        Tab.OFFLINE -> Routes.OFFLINE
        Tab.WALLET -> Routes.WALLET
        Tab.SETTINGS -> Routes.SETTINGS
    }

private data class TabSpec(val tab: Tab, val icon: ImageVector, val label: Int, val tag: String)

private val tabs = listOf(
    TabSpec(Tab.SEARCH, Icons.Default.Search, R.string.nav_search, "tab-search"),
    TabSpec(Tab.SAVED, Icons.Default.Star, R.string.nav_saved, "tab-saved"),
    TabSpec(Tab.OFFLINE, Icons.Default.CloudDownload, R.string.nav_offline, "tab-offline"),
    TabSpec(Tab.WALLET, Icons.Default.ConfirmationNumber, R.string.nav_wallet, "tab-wallet"),
    TabSpec(Tab.SETTINGS, Icons.Default.Settings, R.string.nav_settings, "tab-settings"),
)

@Composable
private fun OdivreloNavigationBar(selected: Tab, onSelect: (Tab) -> Unit) {
    NavigationBar(windowInsets = WindowInsets.systemBars) {
        tabs.forEach { spec ->
            val label = stringResource(spec.label)
            NavigationBarItem(
                selected = selected == spec.tab,
                onClick = { onSelect(spec.tab) },
                icon = { Icon(spec.icon, contentDescription = null) },
                label = { Text(label, maxLines = 1) },
                modifier = Modifier.testTag(spec.tag).semantics { contentDescription = label },
            )
        }
    }
}

@Composable
private fun OdivreloNavigationRail(selected: Tab, onSelect: (Tab) -> Unit) {
    NavigationRail {
        tabs.forEach { spec ->
            val label = stringResource(spec.label)
            NavigationRailItem(
                selected = selected == spec.tab,
                onClick = { onSelect(spec.tab) },
                icon = { Icon(spec.icon, contentDescription = null) },
                label = { Text(label, maxLines = 1) },
                modifier = Modifier.testTag(spec.tag).semantics { contentDescription = label },
            )
        }
    }
}

/** The locale the person is actually reading, for date and number formatting. */
@Composable
fun currentLocale(): Locale {
    val configuration = LocalConfiguration.current
    return remember(configuration) {
        @Suppress("DEPRECATION")
        configuration.locales.takeIf { !it.isEmpty }?.get(0) ?: Locale.getDefault()
    }
}
