package com.ridecomp.feature.training.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ridecomp.core.config.Units
import com.ridecomp.core.engine.training.LoadMethod
import com.ridecomp.core.engine.training.TrainingConfig
import com.ridecomp.core.model.PlanRef
import com.ridecomp.core.usecase.TrainingOverview
import com.ridecomp.core.util.UnitFormatter
import com.ridecomp.feature.training.R
import com.ridecomp.feature.training.viewmodel.TrainingUiState
import com.ridecomp.feature.training.viewmodel.TrainingViewModel
import com.ridecomp.ui.component.ChartSeries
import com.ridecomp.ui.component.EmptyState
import com.ridecomp.ui.component.LineStyle
import com.ridecomp.ui.component.RideCompCard
import com.ridecomp.ui.component.StatGrid
import com.ridecomp.ui.component.StatItem
import com.ridecomp.ui.component.TrendChart
import com.ridecomp.ui.theme.RideCompDimens
import com.ridecomp.ui.theme.RideCompTheme
import kotlin.math.roundToInt
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import org.koin.androidx.compose.koinViewModel

/** Training tab of History (T4.4, 13-UI-SCREENS #20). Values are estimates for trends (§10). */
@Composable
fun TrainingRoute(
    modifier: Modifier = Modifier,
    onOpenPlans: () -> Unit = {},
    onOpenPlan: (PlanRef) -> Unit = {},
    viewModel: TrainingViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    TrainingContent(state, modifier, onOpenPlans, onOpenPlan)
}

/** The training plan card first (T7.4), then the load overview once there are rides with load. */
@Composable
fun TrainingContent(
    state: TrainingUiState,
    modifier: Modifier = Modifier,
    onOpenPlans: () -> Unit = {},
    onOpenPlan: (PlanRef) -> Unit = {},
) {
    val overview = state.overview ?: return
    val formatter = remember(state.unitSystem) { UnitFormatter(state.unitSystem) }
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(
                start = RideCompDimens.screenPaddingHorizontal,
                end = RideCompDimens.screenPaddingHorizontal,
                bottom = RideCompDimens.screenPaddingBottom,
            ),
        verticalArrangement = Arrangement.spacedBy(RideCompDimens.blockGap),
    ) {
        PlanCard(state.activePlan, onOpenPlans, onOpenPlan)
        if (!state.hasLoad) {
            EmptyState(message = stringResource(R.string.training_empty))
            return@Column
        }
        Text(
            text = stringResource(R.string.training_disclaimer),
            style = RideCompTheme.typography.caption,
            color = RideCompTheme.colors.textTertiary,
        )
        FitnessCards(overview)
        TrendCard(overview)
        WeekCard(overview, formatter)
        ZonesCard(state.zonesS, formatter)
    }
}

/** FITNESS (blue) · KELELAHAN (orange) · FORM with its status. */
@Composable
private fun FitnessCards(overview: TrainingOverview) {
    val colors = RideCompTheme.colors
    val today = overview.today
    val form = today?.tsb ?: 0.0
    Row(horizontalArrangement = Arrangement.spacedBy(RideCompDimens.cardGap)) {
        val change = overview.fitnessChange?.roundToInt()
        MetricCard(
            label = stringResource(R.string.training_fitness),
            value = today?.ctl?.roundToInt()?.toString().orEmpty(),
            caption = change?.let { stringResource(R.string.training_fitness_change, signed(it)) }.orEmpty(),
            color = colors.track,
            modifier = Modifier.weight(1f),
        )
        MetricCard(
            label = stringResource(R.string.training_fatigue),
            value = today?.atl?.roundToInt()?.toString().orEmpty(),
            caption = stringResource(R.string.training_fatigue_caption),
            color = colors.accent,
            modifier = Modifier.weight(1f),
        )
        MetricCard(
            label = stringResource(R.string.training_form),
            value = signed(form.roundToInt()),
            caption = stringResource(formStatusRes(form)),
            color = colors.textPrimary,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun MetricCard(label: String, value: String, caption: String, color: Color, modifier: Modifier) {
    RideCompCard(modifier = modifier) {
        Text(text = label.uppercase(), style = RideCompTheme.typography.navLabel, color = color)
        Text(text = value, style = RideCompTheme.typography.statValue, color = RideCompTheme.colors.textPrimary)
        Text(text = caption, style = RideCompTheme.typography.navLabel, color = RideCompTheme.colors.textSecondary)
    }
}

/** TREN 8 MINGGU with the method the loads come from. */
@Composable
private fun TrendCard(overview: TrainingOverview) {
    val colors = RideCompTheme.colors
    val series = persistentListOf(
        ChartSeries(
            stringResource(R.string.training_fitness),
            overview.days.map { it.ctl }.toImmutableList(),
            colors.track,
            LineStyle.SOLID,
        ),
        ChartSeries(
            stringResource(R.string.training_fatigue),
            overview.days.map { it.atl }.toImmutableList(),
            colors.accent,
            LineStyle.DASHED,
        ),
        ChartSeries(
            stringResource(R.string.training_form),
            overview.days.map { it.tsb }.toImmutableList(),
            colors.textSecondary,
            LineStyle.DOTTED,
        ),
    )
    RideCompCard {
        Text(
            text = pluralStringResource(R.plurals.training_trend, TREND_WEEKS, TREND_WEEKS).uppercase(),
            style = RideCompTheme.typography.dataLabel,
            color = colors.textSecondary,
        )
        Text(
            text = stringResource(methodRes(overview.methods)),
            style = RideCompTheme.typography.caption,
            color = colors.textTertiary,
        )
        // A single day is a dot, not a trend: say when it will appear instead of an empty box (novice test).
        if (overview.days.size < MIN_TREND_DAYS) {
            Text(
                text = stringResource(R.string.training_trend_later),
                style = RideCompTheme.typography.body,
                color = colors.textSecondary,
            )
        } else {
            TrendChart(series = series, summary = stringResource(R.string.training_trend_summary))
        }
    }
}

private const val MIN_TREND_DAYS = 2

/** MINGGU INI: rides · distance · hours · load. */
@Composable
private fun WeekCard(overview: TrainingOverview, formatter: UnitFormatter) {
    val week = overview.week
    Column(verticalArrangement = Arrangement.spacedBy(RideCompDimens.spacingXs)) {
        Text(
            text = stringResource(R.string.training_week).uppercase(),
            style = RideCompTheme.typography.dataLabel,
            color = RideCompTheme.colors.textSecondary,
        )
        StatGrid(
            persistentListOf(
                StatItem(stringResource(R.string.training_week_rides), week.rides.toString()),
                StatItem(
                    stringResource(R.string.training_week_distance, formatter.distanceUnit),
                    formatter.distance(week.distanceM),
                ),
                StatItem(
                    stringResource(R.string.training_week_time),
                    formatter.duration(week.movingS * Units.MS_PER_SECOND),
                ),
                StatItem(stringResource(R.string.training_week_load), week.load.roundToInt().toString()),
            ),
            columns = WEEK_COLUMNS,
        )
    }
}

/** ZONA HR MINGGU INI: a bar per zone, longest = full width. */
@Composable
private fun ZonesCard(zonesS: List<Long>, formatter: UnitFormatter) {
    if (zonesS.isEmpty()) return
    val colors = RideCompTheme.colors
    val longest = zonesS.max().coerceAtLeast(1)
    RideCompCard {
        Text(
            text = stringResource(R.string.training_zones).uppercase(),
            style = RideCompTheme.typography.dataLabel,
            color = colors.textSecondary,
        )
        zonesS.forEachIndexed { index, seconds ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(RideCompDimens.spacingSm),
            ) {
                Text(
                    text = stringResource(R.string.training_zone, index + 1),
                    style = RideCompTheme.typography.caption,
                    color = colors.textSecondary,
                    modifier = Modifier.width(RideCompDimens.zoneLabelWidth),
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(RideCompDimens.storageBarHeight)
                        .clip(RoundedCornerShape(RideCompDimens.storageBarHeight))
                        .background(colors.divider),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(seconds.toFloat() / longest)
                            .fillMaxHeight()
                            .background(colors.gradeBands.getOrElse(index) { colors.accent }),
                    )
                }
                Text(
                    text = formatter.duration(seconds * Units.MS_PER_SECOND),
                    style = RideCompTheme.typography.caption,
                    color = colors.textPrimary,
                )
            }
        }
    }
}

@StringRes
private fun formStatusRes(form: Double): Int = when {
    form > TrainingConfig.FORM_FRESH -> R.string.training_form_fresh
    form < TrainingConfig.FORM_TIRED -> R.string.training_form_tired
    else -> R.string.training_form_neutral
}

@StringRes
private fun methodRes(methods: List<LoadMethod>): Int = when {
    LoadMethod.POWER in methods && LoadMethod.HEART_RATE in methods -> R.string.training_method_mixed
    LoadMethod.POWER in methods -> R.string.training_method_power
    else -> R.string.training_method_hr
}

private fun signed(value: Int): String = if (value > 0) "+$value" else value.toString()

private const val WEEK_COLUMNS = 2
private const val TREND_WEEKS = TrainingConfig.TREND_WEEKS.toInt()
