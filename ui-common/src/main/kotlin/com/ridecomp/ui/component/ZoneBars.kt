package com.ridecomp.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import com.ridecomp.ui.preview.RideCompPreview
import com.ridecomp.ui.preview.ThemePreviews
import com.ridecomp.ui.theme.RideCompDimens
import com.ridecomp.ui.theme.RideCompTheme
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/**
 * One zone of a [ZoneBars] list: [name] ("Z2 Daya tahan"), [range] ("114–132 bpm"), the share of the ride
 * ([fraction] 0..1) with its [percent] and [duration] already formatted, and the zone's [color].
 */
@Immutable
data class ZoneBar(
    val name: String,
    val range: String,
    val fraction: Float,
    val percent: String,
    val duration: String,
    val color: Color,
)

/**
 * Time in zones (T11.5, reused for power zones in T11.6): per zone a line with name and range, then a
 * horizontal bar in the zone color with its share and time. Each line is read as one sentence.
 */
@Composable
fun ZoneBars(zones: ImmutableList<ZoneBar>, modifier: Modifier = Modifier) {
    val colors = RideCompTheme.colors
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(RideCompDimens.spacingSm)) {
        zones.forEach { zone ->
            Column(
                modifier = Modifier.clearAndSetSemantics {
                    contentDescription = "${zone.name}, ${zone.range}: ${zone.percent}, ${zone.duration}"
                },
                verticalArrangement = Arrangement.spacedBy(RideCompDimens.spacingXs),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = zone.name,
                        style = RideCompTheme.typography.bodyStrong,
                        color = colors.textPrimary,
                        modifier = Modifier.weight(1f),
                    )
                    Text(text = zone.range, style = RideCompTheme.typography.caption, color = colors.textSecondary)
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(RideCompDimens.spacingS),
                ) {
                    val shape = RoundedCornerShape(RideCompDimens.zoneBarHeight / 2)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(RideCompDimens.zoneBarHeight)
                            .clip(shape)
                            .background(colors.surfaceRaised),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(zone.fraction.coerceIn(0f, 1f))
                                .fillMaxHeight()
                                .clip(shape)
                                .background(zone.color),
                        )
                    }
                    // Fixed columns so every bar has the same length whatever its numbers ("0%" or "57%").
                    Text(
                        text = zone.percent,
                        style = RideCompTheme.typography.bodyStrong,
                        color = colors.textPrimary,
                        textAlign = TextAlign.End,
                        modifier = Modifier.width(RideCompDimens.zonePercentWidth),
                    )
                    Text(
                        text = zone.duration,
                        style = RideCompTheme.typography.caption,
                        color = colors.textSecondary,
                        textAlign = TextAlign.End,
                        modifier = Modifier.width(RideCompDimens.zoneDurationWidth),
                    )
                }
            }
        }
    }
}

@ThemePreviews
@Composable
private fun ZoneBarsPreview() {
    RideCompPreview {
        val zones = RideCompTheme.colors.zones
        ZoneBars(
            zones = persistentListOf(
                ZoneBar("Z1 Recovery", "< 114 bpm", 0.1f, "10%", "4:10", zones[0]),
                ZoneBar("Z2 Endurance", "114–132 bpm", 0.45f, "45%", "18:40", zones[1]),
                ZoneBar("Z3 Tempo", "133–151 bpm", 0.3f, "30%", "12:30", zones[2]),
                ZoneBar("Z4 Threshold", "152–170 bpm", 0.12f, "12%", "5:00", zones[3]),
                ZoneBar("Z5 VO2max", "≥ 171 bpm", 0.03f, "3%", "1:15", zones[4]),
            ),
        )
    }
}
