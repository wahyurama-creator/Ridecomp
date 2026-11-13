package com.ridecomp.feature.ride.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ridecomp.core.config.LapConfig
import com.ridecomp.core.config.Units
import com.ridecomp.core.model.ActivityProfile
import com.ridecomp.core.model.BuiltInProfiles
import com.ridecomp.core.model.RidingPosition
import com.ridecomp.core.model.ScreenMode
import com.ridecomp.core.model.Surface
import com.ridecomp.core.usecase.ProfileLimits
import com.ridecomp.feature.ride.R
import com.ridecomp.feature.ride.model.displayName
import com.ridecomp.feature.ride.model.labelRes
import com.ridecomp.feature.ride.viewmodel.ActivityProfileEditorUiEvent
import com.ridecomp.feature.ride.viewmodel.ActivityProfileEditorViewModel
import com.ridecomp.feature.ride.viewmodel.ActivityProfilesUiEvent
import com.ridecomp.feature.ride.viewmodel.ActivityProfilesUiState
import com.ridecomp.feature.ride.viewmodel.ActivityProfilesViewModel
import com.ridecomp.ui.component.BigButton
import com.ridecomp.ui.component.BigButtonStyle
import com.ridecomp.ui.component.Choice
import com.ridecomp.ui.component.ChoiceChips
import com.ridecomp.ui.component.ConfirmDialog
import com.ridecomp.ui.component.ListRow
import com.ridecomp.ui.component.ScreenScaffold
import com.ridecomp.ui.component.SwitchRow
import com.ridecomp.ui.preview.RideCompPreview
import com.ridecomp.ui.preview.ThemePreviews
import com.ridecomp.ui.theme.RideCompDimens
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

/** Activity profiles list (12-FEATURE-SPECS §4). */
@Composable
fun ActivityProfilesRoute(
    onOpen: (Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ActivityProfilesViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ActivityProfilesScreen(state = state, onEvent = viewModel::onEvent, onOpen = onOpen, modifier = modifier)
}

@Composable
fun ActivityProfilesScreen(
    state: ActivityProfilesUiState,
    onEvent: (ActivityProfilesUiEvent) -> Unit,
    onOpen: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    ScreenScaffold(modifier = modifier, title = stringResource(R.string.profiles_title)) {
        LazyColumn {
            items(state.profiles, key = { it.id }) { profile ->
                val name = profile.displayName()
                val copyName = stringResource(R.string.profiles_copy_name, name)
                ListRow(
                    title = name,
                    subtitle = stringResource(
                        if (profile.isGpsEnabled) R.string.profiles_gps else R.string.profiles_no_gps,
                    ),
                    onClick = { onOpen(profile.id) },
                    trailing = {
                        Row {
                            TextButton(onClick = { onEvent(ActivityProfilesUiEvent.Duplicate(profile.id, copyName)) }) {
                                Text(stringResource(R.string.profiles_duplicate))
                            }
                            if (!profile.isBuiltIn) {
                                TextButton(onClick = { onEvent(ActivityProfilesUiEvent.AskDelete(profile)) }) {
                                    Text(stringResource(R.string.profiles_delete))
                                }
                            }
                        }
                    },
                )
            }
        }
    }
    state.deleteCandidate?.let { profile ->
        ConfirmDialog(
            title = stringResource(R.string.profiles_delete_title, profile.displayName()),
            message = stringResource(R.string.profiles_delete_message),
            confirmLabel = stringResource(R.string.profiles_delete),
            dismissLabel = stringResource(R.string.data_pages_cancel),
            onConfirm = { onEvent(ActivityProfilesUiEvent.ConfirmDelete) },
            onDismiss = { onEvent(ActivityProfilesUiEvent.DismissDelete) },
        )
    }
}

/** Edits one profile; data pages open the page editor for this profile. */
@Composable
fun ActivityProfileEditorRoute(
    profileId: Long,
    onEditPages: (Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ActivityProfileEditorViewModel = koinViewModel(key = profileId.toString()) { parametersOf(profileId) },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    // Pages are saved by the page editor: show their state again when coming back.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.reload() }
    state.profile?.let {
        ActivityProfileEditorScreen(
            profile = it,
            onEvent = viewModel::onEvent,
            onEditPages = { onEditPages(profileId) },
            modifier = modifier,
        )
    }
}

@Composable
fun ActivityProfileEditorScreen(
    profile: ActivityProfile,
    onEvent: (ActivityProfileEditorUiEvent) -> Unit,
    onEditPages: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ScreenScaffold(modifier = modifier, title = profile.displayName()) {
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()).padding(bottom = RideCompDimens.spacingL),
            verticalArrangement = Arrangement.spacedBy(RideCompDimens.spacingM),
        ) {
            OutlinedTextField(
                value = profile.name,
                onValueChange = { onEvent(ActivityProfileEditorUiEvent.Name(it.take(ProfileLimits.NAME_MAX_LENGTH))) },
                label = { Text(stringResource(R.string.profile_name)) },
                placeholder = { Text(stringResource(profile.kind.labelRes())) },
                singleLine = true,
                modifier = Modifier.padding(horizontal = RideCompDimens.spacingM),
            )
            SwitchRow(
                label = stringResource(R.string.profile_gps),
                description = stringResource(R.string.profile_gps_description),
                isChecked = profile.isGpsEnabled,
                onCheckedChange = { onEvent(ActivityProfileEditorUiEvent.Gps(it)) },
            )
            RideOverrides(profile, onEvent)
            PowerEstimateChoices(profile, onEvent)
            DataPagesChoice(profile, onEvent, onEditPages)
        }
    }
}

@Composable
private fun RideOverrides(profile: ActivityProfile, onEvent: (ActivityProfileEditorUiEvent) -> Unit) {
    val general = stringResource(R.string.profile_general)
    val on = stringResource(R.string.profile_on)
    val off = stringResource(R.string.profile_off)
    ChoiceChips(
        label = stringResource(R.string.profile_auto_pause),
        choices = listOf<Choice<Boolean?>>(
            Choice(null, general),
            Choice(true, on),
            Choice(false, off),
        ).toImmutableList(),
        selected = profile.isAutoPauseEnabled,
        onSelect = { onEvent(ActivityProfileEditorUiEvent.AutoPause(it)) },
    )
    ChoiceChips(
        label = stringResource(R.string.profile_auto_lap),
        choices = (
            listOf<Choice<Double?>>(Choice(null, general), Choice(ActivityProfile.AUTO_LAP_OFF, off)) +
                LapConfig.AUTO_LAP_CHOICES_M.map {
                    Choice<Double?>(it, stringResource(R.string.profile_km, (it / Units.METERS_PER_KM).toInt()))
                }
            ).toImmutableList(),
        selected = profile.autoLapM,
        onSelect = { onEvent(ActivityProfileEditorUiEvent.AutoLap(it)) },
    )
    ChoiceChips(
        label = stringResource(R.string.profile_screen),
        choices = listOf<Choice<ScreenMode?>>(
            Choice(null, general),
            Choice(ScreenMode.ALWAYS_ON, stringResource(R.string.profile_screen_always_on)),
            Choice(ScreenMode.AUTO_DIM, stringResource(R.string.profile_screen_auto_dim)),
            Choice(ScreenMode.OFF, stringResource(R.string.profile_screen_off)),
        ).toImmutableList(),
        selected = profile.screenMode,
        onSelect = { onEvent(ActivityProfileEditorUiEvent.Screen(it)) },
    )
}

@Composable
private fun PowerEstimateChoices(profile: ActivityProfile, onEvent: (ActivityProfileEditorUiEvent) -> Unit) {
    ChoiceChips(
        label = stringResource(R.string.profile_surface),
        choices = listOf(
            Choice(Surface.ROAD, stringResource(R.string.profile_surface_road)),
            Choice(Surface.GRAVEL, stringResource(R.string.profile_surface_gravel)),
        ).toImmutableList(),
        selected = profile.surface,
        onSelect = { onEvent(ActivityProfileEditorUiEvent.SurfaceChanged(it)) },
    )
    ChoiceChips(
        label = stringResource(R.string.profile_position),
        choices = positionChoices(),
        selected = profile.ridingPosition,
        onSelect = { onEvent(ActivityProfileEditorUiEvent.Position(it)) },
    )
}

@Composable
private fun positionChoices(): ImmutableList<Choice<RidingPosition>> = listOf(
    Choice(RidingPosition.TOPS, stringResource(R.string.profile_position_tops)),
    Choice(RidingPosition.HOODS, stringResource(R.string.profile_position_hoods)),
    Choice(RidingPosition.DROPS, stringResource(R.string.profile_position_drops)),
).toImmutableList()

@Composable
private fun DataPagesChoice(
    profile: ActivityProfile,
    onEvent: (ActivityProfileEditorUiEvent) -> Unit,
    onEditPages: () -> Unit,
) {
    val hasOwnPages = profile.dataPages != null
    ListRow(
        title = stringResource(R.string.profile_data_pages),
        subtitle = stringResource(if (hasOwnPages) R.string.profile_pages_own else R.string.profile_pages_general),
        onClick = onEditPages,
    )
    if (hasOwnPages) {
        BigButton(
            text = stringResource(R.string.profile_pages_use_general),
            onClick = { onEvent(ActivityProfileEditorUiEvent.UseGeneralPages) },
            style = BigButtonStyle.SECONDARY,
            modifier = Modifier.padding(horizontal = RideCompDimens.spacingM),
        )
    }
}

private val previewProfiles = BuiltInProfiles.ALL.mapIndexed { index, profile -> profile.copy(id = index + 1L) }

@ThemePreviews
@Composable
private fun ActivityProfilesPreview() {
    RideCompPreview {
        ActivityProfilesScreen(
            state = ActivityProfilesUiState(previewProfiles.toImmutableList(), isLoaded = true),
            onEvent = {},
            onOpen = {},
        )
    }
}

@ThemePreviews
@Composable
private fun ActivityProfileEditorPreview() {
    RideCompPreview { ActivityProfileEditorScreen(profile = previewProfiles[2], onEvent = {}, onEditPages = {}) }
}
