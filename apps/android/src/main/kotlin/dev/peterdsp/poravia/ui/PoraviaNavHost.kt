package dev.peterdsp.poravia.ui

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.navArgument
import dev.peterdsp.poravia.state.AppState
import dev.peterdsp.poravia.state.PendingRoute
import dev.peterdsp.poravia.state.PoraviaViewModel
import dev.peterdsp.poravia.state.Tab
import dev.peterdsp.poravia.theme.LocalReduceMotion
import dev.peterdsp.poravia.ui.adaptive.LocalPoraviaWindow
import dev.peterdsp.poravia.ui.journey.JourneyDetailScreen
import dev.peterdsp.poravia.ui.library.SavedScreen
import dev.peterdsp.poravia.ui.offline.OfflineScreen
import dev.peterdsp.poravia.ui.operator.OperatorScreen
import dev.peterdsp.poravia.ui.results.ResultsScreen
import dev.peterdsp.poravia.ui.search.PlacePickerScreen
import dev.peterdsp.poravia.ui.search.SearchScreen
import dev.peterdsp.poravia.ui.settings.CoverageScreen
import dev.peterdsp.poravia.ui.settings.DiagnosticsScreen
import dev.peterdsp.poravia.ui.settings.LicencesScreen
import dev.peterdsp.poravia.ui.settings.NotificationsScreen
import dev.peterdsp.poravia.ui.settings.PrivacyScreen
import dev.peterdsp.poravia.ui.settings.SettingsListScreen
import dev.peterdsp.poravia.ui.settings.SettingsPlaceholder
import dev.peterdsp.poravia.ui.settings.SourcesScreen
import dev.peterdsp.poravia.ui.settings.SupportScreen
import dev.peterdsp.poravia.ui.stop.StopScreen
import dev.peterdsp.poravia.ui.wallet.WalletScreen
import dev.peterdsp.poravia.ui.adaptive.TwoPaneLayout
import androidx.compose.runtime.CompositionLocalProvider

/**
 * Every destination reads the application state for itself.
 *
 * The graph builder is not part of the recomposition of the screens it creates,
 * so a state value captured in a `composable { }` lambda is the value that
 * existed when the graph was built and never changes again. That is how a
 * settings screen ends up insisting there are no reminders while the alarm
 * manager holds one. Reading inside each destination is the only arrangement
 * that cannot go stale.
 */
@Composable
private fun PoraviaViewModel.observed(): AppState {
    val value by state.collectAsStateWithLifecycle()
    return value
}

@Composable
fun PoraviaNavHost(
    navController: NavHostController,
    viewModel: PoraviaViewModel,
    snackbarHost: SnackbarHostState,
) {
    val state = viewModel.observed()
    val window = LocalPoraviaWindow.current
    val reduceMotion = LocalReduceMotion.current
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    /**
     * The one place geometry changes navigation.
     *
     * Going from one pane to two collapses a pushed journey into the detail
     * pane; going the other way pushes the selected journey back onto the
     * stack. The selection itself never moves, so what a person is reading
     * survives a fold, an unfold, a rotation and a split-screen resize.
     */
    LaunchedEffect(window.paneCount, currentRoute) {
        val selected = state.session.selectedJourneyId
        if (window.paneCount == 2 && currentRoute == Routes.JOURNEY_PATTERN) {
            navController.popBackStack(Routes.RESULTS, inclusive = false)
        } else if (window.paneCount == 1 &&
            currentRoute == Routes.RESULTS &&
            selected != null
        ) {
            navController.navigate(Routes.journey(selected, state.session.query.serviceDate))
        }
    }

    // A deep link that names a page rather than the current flow.
    LaunchedEffect(state.pendingRoute) {
        when (val pending = state.pendingRoute) {
            null -> Unit
            is PendingRoute.Operator -> {
                navController.navigate(Routes.operator(pending.id))
                viewModel.consumePendingRoute()
            }

            is PendingRoute.Stop -> {
                navController.navigate(Routes.stop(pending.id, pending.serviceDate))
                viewModel.consumePendingRoute()
            }

            PendingRoute.Coverage -> {
                navController.navigate(Routes.SETTINGS_COVERAGE)
                viewModel.consumePendingRoute()
            }

            PendingRoute.Sources -> {
                navController.navigate(Routes.SETTINGS_SOURCES)
                viewModel.consumePendingRoute()
            }
        }
    }

    // A tab chosen from a deep link has to move the navigation host too.
    LaunchedEffect(state.session.tab) {
        val wanted = state.session.tab.route
        if (currentRoute != null && !currentRoute.startsWith(wanted)) {
            val onDetail = currentRoute == Routes.JOURNEY_PATTERN ||
                currentRoute == Routes.OPERATOR_PATTERN ||
                currentRoute == Routes.STOP_PATTERN ||
                currentRoute == Routes.RESULTS ||
                currentRoute == Routes.PLACE_PICKER
            if (!(state.session.tab == Tab.SEARCH && onDetail)) {
                navController.navigateToTab(state.session.tab)
            }
        }
    }

    // Reduced motion is honoured by removing the transition outright rather
    // than shortening it. A person who asked for no motion gets none.
    NavHost(
        navController = navController,
        startDestination = Routes.SEARCH,
        modifier = Modifier.fillMaxSize(),
        enterTransition = { if (reduceMotion) EnterTransition.None else fadeIn() },
        exitTransition = { if (reduceMotion) ExitTransition.None else fadeOut() },
        popEnterTransition = { if (reduceMotion) EnterTransition.None else fadeIn() },
        popExitTransition = { if (reduceMotion) ExitTransition.None else fadeOut() },
    ) {
        composable(Routes.SEARCH) {
            val current = viewModel.observed()
            SearchScreen(
                state = current,
                viewModel = viewModel,
                onPickPlace = { navController.navigate(Routes.PLACE_PICKER) },
                onRun = {
                    viewModel.runSearch()
                    navController.navigate(Routes.RESULTS)
                },
                onOpenStop = { id ->
                    navController.navigate(Routes.stop(id, state.session.query.serviceDate))
                },
            )
        }

        composable(Routes.PLACE_PICKER) {
            val current = viewModel.observed()
            PlacePickerScreen(
                state = current,
                viewModel = viewModel,
                onDone = { navController.popBackStack() },
            )
        }

        composable(Routes.RESULTS) {
            val current = viewModel.observed()
            ResultsScreen(
                state = current,
                viewModel = viewModel,
                onOpenJourney = { id ->
                    viewModel.selectJourney(id)
                    if (window.paneCount == 1) {
                        navController.navigate(
                            Routes.journey(id, current.session.query.serviceDate),
                        )
                    }
                },
                onOpenOperator = { id -> navController.navigate(Routes.operator(id)) },
                onOpenStop = { id ->
                    navController.navigate(Routes.stop(id, current.session.query.serviceDate))
                },
                onBack = { navController.popBackStack() },
                snackbarHost = snackbarHost,
            )
        }

        composable(
            route = Routes.JOURNEY_PATTERN,
            arguments = listOf(
                navArgument("id") { type = NavType.StringType },
                navArgument("date") {
                    type = NavType.StringType
                    defaultValue = ""
                },
            ),
        ) { entry ->
            val current = viewModel.observed()
            val id = entry.arguments?.getString("id").orEmpty()
            val date = entry.arguments?.getString("date")
                ?.takeIf { it.isNotBlank() }
                ?: current.session.query.serviceDate
            LaunchedEffect(id, date) {
                if (id.isNotBlank()) viewModel.loadJourney(id, date)
            }
            JourneyDetailScreen(
                state = current,
                viewModel = viewModel,
                journeyId = id,
                serviceDate = date,
                onBack = { navController.popBackStack() },
                onOpenOperator = { operatorId ->
                    navController.navigate(Routes.operator(operatorId))
                },
                onOpenStop = { stopId -> navController.navigate(Routes.stop(stopId, date)) },
                snackbarHost = snackbarHost,
            )
        }

        composable(
            route = Routes.OPERATOR_PATTERN,
            arguments = listOf(navArgument("id") { type = NavType.StringType }),
        ) { entry ->
            OperatorScreen(
                operatorId = entry.arguments?.getString("id").orEmpty(),
                state = viewModel.observed(),
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
            )
        }

        composable(
            route = Routes.STOP_PATTERN,
            arguments = listOf(
                navArgument("id") { type = NavType.StringType },
                navArgument("date") {
                    type = NavType.StringType
                    defaultValue = ""
                },
            ),
        ) { entry ->
            val current = viewModel.observed()
            StopScreen(
                stopId = entry.arguments?.getString("id").orEmpty(),
                serviceDate = entry.arguments?.getString("date")
                    ?.takeIf { it.isNotBlank() }
                    ?: current.session.query.serviceDate,
                state = current,
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onOpenJourney = { id, date ->
                    viewModel.selectJourney(id)
                    navController.navigate(Routes.journey(id, date))
                },
            )
        }

        composable(Routes.SAVED) {
            SavedScreen(
                state = viewModel.observed(),
                viewModel = viewModel,
                onOpenJourney = { id, date ->
                    viewModel.selectJourney(id)
                    navController.navigate(Routes.journey(id, date))
                },
                snackbarHost = snackbarHost,
            )
        }

        composable(Routes.OFFLINE) {
            OfflineScreen(state = viewModel.observed(), viewModel = viewModel)
        }

        composable(Routes.WALLET) {
            WalletScreen(state = viewModel.observed(), viewModel = viewModel)
        }

        composable(Routes.SETTINGS) {
            SettingsWithDetail(
                state = viewModel.observed(),
                viewModel = viewModel,
                navController = navController,
                current = null,
            ) { SettingsPlaceholder() }
        }

        Routes.settingsDetails.forEach { route ->
            composable(route) {
                val current = viewModel.observed()
                SettingsWithDetail(
                    state = current,
                    viewModel = viewModel,
                    navController = navController,
                    current = route,
                ) {
                    SettingsDetail(route, current, viewModel) { navController.popBackStack() }
                }
            }
        }
    }
}

/**
 * The settings list and the open section, side by side on a wide window and one
 * at a time on a narrow one. Both read the same route, so a rotation from a
 * phone-width split view into a tablet-width one keeps the open section.
 */
@Composable
private fun SettingsWithDetail(
    state: AppState,
    viewModel: PoraviaViewModel,
    navController: NavHostController,
    current: String?,
    detail: @Composable () -> Unit,
) {
    val window = LocalPoraviaWindow.current
    if (window.paneCount == 2) {
        TwoPaneLayout(
            modifier = Modifier.fillMaxSize(),
            fold = window.fold,
            listPane = { modifier ->
                SettingsListScreen(
                    state = state,
                    viewModel = viewModel,
                    selectedRoute = current,
                    modifier = modifier,
                    onOpen = { route ->
                        navController.navigate(route) {
                            popUpTo(Routes.SETTINGS)
                            launchSingleTop = true
                        }
                    },
                )
            },
            detailPane = { modifier ->
                androidx.compose.foundation.layout.Box(modifier) { detail() }
            },
        )
    } else if (current == null) {
        SettingsListScreen(
            state = state,
            viewModel = viewModel,
            selectedRoute = null,
            modifier = Modifier.fillMaxSize(),
            onOpen = { route -> navController.navigate(route) },
        )
    } else {
        detail()
    }
}

@Composable
private fun SettingsDetail(
    route: String,
    state: AppState,
    viewModel: PoraviaViewModel,
    onBack: () -> Unit,
) {
    when (route) {
        Routes.SETTINGS_PRIVACY -> PrivacyScreen(onBack)
        Routes.SETTINGS_SUPPORT -> SupportScreen(onBack)
        Routes.SETTINGS_SOURCES -> SourcesScreen(state, viewModel, onBack)
        Routes.SETTINGS_LICENCES -> LicencesScreen(onBack)
        Routes.SETTINGS_DIAGNOSTICS -> DiagnosticsScreen(state, viewModel, onBack)
        Routes.SETTINGS_COVERAGE -> CoverageScreen(state, viewModel, onBack)
        Routes.SETTINGS_NOTIFICATIONS -> NotificationsScreen(state, viewModel, onBack)
        else -> SettingsPlaceholder()
    }
}
