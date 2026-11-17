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
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ridecomp.core.config.AutoPauseConfig
import com.ridecomp.core.config.LapConfig
import com.ridecomp.core.config.ReminderConfig
import com.ridecomp.core.config.Units
import com.ridecomp.core.engine.voice.VoiceSettings
import com.ridecomp.core.model.RideSettings
import com.ridecomp.core.model.ScreenMode
import com.ridecomp.core.model.UnitSystem
import com.ridecomp.feature.settings.R
import com.ridecomp.feature.settings.viewmodel.RideSettingsUiEvent
import com.ridecomp.feature.settings.viewmodel.RideSettingsViewModel
import com.ridecomp.ui.component.Choice
import com.ridecomp.ui.component.ChoiceChips
import com.ridecomp.ui.component.ScreenScaffold
import com.ridecomp.ui.component.SwitchRow
import com.ridecomp.ui.preview.RideCompPreview
import com.ridecomp.ui.preview.ThemePreviews
import com.ridecomp.ui.theme.RideCompDimens
import com.ridecomp.ui.theme.RideCompTheme
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import org.koin.androidx.compose.koinViewModel

@Composable
fun RideSettingsRoute(modifier: Modifier = Modifier, viewModel: RideSettingsViewModel = koinViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    RideSettingsScreen(settings = state, onEvent = viewModel::onEvent, modifier = modifier)
}

/** Auto pause, auto lap, reminders, voice, screen mode, battery saver and units (02-DESIGN §3.10). */
@Composable
fun RideSettingsScreen(settings: RideSettings, onEvent: (RideSettingsUiEvent) -> Unit, modifier: Modifier = Modifier) {
    ScreenScaffold(modifier = modifier, title = stringResource(R.string.ride_settings_title)) {
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()).padding(bottom = RideCompDimens.spacingL),
            verticalArrangement = Arrangement.spacedBy(RideCompDimens.spacingM),
        ) {
            SwitchRow(
                label = stringResource(R.string.ride_settings_auto_pause),
                description = stringResource(R.string.ride_settings_auto_pause_description),
                isChecked = settings.isAutoPauseEnabled,
                onCheckedChange = { onEvent(RideSettingsUiEvent.AutoPauseEnabled(it)) },
            )
            if (settings.isAutoPauseEnabled) {
                ChoiceChips(
                    label = stringResource(R.string.ride_settings_auto_pause_delay),
                    choices = AutoPauseConfig.DELAY_CHOICES_S.map {
                        Choice(it, stringResource(R.string.ride_settings_seconds, it))
                    }.toImmutableList(),
                    selected = settings.autoPauseDelayS,
                    onSelect = { onEvent(RideSettingsUiEvent.AutoPauseDelay(it)) },
                )
            }
            ChoiceChips(
                label = stringResource(R.string.ride_settings_auto_lap),
                choices = offOr(
                    LapConfig.AUTO_LAP_CHOICES_M.map {
                        Choice<Double?>(
                            it,
                            stringResource(R.string.ride_settings_km, (it / Units.METERS_PER_KM).toInt()),
                        )
                    },
                ),
                selected = settings.autoLapM,
                onSelect = { onEvent(RideSettingsUiEvent.AutoLap(it)) },
            )
            ReminderChoices(settings, onEvent)
            VoiceChoices(settings, onEvent)
            ScreenAndBattery(settings, onEvent)
        }
    }
}

@Composable
private fun ReminderChoices(settings: RideSettings, onEvent: (RideSettingsUiEvent) -> Unit) {
    val minuteChoices = ReminderConfig.INTERVAL_CHOICES_MIN.map {
        Choice<Int?>(it, stringResource(R.string.ride_settings_minutes, it))
    }
    ChoiceChips(
        label = stringResource(R.string.ride_settings_drink),
        choices = offOr(minuteChoices),
        selected = settings.drinkIntervalMin,
        onSelect = { onEvent(RideSettingsUiEvent.DrinkInterval(it)) },
    )
    ChoiceChips(
        label = stringResource(R.string.ride_settings_eat),
        choices = offOr(minuteChoices),
        selected = settings.eatIntervalMin,
        onSelect = { onEvent(RideSettingsUiEvent.EatInterval(it)) },
    )
}

/** Voice announcements (T7.1): every N km (or mi), laps; reminders are spoken too when on. */
@Composable
private fun VoiceChoices(settings: RideSettings, onEvent: (RideSettingsUiEvent) -> Unit) {
    val unitRes = if (settings.unitSystem == UnitSystem.METRIC) R.string.ride_settings_km else R.string.ride_settings_mi
    ChoiceChips(
        label = stringResource(R.string.ride_settings_voice),
        choices = offOr(VoiceSettings.EVERY_CHOICES.map { Choice<Int?>(it, stringResource(unitRes, it)) }),
        selected = settings.voice.everyUnits,
        onSelect = { onEvent(RideSettingsUiEvent.VoiceEvery(it)) },
    )
    Text(
        text = stringResource(R.string.ride_settings_voice_description),
        style = RideCompTheme.typography.caption,
        color = RideCompTheme.colors.textSecondary,
        modifier = Modifier.padding(horizontal = RideCompDimens.spacingM),
    )
    if (settings.voice.isEnabled) {
        SwitchRow(
            label = stringResource(R.string.ride_settings_voice_laps),
            isChecked = settings.voice.isLapEnabled,
            onCheckedChange = { onEvent(RideSettingsUiEvent.VoiceLaps(it)) },
        )
    }
}

@Composable
private fun ScreenAndBattery(settings: RideSettings, onEvent: (RideSettingsUiEvent) -> Unit) {
    ChoiceChips(
        label = stringResource(R.string.ride_settings_screen),
        choices = listOf(
            Choice(ScreenMode.ALWAYS_ON, stringResource(R.string.ride_settings_screen_always_on)),
            Choice(ScreenMode.AUTO_DIM, stringResource(R.string.ride_settings_screen_auto_dim)),
            Choice(ScreenMode.OFF, stringResource(R.string.ride_settings_screen_off)),
        ).toImmutableList(),
        selected = settings.screenMode,
        onSelect = { onEvent(RideSettingsUiEvent.Screen(it)) },
    )
    SwitchRow(
        label = stringResource(R.string.ride_settings_battery_saver),
        description = stringResource(R.string.ride_settings_battery_saver_description),
        isChecked = settings.isBatterySaverEnabled,
        onCheckedChange = { onEvent(RideSettingsUiEvent.BatterySaver(it)) },
    )
    SwitchRow(
        label = stringResource(R.string.ride_settings_volume_buttons),
        description = stringResource(R.string.ride_settings_volume_buttons_description),
        isChecked = settings.isVolumeButtonsEnabled,
        onCheckedChange = { onEvent(RideSettingsUiEvent.VolumeButtons(it)) },
    )
    ChoiceChips(
        label = stringResource(R.string.ride_settings_units),
        choices = listOf(
            Choice(UnitSystem.METRIC, stringResource(R.string.ride_settings_metric)),
            Choice(UnitSystem.IMPERIAL, stringResource(R.string.ride_settings_imperial)),
        ).toImmutableList(),
        selected = settings.unitSystem,
        onSelect = { onEvent(RideSettingsUiEvent.Units(it)) },
    )
}

/** Prepends an "Off" choice (value null) to optional settings. */
@Composable
private fun <T> offOr(choices: List<Choice<T?>>): ImmutableList<Choice<T?>> =
    (listOf(Choice<T?>(null, stringResource(R.string.ride_settings_off))) + choices).toImmutableList()

@ThemePreviews
@Composable
private fun RideSettingsScreenPreview() {
    RideCompPreview { RideSettingsScreen(settings = RideSettings(), onEvent = {}) }
}
