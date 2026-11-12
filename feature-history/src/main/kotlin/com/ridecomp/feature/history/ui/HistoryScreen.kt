package com.ridecomp.feature.history.ui

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ridecomp.core.config.Units
import com.ridecomp.core.model.Activity
import com.ridecomp.core.util.UnitFormatter
import com.ridecomp.feature.history.R
import com.ridecomp.feature.history.viewmodel.HistoryUiEvent
import com.ridecomp.feature.history.viewmodel.HistoryUiState
import com.ridecomp.feature.history.viewmodel.HistoryViewModel
import com.ridecomp.ui.component.EmptyState
import com.ridecomp.ui.component.ListRow
import com.ridecomp.ui.component.ScreenScaffold
import com.ridecomp.ui.preview.RideCompPreview
import com.ridecomp.ui.preview.ThemePreviews
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import org.koin.androidx.compose.koinViewModel

/** Loads the next page when this many rows remain below the visible ones. */
private const val LOAD_MORE_THRESHOLD = 5

/** History list (T1.13, 02-DESIGN §3.7). */
@Composable
fun HistoryRoute(
    onStartRide: () -> Unit,
    onOpenActivity: (Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HistoryViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    HistoryScreen(
        state = state,
        onStartRide = onStartRide,
        onOpenActivity = onOpenActivity,
        onLoadMore = { viewModel.onEvent(HistoryUiEvent.LoadMore) },
        modifier = modifier,
    )
}

@Composable
fun HistoryScreen(
    state: HistoryUiState,
    onStartRide: () -> Unit,
    onOpenActivity: (Long) -> Unit,
    onLoadMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ScreenScaffold(modifier = modifier, title = stringResource(R.string.history_title)) {
        if (state.isLoaded && state.rides.isEmpty()) {
            EmptyState(
                message = stringResource(R.string.history_empty),
                actionLabel = stringResource(R.string.history_empty_action),
                onAction = onStartRide,
            )
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
            LazyColumn(state = listState) {
                items(state.rides, key = { it.id }) { ride ->
                    ListRow(
                        title = ride.name,
                        subtitle = ride.subtitle(formatter),
                        onClick = { onOpenActivity(ride.id) },
                    )
                }
            }
        }
    }
}

private val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)

@Composable
private fun Activity.subtitle(formatter: UnitFormatter): String = stringResource(
    R.string.history_item_subtitle,
    DATE_FORMAT.format(Instant.ofEpochMilli(startedAtMs).atZone(ZoneId.systemDefault())),
    "${formatter.distance(totals.distanceM)} ${formatter.distanceUnit}",
    formatter.duration(totals.movingS * Units.MS_PER_SECOND),
)

@ThemePreviews
@Composable
private fun HistoryEmptyPreview() {
    RideCompPreview {
        HistoryScreen(state = HistoryUiState(isLoaded = true), onStartRide = {}, onOpenActivity = {}, onLoadMore = {})
    }
}
