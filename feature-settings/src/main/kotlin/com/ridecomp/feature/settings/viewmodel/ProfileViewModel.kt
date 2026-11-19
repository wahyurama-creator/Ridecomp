package com.ridecomp.feature.settings.viewmodel

import androidx.compose.runtime.Immutable
import androidx.lifecycle.viewModelScope
import com.ridecomp.core.config.ProfileDefaults
import com.ridecomp.core.model.RiderProfile
import com.ridecomp.core.model.RidingPosition
import com.ridecomp.core.model.Sex
import com.ridecomp.core.model.Surface
import com.ridecomp.core.usecase.EstimateHrMaxUseCase
import com.ridecomp.core.usecase.ObserveProfileUseCase
import com.ridecomp.core.usecase.ProfileField
import com.ridecomp.core.usecase.SaveProfileUseCase
import com.ridecomp.core.util.fold
import com.ridecomp.core.util.toDecimalOrNull
import com.ridecomp.core.util.toPlainString
import com.ridecomp.ui.udf.UdfViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Profile form; numbers stay text until saved so the user can type freely (T1.2, 02-DESIGN §3.1). */
@Immutable
data class ProfileUiState(
    val weightKg: String = "",
    /** Not edited here any more (each saved bike has its own, D-89), but kept so saving never resets it. */
    val bikeWeightKg: String = "",
    val wheelCircumferenceMm: String = "",
    val hrMaxBpm: String = "",
    val hrRestBpm: String = "",
    val ftpW: String = "",
    val birthYear: String = "",
    val sex: Sex? = null,
    /** Hand position for estimated power (13-UI-SCREENS #2). */
    val ridingPosition: RidingPosition = RidingPosition.HOODS,
    /** Not edited here, but kept so saving never resets it. */
    val surface: Surface = Surface.ROAD,
    val invalidFields: Set<ProfileField> = emptySet(),
    val isHrMaxEstimated: Boolean = false,
    val isLoaded: Boolean = false,
    val isSaving: Boolean = false,
) {
    /** The 220 − age estimate needs a complete birth year (T11.5a): until then the action is not offered. */
    val canEstimateHrMax: Boolean get() = (birthYear.toIntOrNull() ?: 0) >= ProfileDefaults.MIN_BIRTH_YEAR
}

sealed interface ProfileUiEvent {
    data class FieldChanged(val field: ProfileField, val text: String) : ProfileUiEvent

    data class SexChanged(val sex: Sex?) : ProfileUiEvent

    data class PositionChanged(val position: RidingPosition) : ProfileUiEvent

    data object EstimateHrMax : ProfileUiEvent

    data object Save : ProfileUiEvent
}

sealed interface ProfileEffect {
    data object Saved : ProfileEffect
}

class ProfileViewModel(
    private val observeProfile: ObserveProfileUseCase,
    private val saveProfile: SaveProfileUseCase,
    private val estimateHrMax: EstimateHrMaxUseCase,
) : UdfViewModel<ProfileUiState, ProfileUiEvent, ProfileEffect>(ProfileUiState()) {
    init {
        viewModelScope.launch {
            val profile = observeProfile().first()
            updateState { profile.toUiState() }
        }
    }

    override fun onEvent(event: ProfileUiEvent) {
        when (event) {
            is ProfileUiEvent.FieldChanged -> updateState { withField(event.field, event.text) }
            is ProfileUiEvent.SexChanged -> updateState { copy(sex = event.sex) }
            is ProfileUiEvent.PositionChanged -> updateState { copy(ridingPosition = event.position) }
            ProfileUiEvent.EstimateHrMax -> estimate()
            ProfileUiEvent.Save -> save()
        }
    }

    private fun estimate() {
        val year = currentState.birthYear.toIntOrNull() ?: return updateState {
            copy(invalidFields = invalidFields + ProfileField.BIRTH_YEAR)
        }
        updateState {
            copy(
                hrMaxBpm = estimateHrMax(year).toString(),
                isHrMaxEstimated = true,
                invalidFields =
                invalidFields - ProfileField.HR_MAX,
            )
        }
    }

    private fun save() {
        val profile = currentState.toProfileOrNull()
        if (profile == null) {
            updateState { copy(invalidFields = unparsableFields()) }
            return
        }
        updateState { copy(isSaving = true) }
        viewModelScope.launch {
            saveProfile(profile).fold(
                onSuccess = {
                    updateState { copy(isSaving = false, invalidFields = emptySet()) }
                    sendEffect(ProfileEffect.Saved)
                },
                onFailure = { error -> updateState { copy(isSaving = false, invalidFields = error.fields) } },
            )
        }
    }
}

private fun RiderProfile.toUiState() = ProfileUiState(
    weightKg = weightKg.toPlainString(),
    bikeWeightKg = bikeWeightKg.toPlainString(),
    wheelCircumferenceMm = wheelCircumferenceMm.toString(),
    hrMaxBpm = hrMaxBpm?.toString().orEmpty(),
    hrRestBpm = hrRestBpm?.toString().orEmpty(),
    ftpW = ftpW?.toString().orEmpty(),
    birthYear = birthYear?.toString().orEmpty(),
    sex = sex,
    ridingPosition = ridingPosition,
    surface = surface,
    isLoaded = true,
)

private fun ProfileUiState.withField(field: ProfileField, text: String): ProfileUiState {
    val cleared = copy(invalidFields = invalidFields - field)
    return when (field) {
        ProfileField.WEIGHT -> cleared.copy(weightKg = text)
        ProfileField.BIKE_WEIGHT -> cleared.copy(bikeWeightKg = text)
        ProfileField.WHEEL_CIRCUMFERENCE -> cleared.copy(wheelCircumferenceMm = text)
        ProfileField.HR_MAX -> cleared.copy(hrMaxBpm = text, isHrMaxEstimated = false)
        ProfileField.HR_REST -> cleared.copy(hrRestBpm = text)
        ProfileField.FTP -> cleared.copy(ftpW = text)
        ProfileField.BIRTH_YEAR -> cleared.copy(birthYear = text)
    }
}

private fun String.toOptionalInt(): Int? = trim().takeIf { it.isNotEmpty() }?.toIntOrNull()

private fun String.isUnparsableOptionalInt(): Boolean = trim().isNotEmpty() && trim().toIntOrNull() == null

/** The typed profile, or null when any field cannot be parsed (see [unparsableFields]). */
private fun ProfileUiState.toProfileOrNull(): RiderProfile? {
    if (unparsableFields().isNotEmpty()) return null
    // Invariant: unparsableFields() is empty, so the required fields parse.
    return RiderProfile(
        weightKg = requireNotNull(weightKg.toDecimalOrNull()) { "weight" },
        bikeWeightKg = requireNotNull(bikeWeightKg.toDecimalOrNull()) { "bike weight" },
        wheelCircumferenceMm = requireNotNull(wheelCircumferenceMm.trim().toIntOrNull()) { "wheel" },
        hrMaxBpm = hrMaxBpm.toOptionalInt(),
        hrRestBpm = hrRestBpm.toOptionalInt(),
        ftpW = ftpW.toOptionalInt(),
        birthYear = birthYear.toOptionalInt(),
        sex = sex,
        ridingPosition = ridingPosition,
        surface = surface,
    )
}

private fun ProfileUiState.unparsableFields(): Set<ProfileField> = buildSet {
    if (weightKg.toDecimalOrNull() == null) add(ProfileField.WEIGHT)
    if (bikeWeightKg.toDecimalOrNull() == null) add(ProfileField.BIKE_WEIGHT)
    if (wheelCircumferenceMm.trim().toIntOrNull() == null) add(ProfileField.WHEEL_CIRCUMFERENCE)
    if (hrMaxBpm.isUnparsableOptionalInt()) add(ProfileField.HR_MAX)
    if (hrRestBpm.isUnparsableOptionalInt()) add(ProfileField.HR_REST)
    if (ftpW.isUnparsableOptionalInt()) add(ProfileField.FTP)
    if (birthYear.isUnparsableOptionalInt()) add(ProfileField.BIRTH_YEAR)
}
