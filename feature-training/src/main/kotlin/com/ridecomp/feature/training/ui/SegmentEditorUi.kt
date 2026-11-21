package com.ridecomp.feature.training.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.ridecomp.core.config.Units
import com.ridecomp.core.engine.route.RouteClimb
import com.ridecomp.core.engine.segments.SegmentSelectionStats
import com.ridecomp.core.model.UnitSystem
import com.ridecomp.core.usecase.OutlinePoint
import com.ridecomp.core.util.UnitFormatter
import com.ridecomp.feature.training.R
import com.ridecomp.feature.training.viewmodel.SegmentEditorUiEvent
import com.ridecomp.feature.training.viewmodel.SegmentEditorUiState
import com.ridecomp.ui.R as UiR
import com.ridecomp.ui.component.BigButton
import com.ridecomp.ui.component.BigButtonSize
import com.ridecomp.ui.component.ChartValue
import com.ridecomp.ui.component.Choice
import com.ridecomp.ui.component.ChoiceChips
import com.ridecomp.ui.component.IconSquareButton
import com.ridecomp.ui.component.LineChart
import com.ridecomp.ui.component.RideCompCard
import com.ridecomp.ui.component.RideCompTextField
import com.ridecomp.ui.component.ScreenScaffold
import com.ridecomp.ui.component.SectionHeader
import com.ridecomp.ui.component.StatGrid
import com.ridecomp.ui.component.StatItem
import com.ridecomp.ui.component.TrackSketch
import com.ridecomp.ui.map.MapPoint
import com.ridecomp.ui.map.RouteMap
import com.ridecomp.ui.preview.RideCompPreview
import com.ridecomp.ui.preview.ThemePreviews
import com.ridecomp.ui.theme.RideCompDimens
import com.ridecomp.ui.theme.RideCompTheme
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

/**
 * New segment from part of a ride (T4.2, redesigned T11.15): three numbered steps. ① Choose: the street map
 * (or sketch) with the whole ride and the chosen part, quick picks (whole ride, detected climbs), the
 * elevation with a range slider, and fine − / + steps for both ends. ② Check: length, climb, grade, and this
 * ride's time and speed over the part, its altitude range. ③ Name, how matching works, and save.
 */
@Composable
fun SegmentEditorScreen(
    state: SegmentEditorUiState,
    onEvent: (SegmentEditorUiEvent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val formatter = remember(state.unitSystem) { UnitFormatter(state.unitSystem) }
    ScreenScaffold(
        modifier = modifier,
        title = stringResource(R.string.segment_new_title),
        onBack = onBack,
        backLabel = stringResource(R.string.segment_back),
    ) {
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
            ChooseStep(state, formatter, onEvent)
            CheckStep(state, formatter)
            NameStep(state, onEvent)
        }
    }
}

@Composable
private fun ChooseStep(state: SegmentEditorUiState, formatter: UnitFormatter, onEvent: (SegmentEditorUiEvent) -> Unit) {
    SectionHeader(stringResource(R.string.segment_step_choose), horizontalPadding = RideCompDimens.none)
    Text(
        text = stringResource(R.string.segment_new_hint),
        style = RideCompTheme.typography.caption,
        color = RideCompTheme.colors.textSecondary,
    )
    SelectionMap(state)
    QuickPicks(state, formatter, onEvent)
    ElevationWithSlider(state, onEvent)
    EndStepper(
        label = stringResource(R.string.segment_start, "${formatter.distance(state.startM)} ${formatter.distanceUnit}"),
        isStart = true,
        onEvent = onEvent,
    )
    EndStepper(
        label = stringResource(R.string.segment_end, "${formatter.distance(state.endM)} ${formatter.distanceUnit}"),
        isStart = false,
        onEvent = onEvent,
    )
}

/**
 * The whole ride in orange and the chosen part in blue over the street map, green dot at the start, white dot
 * at the end; the line sketch when there is no map (offline without cache or region file).
 */
@Composable
private fun SelectionMap(state: SegmentEditorUiState) {
    val positioned = remember(state.outline) { state.outline.filter { it.latDeg != null && it.lonDeg != null } }
    val ride = remember(positioned) { positioned.mapNotNull { it.toMapPoint() }.toImmutableList() }
    val chosen = remember(positioned, state.startM, state.endM) {
        positioned.filter { it.distanceM in state.startM..state.endM }.mapNotNull { it.toMapPoint() }.toImmutableList()
    }
    val description = stringResource(R.string.segment_track)
    if (ride.size < MIN_LINE_POINTS) return
    if (state.hasMap) {
        RouteMap(
            isOnline = state.mapSource.isOnline,
            isCached = state.mapSource.isCached,
            regionPaths = state.mapRegions,
            route = ride,
            ridden = chosen,
            marker = chosen.firstOrNull(),
            position = chosen.lastOrNull(),
            description = description,
            modifier = Modifier
                .fillMaxWidth()
                .height(RideCompDimens.routeMapHeight)
                .clip(RoundedCornerShape(RideCompDimens.cornerRadiusCard)),
        )
    } else {
        val highlight = positioned.indexOfFirst { it.distanceM >= state.startM }..positioned.indexOfLast {
            it.distanceM <= state.endM
        }
        TrackSketch(points = ride, description = description, highlight = highlight)
    }
}

/** "Seluruh ride" and the ride's biggest climbs; the chip of the current choice is highlighted. */
@Composable
private fun QuickPicks(state: SegmentEditorUiState, formatter: UnitFormatter, onEvent: (SegmentEditorUiEvent) -> Unit) {
    val whole = QuickPick.Whole
    val picks = buildList<Choice<QuickPick?>> {
        add(Choice(whole, stringResource(R.string.segment_pick_whole)))
        state.climbs.forEachIndexed { index, climb ->
            val label = stringResource(
                R.string.segment_pick_climb,
                index + 1,
                "${formatter.distance(climb.lengthM)} ${formatter.distanceUnit}",
                climb.avgGradePercent.roundToInt(),
            )
            add(Choice(QuickPick.Climb(climb), label))
        }
    }.toImmutableList()
    val selected = picks.map { it.value }.firstOrNull { it?.matches(state) == true }
    ChoiceChips(
        label = stringResource(R.string.segment_picks),
        choices = picks,
        selected = selected,
        onSelect = { pick ->
            when (pick) {
                QuickPick.Whole -> onEvent(SegmentEditorUiEvent.SelectWholeRide)
                is QuickPick.Climb -> onEvent(SegmentEditorUiEvent.SelectClimb(pick.climb))
                null -> Unit
            }
        },
        horizontalPadding = RideCompDimens.none,
    )
}

/** A quick choice of the editor. */
private sealed interface QuickPick {
    fun matches(state: SegmentEditorUiState): Boolean

    data object Whole : QuickPick {
        override fun matches(state: SegmentEditorUiState) = state.startM <= 0.0 && state.endM >= state.totalM
    }

    data class Climb(val climb: RouteClimb) : QuickPick {
        override fun matches(state: SegmentEditorUiState) =
            state.startM == climb.startDistanceM && state.endM == climb.endDistanceM
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ElevationWithSlider(state: SegmentEditorUiState, onEvent: (SegmentEditorUiEvent) -> Unit) {
    val divisor = if (state.unitSystem == UnitSystem.METRIC) Units.METERS_PER_KM else Units.METERS_PER_MILE
    val profile = remember(state.outline, divisor) {
        state.outline.map { ChartValue(it.distanceM / divisor, it.altitudeM) }.toImmutableList()
    }
    LineChart(
        title = stringResource(R.string.segment_profile),
        values = profile,
        color = RideCompTheme.colors.track,
        rangeX = (state.startM / divisor)..(state.endM / divisor),
    )
    if (state.totalM > 0) {
        RangeSlider(
            value = state.startM.toFloat()..state.endM.toFloat(),
            onValueChange = { range ->
                onEvent(SegmentEditorUiEvent.RangeChanged(range.start.toDouble(), range.endInclusive.toDouble()))
            },
            valueRange = 0f..state.totalM.toFloat(),
            colors = SliderDefaults.colors(
                thumbColor = RideCompTheme.colors.accent,
                activeTrackColor = RideCompTheme.colors.accent,
                inactiveTrackColor = RideCompTheme.colors.divider,
            ),
        )
    }
}

/** "Mulai 0,40 km  [−] [+]": moves one end by 50 m per tap, for the precision a slider lacks on long rides. */
@Composable
private fun EndStepper(label: String, isStart: Boolean, onEvent: (SegmentEditorUiEvent) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(RideCompDimens.spacingS),
    ) {
        Text(
            text = label,
            style = RideCompTheme.typography.bodyStrong,
            color = RideCompTheme.colors.textPrimary,
            modifier = Modifier.weight(1f),
        )
        IconSquareButton(
            iconRes = UiR.drawable.ic_minus,
            contentDescription = stringResource(
                if (isStart) R.string.segment_start_back else R.string.segment_end_back,
            ),
            onClick = { onEvent(SegmentEditorUiEvent.Nudge(isStart, steps = -1)) },
            tint = RideCompTheme.colors.textPrimary,
        )
        IconSquareButton(
            iconRes = UiR.drawable.ic_plus,
            contentDescription = stringResource(
                if (isStart) R.string.segment_start_forward else R.string.segment_end_forward,
            ),
            onClick = { onEvent(SegmentEditorUiEvent.Nudge(isStart, steps = 1)) },
            tint = RideCompTheme.colors.textPrimary,
        )
    }
}

/** ② The chosen part's figures, and why it cannot be saved yet when it is too short or long. */
@Composable
private fun CheckStep(state: SegmentEditorUiState, formatter: UnitFormatter) {
    SectionHeader(stringResource(R.string.segment_step_check), horizontalPadding = RideCompDimens.none)
    StatGrid(items = selectionItems(state.stats ?: SegmentSelectionStats(state.lengthM, null, null, 0.0), formatter))
    val problem = state.error?.messageRes()
        ?: if (state.isLoaded && !state.isLengthValid) R.string.segment_error_length else null
    problem?.let {
        Text(
            text = stringResource(it),
            style = RideCompTheme.typography.caption,
            color = RideCompTheme.colors.warnText,
        )
    }
}

@Composable
private fun selectionItems(stats: SegmentSelectionStats, formatter: UnitFormatter): ImmutableList<StatItem> {
    val none = stringResource(R.string.segment_value_none)
    return persistentListOf(
        StatItem(
            stringResource(R.string.segment_stat_length),
            formatter.distance(stats.lengthM),
            unit = formatter.distanceUnit,
        ),
        StatItem(
            stringResource(R.string.segment_stat_gain),
            stats.gainM?.let { formatter.elevation(it) } ?: none,
            unit = formatter.elevationUnit.takeIf { stats.gainM != null },
        ),
        StatItem(
            stringResource(R.string.segment_stat_grade),
            stats.avgGradePercent?.let { stringResource(R.string.segment_grade_value, it) } ?: none,
        ),
        StatItem(
            stringResource(R.string.segment_stat_time),
            if (stats.elapsedS > 0) formatter.duration((stats.elapsedS * Units.MS_PER_SECOND).roundToLong()) else none,
        ),
        StatItem(
            stringResource(R.string.segment_stat_speed),
            stats.avgSpeedMps?.let { formatter.speed(it) } ?: none,
            unit = formatter.speedUnit.takeIf { stats.avgSpeedMps != null },
        ),
        StatItem(
            stringResource(R.string.segment_stat_altitude),
            stats.altitudeRangeM?.let { range ->
                stringResource(
                    R.string.segment_altitude_value,
                    formatter.elevation(range.start),
                    formatter.elevation(range.endInclusive),
                )
            } ?: none,
            unit = formatter.elevationUnit.takeIf { stats.altitudeRangeM != null },
        ),
    )
}

/** ③ Name, how future rides are matched, and save. */
@Composable
private fun NameStep(state: SegmentEditorUiState, onEvent: (SegmentEditorUiEvent) -> Unit) {
    SectionHeader(stringResource(R.string.segment_step_name), horizontalPadding = RideCompDimens.none)
    RideCompTextField(
        label = stringResource(R.string.segment_name),
        value = state.name,
        onValueChange = { onEvent(SegmentEditorUiEvent.NameChanged(it)) },
    )
    RideCompCard {
        Row(horizontalArrangement = Arrangement.spacedBy(RideCompDimens.cardGap)) {
            Icon(
                painter = painterResource(UiR.drawable.ic_info),
                contentDescription = null,
                tint = RideCompTheme.colors.textSecondary,
            )
            Text(
                text = stringResource(R.string.segment_how_it_works),
                style = RideCompTheme.typography.caption,
                color = RideCompTheme.colors.textSecondary,
            )
        }
    }
    BigButton(
        text = stringResource(R.string.segment_save),
        onClick = { onEvent(SegmentEditorUiEvent.Save) },
        isEnabled = state.canSave,
        size = BigButtonSize.SECONDARY,
    )
}

private fun OutlinePoint.toMapPoint(): MapPoint? = latDeg?.let { lat -> lonDeg?.let { MapPoint(lat, it) } }

private const val MIN_LINE_POINTS = 2

@ThemePreviews
@Composable
private fun SegmentEditorPreview() {
    val outline = (0..60).map { i ->
        OutlinePoint(-7.78 + i * 0.0003, 110.37 + i * 0.0002, i * 50.0, 120.0 + (i % 20) * 1.5)
    }.toImmutableList()
    RideCompPreview {
        SegmentEditorScreen(
            state = SegmentEditorUiState(
                outline = outline,
                startM = 400.0,
                endM = 1_600.0,
                name = "Tanjakan Kaliurang",
                isLoaded = true,
                stats = SegmentSelectionStats(1_200.0, 34.0, 2.8, 192.0),
            ),
            onEvent = {},
            onBack = {},
        )
    }
}
