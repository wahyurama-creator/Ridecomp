package com.ridecomp.feature.ride.viewmodel

import androidx.compose.runtime.Immutable
import androidx.lifecycle.viewModelScope
import com.ridecomp.core.model.ActivityProfile
import com.ridecomp.core.model.RidingPosition
import com.ridecomp.core.model.ScreenMode
import com.ridecomp.core.model.Surface
import com.ridecomp.core.usecase.DeleteActivityProfileUseCase
import com.ridecomp.core.usecase.DuplicateActivityProfileUseCase
import com.ridecomp.core.usecase.ObserveActivityProfileUseCase
import com.ridecomp.core.usecase.ObserveActivityProfilesUseCase
import com.ridecomp.core.usecase.SaveActivityProfileUseCase
import com.ridecomp.ui.udf.NoEffect
import com.ridecomp.ui.udf.UdfViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.launch

@Immutable
data class ActivityProfilesUiState(
    val profiles: ImmutableList<ActivityProfile> = persistentListOf(),
    val deleteCandidate: ActivityProfile? = null,
    val isLoaded: Boolean = false,
)

sealed interface ActivityProfilesUiEvent {
    /** [copyName] is the localized "<name> (copy)". */
    data class Duplicate(val id: Long, val copyName: String) : ActivityProfilesUiEvent

    data class AskDelete(val profile: ActivityProfile) : ActivityProfilesUiEvent

    data object ConfirmDelete : ActivityProfilesUiEvent

    data object DismissDelete : ActivityProfilesUiEvent
}

/** Activity profile list (12-FEATURE-SPECS §4): open, duplicate, delete custom profiles. */
class ActivityProfilesViewModel(
    observeProfiles: ObserveActivityProfilesUseCase,
    private val duplicate: DuplicateActivityProfileUseCase,
    private val delete: DeleteActivityProfileUseCase,
) : UdfViewModel<ActivityProfilesUiState, ActivityProfilesUiEvent, NoEffect>(ActivityProfilesUiState()) {
    init {
        viewModelScope.launch {
            observeProfiles().collect { updateState { copy(profiles = it.toImmutableList(), isLoaded = true) } }
        }
    }

    override fun onEvent(event: ActivityProfilesUiEvent) {
        when (event) {
            is ActivityProfilesUiEvent.Duplicate -> viewModelScope.launch { duplicate(event.id, event.copyName) }
            is ActivityProfilesUiEvent.AskDelete ->
                if (!event.profile.isBuiltIn) updateState { copy(deleteCandidate = event.profile) }
            ActivityProfilesUiEvent.DismissDelete -> updateState { copy(deleteCandidate = null) }
            ActivityProfilesUiEvent.ConfirmDelete -> {
                val candidate = currentState.deleteCandidate ?: return
                updateState { copy(deleteCandidate = null) }
                viewModelScope.launch { delete(candidate.id) }
            }
        }
    }
}

/** The profile being edited; null until loaded. */
@Immutable
data class ActivityProfileEditorUiState(val profile: ActivityProfile? = null)

sealed interface ActivityProfileEditorUiEvent {
    data class Name(val name: String) : ActivityProfileEditorUiEvent

    data class Gps(val isEnabled: Boolean) : ActivityProfileEditorUiEvent

    /** null = general setting. */
    data class AutoPause(val isEnabled: Boolean?) : ActivityProfileEditorUiEvent

    /** null = general setting, [ActivityProfile.AUTO_LAP_OFF] = off. */
    data class AutoLap(val meters: Double?) : ActivityProfileEditorUiEvent

    data class Screen(val mode: ScreenMode?) : ActivityProfileEditorUiEvent

    data class SurfaceChanged(val surface: Surface) : ActivityProfileEditorUiEvent

    data class Position(val position: RidingPosition) : ActivityProfileEditorUiEvent

    /** Back to the general data pages. */
    data object UseGeneralPages : ActivityProfileEditorUiEvent
}

/**
 * Edits one profile; like the ride settings screen every change is saved immediately and the screen shows
 * the stored value.
 */
class ActivityProfileEditorViewModel(
    private val profileId: Long,
    private val getProfile: ObserveActivityProfileUseCase,
    private val saveProfile: SaveActivityProfileUseCase,
) : UdfViewModel<ActivityProfileEditorUiState, ActivityProfileEditorUiEvent, NoEffect>(ActivityProfileEditorUiState()) {
    init {
        reload()
    }

    /** Called when returning from the page editor, which saves pages itself. */
    fun reload() {
        viewModelScope.launch { getProfile(profileId)?.let { stored -> updateState { copy(profile = stored) } } }
    }

    override fun onEvent(event: ActivityProfileEditorUiEvent) {
        val current = currentState.profile ?: return
        val updated = when (event) {
            is ActivityProfileEditorUiEvent.Name -> current.copy(name = event.name)
            is ActivityProfileEditorUiEvent.Gps -> current.copy(isGpsEnabled = event.isEnabled)
            is ActivityProfileEditorUiEvent.AutoPause -> current.copy(isAutoPauseEnabled = event.isEnabled)
            is ActivityProfileEditorUiEvent.AutoLap -> current.copy(autoLapM = event.meters)
            is ActivityProfileEditorUiEvent.Screen -> current.copy(screenMode = event.mode)
            is ActivityProfileEditorUiEvent.SurfaceChanged -> current.copy(surface = event.surface)
            is ActivityProfileEditorUiEvent.Position -> current.copy(ridingPosition = event.position)
            ActivityProfileEditorUiEvent.UseGeneralPages -> current.copy(dataPages = null)
        }
        updateState { copy(profile = updated) }
        viewModelScope.launch { saveProfile(updated) }
    }
}
