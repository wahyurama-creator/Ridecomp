package com.ridecomp.feature.ride.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ridecomp.core.model.DataPageLayout
import com.ridecomp.core.model.DataPageLimits
import com.ridecomp.feature.ride.R
import com.ridecomp.feature.ride.model.DefaultDataPages
import com.ridecomp.feature.ride.model.RideMetric
import com.ridecomp.feature.ride.model.RideMetricKeys
import com.ridecomp.feature.ride.model.toLayout
import com.ridecomp.feature.ride.viewmodel.DataPagesEditorEffect
import com.ridecomp.feature.ride.viewmodel.DataPagesEditorUiEvent
import com.ridecomp.feature.ride.viewmodel.DataPagesEditorUiState
import com.ridecomp.feature.ride.viewmodel.DataPagesEditorViewModel
import com.ridecomp.ui.component.BigButton
import com.ridecomp.ui.component.BigButtonStyle
import com.ridecomp.ui.component.Choice
import com.ridecomp.ui.component.ChoiceChips
import com.ridecomp.ui.component.DataGrid
import com.ridecomp.ui.component.PickerDialog
import com.ridecomp.ui.component.ScreenScaffold
import com.ridecomp.ui.component.SwitchRow
import com.ridecomp.ui.preview.RideCompPreview
import com.ridecomp.ui.preview.ThemePreviews
import com.ridecomp.ui.theme.RideCompDimens
import com.ridecomp.ui.theme.RideCompTheme
import com.ridecomp.ui.udf.ObserveEffects
import kotlinx.collections.immutable.toImmutableList
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

/** Data page editor entry point (T2.8). */
@Composable
fun DataPagesEditorRoute(
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    /** A profile's own pages, or the general pages when null. */
    profileId: Long? = null,
    viewModel: DataPagesEditorViewModel = koinViewModel(key = profileId?.toString()) { parametersOf(profileId) },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ObserveEffects(viewModel.effects) { effect ->
        when (effect) {
            DataPagesEditorEffect.Saved -> onDone()
        }
    }
    DataPagesEditorScreen(state = state, onEvent = viewModel::onEvent, modifier = modifier)
}

/** Stateless editor: page chips, field count, a tappable preview of the grid, save/reset. */
@Composable
fun DataPagesEditorScreen(
    state: DataPagesEditorUiState,
    onEvent: (DataPagesEditorUiEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    ScreenScaffold(modifier = modifier, title = stringResource(R.string.data_pages_title)) {
        val page = state.page
        // Loading from the local DB takes a few ms: nothing is drawn meanwhile.
        if (!state.isLoaded || page == null) {
            return@ScreenScaffold
        }
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(RideCompDimens.spacingM),
        ) {
            PageChips(state, onEvent)
            ChoiceChips(
                label = stringResource(R.string.data_pages_field_count),
                choices = remember { DataPageLimits.FIELD_COUNTS.map { Choice(it, it.toString()) }.toImmutableList() },
                selected = page.fields.size,
                onSelect = { onEvent(DataPagesEditorUiEvent.SetFieldCount(it)) },
            )
            SwitchRow(
                label = stringResource(R.string.data_pages_first_full_width),
                isChecked = page.isFirstFullWidth,
                onCheckedChange = { onEvent(DataPagesEditorUiEvent.SetFirstFullWidth(it)) },
            )
            Text(
                text = stringResource(R.string.data_pages_tap_hint),
                style = RideCompTheme.typography.body,
                color = RideCompTheme.colors.textSecondary,
                modifier = Modifier.padding(horizontal = RideCompDimens.spacingM),
            )
            PagePreview(page, onPick = { onEvent(DataPagesEditorUiEvent.PickField(it)) })
            PageButtons(state, onEvent)
        }
        BigButton(
            text = stringResource(R.string.data_pages_save),
            onClick = { onEvent(DataPagesEditorUiEvent.Save) },
            modifier = Modifier.padding(RideCompDimens.spacingM),
        )
    }
    state.pickingField?.let { field -> MetricPicker(state.page?.fields?.getOrNull(field), onEvent) }
}

@Composable
private fun PageChips(state: DataPagesEditorUiState, onEvent: (DataPagesEditorUiEvent) -> Unit) {
    val pageLabel = stringResource(R.string.data_pages_page)
    ChoiceChips(
        label = pageLabel,
        choices = state.pages.indices.map { Choice(it, (it + 1).toString()) }.toImmutableList(),
        selected = state.selectedPage,
        onSelect = { onEvent(DataPagesEditorUiEvent.SelectPage(it)) },
    )
}

@Composable
private fun PagePreview(page: DataPageLayout, onPick: (Int) -> Unit) {
    DataGrid(
        count = page.fields.size,
        isFirstFullWidth = page.isFirstFullWidth,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = RideCompDimens.spacingM)
            .aspectRatio(RideCompDimens.DATA_PAGE_PREVIEW_ASPECT_RATIO),
    ) { index, cellModifier ->
        val metric = RideMetricKeys.metricOf(page.fields[index])
        Box(
            modifier = cellModifier
                .background(RideCompTheme.colors.bg)
                .clickable(role = Role.Button) { onPick(index) },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = metric?.let { stringResource(it.labelRes) }.orEmpty(),
                style = RideCompTheme.typography.body,
                color = RideCompTheme.colors.textPrimary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(RideCompDimens.spacingXs),
            )
        }
    }
}

@Composable
private fun PageButtons(state: DataPagesEditorUiState, onEvent: (DataPagesEditorUiEvent) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = RideCompDimens.spacingM),
        horizontalArrangement = Arrangement.spacedBy(RideCompDimens.spacingS),
    ) {
        BigButton(
            text = stringResource(R.string.data_pages_add),
            onClick = { onEvent(DataPagesEditorUiEvent.AddPage) },
            style = BigButtonStyle.SECONDARY,
            isEnabled = state.canAddPage,
            modifier = Modifier.weight(1f),
        )
        BigButton(
            text = stringResource(R.string.data_pages_remove),
            onClick = { onEvent(DataPagesEditorUiEvent.RemovePage) },
            style = BigButtonStyle.DANGER,
            isEnabled = state.canRemovePage,
            modifier = Modifier.weight(1f),
        )
    }
    BigButton(
        text = stringResource(R.string.data_pages_reset),
        onClick = { onEvent(DataPagesEditorUiEvent.ResetToDefaults) },
        style = BigButtonStyle.SECONDARY,
        modifier = Modifier.fillMaxWidth().padding(horizontal = RideCompDimens.spacingM),
    )
}

@Composable
private fun MetricPicker(currentKey: String?, onEvent: (DataPagesEditorUiEvent) -> Unit) {
    val choices = RideMetric.entries.map { Choice(it, stringResource(it.labelRes)) }.toImmutableList()
    PickerDialog(
        title = stringResource(R.string.data_pages_choose_metric),
        choices = choices,
        selected = currentKey?.let(RideMetricKeys::metricOf),
        dismissLabel = stringResource(R.string.data_pages_cancel),
        onPick = { onEvent(DataPagesEditorUiEvent.ChooseMetric(it)) },
        onDismiss = { onEvent(DataPagesEditorUiEvent.DismissPicker) },
    )
}

@ThemePreviews
@Composable
private fun DataPagesEditorPreview() {
    RideCompPreview {
        DataPagesEditorScreen(
            state = DataPagesEditorUiState(
                pages = DefaultDataPages.ALL.toLayout().pages.toImmutableList(),
                isLoaded = true,
            ),
            onEvent = {},
        )
    }
}
