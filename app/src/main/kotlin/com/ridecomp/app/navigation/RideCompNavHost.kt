package com.ridecomp.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.ridecomp.feature.history.ui.HistoryRoute
import com.ridecomp.feature.ride.ui.HomeRoute
import com.ridecomp.feature.routes.ui.RoutesRoute
import com.ridecomp.feature.sensors.ui.SensorsRoute
import com.ridecomp.feature.settings.ui.SettingsRoute

/**
 * App navigation graph. Feature screens only expose callbacks; navigation decisions live here
 * (ARCH-07: ViewModels and screens never hold a NavController).
 */
@Composable
fun RideCompNavHost(navController: NavHostController, modifier: Modifier = Modifier) {
    val navigateHome = { navController.navigateToTopLevel(TopLevelDestination.HOME) }
    NavHost(
        navController = navController,
        startDestination = TopLevelDestination.START.route,
        modifier = modifier,
    ) {
        composable(TopLevelDestination.HOME.route) {
            // TODO(T1.3): open the pre-ride check / ride screen.
            HomeRoute(onStartRide = {})
        }
        composable(TopLevelDestination.HISTORY.route) {
            HistoryRoute(onStartRide = navigateHome)
        }
        composable(TopLevelDestination.ROUTES.route) {
            // TODO(T3.1): open the GPX file picker.
            RoutesRoute(onImportGpx = {})
        }
        composable(TopLevelDestination.SENSORS.route) {
            // TODO(T2.1): start the BLE scan screen.
            SensorsRoute(onScan = {})
        }
        composable(TopLevelDestination.SETTINGS.route) {
            // TODO(T1.2): open the selected settings section.
            SettingsRoute(onSectionClick = {})
        }
    }
}

/** Switches tab without stacking duplicates and keeps each tab's state. */
fun NavHostController.navigateToTopLevel(destination: TopLevelDestination) {
    navigate(destination.route) {
        popUpTo(graph.startDestinationId) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
