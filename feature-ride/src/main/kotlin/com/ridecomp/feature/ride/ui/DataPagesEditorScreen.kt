package com.ridecomp.feature.ride.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ridecomp.core.model.DataPageLayout
import com.ridecomp.core.model.DataPageLimits
import com.ridecomp.feature.ride.R
import com.ridecomp.feature.ride.model.DefaultDataPages
import com.ridecomp.feature.ride.model.MetricCategory
import com.ridecomp.feature.ride.model.RideMetric
import com.ridecomp.feature.ride.model.RideMetricKeys
import com.ridecomp.feature.ride.model.toLayout
import com.ridecomp.feature.ride.viewmodel.DataPagesEditorEffect
import com.ridecomp.feature.ride.viewmodel.DataPagesEditorUiEvent
import com.ridecomp.feature.ride.viewmodel.DataPagesEditorUiState
import com.ridecomp.feature.ride.viewmodel.DataPagesEditorViewModel
import com.ridecomp.ui.R as UiR
import com.ridecomp.ui.component.BigButton
import com.ridecomp.ui.component.BigButtonSize
import com.ridecomp.ui.component.BigButtonStyle
import com.ridecomp.ui.component.Choice
import com.ridecomp.ui.component.ChoiceChips
import com.ridecomp.ui.component.DataGrid
import com.ridecomp.ui.component.LinkText
import com.ridecomp.ui.component.ListCardRow
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

/** Data page editor entry point (T2.8, 13-UI-SCREENS #24). */
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
    DataPagesEditorScreen(state = state, onEvent = viewModel::onEvent, onBack = onDone, modifier = modifier)
}

/** Stateless editor: page chips (+ add), field count, a tappable preview, metric sheet, save/reset. */
@Composable
fun DataPagesEditorScreen(
    state: DataPagesEditorUiState,
    onEvent: (DataPagesEditorUiEvent) -> Unit,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
) {
    ScreenScaffold(
        modifier = modifier,
        title = stringResource(R.string.data_pages_title),
        onBack = onBack,
        backLabel = stringResource(R.string.data_pages_cancel),
    ) {
        val page = state.page
        // Loading from the local DB takes a few ms: nothing is drawn meanwhile.
        if (!state.isLoaded || page == null) {
            return@ScreenScaffold
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = RideCompDimens.screenPaddingHorizontal),
            verticalArrangement = Arrangement.spacedBy(RideCompDimens.blockGap),
        ) {
            PageChips(state, onEvent)
            ChoiceChips(
                label = stringResource(R.string.data_pages_field_count),
                choices = remember { DataPageLimits.FIELD_COUNTS.map { Choice(it, it.toString()) }.toImmutableList() },
                selected = page.fields.size,
                onSelect = { onEvent(DataPagesEditorUiEvent.SetFieldCount(it)) },
                horizontalPadding = RideCompDimens.none,
                columns = FIELD_COUNT_COLUMNS,
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
            )
            PagePreview(page, selected = state.pickingField, onPick = { onEvent(DataPagesEditorUiEvent.PickField(it)) })
            if (state.canRemovePage) {
                BigButton(
                    text = stringResource(R.string.data_pages_remove),
                    onClick = { onEvent(DataPagesEditorUiEvent.RemovePage) },
                    style = BigButtonStyle.TEXT_DANGER,
                )
            }
            LinkText(stringResource(R.string.data_pages_reset), onClick = {
                onEvent(DataPagesEditorUiEvent.ResetToDefaults)
            })
        }
        BigButton(
            text = stringResource(R.string.data_pages_save),
            onClick = { onEvent(DataPagesEditorUiEvent.Save) },
            size = BigButtonSize.SECONDARY,
            modifier = Modifier.padding(RideCompDimens.screenPaddingHorizontal),
        )
    }
    state.pickingField?.let { field -> MetricSheet(field, state.page?.fields?.getOrNull(field), onEvent) }
}

/** Counts 1–8 in two rows of four (13-UI-SCREENS #24, D-54). */
private const val FIELD_COUNT_COLUMNS = 4

/** Page pills ("Halaman 1" …) and a dashed "+" to add one (13-UI-SCREENS #24). */
@Composable
private fun PageChips(state: DataPagesEditorUiState, onEvent: (DataPagesEditorUiEvent) -> Unit) {
    val colors = RideCompTheme.colors
    Row(
        horizontalArrangement = Arrangement.spacedBy(RideCompDimens.spacingS),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ChoiceChips(
            label = stringResource(R.string.data_pages_page),
            choices = state.pages.indices.map { Choice(it, (it + 1).toString()) }.toImmutableList(),
            selected = state.selectedPage,
            onSelect = { onEvent(DataPagesEditorUiEvent.SelectPage(it)) },
            horizontalPadding = RideCompDimens.none,
            modifier = Modifier.weight(1f),
        )
        if (state.canAddPage) {
            val label = stringResource(R.string.data_pages_add)
            Box(
                modifier = Modifier
                    .padding(top = RideCompDimens.spacingL)
                    .size(RideCompDimens.touchTargetMin)
                    .clip(CircleShape)
                    .border(RideCompDimens.borderThin, colors.indicatorInactive, CircleShape)
                    .clickable(role = Role.Button, onClickLabel = label) { onEvent(DataPagesEditorUiEvent.AddPage) },
                contentAlignment = Alignment.Center,
            ) {
                Text(text = PLUS, style = RideCompTheme.typography.title, color = colors.accent)
            }
        }
    }
}

private const val PLUS = "+"

/** Grid preview; the field being edited has the accent border (13-UI-SCREENS #24). */
@Composable
private fun PagePreview(page: DataPageLayout, selected: Int?, onPick: (Int) -> Unit) {
    val colors = RideCompTheme.colors
    DataGrid(
        count = page.fields.size,
        isFirstFullWidth = page.isFirstFullWidth,
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(RideCompDimens.DATA_PAGE_PREVIEW_ASPECT_RATIO),
    ) { index, cellModifier ->
        val metric = RideMetricKeys.metricOf(page.fields[index])
        val isSelected = index == selected
        Box(
            modifier = cellModifier
                .background(if (isSelected) colors.accentTint else colors.bg)
                .then(if (isSelected) Modifier.border(RideCompDimens.borderSelected, colors.accent) else Modifier)
                .clickable(role = Role.Button) { onPick(index) },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = metric?.let { stringResource(it.labelRes) }.orEmpty(),
                style = RideCompTheme.typography.bodyStrong,
                color = colors.textPrimary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(RideCompDimens.spacingXs),
            )
        }
    }
}

/** "Ganti metrik kotak n": category chips, then the metrics of that category (13-UI-SCREENS #24). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MetricSheet(field: Int, currentKey: String?, onEvent: (DataPagesEditorUiEvent) -> Unit) {
    val current = currentKey?.let(RideMetricKeys::metricOf)
    var category by rememberSaveable(field) { mutableStateOf(MetricCategory.of(current)) }
    ModalBottomSheet(
        onDismissRequest = { onEvent(DataPagesEditorUiEvent.DismissPicker) },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        // Raised above the chips' `surface` so unselected chips stay visible (13-UI-SCREENS #24).
        containerColor = RideCompTheme.colors.surfaceRaised,
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = RideCompDimens.screenPaddingHorizontal)
                .padding(bottom = RideCompDimens.screenPaddingBottom)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(RideCompDimens.spacingSm),
        ) {
            Text(
                text = stringResource(R.string.data_pages_change_field, field + 1),
                style = RideCompTheme.typography.cardTitle,
                color = RideCompTheme.colors.textPrimary,
            )
            ChoiceChips(
                label = stringResource(R.string.data_pages_choose_metric),
                choices = MetricCategory.entries.map { Choice(it, stringResource(it.labelRes)) }.toImmutableList(),
                selected = category,
                onSelect = { category = it },
                horizontalPadding = RideCompDimens.none,
            )
            category.metrics.forEachIndexed { index, metric ->
                MetricRow(metric, isCurrent = metric == current, hasDivider = index < category.metrics.lastIndex) {
                    onEvent(DataPagesEditorUiEvent.ChooseMetric(metric))
                }
            }
        }
    }
}

@Composable
private fun MetricRow(metric: RideMetric, isCurrent: Boolean, hasDivider: Boolean, onClick: () -> Unit) {
    ListCardRow(
        title = stringResource(metric.labelRes),
        onClick = onClick,
        hasDivider = hasDivider,
        hasChevron = false,
        trailing = {
            if (isCurrent) {
                Icon(
                    painterResource(UiR.drawable.ic_check),
                    contentDescription = stringResource(R.string.data_pages_current),
                    tint = RideCompTheme.colors.accent,
                )
            }
        },
        modifier = Modifier.heightIn(min = RideCompDimens.touchTargetMin),
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
