package com.ridecomp.feature.history.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ridecomp.core.config.Units
import com.ridecomp.core.model.Activity
import com.ridecomp.core.model.PeriodTotals
import com.ridecomp.core.util.UnitFormatter
import com.ridecomp.feature.history.R
import com.ridecomp.feature.history.viewmodel.HistoryUiEvent
import com.ridecomp.feature.history.viewmodel.HistoryUiState
import com.ridecomp.feature.history.viewmodel.HistoryViewModel
import com.ridecomp.ui.R as UiR
import com.ridecomp.ui.component.BigButton
import com.ridecomp.ui.component.BigButtonSize
import com.ridecomp.ui.component.LinkText
import com.ridecomp.ui.component.RideCompCard
import com.ridecomp.ui.component.ScreenScaffold
import com.ridecomp.ui.component.SegmentedTabs
import com.ridecomp.ui.preview.RideCompPreview
import com.ridecomp.ui.preview.ThemePreviews
import com.ridecomp.ui.theme.RideCompDimens
import com.ridecomp.ui.theme.RideCompTheme
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import kotlinx.collections.immutable.toImmutableList
import org.koin.androidx.compose.koinViewModel

/** Loads the next page when this many rows remain below the visible ones. */
private const val LOAD_MORE_THRESHOLD = 5

/** History list (T1.13, 02-DESIGN §3.7, 13-UI-SCREENS #11, #28). */
@Composable
fun HistoryRoute(
    onStartRide: () -> Unit,
    onOpenActivity: (Long) -> Unit,
    modifier: Modifier = Modifier,
    onRestore: () -> Unit = {},
    recordsContent: (@Composable () -> Unit)? = null,
    segmentsContent: (@Composable () -> Unit)? = null,
    trainingContent: (@Composable () -> Unit)? = null,
    viewModel: HistoryViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    HistoryScreen(
        state = state,
        onStartRide = onStartRide,
        onOpenActivity = onOpenActivity,
        onLoadMore = { viewModel.onEvent(HistoryUiEvent.LoadMore) },
        onRestore = onRestore,
        recordsContent = recordsContent,
        segmentsContent = segmentsContent,
        trainingContent = trainingContent,
        modifier = modifier,
    )
}

/** Tabs of History (13-UI-SCREENS #11): Aktivitas · Rekor · Segmen · Latihan. */
private enum class HistoryTab(@param:StringRes val titleRes: Int) {
    ACTIVITIES(R.string.history_tab_activities),
    RECORDS(R.string.history_tab_records),
    SEGMENTS(R.string.history_tab_segments),
    TRAINING(R.string.history_tab_training),
}

/**
 * History with the design's tabs. The records, segments and training contents come from the training feature
 * through the app (feature modules never depend on each other, ARCH-02); without them the tabs are hidden.
 */
@Composable
fun HistoryScreen(
    state: HistoryUiState,
    onStartRide: () -> Unit,
    onOpenActivity: (Long) -> Unit,
    onLoadMore: () -> Unit,
    modifier: Modifier = Modifier,
    onRestore: () -> Unit = {},
    recordsContent: (@Composable () -> Unit)? = null,
    segmentsContent: (@Composable () -> Unit)? = null,
    trainingContent: (@Composable () -> Unit)? = null,
) {
    var tab by rememberSaveable { mutableStateOf(HistoryTab.ACTIVITIES) }
    ScreenScaffold(modifier = modifier, title = stringResource(R.string.history_title)) {
        if (recordsContent != null) {
            SegmentedTabs(
                tabs = HistoryTab.entries.map { stringResource(it.titleRes) }.toImmutableList(),
                selectedIndex = tab.ordinal,
                onSelect = { tab = HistoryTab.entries[it] },
                modifier = Modifier.padding(
                    start = RideCompDimens.screenPaddingHorizontal,
                    end = RideCompDimens.screenPaddingHorizontal,
                    bottom = RideCompDimens.cardGap,
                ),
            )
        }
        when (tab) {
            HistoryTab.ACTIVITIES -> ActivitiesTab(state, onStartRide, onOpenActivity, onLoadMore, onRestore)
            HistoryTab.RECORDS -> recordsContent?.invoke()
            HistoryTab.SEGMENTS -> segmentsContent?.invoke()
            HistoryTab.TRAINING -> trainingContent?.invoke()
        }
    }
}

@Composable
private fun ActivitiesTab(
    state: HistoryUiState,
    onStartRide: () -> Unit,
    onOpenActivity: (Long) -> Unit,
    onLoadMore: () -> Unit,
    onRestore: () -> Unit,
) {
    if (state.isLoaded && state.rides.isEmpty()) {
        EmptyHistory(onStartRide, onRestore)
    } else {
        val listState = rememberLazyListState()
        val isNearEnd by remember {
            derivedStateOf {
                val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                last >= listState.layoutInfo.totalItemsCount - LOAD_MORE_THRESHOLD
            }
        }
        LaunchedEffect(isNearEnd, state.rides.size) { if (isNearEnd && state.hasMore) onLoadMore() }
        val formatter = remember(state.unitSystem) { UnitFormatter(state.unitSystem) }
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(
                start = RideCompDimens.screenPaddingHorizontal,
                end = RideCompDimens.screenPaddingHorizontal,
                bottom = RideCompDimens.screenPaddingBottom,
            ),
            verticalArrangement = Arrangement.spacedBy(RideCompDimens.cardGap),
        ) {
            item { MonthCard(state.month, formatter) }
            items(state.rides, key = { it.id }) { ride ->
                ActivityCard(
                    ride,
                    formatter,
                    state.nowMs,
                    isOnStrava = ride.id in state.onStrava,
                    onClick = { onOpenActivity(ride.id) },
                )
            }
        }
    }
}

/** "BULAN INI · RIDE · TANJAKAN" (13-UI-SCREENS #11). */
@Composable
private fun MonthCard(month: PeriodTotals, format: UnitFormatter) {
    RideCompCard {
        // Below 360 dp a gap keeps "1.234,5 km" off the next column (audit T13.1 #2); none above (R1).
        Row(horizontalArrangement = Arrangement.spacedBy(RideCompTheme.dimens.statColumnGap)) {
            MonthStat(
                stringResource(R.string.history_month),
                "${format.distance(month.distanceM)} ${format.distanceUnit}",
                Modifier.weight(1f),
            )
            MonthStat(stringResource(R.string.history_month_rides), month.rides.toString(), Modifier.weight(1f))
            MonthStat(
                stringResource(R.string.history_month_ascent),
                "${format.elevation(month.ascentM)} ${format.elevationUnit}",
                Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun MonthStat(label: String, value: String, modifier: Modifier) {
    Column(modifier = modifier) {
        Text(
            text = label.uppercase(),
            style = RideCompTheme.typography.navLabel,
            color = RideCompTheme.colors.textSecondary,
        )
        Text(text = value, style = RideCompTheme.typography.statValue, color = RideCompTheme.colors.textPrimary)
    }
}

/** Ride card: name, export status, relative time, distance · time · climbing (13-UI-SCREENS #11). */
@Composable
private fun ActivityCard(
    ride: Activity,
    format: UnitFormatter,
    nowMs: Long,
    isOnStrava: Boolean,
    onClick: () -> Unit,
) {
    val colors = RideCompTheme.colors
    // On Strava says more than "FIT exported": the ride is where most riders want it.
    val isExported = isOnStrava || ride.fitExportedAtMs != null
    RideCompCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = ride.name,
                style = RideCompTheme.typography.cardTitle,
                color = colors.textPrimary,
                modifier = Modifier.weight(1f),
            )
            // Only good news here: a ride that was not exported is normal, not a warning (D-86).
            if (isExported) {
                Text(
                    text = stringResource(
                        if (isOnStrava) R.string.history_on_strava else R.string.history_fit_exported,
                    ),
                    style = RideCompTheme.typography.caption.copy(fontWeight = FontWeight.Bold),
                    color = colors.ok,
                )
            }
        }
        Text(
            text = relativeTime(ride.startedAtMs, nowMs),
            style = RideCompTheme.typography.caption,
            color = colors.textSecondary,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(RideCompDimens.spacingM)) {
            CardStat(format.distance(ride.totals.distanceM), format.distanceUnit)
            CardStat(
                format.duration(ride.totals.movingS * Units.MS_PER_SECOND),
                stringResource(R.string.history_moving),
            )
            CardStat(
                format.elevation(ride.totals.ascentM),
                stringResource(R.string.history_ascent_unit, format.elevationUnit),
            )
        }
    }
}

@Composable
private fun CardStat(value: String, unit: String) {
    val colors = RideCompTheme.colors
    Text(
        text = buildAnnotatedString {
            withStyle(SpanStyle(color = colors.textPrimary, fontWeight = FontWeight.Bold)) { append(value) }
            append(" ")
            append(unit)
        },
        style = RideCompTheme.typography.body,
        color = colors.textTertiary,
    )
}

/** "Hari ini · 05:31", "Kemarin · 16:40", "Selasa · 16:40" within a week, then the date. */
@Composable
private fun relativeTime(startedAtMs: Long, nowMs: Long): String {
    val zone = ZoneId.systemDefault()
    val locale = LocalConfiguration.current.locales[0]
    val start = Instant.ofEpochMilli(startedAtMs).atZone(zone)
    val days = ChronoUnit.DAYS.between(start.toLocalDate(), Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate())
    val day = when {
        days == 0L -> stringResource(R.string.history_today)
        days == 1L -> stringResource(R.string.history_yesterday)
        days in 2 until DAYS_PER_WEEK -> start.dayOfWeek.getDisplayName(TextStyle.FULL, locale)
        else -> DATE_FORMAT.format(start)
    }
    return stringResource(R.string.history_when, day, TIME_FORMAT.format(start))
}

private const val DAYS_PER_WEEK = 7L
private val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
private val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

/** Empty history (13-UI-SCREENS #28): bike drawing, first-ride button, restore link. */
@Composable
private fun EmptyHistory(onStartRide: () -> Unit, onRestore: () -> Unit) {
    val colors = RideCompTheme.colors
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = RideCompDimens.screenPaddingHorizontal),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(RideCompDimens.blockGap, Alignment.CenterVertically),
    ) {
        Icon(
            painter = painterResource(UiR.drawable.ic_empty_bike),
            contentDescription = null,
            tint = colors.disabled,
            modifier = Modifier.size(
                width = RideCompDimens.emptyIllustrationWidth,
                height = RideCompDimens.emptyIllustrationHeight,
            ),
        )
        Text(
            text = stringResource(R.string.history_empty_title),
            style = RideCompTheme.typography.title,
            color = colors.textPrimary,
        )
        Text(
            text = stringResource(R.string.history_empty),
            style = RideCompTheme.typography.body,
            color = colors.textSecondary,
            textAlign = TextAlign.Center,
        )
        BigButton(
            text = stringResource(R.string.history_empty_action),
            onClick = onStartRide,
            size = BigButtonSize.SECONDARY,
            modifier = Modifier.fillMaxWidth(),
        )
        LinkText(stringResource(R.string.history_empty_restore), onClick = onRestore)
    }
}

@ThemePreviews
@Composable
private fun HistoryEmptyPreview() {
    RideCompPreview {
        HistoryScreen(state = HistoryUiState(isLoaded = true), onStartRide = {}, onOpenActivity = {}, onLoadMore = {})
    }
}
