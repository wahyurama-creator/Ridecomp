package com.ridecomp.feature.training.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ridecomp.core.config.Units
import com.ridecomp.core.engine.efforts.BestEffort
import com.ridecomp.core.engine.efforts.BestEffortKey
import com.ridecomp.core.engine.segments.SegmentMatch
import com.ridecomp.core.repository.RankedEffort
import com.ridecomp.core.repository.RankedSegmentEffort
import com.ridecomp.core.usecase.isTopRank
import com.ridecomp.core.usecase.segmentWindowMs
import com.ridecomp.core.util.UnitFormatter
import com.ridecomp.feature.training.R
import com.ridecomp.feature.training.model.WATT
import com.ridecomp.feature.training.model.label
import com.ridecomp.feature.training.model.valueText
import com.ridecomp.feature.training.viewmodel.RideResultLine
import com.ridecomp.feature.training.viewmodel.RideResultsUiState
import com.ridecomp.feature.training.viewmodel.RideResultsViewModel
import com.ridecomp.ui.R as UiR
import com.ridecomp.ui.component.AnalysisCard
import com.ridecomp.ui.component.AnalysisCardTexts
import com.ridecomp.ui.component.AnalysisRow
import com.ridecomp.ui.component.LinkText
import com.ridecomp.ui.preview.RideCompPreview
import com.ridecomp.ui.preview.ThemePreviews
import com.ridecomp.ui.theme.RideCompDimens
import com.ridecomp.ui.theme.RideCompTheme
import kotlin.math.roundToLong
import kotlinx.collections.immutable.persistentListOf
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * "Results" of a ride on its detail page (T11.7, 14-PHASE11-PLAN): best efforts in the rider's top 3 at the
 * time and the segments passed; hidden when there are none. Tapping a segment opens it; tapping a distance or
 * speed effort opens the segment editor on its stretch ("mark as sprint", T11.8) with [onMakeSegment].
 */
@Composable
fun RideResultsCard(
    activityId: Long,
    onOpenSegment: (Long) -> Unit,
    onMakeSegment: (windowMs: LongRange) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: RideResultsViewModel = koinViewModel(key = "results$activityId") { parametersOf(activityId) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    RideResultsContent(state, onOpenSegment, onMakeSegment, modifier)
}

@Composable
fun RideResultsContent(
    state: RideResultsUiState,
    onOpenSegment: (Long) -> Unit,
    onMakeSegment: (windowMs: LongRange) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (state.isEmpty) return
    val formatter = remember(state.unitSystem) { UnitFormatter(state.unitSystem) }
    var isExpanded by rememberSaveable { mutableStateOf(false) }
    val shown = if (isExpanded) state.lines else state.lines.take(VISIBLE_LINES)
    AnalysisCard(
        texts = AnalysisCardTexts(
            title = stringResource(R.string.results_title),
            info = stringResource(R.string.results_info),
            infoLabel = stringResource(R.string.results_info_label),
        ),
        rows = persistentListOf(
            AnalysisRow(stringResource(R.string.results_best_efforts), state.effortCount.toString()),
            AnalysisRow(stringResource(R.string.results_segments), state.segmentCount.toString()),
        ),
        modifier = modifier,
        footer = {
            // One column: the card spaces its children apart, the lines sit close like the number rows.
            Column {
                shown.forEach { line ->
                    HorizontalDivider(color = RideCompTheme.colors.divider, thickness = RideCompDimens.dividerThickness)
                    when (line) {
                        is RideResultLine.Effort -> EffortLine(line.ranked, formatter, onMakeSegment)
                        is RideResultLine.Pass -> PassLine(line.ranked, formatter) {
                            onOpenSegment(line.ranked.segmentId)
                        }
                    }
                }
            }
            if (state.lines.size > VISIBLE_LINES) {
                LinkText(
                    text = if (isExpanded) {
                        stringResource(R.string.results_show_less)
                    } else {
                        stringResource(R.string.results_show_all, state.lines.size)
                    },
                    onClick = { isExpanded = !isExpanded },
                )
            }
        },
    )
}

@Composable
private fun EffortLine(ranked: RankedEffort, formatter: UnitFormatter, onMakeSegment: (LongRange) -> Unit) {
    val key = ranked.effort.key
    val window = ranked.effort.segmentWindowMs
    ResultLine(
        iconRes = UiR.drawable.ic_trophy,
        iconTint = if (ranked.rank == 1) RideCompTheme.colors.accent else RideCompTheme.colors.textSecondary,
        title = key.label(),
        subtitle = window?.let { stringResource(R.string.results_make_segment) },
        value = key.valueText(ranked.effort.value, formatter),
        rank = ranked.rank,
        onClick = window?.let { { onMakeSegment(it) } },
    )
}

@Composable
private fun PassLine(ranked: RankedSegmentEffort, formatter: UnitFormatter, onClick: () -> Unit) {
    val match = ranked.match
    val facts = listOfNotNull(
        "${formatter.distance(ranked.lengthM)} ${formatter.distanceUnit}",
        "${formatter.speed(match.avgSpeedMps)} ${formatter.speedUnit}",
        match.avgPowerW?.let { "$it $WATT" },
    )
    ResultLine(
        iconRes = UiR.drawable.ic_location,
        iconTint = RideCompTheme.colors.textSecondary,
        title = ranked.segmentName,
        subtitle = facts.joinToString(FACT_SEPARATOR),
        value = formatter.duration((match.elapsedS * Units.MS_PER_SECOND).roundToLong()),
        rank = ranked.rank,
        onClick = onClick,
    )
}

@Composable
private fun ResultLine(
    @DrawableRes iconRes: Int,
    iconTint: Color,
    title: String,
    subtitle: String?,
    value: String,
    rank: Int,
    onClick: (() -> Unit)? = null,
) {
    val colors = RideCompTheme.colors
    // A line that opens something (a segment, the segment editor) gets the full touch height.
    val clickable = onClick?.let {
        Modifier
            .heightIn(min = RideCompDimens.touchTargetMin)
            .clickable(role = Role.Button, onClick = it)
    } ?: Modifier
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(clickable)
            .padding(vertical = RideCompDimens.spacingXs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(RideCompDimens.spacingSm),
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(RideCompDimens.iconSize),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = RideCompTheme.typography.bodyStrong, color = colors.textPrimary)
            subtitle?.let { Text(text = it, style = RideCompTheme.typography.caption, color = colors.textSecondary) }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(text = value, style = RideCompTheme.typography.bodyStrong, color = colors.textPrimary)
            rankLabel(rank)?.let {
                Text(
                    text = it,
                    style = RideCompTheme.typography.caption,
                    color = if (rank == 1) colors.accent else colors.textSecondary,
                )
            }
        }
    }
}

/** "PR", "2nd best", "3rd best"; nothing below the top 3. */
@Composable
private fun rankLabel(rank: Int): String? = when {
    !rank.isTopRank -> null
    rank == 1 -> stringResource(R.string.results_rank_pr)
    rank == 2 -> stringResource(R.string.results_rank_second)
    else -> stringResource(R.string.results_rank_third)
}

/** Lines shown before "See all results". */
private const val VISIBLE_LINES = 3
private const val FACT_SEPARATOR = " · "

@ThemePreviews
@Composable
private fun RideResultsPreview() {
    RideCompPreview {
        RideResultsContent(
            state = RideResultsUiState(
                effortCount = 2,
                segmentCount = 2,
                lines = persistentListOf(
                    RideResultLine.Effort(RankedEffort(BestEffort(BestEffortKey.FASTEST_5_KM, 581.0, 0, 581_000), 1)),
                    RideResultLine.Effort(RankedEffort(BestEffort(BestEffortKey.POWER_5_MIN, 268.0, null, null), 2)),
                    RideResultLine.Pass(
                        RankedSegmentEffort(
                            1,
                            "Sprint Soekarno Hatta",
                            1_200.0,
                            SegmentMatch(0, 0, 165.0, 7.3, null, 240),
                            1,
                        ),
                    ),
                    RideResultLine.Pass(
                        RankedSegmentEffort(
                            2,
                            "Tanjakan Kaliurang",
                            2_400.0,
                            SegmentMatch(0, 0, 610.0, 3.9, 150, null),
                            5,
                        ),
                    ),
                ),
            ),
            onOpenSegment = {},
            onMakeSegment = {},
        )
    }
}
