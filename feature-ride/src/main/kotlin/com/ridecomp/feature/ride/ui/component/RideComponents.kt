package com.ridecomp.feature.ride.ui.component

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import com.ridecomp.core.engine.recording.LiveRide
import com.ridecomp.core.engine.recording.ReminderType
import com.ridecomp.core.engine.recording.RideAction
import com.ridecomp.core.engine.sensors.SensorAlert
import com.ridecomp.core.engine.sensors.SensorAlertType
import com.ridecomp.core.engine.sensors.SensorKind
import com.ridecomp.core.engine.sensors.SensorLimits
import com.ridecomp.core.engine.sensors.SensorState
import com.ridecomp.core.engine.sensors.alerts
import com.ridecomp.core.model.BatteryMode
import com.ridecomp.core.model.RideStatus
import com.ridecomp.core.util.UnitFormatter
import com.ridecomp.feature.ride.R
import com.ridecomp.feature.ride.model.MetricUnitLabels
import com.ridecomp.feature.ride.model.RideMetric
import com.ridecomp.ui.R as UiR
import com.ridecomp.ui.component.AlertBanner
import com.ridecomp.ui.component.AlertSeverity
import com.ridecomp.ui.component.BannerAction
import com.ridecomp.ui.component.DataField
import com.ridecomp.ui.component.DataFieldStatus
import com.ridecomp.ui.component.RecordingIndicator
import com.ridecomp.ui.component.RideControlButtons
import com.ridecomp.ui.component.RideStatusStrip
import com.ridecomp.ui.component.StatusIcon
import com.ridecomp.ui.component.StatusTone
import com.ridecomp.ui.component.TagTone
import com.ridecomp.ui.theme.RideCompDimens
import com.ridecomp.ui.theme.RideCompTheme
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.collections.immutable.toImmutableList

/** Resource-based unit labels, resolved once per composition. */
@Composable
fun rememberMetricUnitLabels(): MetricUnitLabels = MetricUnitLabels(
    bpm = stringResource(R.string.unit_bpm),
    rpm = stringResource(R.string.unit_rpm),
    watt = stringResource(R.string.unit_watt),
    percent = stringResource(R.string.unit_percent),
    wattsPerKg = stringResource(R.string.unit_watts_per_kg),
    kcal = stringResource(R.string.unit_kcal),
)

/** Small tag on the right of a field's label (13-UI-SCREENS §2 DataField). */
private enum class FieldTag(@param:StringRes val textRes: Int, val tone: TagTone) {
    LOST(R.string.ride_tag_lost, TagTone.WARN),
    ESTIMATE(R.string.ride_tag_estimate, TagTone.WARN),
    STOPPED(R.string.ride_tag_stopped, TagTone.WARN),
    RUNNING(R.string.ride_tag_running, TagTone.NORMAL),
}

private fun RideMetric.tag(live: LiveRide, isLost: Boolean): FieldTag? {
    val isAutoPaused = live.status == RideStatus.AUTO_PAUSED
    return when {
        isLost -> FieldTag.LOST
        this == RideMetric.ESTIMATED_POWER -> FieldTag.ESTIMATE
        isAutoPaused && this == RideMetric.MOVING_TIME -> FieldTag.STOPPED
        isAutoPaused && this == RideMetric.ELAPSED_TIME -> FieldTag.RUNNING
        else -> null
    }
}

/** Live values that stop while not moving: grey while paused (13-UI-SCREENS #14). */
private val MOVING_METRICS = setOf(RideMetric.SPEED)

/**
 * One metric cell. Text, tag and state are derived from the shared live state, so this field recomposes
 * only when they change (UI-04), not on every 1 Hz update.
 */
@Composable
fun RideField(
    live: State<LiveRide>,
    metric: RideMetric,
    formatter: UnitFormatter,
    labels: MetricUnitLabels,
    modifier: Modifier = Modifier,
    isHero: Boolean = false,
) {
    val text by remember(metric, formatter) { derivedStateOf { metric.value(live.value, formatter) } }
    val isLost by remember(metric) { derivedStateOf { metric.isSensorLost(live.value) } }
    val tag by remember(metric) { derivedStateOf { metric.tag(live.value, isLost) } }
    val zone by remember(metric) {
        derivedStateOf { live.value.sensors.heartRateZone.takeIf { metric == RideMetric.HEART_RATE } }
    }
    val isStopped by remember(metric) {
        derivedStateOf { metric in MOVING_METRICS && live.value.status != RideStatus.RECORDING }
    }
    val tagText = tag?.let { stringResource(it.textRes) } ?: zone?.let { stringResource(R.string.ride_tag_zone, it) }
    DataField(
        label = stringResource(metric.labelRes),
        value = if (isLost) UnitFormatter.NO_VALUE else text,
        unit = metric.unit(formatter, labels),
        status = when {
            isLost -> DataFieldStatus.WARNING
            isStopped -> DataFieldStatus.INACTIVE
            else -> DataFieldStatus.NORMAL
        },
        tag = tagText,
        tagTone = tag?.tone ?: TagTone.WARN,
        isHero = isHero,
        modifier = modifier,
    )
}

private val CLOCK_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

/** RideStatusStrip with the ride's clock, GPS and sensor icons, phone battery and REC/JEDA (13-UI-SCREENS §2). */
@Composable
fun RideStatusBar(live: LiveRide, modifier: Modifier = Modifier, zone: ZoneId = ZoneId.systemDefault()) {
    val gpsLabel = stringResource(R.string.ride_status_gps)
    val icons = buildList {
        if (live.isGpsEnabled) {
            add(
                StatusIcon(
                    UiR.drawable.ic_status_gps,
                    if (live.hasGpsFix) StatusTone.OK else StatusTone.DANGER,
                    gpsLabel,
                ),
            )
        }
        SensorKind.entries.filter { it in live.sensors.snapshot.states }.forEach { kind ->
            val state = live.sensors.snapshot.states.getValue(kind)
            add(StatusIcon(kind.statusIcon(), state.statusTone(), stringResource(kind.nameRes())))
        }
    }.toImmutableList()
    val isRecording = live.status == RideStatus.RECORDING
    RideStatusStrip(
        clock = CLOCK_FORMAT.format(Instant.ofEpochMilli(live.nowMs).atZone(zone)),
        icons = icons,
        battery = live.phoneBatteryPercent?.let { stringResource(R.string.ride_status_battery, it) }
            ?: UnitFormatter.NO_VALUE,
        isBatteryWarning = live.batteryMode != BatteryMode.NORMAL,
        recording = if (isRecording) RecordingIndicator.REC else RecordingIndicator.PAUSED,
        recordingLabel = stringResource(if (isRecording) R.string.ride_status_rec else R.string.ride_status_pause),
        modifier = modifier,
    )
}

/** Bottom controls: PAUSE fills the width, LAP is a 120 dp outline. Stop only exists on the pause screen. */
@Composable
fun RideControlBar(status: RideStatus, onAction: (RideAction) -> Unit, modifier: Modifier = Modifier) {
    val canLap = status == RideStatus.RECORDING || status == RideStatus.AUTO_PAUSED
    RideControlButtons(
        primaryLabel = stringResource(R.string.ride_pause),
        primaryIconRes = UiR.drawable.ic_pause,
        onPrimary = { onAction(RideAction.PAUSE) },
        secondaryLabel = stringResource(R.string.ride_lap),
        onSecondary = { if (canLap) onAction(RideAction.LAP) },
        modifier = modifier.padding(top = RideCompDimens.spacingS),
    )
}

/**
 * Non-blocking banners above the data (02-DESIGN §3.4, §3.11; 13-UI-SCREENS #14, #15, #30). The battery
 * saver strip comes first, then problems, then reminders.
 */
@Composable
fun RideBanners(live: LiveRide, formatter: UnitFormatter, onAction: (RideAction) -> Unit) {
    BatteryBanner(live.batteryMode)
    if (live.isGpsEnabled && live.status == RideStatus.RECORDING && !live.hasGpsFix) {
        AlertBanner(
            message = stringResource(R.string.ride_gps_lost),
            severity = AlertSeverity.DANGER,
            iconRes = UiR.drawable.ic_warning,
        )
    }
    SensorBanners(live.sensors.snapshot.alerts())
    if (live.isIdlePromptVisible) {
        AlertBanner(
            message = stringResource(R.string.ride_idle_prompt),
            severity = AlertSeverity.WARN,
            actions = listOf(
                BannerAction(stringResource(R.string.ride_idle_save)) { onAction(RideAction.FINISH) },
                BannerAction(stringResource(R.string.ride_idle_continue)) { onAction(RideAction.KEEP_RIDING) },
            ),
        )
    } else if (live.status == RideStatus.AUTO_PAUSED) {
        AutoPauseBlock(stillFor = formatter.duration(live.pausedSinceMs?.let { live.nowMs - it }))
    }
    live.reminder?.let { reminder ->
        val isDrink = reminder.type == ReminderType.DRINK
        AlertBanner(
            title = stringResource(if (isDrink) R.string.ride_reminder_drink else R.string.ride_reminder_eat),
            message = stringResource(
                if (isDrink) R.string.ride_reminder_drink_detail else R.string.ride_reminder_eat_detail,
            ),
            iconRes = if (isDrink) UiR.drawable.ic_bottle else UiR.drawable.ic_bell,
            actions = listOf(
                BannerAction(stringResource(R.string.ride_reminder_done)) { onAction(RideAction.ACK_REMINDER) },
                BannerAction(stringResource(R.string.ride_reminder_snooze)) { onAction(RideAction.SNOOZE_REMINDER) },
            ),
        )
    }
}

/** Large yellow block while auto-paused (13-UI-SCREENS #14): cannot be closed, goes away when moving. */
@Composable
private fun AutoPauseBlock(stillFor: String) {
    val colors = RideCompTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = RideCompDimens.spacingSm, vertical = RideCompDimens.spacingXs)
            .clip(RoundedCornerShape(RideCompDimens.cornerRadiusButton))
            .background(colors.warn)
            .padding(horizontal = RideCompDimens.spacingM, vertical = RideCompDimens.spacingM)
            .semantics { liveRegion = LiveRegionMode.Polite },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(RideCompDimens.spacingXs),
    ) {
        Text(
            text = stringResource(R.string.ride_auto_paused),
            style = RideCompTheme.typography.title,
            color = colors.onWarn,
        )
        Text(
            text = stringResource(R.string.ride_auto_paused_detail, stillFor),
            style = RideCompTheme.typography.bodyStrong,
            color = colors.onWarn,
        )
    }
}

@Composable
private fun BatteryBanner(mode: BatteryMode) {
    when (mode) {
        BatteryMode.NORMAL -> Unit
        BatteryMode.SAVER -> AlertBanner(
            message = stringResource(R.string.ride_battery_saver),
            severity = AlertSeverity.SAVER,
            iconRes = UiR.drawable.ic_battery,
        )
        BatteryMode.CRITICAL, BatteryMode.EMERGENCY -> AlertBanner(
            message = stringResource(R.string.ride_battery_critical),
            severity = AlertSeverity.DANGER,
            iconRes = UiR.drawable.ic_battery,
        )
    }
}

/** One banner per sensor problem (T2.9). Lost = warning while it reconnects; low battery = info. */
@Composable
private fun SensorBanners(alerts: List<SensorAlert>) {
    alerts.forEach { alert ->
        val sensorName = stringResource(alert.kind.nameRes())
        when (alert.type) {
            SensorAlertType.LOST -> AlertBanner(
                title = stringResource(R.string.ride_sensor_lost_title, sensorName),
                message = stringResource(R.string.ride_sensor_lost),
                severity = AlertSeverity.WARN,
                iconRes = UiR.drawable.ic_bluetooth_off,
            )
            SensorAlertType.LOW_BATTERY -> AlertBanner(
                message = stringResource(R.string.ride_sensor_battery_low, sensorName, alert.batteryPercent ?: 0),
                severity = AlertSeverity.INFO,
                iconRes = UiR.drawable.ic_battery,
            )
        }
    }
}

@StringRes
private fun SensorKind.nameRes(): Int = when (this) {
    SensorKind.HEART_RATE -> R.string.ride_sensor_hr
    SensorKind.SPEED_CADENCE -> R.string.ride_sensor_csc
    SensorKind.POWER -> R.string.ride_sensor_power
}

private fun SensorKind.statusIcon(): Int = when (this) {
    SensorKind.HEART_RATE -> UiR.drawable.ic_status_heart
    SensorKind.SPEED_CADENCE -> UiR.drawable.ic_status_cadence
    SensorKind.POWER -> UiR.drawable.ic_power
}

/** Lost or low battery shows yellow (13-UI-SCREENS #30): the ride goes on. */
private fun SensorState.statusTone(): StatusTone {
    val battery = batteryPercent
    return when {
        !isConnected -> StatusTone.WARN
        battery != null && battery < SensorLimits.LOW_BATTERY_PERCENT -> StatusTone.WARN
        else -> StatusTone.OK
    }
}
