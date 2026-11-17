package com.ridecomp.feature.ride.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import com.ridecomp.core.engine.sensors.ErgConfig
import com.ridecomp.core.engine.sensors.TrainerState
import com.ridecomp.feature.ride.R
import com.ridecomp.ui.preview.RideCompPreview
import com.ridecomp.ui.preview.ThemePreviews
import com.ridecomp.ui.theme.RideCompDimens
import com.ridecomp.ui.theme.RideCompTheme

/** Zone buttons for an interval session on a trainer: easy (zone 2) and the session's hard zone (T9.1). */
@Immutable
data class ErgPresets(val easyW: Int, val zone: Int, val zoneW: Int)

sealed interface TrainerAction {
    data class Adjust(val deltaW: Int) : TrainerAction

    data class Set(val watts: Int) : TrainerAction
}

/**
 * Smart trainer in ERG (T9.1, D-95): − / target watts / +, and for interval sessions "Santai" / "Zona 4"
 * buttons. Without control (another app holds the trainer) it only says so.
 */
@Composable
fun TrainerBar(
    state: TrainerState,
    presets: ErgPresets?,
    onAction: (TrainerAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = RideCompTheme.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.surface)
            .padding(horizontal = RideCompDimens.spacingM, vertical = RideCompDimens.spacingS),
        verticalArrangement = Arrangement.spacedBy(RideCompDimens.spacingS),
    ) {
        if (!state.isControlled) {
            Text(
                text = stringResource(R.string.trainer_no_control),
                style = RideCompTheme.typography.caption,
                color = colors.textSecondary,
            )
            return@Column
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(RideCompDimens.spacingS),
        ) {
            Text(
                text = stringResource(R.string.trainer_erg),
                style = RideCompTheme.typography.dataLabel,
                color = colors.textSecondary,
            )
            TrainerButton(stringResource(R.string.trainer_minus), Modifier.weight(1f)) {
                onAction(TrainerAction.Adjust(-ErgConfig.STEP_W))
            }
            Text(
                text = stringResource(R.string.trainer_watts, state.targetW ?: ErgConfig.DEFAULT_W),
                style = RideCompTheme.typography.title,
                color = colors.textPrimary,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(WATTS_WEIGHT),
            )
            TrainerButton(stringResource(R.string.trainer_plus), Modifier.weight(1f)) {
                onAction(TrainerAction.Adjust(ErgConfig.STEP_W))
            }
        }
        presets?.let { zones ->
            Row(horizontalArrangement = Arrangement.spacedBy(RideCompDimens.spacingS)) {
                TrainerButton(stringResource(R.string.trainer_easy), Modifier.weight(1f)) {
                    onAction(TrainerAction.Set(zones.easyW))
                }
                TrainerButton(stringResource(R.string.trainer_zone, zones.zone), Modifier.weight(1f)) {
                    onAction(TrainerAction.Set(zones.zoneW))
                }
            }
        }
    }
}

@Composable
private fun TrainerButton(label: String, modifier: Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(RideCompDimens.cornerRadiusSmall),
        color = RideCompTheme.colors.bg,
        contentColor = RideCompTheme.colors.textPrimary,
        modifier = modifier.heightIn(min = RideCompDimens.touchTargetMin),
    ) {
        // Centered both ways inside the 56 dp touch target (device test: labels sat at the top).
        Box(contentAlignment = Alignment.Center) {
            Text(text = label, style = RideCompTheme.typography.cardTitle, textAlign = TextAlign.Center)
        }
    }
}

private const val WATTS_WEIGHT = 1.4f

@ThemePreviews
@Composable
private fun TrainerBarPreview() {
    RideCompPreview {
        Column {
            TrainerBar(TrainerState(isConnected = true, isControlled = true, targetW = 150), ErgPresets(150, 4, 225), {
            })
            TrainerBar(TrainerState(isConnected = true, isControlled = false), null, {})
        }
    }
}
