package com.ridecomp.ui.component

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import com.ridecomp.ui.R
import com.ridecomp.ui.preview.RideCompPreview
import com.ridecomp.ui.preview.ThemePreviews
import com.ridecomp.ui.theme.RideCompDimens
import com.ridecomp.ui.theme.RideCompTheme
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/** Color of a status icon in the [RideStatusStrip]. */
enum class StatusTone { OK, WARN, DANGER, INACTIVE }

/** One status icon (GPS, HR, cadence…) with its spoken label (UI-08). */
@Immutable
data class StatusIcon(@param:DrawableRes val iconRes: Int, val tone: StatusTone, val description: String)

/** Recording indicator on the right of the [RideStatusStrip]. */
enum class RecordingIndicator { REC, PAUSED, NONE }

/**
 * RideStatusStrip (13-UI-SCREENS §2): 40 dp; clock on the left; status icons, phone battery (warn in
 * battery saver) and ● REC / ● JEDA on the right.
 */
@Composable
fun RideStatusStrip(
    clock: String,
    icons: ImmutableList<StatusIcon>,
    battery: String,
    recording: RecordingIndicator,
    recordingLabel: String,
    modifier: Modifier = Modifier,
    isBatteryWarning: Boolean = false,
) {
    val colors = RideCompTheme.colors
    val style = RideCompTheme.typography.caption.copy(fontWeight = FontWeight.SemiBold)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(RideCompDimens.rideTopBarHeight)
            .padding(horizontal = RideCompDimens.spacingM),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = clock, style = style, color = colors.textPrimary, modifier = Modifier.weight(1f))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(RideCompDimens.cardGap),
        ) {
            icons.forEach { icon ->
                Icon(
                    painter = painterResource(icon.iconRes),
                    contentDescription = icon.description,
                    tint = when (icon.tone) {
                        StatusTone.OK -> colors.ok
                        StatusTone.WARN -> colors.warnText
                        StatusTone.DANGER -> colors.danger
                        StatusTone.INACTIVE -> colors.disabled
                    },
                    modifier = Modifier.size(RideCompDimens.statusIconSize),
                )
            }
            Text(text = battery, style = style, color = if (isBatteryWarning) colors.warnText else colors.textSecondary)
            if (recording != RecordingIndicator.NONE) {
                val tint = if (recording == RecordingIndicator.REC) colors.danger else colors.warnText
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(RideCompDimens.spacingXs),
                ) {
                    Box(modifier = Modifier.size(RideCompDimens.recDotSize).background(tint, CircleShape))
                    Text(text = recordingLabel, style = style.copy(fontWeight = FontWeight.ExtraBold), color = tint)
                }
            }
        }
    }
}

/**
 * RideControlBar buttons (13-UI-SCREENS §2): the primary action (PAUSE) fills the width, the secondary
 * (LAP) is a 120 dp outline. Both 72 dp tall (UI-07).
 */
@Composable
fun RideControlButtons(
    primaryLabel: String,
    @DrawableRes primaryIconRes: Int,
    onPrimary: () -> Unit,
    secondaryLabel: String,
    onSecondary: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = RideCompTheme.colors
    val shape = RoundedCornerShape(RideCompDimens.cornerRadiusButton)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                start = RideCompDimens.spacingSm,
                end = RideCompDimens.spacingSm,
                bottom = RideCompDimens.spacingM,
            ),
        horizontalArrangement = Arrangement.spacedBy(RideCompDimens.cardGap),
    ) {
        Button(
            onClick = onPrimary,
            modifier = Modifier.weight(1f).height(RideCompDimens.buttonHeightPrimary),
            shape = shape,
            colors = ButtonDefaults.buttonColors(containerColor = colors.accent, contentColor = colors.onAccent),
        ) {
            Icon(
                painterResource(primaryIconRes),
                contentDescription = null,
                modifier = Modifier.size(RideCompDimens.iconSize),
            )
            Box(modifier = Modifier.width(RideCompDimens.cardGap))
            Text(text = primaryLabel, style = RideCompTheme.typography.button)
        }
        Button(
            onClick = onSecondary,
            modifier = Modifier.width(RideCompDimens.lapButtonWidth).height(RideCompDimens.buttonHeightPrimary),
            shape = shape,
            border = BorderStroke(RideCompDimens.borderSelected, colors.textPrimary),
            colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent, contentColor = colors.textPrimary),
        ) {
            Text(text = secondaryLabel, style = RideCompTheme.typography.button)
        }
    }
}

/** MapAttribution (13-UI-SCREENS §2, 04-INTEGRATIONS §D): required on every map, bottom-left. */
@Composable
fun MapAttribution(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = RideCompTheme.typography.attribution,
        color = RideCompTheme.colors.textSecondary,
        modifier = modifier
            .background(RideCompTheme.colors.bg.copy(alpha = ATTRIBUTION_BACKGROUND_ALPHA))
            .padding(horizontal = RideCompDimens.spacingXs)
            .semantics { contentDescription = text },
    )
}

private const val ATTRIBUTION_BACKGROUND_ALPHA = 0.6f

@ThemePreviews
@Composable
private fun RideChromePreview() {
    RideCompPreview {
        Column {
            RideStatusStrip(
                clock = "06:42",
                icons = persistentListOf(
                    StatusIcon(R.drawable.ic_status_gps, StatusTone.OK, "GPS"),
                    StatusIcon(R.drawable.ic_status_heart, StatusTone.OK, "HR"),
                    StatusIcon(R.drawable.ic_status_cadence, StatusTone.WARN, "Cadence"),
                ),
                battery = "78%",
                recording = RecordingIndicator.REC,
                recordingLabel = "REC",
            )
            MapAttribution("© OpenStreetMap contributors")
            RideControlButtons(
                primaryLabel = "PAUSE",
                primaryIconRes = R.drawable.ic_pause,
                onPrimary = {},
                secondaryLabel = "LAP",
                onSecondary = {},
            )
        }
    }
}
