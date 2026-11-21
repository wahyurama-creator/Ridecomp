package com.ridecomp.app.navigation

import android.os.Bundle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.ridecomp.app.DevTools
import com.ridecomp.core.model.GuideTip
import com.ridecomp.core.model.GuideTopic
import com.ridecomp.core.model.PlanRef
import com.ridecomp.feature.history.ui.ActivityDetailRoute
import com.ridecomp.feature.history.ui.HistoryRoute
import com.ridecomp.feature.history.ui.StravaHelpRoute
import com.ridecomp.feature.ride.ui.ActivityProfileEditorRoute
import com.ridecomp.feature.ride.ui.ActivityProfilesRoute
import com.ridecomp.feature.ride.ui.BikeEditorRoute
import com.ridecomp.feature.ride.ui.BikesRoute
import com.ridecomp.feature.ride.ui.ComponentEditorRoute
import com.ridecomp.feature.ride.ui.DataPagesEditorRoute
import com.ridecomp.feature.ride.ui.HomeRoute
import com.ridecomp.feature.ride.ui.OnboardingBikeForm
import com.ridecomp.feature.ride.ui.PreRideRoute
import com.ridecomp.feature.ride.ui.RideRoute
import com.ridecomp.feature.ride.ui.SummaryRoute
import com.ridecomp.feature.ride.viewmodel.BikeTarget
import com.ridecomp.feature.ride.viewmodel.ComponentTarget
import com.ridecomp.feature.routes.ui.RouteDetailRoute
import com.ridecomp.feature.routes.ui.RoutesRoute
import com.ridecomp.feature.sensors.ui.SensorsRoute
import com.ridecomp.feature.settings.ui.AppUpdateRoute
import com.ridecomp.feature.settings.ui.BackupRoute
import com.ridecomp.feature.settings.ui.BatteryHelpRoute
import com.ridecomp.feature.settings.ui.DisplayRoute
import com.ridecomp.feature.settings.ui.GuideTip
import com.ridecomp.feature.settings.ui.HelpRoute
import com.ridecomp.feature.settings.ui.HelpTopicScreen
import com.ridecomp.feature.settings.ui.LanguageRoute
import com.ridecomp.feature.settings.ui.OfflineMapsRoute
import com.ridecomp.feature.settings.ui.OnboardingRoute
import com.ridecomp.feature.settings.ui.OssLicensesRoute
import com.ridecomp.feature.settings.ui.ProfileRoute
import com.ridecomp.feature.settings.ui.RideSettingsRoute
import com.ridecomp.feature.settings.ui.SafetyRoute
import com.ridecomp.feature.settings.ui.SettingsRoute
import com.ridecomp.feature.settings.ui.SettingsSection
import com.ridecomp.feature.settings.ui.StravaRoute
import com.ridecomp.feature.training.model.PlanRefArg
import com.ridecomp.feature.training.ui.NewRecordsCard
import com.ridecomp.feature.training.ui.PlanBuilderRoute
import com.ridecomp.feature.training.ui.PlanDetailRoute
import com.ridecomp.feature.training.ui.PlansRoute
import com.ridecomp.feature.training.ui.RecordsRoute
import com.ridecomp.feature.training.ui.RideResultsCard
import com.ridecomp.feature.training.ui.SegmentDetailRoute
import com.ridecomp.feature.training.ui.SegmentEditorRoute
import com.ridecomp.feature.training.ui.SegmentsRoute
import com.ridecomp.feature.training.ui.ShareCardRoute
import com.ridecomp.feature.training.ui.TrainingRoute

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
    const val OFFLINE_MAPS = "offline_maps"
    const val LICENSES = "licenses"
    const val APP_UPDATE = "app_update"
    const val STRAVA = "strava"
    const val LANGUAGE = "language"
    const val DISPLAY = "display"
    const val HELP = "help"
    const val ARG_TOPIC = "topic"
    const val HELP_TOPIC = "help/{$ARG_TOPIC}"

    fun helpTopic(topic: GuideTopic): String = "help/${topic.name}"
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

    const val ARG_SEGMENT_ID = "segmentId"
    const val SEGMENT_DETAIL = "segment/{$ARG_SEGMENT_ID}"
    const val ARG_START_MS = "startMs"
    const val ARG_END_MS = "endMs"
    const val NO_WINDOW = -1L
    const val NEW_SEGMENT = "segment_new/{$ARG_ACTIVITY_ID}?$ARG_START_MS={$ARG_START_MS}&$ARG_END_MS={$ARG_END_MS}"

    fun segmentDetail(segmentId: Long): String = "segment/$segmentId"

    /** The segment editor, on a best effort's [windowMs] when given ("mark as sprint", T11.8). */
    fun newSegment(activityId: Long, windowMs: LongRange? = null): String = if (windowMs == null) {
        "segment_new/$activityId"
    } else {
        "segment_new/$activityId?$ARG_START_MS=${windowMs.first}&$ARG_END_MS=${windowMs.last}"
    }

    const val SHARE_CARD = "share/{$ARG_ACTIVITY_ID}"

    fun shareCard(activityId: Long): String = "share/$activityId"

    /** Full-screen routes where the bottom bar is hidden (ride flow, onboarding). */
    val FULL_SCREEN: Set<String> = setOf(ONBOARDING, PRE_RIDE, RIDE, SUMMARY)
}

/**
 * App navigation graph. Feature screens only expose callbacks; navigation decisions live here
 * (ARCH-07: ViewModels and screens never hold a NavController).
 */
@Composable
fun RideCompNavHost(navController: NavHostController, startDestination: String, modifier: Modifier = Modifier) {
    NavHost(navController = navController, startDestination = startDestination, modifier = modifier) {
        composable(AppRoutes.ONBOARDING) {
            OnboardingRoute(
                onDone = { navController.navigateClearingBackStack(TopLevelDestination.HOME.route) },
                onOpenSensors = { navController.navigate(TopLevelDestination.SENSORS.route) },
                bikeStep = { header, onSaved, stepModifier ->
                    OnboardingBikeForm(header = header, onSaved = onSaved, modifier = stepModifier)
                },
            )
        }
        rideGraph(navController)
        bikeGraph(navController)
        planGraph(navController)
        historyGraph(navController)
        segmentGraph(navController)
        composable(TopLevelDestination.ROUTES.route) {
            val incoming by IncomingGpx.uri.collectAsStateWithLifecycle()
            RoutesRoute(
                onOpenRoute = { navController.navigate(AppRoutes.routeDetail(it)) },
                incomingUri = incoming,
                onIncomingHandled = IncomingGpx::consumed,
                tipContent = {
                    GuideTip(GuideTip.ROUTES, onLearnMore = { navController.navigate(AppRoutes.helpTopic(it)) })
                },
            )
        }
        composable(
            AppRoutes.ROUTE_DETAIL,
            arguments = listOf(navArgument(AppRoutes.ARG_ROUTE_ID) { type = NavType.LongType }),
        ) { entry ->
            RouteDetailRoute(
                routeId = entry.arguments?.getLong(AppRoutes.ARG_ROUTE_ID) ?: AppRoutes.NO_ID,
                onOpenOfflineMaps = { navController.navigate(AppRoutes.OFFLINE_MAPS) },
                onBack = { navController.popBackStack() },
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
            SensorsRoute(
                onOpenWatchGuide = { navController.navigate(AppRoutes.helpTopic(GuideTopic.WATCHES)) },
                tipContent = {
                    GuideTip(GuideTip.SENSORS, onLearnMore = { navController.navigate(AppRoutes.helpTopic(it)) })
                },
            )
        }
        composable(TopLevelDestination.SETTINGS.route) {
            SettingsRoute(
                onSectionClick = { section -> openSection(section, navController) },
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
        composable(AppRoutes.OFFLINE_MAPS) {
            OfflineMapsRoute(
                onBack = { navController.popBackStack() },
                tipContent = {
                    GuideTip(GuideTip.OFFLINE_MAPS, onLearnMore = { navController.navigate(AppRoutes.helpTopic(it)) })
                },
            )
        }
        composable(AppRoutes.LICENSES) { OssLicensesRoute(onBack = { navController.popBackStack() }) }
        composable(AppRoutes.APP_UPDATE) { AppUpdateRoute(onBack = { navController.popBackStack() }) }
        composable(AppRoutes.LANGUAGE) { LanguageRoute(onBack = { navController.popBackStack() }) }
        composable(AppRoutes.DISPLAY) { DisplayRoute(onBack = { navController.popBackStack() }) }
        composable(AppRoutes.STRAVA) {
            val redirect by IncomingStravaAuth.redirect.collectAsStateWithLifecycle()
            StravaRoute(
                onBack = { navController.popBackStack() },
                redirect = redirect,
                onRedirectHandled = IncomingStravaAuth::consumed,
            )
        }
        composable(AppRoutes.BACKUP) {
            BackupRoute(
                onBack = { navController.popBackStack() },
                tipContent = {
                    GuideTip(GuideTip.BACKUP, onLearnMore = { navController.navigate(AppRoutes.helpTopic(it)) })
                },
            )
        }
        composable(AppRoutes.HELP) {
            HelpRoute(
                onOpenTopic = { navController.navigate(AppRoutes.helpTopic(it)) },
                onBack = { navController.popBackStack() },
            )
        }
        composable(
            AppRoutes.HELP_TOPIC,
            arguments = listOf(navArgument(AppRoutes.ARG_TOPIC) { type = NavType.StringType }),
        ) { entry ->
            val topic = GuideTopic.entries.firstOrNull { it.name == entry.arguments?.getString(AppRoutes.ARG_TOPIC) }
            if (topic != null) HelpTopicScreen(topic, onBack = { navController.popBackStack() })
        }
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
            tipContent = {
                GuideTip(GuideTip.FIRST_RIDE, onLearnMore = { navController.navigate(AppRoutes.helpTopic(it)) })
            },
            onOpenSensors = { navController.navigateToTopLevel(TopLevelDestination.SENSORS) },
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
            newRecordsContent = { activityId -> NewRecordsCard(activityId) },
        )
    }
}

private fun NavGraphBuilder.historyGraph(navController: NavHostController) {
    composable(TopLevelDestination.HISTORY.route) {
        HistoryRoute(
            onStartRide = { navController.navigate(AppRoutes.preRide()) },
            onOpenActivity = { id -> navController.navigate(AppRoutes.activityDetail(id)) },
            onRestore = { navController.navigate(AppRoutes.BACKUP) },
            recordsContent = {
                RecordsRoute(onOpenActivity = { id -> navController.navigate(AppRoutes.activityDetail(id)) })
            },
            segmentsContent = {
                SegmentsRoute(onOpenSegment = { id -> navController.navigate(AppRoutes.segmentDetail(id)) })
            },
            trainingContent = { TrainingTab(navController) },
        )
    }
    composable(
        AppRoutes.ACTIVITY_DETAIL,
        arguments = listOf(navArgument(AppRoutes.ARG_ACTIVITY_ID) { type = NavType.LongType }),
    ) { entry ->
        ActivityDetail(entry.arguments?.getLong(AppRoutes.ARG_ACTIVITY_ID) ?: AppRoutes.NO_ACTIVITY, navController)
    }
}

/** Ride detail with its links: export help, segment, share card, Strava, sensors, profile and results (T11.7). */
@Composable
private fun ActivityDetail(id: Long, navController: NavHostController) {
    ActivityDetailRoute(
        activityId = id,
        onDeleted = { navController.popBackStack() },
        onOpenStravaHelp = { navController.navigate(AppRoutes.STRAVA_HELP) },
        onOpenSensors = { navController.navigateToTopLevel(TopLevelDestination.SENSORS) },
        onOpenProfile = { navController.navigate(AppRoutes.PROFILE) },
        onBack = { navController.popBackStack() },
        tipContent = {
            GuideTip(GuideTip.EXPORT, onLearnMore = { navController.navigate(AppRoutes.helpTopic(it)) })
        },
        onCreateSegment = { navController.navigate(AppRoutes.newSegment(id)) },
        onShareCard = { navController.navigate(AppRoutes.shareCard(id)) },
        onOpenStravaSettings = { navController.navigate(AppRoutes.STRAVA) },
        resultsContent = {
            RideResultsCard(
                id,
                onOpenSegment = { segmentId -> navController.navigate(AppRoutes.segmentDetail(segmentId)) },
                onMakeSegment = { windowMs -> navController.navigate(AppRoutes.newSegment(id, windowMs)) },
            )
        },
    )
}

/** The segment editor: from a ride's detail, or on a best effort's stretch (T11.8). */
private fun NavGraphBuilder.newSegmentGraph(navController: NavHostController) {
    composable(
        AppRoutes.NEW_SEGMENT,
        arguments = listOf(
            navArgument(AppRoutes.ARG_ACTIVITY_ID) { type = NavType.LongType },
            windowArgument(AppRoutes.ARG_START_MS),
            windowArgument(AppRoutes.ARG_END_MS),
        ),
    ) { entry ->
        SegmentEditorRoute(
            activityId = entry.arguments?.getLong(AppRoutes.ARG_ACTIVITY_ID) ?: AppRoutes.NO_ACTIVITY,
            windowMs = entry.arguments?.windowMs(),
            onCreated = { id ->
                navController.popBackStack()
                navController.navigate(AppRoutes.segmentDetail(id))
            },
            onBack = { navController.popBackStack() },
        )
    }
}

/** An optional best effort time bound of the segment editor (T11.8). */
private fun windowArgument(name: String) = navArgument(name) {
    type = NavType.LongType
    defaultValue = AppRoutes.NO_WINDOW
}

private fun Bundle.windowMs(): LongRange? {
    val startMs = getLong(AppRoutes.ARG_START_MS, AppRoutes.NO_WINDOW)
    val endMs = getLong(AppRoutes.ARG_END_MS, AppRoutes.NO_WINDOW)
    return (startMs..endMs).takeIf { startMs != AppRoutes.NO_WINDOW && endMs > startMs }
}

private fun openSection(section: SettingsSection, navController: NavHostController) {
    navController.navigate(section.route())
}

/** The screen of a settings entry. */
private fun SettingsSection.route(): String = when (this) {
    SettingsSection.PROFILE -> AppRoutes.PROFILE
    SettingsSection.BIKES, SettingsSection.ACTIVITY_PROFILES, SettingsSection.DATA_PAGES -> rideSetupRoute()
    SettingsSection.RIDE_SCREEN_BATTERY, SettingsSection.AUTO_PAUSE, SettingsSection.REMINDERS ->
        AppRoutes.RIDE_SETTINGS
    SettingsSection.BATTERY_HELP -> AppRoutes.BATTERY_HELP
    SettingsSection.DEVELOPER -> AppRoutes.DEVELOPER
    SettingsSection.DISPLAY, SettingsSection.LANGUAGE, SettingsSection.BACKUP, SettingsSection.STRAVA -> appRoute()
    SettingsSection.SAFETY -> AppRoutes.SAFETY
    SettingsSection.OFFLINE_MAPS -> AppRoutes.OFFLINE_MAPS
    SettingsSection.OPEN_SOURCE_LICENSES -> AppRoutes.LICENSES
    SettingsSection.APP_UPDATE -> AppRoutes.APP_UPDATE
    SettingsSection.HELP -> AppRoutes.HELP
}

/** Display, language, backup and Strava: app-wide screens. */
private fun SettingsSection.appRoute(): String = when {
    this == SettingsSection.DISPLAY -> AppRoutes.DISPLAY
    this == SettingsSection.LANGUAGE -> AppRoutes.LANGUAGE
    this == SettingsSection.BACKUP -> AppRoutes.BACKUP
    else -> AppRoutes.STRAVA
}

/** Bikes, activity profiles and data pages: setup screens of the ride feature. */
private fun SettingsSection.rideSetupRoute(): String = when {
    this == SettingsSection.BIKES -> BikeRoutes.BIKES
    this == SettingsSection.DATA_PAGES -> AppRoutes.dataPages()
    else -> AppRoutes.ACTIVITY_PROFILES
}

/** Saved bikes and their parts (D-89, T7.3). */
object BikeRoutes {
    const val BIKES = "bikes"
    const val NEW_BIKE = "bike_new"
    const val ARG_BIKE_ID = "bikeId"
    const val BIKE = "bike/{$ARG_BIKE_ID}"
    const val ARG_COMPONENT_ID = "componentId"
    const val BIKE_COMPONENT = "bike_component/{$ARG_BIKE_ID}/{$ARG_COMPONENT_ID}"

    fun bike(bikeId: Long): String = "bike/$bikeId"

    /** A part of a bike; [componentId] null = a new part. */
    fun bikeComponent(bikeId: Long, componentId: Long?): String =
        "bike_component/$bikeId/${componentId ?: AppRoutes.NO_ID}"
}

/** Training tab of History with its plan card (T7.4). */
@Composable
private fun TrainingTab(navController: NavHostController) {
    TrainingRoute(
        onOpenPlans = { navController.navigate(PlanRoutes.PLANS) },
        onOpenPlan = { navController.navigate(PlanRoutes.plan(it)) },
    )
}

/** Training plans (T7.4). */
object PlanRoutes {
    const val PLANS = "plans"
    const val ARG_PLAN = "plan"
    const val PLAN = "plan/{$ARG_PLAN}"
    const val ARG_PLAN_ID = "planId"
    const val BUILDER = "plan_builder?$ARG_PLAN_ID={$ARG_PLAN_ID}"

    fun plan(ref: PlanRef): String = "plan/${PlanRefArg.encode(ref)}"

    fun builder(planId: Long? = null): String = if (planId ==
        null
    ) {
        "plan_builder"
    } else {
        "plan_builder?$ARG_PLAN_ID=$planId"
    }
}

/** Plan list, plan detail and the plan builder (T7.4). */
private fun NavGraphBuilder.planGraph(navController: NavHostController) {
    composable(PlanRoutes.PLANS) {
        PlansRoute(
            onOpenPlan = { navController.navigate(PlanRoutes.plan(it)) },
            onBuild = { navController.navigate(PlanRoutes.builder()) },
            onBack = { navController.popBackStack() },
        )
    }
    composable(PlanRoutes.PLAN, arguments = listOf(navArgument(PlanRoutes.ARG_PLAN) { type = NavType.StringType })) {
        val ref = PlanRefArg.decode(it.arguments?.getString(PlanRoutes.ARG_PLAN))
        if (ref != null) {
            PlanDetailRoute(
                ref = ref,
                onEdit = { id -> navController.navigate(PlanRoutes.builder(id)) },
                onBack = { navController.popBackStack() },
            )
        }
    }
    composable(PlanRoutes.BUILDER, arguments = listOf(optionalIdArgument(PlanRoutes.ARG_PLAN_ID))) { entry ->
        PlanBuilderRoute(
            planId = entry.optionalId(PlanRoutes.ARG_PLAN_ID),
            onSaved = { ref ->
                navController.navigate(PlanRoutes.plan(ref)) { popUpTo(PlanRoutes.BUILDER) { inclusive = true } }
            },
            onBack = { navController.popBackStack() },
        )
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
            onManageBikes = { navController.navigate(BikeRoutes.BIKES) },
            onOpenProfile = { navController.navigate(AppRoutes.PROFILE) },
            onStarted = {
                navController.navigate(AppRoutes.RIDE) {
                    popUpTo(AppRoutes.PRE_RIDE) { inclusive = true }
                }
            },
        )
    }
}

/** Saved bikes (D-89): list, new bike, edit bike. */
private fun NavGraphBuilder.bikeGraph(navController: NavHostController) {
    composable(BikeRoutes.BIKES) {
        BikesRoute(
            onOpen = { navController.navigate(BikeRoutes.bike(it)) },
            onAdd = { navController.navigate(BikeRoutes.NEW_BIKE) },
            onBack = { navController.popBackStack() },
        )
    }
    composable(BikeRoutes.NEW_BIKE) {
        BikeEditorRoute(target = BikeTarget.New, onDone = { navController.popBackStack() })
    }
    composable(
        BikeRoutes.BIKE,
        arguments = listOf(navArgument(BikeRoutes.ARG_BIKE_ID) { type = NavType.LongType }),
    ) { entry ->
        val bikeId = entry.arguments?.getLong(BikeRoutes.ARG_BIKE_ID) ?: AppRoutes.NO_ID
        BikeEditorRoute(
            target = BikeTarget.Existing(bikeId),
            onDone = { navController.popBackStack() },
            onOpenComponent = { bike, component -> navController.navigate(BikeRoutes.bikeComponent(bike, component)) },
        )
    }
    composable(
        BikeRoutes.BIKE_COMPONENT,
        arguments = listOf(
            navArgument(BikeRoutes.ARG_BIKE_ID) { type = NavType.LongType },
            navArgument(BikeRoutes.ARG_COMPONENT_ID) { type = NavType.LongType },
        ),
    ) { entry ->
        val bikeId = entry.arguments?.getLong(BikeRoutes.ARG_BIKE_ID) ?: AppRoutes.NO_ID
        val componentId = entry.arguments?.getLong(BikeRoutes.ARG_COMPONENT_ID)?.takeIf { it != AppRoutes.NO_ID }
        ComponentEditorRoute(
            target = ComponentTarget(bikeId, componentId),
            onDone = { navController.popBackStack() },
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

/** Segment detail and editor (T4.2), share card editor (T4.5). */
private fun NavGraphBuilder.segmentGraph(navController: NavHostController) {
    composable(
        AppRoutes.SHARE_CARD,
        arguments = listOf(navArgument(AppRoutes.ARG_ACTIVITY_ID) { type = NavType.LongType }),
    ) { entry ->
        ShareCardRoute(
            activityId = entry.arguments?.getLong(AppRoutes.ARG_ACTIVITY_ID) ?: AppRoutes.NO_ACTIVITY,
            onBack = { navController.popBackStack() },
        )
    }
    composable(
        AppRoutes.SEGMENT_DETAIL,
        arguments = listOf(navArgument(AppRoutes.ARG_SEGMENT_ID) { type = NavType.LongType }),
    ) { entry ->
        SegmentDetailRoute(
            segmentId = entry.arguments?.getLong(AppRoutes.ARG_SEGMENT_ID) ?: AppRoutes.NO_ID,
            onBack = { navController.popBackStack() },
            onOpenActivity = { id -> navController.navigate(AppRoutes.activityDetail(id)) },
        )
    }
    newSegmentGraph(navController)
    composable(AppRoutes.STRAVA_HELP) { StravaHelpRoute() }
}
