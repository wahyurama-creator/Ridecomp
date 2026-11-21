package com.ridecomp.feature.ride.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.ridecomp.core.config.Units
import com.ridecomp.core.engine.safety.IncidentAction
import com.ridecomp.core.engine.safety.IncidentState
import com.ridecomp.feature.ride.R
import com.ridecomp.ui.R as UiR
import com.ridecomp.ui.component.AlertBanner
import com.ridecomp.ui.component.AlertSeverity
import com.ridecomp.ui.component.BannerAction
import com.ridecomp.ui.component.BigButton
import com.ridecomp.ui.component.BigButtonSize
import com.ridecomp.ui.component.BigButtonStyle
import com.ridecomp.ui.preview.RideCompPreview
import com.ridecomp.ui.preview.ThemePreviews
import com.ridecomp.ui.theme.RideCompDimens
import com.ridecomp.ui.theme.RideCompTheme
import com.ridecomp.ui.theme.ThemeMode
import com.ridecomp.ui.util.holdToConfirm
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/** Manual SOS needs a 3 s hold (12-FEATURE-SPECS §5.3 step 5). */
private const val SOS_HOLD_MS = 3_000L

/**
 * Full-screen countdown (12-FEATURE-SPECS §5.3 step 1, 13-UI-SCREENS #18): deep red, the seconds in a
 * white ring, who gets the SMS, a 120 dp "I AM OK" and "SEND NOW". The alarm comes from the service. Always
 * dark, whatever the app theme (T11.9): the same urgent look in every case.
 */
@Composable
fun IncidentCountdown(
    state: IncidentState.Countdown,
    nowMs: Long,
    contactNames: ImmutableList<String>,
    onIncident: (IncidentAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    RideCompTheme(themeMode = ThemeMode.DARK) {
        IncidentCountdownContent(state, nowMs, contactNames, onIncident, modifier)
    }
}

@Composable
private fun IncidentCountdownContent(
    state: IncidentState.Countdown,
    nowMs: Long,
    contactNames: ImmutableList<String>,
    onIncident: (IncidentAction) -> Unit,
    modifier: Modifier,
) {
    val colors = RideCompTheme.colors
    val secondsLeft = ((state.deadlineMs - nowMs + Units.MS_PER_SECOND - 1) / Units.MS_PER_SECOND).coerceAtLeast(0)
    BoxWithConstraints(modifier = modifier.fillMaxSize().background(colors.dangerDeep)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .heightIn(min = maxHeight)
                .padding(horizontal = RideCompDimens.screenPaddingHorizontal, vertical = RideCompDimens.spacingXl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(RideCompDimens.blockGap),
            ) {
                Icon(
                    painterResource(UiR.drawable.ic_warning),
                    contentDescription = null,
                    tint = colors.onDanger,
                    modifier = Modifier.size(RideCompDimens.iconSizeLarge),
                )
                Text(
                    text = stringResource(
                        if (state.isManual) R.string.incident_title_manual else R.string.incident_title,
                    ),
                    style = RideCompTheme.typography.screenTitle,
                    color = colors.onDanger,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = stringResource(R.string.incident_question),
                    style = RideCompTheme.typography.body,
                    color = colors.onDanger,
                    textAlign = TextAlign.Center,
                )
                CountdownRing(secondsLeft)
                Text(
                    text = recipientsText(contactNames),
                    style = RideCompTheme.typography.body,
                    color = colors.onDanger,
                    textAlign = TextAlign.Center,
                )
            }
            Column(
                modifier = Modifier.padding(top = RideCompDimens.blockGap),
                verticalArrangement = Arrangement.spacedBy(RideCompDimens.spacingSm),
            ) {
                BigButton(
                    text = stringResource(R.string.incident_ok),
                    onClick = { onIncident(IncidentAction.CANCEL) },
                    style = BigButtonStyle.INVERSE_LIGHT,
                    size = BigButtonSize.INCIDENT,
                )
                BigButton(
                    text = stringResource(R.string.incident_send_now),
                    onClick = { onIncident(IncidentAction.SEND_NOW) },
                    style = BigButtonStyle.OUTLINE_LIGHT,
                )
                Text(
                    text = stringResource(R.string.incident_alarm_note),
                    style = RideCompTheme.typography.caption,
                    color = colors.onDanger,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/** The seconds left in a thick white ring; announced politely so TalkBack users hear the countdown. */
@Composable
private fun CountdownRing(secondsLeft: Long) {
    val colors = RideCompTheme.colors
    Column(
        modifier = Modifier
            .size(RideCompTheme.dimens.countdownRing)
            .border(RideCompDimens.countdownRingStroke, colors.onDanger, CircleShape)
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(text = secondsLeft.toString(), style = RideCompTheme.typography.hugeNumber, color = colors.onDanger)
        Text(
            text = stringResource(R.string.incident_seconds),
            style = RideCompTheme.typography.bodyStrong,
            color = colors.onDanger,
        )
    }
}

/** "…sent to **Ibu** and **Kakak**" with the names in bold; the generic hint without contacts. */
@Composable
private fun recipientsText(names: List<String>): AnnotatedString {
    if (names.isEmpty()) return AnnotatedString(stringResource(R.string.incident_countdown_hint))
    val joined = names.dropLast(1).joinToString(NAME_SEPARATOR).let { first ->
        if (first.isEmpty()) names.last() else first + stringResource(R.string.incident_and) + names.last()
    }
    val template = stringResource(R.string.incident_recipients, joined)
    return buildAnnotatedString {
        append(template)
        names.forEach { name ->
            var from = template.indexOf(name)
            while (from >= 0) {
                addStyle(SpanStyle(fontWeight = FontWeight.Bold), from, from + name.length)
                from = template.indexOf(name, from + name.length)
            }
        }
    }
}

private const val NAME_SEPARATOR = ", "

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
    val colors = RideCompTheme.colors
    val shape = RoundedCornerShape(RideCompDimens.cornerRadiusCard)
    val label = stringResource(R.string.incident_sos_hold)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = RideCompDimens.buttonHeightTertiary)
            .clip(shape)
            .border(RideCompDimens.borderSelected, colors.danger, shape)
            .semantics {
                role = Role.Button
                contentDescription = label
            }
            .holdToConfirm(SOS_HOLD_MS) { onIncident(IncidentAction.SOS) },
        horizontalArrangement = Arrangement.spacedBy(RideCompDimens.cardGap, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(UiR.drawable.ic_warning), contentDescription = null, tint = colors.danger)
        Text(text = label, style = RideCompTheme.typography.buttonSmall, color = colors.danger)
    }
}

@ThemePreviews
@Composable
private fun IncidentCountdownPreview() {
    RideCompPreview {
        IncidentCountdown(
            IncidentState.Countdown(deadlineMs = 21_000, isManual = false),
            nowMs = 0,
            contactNames = persistentListOf("Ibu", "Kakak"),
            onIncident = {},
        )
    }
}

@ThemePreviews
@Composable
private fun SosButtonPreview() {
    RideCompPreview { SosButton(onIncident = {}) }
}
