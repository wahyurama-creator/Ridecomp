package com.ridecomp.feature.ride.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import com.ridecomp.core.config.Units
import com.ridecomp.core.engine.segments.LiveSegmentState
import com.ridecomp.core.engine.segments.SegmentProgress
import com.ridecomp.core.engine.segments.SegmentResult
import com.ridecomp.core.model.UnitSystem
import com.ridecomp.core.util.UnitFormatter
import com.ridecomp.feature.ride.R
import com.ridecomp.ui.component.AlertBanner
import com.ridecomp.ui.component.AlertSeverity
import com.ridecomp.ui.component.DataField
import com.ridecomp.ui.component.LabelTone
import com.ridecomp.ui.preview.RideCompPreview
import com.ridecomp.ui.preview.ThemePreviews
import com.ridecomp.ui.theme.RideCompDimens
import com.ridecomp.ui.theme.RideCompTheme
import kotlin.math.roundToLong

/** "Segmen: Tanjakan Kampung · mulai" and the result "1:42 · PR baru!" (12-FEATURE-SPECS §9.3). */
@Composable
fun SegmentBanners(state: LiveSegmentState, formatter: UnitFormatter) {
    state.approaching?.let {
        AlertBanner(message = stringResource(R.string.ride_segment_approaching, it), severity = AlertSeverity.INFO)
    }
    state.result?.let { ResultBanner(it, formatter) }
}

@Composable
private fun ResultBanner(result: SegmentResult, formatter: UnitFormatter) {
    val time = formatter.duration(result.elapsedS.toMs())
    val delta = result.deltaVsPrS
    AlertBanner(
        title = result.name,
        message = when {
            result.isPr -> stringResource(R.string.ride_segment_result_pr, time)
            delta != null -> stringResource(
                R.string.ride_segment_result_slower,
                time,
                formatter.signedDuration(delta.toMs()),
            )
            else -> time
        },
        severity = if (result.isPr) AlertSeverity.OK else AlertSeverity.WARN,
    )
}

/**
 * Live segment page (13-UI-SCREENS #17): replaces the main page while riding a segment. Ahead of the record is
 * green with a minus sign, behind is orange with a plus sign (not only color).
 */
@Composable
fun SegmentPage(progress: SegmentProgress, heartRateBpm: Int?, speedMps: Double?, formatter: UnitFormatter) {
    val colors = RideCompTheme.colors
    val divider = if (colors.isDark) RideCompDimens.dividerThickness else RideCompDimens.dividerThicknessLight
    val delta = progress.deltaVsPrS
    Column(
        modifier = Modifier.fillMaxSize().background(colors.divider),
        verticalArrangement = Arrangement.spacedBy(divider),
    ) {
        DataField(
            label = stringResource(R.string.ride_segment_label, progress.name),
            value = delta?.let { formatter.signedDuration(it.toMs()) } ?: formatter.duration(progress.elapsedMs),
            tag = stringResource(
                when {
                    delta == null -> R.string.ride_segment_first
                    delta <= 0 -> R.string.ride_segment_ahead
                    else -> R.string.ride_segment_behind
                },
            ),
            isHero = true,
            labelTone = LabelTone.ACCENT,
            valueColor = when {
                delta == null -> colors.textPrimary
                delta <= 0 -> colors.ok
                else -> colors.accent
            },
            modifier = Modifier.fillMaxWidth().weight(HERO_WEIGHT),
        )
        ProgressRow(progress, formatter)
        Row(modifier = Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(divider)) {
            DataField(
                label = stringResource(R.string.ride_segment_time),
                value = formatter.duration(progress.elapsedMs),
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
            DataField(
                label = stringResource(R.string.ride_segment_pr),
                value = progress.prElapsedS?.let { formatter.duration(it.toMs()) } ?: formatter.duration(null),
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
        }
        Row(modifier = Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(divider)) {
            DataField(
                label = stringResource(R.string.metric_heart_rate),
                value = formatter.integer(heartRateBpm),
                unit = stringResource(R.string.unit_bpm),
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
            DataField(
                label = stringResource(R.string.metric_speed),
                value = formatter.speed(speedMps),
                unit = formatter.speedUnit,
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
        }
    }
}

/** Orange progress bar with the distance still to ride. */
@Composable
private fun ProgressRow(progress: SegmentProgress, formatter: UnitFormatter) {
    val colors = RideCompTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.bg)
            .padding(horizontal = RideCompDimens.spacingM, vertical = RideCompDimens.spacingSm),
        verticalArrangement = Arrangement.spacedBy(RideCompDimens.spacingXs),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(RideCompDimens.storageBarHeight)
                .clip(RoundedCornerShape(RideCompDimens.storageBarHeight))
                .background(colors.divider),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress.fraction.toFloat())
                    .fillMaxHeight()
                    .background(colors.accent),
            )
        }
        Text(
            text = stringResource(
                R.string.ride_segment_remaining,
                formatter.distance(progress.remainingM),
                formatter.distanceUnit,
            ),
            style = RideCompTheme.typography.caption,
            color = colors.textSecondary,
        )
    }
}

private fun Double.toMs(): Long = (this * Units.MS_PER_SECOND).roundToLong()

private const val HERO_WEIGHT = 1.6f

@ThemePreviews
@Composable
private fun SegmentPagePreview() {
    RideCompPreview {
        SegmentPage(
            progress = SegmentProgress("Tanjakan Kampung", 98_000, 1_300.0, 420.0, 108.0, -6.0),
            heartRateBpm = 164,
            speedMps = 6.8,
            formatter = UnitFormatter(UnitSystem.METRIC),
        )
    }
}
