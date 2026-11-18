package com.ridecomp.feature.settings.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ridecomp.core.engine.update.AppRelease
import com.ridecomp.core.engine.update.UpdateCheckResult
import com.ridecomp.core.usecase.UpdateSettings
import com.ridecomp.feature.settings.R
import com.ridecomp.feature.settings.viewmodel.AppUpdateEffect
import com.ridecomp.feature.settings.viewmodel.AppUpdateUiEvent
import com.ridecomp.feature.settings.viewmodel.AppUpdateUiState
import com.ridecomp.feature.settings.viewmodel.AppUpdateViewModel
import com.ridecomp.ui.R as UiR
import com.ridecomp.ui.component.BigButton
import com.ridecomp.ui.component.BigButtonStyle
import com.ridecomp.ui.component.ListCard
import com.ridecomp.ui.component.ListCardRow
import com.ridecomp.ui.component.ScreenScaffold
import com.ridecomp.ui.component.SwitchRow
import com.ridecomp.ui.preview.RideCompPreview
import com.ridecomp.ui.preview.ThemePreviews
import com.ridecomp.ui.theme.RideCompDimens
import com.ridecomp.ui.theme.RideCompTheme
import com.ridecomp.ui.udf.ObserveEffects
import org.koin.androidx.compose.koinViewModel

/** Setelan → Pembaruan aplikasi (T10.9). */
@Composable
fun AppUpdateRoute(
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    viewModel: AppUpdateViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val uriHandler = LocalUriHandler.current
    ObserveEffects(viewModel.effects) { effect ->
        when (effect) {
            is AppUpdateEffect.OpenUrl -> uriHandler.openUri(effect.url)
        }
    }
    AppUpdateScreen(state = state, onEvent = viewModel::onEvent, onBack = onBack, modifier = modifier)
}

@Composable
fun AppUpdateScreen(
    state: AppUpdateUiState,
    onEvent: (AppUpdateUiEvent) -> Unit,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
) {
    ScreenScaffold(
        modifier = modifier,
        title = stringResource(R.string.update_title),
        onBack = onBack,
        backLabel = stringResource(R.string.onboarding_back_label),
    ) {
        val settings = state.settings ?: return@ScreenScaffold
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
            ListCard {
                ListCardRow(
                    title = stringResource(R.string.update_installed),
                    value = settings.installedVersion,
                    hasDivider = false,
                )
            }
            ListCard {
                SwitchRow(
                    label = stringResource(R.string.update_auto),
                    description = stringResource(R.string.update_auto_hint),
                    isChecked = settings.isAutoCheckEnabled,
                    onCheckedChange = { onEvent(AppUpdateUiEvent.SetAutoCheck(it)) },
                )
            }
            CheckResult(state, settings)
            settings.available?.let { release -> DownloadButton(release, onEvent) }
            BigButton(
                text = stringResource(if (state.isChecking) R.string.update_checking else R.string.update_check_now),
                onClick = { onEvent(AppUpdateUiEvent.CheckNow) },
                isEnabled = !state.isChecking,
                style = BigButtonStyle.OUTLINE,
            )
            Text(
                text = stringResource(R.string.update_how),
                style = RideCompTheme.typography.caption,
                color = RideCompTheme.colors.textSecondary,
            )
        }
    }
}

@Composable
private fun CheckResult(state: AppUpdateUiState, settings: UpdateSettings) {
    val message = when {
        settings.available != null -> null
        state.lastResult == UpdateCheckResult.UP_TO_DATE -> R.string.update_up_to_date
        state.lastResult == UpdateCheckResult.FAILED -> R.string.update_failed
        else -> null
    } ?: return
    Text(
        text = stringResource(message),
        style = RideCompTheme.typography.body,
        color = if (state.lastResult == UpdateCheckResult.FAILED) {
            RideCompTheme.colors.warnText
        } else {
            RideCompTheme.colors.ok
        },
    )
}

@Composable
private fun DownloadButton(release: AppRelease, onEvent: (AppUpdateUiEvent) -> Unit) {
    BigButton(
        text = stringResource(R.string.update_download, release.version),
        onClick = { onEvent(AppUpdateUiEvent.OpenRelease) },
        iconRes = UiR.drawable.ic_download,
    )
}

@ThemePreviews
@Composable
private fun AppUpdateScreenPreview() {
    RideCompPreview {
        AppUpdateScreen(
            state = AppUpdateUiState(
                settings = UpdateSettings(
                    installedVersion = "0.9.1",
                    isAutoCheckEnabled = true,
                    lastCheckMs = null,
                    available = AppRelease(
                        "0.9.2",
                        "https://github.com/wahyurama-creator/Ridecomp/releases/tag/v0.9.2",
                    ),
                ),
            ),
            onEvent = {},
        )
    }
}
