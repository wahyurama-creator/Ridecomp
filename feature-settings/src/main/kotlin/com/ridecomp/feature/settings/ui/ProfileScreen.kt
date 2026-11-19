package com.ridecomp.feature.settings.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ridecomp.core.model.RidingPosition
import com.ridecomp.core.model.Sex
import com.ridecomp.core.usecase.ProfileField
import com.ridecomp.feature.settings.R
import com.ridecomp.feature.settings.viewmodel.ProfileEffect
import com.ridecomp.feature.settings.viewmodel.ProfileUiEvent
import com.ridecomp.feature.settings.viewmodel.ProfileUiState
import com.ridecomp.feature.settings.viewmodel.ProfileViewModel
import com.ridecomp.ui.R as UiR
import com.ridecomp.ui.component.BigButton
import com.ridecomp.ui.component.BigButtonSize
import com.ridecomp.ui.component.Choice
import com.ridecomp.ui.component.ChoiceChips
import com.ridecomp.ui.component.LinkText
import com.ridecomp.ui.component.RideCompCard
import com.ridecomp.ui.component.RideCompTextField
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
fun ProfileRoute(
    onSaved: () -> Unit,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = onSaved,
    viewModel: ProfileViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ObserveEffects(viewModel.effects) { effect ->
        when (effect) {
            ProfileEffect.Saved -> onSaved()
        }
    }
    ScreenScaffold(
        modifier = modifier,
        title = stringResource(R.string.profile_title),
        onBack = onBack,
        backLabel = stringResource(R.string.onboarding_back_label),
    ) {
        ProfileForm(state = state, onEvent = viewModel::onEvent)
    }
}

/**
 * Stateless, scrollable profile form (13-UI-SCREENS #2); reused by onboarding, where the optional
 * birth year, resting HR and sex are left for later ([isOnboarding]).
 */
@Composable
fun ProfileForm(
    state: ProfileUiState,
    onEvent: (ProfileUiEvent) -> Unit,
    modifier: Modifier = Modifier,
    isOnboarding: Boolean = false,
    header: @Composable () -> Unit = {},
) {
    val error = stringResource(R.string.profile_error_invalid)
    fun errorOf(field: ProfileField) = error.takeIf { field in state.invalidFields }
    fun change(field: ProfileField): (String) -> Unit = { onEvent(ProfileUiEvent.FieldChanged(field, it)) }
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(
                start = RideCompDimens.screenPaddingHorizontal,
                end = RideCompDimens.screenPaddingHorizontal,
                bottom = RideCompDimens.screenPaddingBottom,
            ),
        verticalArrangement = Arrangement.spacedBy(RideCompDimens.blockGap),
    ) {
        header()
        // Bike weight and wheel size belong to each saved bike (D-89).
        RideCompTextField(
            label = stringResource(R.string.profile_weight),
            value = state.weightKg,
            onValueChange = change(ProfileField.WEIGHT),
            suffix = stringResource(R.string.unit_kg),
            error = errorOf(ProfileField.WEIGHT),
            keyboardType = KeyboardType.Decimal,
        )
        if (isOnboarding) {
            // A beginner only needs the weight; the rest has sensible defaults (novice test, D-86).
            var isAdvancedShown by rememberSaveable { mutableStateOf(false) }
            AdvancedToggle(isShown = isAdvancedShown, onToggle = { isAdvancedShown = !isAdvancedShown })
            if (isAdvancedShown) AdvancedFields(state, onEvent, ::errorOf)
            Text(
                text = stringResource(R.string.profile_later_note),
                style = RideCompTheme.typography.caption,
                color = RideCompTheme.colors.textSecondary,
            )
        } else {
            AdvancedFields(state, onEvent, ::errorOf)
            OptionalFields(state, onEvent, ::errorOf)
        }
        BigButton(
            text = stringResource(if (isOnboarding) R.string.profile_save_next else R.string.profile_save),
            onClick = { onEvent(ProfileUiEvent.Save) },
            isEnabled = state.isLoaded && !state.isSaving,
            size = BigButtonSize.SECONDARY,
        )
    }
}

/** HR max, FTP and hand position: shown in settings, folded away during onboarding. */
@Composable
private fun AdvancedFields(
    state: ProfileUiState,
    onEvent: (ProfileUiEvent) -> Unit,
    errorOf: (ProfileField) -> String?,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(RideCompDimens.spacingSm)) {
        RideCompTextField(
            label = stringResource(R.string.profile_hr_max),
            value = state.hrMaxBpm,
            onValueChange = { onEvent(ProfileUiEvent.FieldChanged(ProfileField.HR_MAX, it)) },
            suffix = stringResource(R.string.unit_bpm),
            error = errorOf(ProfileField.HR_MAX),
            keyboardType = KeyboardType.Number,
            modifier = Modifier.weight(1f),
        )
        RideCompTextField(
            label = stringResource(R.string.profile_ftp),
            value = state.ftpW,
            onValueChange = { onEvent(ProfileUiEvent.FieldChanged(ProfileField.FTP, it)) },
            suffix = stringResource(R.string.unit_watt),
            error = errorOf(ProfileField.FTP),
            keyboardType = KeyboardType.Number,
            modifier = Modifier.weight(1f),
        )
    }
    if (state.isHrMaxEstimated) {
        Text(
            text = stringResource(R.string.profile_hr_max_estimated),
            style = RideCompTheme.typography.caption,
            color = RideCompTheme.colors.warnText,
        )
    }
    ChoiceChips(
        label = stringResource(R.string.profile_hand_position),
        choices = listOf(
            Choice(RidingPosition.TOPS, stringResource(R.string.profile_hand_tops)),
            Choice(RidingPosition.HOODS, stringResource(R.string.profile_hand_hoods)),
            Choice(RidingPosition.DROPS, stringResource(R.string.profile_hand_drops)),
        ).toImmutableList(),
        selected = state.ridingPosition,
        onSelect = { onEvent(ProfileUiEvent.PositionChanged(it)) },
        horizontalPadding = RideCompDimens.none,
        columns = RidingPosition.entries.size,
    )
}

/** "Pengaturan lanjutan (opsional)": opens the advanced fields in place. */
@Composable
private fun AdvancedToggle(isShown: Boolean, onToggle: () -> Unit) {
    val colors = RideCompTheme.colors
    RideCompCard(onClick = onToggle) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(RideCompDimens.spacingSm),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.profile_advanced_title),
                    style = RideCompTheme.typography.bodyStrong,
                    color = colors.textPrimary,
                )
                Text(
                    text = stringResource(R.string.profile_advanced_subtitle),
                    style = RideCompTheme.typography.caption,
                    color = colors.textSecondary,
                )
            }
            Icon(
                painter = painterResource(UiR.drawable.ic_chevron_down),
                contentDescription = null,
                tint = colors.textSecondary,
                modifier = Modifier
                    .size(RideCompDimens.iconSize)
                    .rotate(if (isShown) HALF_TURN_DEG else 0f),
            )
        }
    }
}

private const val HALF_TURN_DEG = 180f

/** Birth year (with the 220 − age estimate), resting HR and sex: optional, settings only. */
@Composable
private fun OptionalFields(
    state: ProfileUiState,
    onEvent: (ProfileUiEvent) -> Unit,
    errorOf: (ProfileField) -> String?,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(RideCompDimens.spacingSm)) {
        RideCompTextField(
            label = stringResource(R.string.profile_birth_year),
            value = state.birthYear,
            onValueChange = { onEvent(ProfileUiEvent.FieldChanged(ProfileField.BIRTH_YEAR, it)) },
            error = errorOf(ProfileField.BIRTH_YEAR),
            keyboardType = KeyboardType.Number,
            modifier = Modifier.weight(1f),
        )
        RideCompTextField(
            label = stringResource(R.string.profile_hr_rest),
            value = state.hrRestBpm,
            onValueChange = { onEvent(ProfileUiEvent.FieldChanged(ProfileField.HR_REST, it)) },
            suffix = stringResource(R.string.unit_bpm),
            error = errorOf(ProfileField.HR_REST),
            keyboardType = KeyboardType.Number,
            modifier = Modifier.weight(1f),
        )
    }
    // An action only once there is a birth year to estimate from (T11.5a); before that, what it needs.
    if (state.canEstimateHrMax) {
        LinkText(stringResource(R.string.profile_estimate_hr_max), onClick = { onEvent(ProfileUiEvent.EstimateHrMax) })
    } else {
        Text(
            text = stringResource(R.string.profile_estimate_hr_max_needs_year),
            style = RideCompTheme.typography.caption,
            color = RideCompTheme.colors.textSecondary,
        )
    }
    ChoiceChips(
        label = stringResource(R.string.profile_sex),
        choices = listOf<Choice<Sex?>>(
            Choice(null, stringResource(R.string.profile_sex_unset)),
            Choice(Sex.MALE, stringResource(R.string.profile_sex_male)),
            Choice(Sex.FEMALE, stringResource(R.string.profile_sex_female)),
        ).toImmutableList(),
        selected = state.sex,
        onSelect = { onEvent(ProfileUiEvent.SexChanged(it)) },
        horizontalPadding = RideCompDimens.none,
        columns = SEX_COLUMNS,
    )
}

private const val SEX_COLUMNS = 3

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
