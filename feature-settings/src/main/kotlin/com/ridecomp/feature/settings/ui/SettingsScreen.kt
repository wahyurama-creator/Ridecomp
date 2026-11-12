package com.ridecomp.feature.settings.ui

import android.os.Build
import androidx.annotation.StringRes
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.ridecomp.feature.settings.R
import com.ridecomp.ui.component.ListRow
import com.ridecomp.ui.component.ScreenScaffold
import com.ridecomp.ui.preview.RideCompPreview
import com.ridecomp.ui.preview.ThemePreviews

/**
 * Settings sections (02-DESIGN §3.10) in screen order. [taskId] marks sections delivered in a later
 * roadmap task; they are listed but not yet openable.
 */
enum class SettingsSection(
    @param:StringRes val titleRes: Int,
    @param:StringRes val subtitleRes: Int? = null,
    val taskId: String? = null,
) {
    PROFILE(R.string.settings_profile, R.string.settings_profile_subtitle),
    RIDE_SCREEN_BATTERY(R.string.settings_screen_battery),
    DATA_PAGES(R.string.settings_data_pages, taskId = "T2.8"),
    SAFETY(R.string.settings_safety, taskId = "T2.14"),
    BACKUP(R.string.settings_backup, taskId = "T2.17"),
    OFFLINE_MAPS(R.string.settings_offline_maps, taskId = "T3.4"),
    LANGUAGE(R.string.settings_language),
    BATTERY_HELP(R.string.settings_battery_help),
    ;

    /** Per-app language lives in Android settings from Android 13 (locales_config). */
    val isAvailable: Boolean
        get() = taskId == null && (this != LANGUAGE || Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
}

/** Settings entry point (UI-01). */
@Composable
fun SettingsRoute(onSectionClick: (SettingsSection) -> Unit, modifier: Modifier = Modifier) {
    SettingsScreen(onSectionClick = onSectionClick, modifier = modifier)
}

/** Stateless list of settings sections. */
@Composable
fun SettingsScreen(onSectionClick: (SettingsSection) -> Unit, modifier: Modifier = Modifier) {
    ScreenScaffold(modifier = modifier, title = stringResource(R.string.settings_title)) {
        LazyColumn {
            items(SettingsSection.entries, key = { it.name }) { section ->
                ListRow(
                    title = stringResource(section.titleRes),
                    subtitle = if (section.isAvailable) {
                        section.subtitleRes?.let { stringResource(it) }
                    } else {
                        stringResource(R.string.settings_coming_soon)
                    },
                    onClick = if (section.isAvailable) {
                        { onSectionClick(section) }
                    } else {
                        null
                    },
                )
            }
        }
    }
}

@ThemePreviews
@Composable
private fun SettingsScreenPreview() {
    RideCompPreview { SettingsScreen(onSectionClick = {}) }
}
