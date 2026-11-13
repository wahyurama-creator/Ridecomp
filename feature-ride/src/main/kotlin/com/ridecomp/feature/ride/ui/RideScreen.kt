package com.ridecomp.feature.ride.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ridecomp.core.engine.recording.BatteryModePolicy
import com.ridecomp.core.engine.recording.LiveRide
import com.ridecomp.core.engine.recording.RideAction
import com.ridecomp.core.engine.safety.IncidentAction
import com.ridecomp.core.engine.safety.IncidentState
import com.ridecomp.core.model.RideStatus
import com.ridecomp.core.util.UnitFormatter
import com.ridecomp.feature.ride.R
import com.ridecomp.feature.ride.model.DataPage
import com.ridecomp.feature.ride.model.DefaultDataPages
import com.ridecomp.feature.ride.model.MetricUnitLabels
import com.ridecomp.feature.ride.model.RideMetric
import com.ridecomp.feature.ride.model.RouteView
import com.ridecomp.feature.ride.model.rideAction
import com.ridecomp.feature.ride.ui.component.BreadcrumbMap
import com.ridecomp.feature.ride.ui.component.ClimbPanel
import com.ridecomp.feature.ride.ui.component.IncidentAlertedBanner
import com.ridecomp.feature.ride.ui.component.IncidentCountdown
import com.ridecomp.feature.ride.ui.component.NextClimbInfo
import com.ridecomp.feature.ride.ui.component.RideBanners
import com.ridecomp.feature.ride.ui.component.RideControlBar
import com.ridecomp.feature.ride.ui.component.RideField
import com.ridecomp.feature.ride.ui.component.RideScreenModeEffect
import com.ridecomp.feature.ride.ui.component.RideStatusBar
import com.ridecomp.feature.ride.ui.component.RouteGuidanceBanners
import com.ridecomp.feature.ride.ui.component.SosButton
import com.ridecomp.feature.ride.ui.component.rememberMetricUnitLabels
import com.ridecomp.feature.ride.viewmodel.RideEffect
import com.ridecomp.feature.ride.viewmodel.RideViewModel
import com.ridecomp.ui.component.AlertBanner
import com.ridecomp.ui.component.AlertSeverity
import com.ridecomp.ui.component.BigButton
import com.ridecomp.ui.component.BigButtonStyle
import com.ridecomp.ui.component.ConfirmDialog
import com.ridecomp.ui.component.DataGrid
import com.ridecomp.ui.component.LineChart
import com.ridecomp.ui.component.StatRow
import com.ridecomp.ui.theme.RideCompDimens
import com.ridecomp.ui.theme.RideCompTheme
import com.ridecomp.ui.udf.ObserveEffects
import com.ridecomp.ui.util.volumeKeys
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
    route: RouteView? = null,
) {
    val incident = live.value.incident
    if (incident is IncidentState.Countdown) {
        IncidentCountdown(incident, live.value.nowMs, onIncident, modifier)
        return
    }
    val labels = rememberMetricUnitLabels()
    var areBarsVisible by rememberSaveable { mutableStateOf(true) }
    val status = live.value.status
    Column(modifier = modifier.fillMaxSize().background(RideCompTheme.colors.bg)) {
        if (areBarsVisible) {
            RideStatusBar(
                status = status,
                hasGpsFix = live.value.hasGpsFix,
                batteryMode = live.value.batteryMode,
                sensors = live.value.sensors.snapshot.states,
                isGpsEnabled = live.value.isGpsEnabled,
            )
        }
        RideBanners(live.value, onAction)
        live.value.route?.takeIf { it.isOffRoute }?.let { OffRouteBanner(it.distanceFromRouteM, formatter) }
        live.value.route?.let { RouteGuidanceBanners(it, formatter) }
        if (incident is IncidentState.Alerted) IncidentAlertedBanner(incident, onIncident)
        if (status == RideStatus.PAUSED) {
            PauseContent(live.value, formatter, onAction, Modifier.weight(1f)) {
                if (isSosAvailable) SosButton(onIncident)
            }
        } else {
            // With a route, the navigation page comes first (02-DESIGN §3.4 page 2, here first while following).
            val pageCount = pages.size + if (route != null) 1 else 0
            val pagerState = rememberPagerState { pageCount }
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f).pointerInput(Unit) {
                    detectTapGestures(onDoubleTap = { areBarsVisible = !areBarsVisible })
                },
            ) { index ->
                val pageIndex = if (route != null) index - 1 else index
                if (route != null && pageIndex < 0) {
                    NavigationPage(route, live, formatter, labels)
                } else {
                    DataPageContent(pages[pageIndex], live, formatter, labels, route)
                }
            }
        }
        if (areBarsVisible || status == RideStatus.PAUSED) RideControlBar(status, onAction)
    }
}

@Composable
private fun DataPageContent(
    page: DataPage,
    live: State<LiveRide>,
    formatter: UnitFormatter,
    labels: MetricUnitLabels,
    route: RouteView?,
) {
    // The climb page gets the route elevation profile with the rider's position (T3.9).
    val showsProfile = route?.hasElevation == true && RideMetric.GRADE in page.metrics
    Column(modifier = Modifier.fillMaxSize()) {
        DataGrid(
            count = page.metrics.size,
            isFirstFullWidth = page.isFirstFullWidth,
            modifier = Modifier.weight(1f),
        ) { index, cellModifier ->
            RideField(
                live = live,
                metric = page.metrics[index],
                formatter = formatter,
                labels = labels,
                modifier = cellModifier,
            )
        }
        if (showsProfile) ClimbSection(route, live, formatter)
    }
}

/** On a climb: the climb panel replaces the route profile (12-FEATURE-SPECS §6.2). */
@Composable
private fun ClimbSection(route: RouteView, live: State<LiveRide>, formatter: UnitFormatter) {
    val progress = live.value.route
    val climb = progress?.climb
    if (climb != null) {
        ClimbPanel(climb, formatter)
    } else {
        progress?.nextClimb?.let { NextClimbInfo(it, formatter) }
        RouteProfile(route, live)
    }
}

/** Navigation page (T3.5/T3.8): breadcrumb map plus distance to go and speed (02-DESIGN §3.4). */
@Composable
private fun NavigationPage(
    route: RouteView,
    live: State<LiveRide>,
    formatter: UnitFormatter,
    labels: MetricUnitLabels,
) {
    val current = live.value
    Column(modifier = Modifier.fillMaxSize()) {
        BreadcrumbMap(
            line = route.line,
            distanceAlongM = current.route?.distanceAlongM,
            latDeg = current.latDeg,
            lonDeg = current.lonDeg,
            modifier = Modifier.fillMaxWidth().weight(2f),
        )
        DataGrid(count = NAVIGATION_FIELDS.size, modifier = Modifier.weight(1f)) { index, cellModifier ->
            RideField(
                live = live,
                metric = NAVIGATION_FIELDS[index],
                formatter = formatter,
                labels = labels,
                modifier = cellModifier,
            )
        }
        if (route.hasElevation) RouteProfile(route, live)
    }
}

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

@Composable
private fun OffRouteBanner(distanceM: Double, formatter: UnitFormatter) {
    AlertBanner(
        message = stringResource(R.string.ride_off_route, formatter.distance(distanceM), formatter.distanceUnit),
        severity = AlertSeverity.DANGER,
    )
}

/** Fields under the breadcrumb (02-DESIGN §3.4: "peta + 2 field"). */
private val NAVIGATION_FIELDS = listOf(RideMetric.DISTANCE_REMAINING, RideMetric.SPEED)

/** Pause screen (02-DESIGN §3.5): running summary + RESUME, FINISH & SAVE, DISCARD (2-step). */
@Composable
private fun PauseContent(
    live: LiveRide,
    formatter: UnitFormatter,
    onAction: (RideAction) -> Unit,
    modifier: Modifier,
    extra: @Composable () -> Unit,
) {
    var isDiscardDialogShown by remember { mutableStateOf(false) }
    Column(
        modifier = modifier.fillMaxWidth().padding(RideCompDimens.spacingM),
        verticalArrangement = Arrangement.spacedBy(RideCompDimens.spacingS),
    ) {
        listOf(RideMetric.DISTANCE, RideMetric.MOVING_TIME, RideMetric.AVG_SPEED, RideMetric.ASCENT).forEach { metric ->
            StatRow(
                label = stringResource(metric.labelRes),
                value =
                metric.value(live, formatter) +
                    (metric.unit(formatter, rememberMetricUnitLabels())?.let { " $it" } ?: ""),
            )
        }
        BigButton(
            text = stringResource(R.string.ride_finish_save),
            onClick = { onAction(RideAction.FINISH) },
            style = BigButtonStyle.SECONDARY,
        )
        BigButton(
            text = stringResource(R.string.ride_discard),
            onClick = { isDiscardDialogShown = true },
            style = BigButtonStyle.DANGER,
        )
        extra()
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
