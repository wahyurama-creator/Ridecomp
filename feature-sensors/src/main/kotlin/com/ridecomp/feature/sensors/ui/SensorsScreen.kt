package com.ridecomp.feature.sensors.ui

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ridecomp.core.engine.sensors.DiscoveredSensor
import com.ridecomp.core.engine.sensors.SavedSensor
import com.ridecomp.core.engine.sensors.SensorKind
import com.ridecomp.core.engine.sensors.isBatteryLow
import com.ridecomp.core.engine.sensors.isLikelyWatch
import com.ridecomp.core.usecase.ScanBlocker
import com.ridecomp.feature.sensors.R
import com.ridecomp.feature.sensors.viewmodel.SensorsUiEvent
import com.ridecomp.feature.sensors.viewmodel.SensorsUiState
import com.ridecomp.feature.sensors.viewmodel.SensorsViewModel
import com.ridecomp.ui.R as UiR
import com.ridecomp.ui.component.AlertBanner
import com.ridecomp.ui.component.AlertSeverity
import com.ridecomp.ui.component.BannerAction
import com.ridecomp.ui.component.BigButton
import com.ridecomp.ui.component.BigButtonSize
import com.ridecomp.ui.component.BigButtonStyle
import com.ridecomp.ui.component.ConfirmDialog
import com.ridecomp.ui.component.EmptyState
import com.ridecomp.ui.component.IconSquareButton
import com.ridecomp.ui.component.ListCard
import com.ridecomp.ui.component.ListCardRow
import com.ridecomp.ui.component.RideCompCard
import com.ridecomp.ui.component.ScreenScaffold
import com.ridecomp.ui.component.SectionHeader
import com.ridecomp.ui.component.SwitchRow
import com.ridecomp.ui.preview.RideCompPreview
import com.ridecomp.ui.preview.ThemePreviews
import com.ridecomp.ui.theme.RideCompDimens
import com.ridecomp.ui.theme.RideCompTheme
import kotlinx.collections.immutable.persistentListOf
import org.koin.androidx.compose.koinViewModel

/** Permissions a BLE scan needs on this Android version (03-ARCHITECTURE §2). */
private fun blePermissions(): Array<String> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
    arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
} else {
    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
}

/** Sensors entry point (UI-01, T2.1). The scan stops when the screen is no longer visible (BAT-04). */
@Composable
fun SensorsRoute(
    onOpenWatchGuide: () -> Unit,
    modifier: Modifier = Modifier,
    tipContent: (@Composable () -> Unit)? = null,
    viewModel: SensorsViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    // Either result re-checks the blocker and starts the scan when it is gone.
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        viewModel.onEvent(SensorsUiEvent.StartScan)
    }
    val enableLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        viewModel.onEvent(SensorsUiEvent.StartScan)
    }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.onEvent(SensorsUiEvent.StopScan) }
    val fallbackName = stringResource(R.string.sensors_unnamed)
    SensorsScreen(
        state = state,
        onEvent = viewModel::onEvent,
        onPair = { viewModel.onEvent(SensorsUiEvent.Pair(it, fallbackName)) },
        onFixBlocker = { blocker ->
            when (blocker) {
                ScanBlocker.NO_PERMISSION -> permissionLauncher.launch(blePermissions())
                ScanBlocker.BLUETOOTH_OFF -> enableLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
            }
        },
        onOpenWatchGuide = onOpenWatchGuide,
        modifier = modifier,
        tipContent = tipContent,
    )
}

/** Stateless Sensors screen (02-DESIGN §3.8, 13-UI-SCREENS #4). */
@Composable
fun SensorsScreen(
    state: SensorsUiState,
    onEvent: (SensorsUiEvent) -> Unit,
    onPair: (DiscoveredSensor) -> Unit,
    onFixBlocker: (ScanBlocker) -> Unit,
    onOpenWatchGuide: () -> Unit,
    modifier: Modifier = Modifier,
    tipContent: (@Composable () -> Unit)? = null,
) {
    ScreenScaffold(
        modifier = modifier,
        title = stringResource(R.string.sensors_title),
        banner = { SensorsBanner(state, onFixBlocker, onOpenWatchGuide) },
    ) {
        val isEmpty = state.saved.isEmpty() && state.discovered.isEmpty() && !state.isScanning
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = RideCompDimens.screenPaddingHorizontal),
            verticalArrangement = Arrangement.spacedBy(RideCompDimens.cardGap),
        ) {
            tipContent?.let { tip -> item(key = TIP_KEY) { tip() } }
            if (isEmpty) {
                item(key = EMPTY_KEY) { EmptyState(message = stringResource(R.string.sensors_empty)) }
            }
            savedSection(state, onForget = { onEvent(SensorsUiEvent.AskForget(it)) })
            item(key = WIDE_SCAN_KEY) {
                WideScanSwitch(state.isWideScan, onOpenWatchGuide) { onEvent(SensorsUiEvent.WideScan(it)) }
            }
            discoveredSection(state, onPair)
            item(key = INFO_KEY) { BleInfo() }
        }
        BigButton(
            text = stringResource(if (state.isScanning) R.string.sensors_stop_scan else R.string.sensors_scan_action),
            onClick = {
                val blocker = state.blocker
                when {
                    state.isScanning -> onEvent(SensorsUiEvent.StopScan)
                    // Straight to the system question instead of a banner first (novice test, D-86).
                    blocker != null -> onFixBlocker(blocker)
                    else -> onEvent(SensorsUiEvent.StartScan)
                }
            },
            style = BigButtonStyle.SECONDARY,
            size = BigButtonSize.TERTIARY,
            modifier = Modifier.padding(RideCompDimens.screenPaddingHorizontal),
        )
    }
    state.forgetCandidate?.let { sensor ->
        ConfirmDialog(
            title = stringResource(R.string.sensors_forget_title, sensor.name),
            message = stringResource(R.string.sensors_forget_message),
            confirmLabel = stringResource(R.string.sensors_forget),
            dismissLabel = stringResource(R.string.sensors_cancel),
            onConfirm = { onEvent(SensorsUiEvent.ConfirmForget) },
            onDismiss = { onEvent(SensorsUiEvent.DismissForget) },
        )
    }
}

@Composable
private fun SensorsBanner(state: SensorsUiState, onFixBlocker: (ScanBlocker) -> Unit, onOpenWatchGuide: () -> Unit) {
    val blocker = state.blocker
    // Nothing found, or a watch that does not share heart rate: point to the per-brand guide (T12.3).
    val watchGuide = listOf(BannerAction(stringResource(R.string.sensors_watch_guide_action), onOpenWatchGuide))
    when {
        blocker != null -> AlertBanner(
            message = stringResource(blocker.messageRes()),
            severity = AlertSeverity.WARN,
            iconRes = UiR.drawable.ic_bluetooth_off,
            actions = listOf(BannerAction(stringResource(blocker.actionRes())) { onFixBlocker(blocker) }),
        )
        state.probeFailure != null -> AlertBanner(
            message = stringResource(
                if (state.probeFailure.isUnreachable) {
                    R.string.sensors_probe_unreachable
                } else {
                    R.string.sensors_probe_none
                },
                state.probeFailure.name,
            ),
            severity = AlertSeverity.WARN,
            iconRes = UiR.drawable.ic_info,
            actions = if (state.probeFailure.isUnreachable) emptyList() else watchGuide,
        )
        state.hasScanFailed -> AlertBanner(
            message = stringResource(R.string.sensors_scan_failed),
            severity = AlertSeverity.WARN,
        )
        state.hasFoundNothing -> AlertBanner(
            message = stringResource(R.string.sensors_none_found),
            severity = AlertSeverity.INFO,
            iconRes = UiR.drawable.ic_info,
            actions = watchGuide,
        )
    }
}

private fun LazyListScope.savedSection(state: SensorsUiState, onForget: (SavedSensor) -> Unit) {
    if (state.saved.isEmpty()) return
    item(key = SAVED_HEADER_KEY) {
        SectionHeader(stringResource(R.string.sensors_saved), horizontalPadding = RideCompDimens.none)
    }
    // Saved sensors connect when a ride starts; scans leave them out, so no live status here (D-60).
    items(state.saved, key = { SAVED_KEY_PREFIX + it.address }) { sensor ->
        SensorCard(
            iconKind = sensor.kinds.firstOrNull(),
            // A GloryFit watch is a watch whatever its name (T12.5b).
            isWatch = sensor.isGloryFitWatch || isLikelyWatch(sensor.name),
            title = sensor.name,
            subtitle = savedSubtitle(sensor),
            isOutlined = false,
        ) {
            IconSquareButton(
                iconRes = UiR.drawable.ic_trash,
                contentDescription = stringResource(R.string.sensors_forget_label, sensor.name),
                onClick = { onForget(sensor) },
            )
        }
    }
}

private fun LazyListScope.discoveredSection(state: SensorsUiState, onPair: (DiscoveredSensor) -> Unit) {
    val found = state.discovered.filter { sensor -> state.saved.none { it.address == sensor.address } }
    if (found.isEmpty() && !state.isScanning) return
    item(key = FOUND_HEADER_KEY) { FoundHeader(state.isScanning) }
    items(found, key = { FOUND_KEY_PREFIX + it.address }) { sensor ->
        FoundSensorCard(sensor, probingAddress = state.probingAddress, onPair = onPair)
    }
}

@Composable
private fun FoundHeader(isScanning: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        SectionHeader(
            stringResource(R.string.sensors_found),
            horizontalPadding = RideCompDimens.none,
            modifier = Modifier.weight(1f),
        )
        if (isScanning) {
            Text(
                text = stringResource(R.string.sensors_scanning),
                style = RideCompTheme.typography.caption.copy(fontWeight = FontWeight.SemiBold),
                color = RideCompTheme.colors.accent,
            )
        }
    }
}

/** At or above this the sensor is near the phone ("sinyal kuat"). */
private const val STRONG_SIGNAL_DBM = -70

/** Sensor card: type icon, name, details, trailing status/actions (13-UI-SCREENS #4). */
@Composable
private fun SensorCard(
    iconKind: SensorKind?,
    isWatch: Boolean,
    title: String,
    subtitle: String,
    isOutlined: Boolean,
    trailing: @Composable () -> Unit,
) {
    val colors = RideCompTheme.colors
    val shape = RoundedCornerShape(RideCompDimens.cornerRadiusCard)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (isOutlined) colors.bg else colors.surface)
            .border(RideCompDimens.borderThin, if (isOutlined) colors.divider else colors.surface, shape)
            .padding(horizontal = RideCompDimens.cardPaddingHorizontal, vertical = RideCompDimens.cardPaddingVertical),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(RideCompDimens.spacingSm),
    ) {
        Icon(
            // A watch keeps the colour of what it measures, with a watch shape (T12.3).
            painterResource(if (isWatch) UiR.drawable.ic_watch else iconKind.iconRes()),
            contentDescription = null,
            tint = iconKind.tint(),
            modifier = Modifier.size(RideCompDimens.iconSize),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = RideCompTheme.typography.cardTitle, color = colors.textPrimary)
            Text(text = subtitle, style = RideCompTheme.typography.caption, color = colors.textSecondary)
        }
        trailing()
    }
}

/** "Only Bluetooth (BLE)…" note (13-UI-SCREENS #4). */
@Composable
private fun BleInfo() {
    RideCompCard(modifier = Modifier.padding(top = RideCompDimens.spacingS)) {
        Row(horizontalArrangement = Arrangement.spacedBy(RideCompDimens.cardGap)) {
            Icon(
                painterResource(UiR.drawable.ic_info),
                contentDescription = null,
                tint = RideCompTheme.colors.textSecondary,
            )
            Text(
                text = stringResource(R.string.sensors_ble_info),
                style = RideCompTheme.typography.caption,
                color = RideCompTheme.colors.textTertiary,
            )
        }
    }
}

private fun SensorKind?.iconRes(): Int = when (this) {
    SensorKind.HEART_RATE -> UiR.drawable.ic_heart
    SensorKind.SPEED_CADENCE -> UiR.drawable.ic_cadence
    SensorKind.POWER -> UiR.drawable.ic_power
    null -> UiR.drawable.ic_nav_sensors
}

@Composable
private fun SensorKind?.tint(): Color = when (this) {
    SensorKind.HEART_RATE -> RideCompTheme.colors.danger
    SensorKind.SPEED_CADENCE -> RideCompTheme.colors.track
    SensorKind.POWER -> RideCompTheme.colors.warnText
    null -> RideCompTheme.colors.textSecondary
}

@Composable
private fun savedSubtitle(sensor: SavedSensor): String {
    val kinds = watchLabel(isLikelyWatch(sensor.name), kindsLabel(sensor.kinds, sensor.isTrainer))
    val battery = sensor.lastBatteryPercent
    return when {
        // The watch is held by its own app unless that app is closed (one connection only, T12.5b).
        sensor.isGloryFitWatch -> stringResource(R.string.sensors_kind_gloryfit_watch)
        battery == null -> kinds
        else -> {
            val batteryRes = if (sensor.isBatteryLow) R.string.sensors_battery_low else R.string.sensors_battery
            stringResource(R.string.sensors_subtitle, kinds, stringResource(batteryRes, battery))
        }
    }
}

/** "Power · Speed & cadence", or "Smart trainer (ERG)" for an FTMS trainer (T9.1). */
@Composable
private fun kindsLabel(kinds: Set<SensorKind>, isTrainer: Boolean = false): String {
    if (isTrainer) return stringResource(R.string.sensors_kind_trainer)
    return SensorKind.entries.filter { it in kinds }.map { stringResource(it.labelRes()) }
        .ifEmpty { listOf(stringResource(R.string.sensors_kind_unknown)) }
        .joinToString(separator = stringResource(R.string.sensors_kind_separator))
}

/** "Watch · Heart rate" for a watch or band (T12.3), otherwise [kinds] unchanged. */
@Composable
private fun watchLabel(isWatch: Boolean, kinds: String): String = if (isWatch) {
    val separator = stringResource(R.string.sensors_kind_separator)
    stringResource(R.string.sensors_kind_watch) + separator + kinds
} else {
    kinds
}

/** A found device with "Pasangkan", or "Periksa" for one from the wide scan that is checked first (T12.2). */
@Composable
private fun FoundSensorCard(sensor: DiscoveredSensor, probingAddress: String?, onPair: (DiscoveredSensor) -> Unit) {
    val isStrong = sensor.rssiDbm >= STRONG_SIGNAL_DBM
    // A device from the wide scan does not say what it is: it is checked before it is saved (T12.2).
    val needsCheck = sensor.kinds.isEmpty()
    val isWatch = isLikelyWatch(sensor.name)
    val detected = if (needsCheck) {
        stringResource(R.string.sensors_kind_unchecked)
    } else {
        kindsLabel(sensor.kinds, sensor.isTrainer)
    }
    val kinds = watchLabel(isWatch, detected)
    SensorCard(
        iconKind = sensor.kinds.firstOrNull(),
        isWatch = isWatch,
        title = sensor.name ?: stringResource(
            if (needsCheck) R.string.sensors_unnamed_device else R.string.sensors_unnamed,
        ),
        subtitle = stringResource(
            R.string.sensors_subtitle,
            kinds,
            stringResource(if (isStrong) R.string.sensors_signal_strong else R.string.sensors_signal_weak),
        ),
        isOutlined = true,
    ) {
        BigButton(
            text = stringResource(
                when {
                    probingAddress == sensor.address -> R.string.sensors_checking
                    needsCheck -> R.string.sensors_check
                    else -> R.string.sensors_pair
                },
            ),
            onClick = { onPair(sensor) },
            isEnabled = probingAddress == null,
            // A strong signal is the likely sensor on your bike: filled button (13-UI-SCREENS #4).
            style = if (isStrong) BigButtonStyle.PRIMARY else BigButtonStyle.OUTLINE,
            size = BigButtonSize.TERTIARY,
            // As wide as its label ("Pasangkan" must not break mid-word), never tiny. BigButton fills
            // the width it gets, so the width is fixed to the label's here (device test of TS.1).
            modifier = Modifier
                .widthIn(min = RideCompDimens.actionButtonMinWidth)
                .width(IntrinsicSize.Max),
        )
    }
}

/** "Show all Bluetooth devices" (T12.2): for watches that share heart rate without advertising it. */
@Composable
private fun WideScanSwitch(isOn: Boolean, onOpenWatchGuide: () -> Unit, onChange: (Boolean) -> Unit) {
    ListCard {
        SwitchRow(
            label = stringResource(R.string.sensors_wide_scan),
            description = stringResource(R.string.sensors_wide_scan_hint),
            isChecked = isOn,
            onCheckedChange = onChange,
        )
        // Always here, at every rider level: which heart rate source works and how (T12.3).
        ListCardRow(
            title = stringResource(R.string.sensors_hr_guide),
            subtitle = stringResource(R.string.sensors_hr_guide_hint),
            onClick = onOpenWatchGuide,
            hasDivider = false,
            leading = {
                Icon(
                    painterResource(UiR.drawable.ic_help),
                    contentDescription = null,
                    tint = RideCompTheme.colors.textSecondary,
                    modifier = Modifier.size(RideCompDimens.iconSize),
                )
            },
        )
    }
}

@StringRes
private fun SensorKind.labelRes(): Int = when (this) {
    SensorKind.HEART_RATE -> R.string.sensors_kind_hr
    SensorKind.SPEED_CADENCE -> R.string.sensors_kind_csc
    SensorKind.POWER -> R.string.sensors_kind_power
}

@StringRes
private fun ScanBlocker.messageRes(): Int = when (this) {
    ScanBlocker.NO_PERMISSION -> R.string.sensors_blocker_permission
    ScanBlocker.BLUETOOTH_OFF -> R.string.sensors_blocker_bluetooth
}

@StringRes
private fun ScanBlocker.actionRes(): Int = when (this) {
    ScanBlocker.NO_PERMISSION -> R.string.sensors_blocker_permission_action
    ScanBlocker.BLUETOOTH_OFF -> R.string.sensors_blocker_bluetooth_action
}

private const val SAVED_HEADER_KEY = "saved_header"
private const val EMPTY_KEY = "empty"
private const val TIP_KEY = "tip"
private const val INFO_KEY = "info"
private const val WIDE_SCAN_KEY = "wide_scan"
private const val FOUND_HEADER_KEY = "found_header"
private const val SAVED_KEY_PREFIX = "saved_"
private const val FOUND_KEY_PREFIX = "found_"

private val previewSaved = SavedSensor("AA:BB", "HRM-Dual", setOf(SensorKind.HEART_RATE), lastBatteryPercent = 12)
private val previewFound = DiscoveredSensor("CC:DD", "KICKR", setOf(SensorKind.POWER, SensorKind.SPEED_CADENCE), -60)

@ThemePreviews
@Composable
private fun SensorsScreenPreview() {
    RideCompPreview {
        SensorsScreen(
            state = SensorsUiState(
                saved = persistentListOf(previewSaved),
                discovered = persistentListOf(previewFound),
                isScanning = true,
                isLoaded = true,
            ),
            onEvent = {},
            onPair = {},
            onFixBlocker = {},
            onOpenWatchGuide = {},
        )
    }
}

@ThemePreviews
@Composable
private fun SensorsBlockedPreview() {
    RideCompPreview {
        SensorsScreen(
            state = SensorsUiState(blocker = ScanBlocker.BLUETOOTH_OFF, isLoaded = true),
            onEvent = {},
            onPair = {},
            onFixBlocker = {},
            onOpenWatchGuide = {},
        )
    }
}
