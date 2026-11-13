package com.ridecomp.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.ridecomp.app.navigation.AppRoutes
import com.ridecomp.app.navigation.IncomingGpx
import com.ridecomp.app.navigation.RideCompNavHost
import com.ridecomp.app.navigation.TopLevelDestination
import com.ridecomp.app.navigation.navigateToTopLevel
import com.ridecomp.ui.theme.RideCompDimens
import com.ridecomp.ui.theme.RideCompTheme
import org.koin.androidx.compose.koinViewModel

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

@Composable
private fun RideCompBottomBar(navController: NavHostController, route: String?) {
    val current = TopLevelDestination.fromRoute(route)
    val colors = RideCompTheme.colors
    NavigationBar(containerColor = colors.surface) {
        TopLevelDestination.entries.forEach { destination ->
            NavigationBarItem(
                selected = destination == current,
                onClick = { navController.navigateToTopLevel(destination) },
                icon = {
                    Icon(
                        painter = painterResource(destination.iconRes),
                        // The label below already names the tab for accessibility.
                        contentDescription = null,
                        modifier = Modifier.size(RideCompDimens.iconSize),
                    )
                },
                label = { Text(text = stringResource(destination.labelRes)) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = colors.accent,
                    selectedTextColor = colors.accent,
                    indicatorColor = colors.bg,
                    unselectedIconColor = colors.textSecondary,
                    unselectedTextColor = colors.textSecondary,
                ),
            )
        }
    }
}
