package com.ridecomp.feature.settings.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ridecomp.core.config.ProfileDefaults
import com.ridecomp.core.model.Sex
import com.ridecomp.core.usecase.ProfileField
import com.ridecomp.feature.settings.R
import com.ridecomp.feature.settings.viewmodel.ProfileEffect
import com.ridecomp.feature.settings.viewmodel.ProfileUiEvent
import com.ridecomp.feature.settings.viewmodel.ProfileUiState
import com.ridecomp.feature.settings.viewmodel.ProfileViewModel
import com.ridecomp.ui.component.BigButton
import com.ridecomp.ui.component.Choice
import com.ridecomp.ui.component.ChoiceChips
import com.ridecomp.ui.component.NumberField
import com.ridecomp.ui.component.ScreenScaffold
import com.ridecomp.ui.preview.RideCompPreview
import com.ridecomp.ui.preview.ThemePreviews
import com.ridecomp.ui.theme.RideCompDimens
import com.ridecomp.ui.theme.RideCompTheme
import com.ridecomp.ui.udf.ObserveEffects
import kotlinx.collections.immutable.toImmutableList
import org.koin.androidx.compose.koinViewModel

/** Profile settings screen (T1.2). */
@Composable
fun ProfileRoute(onSaved: () -> Unit, modifier: Modifier = Modifier, viewModel: ProfileViewModel = koinViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ObserveEffects(viewModel.effects) { effect ->
        when (effect) {
            ProfileEffect.Saved -> onSaved()
        }
    }
    ScreenScaffold(modifier = modifier, title = stringResource(R.string.profile_title)) {
        ProfileForm(state = state, onEvent = viewModel::onEvent)
    }
}

/** Stateless, scrollable profile form; reused by onboarding. */
@Composable
fun ProfileForm(state: ProfileUiState, onEvent: (ProfileUiEvent) -> Unit, modifier: Modifier = Modifier) {
    val error = stringResource(R.string.profile_error_invalid)
    fun errorOf(field: ProfileField) = error.takeIf { field in state.invalidFields }
    fun change(field: ProfileField): (String) -> Unit = { onEvent(ProfileUiEvent.FieldChanged(field, it)) }
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()).padding(bottom = RideCompDimens.spacingL),
        verticalArrangement = Arrangement.spacedBy(RideCompDimens.spacingS),
    ) {
        NumberField(
            label = stringResource(R.string.profile_weight),
            value = state.weightKg,
            onValueChange = change(ProfileField.WEIGHT),
            unit = stringResource(R.string.unit_kg),
            error = errorOf(ProfileField.WEIGHT),
            isDecimal = true,
        )
        NumberField(
            label = stringResource(R.string.profile_bike_weight),
            value = state.bikeWeightKg,
            onValueChange = change(ProfileField.BIKE_WEIGHT),
            unit = stringResource(R.string.unit_kg),
            error = errorOf(ProfileField.BIKE_WEIGHT),
            isDecimal = true,
        )
        NumberField(
            label = stringResource(R.string.profile_wheel),
            value = state.wheelCircumferenceMm,
            onValueChange = change(ProfileField.WHEEL_CIRCUMFERENCE),
            unit = stringResource(R.string.unit_mm),
            error = errorOf(ProfileField.WHEEL_CIRCUMFERENCE),
        )
        ChoiceChips(
            label = stringResource(R.string.profile_wheel_common),
            choices = ProfileDefaults.COMMON_WHEELS_MM.map { (label, mm) -> Choice<Int?>(mm, label) }.toImmutableList(),
            selected = state.wheelCircumferenceMm.toIntOrNull(),
            onSelect = { mm -> mm?.let { onEvent(ProfileUiEvent.WheelPicked(it)) } },
        )
        HeartRateFields(state, onEvent, ::errorOf)
        NumberField(
            label = stringResource(R.string.profile_ftp),
            value = state.ftpW,
            onValueChange = change(ProfileField.FTP),
            unit = stringResource(R.string.unit_watt),
            error = errorOf(ProfileField.FTP),
        )
        SexChoice(selected = state.sex, onSelect = { onEvent(ProfileUiEvent.SexChanged(it)) })
        BigButton(
            text = stringResource(R.string.profile_save),
            onClick = { onEvent(ProfileUiEvent.Save) },
            isEnabled = state.isLoaded && !state.isSaving,
            modifier = Modifier.padding(horizontal = RideCompDimens.spacingM),
        )
    }
}

@Composable
private fun HeartRateFields(
    state: ProfileUiState,
    onEvent: (ProfileUiEvent) -> Unit,
    errorOf: (ProfileField) -> String?,
) {
    NumberField(
        label = stringResource(R.string.profile_birth_year),
        value = state.birthYear,
        onValueChange = { onEvent(ProfileUiEvent.FieldChanged(ProfileField.BIRTH_YEAR, it)) },
        error = errorOf(ProfileField.BIRTH_YEAR),
    )
    NumberField(
        label = stringResource(R.string.profile_hr_max),
        value = state.hrMaxBpm,
        onValueChange = { onEvent(ProfileUiEvent.FieldChanged(ProfileField.HR_MAX, it)) },
        unit = stringResource(R.string.unit_bpm),
        error = errorOf(ProfileField.HR_MAX),
    )
    if (state.isHrMaxEstimated) {
        Text(
            text = stringResource(R.string.profile_hr_max_estimated),
            style = RideCompTheme.typography.dataLabel,
            color = RideCompTheme.colors.warnText,
            modifier = Modifier.padding(horizontal = RideCompDimens.spacingM),
        )
    }
    TextButton(
        onClick = { onEvent(ProfileUiEvent.EstimateHrMax) },
        modifier = Modifier.padding(horizontal = RideCompDimens.spacingS),
    ) {
        Text(stringResource(R.string.profile_estimate_hr_max))
    }
    NumberField(
        label = stringResource(R.string.profile_hr_rest),
        value = state.hrRestBpm,
        onValueChange = { onEvent(ProfileUiEvent.FieldChanged(ProfileField.HR_REST, it)) },
        unit = stringResource(R.string.unit_bpm),
        error = errorOf(ProfileField.HR_REST),
    )
}

@Composable
private fun SexChoice(selected: Sex?, onSelect: (Sex?) -> Unit) {
    ChoiceChips(
        label = stringResource(R.string.profile_sex),
        choices = listOf<Choice<Sex?>>(
            Choice(null, stringResource(R.string.profile_sex_unset)),
            Choice(Sex.MALE, stringResource(R.string.profile_sex_male)),
            Choice(Sex.FEMALE, stringResource(R.string.profile_sex_female)),
        ).toImmutableList(),
        selected = selected,
        onSelect = onSelect,
    )
}

@ThemePreviews
@Composable
private fun ProfileFormPreview() {
    RideCompPreview {
        ProfileForm(
            state = ProfileUiState(
                weightKg = "70",
                bikeWeightKg = "9",
                wheelCircumferenceMm = "2105",
                invalidFields = setOf(ProfileField.FTP),
                isLoaded = true,
            ),
            onEvent = {},
        )
    }
}
