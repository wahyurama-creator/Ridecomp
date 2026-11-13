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
import com.ridecomp.feature.ride.model.rideAction
import com.ridecomp.feature.ride.ui.component.IncidentAlertedBanner
import com.ridecomp.feature.ride.ui.component.IncidentCountdown
import com.ridecomp.feature.ride.ui.component.RideBanners
import com.ridecomp.feature.ride.ui.component.RideControlBar
import com.ridecomp.feature.ride.ui.component.RideField
import com.ridecomp.feature.ride.ui.component.RideScreenModeEffect
import com.ridecomp.feature.ride.ui.component.RideStatusBar
import com.ridecomp.feature.ride.ui.component.SosButton
import com.ridecomp.feature.ride.ui.component.rememberMetricUnitLabels
import com.ridecomp.feature.ride.viewmodel.RideEffect
import com.ridecomp.feature.ride.viewmodel.RideViewModel
import com.ridecomp.ui.component.BigButton
import com.ridecomp.ui.component.BigButtonStyle
import com.ridecomp.ui.component.ConfirmDialog
import com.ridecomp.ui.component.DataGrid
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
        if (incident is IncidentState.Alerted) IncidentAlertedBanner(incident, onIncident)
        if (status == RideStatus.PAUSED) {
            PauseContent(live.value, formatter, onAction, Modifier.weight(1f)) {
                if (isSosAvailable) SosButton(onIncident)
            }
        } else {
            val pagerState = rememberPagerState { pages.size }
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f).pointerInput(Unit) {
                    detectTapGestures(onDoubleTap = { areBarsVisible = !areBarsVisible })
                },
            ) { index -> DataPageContent(pages[index], live, formatter, labels) }
        }
        if (areBarsVisible || status == RideStatus.PAUSED) RideControlBar(status, onAction)
    }
}

@Composable
private fun DataPageContent(page: DataPage, live: State<LiveRide>, formatter: UnitFormatter, labels: MetricUnitLabels) {
    DataGrid(count = page.metrics.size, isFirstFullWidth = page.isFirstFullWidth) { index, cellModifier ->
        RideField(
            live = live,
            metric = page.metrics[index],
            formatter = formatter,
            labels = labels,
            modifier = cellModifier,
        )
    }
}

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
