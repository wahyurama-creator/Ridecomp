package com.ridecomp.feature.ride.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import com.ridecomp.core.config.Units
import com.ridecomp.core.engine.safety.IncidentAction
import com.ridecomp.core.engine.safety.IncidentState
import com.ridecomp.feature.ride.R
import com.ridecomp.ui.component.AlertBanner
import com.ridecomp.ui.component.AlertSeverity
import com.ridecomp.ui.component.BannerAction
import com.ridecomp.ui.component.BigButton
import com.ridecomp.ui.component.BigButtonStyle
import com.ridecomp.ui.preview.RideCompPreview
import com.ridecomp.ui.preview.ThemePreviews
import com.ridecomp.ui.theme.RideCompDimens
import com.ridecomp.ui.theme.RideCompTheme
import com.ridecomp.ui.util.holdToConfirm

/** Manual SOS needs a 3 s hold (12-FEATURE-SPECS §5.3 step 5). */
private const val SOS_HOLD_MS = 3_000L

/**
 * Full-screen countdown (12-FEATURE-SPECS §5.3 step 1): huge "I AM OK" to cancel, "SEND NOW" below. Takes
 * the whole screen so it cannot be missed; the alarm sound comes from the recording service.
 */
@Composable
fun IncidentCountdown(
    state: IncidentState.Countdown,
    nowMs: Long,
    onIncident: (IncidentAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val secondsLeft = ((state.deadlineMs - nowMs + Units.MS_PER_SECOND - 1) / Units.MS_PER_SECOND).coerceAtLeast(0)
    Column(
        modifier = modifier.fillMaxSize().background(RideCompTheme.colors.danger).padding(RideCompDimens.spacingL),
        verticalArrangement = Arrangement.spacedBy(RideCompDimens.spacingL, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.incident_question),
            style = RideCompTheme.typography.title,
            color = RideCompTheme.colors.onDanger,
            textAlign = TextAlign.Center,
        )
        Text(
            text = secondsLeft.toString(),
            style = RideCompTheme.typography.dataValue,
            color = RideCompTheme.colors.onDanger,
        )
        Text(
            text = stringResource(R.string.incident_countdown_hint),
            style = RideCompTheme.typography.body,
            color = RideCompTheme.colors.onDanger,
            textAlign = TextAlign.Center,
        )
        BigButton(
            text = stringResource(R.string.incident_ok),
            onClick = { onIncident(IncidentAction.CANCEL) },
            modifier = Modifier.fillMaxWidth(),
        )
        BigButton(
            text = stringResource(R.string.incident_send_now),
            onClick = { onIncident(IncidentAction.SEND_NOW) },
            style = BigButtonStyle.SECONDARY,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** After sending: who got the SMS, retry status and "I AM SAFE" (12-FEATURE-SPECS §5.3 step 4). */
@Composable
fun IncidentAlertedBanner(state: IncidentState.Alerted, onIncident: (IncidentAction) -> Unit) {
    val message = when {
        state.sentContacts > 0 -> pluralStringResource(R.plurals.incident_sent, state.sentContacts, state.sentContacts)
        state.isRetrying -> stringResource(R.string.incident_retrying)
        else -> stringResource(R.string.incident_failed)
    }
    AlertBanner(
        message = message,
        severity = if (state.sentContacts > 0) AlertSeverity.WARN else AlertSeverity.DANGER,
        actions = listOf(BannerAction(stringResource(R.string.incident_safe)) { onIncident(IncidentAction.SAFE) }),
    )
}

/** Hold-for-3-seconds SOS on the pause screen; a tap does nothing, so it cannot fire by accident. */
@Composable
fun SosButton(onIncident: (IncidentAction) -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = RideCompDimens.touchTargetPrimary)
            .background(RideCompTheme.colors.danger)
            .semantics { role = Role.Button }
            .holdToConfirm(SOS_HOLD_MS) { onIncident(IncidentAction.SOS) },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(R.string.incident_sos_hold),
            style = RideCompTheme.typography.button,
            color = RideCompTheme.colors.onDanger,
        )
    }
}

@ThemePreviews
@Composable
private fun IncidentCountdownPreview() {
    RideCompPreview {
        IncidentCountdown(IncidentState.Countdown(deadlineMs = 21_000, isManual = false), nowMs = 0, onIncident = {})
    }
}

@ThemePreviews
@Composable
private fun SosButtonPreview() {
    RideCompPreview { SosButton(onIncident = {}) }
}
