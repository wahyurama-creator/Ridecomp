package com.ridecomp.feature.settings.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ridecomp.core.config.StravaConfig
import com.ridecomp.core.model.StravaStatus
import com.ridecomp.core.usecase.StravaAppError
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
import com.ridecomp.ui.component.BigButtonSize
import com.ridecomp.ui.component.BigButtonStyle
import com.ridecomp.ui.component.CardTone
import com.ridecomp.ui.component.ConfirmDialog
import com.ridecomp.ui.component.IconTile
import com.ridecomp.ui.component.LinkText
import com.ridecomp.ui.component.RideCompCard
import com.ridecomp.ui.component.RideCompTextField
import com.ridecomp.ui.component.ScreenScaffold
import com.ridecomp.ui.component.SwitchRow
import com.ridecomp.ui.preview.RideCompPreview
import com.ridecomp.ui.preview.ThemePreviews
import com.ridecomp.ui.theme.RideCompDimens
import com.ridecomp.ui.theme.RideCompTheme
import com.ridecomp.ui.udf.ObserveEffects
import com.ridecomp.ui.util.ExcludeFromAutofill
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
    // The Client Secret must not reach a password manager's cloud.
    ExcludeFromAutofill()
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
        onOpenApiPage = { viewModel.onEvent(StravaUiEvent.OpenApiPage) },
        onBack = onBack,
        modifier = modifier,
    )
}

@Composable
fun StravaScreen(
    state: StravaUiState,
    onEvent: (StravaUiEvent) -> Unit,
    onOpenApiPage: () -> Unit,
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
            when {
                status == null -> Unit
                state.isFormShown -> SetupForm(state, onEvent, onOpenApiPage)
                status is StravaStatus.NotConnected -> ConnectCard(status, state, onEvent)
                status is StravaStatus.Connected -> ConnectedCard(status, onEvent)
            }
        }
    }
}

/** T5.1: why a personal API app is needed, how to make it, and the two fields. */
@Composable
private fun SetupForm(state: StravaUiState, onEvent: (StravaUiEvent) -> Unit, onOpenApiPage: () -> Unit) {
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
    RideCompCard {
        Step(STEP_OPEN, stringResource(R.string.strava_step_open)) {
            BigButton(
                text = stringResource(R.string.strava_open_api_page),
                onClick = onOpenApiPage,
                style = BigButtonStyle.OUTLINE,
                size = BigButtonSize.COMPACT,
            )
        }
        Step(STEP_FORM, stringResource(R.string.strava_step_form)) {
            FormValue(stringResource(R.string.strava_field_name), stringResource(R.string.strava_value_name))
            FormValue(stringResource(R.string.strava_field_category), stringResource(R.string.strava_value_category))
            FormValue(stringResource(R.string.strava_field_website), stringResource(R.string.strava_value_website))
            FormValue(stringResource(R.string.strava_field_callback), StravaConfig.REDIRECT_HOST, isImportant = true)
        }
        Step(STEP_COPY, stringResource(R.string.strava_step_copy)) {}
    }
    RideCompTextField(
        label = stringResource(R.string.strava_client_id),
        value = state.clientId,
        onValueChange = { onEvent(StravaUiEvent.ClientIdChanged(it)) },
        keyboardType = KeyboardType.Number,
        error = if (state.appError == StravaAppError.INVALID_CLIENT_ID) {
            stringResource(R.string.strava_client_id_error)
        } else {
            null
        },
    )
    RideCompTextField(
        label = stringResource(R.string.strava_client_secret),
        value = state.clientSecret,
        onValueChange = { onEvent(StravaUiEvent.ClientSecretChanged(it)) },
        isPassword = true,
        error = if (state.appError == StravaAppError.INVALID_CLIENT_SECRET) {
            stringResource(R.string.strava_client_secret_error)
        } else {
            null
        },
    )
    Text(
        text = stringResource(R.string.strava_secret_note),
        style = RideCompTheme.typography.caption,
        color = colors.textTertiary,
    )
    BigButton(
        text = stringResource(R.string.strava_save_app),
        onClick = { onEvent(StravaUiEvent.SaveApp) },
        isEnabled = !state.isWorking && state.clientId.isNotBlank() && state.clientSecret.isNotBlank(),
        size = BigButtonSize.SECONDARY,
    )
    if (state.isEditingApp) {
        BigButton(
            text = stringResource(R.string.strava_cancel),
            onClick = { onEvent(StravaUiEvent.CancelEdit) },
            style = BigButtonStyle.TEXT,
        )
    }
}

private const val STEP_OPEN = 1
private const val STEP_FORM = 2
private const val STEP_COPY = 3

/** A numbered step: number badge, text, then what belongs to it. */
@Composable
private fun Step(number: Int, text: String, content: @Composable () -> Unit) {
    val colors = RideCompTheme.colors
    Row(horizontalArrangement = Arrangement.spacedBy(RideCompDimens.spacingSm)) {
        Box(
            modifier = Modifier
                .size(RideCompDimens.stepBadgeSize)
                .clip(CircleShape)
                .background(colors.accent),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = number.toString(), style = RideCompTheme.typography.navLabel, color = colors.onAccent)
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(RideCompDimens.spacingS)) {
            Text(text = text, style = RideCompTheme.typography.body, color = colors.textPrimary)
            content()
        }
    }
}

/** "Authorization Callback Domain: localhost" as the rider should type it. */
@Composable
private fun FormValue(field: String, value: String, isImportant: Boolean = false) {
    val colors = RideCompTheme.colors
    Column {
        Text(text = field, style = RideCompTheme.typography.caption, color = colors.textSecondary)
        Text(
            text = value,
            style = RideCompTheme.typography.bodyStrong.copy(fontFamily = FontFamily.Monospace),
            color = if (isImportant) colors.accent else colors.textPrimary,
            modifier = Modifier
                .clip(RoundedCornerShape(RideCompDimens.cornerRadius))
                .background(colors.surfaceRaised)
                .padding(horizontal = RideCompDimens.spacingS, vertical = RideCompDimens.spacingXs),
        )
    }
}

/** T5.2: the app is saved; one tap opens Strava to allow uploads. */
@Composable
private fun ConnectCard(status: StravaStatus.NotConnected, state: StravaUiState, onEvent: (StravaUiEvent) -> Unit) {
    val colors = RideCompTheme.colors
    RideCompCard {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(RideCompDimens.spacingSm),
        ) {
            IconTile(UiR.drawable.ic_check, tint = colors.ok)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.strava_app_saved),
                    style = RideCompTheme.typography.bodyStrong,
                    color = colors.textPrimary,
                )
                Text(
                    text = stringResource(R.string.strava_client_id_value, status.clientId),
                    style = RideCompTheme.typography.caption,
                    color = colors.textSecondary,
                )
            }
        }
        LinkText(stringResource(R.string.strava_change_app), onClick = { onEvent(StravaUiEvent.EditApp) })
    }
    Text(
        text = stringResource(R.string.strava_connect_hint),
        style = RideCompTheme.typography.body,
        color = colors.textSecondary,
    )
    BigButton(
        text = stringResource(R.string.strava_connect),
        onClick = { onEvent(StravaUiEvent.Connect) },
        isEnabled = !state.isWorking,
        iconRes = UiR.drawable.ic_upload,
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
            state = StravaUiState(status = StravaStatus.NotSetUp),
            onEvent = {},
            onOpenApiPage = {},
            onBack = {},
        )
    }
}

@ThemePreviews
@Composable
private fun StravaConnectedPreview() {
    RideCompPreview {
        StravaScreen(
            state = StravaUiState(status = StravaStatus.Connected("12345", "Rider")),
            onEvent = {},
            onOpenApiPage = {},
            onBack = {},
        )
    }
}
