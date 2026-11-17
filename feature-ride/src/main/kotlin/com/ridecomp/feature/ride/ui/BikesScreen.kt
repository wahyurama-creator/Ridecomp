package com.ridecomp.feature.ride.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ridecomp.core.config.ProfileDefaults
import com.ridecomp.core.model.ActivityProfile
import com.ridecomp.core.model.Bike
import com.ridecomp.core.model.BuiltInProfiles
import com.ridecomp.core.model.ComponentWear
import com.ridecomp.core.usecase.BikeField
import com.ridecomp.core.usecase.BikeLimits
import com.ridecomp.feature.ride.R
import com.ridecomp.feature.ride.model.bikeSummary
import com.ridecomp.feature.ride.model.displayName
import com.ridecomp.feature.ride.viewmodel.BikeEditorEffect
import com.ridecomp.feature.ride.viewmodel.BikeEditorUiEvent
import com.ridecomp.feature.ride.viewmodel.BikeEditorUiState
import com.ridecomp.feature.ride.viewmodel.BikeEditorViewModel
import com.ridecomp.feature.ride.viewmodel.BikeTarget
import com.ridecomp.feature.ride.viewmodel.BikesUiEvent
import com.ridecomp.feature.ride.viewmodel.BikesUiState
import com.ridecomp.feature.ride.viewmodel.BikesViewModel
import com.ridecomp.ui.component.BigButton
import com.ridecomp.ui.component.BigButtonSize
import com.ridecomp.ui.component.BigButtonStyle
import com.ridecomp.ui.component.Choice
import com.ridecomp.ui.component.ChoiceChips
import com.ridecomp.ui.component.ConfirmDialog
import com.ridecomp.ui.component.LinkText
import com.ridecomp.ui.component.RideCompCard
import com.ridecomp.ui.component.RideCompTextField
import com.ridecomp.ui.component.ScreenScaffold
import com.ridecomp.ui.preview.RideCompPreview
import com.ridecomp.ui.preview.ThemePreviews
import com.ridecomp.ui.theme.RideCompDimens
import com.ridecomp.ui.theme.RideCompTheme
import com.ridecomp.ui.udf.ObserveEffects
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

/** "My bikes" (D-89). */
@Composable
fun BikesRoute(
    onOpen: (Long) -> Unit,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    viewModel: BikesViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    BikesScreen(
        state = state,
        onEvent = viewModel::onEvent,
        onOpen = onOpen,
        onAdd = onAdd,
        modifier = modifier,
        onBack = onBack,
    )
}

@Composable
fun BikesScreen(
    state: BikesUiState,
    onEvent: (BikesUiEvent) -> Unit,
    onOpen: (Long) -> Unit,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
) {
    ScreenScaffold(
        modifier = modifier,
        title = stringResource(R.string.bikes_title),
        onBack = onBack,
        backLabel = stringResource(R.string.bikes_back),
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(
                    start = RideCompDimens.screenPaddingHorizontal,
                    end = RideCompDimens.screenPaddingHorizontal,
                    bottom = RideCompDimens.screenPaddingBottom,
                ),
            verticalArrangement = Arrangement.spacedBy(RideCompDimens.spacingSm),
        ) {
            Text(
                text = stringResource(R.string.bikes_intro),
                style = RideCompTheme.typography.body,
                color = RideCompTheme.colors.textSecondary,
            )
            state.bikes.forEach { bike ->
                BikeCard(
                    bike = bike,
                    summary = bikeSummary(bike, state.profiles.firstOrNull { it.id == bike.profileId }),
                    due = state.dueComponents.filter { it.component.bikeId == bike.id }.toImmutableList(),
                    isSelected = bike.id == state.selectedId,
                    onOpen = { onOpen(bike.id) },
                    onSelect = { onEvent(BikesUiEvent.Select(bike.id)) },
                )
            }
            if (state.isLoaded) {
                BigButton(
                    text = stringResource(R.string.bikes_add),
                    onClick = onAdd,
                    style = BigButtonStyle.SECONDARY,
                    size = BigButtonSize.SECONDARY,
                )
            }
        }
    }
}

/** One bike: tap to edit; the bike for the next ride is marked, the others can be picked here too. */
@Composable
private fun BikeCard(
    bike: Bike,
    summary: String,
    due: ImmutableList<ComponentWear>,
    isSelected: Boolean,
    onOpen: () -> Unit,
    onSelect: () -> Unit,
) {
    val colors = RideCompTheme.colors
    RideCompCard(onClick = onOpen) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(RideCompDimens.spacingSm),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = bike.displayName(), style = RideCompTheme.typography.bodyStrong, color = colors.textPrimary)
                Text(text = summary, style = RideCompTheme.typography.caption, color = colors.textSecondary)
                if (due.isNotEmpty()) DueComponentsText(due)
            }
            if (isSelected) {
                Text(
                    text = stringResource(R.string.bikes_in_use),
                    style = RideCompTheme.typography.caption,
                    color = colors.ok,
                )
            } else {
                LinkText(stringResource(R.string.bikes_use), onClick = onSelect)
            }
        }
    }
}

/** "⚠ Service due: Chain, Tyres" (T7.3). */
@Composable
fun DueComponentsText(due: List<ComponentWear>, modifier: Modifier = Modifier) {
    Text(
        text = stringResource(R.string.components_due_list, due.map { it.component.displayName() }.joinToString()),
        style = RideCompTheme.typography.caption,
        color = RideCompTheme.colors.accent,
        modifier = modifier,
    )
}

/** Adds or edits one bike, then goes back. */
@Composable
fun BikeEditorRoute(
    target: BikeTarget,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenComponent: (bikeId: Long, componentId: Long?) -> Unit = { _, _ -> },
    viewModel: BikeEditorViewModel = koinViewModel(key = target.toString()) { parametersOf(target) },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ObserveEffects(viewModel.effects) { effect ->
        when (effect) {
            BikeEditorEffect.Done -> onDone()
        }
    }
    ScreenScaffold(
        modifier = modifier,
        title = stringResource(if (state.isNew) R.string.bike_add_title else R.string.bike_edit_title),
        onBack = onDone,
        backLabel = stringResource(R.string.bikes_back),
    ) {
        BikeForm(
            state = state,
            onEvent = viewModel::onEvent,
            saveLabel = stringResource(R.string.bike_save),
            onOpenComponent = { componentId -> onOpenComponent(state.id, componentId) },
        )
    }
}

/**
 * The first bike during onboarding (D-89): the bike created with the app, filled in here. Shown by the
 * onboarding of :feature-settings through the app's navigation, which keeps the features independent.
 */
@Composable
fun OnboardingBikeForm(
    header: @Composable () -> Unit,
    onSaved: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: BikeEditorViewModel = koinViewModel(key = BikeTarget.First.toString()) {
        parametersOf(BikeTarget.First)
    },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ObserveEffects(viewModel.effects) { effect ->
        when (effect) {
            BikeEditorEffect.Done -> onSaved()
        }
    }
    BikeForm(
        state = state,
        onEvent = viewModel::onEvent,
        saveLabel = stringResource(R.string.bike_save_next),
        header = header,
        modifier = modifier,
    )
}

/** Stateless, scrollable bike form: name, type, weight, wheel size. */
@Composable
fun BikeForm(
    state: BikeEditorUiState,
    onEvent: (BikeEditorUiEvent) -> Unit,
    saveLabel: String,
    modifier: Modifier = Modifier,
    header: @Composable () -> Unit = {},
    /** Opens a part (null = new); null hides the parts, e.g. during onboarding (T7.3). */
    onOpenComponent: ((Long?) -> Unit)? = null,
) {
    val error = stringResource(R.string.bike_error_invalid)
    fun errorOf(field: BikeField) = error.takeIf { field in state.invalidFields }
    // The save button stays at the bottom: on a small phone it was below the tyre sizes (release test).
    Column(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(
                    start = RideCompDimens.screenPaddingHorizontal,
                    end = RideCompDimens.screenPaddingHorizontal,
                    bottom = RideCompDimens.blockGap,
                ),
            verticalArrangement = Arrangement.spacedBy(RideCompDimens.blockGap),
        ) {
            BikeFields(state, onEvent, header, onOpenComponent, ::errorOf)
        }
        BigButton(
            text = saveLabel,
            onClick = { onEvent(BikeEditorUiEvent.Save) },
            isEnabled = state.isLoaded && !state.isSaving,
            size = BigButtonSize.SECONDARY,
            modifier = Modifier.padding(
                start = RideCompDimens.screenPaddingHorizontal,
                end = RideCompDimens.screenPaddingHorizontal,
                bottom = RideCompDimens.screenPaddingBottom,
            ),
        )
    }
    if (state.isDeleteAsked) DeleteBikeDialog(state, onEvent)
}

/** Name, type, weight, wheel size; then the parts and delete for a saved bike. */
@Composable
private fun BikeFields(
    state: BikeEditorUiState,
    onEvent: (BikeEditorUiEvent) -> Unit,
    header: @Composable () -> Unit,
    onOpenComponent: ((Long?) -> Unit)?,
    errorOf: (BikeField) -> String?,
) {
    header()
    RideCompTextField(
        label = stringResource(R.string.bike_name),
        value = state.name,
        onValueChange = { onEvent(BikeEditorUiEvent.NameChanged(it.take(BikeLimits.NAME_MAX_LENGTH))) },
    )
    BikeTypeChoice(state, onEvent)
    Row(horizontalArrangement = Arrangement.spacedBy(RideCompDimens.spacingSm)) {
        RideCompTextField(
            label = stringResource(R.string.bike_weight),
            value = state.weightKg,
            onValueChange = { onEvent(BikeEditorUiEvent.FieldChanged(BikeField.WEIGHT, it)) },
            suffix = stringResource(R.string.unit_kg),
            error = errorOf(BikeField.WEIGHT),
            keyboardType = KeyboardType.Decimal,
            modifier = Modifier.weight(1f),
        )
        RideCompTextField(
            label = stringResource(R.string.bike_wheel),
            value = state.wheelCircumferenceMm,
            onValueChange = { onEvent(BikeEditorUiEvent.FieldChanged(BikeField.WHEEL_CIRCUMFERENCE, it)) },
            suffix = stringResource(R.string.unit_mm),
            error = errorOf(BikeField.WHEEL_CIRCUMFERENCE),
            keyboardType = KeyboardType.Number,
            modifier = Modifier.weight(1f),
        )
    }
    ChoiceChips(
        label = stringResource(R.string.bike_wheel_common),
        choices = ProfileDefaults.COMMON_WHEELS_MM.map { (label, mm) -> Choice<Int?>(mm, label) }.toImmutableList(),
        selected = state.wheelCircumferenceMm.trim().toIntOrNull(),
        onSelect = { mm -> mm?.let { onEvent(BikeEditorUiEvent.WheelPicked(it)) } },
        horizontalPadding = RideCompDimens.none,
        columns = WHEEL_COLUMNS,
    )
    if (onOpenComponent != null && !state.isNew && state.isLoaded) {
        ComponentsSection(state.components, state.bikeDistanceM, onOpenComponent)
    }
    if (state.canDelete) {
        BigButton(
            text = stringResource(R.string.bike_delete),
            onClick = { onEvent(BikeEditorUiEvent.AskDelete) },
            style = BigButtonStyle.TEXT,
        )
    }
}

@Composable
private fun DeleteBikeDialog(state: BikeEditorUiState, onEvent: (BikeEditorUiEvent) -> Unit) {
    val name = state.name.ifBlank { stringResource(R.string.bike_default_name) }
    ConfirmDialog(
        title = stringResource(R.string.bike_delete_title, name),
        message = stringResource(R.string.bike_delete_message),
        confirmLabel = stringResource(R.string.bike_delete),
        dismissLabel = stringResource(R.string.data_pages_cancel),
        onConfirm = { onEvent(BikeEditorUiEvent.ConfirmDelete) },
        onDismiss = { onEvent(BikeEditorUiEvent.DismissDelete) },
    )
}

/** The type is an activity profile: it decides data pages, GPS and the FIT sport (D-89). */
@Composable
private fun BikeTypeChoice(state: BikeEditorUiState, onEvent: (BikeEditorUiEvent) -> Unit) {
    val selected = state.profileId ?: return
    Column(verticalArrangement = Arrangement.spacedBy(RideCompDimens.spacingS)) {
        ChoiceChips(
            label = stringResource(R.string.bike_type),
            choices = state.profiles.map { Choice(it.id, it.displayName()) }.toImmutableList(),
            selected = selected,
            onSelect = { onEvent(BikeEditorUiEvent.TypePicked(it)) },
            horizontalPadding = RideCompDimens.none,
            columns = TYPE_COLUMNS,
        )
        Text(
            text = stringResource(R.string.bike_type_hint),
            style = RideCompTheme.typography.caption,
            color = RideCompTheme.colors.textSecondary,
        )
    }
}

/** Common wheel sizes in rows of three, like the profile form. */
private const val WHEEL_COLUMNS = 3

/** Bike types in rows of three: Road, Gravel, MTB / Indoor, Commute, custom. */
private const val TYPE_COLUMNS = 3

private val previewProfiles: List<ActivityProfile> =
    BuiltInProfiles.ALL.mapIndexed { index, profile -> profile.copy(id = index + 1L) }

@ThemePreviews
@Composable
private fun BikesPreview() {
    RideCompPreview {
        BikesScreen(
            state = BikesUiState(
                bikes = listOf(
                    Bike(id = 1, profileId = 1),
                    Bike(id = 2, name = "Polygon Xtrada", profileId = 3, weightKg = 13.5, wheelCircumferenceMm = 2_273),
                ).toImmutableList(),
                profiles = previewProfiles.toImmutableList(),
                selectedId = 1,
                isLoaded = true,
            ),
            onEvent = {},
            onOpen = {},
            onAdd = {},
        )
    }
}

@ThemePreviews
@Composable
private fun BikeFormPreview() {
    RideCompPreview {
        BikeForm(
            state = BikeEditorUiState(
                id = 2,
                name = "Polygon Xtrada",
                profileId = 3,
                weightKg = "13.5",
                wheelCircumferenceMm = "2273",
                profiles = previewProfiles.toImmutableList(),
                canDelete = true,
                isLoaded = true,
            ),
            onEvent = {},
            saveLabel = "Save",
        )
    }
}
