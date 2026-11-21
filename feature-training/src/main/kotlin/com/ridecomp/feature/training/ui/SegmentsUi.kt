package com.ridecomp.feature.training.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ridecomp.core.config.Units
import com.ridecomp.core.engine.segments.SegmentError
import com.ridecomp.core.repository.SegmentEffortView
import com.ridecomp.core.repository.SegmentSummary
import com.ridecomp.core.util.UnitFormatter
import com.ridecomp.feature.training.R
import com.ridecomp.feature.training.viewmodel.SegmentDetailEffect
import com.ridecomp.feature.training.viewmodel.SegmentDetailUiEvent
import com.ridecomp.feature.training.viewmodel.SegmentDetailUiState
import com.ridecomp.feature.training.viewmodel.SegmentDetailViewModel
import com.ridecomp.feature.training.viewmodel.SegmentDraft
import com.ridecomp.feature.training.viewmodel.SegmentEditorEffect
import com.ridecomp.feature.training.viewmodel.SegmentEditorViewModel
import com.ridecomp.feature.training.viewmodel.SegmentsUiState
import com.ridecomp.feature.training.viewmodel.SegmentsViewModel
import com.ridecomp.ui.component.BigButton
import com.ridecomp.ui.component.BigButtonStyle
import com.ridecomp.ui.component.CardTone
import com.ridecomp.ui.component.ChartValue
import com.ridecomp.ui.component.ConfirmDialog
import com.ridecomp.ui.component.EmptyState
import com.ridecomp.ui.component.LineChart
import com.ridecomp.ui.component.ListCard
import com.ridecomp.ui.component.ListCardRow
import com.ridecomp.ui.component.RideCompCard
import com.ridecomp.ui.component.ScreenScaffold
import com.ridecomp.ui.component.SectionHeader
import com.ridecomp.ui.component.StatGrid
import com.ridecomp.ui.component.StatItem
import com.ridecomp.ui.preview.RideCompPreview
import com.ridecomp.ui.preview.ThemePreviews
import com.ridecomp.ui.theme.RideCompDimens
import com.ridecomp.ui.theme.RideCompTheme
import com.ridecomp.ui.udf.ObserveEffects
import java.text.DateFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale
import kotlin.math.roundToLong
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

/** Segments tab of History (T4.2): the rider's segments with their records, or how to make one. */
@Composable
fun SegmentsRoute(
    onOpenSegment: (Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SegmentsViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    SegmentsContent(state, onOpenSegment, modifier)
}

@Composable
fun SegmentsContent(state: SegmentsUiState, onOpenSegment: (Long) -> Unit, modifier: Modifier = Modifier) {
    if (state.isLoaded && state.segments.isEmpty()) {
        EmptyState(message = stringResource(R.string.segments_empty), modifier = modifier)
        return
    }
    val formatter = remember(state.unitSystem) { UnitFormatter(state.unitSystem) }
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(
            start = RideCompDimens.screenPaddingHorizontal,
            end = RideCompDimens.screenPaddingHorizontal,
            bottom = RideCompDimens.screenPaddingBottom,
        ),
    ) {
        item {
            ListCard {
                state.segments.forEachIndexed { index, summary ->
                    SegmentRow(summary, formatter, hasDivider = index < state.segments.lastIndex) {
                        onOpenSegment(summary.segment.id)
                    }
                }
            }
        }
    }
}

@Composable
private fun SegmentRow(summary: SegmentSummary, formatter: UnitFormatter, hasDivider: Boolean, onClick: () -> Unit) {
    val segment = summary.segment
    ListCardRow(
        title = segment.name,
        subtitle = stringResource(
            R.string.segment_facts,
            "${formatter.distance(segment.lengthM)} ${formatter.distanceUnit}",
            formatter.grade(segment.avgGradePercent),
            summary.effortCount,
        ),
        value = summary.bestElapsedS?.let { formatter.duration(it.toMs()) },
        hasDivider = hasDivider,
        onClick = onClick,
    )
}

/** Segment detail (T4.2): numbers, record, fastest efforts, trend over time, delete. */
@Composable
fun SegmentDetailRoute(
    segmentId: Long,
    onBack: () -> Unit,
    onOpenActivity: (Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SegmentDetailViewModel = koinViewModel(key = "segment-$segmentId") { parametersOf(segmentId) },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ObserveEffects(viewModel.effects) { effect ->
        when (effect) {
            SegmentDetailEffect.Deleted -> onBack()
        }
    }
    SegmentDetailScreen(state, viewModel::onEvent, onBack, onOpenActivity, modifier)
}

@Composable
fun SegmentDetailScreen(
    state: SegmentDetailUiState,
    onEvent: (SegmentDetailUiEvent) -> Unit,
    onBack: () -> Unit,
    onOpenActivity: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val formatter = remember(state.unitSystem) { UnitFormatter(state.unitSystem) }
    val segment = state.segment
    ScreenScaffold(
        modifier = modifier,
        title = segment?.name ?: stringResource(R.string.segment_title),
        onBack = onBack,
        backLabel = stringResource(R.string.segment_back),
    ) {
        if (segment == null) {
            if (state.isLoaded) EmptyState(message = stringResource(R.string.segment_missing))
            return@ScreenScaffold
        }
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
            StatGrid(
                persistentListOf(
                    StatItem(
                        stringResource(R.string.segment_length),
                        formatter.distance(segment.lengthM),
                        formatter.distanceUnit,
                    ),
                    StatItem(
                        stringResource(R.string.segment_gain),
                        formatter.elevation(segment.gainM),
                        formatter.elevationUnit,
                    ),
                    StatItem(stringResource(R.string.segment_grade), formatter.grade(segment.avgGradePercent), "%"),
                ),
            )
            val best = state.efforts.firstOrNull()
            if (best == null) {
                Text(
                    text = stringResource(R.string.segment_no_efforts),
                    style = RideCompTheme.typography.body,
                    color = RideCompTheme.colors.textSecondary,
                )
            } else {
                RecordCard(best, formatter)
                SectionHeader(stringResource(R.string.segment_best_efforts), horizontalPadding = RideCompDimens.none)
                val top = state.efforts.take(TOP_EFFORTS)
                ListCard {
                    top.forEachIndexed { index, effort ->
                        EffortRow(effort, formatter, hasDivider = index < top.lastIndex) {
                            onOpenActivity(effort.effort.activityId)
                        }
                    }
                }
                TrendChart(state.efforts, formatter)
            }
            BigButton(
                text = stringResource(R.string.segment_delete),
                onClick = { onEvent(SegmentDetailUiEvent.AskDelete) },
                style = BigButtonStyle.TEXT_DANGER,
            )
        }
    }
    if (state.isDeleteAsked && segment != null) {
        ConfirmDialog(
            title = stringResource(R.string.segment_delete_title, segment.name),
            message = stringResource(R.string.segment_delete_message),
            confirmLabel = stringResource(R.string.segment_delete),
            dismissLabel = stringResource(R.string.segment_cancel),
            onConfirm = { onEvent(SegmentDetailUiEvent.ConfirmDelete) },
            onDismiss = { onEvent(SegmentDetailUiEvent.DismissDelete) },
        )
    }
}

@Composable
private fun RecordCard(best: SegmentEffortView, formatter: UnitFormatter) {
    RideCompCard(tone = CardTone.ACCENT) {
        Text(
            text = stringResource(R.string.segment_record).uppercase(),
            style = RideCompTheme.typography.dataLabel,
            color = RideCompTheme.colors.accent,
        )
        Text(
            text = formatter.duration(best.effort.match.elapsedS.toMs()),
            style = RideCompTheme.typography.statValue,
            color = RideCompTheme.colors.textPrimary,
        )
        Text(
            text = stringResource(R.string.segment_effort_ride, best.activityName, best.date()),
            style = RideCompTheme.typography.caption,
            color = RideCompTheme.colors.textSecondary,
        )
    }
}

@Composable
private fun EffortRow(effort: SegmentEffortView, formatter: UnitFormatter, hasDivider: Boolean, onClick: () -> Unit) {
    val match = effort.effort.match
    ListCardRow(
        title = formatter.duration(match.elapsedS.toMs()),
        subtitle = stringResource(R.string.segment_effort_ride, effort.activityName, effort.date()),
        value = "${formatter.speed(match.avgSpeedMps)} ${formatter.speedUnit}",
        hasDivider = hasDivider,
        onClick = onClick,
    )
}

/** Elapsed time of each effort in ride order (lower is better). */
@Composable
private fun TrendChart(efforts: List<SegmentEffortView>, formatter: UnitFormatter) {
    if (efforts.size < 2) return
    val values = remember(efforts) {
        efforts.sortedBy { it.startedAtMs }
            .mapIndexed { index, effort ->
                ChartValue(
                    index.toDouble(),
                    effort.effort.match.elapsedS / Units.SECONDS_PER_MINUTE,
                )
            }
            .toImmutableList()
    }
    LineChart(
        title = stringResource(R.string.segment_trend),
        values = values,
        summary = pluralStringResource(
            R.plurals.segment_trend_summary,
            efforts.size,
            efforts.size,
            formatter.duration(efforts.first().effort.match.elapsedS.toMs()),
        ),
    )
}

/**
 * New segment from a ride (T4.2, D-68), or from a best effort's [windowMs] named "Sprint 20 Nov" after its
 * day (T11.8).
 */
@Composable
fun SegmentEditorRoute(
    activityId: Long,
    windowMs: LongRange?,
    onCreated: (Long) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val draft = windowMs?.let { SegmentDraft(it, sprintName(it.first)) }
    val viewModel: SegmentEditorViewModel = koinViewModel(key = "segment-new-$activityId-${windowMs?.first}") {
        parametersOf(activityId, draft)
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ObserveEffects(viewModel.effects) { effect ->
        when (effect) {
            is SegmentEditorEffect.Created -> onCreated(effect.segmentId)
        }
    }
    SegmentEditorScreen(state, viewModel::onEvent, onBack, modifier)
}

/** "Sprint 20 Nov": the day the effort was ridden, in the phone's language and time zone. */
@Composable
private fun sprintName(startedAtMs: Long): String {
    val locale: Locale = LocalConfiguration.current.locales[0]
    val day = remember(startedAtMs, locale) {
        Instant.ofEpochMilli(startedAtMs).atZone(ZoneId.systemDefault()).toLocalDate()
            .format(DateTimeFormatter.ofPattern(SPRINT_DAY_PATTERN, locale))
    }
    return stringResource(R.string.segment_sprint_name, day)
}

private const val SPRINT_DAY_PATTERN = "d MMM"

@StringRes
internal fun SegmentError.messageRes(): Int = when (this) {
    SegmentError.TOO_SHORT, SegmentError.TOO_LONG -> R.string.segment_error_length
    SegmentError.NO_POSITION -> R.string.segment_error_position
}

private fun SegmentEffortView.date(): String = DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(startedAtMs))

private fun Double.toMs(): Long = (this * Units.MS_PER_SECOND).roundToLong()

private const val TOP_EFFORTS = 5

@ThemePreviews
@Composable
private fun SegmentsPreview() {
    RideCompPreview { SegmentsContent(SegmentsUiState(isLoaded = true), onOpenSegment = {}) }
}
