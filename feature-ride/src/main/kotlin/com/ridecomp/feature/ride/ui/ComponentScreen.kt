package com.ridecomp.feature.ride.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ridecomp.core.config.Units
import com.ridecomp.core.model.BikeComponent
import com.ridecomp.core.model.ComponentType
import com.ridecomp.core.model.ComponentWear
import com.ridecomp.core.usecase.ComponentField
import com.ridecomp.core.usecase.ComponentLimits
import com.ridecomp.feature.ride.R
import com.ridecomp.feature.ride.model.displayName
import com.ridecomp.feature.ride.model.distanceText
import com.ridecomp.feature.ride.model.labelRes
import com.ridecomp.feature.ride.viewmodel.ComponentEditorEffect
import com.ridecomp.feature.ride.viewmodel.ComponentEditorUiEvent
import com.ridecomp.feature.ride.viewmodel.ComponentEditorUiState
import com.ridecomp.feature.ride.viewmodel.ComponentEditorViewModel
import com.ridecomp.feature.ride.viewmodel.ComponentTarget
import com.ridecomp.ui.component.BigButton
import com.ridecomp.ui.component.BigButtonSize
import com.ridecomp.ui.component.BigButtonStyle
import com.ridecomp.ui.component.Choice
import com.ridecomp.ui.component.ChoiceChips
import com.ridecomp.ui.component.ConfirmDialog
import com.ridecomp.ui.component.RideCompCard
import com.ridecomp.ui.component.RideCompTextField
import com.ridecomp.ui.component.ScreenScaffold
import com.ridecomp.ui.component.SectionHeader
import com.ridecomp.ui.preview.RideCompPreview
import com.ridecomp.ui.preview.ThemePreviews
import com.ridecomp.ui.theme.RideCompDimens
import com.ridecomp.ui.theme.RideCompTheme
import com.ridecomp.ui.udf.ObserveEffects
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

/** Parts of a bike with their distance and limit (T7.3); shown in the bike editor. */
@Composable
fun ComponentsSection(components: ImmutableList<ComponentWear>, bikeDistanceM: Double, onOpen: (Long?) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(RideCompDimens.spacingSm)) {
        SectionHeader(stringResource(R.string.components_title), horizontalPadding = RideCompDimens.none)
        Text(
            text = stringResource(R.string.components_intro, (bikeDistanceM / Units.METERS_PER_KM).toLong()),
            style = RideCompTheme.typography.caption,
            color = RideCompTheme.colors.textSecondary,
        )
        components.forEach { wear -> ComponentRow(wear, onClick = { onOpen(wear.component.id) }) }
        BigButton(
            text = stringResource(R.string.components_add),
            onClick = { onOpen(null) },
            style = BigButtonStyle.SECONDARY,
            size = BigButtonSize.SECONDARY,
        )
    }
}

/** Name, "3.120 / 3.000 km" and a bar that turns orange when the part is due. */
@Composable
private fun ComponentRow(wear: ComponentWear, onClick: () -> Unit) {
    val colors = RideCompTheme.colors
    val barColor = if (wear.isDue) colors.accent else colors.ok
    RideCompCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = wear.component.displayName(),
                style = RideCompTheme.typography.bodyStrong,
                color = colors.textPrimary,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = wear.distanceText(),
                style = RideCompTheme.typography.caption,
                color = if (wear.isDue) colors.accent else colors.textSecondary,
            )
        }
        wear.fraction?.let { fraction ->
            Box(
                modifier = Modifier
                    .padding(top = RideCompDimens.spacingS)
                    .fillMaxWidth()
                    .height(RideCompDimens.progressBarHeight)
                    .clip(RoundedCornerShape(RideCompDimens.progressBarHeight))
                    .background(colors.divider),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction)
                        .height(RideCompDimens.progressBarHeight)
                        .background(barColor),
                )
            }
        }
        if (wear.isDue) {
            Text(
                text = stringResource(R.string.component_due),
                style = RideCompTheme.typography.caption,
                color = colors.accent,
                modifier = Modifier.padding(top = RideCompDimens.spacingS),
            )
        }
    }
}

/** Adds or edits a part (T7.3). */
@Composable
fun ComponentEditorRoute(
    target: ComponentTarget,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ComponentEditorViewModel = koinViewModel(key = target.toString()) { parametersOf(target) },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ObserveEffects(viewModel.effects) { effect ->
        when (effect) {
            ComponentEditorEffect.Done -> onDone()
        }
    }
    ScreenScaffold(
        modifier = modifier,
        title = stringResource(if (state.isNew) R.string.component_add_title else R.string.component_edit_title),
        onBack = onDone,
        backLabel = stringResource(R.string.bikes_back),
    ) {
        ComponentForm(state, viewModel::onEvent)
    }
}

@Composable
fun ComponentForm(state: ComponentEditorUiState, onEvent: (ComponentEditorUiEvent) -> Unit) {
    val error = stringResource(R.string.bike_error_invalid)
    Column(
        modifier = Modifier
            .verticalScroll(rememberScrollState())
            .padding(
                start = RideCompDimens.screenPaddingHorizontal,
                end = RideCompDimens.screenPaddingHorizontal,
                bottom = RideCompDimens.screenPaddingBottom,
            ),
        verticalArrangement = Arrangement.spacedBy(RideCompDimens.blockGap),
    ) {
        ChoiceChips(
            label = stringResource(R.string.component_type),
            choices = ComponentType.entries.map { Choice(it, stringResource(it.labelRes())) }.toImmutableList(),
            selected = state.type,
            onSelect = { onEvent(ComponentEditorUiEvent.TypePicked(it)) },
            horizontalPadding = RideCompDimens.none,
            columns = TYPE_COLUMNS,
        )
        RideCompTextField(
            label = stringResource(R.string.component_name),
            value = state.name,
            onValueChange = { onEvent(ComponentEditorUiEvent.NameChanged(it.take(ComponentLimits.NAME_MAX_LENGTH))) },
        )
        RideCompTextField(
            label = stringResource(R.string.component_limit),
            value = state.limitKm,
            onValueChange = { onEvent(ComponentEditorUiEvent.LimitChanged(it)) },
            suffix = stringResource(R.string.unit_km),
            error = error.takeIf { ComponentField.LIMIT in state.invalidFields },
            keyboardType = KeyboardType.Decimal,
        )
        if (state.isNew) {
            RideCompTextField(
                label = stringResource(R.string.component_initial),
                value = state.initialKm,
                onValueChange = { onEvent(ComponentEditorUiEvent.InitialChanged(it)) },
                suffix = stringResource(R.string.unit_km),
                error = error.takeIf { ComponentField.INITIAL_DISTANCE in state.invalidFields },
                keyboardType = KeyboardType.Decimal,
            )
        }
        Text(
            text = stringResource(R.string.component_hint),
            style = RideCompTheme.typography.caption,
            color = RideCompTheme.colors.textSecondary,
        )
        BigButton(
            text = stringResource(R.string.bike_save),
            onClick = { onEvent(ComponentEditorUiEvent.Save) },
            isEnabled = state.isLoaded,
            size = BigButtonSize.SECONDARY,
        )
        if (!state.isNew) ExistingPartActions(state, onEvent)
    }
    if (state.isDeleteAsked) {
        ConfirmDialog(
            title = stringResource(R.string.component_delete_title),
            message = stringResource(R.string.component_delete_message),
            confirmLabel = stringResource(R.string.component_delete),
            dismissLabel = stringResource(R.string.data_pages_cancel),
            onConfirm = { onEvent(ComponentEditorUiEvent.ConfirmDelete) },
            onDismiss = { onEvent(ComponentEditorUiEvent.DismissDelete) },
        )
    }
}

/** "Just replaced" restarts the count at 0 km; delete removes the part. */
@Composable
private fun ExistingPartActions(state: ComponentEditorUiState, onEvent: (ComponentEditorUiEvent) -> Unit) {
    state.distanceM?.let { distance ->
        Text(
            text = stringResource(R.string.component_done, (distance / Units.METERS_PER_KM).toLong()),
            style = RideCompTheme.typography.bodyStrong,
            color = RideCompTheme.colors.textPrimary,
        )
    }
    BigButton(
        text = stringResource(R.string.component_replaced),
        onClick = { onEvent(ComponentEditorUiEvent.Replaced) },
        style = BigButtonStyle.SECONDARY,
        size = BigButtonSize.SECONDARY,
    )
    BigButton(
        text = stringResource(R.string.component_delete),
        onClick = { onEvent(ComponentEditorUiEvent.AskDelete) },
        style = BigButtonStyle.TEXT,
    )
}

/** Six part types in rows of three. */
private const val TYPE_COLUMNS = 3

@ThemePreviews
@Composable
private fun ComponentsSectionPreview() {
    val chain = BikeComponent(id = 1, bikeId = 1, type = ComponentType.CHAIN, installedAtMs = 0)
    val tires = BikeComponent(id = 2, bikeId = 1, type = ComponentType.TIRES, name = "GP5000", installedAtMs = 0)
    RideCompPreview {
        ComponentsSection(
            components = persistentListOf(ComponentWear(chain, 3_120_000.0), ComponentWear(tires, 1_250_000.0)),
            bikeDistanceM = 4_200_000.0,
            onOpen = {},
        )
    }
}

@ThemePreviews
@Composable
private fun ComponentFormPreview() {
    RideCompPreview { ComponentForm(ComponentEditorUiState(isLoaded = true), onEvent = {}) }
}
