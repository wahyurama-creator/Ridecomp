package com.ridecomp.feature.ride.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.ridecomp.core.engine.recording.LiveRide
import com.ridecomp.core.engine.recording.ReminderType
import com.ridecomp.core.engine.recording.RideAction
import com.ridecomp.core.model.BatteryMode
import com.ridecomp.core.model.RideStatus
import com.ridecomp.core.util.UnitFormatter
import com.ridecomp.feature.ride.R
import com.ridecomp.feature.ride.model.MetricUnitLabels
import com.ridecomp.feature.ride.model.RideMetric
import com.ridecomp.ui.component.AlertBanner
import com.ridecomp.ui.component.AlertSeverity
import com.ridecomp.ui.component.BannerAction
import com.ridecomp.ui.component.BigButton
import com.ridecomp.ui.component.BigButtonStyle
import com.ridecomp.ui.component.DataField
import com.ridecomp.ui.component.DataFieldStatus
import com.ridecomp.ui.theme.RideCompDimens
import com.ridecomp.ui.theme.RideCompTheme

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

/**
 * One metric cell. The text is derived from the shared live state, so this field recomposes only when
 * its own text changes (UI-04), not on every 1 Hz update.
 */
@Composable
fun RideField(
    live: State<LiveRide>,
    metric: RideMetric,
    formatter: UnitFormatter,
    labels: MetricUnitLabels,
    modifier: Modifier = Modifier,
) {
    val text by remember(metric, formatter) { derivedStateOf { metric.value(live.value, formatter) } }
    val isLost by remember(metric) { derivedStateOf { metric.isSensorLost(live.value) } }
    DataField(
        label = stringResource(metric.labelRes),
        value = if (isLost) UnitFormatter.NO_VALUE else text,
        unit = metric.unit(formatter, labels),
        status = if (isLost) DataFieldStatus.WARNING else DataFieldStatus.NORMAL,
        modifier = modifier,
    )
}

/** Thin top bar: REC/PAUSE, GPS state, battery mode (02-DESIGN §3.4). */
@Composable
fun RideStatusBar(status: RideStatus, hasGpsFix: Boolean, batteryMode: BatteryMode, modifier: Modifier = Modifier) {
    val colors = RideCompTheme.colors
    val (label, color) = when (status) {
        RideStatus.RECORDING -> stringResource(R.string.ride_status_rec) to colors.danger
        RideStatus.PAUSED, RideStatus.AUTO_PAUSED, RideStatus.FINISHED, RideStatus.IDLE ->
            stringResource(R.string.ride_status_pause) to colors.warnText
    }
    Row(
        modifier = modifier.fillMaxWidth().height(
            RideCompDimens.rideTopBarHeight,
        ).padding(horizontal = RideCompDimens.spacingS),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, style = RideCompTheme.typography.dataLabel, color = color)
        Text(
            text = GPS_LABEL,
            style = RideCompTheme.typography.dataLabel,
            color = if (hasGpsFix) colors.ok else colors.danger,
        )
        val batteryColor = when (batteryMode) {
            BatteryMode.NORMAL -> colors.textSecondary
            BatteryMode.SAVER -> colors.warnText
            BatteryMode.CRITICAL, BatteryMode.EMERGENCY -> colors.danger
        }
        Text(text = BATTERY_LABEL, style = RideCompTheme.typography.dataLabel, color = batteryColor)
    }
}

/** Bottom controls: big PAUSE/RESUME left, LAP right. Stop only exists on the pause screen. */
@Composable
fun RideControlBar(status: RideStatus, onAction: (RideAction) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().background(RideCompTheme.colors.bg).padding(RideCompDimens.spacingS),
        horizontalArrangement = Arrangement.spacedBy(RideCompDimens.spacingS),
    ) {
        val isPaused = status == RideStatus.PAUSED
        BigButton(
            text = stringResource(if (isPaused) R.string.ride_resume else R.string.ride_pause),
            onClick = { onAction(if (isPaused) RideAction.RESUME else RideAction.PAUSE) },
            modifier = Modifier.weight(2f),
        )
        BigButton(
            text = stringResource(R.string.ride_lap),
            onClick = { onAction(RideAction.LAP) },
            style = BigButtonStyle.SECONDARY,
            isEnabled = status == RideStatus.RECORDING || status == RideStatus.AUTO_PAUSED,
            modifier = Modifier.weight(1f),
        )
    }
}

/** Non-blocking banners above the data (02-DESIGN §3.4, §3.11). At most the most important few show. */
@Composable
fun RideBanners(live: LiveRide, onAction: (RideAction) -> Unit) {
    if (live.status == RideStatus.RECORDING && !live.hasGpsFix) {
        AlertBanner(message = stringResource(R.string.ride_gps_lost), severity = AlertSeverity.DANGER)
    }
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
        AlertBanner(message = stringResource(R.string.ride_auto_paused), severity = AlertSeverity.WARN)
    }
    live.reminder?.let { reminder ->
        AlertBanner(
            message = stringResource(
                when (reminder.type) {
                    ReminderType.DRINK -> R.string.ride_reminder_drink
                    ReminderType.EAT -> R.string.ride_reminder_eat
                },
            ),
            actions = listOf(
                BannerAction(stringResource(R.string.ride_reminder_done)) { onAction(RideAction.ACK_REMINDER) },
                BannerAction(stringResource(R.string.ride_reminder_snooze)) { onAction(RideAction.SNOOZE_REMINDER) },
            ),
        )
    }
    BatteryBanner(live.batteryMode)
}

@Composable
private fun BatteryBanner(mode: BatteryMode) {
    when (mode) {
        BatteryMode.NORMAL -> Unit
        BatteryMode.SAVER ->
            AlertBanner(message = stringResource(R.string.ride_battery_saver), severity = AlertSeverity.WARN)
        BatteryMode.CRITICAL, BatteryMode.EMERGENCY ->
            AlertBanner(message = stringResource(R.string.ride_battery_critical), severity = AlertSeverity.DANGER)
    }
}

/** Short status symbols (not translated). */
private const val GPS_LABEL = "GPS"
private const val BATTERY_LABEL = "BAT"
