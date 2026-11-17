package com.ridecomp.feature.settings.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
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
import com.ridecomp.ui.component.ConfirmDialog
import com.ridecomp.ui.component.IconTile
import com.ridecomp.ui.component.ListCard
import com.ridecomp.ui.component.ListCardRow
import com.ridecomp.ui.component.RideCompCard
import com.ridecomp.ui.component.ScreenScaffold
import com.ridecomp.ui.component.SwitchRow
import com.ridecomp.ui.preview.RideCompPreview
import com.ridecomp.ui.preview.ThemePreviews
import com.ridecomp.ui.theme.BrandColors
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

/** Not linked yet: what linking gives, then one button. */
@Composable
private fun ConnectCard(state: StravaUiState, onEvent: (StravaUiEvent) -> Unit) {
    val colors = RideCompTheme.colors
    RideCompCard {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = RideCompDimens.spacingS),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(RideCompDimens.spacingSm),
        ) {
            LinkVisual()
            Text(
                text = stringResource(R.string.strava_hero_title),
                style = RideCompTheme.typography.title,
                color = colors.textPrimary,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(R.string.strava_hero_text),
                style = RideCompTheme.typography.body,
                color = colors.textSecondary,
                textAlign = TextAlign.Center,
            )
        }
    }
    ListCard {
        Benefit(UiR.drawable.ic_upload, R.string.strava_benefit_tap_title, R.string.strava_benefit_tap_text)
        Benefit(UiR.drawable.ic_play, R.string.strava_benefit_auto_title, R.string.strava_benefit_auto_text)
        Benefit(
            UiR.drawable.ic_check,
            R.string.strava_benefit_private_title,
            R.string.strava_benefit_private_text,
            tint = colors.ok,
            hasDivider = false,
        )
    }
    BigButton(
        text = stringResource(R.string.strava_connect),
        onClick = { onEvent(StravaUiEvent.Connect) },
        isEnabled = !state.isWorking,
    )
    Text(
        text = stringResource(R.string.strava_connect_hint),
        style = RideCompTheme.typography.caption,
        color = colors.textTertiary,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
}

/** RideComp, three dots, Strava: the two apps being linked. */
@Composable
private fun LinkVisual() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(RideCompDimens.spacingS),
        // Decorative: the title below says what happens.
        modifier = Modifier.clearAndSetSemantics {},
    ) {
        LinkBadge(UiR.drawable.ic_nav_ride, RideCompTheme.colors.accentTint, RideCompTheme.colors.accent)
        repeat(LINK_DOTS) {
            Box(
                modifier = Modifier
                    .size(RideCompDimens.linkDotSize)
                    .clip(CircleShape)
                    .background(RideCompTheme.colors.textTertiary),
            )
        }
        LinkBadge(UiR.drawable.ic_upload, BrandColors.StravaOrange, BrandColors.OnStravaOrange)
    }
}

@Composable
private fun LinkBadge(@DrawableRes iconRes: Int, background: Color, tint: Color) {
    Box(
        modifier = Modifier
            .size(RideCompDimens.linkBadgeSize)
            .clip(RoundedCornerShape(RideCompDimens.cornerRadiusCard))
            .background(background),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(RideCompDimens.iconSize),
        )
    }
}

@Composable
private fun Benefit(
    @DrawableRes iconRes: Int,
    @StringRes titleRes: Int,
    @StringRes textRes: Int,
    tint: Color = RideCompTheme.colors.accent,
    hasDivider: Boolean = true,
) {
    ListCardRow(
        title = stringResource(titleRes),
        subtitle = stringResource(textRes),
        hasDivider = hasDivider,
        leading = { IconTile(iconRes, tint = tint) },
    )
}

private const val LINK_DOTS = 3

/** Linked: who, auto-upload, and a quiet way out. */
@Composable
private fun ConnectedCard(status: StravaStatus.Connected, onEvent: (StravaUiEvent) -> Unit) {
    val colors = RideCompTheme.colors
    val name = status.athleteName ?: stringResource(R.string.strava_connected)
    RideCompCard {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(RideCompDimens.spacingSm),
        ) {
            Box(
                modifier = Modifier
                    .size(RideCompDimens.avatarSizeLarge)
                    .clip(CircleShape)
                    .background(BrandColors.StravaOrange),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = name.initials(),
                    style = RideCompTheme.typography.cardTitle,
                    color = BrandColors.OnStravaOrange,
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(text = name, style = RideCompTheme.typography.cardTitle, color = colors.textPrimary)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(RideCompDimens.spacingXs),
                ) {
                    Box(
                        modifier = Modifier
                            .size(RideCompDimens.linkDotSize)
                            .clip(CircleShape)
                            .background(colors.ok),
                    )
                    Text(
                        text = stringResource(R.string.strava_connected),
                        style = RideCompTheme.typography.caption,
                        color = colors.ok,
                    )
                }
            }
        }
    }
    ListCard {
        SwitchRow(
            label = stringResource(R.string.strava_auto_upload),
            description = stringResource(R.string.strava_auto_upload_hint),
            isChecked = status.isAutoUpload,
            onCheckedChange = { onEvent(StravaUiEvent.SetAutoUpload(it)) },
        )
    }
    Text(
        text = stringResource(R.string.strava_connected_hint),
        style = RideCompTheme.typography.caption,
        color = colors.textTertiary,
    )
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

/** "Wahyu Rama" → "WR". */
private fun String.initials(): String =
    split(" ").filter { it.isNotBlank() }.take(MAX_INITIALS).joinToString("") { it.first().uppercase() }

private const val MAX_INITIALS = 2

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
