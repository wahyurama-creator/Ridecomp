package com.ridecomp.feature.settings.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ridecomp.core.model.AppTheme
import com.ridecomp.feature.settings.R
import com.ridecomp.feature.settings.viewmodel.DisplayViewModel
import com.ridecomp.ui.component.CardTone
import com.ridecomp.ui.component.RideCompCard
import com.ridecomp.ui.component.ScreenScaffold
import com.ridecomp.ui.component.SectionHeader
import com.ridecomp.ui.preview.RideCompPreview
import com.ridecomp.ui.preview.ThemePreviews
import com.ridecomp.ui.theme.RideCompDimens
import com.ridecomp.ui.theme.RideCompTheme
import org.koin.androidx.compose.koinViewModel

/** Settings → Display (T11.9, D-110). */
@Composable
fun DisplayRoute(onBack: () -> Unit, modifier: Modifier = Modifier, viewModel: DisplayViewModel = koinViewModel()) {
    val selected by viewModel.uiState.collectAsStateWithLifecycle()
    ScreenScaffold(
        modifier = modifier,
        title = stringResource(R.string.display_title),
        onBack = onBack,
        backLabel = stringResource(R.string.language_back),
    ) {
        ThemeChooser(
            selected = selected,
            onSelect = viewModel::onEvent,
            modifier = Modifier.padding(horizontal = RideCompDimens.screenPaddingHorizontal),
        )
    }
}

/** The three themes with the selected one highlighted; a tap applies it at once. */
@Composable
fun ThemeChooser(selected: AppTheme, onSelect: (AppTheme) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(RideCompDimens.cardGap)) {
        SectionHeader(text = stringResource(R.string.display_theme))
        THEMES.forEach { theme ->
            RideCompCard(
                tone = if (theme == selected) CardTone.ACCENT else CardTone.NORMAL,
                onClick = { onSelect(theme) },
            ) {
                Text(
                    text = stringResource(theme.nameRes()),
                    style = RideCompTheme.typography.cardTitle,
                    color = RideCompTheme.colors.textPrimary,
                )
                Text(
                    text = stringResource(theme.hintRes()),
                    style = RideCompTheme.typography.caption,
                    color = RideCompTheme.colors.textSecondary,
                )
            }
        }
    }
}

private val THEMES = listOf(AppTheme.DARK, AppTheme.LIGHT, AppTheme.SYSTEM)

@StringRes
internal fun AppTheme.nameRes(): Int = when (this) {
    AppTheme.DARK -> R.string.display_theme_dark
    AppTheme.LIGHT -> R.string.display_theme_light
    AppTheme.SYSTEM -> R.string.display_theme_system
}

@StringRes
private fun AppTheme.hintRes(): Int = when (this) {
    AppTheme.DARK -> R.string.display_theme_dark_hint
    AppTheme.LIGHT -> R.string.display_theme_light_hint
    AppTheme.SYSTEM -> R.string.display_theme_system_hint
}

@ThemePreviews
@Composable
private fun ThemeChooserPreview() {
    RideCompPreview { ThemeChooser(selected = AppTheme.DARK, onSelect = {}) }
}
