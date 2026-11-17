package com.ridecomp.feature.ride.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import com.ridecomp.core.engine.training.TargetSource
import com.ridecomp.core.engine.training.TargetStatus
import com.ridecomp.core.engine.training.ZoneState
import com.ridecomp.core.model.Intensity
import com.ridecomp.core.model.UnitSystem
import com.ridecomp.core.util.UnitFormatter
import com.ridecomp.feature.ride.R
import com.ridecomp.feature.ride.model.feelRes
import com.ridecomp.ui.preview.RideCompPreview
import com.ridecomp.ui.preview.ThemePreviews
import com.ridecomp.ui.theme.RideCompDimens
import com.ridecomp.ui.theme.RideCompTheme

/**
 * Today's planned session during the ride (T7.4b, D-94): "Z2 · 128 bpm · di zona" with the target range
 * (heart rate, power, or how it should feel without sensors) and the time toward the planned duration.
 */
@Composable
fun SessionTargetBar(target: TargetStatus, formatter: UnitFormatter, modifier: Modifier = Modifier) {
    val colors = RideCompTheme.colors
    val color = target.stateColor()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.surface)
            .padding(horizontal = RideCompDimens.spacingM, vertical = RideCompDimens.spacingS),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(RideCompDimens.spacingSm),
    ) {
        Text(
            text = stringResource(R.string.session_zone_badge, target.intensity.zone),
            style = RideCompTheme.typography.cardTitle,
            color = color,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = headline(target),
                style = RideCompTheme.typography.bodyStrong,
                color = color,
            )
            Text(
                text = range(target),
                style = RideCompTheme.typography.caption,
                color = colors.textSecondary,
            )
        }
        Text(
            text = if (target.isDone) {
                stringResource(R.string.session_done)
            } else {
                stringResource(
                    R.string.session_time,
                    formatter.duration(target.doneMs),
                    formatter.duration(target.durationMs),
                )
            },
            style = RideCompTheme.typography.bodyStrong,
            color = if (target.isDone) colors.ok else colors.textPrimary,
        )
    }
}

@Composable
private fun TargetStatus.stateColor(): Color = when {
    source == TargetSource.FEEL -> RideCompTheme.colors.textPrimary
    state == ZoneState.IN -> RideCompTheme.colors.ok
    state == ZoneState.ABOVE -> RideCompTheme.colors.accent
    else -> RideCompTheme.colors.textSecondary
}

/** "128 bpm · di zona", or how it should feel. */
@Composable
private fun headline(target: TargetStatus): String {
    val current = target.current
    if (target.source == TargetSource.FEEL || current == null) return stringResource(target.intensity.feelRes())
    val unit = stringResource(if (target.source == TargetSource.HEART_RATE) R.string.unit_bpm else R.string.unit_watt)
    val state = stringResource(
        when (target.state) {
            ZoneState.IN -> R.string.session_in_zone
            ZoneState.ABOVE -> R.string.session_above
            ZoneState.BELOW -> R.string.session_below
            ZoneState.UNKNOWN -> R.string.session_no_reading
        },
    )
    return stringResource(R.string.session_current, current, unit, state)
}

/** "Target 114–133 bpm", "≥ 171 bpm", or "by feel" without sensors. */
@Composable
private fun range(target: TargetStatus): String {
    if (target.source == TargetSource.FEEL) return stringResource(R.string.session_by_feel)
    val unit = stringResource(if (target.source == TargetSource.HEART_RATE) R.string.unit_bpm else R.string.unit_watt)
    val low = target.lowValue
    val high = target.highValue
    return when {
        low != null && high != null -> stringResource(R.string.session_range, low, high, unit)
        high != null -> stringResource(R.string.session_range_below, high, unit)
        low != null -> stringResource(R.string.session_range_above, low, unit)
        else -> ""
    }
}

private const val PREVIEW_HOUR_MS = 3_600_000L

@ThemePreviews
@Composable
private fun SessionTargetBarPreview() {
    RideCompPreview {
        Column {
            SessionTargetBar(
                TargetStatus(
                    intensity = Intensity.ENDURANCE,
                    durationMs = PREVIEW_HOUR_MS,
                    source = TargetSource.HEART_RATE,
                    lowValue = 114,
                    highValue = 133,
                    current = 141,
                    state = ZoneState.ABOVE,
                    doneMs = PREVIEW_HOUR_MS / 3,
                ),
                UnitFormatter(UnitSystem.METRIC),
            )
            SessionTargetBar(
                TargetStatus(Intensity.TEMPO, PREVIEW_HOUR_MS, TargetSource.FEEL, doneMs = PREVIEW_HOUR_MS),
                UnitFormatter(UnitSystem.METRIC),
            )
        }
    }
}
