package com.ridecomp.feature.training.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ridecomp.core.engine.efforts.BestEffort
import com.ridecomp.core.engine.efforts.BestEffortKey
import com.ridecomp.core.engine.efforts.BestEffortType
import com.ridecomp.core.model.ActivityKind
import com.ridecomp.core.repository.PersonalRecord
import com.ridecomp.core.repository.RideEffort
import com.ridecomp.core.util.UnitFormatter
import com.ridecomp.feature.training.R
import com.ridecomp.feature.training.model.label
import com.ridecomp.feature.training.model.labelRes
import com.ridecomp.feature.training.model.titleRes
import com.ridecomp.feature.training.model.valueText
import com.ridecomp.feature.training.viewmodel.NewRecordsUiState
import com.ridecomp.feature.training.viewmodel.NewRecordsViewModel
import com.ridecomp.feature.training.viewmodel.RecordsUiEvent
import com.ridecomp.feature.training.viewmodel.RecordsUiState
import com.ridecomp.feature.training.viewmodel.RecordsViewModel
import com.ridecomp.ui.R as UiR
import com.ridecomp.ui.component.CardTone
import com.ridecomp.ui.component.Choice
import com.ridecomp.ui.component.ChoiceChips
import com.ridecomp.ui.component.EmptyState
import com.ridecomp.ui.component.ListCard
import com.ridecomp.ui.component.ListCardRow
import com.ridecomp.ui.component.RideCompCard
import com.ridecomp.ui.component.SectionHeader
import com.ridecomp.ui.preview.RideCompPreview
import com.ridecomp.ui.preview.ThemePreviews
import com.ridecomp.ui.theme.RideCompDimens
import com.ridecomp.ui.theme.RideCompTheme
import java.text.DateFormat
import java.util.Date
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

/** Records tab of History (T4.1, 13-UI-SCREENS #20 note): personal records per profile kind. */
@Composable
fun RecordsRoute(
    onOpenActivity: (Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RecordsViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    RecordsContent(state = state, onEvent = viewModel::onEvent, onOpenActivity = onOpenActivity, modifier = modifier)
}

/** Stateless records list: kind chips (when the rider has several kinds), then one card per record group. */
@Composable
fun RecordsContent(
    state: RecordsUiState,
    onEvent: (RecordsUiEvent) -> Unit,
    onOpenActivity: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (state.isLoaded && state.records.isEmpty()) {
        EmptyState(message = stringResource(R.string.records_empty), modifier = modifier)
        return
    }
    val formatter = remember(state.unitSystem) { UnitFormatter(state.unitSystem) }
    val groups = remember(state.records) { state.records.groupBy { it.key.type } }
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(
            start = RideCompDimens.screenPaddingHorizontal,
            end = RideCompDimens.screenPaddingHorizontal,
            bottom = RideCompDimens.screenPaddingBottom,
        ),
        verticalArrangement = Arrangement.spacedBy(RideCompDimens.cardGap),
    ) {
        val selected = state.selectedKind
        if (state.kinds.size > 1 && selected != null) {
            item(key = KINDS_KEY) {
                val choices = state.kinds.map { Choice(it, stringResource(it.labelRes())) }.toImmutableList()
                ChoiceChips(
                    label = stringResource(R.string.records_kind),
                    choices = choices,
                    selected = selected,
                    onSelect = { onEvent(RecordsUiEvent.SelectKind(it)) },
                    horizontalPadding = RideCompDimens.none,
                )
            }
        }
        BestEffortType.entries.forEach { type ->
            val records = groups[type].orEmpty()
            if (records.isEmpty()) return@forEach
            item(key = type.name) {
                SectionHeader(stringResource(type.titleRes()), horizontalPadding = RideCompDimens.none)
                ListCard {
                    records.forEachIndexed { index, record ->
                        RecordRow(record, formatter, hasDivider = index < records.lastIndex) {
                            onOpenActivity(record.activityId)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RecordRow(record: PersonalRecord, formatter: UnitFormatter, hasDivider: Boolean, onClick: () -> Unit) {
    val date = remember(record.startedAtMs) {
        DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(record.startedAtMs))
    }
    ListCardRow(
        title = record.key.label(),
        subtitle = stringResource(R.string.records_set_on, record.activityName, date),
        value = record.key.valueText(record.value, formatter),
        hasDivider = hasDivider,
        onClick = onClick,
    )
}

/** Summary card "2 PR baru" (13-UI-SCREENS #10); hidden when the ride sets no record. */
@Composable
fun NewRecordsCard(activityId: Long, modifier: Modifier = Modifier) {
    val viewModel: NewRecordsViewModel = koinViewModel(key = activityId.toString()) { parametersOf(activityId) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    NewRecordsCardContent(state, modifier)
}

@Composable
fun NewRecordsCardContent(state: NewRecordsUiState, modifier: Modifier = Modifier) {
    if (state.records.isEmpty()) return
    val formatter = remember(state.unitSystem) { UnitFormatter(state.unitSystem) }
    val colors = RideCompTheme.colors
    RideCompCard(modifier = modifier, tone = CardTone.ACCENT) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(RideCompDimens.spacingSm),
        ) {
            Icon(
                painter = painterResource(UiR.drawable.ic_trophy),
                contentDescription = null,
                tint = colors.accent,
                modifier = Modifier.size(RideCompDimens.iconSize),
            )
            Text(
                text = pluralStringResource(R.plurals.records_new, state.records.size, state.records.size),
                style = RideCompTheme.typography.cardTitle,
                color = colors.textPrimary,
            )
        }
        state.records.forEach { (effort) ->
            Row {
                Text(
                    text = effort.key.label(),
                    style = RideCompTheme.typography.body,
                    color = colors.textSecondary,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = effort.key.valueText(effort.value, formatter),
                    style = RideCompTheme.typography.bodyStrong,
                    color = colors.accent,
                )
            }
        }
    }
}

private const val KINDS_KEY = "kinds"

@ThemePreviews
@Composable
private fun RecordsPreview() {
    RideCompPreview {
        RecordsContent(
            state = RecordsUiState(
                kinds = persistentListOf(ActivityKind.ROAD, ActivityKind.INDOOR),
                selectedKind = ActivityKind.ROAD,
                records = persistentListOf(
                    PersonalRecord(BestEffortKey.FASTEST_10_KM, 1_274.0, 1, "Gowes Pagi", 0),
                    PersonalRecord(BestEffortKey.POWER_5_MIN, 268.0, 2, "Kaliurang", 0),
                    PersonalRecord(BestEffortKey.LONGEST_RIDE, 82_400.0, 3, "Borobudur", 0),
                ),
                isLoaded = true,
            ),
            onEvent = {},
            onOpenActivity = {},
        )
    }
}

@ThemePreviews
@Composable
private fun NewRecordsPreview() {
    RideCompPreview {
        NewRecordsCardContent(
            NewRecordsUiState(
                records = persistentListOf(
                    RideEffort(BestEffort(BestEffortKey.FASTEST_10_KM, 1_274.0, null, null), isPr = true),
                    RideEffort(BestEffort(BestEffortKey.LONGEST_RIDE, 42_100.0, null, null), isPr = true),
                ),
            ),
        )
    }
}
