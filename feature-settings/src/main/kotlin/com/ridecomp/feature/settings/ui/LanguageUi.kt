package com.ridecomp.feature.settings.ui

import android.os.Build
import androidx.activity.compose.LocalActivity
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
import com.ridecomp.core.model.AppLanguage
import com.ridecomp.feature.settings.R
import com.ridecomp.feature.settings.viewmodel.LanguageEffect
import com.ridecomp.feature.settings.viewmodel.LanguageViewModel
import com.ridecomp.ui.component.CardTone
import com.ridecomp.ui.component.RideCompCard
import com.ridecomp.ui.component.ScreenScaffold
import com.ridecomp.ui.preview.RideCompPreview
import com.ridecomp.ui.preview.ThemePreviews
import com.ridecomp.ui.theme.RideCompDimens
import com.ridecomp.ui.theme.RideCompTheme
import com.ridecomp.ui.udf.ObserveEffects
import org.koin.androidx.compose.koinViewModel

/** The language list with the selected one highlighted; a tap applies it at once. */
@Composable
fun LanguageChooser(selected: AppLanguage, onSelect: (AppLanguage) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(RideCompDimens.cardGap)) {
        LANGUAGES.forEach { language ->
            RideCompCard(
                tone = if (language == selected) CardTone.ACCENT else CardTone.NORMAL,
                onClick = { onSelect(language) },
            ) {
                Text(
                    text = stringResource(language.nameRes()),
                    style = RideCompTheme.typography.cardTitle,
                    color = RideCompTheme.colors.textPrimary,
                )
                language.hintRes()?.let {
                    Text(
                        text = stringResource(it),
                        style = RideCompTheme.typography.caption,
                        color = RideCompTheme.colors.textSecondary,
                    )
                }
            }
        }
    }
}

/** The chooser wired to [LanguageViewModel]; Android 8–12 redraw the screen in the new language. */
@Composable
fun LanguagePicker(modifier: Modifier = Modifier, viewModel: LanguageViewModel = koinViewModel()) {
    val selected by viewModel.uiState.collectAsStateWithLifecycle()
    val activity = LocalActivity.current
    ObserveEffects(viewModel.effects) { effect ->
        when (effect) {
            LanguageEffect.Recreate -> if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) activity?.recreate()
        }
    }
    LanguageChooser(selected = selected, onSelect = viewModel::onEvent, modifier = modifier)
}

/** Settings → Language. */
@Composable
fun LanguageRoute(onBack: () -> Unit, modifier: Modifier = Modifier) {
    ScreenScaffold(
        modifier = modifier,
        title = stringResource(R.string.language_title),
        onBack = onBack,
        backLabel = stringResource(R.string.language_back),
    ) {
        LanguagePicker(modifier = Modifier.padding(horizontal = RideCompDimens.screenPaddingHorizontal))
    }
}

/** Indonesian first: the app's main audience. */
private val LANGUAGES = listOf(AppLanguage.INDONESIAN, AppLanguage.ENGLISH, AppLanguage.SYSTEM)

@StringRes
internal fun AppLanguage.nameRes(): Int = when (this) {
    AppLanguage.SYSTEM -> R.string.language_system
    AppLanguage.INDONESIAN -> R.string.language_indonesian
    AppLanguage.ENGLISH -> R.string.language_english
}

@StringRes
private fun AppLanguage.hintRes(): Int? = when (this) {
    AppLanguage.SYSTEM -> R.string.language_system_hint
    AppLanguage.INDONESIAN, AppLanguage.ENGLISH -> null
}

@ThemePreviews
@Composable
private fun LanguageChooserPreview() {
    RideCompPreview { LanguageChooser(selected = AppLanguage.INDONESIAN, onSelect = {}) }
}
