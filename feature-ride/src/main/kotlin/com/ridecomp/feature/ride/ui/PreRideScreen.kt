package com.ridecomp.feature.ride.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ridecomp.core.config.GpsConfig
import com.ridecomp.core.engine.recording.BatteryStatus
import com.ridecomp.core.engine.sensors.SavedSensor
import com.ridecomp.core.engine.sensors.SensorKind
import com.ridecomp.core.model.Bike
import com.ridecomp.core.model.BuiltInProfiles
import com.ridecomp.core.model.ScreenMode
import com.ridecomp.core.usecase.GpsStatus
import com.ridecomp.core.util.UnitFormatter
import com.ridecomp.feature.ride.R
import com.ridecomp.feature.ride.model.bikeSummary
import com.ridecomp.feature.ride.model.displayName
import com.ridecomp.feature.ride.model.summary
import com.ridecomp.feature.ride.viewmodel.PreRideEffect
import com.ridecomp.feature.ride.viewmodel.PreRideUiEvent
import com.ridecomp.feature.ride.viewmodel.PreRideUiState
import com.ridecomp.feature.ride.viewmodel.PreRideViewModel
import com.ridecomp.ui.R as UiR
import com.ridecomp.ui.component.BigButton
import com.ridecomp.ui.component.BigButtonSize
import com.ridecomp.ui.component.BigButtonStyle
import com.ridecomp.ui.component.Choice
import com.ridecomp.ui.component.ChoiceChips
import com.ridecomp.ui.component.LinkText
import com.ridecomp.ui.component.ListCard
import com.ridecomp.ui.component.ListCardRow
import com.ridecomp.ui.component.RideCompCard
import com.ridecomp.ui.component.ScreenScaffold
import com.ridecomp.ui.component.SectionHeader
import com.ridecomp.ui.component.SwitchRow
import com.ridecomp.ui.component.TopBarIcon
import com.ridecomp.ui.preview.RideCompPreview
import com.ridecomp.ui.preview.ThemePreviews
import com.ridecomp.ui.theme.RideCompDimens
import com.ridecomp.ui.theme.RideCompTheme
import com.ridecomp.ui.udf.ObserveEffects
import kotlin.math.roundToInt
import kotlinx.collections.immutable.toImmutableList
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

/** Pre-ride check (02-DESIGN §3.3, 13-UI-SCREENS #5, #29): checks, bike, route, screen mode, MULAI. */
@Composable
fun PreRideRoute(
    onStarted: () -> Unit,
    modifier: Modifier = Modifier,
    /** Route chosen in the route detail ("Ride this route"), or null. */
    routeId: Long? = null,
    onCancel: () -> Unit = {},
    onChooseRoute: () -> Unit = {},
    onManageBikes: () -> Unit = {},
    /** Profile skipped in onboarding: "Isi sekarang" opens it (T10.12). */
    onOpenProfile: () -> Unit = {},
    viewModel: PreRideViewModel = koinViewModel(key = routeId?.toString()) { parametersOf(routeId) },
) {
    val gps by viewModel.gpsStatus.collectAsStateWithLifecycle()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ObserveEffects(viewModel.effects) { effect ->
        when (effect) {
            PreRideEffect.OpenRide -> onStarted()
        }
    }
    PreRideScreen(
        gps = gps,
        state = state,
        onEvent = viewModel::onEvent,
        onCancel = onCancel,
        onChooseRoute = onChooseRoute,
        onManageBikes = onManageBikes,
        modifier = modifier,
        onOpenProfile = onOpenProfile,
    )
}

@Composable
fun PreRideScreen(
    gps: GpsStatus,
    state: PreRideUiState,
    onEvent: (PreRideUiEvent) -> Unit,
    modifier: Modifier = Modifier,
    onCancel: () -> Unit = {},
    onChooseRoute: () -> Unit = {},
    onManageBikes: () -> Unit = {},
    onOpenProfile: () -> Unit = {},
) {
    var isProfileHintClosed by rememberSaveable { mutableStateOf(false) }
    // While GPS searches, the waiting state is shown first (#29); "Pilihan ride" opens the check list.
    var isChecklistShown by rememberSaveable { mutableStateOf(false) }
    val onStart = { onEvent(PreRideUiEvent.Start) }
    ScreenScaffold(
        modifier = modifier,
        title = stringResource(R.string.pre_ride_title),
        onBack = onCancel,
        backLabel = stringResource(R.string.ride_cancel),
        backIcon = TopBarIcon.CLOSE,
    ) {
        // The start button stays on screen; only the options above it scroll (novice test, D-86).
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = RideCompDimens.screenPaddingHorizontal),
        ) {
            if (state.isProfileMissing && !isProfileHintClosed) {
                ProfileHint(onOpenProfile = onOpenProfile, onClose = { isProfileHintClosed = true })
            }
            if (gps is GpsStatus.Searching && !isChecklistShown) {
                GpsWaiting(gps, onShowOptions = { isChecklistShown = true })
            } else {
                Checklist(gps, state, onEvent, onChooseRoute, onManageBikes)
            }
        }
        Box(
            modifier = Modifier.padding(
                start = RideCompDimens.screenPaddingHorizontal,
                end = RideCompDimens.screenPaddingHorizontal,
                bottom = RideCompDimens.screenPaddingBottom,
            ),
        ) {
            StartButtons(gps, onStart)
        }
    }
}

/**
 * Profile skipped in onboarding (T10.12): a quiet dark card in the app's style (accent icon, "Isi sekarang"
 * link, close button) so an optional reminder never outshouts the ride checks. Defaults are used meanwhile.
 */
@Composable
private fun ProfileHint(onOpenProfile: () -> Unit, onClose: () -> Unit) {
    val colors = RideCompTheme.colors
    val closeLabel = stringResource(R.string.pre_ride_profile_later)
    RideCompCard(modifier = Modifier.padding(bottom = RideCompDimens.blockGap)) {
        Row(horizontalArrangement = Arrangement.spacedBy(RideCompDimens.spacingSm)) {
            Box(
                modifier = Modifier
                    .size(RideCompDimens.iconTileSize)
                    .clip(CircleShape)
                    .background(colors.accentTint),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(UiR.drawable.ic_heart),
                    contentDescription = null,
                    tint = colors.accent,
                    modifier = Modifier.size(RideCompDimens.iconSize),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.pre_ride_profile_title),
                    style = RideCompTheme.typography.cardTitle,
                    color = colors.textPrimary,
                )
                Text(
                    text = stringResource(R.string.pre_ride_profile_text),
                    style = RideCompTheme.typography.caption,
                    color = colors.textSecondary,
                )
                LinkText(text = stringResource(R.string.pre_ride_profile_fill), onClick = onOpenProfile)
            }
            Box(
                modifier = Modifier
                    .size(RideCompDimens.touchTargetMin)
                    .clickable(role = Role.Button, onClick = onClose)
                    .semantics { contentDescription = closeLabel },
                contentAlignment = Alignment.TopEnd,
            ) {
                Icon(
                    painter = painterResource(UiR.drawable.ic_close),
                    contentDescription = null,
                    tint = colors.textSecondary,
                    modifier = Modifier.size(RideCompDimens.iconSize),
                )
            }
        }
    }
}

@Composable
private fun Checklist(
    gps: GpsStatus,
    state: PreRideUiState,
    onEvent: (PreRideUiEvent) -> Unit,
    onChooseRoute: () -> Unit,
    onManageBikes: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(RideCompDimens.blockGap)) {
        ListCard {
            CheckRow(gpsCheck(gps))
            SensorKind.entries.forEach { kind -> CheckRow(sensorCheck(state.sensors, kind)) }
            state.battery?.let { CheckRow(batteryCheck(it), hasDivider = false) }
        }
        BikePicker(state, onEvent, onManageBikes)
        state.session?.let { session ->
            SwitchRow(
                label = stringResource(R.string.pre_ride_session) + ": " + session.summary(),
                description = stringResource(R.string.pre_ride_session_description),
                isChecked = state.isSessionOn,
                onCheckedChange = { onEvent(PreRideUiEvent.SessionOn(it)) },
            )
        }
        RoutePicker(state, onEvent, onChooseRoute)
        ScreenModePicker(state, onEvent)
    }
}

/** One check list line: state icon, name, status in the state color. */
private data class Check(val title: String, val status: String, val level: CheckLevel)

/** NEUTRAL: optional and not used (no sensor): nothing is wrong, so no warning sign. */
private enum class CheckLevel { OK, NEUTRAL, WARN, ERROR }

@Composable
private fun CheckRow(check: Check, hasDivider: Boolean = true) {
    val color = check.level.color()
    ListCardRow(
        title = check.title,
        hasDivider = hasDivider,
        trailing = { Text(text = check.status, style = RideCompTheme.typography.bodyStrong, color = color) },
        leading = {
            Icon(
                painterResource(
                    when (check.level) {
                        CheckLevel.OK -> UiR.drawable.ic_check
                        CheckLevel.NEUTRAL -> UiR.drawable.ic_info
                        CheckLevel.WARN, CheckLevel.ERROR -> UiR.drawable.ic_alert_circle
                    },
                ),
                contentDescription = null,
                tint = color,
            )
        },
    )
}

@Composable
private fun CheckLevel.color(): Color = when (this) {
    CheckLevel.OK -> RideCompTheme.colors.ok
    CheckLevel.NEUTRAL -> RideCompTheme.colors.textSecondary
    CheckLevel.WARN -> RideCompTheme.colors.warnText
    CheckLevel.ERROR -> RideCompTheme.colors.danger
}

@Composable
private fun gpsCheck(gps: GpsStatus): Check {
    val title = stringResource(R.string.pre_ride_check_gps)
    return when (gps) {
        GpsStatus.NoPermission -> Check(title, stringResource(R.string.pre_ride_gps_no_permission), CheckLevel.ERROR)
        GpsStatus.Disabled -> Check(title, stringResource(R.string.pre_ride_gps_disabled), CheckLevel.ERROR)
        is GpsStatus.Searching -> Check(title, stringResource(R.string.pre_ride_gps_searching), CheckLevel.WARN)
        GpsStatus.NotUsed -> Check(title, stringResource(R.string.pre_ride_gps_not_used), CheckLevel.OK)
        is GpsStatus.Ready -> Check(
            title,
            gps.accuracyM?.let { stringResource(R.string.pre_ride_gps_ready, it.roundToInt().toString()) }
                ?: stringResource(R.string.pre_ride_gps_ready_unknown),
            CheckLevel.OK,
        )
    }
}

/** Saved sensors connect when the ride starts; here they show as saved, with their last battery. */
@Composable
private fun sensorCheck(sensors: List<SavedSensor>, kind: SensorKind): Check {
    val sensor = sensors.firstOrNull { kind in it.kinds }
    val title = stringResource(
        when (kind) {
            SensorKind.HEART_RATE -> R.string.pre_ride_check_hr
            SensorKind.SPEED_CADENCE -> R.string.pre_ride_check_csc
            SensorKind.POWER -> R.string.pre_ride_check_power
        },
    )
    return when {
        sensor != null -> Check(
            title,
            sensor.lastBatteryPercent?.let { stringResource(R.string.pre_ride_sensor_saved_battery, it) }
                ?: stringResource(R.string.pre_ride_sensor_saved),
            CheckLevel.OK,
        )
        kind == SensorKind.POWER -> Check(title, stringResource(R.string.pre_ride_power_estimate), CheckLevel.NEUTRAL)
        else -> Check(title, stringResource(R.string.pre_ride_sensor_none), CheckLevel.NEUTRAL)
    }
}

@Composable
private fun batteryCheck(battery: BatteryStatus): Check = Check(
    stringResource(R.string.pre_ride_check_battery),
    stringResource(R.string.pre_ride_battery_value, battery.percent),
    if (battery.percent > LOW_BATTERY_PERCENT) CheckLevel.OK else CheckLevel.WARN,
)

/** Bikes in rows of two, so names fit (D-89). */
private const val BIKE_COLUMNS = 2

private val MAX_ACCURACY_M = GpsConfig.MAX_ACCURACY_M.roundToInt()

/** Below this the ride will soon switch to the battery saver (12-FEATURE-SPECS §3). */
private const val LOW_BATTERY_PERCENT = 20

/** The saved bikes (D-89): pick one; its type, weight and wheel size are shown and used for the ride. */
@Composable
private fun BikePicker(state: PreRideUiState, onEvent: (PreRideUiEvent) -> Unit, onManageBikes: () -> Unit) {
    val bike = state.bike ?: return
    Column(verticalArrangement = Arrangement.spacedBy(RideCompDimens.spacingS)) {
        ChoiceChips(
            label = stringResource(R.string.pre_ride_bike),
            choices = state.bikes.map { Choice(it.id, it.displayName()) }.toImmutableList(),
            selected = bike.id,
            onSelect = { onEvent(PreRideUiEvent.SelectBike(it)) },
            horizontalPadding = RideCompDimens.none,
            columns = BIKE_COLUMNS.coerceAtMost(state.bikes.size),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = bikeSummary(bike, state.selected),
                style = RideCompTheme.typography.caption,
                color = RideCompTheme.colors.textSecondary,
                modifier = Modifier.weight(1f),
            )
            LinkText(stringResource(R.string.pre_ride_manage_bikes), onClick = onManageBikes)
        }
        if (state.dueComponents.isNotEmpty()) DueComponentsText(state.dueComponents)
    }
}

@Composable
private fun RoutePicker(state: PreRideUiState, onEvent: (PreRideUiEvent) -> Unit, onChooseRoute: () -> Unit) {
    val route = state.route
    Column(verticalArrangement = Arrangement.spacedBy(RideCompDimens.spacingS)) {
        SectionHeader(stringResource(R.string.pre_ride_route_label), horizontalPadding = RideCompDimens.none)
        RideCompCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val format = UnitFormatter(state.settings.unitSystem)
                Text(
                    text = route?.let {
                        stringResource(
                            R.string.pre_ride_route_value,
                            it.name,
                            format.distance(it.distanceM),
                            format.distanceUnit,
                        )
                    } ?: stringResource(R.string.pre_ride_route_none),
                    style = RideCompTheme.typography.bodyStrong,
                    color = RideCompTheme.colors.textPrimary,
                    modifier = Modifier.weight(1f),
                )
                if (route != null) {
                    LinkText(stringResource(R.string.pre_ride_route_clear), onClick = {
                        onEvent(PreRideUiEvent.ClearRoute)
                    })
                } else {
                    LinkText(stringResource(R.string.pre_ride_route_choose), onClick = onChooseRoute)
                }
            }
        }
    }
}

@Composable
private fun ScreenModePicker(state: PreRideUiState, onEvent: (PreRideUiEvent) -> Unit) {
    ChoiceChips(
        label = stringResource(R.string.pre_ride_screen_mode),
        choices = listOf(
            Choice(ScreenMode.ALWAYS_ON, stringResource(R.string.pre_ride_screen_always_on)),
            Choice(ScreenMode.AUTO_DIM, stringResource(R.string.pre_ride_screen_auto_dim)),
            Choice(ScreenMode.OFF, stringResource(R.string.pre_ride_screen_off)),
        ).toImmutableList(),
        selected = state.screenMode,
        onSelect = { if (!state.isScreenModeFromProfile) onEvent(PreRideUiEvent.SelectScreenMode(it)) },
        horizontalPadding = RideCompDimens.none,
        columns = ScreenMode.entries.size,
    )
    if (state.isScreenModeFromProfile) {
        Text(
            text = stringResource(R.string.pre_ride_screen_mode_from_profile),
            style = RideCompTheme.typography.caption,
            color = RideCompTheme.colors.textSecondary,
        )
    }
}

/** Waiting for a GPS fix (13-UI-SCREENS #29): static indicator (no animation on low-end phones), tips. */
@Composable
private fun GpsWaiting(gps: GpsStatus.Searching, onShowOptions: () -> Unit) {
    val colors = RideCompTheme.colors
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(RideCompDimens.blockGap),
    ) {
        Box(
            modifier = Modifier
                .padding(top = RideCompDimens.spacingL)
                .size(RideCompTheme.dimens.gpsWaitOuter)
                .border(RideCompDimens.borderSelected, colors.indicatorInactive, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(RideCompTheme.dimens.gpsWaitInner)
                    .border(RideCompDimens.borderSelected, colors.warnText, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painterResource(UiR.drawable.ic_location),
                    contentDescription = null,
                    tint = colors.warnText,
                    modifier = Modifier.size(RideCompDimens.iconSizeLarge),
                )
            }
        }
        Text(
            text = stringResource(R.string.pre_ride_gps_waiting),
            style = RideCompTheme.typography.title,
            color = colors.textPrimary,
        )
        Text(
            text =
            gps.accuracyM?.let { stringResource(R.string.pre_ride_gps_accuracy, it.roundToInt(), MAX_ACCURACY_M) }
                ?: stringResource(R.string.pre_ride_gps_need, MAX_ACCURACY_M),
            style = RideCompTheme.typography.body,
            color = colors.textSecondary,
            textAlign = TextAlign.Center,
        )
        RideCompCard {
            Text(
                text = stringResource(R.string.pre_ride_gps_tips_title),
                style = RideCompTheme.typography.bodyStrong,
                color = colors.textPrimary,
            )
            Text(
                text = stringResource(R.string.pre_ride_gps_tips),
                style = RideCompTheme.typography.body,
                color = colors.textTertiary,
            )
        }
        LinkText(stringResource(R.string.pre_ride_show_options), onClick = onShowOptions)
    }
}

@Composable
private fun StartButtons(gps: GpsStatus, onStart: () -> Unit) {
    Column(
        modifier = Modifier.padding(top = RideCompDimens.blockGap),
        verticalArrangement = Arrangement.spacedBy(RideCompDimens.spacingS),
    ) {
        if (gps is GpsStatus.Ready || gps == GpsStatus.NotUsed) {
            BigButton(text = stringResource(R.string.pre_ride_start), onClick = onStart, size = BigButtonSize.HERO)
        } else {
            // Recording starts now; position follows once the GPS has a fix (02-DESIGN §3.13).
            BigButton(
                text = stringResource(R.string.pre_ride_start_without_fix),
                onClick = onStart,
                style = BigButtonStyle.OUTLINE,
                isEnabled = gps != GpsStatus.NoPermission,
            )
            Text(
                text = stringResource(R.string.pre_ride_start_without_fix_hint),
                style = RideCompTheme.typography.caption,
                color = RideCompTheme.colors.textSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

private val previewProfiles = BuiltInProfiles.ALL.mapIndexed { index, profile -> profile.copy(id = index + 1L) }
private val previewBike = Bike(id = 1, profileId = previewProfiles.first().id)
private val previewState = PreRideUiState(
    battery = BatteryStatus(76, false),
    bikes = listOf(previewBike, Bike(id = 2, name = "Polygon Xtrada", profileId = 3)).toImmutableList(),
    bike = previewBike,
    profiles = previewProfiles.toImmutableList(),
    selected = previewProfiles.first(),
)

@ThemePreviews
@Composable
private fun PreRideSearchingPreview() {
    RideCompPreview { PreRideScreen(gps = GpsStatus.Searching(48.0), state = previewState, onEvent = {}) }
}

@ThemePreviews
@Composable
private fun PreRideReadyPreview() {
    RideCompPreview { PreRideScreen(gps = GpsStatus.Ready(4.0), state = previewState, onEvent = {}) }
}
