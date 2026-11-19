package com.ridecomp.feature.training.viewmodel

import androidx.compose.runtime.Immutable
import androidx.lifecycle.viewModelScope
import com.ridecomp.core.model.UnitSystem
import com.ridecomp.core.repository.RankedEffort
import com.ridecomp.core.repository.RankedSegmentEffort
import com.ridecomp.core.usecase.ObserveRideResultsUseCase
import com.ridecomp.core.usecase.ObserveRideSettingsUseCase
import com.ridecomp.core.usecase.isTopRank
import com.ridecomp.ui.udf.NoEffect
import com.ridecomp.ui.udf.UdfViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.launch

/** One line of the results: a best effort, or a pass over a segment (opens the segment). */
@Immutable
sealed interface RideResultLine {
    val rank: Int

    data class Effort(val ranked: RankedEffort) : RideResultLine {
        override val rank: Int get() = ranked.rank
    }

    data class Pass(val ranked: RankedSegmentEffort) : RideResultLine {
        override val rank: Int get() = ranked.rank
    }
}

@Immutable
data class RideResultsUiState(
    val effortCount: Int = 0,
    val segmentCount: Int = 0,
    /** By rank; on a tie, segments before best efforts. */
    val lines: ImmutableList<RideResultLine> = persistentListOf(),
    val unitSystem: UnitSystem = UnitSystem.METRIC,
) {
    val isEmpty: Boolean get() = lines.isEmpty()
}

/** The "Results" card of the ride detail (T11.7); nothing is stored here. */
class RideResultsViewModel(
    activityId: Long,
    observeResults: ObserveRideResultsUseCase,
    observeSettings: ObserveRideSettingsUseCase,
) : UdfViewModel<RideResultsUiState, Unit, NoEffect>(RideResultsUiState()) {
    init {
        viewModelScope.launch {
            observeResults(activityId).collect { results ->
                // Medals first, a segment before an effort of the same rank (segments are the rider's own
                // choice); passes outside the top 3 last, in ride order (stable sort).
                val lines = (results.segments.map(RideResultLine::Pass) + results.efforts.map(RideResultLine::Effort))
                    .sortedBy { if (it.rank.isTopRank) it.rank else Int.MAX_VALUE }
                updateState {
                    copy(
                        effortCount = results.efforts.size,
                        segmentCount = results.segments.size,
                        lines = lines.toImmutableList(),
                    )
                }
            }
        }
        viewModelScope.launch {
            observeSettings().collect { settings -> updateState { copy(unitSystem = settings.unitSystem) } }
        }
    }

    override fun onEvent(event: Unit) = Unit
}
