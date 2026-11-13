package com.ridecomp.feature.settings.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ridecomp.feature.settings.R
import com.ridecomp.feature.settings.viewmodel.OssComponent
import com.ridecomp.feature.settings.viewmodel.OssLicensesUiEvent
import com.ridecomp.feature.settings.viewmodel.OssLicensesUiState
import com.ridecomp.feature.settings.viewmodel.OssLicensesViewModel
import com.ridecomp.ui.component.ListCard
import com.ridecomp.ui.component.ListCardRow
import com.ridecomp.ui.component.ScreenScaffold
import com.ridecomp.ui.preview.RideCompPreview
import com.ridecomp.ui.preview.ThemePreviews
import com.ridecomp.ui.theme.RideCompDimens
import com.ridecomp.ui.theme.RideCompTheme
import kotlinx.collections.immutable.persistentListOf
import org.koin.androidx.compose.koinViewModel

/** Open source licenses entry point (D-65). */
@Composable
fun OssLicensesRoute(
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    viewModel: OssLicensesViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    OssLicensesScreen(state = state, onEvent = viewModel::onEvent, onBack = onBack, modifier = modifier)
}

/** Stateless screen: component list, or the selected license text (system back returns to the list). */
@Composable
fun OssLicensesScreen(
    state: OssLicensesUiState,
    onEvent: (OssLicensesUiEvent) -> Unit,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
) {
    val selected = state.selected
    BackHandler(enabled = selected != null) { onEvent(OssLicensesUiEvent.Close) }
    ScreenScaffold(
        modifier = modifier,
        title = stringResource(selected?.titleRes ?: R.string.licenses_title),
        subtitle = selected?.let { stringResource(it.licenseRes) },
        onBack = if (selected != null) ({ onEvent(OssLicensesUiEvent.Close) }) else onBack,
        backLabel = stringResource(R.string.onboarding_back_label),
    ) {
        if (selected == null) ComponentList(onEvent) else LicenseText(state)
    }
}

@Composable
private fun ComponentList(onEvent: (OssLicensesUiEvent) -> Unit) {
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
        Text(
            text = stringResource(R.string.licenses_intro),
            style = RideCompTheme.typography.caption,
            color = RideCompTheme.colors.textSecondary,
        )
        val components = OssComponent.entries
        ListCard {
            components.forEachIndexed { index, component ->
                ListCardRow(
                    title = stringResource(component.titleRes),
                    subtitle = stringResource(component.licenseRes),
                    hasDivider = index < components.lastIndex,
                    onClick = { onEvent(OssLicensesUiEvent.Open(component)) },
                )
            }
        }
    }
}

@Composable
private fun LicenseText(state: OssLicensesUiState) {
    LazyColumn(
        contentPadding = PaddingValues(
            start = RideCompDimens.screenPaddingHorizontal,
            end = RideCompDimens.screenPaddingHorizontal,
            bottom = RideCompDimens.screenPaddingBottom,
        ),
        verticalArrangement = Arrangement.spacedBy(RideCompDimens.cardGap),
    ) {
        items(state.paragraphs) { paragraph ->
            Text(text = paragraph, style = RideCompTheme.typography.caption, color = RideCompTheme.colors.textTertiary)
        }
    }
}

@ThemePreviews
@Composable
private fun OssLicensesListPreview() {
    RideCompPreview { OssLicensesScreen(state = OssLicensesUiState(), onEvent = {}) }
}

@ThemePreviews
@Composable
private fun OssLicensesTextPreview() {
    RideCompPreview {
        OssLicensesScreen(
            state = OssLicensesUiState(
                selected = OssComponent.MAPLIBRE_NATIVE,
                paragraphs = persistentListOf("BSD 2-Clause License", "Copyright (c) 2021 MapLibre contributors"),
            ),
            onEvent = {},
        )
    }
}
