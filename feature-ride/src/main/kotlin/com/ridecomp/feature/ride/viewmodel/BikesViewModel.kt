package com.ridecomp.feature.ride.viewmodel

import androidx.compose.runtime.Immutable
import androidx.lifecycle.viewModelScope
import com.ridecomp.core.config.ProfileDefaults
import com.ridecomp.core.model.ActivityProfile
import com.ridecomp.core.model.Bike
import com.ridecomp.core.usecase.BikeField
import com.ridecomp.core.usecase.DeleteBikeUseCase
import com.ridecomp.core.usecase.GetBikeUseCase
import com.ridecomp.core.usecase.ObserveActivityProfilesUseCase
import com.ridecomp.core.usecase.ObserveBikeChoiceUseCase
import com.ridecomp.core.usecase.SaveBikeUseCase
import com.ridecomp.core.usecase.SelectBikeUseCase
import com.ridecomp.core.util.fold
import com.ridecomp.core.util.toDecimalOrNull
import com.ridecomp.core.util.toPlainString
import com.ridecomp.ui.udf.NoEffect
import com.ridecomp.ui.udf.UdfViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@Immutable
data class BikesUiState(
    val bikes: ImmutableList<Bike> = persistentListOf(),
    /** For the type name of each bike. */
    val profiles: ImmutableList<ActivityProfile> = persistentListOf(),
    /** The bike preselected for the next ride. */
    val selectedId: Long? = null,
    val isLoaded: Boolean = false,
)

sealed interface BikesUiEvent {
    /** "Use for the next ride". */
    data class Select(val id: Long) : BikesUiEvent
}

/** "My bikes" (D-89): saved once, picked before each ride. */
class BikesViewModel(
    observeBikes: ObserveBikeChoiceUseCase,
    observeProfiles: ObserveActivityProfilesUseCase,
    private val selectBike: SelectBikeUseCase,
) : UdfViewModel<BikesUiState, BikesUiEvent, NoEffect>(BikesUiState()) {
    init {
        viewModelScope.launch {
            combine(observeBikes(), observeProfiles()) { choice, profiles ->
                BikesUiState(choice.bikes.toImmutableList(), profiles.toImmutableList(), choice.selected?.id, true)
            }.collect { state -> updateState { state } }
        }
    }

    override fun onEvent(event: BikesUiEvent) {
        when (event) {
            is BikesUiEvent.Select -> viewModelScope.launch { selectBike(event.id) }
        }
    }
}

/** Which bike the editor opens. */
sealed interface BikeTarget {
    data object New : BikeTarget

    data class Existing(val id: Long) : BikeTarget

    /** Onboarding: the bike created with the app, filled in on first launch. */
    data object First : BikeTarget
}

/** Bike form; numbers stay text until saved so the rider can type freely (like the profile form). */
@Immutable
data class BikeEditorUiState(
    val id: Long = 0,
    val name: String = "",
    /** The bike type (an activity profile). */
    val profileId: Long? = null,
    val weightKg: String = "",
    val wheelCircumferenceMm: String = "",
    val profiles: ImmutableList<ActivityProfile> = persistentListOf(),
    val invalidFields: Set<BikeField> = emptySet(),
    /** The last bike cannot be deleted: a ride always has one. */
    val canDelete: Boolean = false,
    val isDeleteAsked: Boolean = false,
    val isLoaded: Boolean = false,
    val isSaving: Boolean = false,
) {
    val isNew: Boolean get() = id == 0L
}

sealed interface BikeEditorUiEvent {
    data class NameChanged(val name: String) : BikeEditorUiEvent

    data class TypePicked(val profileId: Long) : BikeEditorUiEvent

    data class FieldChanged(val field: BikeField, val text: String) : BikeEditorUiEvent

    /** Wheel size picked from the common sizes list. */
    data class WheelPicked(val circumferenceMm: Int) : BikeEditorUiEvent

    data object Save : BikeEditorUiEvent

    data object AskDelete : BikeEditorUiEvent

    data object ConfirmDelete : BikeEditorUiEvent

    data object DismissDelete : BikeEditorUiEvent
}

sealed interface BikeEditorEffect {
    /** Saved or deleted: close the form. */
    data object Done : BikeEditorEffect
}

/** Use cases of the bike form, grouped to keep the constructor short (KT-05). */
data class BikeEditorUseCases(
    val getBike: GetBikeUseCase,
    val observeBikes: ObserveBikeChoiceUseCase,
    val observeProfiles: ObserveActivityProfilesUseCase,
    val save: SaveBikeUseCase,
    val delete: DeleteBikeUseCase,
)

/** Adds or edits one bike: name, type, weight and wheel size (D-89). */
class BikeEditorViewModel(private val target: BikeTarget, private val useCases: BikeEditorUseCases) :
    UdfViewModel<BikeEditorUiState, BikeEditorUiEvent, BikeEditorEffect>(BikeEditorUiState()) {
    init {
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        val profiles = useCases.observeProfiles().first()
        val bikes = useCases.observeBikes().first().bikes
        val bike = when (target) {
            BikeTarget.New -> null
            is BikeTarget.Existing -> useCases.getBike(target.id)
            BikeTarget.First -> bikes.firstOrNull()
        }
        // A new bike, or one whose custom type was deleted, starts as the first type (Road).
        val profileId = bike?.profileId?.takeIf { id -> profiles.any { it.id == id } } ?: profiles.firstOrNull()?.id
        val wheelMm = bike?.wheelCircumferenceMm ?: ProfileDefaults.WHEEL_CIRCUMFERENCE_MM
        updateState {
            BikeEditorUiState(
                id = bike?.id ?: 0,
                name = bike?.name.orEmpty(),
                profileId = profileId,
                weightKg = (bike?.weightKg ?: ProfileDefaults.BIKE_WEIGHT_KG).toPlainString(),
                wheelCircumferenceMm = wheelMm.toString(),
                profiles = profiles.toImmutableList(),
                canDelete = bike != null && bikes.size > 1,
                isLoaded = true,
            )
        }
    }

    override fun onEvent(event: BikeEditorUiEvent) {
        when (event) {
            is BikeEditorUiEvent.NameChanged -> updateState { copy(name = event.name) }
            is BikeEditorUiEvent.TypePicked -> updateState { copy(profileId = event.profileId) }
            is BikeEditorUiEvent.FieldChanged -> updateState { withField(event.field, event.text) }
            is BikeEditorUiEvent.WheelPicked ->
                updateState { withField(BikeField.WHEEL_CIRCUMFERENCE, event.circumferenceMm.toString()) }
            BikeEditorUiEvent.Save -> save()
            BikeEditorUiEvent.AskDelete -> if (currentState.canDelete) updateState { copy(isDeleteAsked = true) }
            BikeEditorUiEvent.DismissDelete -> updateState { copy(isDeleteAsked = false) }
            BikeEditorUiEvent.ConfirmDelete -> delete()
        }
    }

    private fun save() {
        val state = currentState
        if (state.isSaving) return
        val weight = state.weightKg.toDecimalOrNull()
        val wheel = state.wheelCircumferenceMm.trim().toIntOrNull()
        val bike = if (weight != null && wheel != null && state.profileId != null) {
            Bike(state.id, state.name, state.profileId, weight, wheel)
        } else {
            null
        }
        if (bike == null) {
            updateState {
                copy(
                    invalidFields = buildSet {
                        if (weight == null) add(BikeField.WEIGHT)
                        if (wheel == null) add(BikeField.WHEEL_CIRCUMFERENCE)
                    },
                )
            }
            return
        }
        updateState { copy(isSaving = true) }
        viewModelScope.launch {
            useCases.save(bike).fold(
                onSuccess = {
                    updateState { copy(isSaving = false, invalidFields = emptySet()) }
                    sendEffect(BikeEditorEffect.Done)
                },
                onFailure = { error -> updateState { copy(isSaving = false, invalidFields = error.fields) } },
            )
        }
    }

    private fun delete() {
        updateState { copy(isDeleteAsked = false) }
        viewModelScope.launch {
            if (useCases.delete(currentState.id)) sendEffect(BikeEditorEffect.Done)
        }
    }
}

private fun BikeEditorUiState.withField(field: BikeField, text: String): BikeEditorUiState {
    val cleared = copy(invalidFields = invalidFields - field)
    return when (field) {
        BikeField.WEIGHT -> cleared.copy(weightKg = text)
        BikeField.WHEEL_CIRCUMFERENCE -> cleared.copy(wheelCircumferenceMm = text)
    }
}
