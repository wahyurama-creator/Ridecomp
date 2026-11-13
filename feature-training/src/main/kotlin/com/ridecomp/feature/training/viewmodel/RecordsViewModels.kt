package com.ridecomp.feature.training.viewmodel

import androidx.compose.runtime.Immutable
import androidx.lifecycle.viewModelScope
import com.ridecomp.core.model.ActivityKind
import com.ridecomp.core.model.UnitSystem
import com.ridecomp.core.repository.PersonalRecord
import com.ridecomp.core.repository.RideEffort
import com.ridecomp.core.usecase.ObservePersonalRecordsUseCase
import com.ridecomp.core.usecase.ObserveRecordKindsUseCase
import com.ridecomp.core.usecase.ObserveRideSettingsUseCase
import com.ridecomp.core.usecase.PreviewNewRecordsUseCase
import com.ridecomp.ui.udf.NoEffect
import com.ridecomp.ui.udf.UdfViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch

@Immutable
data class RecordsUiState(
    val kinds: ImmutableList<ActivityKind> = persistentListOf(),
    val selectedKind: ActivityKind? = null,
    val records: ImmutableList<PersonalRecord> = persistentListOf(),
    val unitSystem: UnitSystem = UnitSystem.METRIC,
    val isLoaded: Boolean = false,
)

sealed interface RecordsUiEvent {
    data class SelectKind(val kind: ActivityKind) : RecordsUiEvent
}

/** Records tab (T4.1, 12-FEATURE-SPECS §8): personal records per profile kind. */
@OptIn(ExperimentalCoroutinesApi::class)
class RecordsViewModel(
    observeRecords: ObservePersonalRecordsUseCase,
    observeKinds: ObserveRecordKindsUseCase,
    observeSettings: ObserveRideSettingsUseCase,
) : UdfViewModel<RecordsUiState, RecordsUiEvent, NoEffect>(RecordsUiState()) {
    private val selected = MutableStateFlow<ActivityKind?>(null)

    init {
        viewModelScope.launch {
            observeKinds().collect { kinds ->
                // Keep the rider's choice while it still has records; otherwise the first kind (road first).
                val kind = selected.value?.takeIf { it in kinds } ?: kinds.firstOrNull()
                selected.value = kind
                updateState {
                    copy(
                        kinds = kinds.toImmutableList(),
                        selectedKind = kind,
                        records = if (kind == null) persistentListOf() else records,
                        isLoaded = isLoaded || kind == null,
                    )
                }
            }
        }
        viewModelScope.launch {
            selected.filterNotNull().flatMapLatest(observeRecords::invoke).collect { records ->
                updateState { copy(records = records.toImmutableList(), isLoaded = true) }
            }
        }
        viewModelScope.launch {
            observeSettings().collect { settings -> updateState { copy(unitSystem = settings.unitSystem) } }
        }
    }

    override fun onEvent(event: RecordsUiEvent) {
        when (event) {
            is RecordsUiEvent.SelectKind -> {
                selected.value = event.kind
                updateState { copy(selectedKind = event.kind) }
            }
        }
    }
}

@Immutable
data class NewRecordsUiState(
    val records: ImmutableList<RideEffort> = persistentListOf(),
    val unitSystem: UnitSystem = UnitSystem.METRIC,
)

/** "N new records" card on the summary before saving (13-UI-SCREENS #10); nothing is stored here. */
class NewRecordsViewModel(
    activityId: Long,
    preview: PreviewNewRecordsUseCase,
    observeSettings: ObserveRideSettingsUseCase,
) : UdfViewModel<NewRecordsUiState, Unit, NoEffect>(NewRecordsUiState()) {
    init {
        viewModelScope.launch {
            val records = preview(activityId).sortedBy { it.effort.key.ordinal }
            updateState { copy(records = records.toImmutableList()) }
        }
        viewModelScope.launch {
            observeSettings().collect { settings -> updateState { copy(unitSystem = settings.unitSystem) } }
        }
    }

    override fun onEvent(event: Unit) = Unit
}
