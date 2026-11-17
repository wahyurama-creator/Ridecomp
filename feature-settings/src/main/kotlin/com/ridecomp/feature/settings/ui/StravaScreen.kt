package com.ridecomp.feature.settings.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ridecomp.core.model.StravaStatus
import com.ridecomp.core.usecase.StravaAuthRedirect
import com.ridecomp.feature.settings.R
import com.ridecomp.feature.settings.viewmodel.StravaEffect
import com.ridecomp.feature.settings.viewmodel.StravaMessage
import com.ridecomp.feature.settings.viewmodel.StravaUiEvent
import com.ridecomp.feature.settings.viewmodel.StravaUiState
import com.ridecomp.feature.settings.viewmodel.StravaViewModel
import com.ridecomp.ui.R as UiR
import com.ridecomp.ui.component.AlertBanner
import com.ridecomp.ui.component.AlertSeverity
import com.ridecomp.ui.component.BannerAction
import com.ridecomp.ui.component.BigButton
import com.ridecomp.ui.component.BigButtonStyle
import com.ridecomp.ui.component.CardTone
import com.ridecomp.ui.component.ConfirmDialog
import com.ridecomp.ui.component.IconTile
import com.ridecomp.ui.component.RideCompCard
import com.ridecomp.ui.component.ScreenScaffold
import com.ridecomp.ui.component.SwitchRow
import com.ridecomp.ui.preview.RideCompPreview
import com.ridecomp.ui.preview.ThemePreviews
import com.ridecomp.ui.theme.RideCompDimens
import com.ridecomp.ui.theme.RideCompTheme
import com.ridecomp.ui.udf.ObserveEffects
import org.koin.androidx.compose.koinViewModel

/** Strava connection (Phase 5). [redirect] is the OAuth reply the app received, handled once. */
@Composable
fun StravaRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    redirect: StravaAuthRedirect? = null,
    onRedirectHandled: () -> Unit = {},
    viewModel: StravaViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    ObserveEffects(viewModel.effects) { effect ->
        when (effect) {
            is StravaEffect.OpenUrl -> try {
                // External app or browser, never a WebView (SEC-AUTH-01).
                context.startActivity(Intent(Intent.ACTION_VIEW, effect.url.toUri()))
            } catch (_: ActivityNotFoundException) {
                viewModel.onEvent(StravaUiEvent.OpenFailed)
            }
        }
    }
    LaunchedEffect(redirect) {
        redirect?.let {
            viewModel.onEvent(StravaUiEvent.Redirect(it))
            onRedirectHandled()
        }
    }
    StravaScreen(
        state = state,
        onEvent = viewModel::onEvent,
        onBack = onBack,
        modifier = modifier,
    )
}

@Composable
fun StravaScreen(
    state: StravaUiState,
    onEvent: (StravaUiEvent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ScreenScaffold(
        modifier = modifier,
        title = stringResource(R.string.strava_connect_title),
        subtitle = stringResource(R.string.strava_connect_subtitle),
        onBack = onBack,
        backLabel = stringResource(R.string.strava_back),
        banner = state.message?.let { message ->
            { MessageBanner(message, onShown = { onEvent(StravaUiEvent.MessageShown) }) }
        },
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(
                    start = RideCompDimens.screenPaddingHorizontal,
                    end = RideCompDimens.screenPaddingHorizontal,
                    bottom = RideCompDimens.screenPaddingBottom,
                ),
            verticalArrangement = Arrangement.spacedBy(RideCompDimens.blockGap),
        ) {
            val status = state.status
            when (status) {
                null -> Unit
                StravaStatus.Unavailable -> UnavailableCard()
                StravaStatus.NotConnected -> ConnectCard(state, onEvent)
                is StravaStatus.Connected -> ConnectedCard(status, onEvent)
            }
        }
    }
}

/** This build has no Strava app configured (D-82). */
@Composable
private fun UnavailableCard() {
    RideCompCard {
        Text(
            text = stringResource(R.string.strava_unavailable),
            style = RideCompTheme.typography.body,
            color = RideCompTheme.colors.textSecondary,
        )
    }
}

/** One tap: Strava opens, the rider taps Authorize and comes back connected. */
@Composable
private fun ConnectCard(state: StravaUiState, onEvent: (StravaUiEvent) -> Unit) {
    val colors = RideCompTheme.colors
    RideCompCard {
        Text(
            text = stringResource(R.string.strava_setup_title),
            style = RideCompTheme.typography.cardTitle,
            color = colors.textPrimary,
        )
        Text(
            text = stringResource(R.string.strava_setup_intro),
            style = RideCompTheme.typography.body,
            color = colors.textSecondary,
        )
    }
    BigButton(
        text = stringResource(R.string.strava_connect),
        onClick = { onEvent(StravaUiEvent.Connect) },
        isEnabled = !state.isWorking,
        iconRes = UiR.drawable.ic_upload,
    )
    Text(
        text = stringResource(R.string.strava_connect_hint),
        style = RideCompTheme.typography.caption,
        color = colors.textTertiary,
    )
}

@Composable
private fun ConnectedCard(status: StravaStatus.Connected, onEvent: (StravaUiEvent) -> Unit) {
    val colors = RideCompTheme.colors
    RideCompCard(tone = CardTone.OK) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(RideCompDimens.spacingSm),
        ) {
            IconTile(UiR.drawable.ic_check, tint = colors.ok)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = status.athleteName?.let { stringResource(R.string.strava_connected_as, it) }
                        ?: stringResource(R.string.strava_connected),
                    style = RideCompTheme.typography.bodyStrong,
                    color = colors.textPrimary,
                )
                Text(
                    text = stringResource(R.string.strava_connected_hint),
                    style = RideCompTheme.typography.caption,
                    color = colors.textSecondary,
                )
            }
        }
    }
    RideCompCard(contentPadding = false) {
        SwitchRow(
            label = stringResource(R.string.strava_auto_upload),
            description = stringResource(R.string.strava_auto_upload_hint),
            isChecked = status.isAutoUpload,
            onCheckedChange = { onEvent(StravaUiEvent.SetAutoUpload(it)) },
        )
    }
    var isConfirmShown by rememberSaveable { mutableStateOf(false) }
    BigButton(
        text = stringResource(R.string.strava_disconnect),
        onClick = { isConfirmShown = true },
        style = BigButtonStyle.TEXT_DANGER,
    )
    if (isConfirmShown) {
        ConfirmDialog(
            title = stringResource(R.string.strava_disconnect_title),
            message = stringResource(R.string.strava_disconnect_message),
            confirmLabel = stringResource(R.string.strava_disconnect),
            dismissLabel = stringResource(R.string.strava_cancel),
            onConfirm = {
                isConfirmShown = false
                onEvent(StravaUiEvent.Disconnect)
            },
            onDismiss = { isConfirmShown = false },
        )
    }
}

@Composable
private fun MessageBanner(message: StravaMessage, onShown: () -> Unit) {
    val (textRes, severity) = when (message) {
        StravaMessage.CONNECTED -> R.string.strava_message_connected to AlertSeverity.OK
        StravaMessage.DENIED -> R.string.strava_message_denied to AlertSeverity.WARN
        StravaMessage.MISSING_SCOPE -> R.string.strava_message_scope to AlertSeverity.WARN
        StravaMessage.OFFLINE -> R.string.strava_message_offline to AlertSeverity.WARN
        StravaMessage.REJECTED -> R.string.strava_message_rejected to AlertSeverity.DANGER
        StravaMessage.FAILED -> R.string.strava_message_failed to AlertSeverity.DANGER
        StravaMessage.NO_BROWSER -> R.string.strava_message_no_browser to AlertSeverity.WARN
        StravaMessage.DISCONNECTED -> R.string.strava_message_disconnected to AlertSeverity.OK
        StravaMessage.DISCONNECTED_LOCALLY -> R.string.strava_message_disconnected_locally to AlertSeverity.WARN
    }
    AlertBanner(
        message = stringResource(textRes),
        severity = severity,
        actions = listOf(BannerAction(stringResource(R.string.strava_ok), onShown)),
    )
}

@ThemePreviews
@Composable
private fun StravaSetupPreview() {
    RideCompPreview {
        StravaScreen(
            state = StravaUiState(status = StravaStatus.NotConnected),
            onEvent = {},
            onBack = {},
        )
    }
}

@ThemePreviews
@Composable
private fun StravaConnectedPreview() {
    RideCompPreview {
        StravaScreen(
            state = StravaUiState(status = StravaStatus.Connected("Rider")),
            onEvent = {},
            onBack = {},
        )
    }
}
