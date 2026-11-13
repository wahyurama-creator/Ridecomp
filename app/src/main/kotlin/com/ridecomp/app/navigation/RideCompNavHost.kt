package com.ridecomp.app.navigation

import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.ridecomp.app.DevTools
import com.ridecomp.feature.history.ui.ActivityDetailRoute
import com.ridecomp.feature.history.ui.HistoryRoute
import com.ridecomp.feature.history.ui.StravaHelpRoute
import com.ridecomp.feature.ride.ui.ActivityProfileEditorRoute
import com.ridecomp.feature.ride.ui.ActivityProfilesRoute
import com.ridecomp.feature.ride.ui.DataPagesEditorRoute
import com.ridecomp.feature.ride.ui.HomeRoute
import com.ridecomp.feature.ride.ui.PreRideRoute
import com.ridecomp.feature.ride.ui.RideRoute
import com.ridecomp.feature.ride.ui.SummaryRoute
import com.ridecomp.feature.routes.ui.RouteDetailRoute
import com.ridecomp.feature.routes.ui.RoutesRoute
import com.ridecomp.feature.sensors.ui.SensorsRoute
import com.ridecomp.feature.settings.ui.BackupRoute
import com.ridecomp.feature.settings.ui.BatteryHelpRoute
import com.ridecomp.feature.settings.ui.OnboardingRoute
import com.ridecomp.feature.settings.ui.ProfileRoute
import com.ridecomp.feature.settings.ui.RideSettingsRoute
import com.ridecomp.feature.settings.ui.SafetyRoute
import com.ridecomp.feature.settings.ui.SettingsRoute
import com.ridecomp.feature.settings.ui.SettingsSection

/** Routes that are not bottom-bar tabs, in one place. */
object AppRoutes {
    const val ONBOARDING = "onboarding"
    const val ARG_ROUTE_ID = "routeId"
    const val PRE_RIDE = "pre_ride?$ARG_ROUTE_ID={$ARG_ROUTE_ID}"

    /** Pre-ride with a route to follow (T3.6). */
    fun preRide(routeId: Long? = null): String = if (routeId == null) "pre_ride" else "pre_ride?$ARG_ROUTE_ID=$routeId"
    const val RIDE = "ride"
    const val SUMMARY = "summary"
    const val PROFILE = "profile"
    const val RIDE_SETTINGS = "ride_settings"
    const val BATTERY_HELP = "battery_help"
    const val ARG_PROFILE_ID = "profileId"
    const val DATA_PAGES = "data_pages?$ARG_PROFILE_ID={$ARG_PROFILE_ID}"
    const val ACTIVITY_PROFILES = "activity_profiles"
    const val SAFETY = "safety"
    const val BACKUP = "backup"
    const val ROUTE_DETAIL = "route/{$ARG_ROUTE_ID}"

    fun routeDetail(routeId: Long): String = "route/$routeId"
    const val ACTIVITY_PROFILE = "activity_profile/{$ARG_PROFILE_ID}"

    fun dataPages(profileId: Long? = null): String =
        if (profileId == null) "data_pages" else "data_pages?$ARG_PROFILE_ID=$profileId"

    fun activityProfile(profileId: Long): String = "activity_profile/$profileId"
    const val STRAVA_HELP = "strava_help"
    const val DEVELOPER = "developer"
    const val ARG_ACTIVITY_ID = "activityId"
    const val ACTIVITY_DETAIL = "activity/{$ARG_ACTIVITY_ID}"

    const val NO_ACTIVITY = -1L

    /** Missing optional id argument. */
    const val NO_ID = -1L

    fun activityDetail(activityId: Long): String = "activity/$activityId"

    /** Full-screen routes where the bottom bar is hidden (ride flow, onboarding). */
    val FULL_SCREEN: Set<String> = setOf(ONBOARDING, PRE_RIDE, RIDE, SUMMARY)
}

private const val PACKAGE_SCHEME = "package:"

/**
 * App navigation graph. Feature screens only expose callbacks; navigation decisions live here
 * (ARCH-07: ViewModels and screens never hold a NavController).
 */
@Composable
fun RideCompNavHost(navController: NavHostController, startDestination: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    NavHost(navController = navController, startDestination = startDestination, modifier = modifier) {
        composable(AppRoutes.ONBOARDING) {
            OnboardingRoute(
                onDone = { navController.navigateClearingBackStack(TopLevelDestination.HOME.route) },
                onOpenSensors = { navController.navigate(TopLevelDestination.SENSORS.route) },
            )
        }
        rideGraph(navController)
        historyGraph(navController)
        composable(TopLevelDestination.ROUTES.route) {
            val incoming by IncomingGpx.uri.collectAsStateWithLifecycle()
            RoutesRoute(
                onOpenRoute = { navController.navigate(AppRoutes.routeDetail(it)) },
                incomingUri = incoming,
                onIncomingHandled = IncomingGpx::consumed,
            )
        }
        composable(
            AppRoutes.ROUTE_DETAIL,
            arguments = listOf(navArgument(AppRoutes.ARG_ROUTE_ID) { type = NavType.LongType }),
        ) { entry ->
            RouteDetailRoute(
                routeId = entry.arguments?.getLong(AppRoutes.ARG_ROUTE_ID) ?: AppRoutes.NO_ID,
                onOpenRoute = {
                    navController.navigate(AppRoutes.routeDetail(it)) {
                        popUpTo(AppRoutes.ROUTE_DETAIL) {
                            inclusive =
                                true
                        }
                    }
                },
                onDeleted = { navController.popBackStack() },
                onRideRoute = { navController.navigate(AppRoutes.preRide(it)) },
            )
        }
        composable(TopLevelDestination.SENSORS.route) {
            SensorsRoute()
        }
        composable(TopLevelDestination.SETTINGS.route) {
            SettingsRoute(
                onSectionClick = { section -> openSection(section, navController, context) },
                isDeveloperMenuVisible = DevTools.IS_DEVELOPER_MENU_AVAILABLE,
            )
        }
        composable(AppRoutes.PROFILE) { ProfileRoute(onSaved = { navController.popBackStack() }) }
        composable(AppRoutes.RIDE_SETTINGS) { RideSettingsRoute() }
        composable(AppRoutes.BATTERY_HELP) { BatteryHelpRoute() }
        composable(
            AppRoutes.DATA_PAGES,
            arguments = listOf(optionalIdArgument(AppRoutes.ARG_PROFILE_ID)),
        ) { entry ->
            DataPagesEditorRoute(
                onDone = { navController.popBackStack() },
                profileId = entry.optionalId(AppRoutes.ARG_PROFILE_ID),
            )
        }
        composable(AppRoutes.SAFETY) { SafetyRoute(onBack = { navController.popBackStack() }) }
        composable(AppRoutes.BACKUP) { BackupRoute(onBack = { navController.popBackStack() }) }
        composable(AppRoutes.ACTIVITY_PROFILES) {
            ActivityProfilesRoute(onOpen = { navController.navigate(AppRoutes.activityProfile(it)) })
        }
        composable(
            AppRoutes.ACTIVITY_PROFILE,
            arguments = listOf(navArgument(AppRoutes.ARG_PROFILE_ID) { type = NavType.LongType }),
        ) { entry ->
            val profileId = entry.arguments?.getLong(AppRoutes.ARG_PROFILE_ID) ?: AppRoutes.NO_ID
            ActivityProfileEditorRoute(
                profileId = profileId,
                onEditPages = { navController.navigate(AppRoutes.dataPages(it)) },
            )
        }
        composable(AppRoutes.DEVELOPER) { DevTools.DeveloperScreen() }
    }
}

private fun NavGraphBuilder.rideGraph(navController: NavHostController) {
    composable(TopLevelDestination.HOME.route) {
        HomeRoute(
            onOpenPreRide = { navController.navigate(AppRoutes.preRide()) },
            onOpenRide = { navController.navigate(AppRoutes.RIDE) { launchSingleTop = true } },
            onOpenBackup = { navController.navigate(AppRoutes.BACKUP) },
        )
    }
    preRideDestination(navController)
    composable(AppRoutes.RIDE) {
        RideRoute(
            onFinished = {
                navController.navigate(AppRoutes.SUMMARY) { popUpTo(AppRoutes.RIDE) { inclusive = true } }
            },
            onClosed = { navController.navigateClearingBackStack(TopLevelDestination.HOME.route) },
        )
    }
    composable(AppRoutes.SUMMARY) {
        SummaryRoute(
            onSaved = { activityId ->
                navController.navigateClearingBackStack(TopLevelDestination.HISTORY.route)
                navController.navigate(AppRoutes.activityDetail(activityId))
            },
            onDiscarded = { navController.navigateClearingBackStack(TopLevelDestination.HOME.route) },
            onStravaHelp = { navController.navigate(AppRoutes.STRAVA_HELP) },
        )
    }
}

private fun NavGraphBuilder.historyGraph(navController: NavHostController) {
    composable(TopLevelDestination.HISTORY.route) {
        HistoryRoute(
            onStartRide = { navController.navigate(AppRoutes.preRide()) },
            onOpenActivity = { id -> navController.navigate(AppRoutes.activityDetail(id)) },
            onRestore = { navController.navigate(AppRoutes.BACKUP) },
        )
    }
    composable(
        AppRoutes.ACTIVITY_DETAIL,
        arguments = listOf(navArgument(AppRoutes.ARG_ACTIVITY_ID) { type = NavType.LongType }),
    ) { entry ->
        ActivityDetailRoute(
            activityId = entry.arguments?.getLong(AppRoutes.ARG_ACTIVITY_ID) ?: AppRoutes.NO_ACTIVITY,
            onDeleted = { navController.popBackStack() },
            onOpenStravaHelp = { navController.navigate(AppRoutes.STRAVA_HELP) },
            onBack = { navController.popBackStack() },
        )
    }
    composable(AppRoutes.STRAVA_HELP) { StravaHelpRoute() }
}

private fun openSection(section: SettingsSection, navController: NavHostController, context: Context) {
    when (section) {
        SettingsSection.PROFILE -> navController.navigate(AppRoutes.PROFILE)
        SettingsSection.RIDE_SCREEN_BATTERY, SettingsSection.AUTO_PAUSE, SettingsSection.REMINDERS ->
            navController.navigate(AppRoutes.RIDE_SETTINGS)
        SettingsSection.BATTERY_HELP -> navController.navigate(AppRoutes.BATTERY_HELP)
        SettingsSection.DEVELOPER -> navController.navigate(AppRoutes.DEVELOPER)
        SettingsSection.LANGUAGE -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.startActivity(
                Intent(Settings.ACTION_APP_LOCALE_SETTINGS, (PACKAGE_SCHEME + context.packageName).toUri()),
            )
        }
        SettingsSection.DATA_PAGES -> navController.navigate(AppRoutes.dataPages())
        SettingsSection.ACTIVITY_PROFILES -> navController.navigate(AppRoutes.ACTIVITY_PROFILES)
        SettingsSection.SAFETY -> navController.navigate(AppRoutes.SAFETY)
        SettingsSection.BACKUP -> navController.navigate(AppRoutes.BACKUP)
        SettingsSection.OFFLINE_MAPS -> Unit
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

/** Navigates to [route] and removes everything before it (end of a flow). */
fun NavHostController.navigateClearingBackStack(route: String) {
    navigate(route) {
        popUpTo(graph.id) { inclusive = true }
        launchSingleTop = true
    }
}

/** Pre-ride check, optionally with a route to follow (T3.6). */
private fun NavGraphBuilder.preRideDestination(navController: NavHostController) {
    composable(AppRoutes.PRE_RIDE, arguments = listOf(optionalIdArgument(AppRoutes.ARG_ROUTE_ID))) { entry ->
        PreRideRoute(
            routeId = entry.optionalId(AppRoutes.ARG_ROUTE_ID),
            onCancel = { navController.popBackStack() },
            onChooseRoute = { navController.navigateToTopLevel(TopLevelDestination.ROUTES) },
            onStarted = {
                navController.navigate(AppRoutes.RIDE) {
                    popUpTo(AppRoutes.PRE_RIDE) { inclusive = true }
                }
            },
        )
    }
}

/** Optional Long id argument (`?name={name}`); absent = [AppRoutes.NO_ID]. */
private fun optionalIdArgument(name: String) = navArgument(name) {
    type = NavType.LongType
    defaultValue = AppRoutes.NO_ID
}

/** The optional id argument, or null when absent. */
private fun NavBackStackEntry.optionalId(name: String): Long? =
    arguments?.getLong(name)?.takeIf { it != AppRoutes.NO_ID }
