package com.ridecomp.feature.sensors.ui

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ridecomp.core.engine.sensors.DiscoveredSensor
import com.ridecomp.core.engine.sensors.SavedSensor
import com.ridecomp.core.engine.sensors.SensorKind
import com.ridecomp.core.engine.sensors.isBatteryLow
import com.ridecomp.core.usecase.ScanBlocker
import com.ridecomp.feature.sensors.R
import com.ridecomp.feature.sensors.viewmodel.SensorsUiEvent
import com.ridecomp.feature.sensors.viewmodel.SensorsUiState
import com.ridecomp.feature.sensors.viewmodel.SensorsViewModel
import com.ridecomp.ui.component.AlertBanner
import com.ridecomp.ui.component.AlertSeverity
import com.ridecomp.ui.component.BannerAction
import com.ridecomp.ui.component.BigButton
import com.ridecomp.ui.component.BigButtonStyle
import com.ridecomp.ui.component.ConfirmDialog
import com.ridecomp.ui.component.EmptyState
import com.ridecomp.ui.component.ListRow
import com.ridecomp.ui.component.ScreenScaffold
import com.ridecomp.ui.component.SectionHeader
import com.ridecomp.ui.preview.RideCompPreview
import com.ridecomp.ui.preview.ThemePreviews
import com.ridecomp.ui.theme.RideCompDimens
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
fun SensorsRoute(modifier: Modifier = Modifier, viewModel: SensorsViewModel = koinViewModel()) {
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
        modifier = modifier,
    )
}

/** Stateless Sensors screen (02-DESIGN §3.8). */
@Composable
fun SensorsScreen(
    state: SensorsUiState,
    onEvent: (SensorsUiEvent) -> Unit,
    onPair: (DiscoveredSensor) -> Unit,
    onFixBlocker: (ScanBlocker) -> Unit,
    modifier: Modifier = Modifier,
) {
    ScreenScaffold(
        modifier = modifier,
        title = stringResource(R.string.sensors_title),
        banner = { SensorsBanner(state, onFixBlocker) },
    ) {
        val isEmpty = state.saved.isEmpty() && state.discovered.isEmpty() && !state.isScanning
        Box(modifier = Modifier.weight(1f)) {
            if (isEmpty) {
                EmptyState(message = stringResource(R.string.sensors_empty))
            } else {
                LazyColumn {
                    savedSection(state.saved, onForget = { onEvent(SensorsUiEvent.AskForget(it)) })
                    discoveredSection(state, onPair)
                }
            }
        }
        BigButton(
            text = stringResource(if (state.isScanning) R.string.sensors_stop_scan else R.string.sensors_scan_action),
            onClick = { onEvent(if (state.isScanning) SensorsUiEvent.StopScan else SensorsUiEvent.StartScan) },
            style = if (state.isScanning) BigButtonStyle.SECONDARY else BigButtonStyle.PRIMARY,
            modifier = Modifier.padding(RideCompDimens.spacingM),
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
private fun SensorsBanner(state: SensorsUiState, onFixBlocker: (ScanBlocker) -> Unit) {
    val blocker = state.blocker
    when {
        blocker != null -> AlertBanner(
            message = stringResource(blocker.messageRes()),
            severity = AlertSeverity.WARN,
            actions = listOf(BannerAction(stringResource(blocker.actionRes())) { onFixBlocker(blocker) }),
        )
        state.hasScanFailed -> AlertBanner(
            message = stringResource(R.string.sensors_scan_failed),
            severity = AlertSeverity.WARN,
        )
    }
}

private fun LazyListScope.savedSection(saved: List<SavedSensor>, onForget: (SavedSensor) -> Unit) {
    if (saved.isEmpty()) return
    item(key = SAVED_HEADER_KEY) { SectionHeader(stringResource(R.string.sensors_saved)) }
    items(saved, key = { SAVED_KEY_PREFIX + it.address }) { sensor ->
        ListRow(
            title = sensor.name,
            subtitle = savedSubtitle(sensor),
            trailing = {
                TextButton(onClick = { onForget(sensor) }) { Text(stringResource(R.string.sensors_forget)) }
            },
        )
    }
}

private fun LazyListScope.discoveredSection(state: SensorsUiState, onPair: (DiscoveredSensor) -> Unit) {
    if (state.discovered.isEmpty() && !state.isScanning) return
    item(key = FOUND_HEADER_KEY) {
        SectionHeader(stringResource(if (state.isScanning) R.string.sensors_scanning else R.string.sensors_found))
    }
    items(state.discovered, key = { FOUND_KEY_PREFIX + it.address }) { sensor ->
        ListRow(
            title = sensor.name ?: stringResource(R.string.sensors_unnamed),
            subtitle = kindsLabel(sensor.kinds),
            onClick = { onPair(sensor) },
            trailing = { Text(stringResource(R.string.sensors_pair)) },
        )
    }
}

@Composable
private fun savedSubtitle(sensor: SavedSensor): String {
    val kinds = kindsLabel(sensor.kinds)
    val battery = sensor.lastBatteryPercent ?: return kinds
    val batteryRes = if (sensor.isBatteryLow) R.string.sensors_battery_low else R.string.sensors_battery
    return stringResource(R.string.sensors_subtitle, kinds, stringResource(batteryRes, battery))
}

@Composable
private fun kindsLabel(kinds: Set<SensorKind>): String =
    SensorKind.entries.filter { it in kinds }.map { stringResource(it.labelRes()) }
        .ifEmpty { listOf(stringResource(R.string.sensors_kind_unknown)) }
        .joinToString(separator = stringResource(R.string.sensors_kind_separator))

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
        )
    }
}
