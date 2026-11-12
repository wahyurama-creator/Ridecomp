package com.ridecomp.app

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.ridecomp.app.navigation.RideCompNavHost
import com.ridecomp.app.navigation.TopLevelDestination
import com.ridecomp.app.navigation.navigateToTopLevel
import com.ridecomp.ui.theme.RideCompDimens
import com.ridecomp.ui.theme.RideCompTheme

/** Root composable: bottom navigation + navigation graph. */
@Composable
fun RideCompApp(modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    Scaffold(
        modifier = modifier,
        containerColor = RideCompTheme.colors.bg,
        bottomBar = { RideCompBottomBar(navController) },
    ) { innerPadding ->
        RideCompNavHost(navController = navController, modifier = Modifier.padding(innerPadding))
    }
}

@Composable
private fun RideCompBottomBar(navController: NavHostController) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val current = TopLevelDestination.fromRoute(backStackEntry?.destination?.route)
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
