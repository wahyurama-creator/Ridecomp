package com.ridecomp.feature.settings.ui

import android.os.Build
import android.text.format.Formatter
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ridecomp.core.model.RideSettings
import com.ridecomp.core.model.ScreenMode
import com.ridecomp.feature.settings.R
import com.ridecomp.feature.settings.viewmodel.SettingsUiState
import com.ridecomp.feature.settings.viewmodel.SettingsViewModel
import com.ridecomp.ui.component.ListCard
import com.ridecomp.ui.component.ListCardRow
import com.ridecomp.ui.component.ScreenScaffold
import com.ridecomp.ui.component.ValueTone
import com.ridecomp.ui.component.WarningCard
import com.ridecomp.ui.preview.RideCompPreview
import com.ridecomp.ui.preview.ThemePreviews
import com.ridecomp.ui.theme.RideCompDimens
import com.ridecomp.ui.theme.RideCompTheme
import org.koin.androidx.compose.koinViewModel

/**
 * Settings sections (02-DESIGN §3.10, 13-UI-SCREENS #13). [taskId] marks sections delivered in a later
 * roadmap task; they are listed but not yet openable. Upload to Strava (Phase 5) is not listed.
 */
enum class SettingsSection(@param:StringRes val titleRes: Int, val taskId: String? = null) {
    PROFILE(R.string.settings_profile),
    BIKES(R.string.settings_bikes),
    ACTIVITY_PROFILES(R.string.settings_activity_profiles),
    RIDE_SCREEN_BATTERY(R.string.settings_screen_battery),
    AUTO_PAUSE(R.string.settings_auto_pause),
    REMINDERS(R.string.settings_reminders),
    SAFETY(R.string.settings_safety),
    DATA_PAGES(R.string.settings_data_pages),
    OFFLINE_MAPS(R.string.settings_offline_maps),
    DISPLAY(R.string.settings_display),
    LANGUAGE(R.string.settings_language),
    BACKUP(R.string.settings_backup),
    STRAVA(R.string.settings_strava),
    BATTERY_HELP(R.string.settings_battery_help),
    OPEN_SOURCE_LICENSES(R.string.settings_licenses),
    APP_UPDATE(R.string.settings_update),
    HELP(R.string.guide_help_title),

    /** Debug builds only (simulated GPS, 07-TEST-PLAN §2). */
    DEVELOPER(R.string.settings_developer),
    ;

    val isAvailable: Boolean
        get() = taskId == null
}

/** The design's two cards (13-UI-SCREENS #13). */
private val RIDE_SECTIONS = listOf(
    SettingsSection.PROFILE,
    SettingsSection.BIKES,
    SettingsSection.ACTIVITY_PROFILES,
    SettingsSection.RIDE_SCREEN_BATTERY,
    SettingsSection.AUTO_PAUSE,
    SettingsSection.REMINDERS,
    SettingsSection.SAFETY,
)
private val APP_SECTIONS = listOf(
    SettingsSection.HELP,
    SettingsSection.DATA_PAGES,
    SettingsSection.OFFLINE_MAPS,
    SettingsSection.DISPLAY,
    SettingsSection.LANGUAGE,
    SettingsSection.BACKUP,
    SettingsSection.STRAVA,
    SettingsSection.APP_UPDATE,
    SettingsSection.OPEN_SOURCE_LICENSES,
    SettingsSection.DEVELOPER,
)

/** Settings entry point (UI-01). */
@Composable
fun SettingsRoute(
    onSectionClick: (SettingsSection) -> Unit,
    modifier: Modifier = Modifier,
    isDeveloperMenuVisible: Boolean = false,
    viewModel: SettingsViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LifecycleResumeEffect(Unit) {
        viewModel.onEvent(Unit)
        onPauseOrDispose {}
    }
    SettingsScreen(
        state = state,
        onSectionClick = onSectionClick,
        modifier = modifier,
        isDeveloperMenuVisible = isDeveloperMenuVisible,
    )
}

/** Stateless settings overview. */
@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onSectionClick: (SettingsSection) -> Unit,
    modifier: Modifier = Modifier,
    isDeveloperMenuVisible: Boolean = false,
) {
    val brand = remember { PhoneBrand.of(Build.MANUFACTURER) }
    ScreenScaffold(modifier = modifier, title = stringResource(R.string.settings_title)) {
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
            WarningCard(
                title = stringResource(R.string.settings_battery_help),
                message = if (brand == PhoneBrand.OTHER) {
                    stringResource(R.string.settings_battery_help_generic)
                } else {
                    stringResource(R.string.settings_battery_help_brand, Build.MANUFACTURER)
                },
                onClick = { onSectionClick(SettingsSection.BATTERY_HELP) },
            )
            SectionCard(RIDE_SECTIONS, state, onSectionClick)
            SectionCard(
                APP_SECTIONS.filter {
                    it != SettingsSection.DEVELOPER || isDeveloperMenuVisible
                },
                state,
                onSectionClick,
            )
            Text(
                text = stringResource(R.string.settings_footer),
                style = RideCompTheme.typography.caption,
                color = RideCompTheme.colors.disabled,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun SectionCard(
    sections: List<SettingsSection>,
    state: SettingsUiState,
    onSectionClick: (SettingsSection) -> Unit,
) {
    ListCard {
        sections.forEachIndexed { index, section ->
            val (value, tone) = section.summary(state)
            ListCardRow(
                title = stringResource(section.titleRes),
                value = if (section.isAvailable) value else stringResource(R.string.settings_coming_soon),
                valueTone = tone,
                onClick = if (section.isAvailable) {
                    { onSectionClick(section) }
                } else {
                    null
                },
                hasDivider = index < sections.lastIndex,
            )
        }
    }
}

/** Short current value of a section ("68 kg · FTP —", "Aktif · 2 kontak"). */
@Composable
private fun SettingsSection.summary(state: SettingsUiState): Pair<String?, ValueTone> {
    val settings = state.rideSettings
    return when (this) {
        SettingsSection.PROFILE -> profileSummary(state) to ValueTone.NORMAL
        SettingsSection.BIKES, SettingsSection.ACTIVITY_PROFILES -> countSummary(state) to ValueTone.NORMAL
        SettingsSection.RIDE_SCREEN_BATTERY -> stringResource(settings.screenMode.labelRes()) to ValueTone.NORMAL
        SettingsSection.AUTO_PAUSE -> autoPauseSummary(settings)
        SettingsSection.REMINDERS -> stringResource(
            R.string.settings_reminders_value,
            settings.drinkIntervalMin?.toString() ?: stringResource(R.string.settings_none),
            settings.eatIntervalMin?.toString() ?: stringResource(R.string.settings_none),
        ) to ValueTone.NORMAL
        SettingsSection.SAFETY -> safetySummary(state)
        SettingsSection.DISPLAY, SettingsSection.LANGUAGE -> appearanceSummary(state) to ValueTone.NORMAL
        SettingsSection.BACKUP -> stringResource(R.string.settings_backup_value) to ValueTone.NORMAL
        SettingsSection.OFFLINE_MAPS -> offlineMapsSummary(state) to ValueTone.NORMAL
        SettingsSection.STRAVA -> stravaSummary(state)
        SettingsSection.DATA_PAGES, SettingsSection.BATTERY_HELP,
        SettingsSection.OPEN_SOURCE_LICENSES, SettingsSection.HELP, SettingsSection.DEVELOPER,
        SettingsSection.APP_UPDATE,
        -> null to ValueTone.NORMAL
    }
}

/** The chosen theme ("Dark") or language. */
@Composable
private fun SettingsSection.appearanceSummary(state: SettingsUiState): String = stringResource(
    if (this == SettingsSection.DISPLAY) state.theme.nameRes() else state.language.nameRes(),
)

/** "2 bikes", "5 profiles · data pages". */
@Composable
private fun SettingsSection.countSummary(state: SettingsUiState): String = if (this == SettingsSection.BIKES) {
    pluralStringResource(R.plurals.settings_bikes_value, state.bikeCount, state.bikeCount)
} else {
    pluralStringResource(
        R.plurals.settings_activity_profiles_value,
        state.activityProfileCount,
        state.activityProfileCount,
    )
}

@Composable
private fun stravaSummary(state: SettingsUiState): Pair<String, ValueTone> = if (state.isStravaConnected) {
    stringResource(R.string.settings_strava_connected) to ValueTone.OK
} else {
    stringResource(R.string.settings_strava_not_connected) to ValueTone.NORMAL
}

/** "Online · Wi‑Fi & data", plus the offline areas when there are some (D-90). */
@Composable
private fun offlineMapsSummary(state: SettingsUiState): String {
    val online =
        stringResource(R.string.settings_maps_online_value, stringResource(state.rideSettings.onlineMapMode.labelRes()))
    if (state.offlineMapCount == 0) return online
    return online + SUMMARY_SEPARATOR + pluralStringResource(
        R.plurals.settings_offline_maps_value,
        state.offlineMapCount,
        state.offlineMapCount,
        Formatter.formatShortFileSize(LocalContext.current, state.offlineMapBytes),
    )
}

private const val SUMMARY_SEPARATOR = " · "

@Composable
private fun profileSummary(state: SettingsUiState): String? = state.profile?.let { profile ->
    stringResource(
        R.string.settings_profile_value,
        profile.weightKg.plain(),
        profile.ftpW?.toString() ?: stringResource(R.string.settings_none),
    )
}

@Composable
private fun autoPauseSummary(settings: RideSettings): Pair<String, ValueTone> = if (settings.isAutoPauseEnabled) {
    stringResource(R.string.settings_auto_pause_on, settings.autoPauseDelayS) to ValueTone.OK
} else {
    stringResource(R.string.settings_off) to ValueTone.NORMAL
}

@Composable
private fun safetySummary(state: SettingsUiState): Pair<String?, ValueTone> {
    val safety = state.safety ?: return null to ValueTone.NORMAL
    return if (safety.settings.isEnabled) {
        pluralStringResource(R.plurals.settings_safety_on, safety.contacts.size, safety.contacts.size) to ValueTone.OK
    } else {
        stringResource(R.string.settings_off) to ValueTone.NORMAL
    }
}

@StringRes
private fun ScreenMode.labelRes(): Int = when (this) {
    ScreenMode.ALWAYS_ON -> R.string.ride_settings_screen_always_on
    ScreenMode.AUTO_DIM -> R.string.ride_settings_screen_auto_dim
    ScreenMode.OFF -> R.string.settings_screen_off
}

/** "68" or "68.5" without a trailing ".0". */
private fun Double.plain(): String = if (this % 1.0 == 0.0) toLong().toString() else toString()

@ThemePreviews
@Composable
private fun SettingsScreenPreview() {
    RideCompPreview { SettingsScreen(state = SettingsUiState(activityProfileCount = 5), onSectionClick = {}) }
}
