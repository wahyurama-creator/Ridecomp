package com.ridecomp.feature.ride.viewmodel

import androidx.compose.runtime.Immutable
import androidx.lifecycle.viewModelScope
import com.ridecomp.core.config.Units
import com.ridecomp.core.model.BikeComponent
import com.ridecomp.core.model.ComponentType
import com.ridecomp.core.source.Clock
import com.ridecomp.core.usecase.ComponentField
import com.ridecomp.core.usecase.DeleteComponentUseCase
import com.ridecomp.core.usecase.GetComponentUseCase
import com.ridecomp.core.usecase.ObserveComponentWearUseCase
import com.ridecomp.core.usecase.ReplaceComponentUseCase
import com.ridecomp.core.usecase.SaveComponentUseCase
import com.ridecomp.core.util.fold
import com.ridecomp.core.util.toDecimalOrNull
import com.ridecomp.core.util.toPlainString
import com.ridecomp.ui.udf.UdfViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** The part to edit: a new one on [bikeId], or an existing one. */
data class ComponentTarget(val bikeId: Long, val componentId: Long? = null)

/** Component form (T7.3); distances are typed in km and stay text until saved. */
@Immutable
data class ComponentEditorUiState(
    val type: ComponentType = ComponentType.CHAIN,
    val name: String = "",
    /** Empty = no reminder. */
    val limitKm: String = ComponentType.CHAIN.defaultLimitKm?.toString().orEmpty(),
    /** Distance it already had; only for a new part. */
    val initialKm: String = "",
    /** Distance done so far (existing part). */
    val distanceM: Double? = null,
    val isNew: Boolean = true,
    val invalidFields: Set<ComponentField> = emptySet(),
    val isDeleteAsked: Boolean = false,
    val isLoaded: Boolean = false,
)

sealed interface ComponentEditorUiEvent {
    /** Also fills the usual limit of that type. */
    data class TypePicked(val type: ComponentType) : ComponentEditorUiEvent

    data class NameChanged(val name: String) : ComponentEditorUiEvent

    data class LimitChanged(val text: String) : ComponentEditorUiEvent

    data class InitialChanged(val text: String) : ComponentEditorUiEvent

    data object Save : ComponentEditorUiEvent

    /** "Just replaced": back to 0 km from now. */
    data object Replaced : ComponentEditorUiEvent

    data object AskDelete : ComponentEditorUiEvent

    data object ConfirmDelete : ComponentEditorUiEvent

    data object DismissDelete : ComponentEditorUiEvent
}

sealed interface ComponentEditorEffect {
    data object Done : ComponentEditorEffect
}

/** Use cases of the component form, grouped to keep the constructor short (KT-05). */
data class ComponentEditorUseCases(
    val get: GetComponentUseCase,
    val observeWear: ObserveComponentWearUseCase,
    val save: SaveComponentUseCase,
    val replace: ReplaceComponentUseCase,
    val delete: DeleteComponentUseCase,
)

/** Adds or edits a part of a bike, or marks it replaced (T7.3, D-93). */
class ComponentEditorViewModel(
    private val target: ComponentTarget,
    private val useCases: ComponentEditorUseCases,
    private val clock: Clock,
) : UdfViewModel<ComponentEditorUiState, ComponentEditorUiEvent, ComponentEditorEffect>(ComponentEditorUiState()) {
    private var stored: BikeComponent? = null

    init {
        viewModelScope.launch {
            val id = target.componentId
            val component = id?.let { useCases.get(it) }
            stored = component
            if (component == null) {
                updateState { copy(isLoaded = true) }
                return@launch
            }
            val wear = useCases.observeWear(target.bikeId).first().firstOrNull { it.component.id == id }
            updateState {
                copy(
                    type = component.type,
                    name = component.name,
                    limitKm = component.limitM?.let { (it / Units.METERS_PER_KM).toPlainString() }.orEmpty(),
                    distanceM = wear?.distanceM,
                    isNew = false,
                    isLoaded = true,
                )
            }
        }
    }

    override fun onEvent(event: ComponentEditorUiEvent) {
        when (event) {
            is ComponentEditorUiEvent.TypePicked -> updateState {
                copy(
                    type = event.type,
                    limitKm = event.type.defaultLimitKm?.toString().orEmpty(),
                    invalidFields = invalidFields - ComponentField.LIMIT,
                )
            }
            is ComponentEditorUiEvent.NameChanged -> updateState { copy(name = event.name) }
            is ComponentEditorUiEvent.LimitChanged ->
                updateState { copy(limitKm = event.text, invalidFields = invalidFields - ComponentField.LIMIT) }
            is ComponentEditorUiEvent.InitialChanged -> updateState {
                copy(initialKm = event.text, invalidFields = invalidFields - ComponentField.INITIAL_DISTANCE)
            }
            ComponentEditorUiEvent.Save -> save()
            ComponentEditorUiEvent.Replaced -> stored?.let { component ->
                viewModelScope.launch {
                    useCases.replace(component.id)
                    sendEffect(ComponentEditorEffect.Done)
                }
            }
            ComponentEditorUiEvent.AskDelete -> if (!currentState.isNew) updateState { copy(isDeleteAsked = true) }
            ComponentEditorUiEvent.DismissDelete -> updateState { copy(isDeleteAsked = false) }
            ComponentEditorUiEvent.ConfirmDelete -> stored?.let { component ->
                updateState { copy(isDeleteAsked = false) }
                viewModelScope.launch {
                    useCases.delete(component.id)
                    sendEffect(ComponentEditorEffect.Done)
                }
            }
        }
    }

    private fun save() {
        val state = currentState
        val limitKm = state.limitKm.trim().takeIf { it.isNotEmpty() }?.let { it.toDecimalOrNull() ?: Double.NaN }
        val initialKm = state.initialKm.trim().takeIf { it.isNotEmpty() }?.let { it.toDecimalOrNull() ?: Double.NaN }
        val base = stored ?: BikeComponent(bikeId = target.bikeId, type = state.type, installedAtMs = clock.nowMs())
        val component = base.copy(
            type = state.type,
            name = state.name,
            limitM = limitKm?.times(Units.METERS_PER_KM),
            initialDistanceM = if (stored == null) (initialKm ?: 0.0) * Units.METERS_PER_KM else base.initialDistanceM,
        )
        viewModelScope.launch {
            useCases.save(component).fold(
                onSuccess = { sendEffect(ComponentEditorEffect.Done) },
                onFailure = { error -> updateState { copy(invalidFields = error.fields) } },
            )
        }
    }
}
