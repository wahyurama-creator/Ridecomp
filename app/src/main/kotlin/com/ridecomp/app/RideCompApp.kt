package com.ridecomp.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.ridecomp.app.navigation.AppRoutes
import com.ridecomp.app.navigation.IncomingDebugScreen
import com.ridecomp.app.navigation.IncomingGpx
import com.ridecomp.app.navigation.IncomingStravaAuth
import com.ridecomp.app.navigation.IncomingWidget
import com.ridecomp.app.navigation.RideCompNavHost
import com.ridecomp.app.navigation.TopLevelDestination
import com.ridecomp.app.navigation.navigateToTopLevel
import com.ridecomp.core.source.Logger
import com.ridecomp.ui.component.BottomNavItem
import com.ridecomp.ui.component.RideCompBottomNav
import com.ridecomp.ui.theme.RideCompTheme
import kotlinx.collections.immutable.toImmutableList
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

/** Root composable: bottom navigation + navigation graph; first launch starts with onboarding. */
@Composable
fun RideCompApp(modifier: Modifier = Modifier, viewModel: AppViewModel = koinViewModel()) {
    val isOnboardingDone by viewModel.isOnboardingDone.collectAsStateWithLifecycle()
    val done = isOnboardingDone
    if (done == null) {
        // Loading the first-launch flag: keep the black window, no flash of the wrong screen.
        Box(modifier = modifier.fillMaxSize().background(RideCompTheme.colors.bg))
        return
    }
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val route = backStackEntry?.destination?.route
    val incomingGpx by IncomingGpx.uri.collectAsStateWithLifecycle()
    // A GPX opened from another app: show the routes tab, which imports it (onboarding comes first).
    LaunchedEffect(incomingGpx, done) {
        if (incomingGpx != null && done) navController.navigateToTopLevel(TopLevelDestination.ROUTES)
    }
    val stravaRedirect by IncomingStravaAuth.redirect.collectAsStateWithLifecycle()
    // Back from Strava's authorize page: the Strava screen finishes the connection.
    LaunchedEffect(stravaRedirect, done) {
        if (stravaRedirect != null && done) navController.navigate(AppRoutes.STRAVA) { launchSingleTop = true }
    }
    val widgetTarget by IncomingWidget.target.collectAsStateWithLifecycle()
    // The widget button: the pre-ride check, or the ride already running (T9.2).
    LaunchedEffect(widgetTarget, done) {
        val target = widgetTarget ?: return@LaunchedEffect
        if (!done) return@LaunchedEffect
        val destination = when (target) {
            IncomingWidget.Target.PRE_RIDE -> AppRoutes.preRide()
            IncomingWidget.Target.RIDE -> AppRoutes.RIDE
        }
        navController.navigate(destination) { launchSingleTop = true }
        IncomingWidget.consumed()
    }
    DebugScreenEffect(navController, isOnboardingDone = done)
    Scaffold(
        modifier = modifier,
        containerColor = RideCompTheme.colors.bg,
        bottomBar = { if (route !in AppRoutes.FULL_SCREEN) RideCompBottomBar(navController, route) },
    ) { innerPadding ->
        RideCompNavHost(
            navController = navController,
            startDestination = if (done) TopLevelDestination.START.route else AppRoutes.ONBOARDING,
            modifier = Modifier.padding(innerPadding),
        )
    }
}

/** Debug builds only: the comparison screenshot script opens one screen on top of the start screen (T13.2). */
@Composable
private fun DebugScreenEffect(navController: NavHostController, isOnboardingDone: Boolean) {
    val debugScreen by IncomingDebugScreen.route.collectAsStateWithLifecycle()
    val logger = koinInject<Logger>()
    LaunchedEffect(debugScreen, isOnboardingDone) {
        val screen = debugScreen ?: return@LaunchedEffect
        if (!isOnboardingDone) return@LaunchedEffect
        try {
            navController.navigate(screen) {
                popUpTo(navController.graph.startDestinationId)
                launchSingleTop = true
            }
        } catch (e: IllegalArgumentException) {
            logger.warn(DEBUG_SCREEN_TAG, e) { "No screen $screen" }
        }
        IncomingDebugScreen.consumed()
    }
}

private const val DEBUG_SCREEN_TAG = "DebugScreen"

@Composable
private fun RideCompBottomBar(navController: NavHostController, route: String?) {
    val current = TopLevelDestination.fromRoute(route)
    val items = TopLevelDestination.entries.map { destination ->
        BottomNavItem(stringResource(destination.labelRes), destination.iconRes, isSelected = destination == current)
    }.toImmutableList()
    RideCompBottomNav(
        items = items,
        onSelect = { index -> navController.navigateToTopLevel(TopLevelDestination.entries[index]) },
    )
}
