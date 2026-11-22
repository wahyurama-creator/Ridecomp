package com.ridecomp.feature.ride.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ridecomp.core.engine.map.BackToStart
import com.ridecomp.core.engine.recording.BatteryModePolicy
import com.ridecomp.core.engine.recording.LiveRide
import com.ridecomp.core.engine.recording.RideAction
import com.ridecomp.core.engine.route.RoutePoint
import com.ridecomp.core.engine.route.riddenUntil
import com.ridecomp.core.engine.safety.IncidentAction
import com.ridecomp.core.engine.safety.IncidentState
import com.ridecomp.core.engine.sensors.TrainerState
import com.ridecomp.core.model.BatteryMode
import com.ridecomp.core.model.RideStatus
import com.ridecomp.core.util.UnitFormatter
import com.ridecomp.feature.ride.R
import com.ridecomp.feature.ride.model.DataPage
import com.ridecomp.feature.ride.model.DefaultDataPages
import com.ridecomp.feature.ride.model.MetricUnitLabels
import com.ridecomp.feature.ride.model.RideMapView
import com.ridecomp.feature.ride.model.RideMetric
import com.ridecomp.feature.ride.model.RouteView
import com.ridecomp.feature.ride.model.rideAction
import com.ridecomp.feature.ride.model.withFallbacks
import com.ridecomp.feature.ride.ui.component.BreadcrumbMap
import com.ridecomp.feature.ride.ui.component.ClimbPage
import com.ridecomp.feature.ride.ui.component.ErgPresets
import com.ridecomp.feature.ride.ui.component.IncidentAlertedBanner
import com.ridecomp.feature.ride.ui.component.IncidentCountdown
import com.ridecomp.feature.ride.ui.component.NextClimbInfo
import com.ridecomp.feature.ride.ui.component.RideBanners
import com.ridecomp.feature.ride.ui.component.RideControlBar
import com.ridecomp.feature.ride.ui.component.RideField
import com.ridecomp.feature.ride.ui.component.RideScreenModeEffect
import com.ridecomp.feature.ride.ui.component.RideStatusBar
import com.ridecomp.feature.ride.ui.component.RouteGuidanceBanners
import com.ridecomp.feature.ride.ui.component.SegmentBanners
import com.ridecomp.feature.ride.ui.component.SegmentPage
import com.ridecomp.feature.ride.ui.component.SessionTargetBar
import com.ridecomp.feature.ride.ui.component.SosButton
import com.ridecomp.feature.ride.ui.component.TrainerAction
import com.ridecomp.feature.ride.ui.component.TrainerBar
import com.ridecomp.feature.ride.ui.component.rememberMetricUnitLabels
import com.ridecomp.feature.ride.viewmodel.RideEffect
import com.ridecomp.feature.ride.viewmodel.RideViewModel
import com.ridecomp.ui.R as UiR
import com.ridecomp.ui.component.AlertBanner
import com.ridecomp.ui.component.AlertSeverity
import com.ridecomp.ui.component.BigButton
import com.ridecomp.ui.component.BigButtonSize
import com.ridecomp.ui.component.BigButtonStyle
import com.ridecomp.ui.component.ConfirmDialog
import com.ridecomp.ui.component.DataGrid
import com.ridecomp.ui.component.IconSquareButton
import com.ridecomp.ui.component.LineChart
import com.ridecomp.ui.component.PageIndicator
import com.ridecomp.ui.component.StatCard
import com.ridecomp.ui.map.MapPoint
import com.ridecomp.ui.map.RouteMap
import com.ridecomp.ui.theme.RideCompDimens
import com.ridecomp.ui.theme.RideCompTheme
import com.ridecomp.ui.theme.WindowWidthClass
import com.ridecomp.ui.udf.ObserveEffects
import com.ridecomp.ui.util.volumeKeys
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import org.koin.androidx.compose.koinViewModel

/** Ride screen (T1.8): data pages, status bar, controls, banners, pause screen, screen mode. */
@Composable
fun RideRoute(
    onFinished: () -> Unit,
    onClosed: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RideViewModel = koinViewModel(),
) {
    val live = viewModel.liveRide.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val pages by viewModel.pages.collectAsStateWithLifecycle()
    val isSosAvailable by viewModel.isSosAvailable.collectAsStateWithLifecycle()
    val contactNames by viewModel.contactNames.collectAsStateWithLifecycle()
    val map by viewModel.map.collectAsStateWithLifecycle()
    val isBackToStart by viewModel.isBackToStart.collectAsStateWithLifecycle()
    val backToStart by viewModel.backToStart.collectAsStateWithLifecycle()
    val trainer by viewModel.trainer.collectAsStateWithLifecycle()
    val ergPresets by viewModel.ergPresets.collectAsStateWithLifecycle()
    val route by viewModel.route.collectAsStateWithLifecycle()
    ObserveEffects(viewModel.effects) { effect ->
        when (effect) {
            RideEffect.OpenSummary -> onFinished()
            RideEffect.Close -> onClosed()
        }
    }
    val formatter = remember(settings.unitSystem) { UnitFormatter(settings.unitSystem) }
    var interaction by remember { mutableIntStateOf(0) }
    val current = live.value
    RideScreenModeEffect(
        mode = BatteryModePolicy.effectiveScreenMode(settings.screenMode, current.batteryMode),
        wakeKey = listOf(
            interaction,
            current.status,
            current.reminder,
            current.lap?.number,
            current.isIdlePromptVisible,
            // An incident wakes and keeps the screen on (countdown, 12-FEATURE-SPECS §5.3).
            current.incident,
        ),
    )
    RideScreen(
        live = live,
        formatter = formatter,
        onAction = viewModel::onEvent,
        pages = pages,
        onIncident = viewModel::onIncident,
        isSosAvailable = isSosAvailable,
        contactNames = contactNames,
        map = map.copy(isBackToStart = isBackToStart, backToStart = backToStart),
        onToggleBackToStart = viewModel::toggleBackToStart,
        trainer = trainer,
        ergPresets = ergPresets,
        onTrainer = viewModel::onTrainer,
        route = route,
        modifier = modifier
            .volumeKeys(settings.isVolumeButtonsEnabled) { key ->
                interaction++
                key.rideAction(live.value.status)?.let(viewModel::onEvent)
            }
            .pointerInput(Unit) {
                // Any touch wakes a dimmed screen; the event is observed, not consumed.
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    interaction++
                }
            },
    )
}

@Composable
fun RideScreen(
    live: State<LiveRide>,
    formatter: UnitFormatter,
    onAction: (RideAction) -> Unit,
    modifier: Modifier = Modifier,
    pages: List<DataPage> = DefaultDataPages.ALL,
    onIncident: (IncidentAction) -> Unit = {},
    isSosAvailable: Boolean = false,
    contactNames: ImmutableList<String> = persistentListOf(),
    map: RideMapView = RideMapView(),
    route: RouteView? = null,
    onToggleBackToStart: () -> Unit = {},
    trainer: TrainerState = TrainerState(),
    ergPresets: ErgPresets? = null,
    onTrainer: (TrainerAction) -> Unit = {},
) {
    val incident = live.value.incident
    if (incident is IncidentState.Countdown) {
        IncidentCountdown(incident, live.value.nowMs, contactNames, onIncident, modifier)
        return
    }
    val labels = rememberMetricUnitLabels()
    // Double tap hides only the status strip for more room; PAUSE/LAP always stay (D-101, rider feedback).
    var isStatusBarVisible by rememberSaveable { mutableStateOf(true) }
    val status = live.value.status
    Column(modifier = modifier.fillMaxSize().background(RideCompTheme.colors.bg)) {
        if (isStatusBarVisible || status == RideStatus.PAUSED) RideStatusBar(live.value)
        if (incident is IncidentState.Alerted) IncidentAlertedBanner(incident, onIncident)
        if (status == RideStatus.PAUSED) {
            PauseContent(live.value, formatter, labels, onAction, Modifier.weight(1f)) {
                if (isSosAvailable) SosButton(onIncident)
            }
            return@Column
        }
        RideBanners(live.value, formatter, onAction)
        LapDoneBanner(live.value, formatter)
        GuidanceBanners(live.value, formatter)
        live.value.target?.let { SessionTargetBar(it, formatter) }
        if (trainer.isConnected) TrainerBar(trainer, ergPresets, onTrainer)
        // Every GPS ride has a map page (D-90); with a route it follows the route (T3.5).
        val hasMapPage = route != null || live.value.isGpsEnabled
        val screens = remember(pages, hasMapPage) { ridePages(pages, hasMapPage) }
        val pagerState = rememberPagerState { screens.size }
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f).pointerInput(Unit) {
                detectTapGestures(onDoubleTap = { isStatusBarVisible = !isStatusBarVisible })
            },
        ) { index ->
            when (val screen = screens[index]) {
                RidePage.Navigation -> NavigationPage(route, map, live, formatter, labels, onToggleBackToStart)
                is RidePage.Data ->
                    DataPageContent(screen.page, live, formatter, labels, route, isMain = screen.page == pages.first())
            }
        }
        if (screens.size > 1) {
            PageIndicator(
                count = screens.size,
                current = pagerState.currentPage,
                description = stringResource(R.string.ride_page_indicator, pagerState.currentPage + 1, screens.size),
            )
        }
        RideControlBar(status, onAction)
    }
}

/** A swipeable page of the ride screen. */
private sealed interface RidePage {
    data class Data(val page: DataPage) : RidePage

    data object Navigation : RidePage
}

/**
 * Page order Utama · Peta · Tanjakan · Lap (13-UI-SCREENS §2 PageIndicator, 02-DESIGN §3.4): the map
 * page comes second, after the first data page.
 */
private fun ridePages(pages: List<DataPage>, hasMapPage: Boolean): List<RidePage> {
    val data = pages.map { RidePage.Data(it) }
    if (!hasMapPage) return data
    return data.take(1) + RidePage.Navigation + data.drop(1)
}

@Composable
private fun DataPageContent(
    page: DataPage,
    live: State<LiveRide>,
    formatter: UnitFormatter,
    labels: MetricUnitLabels,
    route: RouteView?,
    isMain: Boolean,
) {
    // While riding a segment its page replaces the main page (13-UI-SCREENS #17, 12-FEATURE-SPECS §9.3).
    val segment = live.value.segment?.active.takeIf { isMain }
    if (segment != null) {
        SegmentPage(segment, live.value.sensors.heartRateBpm, live.value.speedMps, formatter)
        return
    }
    // The climb page gets the route elevation profile with the rider's position (T3.9).
    val showsProfile = route?.hasElevation == true && RideMetric.GRADE in page.metrics
    val isLapPage = RideMetric.LAP_TIME in page.metrics
    // On a climb the climb page replaces the grid (13-UI-SCREENS #8, 12-FEATURE-SPECS §6.2).
    val climb = live.value.route?.climb.takeIf { showsProfile }
    if (climb != null) {
        ClimbPage(climb, live.value.elevation.gradePercent, formatter)
        return
    }
    val sensorsInUse = live.value.sensors.snapshot.states.keys
    val metrics = remember(page.metrics, sensorsInUse) { page.metrics.withFallbacks(sensorsInUse) }
    Column(modifier = Modifier.fillMaxSize()) {
        if (isLapPage) LapHeader(live, formatter)
        DataGrid(
            count = metrics.size,
            isFirstFullWidth = page.isFirstFullWidth,
            firstRowWeight = HERO_ROW_WEIGHT,
            modifier = Modifier.weight(1f),
        ) { index, cellModifier ->
            RideField(
                live = live,
                metric = metrics[index],
                formatter = formatter,
                labels = labels,
                isHero = index == 0 && page.isFirstFullWidth,
                modifier = cellModifier,
            )
        }
        if (isLapPage) LapProjection(live, formatter)
        if (showsProfile) ClimbSection(route, live, formatter)
    }
}

/** On a climb: the climb panel replaces the route profile (12-FEATURE-SPECS §6.2). */
@Composable
private fun ClimbSection(route: RouteView, live: State<LiveRide>, formatter: UnitFormatter) {
    val progress = live.value.route
    progress?.nextClimb?.let { NextClimbInfo(it, formatter) }
    RouteProfile(route, live)
}

/**
 * Map page (T3.5/T3.8, D-90): the map while there is one (online or offline file), else the breadcrumb;
 * with a route: the route, distance to go and speed; without: the line ridden so far, distance and speed.
 * "Ke start" (T7.2) shows the way back along that line, the start and the distance and direction to it.
 */
@Composable
private fun NavigationPage(
    route: RouteView?,
    map: RideMapView,
    live: State<LiveRide>,
    formatter: UnitFormatter,
    labels: MetricUnitLabels,
    onToggleBackToStart: () -> Unit,
) {
    val current = live.value
    // Battery saver turns the tile map into the breadcrumb (13-UI-SCREENS #14, 12-FEATURE-SPECS §3).
    val isTileMap = map.hasMap && current.batteryMode == BatteryMode.NORMAL
    val followsRoute = route != null && !map.isBackToStart
    Column(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
            if (isTileMap) {
                RideMap(route.takeIf { followsRoute }, map, live, Modifier.fillMaxSize())
            } else {
                BreadcrumbMap(
                    line = if (followsRoute) route.line else map.trail,
                    // Without a route the whole line is drawn as "ahead" (accent) so it stands out.
                    distanceAlongM = if (followsRoute) current.route?.distanceAlongM else 0.0,
                    latDeg = current.latDeg,
                    lonDeg = current.lonDeg,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            BackToStartButton(
                isOn = map.isBackToStart,
                onClick = onToggleBackToStart,
                modifier = Modifier.align(Alignment.TopStart).padding(RideCompDimens.spacingSm),
            )
        }
        if (map.isBackToStart) {
            BackToStartBar(map.backToStart, formatter)
        } else {
            // Two fixed-height fields side by side, the map takes the rest (13-UI-SCREENS #7); the elevation
            // profile lives on the climb page.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(RideCompDimens.navigationFieldHeight)
                    .background(RideCompTheme.colors.divider),
                horizontalArrangement = Arrangement.spacedBy(RideCompDimens.dividerThickness),
            ) {
                (if (route != null) NAVIGATION_FIELDS else FREE_RIDE_MAP_FIELDS).forEach { metric ->
                    RideField(
                        live = live,
                        metric = metric,
                        formatter = formatter,
                        labels = labels,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

/**
 * Tile map of the ride (T3.5, D-90; 13-UI-SCREENS #7): route and ridden part, or on a ride without a
 * route the line ridden so far; position, north-up. Panning stops following; "follow" brings it back.
 */
@Composable
private fun RideMap(route: RouteView?, map: RideMapView, live: State<LiveRide>, modifier: Modifier) {
    var isFollowing by rememberSaveable { mutableStateOf(true) }
    // Back to start (T7.2): the ridden line becomes the way back, in accent, with the start marked.
    val line = remember(route, map.isBackToStart, map.trail) {
        if (map.isBackToStart) map.trail.toMapPoints() else route?.line.orEmpty().toMapPoints()
    }
    val along by remember { derivedStateOf { live.value.route?.distanceAlongM } }
    val ridden = remember(route, along, map.trail, map.isBackToStart) {
        when {
            map.isBackToStart -> persistentListOf()
            route != null -> route.line.riddenUntil(along ?: 0.0).toMapPoints()
            else -> map.trail.toMapPoints()
        }
    }
    val start = map.backToStart?.start?.let { MapPoint(it.latDeg, it.lonDeg) }
    val lat = live.value.latDeg
    val lon = live.value.lonDeg
    val position = if (lat != null && lon != null) MapPoint(lat, lon) else null
    Box(modifier = modifier) {
        RouteMap(
            isOnline = map.isOnline,
            isCached = map.isCached,
            regionPaths = map.regionPaths,
            route = line,
            ridden = ridden,
            position = position,
            isFollowing = isFollowing,
            onUserMoved = { isFollowing = false },
            marker = start.takeIf { map.isBackToStart },
            description = stringResource(R.string.breadcrumb_description),
            modifier = Modifier.fillMaxSize(),
            // Side drags pan the map; the pages still turn with a swipe on the fields below it.
            isDragExclusive = true,
        )
        if (!isFollowing) {
            IconSquareButton(
                iconRes = UiR.drawable.ic_my_location,
                contentDescription = stringResource(R.string.ride_map_follow),
                onClick = { isFollowing = true },
                tint = RideCompTheme.colors.textPrimary,
                modifier = Modifier.align(Alignment.BottomEnd).padding(RideCompDimens.spacingSm),
            )
        }
    }
}

/** "Ke start" / "Tutup" over the map (T7.2). */
@Composable
private fun BackToStartButton(isOn: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = RideCompTheme.colors
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(RideCompDimens.cornerRadiusSmall),
        color = if (isOn) colors.accent else colors.surface,
        contentColor = if (isOn) colors.onAccent else colors.textPrimary,
        modifier = modifier.heightIn(min = RideCompDimens.touchTargetMin),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = RideCompDimens.spacingSm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(RideCompDimens.spacingS),
        ) {
            Icon(
                painter = painterResource(if (isOn) UiR.drawable.ic_close else UiR.drawable.ic_flag),
                contentDescription = null,
                modifier = Modifier.size(RideCompDimens.iconSize),
            )
            Text(
                text = stringResource(if (isOn) R.string.ride_back_close else R.string.ride_back_to_start),
                style = RideCompTheme.typography.bodyStrong,
            )
        }
    }
}

/** Distance back along the ridden line, direction arrow (north-up map) and straight-line distance (T7.2). */
@Composable
private fun BackToStartBar(back: BackToStart?, formatter: UnitFormatter) {
    val colors = RideCompTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(RideCompDimens.navigationFieldHeight)
            .background(colors.bg)
            .padding(horizontal = RideCompDimens.spacingM),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(RideCompDimens.spacingM),
    ) {
        when {
            back == null -> Text(
                text = stringResource(R.string.ride_back_waiting),
                style = RideCompTheme.typography.bodyStrong,
                color = colors.textSecondary,
            )
            back.isArrived -> Text(
                text = stringResource(R.string.ride_back_arrived),
                style = RideCompTheme.typography.cardTitle,
                color = colors.ok,
            )
            else -> {
                BackDistance(stringResource(R.string.ride_back_along), back.alongTrailM, formatter, Modifier.weight(1f))
                Icon(
                    painter = painterResource(UiR.drawable.ic_navigation),
                    contentDescription = stringResource(R.string.ride_back_direction),
                    tint = colors.accent,
                    modifier = Modifier.size(RideCompDimens.touchTargetMin).rotate(back.bearingDeg.toFloat()),
                )
                BackDistance(
                    stringResource(R.string.ride_back_straight),
                    back.straightM,
                    formatter,
                    Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun BackDistance(label: String, distanceM: Double, formatter: UnitFormatter, modifier: Modifier) {
    Column(modifier = modifier) {
        Text(
            text = label.uppercase(),
            style = RideCompTheme.typography.dataLabel,
            color = RideCompTheme.colors.textSecondary,
        )
        Text(
            text = stringResource(R.string.ride_back_distance, formatter.distance(distanceM), formatter.distanceUnit),
            style = RideCompTheme.typography.title,
            color = RideCompTheme.colors.textPrimary,
        )
    }
}

private fun List<RoutePoint>.toMapPoints(): ImmutableList<MapPoint> =
    map { MapPoint(it.latDeg, it.lonDeg) }.toImmutableList()

@Composable
private fun RouteProfile(route: RouteView, live: State<LiveRide>) {
    LineChart(
        title = stringResource(R.string.metric_remaining_ascent),
        values = route.elevation,
        color = RideCompTheme.colors.track,
        markerX = live.value.route?.distanceAlongM?.div(route.distanceDivisor),
        modifier = Modifier.padding(horizontal = RideCompDimens.spacingS),
    )
}

/**
 * "Lap 1 done · 2:04 · 0.72 km" for the first seconds of the next lap: the LAP button (or an auto lap) gets
 * a visible answer on every page, not only on the lap page (novice test, D-86).
 */
@Composable
private fun LapDoneBanner(live: LiveRide, formatter: UnitFormatter) {
    val lap = live.lap?.takeIf { it.elapsedMs <= LAP_BANNER_MS } ?: return
    val previousMs = lap.previousLapMs
    val previousM = lap.previousLapDistanceM
    if (previousMs != null && previousM != null) LapDoneMessage(lap.number - 1, previousMs, previousM, formatter)
}

@Composable
private fun LapDoneMessage(number: Int, previousMs: Long, previousM: Double, formatter: UnitFormatter) {
    AlertBanner(
        message = stringResource(
            R.string.ride_lap_done,
            number,
            formatter.duration(previousMs),
            formatter.distance(previousM),
            formatter.distanceUnit,
        ),
        severity = AlertSeverity.OK,
    )
}

private const val LAP_BANNER_MS = 4_000L

/** Off route, turn and climb (route) and live segment banners. */
@Composable
private fun GuidanceBanners(live: LiveRide, formatter: UnitFormatter) {
    live.route?.takeIf { it.isOffRoute }?.let { OffRouteBanner(it.distanceFromRouteM, formatter) }
    live.route?.let { RouteGuidanceBanners(it, formatter) }
    live.segment?.let { SegmentBanners(it, formatter) }
}

@Composable
private fun OffRouteBanner(distanceM: Double, formatter: UnitFormatter) {
    AlertBanner(
        message = stringResource(R.string.ride_off_route, formatter.distance(distanceM), formatter.distanceUnit),
        severity = AlertSeverity.DANGER,
    )
}

/** Fields under the breadcrumb (02-DESIGN §3.4: "peta + 2 field"). */
private val NAVIGATION_FIELDS = listOf(RideMetric.DISTANCE_REMAINING, RideMetric.SPEED)
private val FREE_RIDE_MAP_FIELDS = listOf(RideMetric.DISTANCE, RideMetric.SPEED)

/** Height of a page's hero row relative to the other rows (13-UI-SCREENS #6). */
private const val HERO_ROW_WEIGHT = 1.5f

/** Lap page header (13-UI-SCREENS #25): "LAP 3" and how laps are cut. */
@Composable
private fun LapHeader(live: State<LiveRide>, formatter: UnitFormatter) {
    val lap = live.value.lap ?: return
    Row(
        modifier = Modifier.fillMaxWidth().padding(
            horizontal = RideCompDimens.spacingM,
            vertical = RideCompDimens.spacingS,
        ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.ride_lap_title, lap.number),
            style = RideCompTheme.typography.title,
            color = RideCompTheme.colors.accent,
            modifier = Modifier.weight(1f),
        )
        Text(
            text =
            lap.autoLapM?.let {
                stringResource(R.string.ride_lap_auto, formatter.distance(it), formatter.distanceUnit)
            }
                ?: stringResource(R.string.ride_lap_manual),
            style = RideCompTheme.typography.caption,
            color = RideCompTheme.colors.textSecondary,
        )
    }
}

/** Previous lap vs the running lap's projection (13-UI-SCREENS #25); projection for distance auto-laps only. */
@Composable
private fun LapProjection(live: State<LiveRide>, formatter: UnitFormatter) {
    val lap = live.value.lap ?: return
    val previousMs = lap.previousLapMs ?: return
    val colors = RideCompTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth().padding(
            horizontal = RideCompDimens.spacingM,
            vertical = RideCompDimens.spacingS,
        ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(
                    R.string.ride_lap_previous,
                    lap.number - 1,
                    formatter.distance(lap.previousLapDistanceM),
                    formatter.distanceUnit,
                ),
                style = RideCompTheme.typography.dataLabel,
                color = colors.textSecondary,
            )
            Text(
                text = formatter.duration(previousMs),
                style = RideCompTheme.typography.statValue,
                color = colors.textPrimary,
            )
        }
        val projected = lap.projectedLapMs
        if (projected != null) {
            val delta = lap.projectedDeltaMs
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = stringResource(R.string.ride_lap_projection, lap.number),
                    style = RideCompTheme.typography.dataLabel,
                    color = colors.textSecondary,
                )
                Text(
                    text = delta?.let {
                        stringResource(
                            R.string.ride_lap_projection_value,
                            formatter.duration(projected),
                            formatter.signedDuration(it),
                        )
                    } ?: formatter.duration(projected),
                    style = RideCompTheme.typography.statValue,
                    // Faster is green, slower orange; the sign says it too (not color alone).
                    color = when {
                        delta == null -> colors.textPrimary
                        delta <= 0 -> colors.ok
                        else -> colors.accent
                    },
                )
            }
        }
    }
}

/**
 * Pause screen (02-DESIGN §3.5, 13-UI-SCREENS #9): moving time, four summary cards, SOS, LANJUTKAN,
 * SELESAI & SIMPAN and "Buang ride" (2-step). Stop exists only here.
 */
@Composable
private fun PauseContent(
    live: LiveRide,
    formatter: UnitFormatter,
    labels: MetricUnitLabels,
    onAction: (RideAction) -> Unit,
    modifier: Modifier,
    sos: @Composable () -> Unit,
) {
    var isDiscardDialogShown by remember { mutableStateOf(false) }
    val colors = RideCompTheme.colors
    // Buttons sit at the bottom like the design; the whole screen still scrolls on short phones.
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .heightIn(min = maxHeight)
                .padding(horizontal = RideCompDimens.screenPaddingHorizontal, vertical = RideCompDimens.blockGap),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            PauseSummary(live, formatter, labels)
            PauseActions(onAction, onDiscard = { isDiscardDialogShown = true }, sos = sos)
        }
    }
    if (isDiscardDialogShown) {
        ConfirmDialog(
            title = stringResource(R.string.ride_discard_title),
            message = stringResource(R.string.ride_discard_message),
            confirmLabel = stringResource(R.string.ride_discard),
            dismissLabel = stringResource(R.string.ride_cancel),
            onConfirm = {
                isDiscardDialogShown = false
                onAction(RideAction.DISCARD)
            },
            onDismiss = { isDiscardDialogShown = false },
        )
    }
}

@Composable
private fun PauseSummary(live: LiveRide, formatter: UnitFormatter, labels: MetricUnitLabels) {
    val colors = RideCompTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(RideCompDimens.blockGap)) {
        Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = stringResource(R.string.ride_paused_label),
                style = RideCompTheme.typography.cardTitle,
                color = colors.warnText,
            )
            Text(
                text = formatter.duration(live.movingMs),
                style = RideCompTheme.typography.hugeNumber.copy(fontSize = RideCompTheme.dimens.pauseTimeSize),
                color = colors.textPrimary,
            )
            Text(
                text = stringResource(R.string.ride_moving_total, formatter.duration(live.elapsedMs)),
                style = RideCompTheme.typography.body,
                color = colors.textSecondary,
            )
        }
        PAUSE_STATS.withFallbacks(live.sensors.snapshot.states.keys).chunked(PAUSE_STATS_COLUMNS).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(RideCompDimens.cardGap)) {
                row.forEach { metric ->
                    StatCard(
                        label = stringResource(metric.labelRes),
                        value = metric.value(live, formatter),
                        unit = metric.unit(formatter, labels),
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun PauseActions(onAction: (RideAction) -> Unit, onDiscard: () -> Unit, sos: @Composable () -> Unit) {
    Column(
        modifier = Modifier.padding(top = RideCompDimens.blockGap),
        verticalArrangement = Arrangement.spacedBy(RideCompDimens.spacingSm),
    ) {
        sos()
        BigButton(
            text = stringResource(R.string.ride_resume),
            onClick = { onAction(RideAction.RESUME) },
            // Below 360 dp the primary height (still ≥ 72 dp, §4.4) keeps FINISH on screen (T13.4).
            size = when (RideCompTheme.widthClass) {
                WindowWidthClass.COMPACT -> BigButtonSize.PRIMARY
                WindowWidthClass.REGULAR -> BigButtonSize.HERO
            },
            iconRes = UiR.drawable.ic_play,
        )
        BigButton(
            text = stringResource(R.string.ride_finish_save),
            onClick = { onAction(RideAction.FINISH) },
            style = BigButtonStyle.OUTLINE,
        )
        BigButton(
            text = stringResource(R.string.ride_discard),
            onClick = onDiscard,
            style = BigButtonStyle.TEXT_DANGER,
        )
        Text(
            text = stringResource(R.string.ride_discard_note),
            style = RideCompTheme.typography.caption,
            color = RideCompTheme.colors.textSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** Cards of the pause screen (13-UI-SCREENS #9), two per row. */
private val PAUSE_STATS =
    listOf(RideMetric.DISTANCE, RideMetric.AVG_SPEED, RideMetric.ASCENT, RideMetric.AVG_HEART_RATE)
private const val PAUSE_STATS_COLUMNS = 2
