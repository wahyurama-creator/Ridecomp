package com.ridecomp.feature.training.viewmodel

import androidx.compose.runtime.Immutable
import androidx.lifecycle.viewModelScope
import com.ridecomp.core.engine.segments.Segment
import com.ridecomp.core.engine.segments.SegmentConfig
import com.ridecomp.core.engine.segments.SegmentError
import com.ridecomp.core.model.UnitSystem
import com.ridecomp.core.repository.SegmentEffortView
import com.ridecomp.core.repository.SegmentSummary
import com.ridecomp.core.usecase.CreateSegmentFromActivityUseCase
import com.ridecomp.core.usecase.DeleteSegmentUseCase
import com.ridecomp.core.usecase.LoadTrackOutlineUseCase
import com.ridecomp.core.usecase.ObserveActivityUseCase
import com.ridecomp.core.usecase.ObserveRideSettingsUseCase
import com.ridecomp.core.usecase.ObserveSegmentEffortsUseCase
import com.ridecomp.core.usecase.ObserveSegmentUseCase
import com.ridecomp.core.usecase.ObserveSegmentsUseCase
import com.ridecomp.core.usecase.OutlinePoint
import com.ridecomp.core.util.Outcome
import com.ridecomp.ui.udf.NoEffect
import com.ridecomp.ui.udf.UdfViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@Immutable
data class SegmentsUiState(
    val segments: ImmutableList<SegmentSummary> = persistentListOf(),
    val unitSystem: UnitSystem = UnitSystem.METRIC,
    val isLoaded: Boolean = false,
)

/** Segments tab of History (T4.2, 12-FEATURE-SPECS §9.2): every segment with its record. */
class SegmentsViewModel(observeSegments: ObserveSegmentsUseCase, observeSettings: ObserveRideSettingsUseCase) :
    UdfViewModel<SegmentsUiState, Unit, NoEffect>(SegmentsUiState()) {
    init {
        viewModelScope.launch {
            observeSegments().collect { segments ->
                updateState { copy(segments = segments.toImmutableList(), isLoaded = true) }
            }
        }
        viewModelScope.launch {
            observeSettings().collect { settings -> updateState { copy(unitSystem = settings.unitSystem) } }
        }
    }

    override fun onEvent(event: Unit) = Unit
}

@Immutable
data class SegmentDetailUiState(
    val segment: Segment? = null,
    /** Fastest first. */
    val efforts: ImmutableList<SegmentEffortView> = persistentListOf(),
    val unitSystem: UnitSystem = UnitSystem.METRIC,
    val isDeleteAsked: Boolean = false,
    val isLoaded: Boolean = false,
)

sealed interface SegmentDetailUiEvent {
    data object AskDelete : SegmentDetailUiEvent

    data object ConfirmDelete : SegmentDetailUiEvent

    data object DismissDelete : SegmentDetailUiEvent
}

sealed interface SegmentDetailEffect {
    data object Deleted : SegmentDetailEffect
}

/** Segment detail (T4.2): record, best efforts, trend, delete with confirmation. */
class SegmentDetailViewModel(
    private val segmentId: Long,
    observeSegment: ObserveSegmentUseCase,
    observeEfforts: ObserveSegmentEffortsUseCase,
    observeSettings: ObserveRideSettingsUseCase,
    private val delete: DeleteSegmentUseCase,
) : UdfViewModel<SegmentDetailUiState, SegmentDetailUiEvent, SegmentDetailEffect>(SegmentDetailUiState()) {
    init {
        viewModelScope.launch {
            observeSegment(segmentId).collect { segment -> updateState { copy(segment = segment, isLoaded = true) } }
        }
        viewModelScope.launch {
            observeEfforts(segmentId).collect { efforts -> updateState { copy(efforts = efforts.toImmutableList()) } }
        }
        viewModelScope.launch {
            observeSettings().collect { settings -> updateState { copy(unitSystem = settings.unitSystem) } }
        }
    }

    override fun onEvent(event: SegmentDetailUiEvent) {
        when (event) {
            SegmentDetailUiEvent.AskDelete -> updateState { copy(isDeleteAsked = true) }
            SegmentDetailUiEvent.DismissDelete -> updateState { copy(isDeleteAsked = false) }
            SegmentDetailUiEvent.ConfirmDelete -> {
                updateState { copy(isDeleteAsked = false) }
                viewModelScope.launch {
                    delete(segmentId)
                    sendEffect(SegmentDetailEffect.Deleted)
                }
            }
        }
    }
}

@Immutable
data class SegmentEditorUiState(
    val outline: ImmutableList<OutlinePoint> = persistentListOf(),
    val startM: Double = 0.0,
    val endM: Double = 0.0,
    val name: String = "",
    val unitSystem: UnitSystem = UnitSystem.METRIC,
    val error: SegmentError? = null,
    val isSaving: Boolean = false,
    val isLoaded: Boolean = false,
) {
    val totalM: Double get() = outline.lastOrNull()?.distanceM ?: 0.0
    val lengthM: Double get() = endM - startM
    val isLengthValid: Boolean get() = lengthM in SegmentConfig.MIN_LENGTH_M..SegmentConfig.MAX_LENGTH_M
    val canSave: Boolean get() = isLoaded && isLengthValid && name.isNotBlank() && !isSaving
}

sealed interface SegmentEditorUiEvent {
    data class RangeChanged(val startM: Double, val endM: Double) : SegmentEditorUiEvent

    data class NameChanged(val name: String) : SegmentEditorUiEvent

    data object Save : SegmentEditorUiEvent
}

sealed interface SegmentEditorEffect {
    data class Created(val segmentId: Long) : SegmentEditorEffect
}

/** A best effort to start the editor on (T11.8): its time window and the default "Sprint 20 Nov" name. */
data class SegmentDraft(val windowMs: LongRange, val name: String)

/**
 * New segment from part of a ride (T4.2, D-68): two slider handles over distance, a name, save. With a
 * [draft] the handles start on the best effort's stretch, otherwise on the whole ride named after it.
 */
class SegmentEditorViewModel(
    private val activityId: Long,
    draft: SegmentDraft?,
    loadOutline: LoadTrackOutlineUseCase,
    observeActivity: ObserveActivityUseCase,
    observeSettings: ObserveRideSettingsUseCase,
    private val create: CreateSegmentFromActivityUseCase,
) : UdfViewModel<SegmentEditorUiState, SegmentEditorUiEvent, SegmentEditorEffect>(SegmentEditorUiState()) {
    init {
        viewModelScope.launch {
            // A deleted ride gives an empty editor (nothing to save) instead of waiting forever.
            val rideName = observeActivity(activityId).first()?.name.orEmpty()
            val outline = loadOutline(activityId, draft?.windowMs)
            val total = outline.points.lastOrNull()?.distanceM ?: 0.0
            val window = outline.windowM
            updateState {
                copy(
                    outline = outline.points.toImmutableList(),
                    startM = window?.start ?: 0.0,
                    endM = window?.endInclusive ?: minOf(total, SegmentConfig.MAX_LENGTH_M),
                    name = draft?.name ?: rideName,
                    isLoaded = true,
                )
            }
        }
        viewModelScope.launch {
            observeSettings().collect { settings -> updateState { copy(unitSystem = settings.unitSystem) } }
        }
    }

    override fun onEvent(event: SegmentEditorUiEvent) {
        when (event) {
            is SegmentEditorUiEvent.RangeChanged ->
                updateState { copy(startM = event.startM, endM = event.endM, error = null) }
            is SegmentEditorUiEvent.NameChanged -> updateState { copy(name = event.name) }
            SegmentEditorUiEvent.Save -> save()
        }
    }

    private fun save() {
        val state = currentState
        if (!state.canSave) return
        updateState { copy(isSaving = true) }
        viewModelScope.launch {
            when (val result = create(activityId, state.startM, state.endM, state.name)) {
                is Outcome.Success -> sendEffect(SegmentEditorEffect.Created(result.value))
                is Outcome.Failure -> updateState { copy(error = result.error, isSaving = false) }
            }
        }
    }
}
