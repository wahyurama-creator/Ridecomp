package com.ridecomp.feature.settings.ui

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

/** Settings sections (02-DESIGN §3.10). Order here is the order on screen. */
enum class SettingsSection(@param:StringRes val titleRes: Int, @param:StringRes val subtitleRes: Int? = null) {
    PROFILE(R.string.settings_profile, R.string.settings_profile_subtitle),
    DATA_PAGES(R.string.settings_data_pages),
    SCREEN_BATTERY(R.string.settings_screen_battery),
    SAFETY(R.string.settings_safety),
    OFFLINE_MAPS(R.string.settings_offline_maps),
    UNITS(R.string.settings_units),
    LANGUAGE(R.string.settings_language),
    BACKUP(R.string.settings_backup),
    BATTERY_HELP(R.string.settings_battery_help),
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
                    subtitle = section.subtitleRes?.let { stringResource(it) },
                    onClick = { onSectionClick(section) },
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
