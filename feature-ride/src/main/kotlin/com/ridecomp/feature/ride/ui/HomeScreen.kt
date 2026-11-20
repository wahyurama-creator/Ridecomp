package com.ridecomp.feature.ride.ui

import android.text.format.Formatter
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ridecomp.core.config.Units
import com.ridecomp.core.engine.map.MapBounds
import com.ridecomp.core.engine.map.MapDownload
import com.ridecomp.core.engine.map.MapPackError
import com.ridecomp.core.engine.map.MapRegion
import com.ridecomp.core.engine.map.MapRegionState
import com.ridecomp.core.engine.map.MapRegionStatus
import com.ridecomp.core.engine.recording.BatteryStatus
import com.ridecomp.core.engine.sensors.SavedSensor
import com.ridecomp.core.engine.sensors.SensorKind
import com.ridecomp.core.engine.sensors.isBatteryLow
import com.ridecomp.core.engine.update.AppRelease
import com.ridecomp.core.model.Activity
import com.ridecomp.core.usecase.ActivePlan
import com.ridecomp.core.usecase.BackupReminder
import com.ridecomp.core.usecase.GpsAvailability
import com.ridecomp.core.usecase.MapRegionOffer
import com.ridecomp.core.usecase.RecoveryChoice
import com.ridecomp.core.util.UnitFormatter
import com.ridecomp.feature.ride.R
import com.ridecomp.feature.ride.model.displayName
import com.ridecomp.feature.ride.model.feelRes
import com.ridecomp.feature.ride.model.summary
import com.ridecomp.feature.ride.viewmodel.HomeEffect
import com.ridecomp.feature.ride.viewmodel.HomeUiEvent
import com.ridecomp.feature.ride.viewmodel.HomeUiState
import com.ridecomp.feature.ride.viewmodel.HomeViewModel
import com.ridecomp.ui.R as UiR
import com.ridecomp.ui.component.BigButton
import com.ridecomp.ui.component.BigButtonSize
import com.ridecomp.ui.component.BigButtonStyle
import com.ridecomp.ui.component.CardTone
import com.ridecomp.ui.component.ConfirmDialog
import com.ridecomp.ui.component.LinkText
import com.ridecomp.ui.component.RideCompCard
import com.ridecomp.ui.component.StatusTone
import com.ridecomp.ui.preview.RideCompPreview
import com.ridecomp.ui.preview.ThemePreviews
import com.ridecomp.ui.theme.RideCompDimens
import com.ridecomp.ui.theme.RideCompTheme
import com.ridecomp.ui.udf.ObserveEffects
import com.ridecomp.ui.util.thenIf
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.collections.immutable.persistentListOf
import org.koin.androidx.compose.koinViewModel

/** Home (02-DESIGN §3.2): START RIDE, ride in progress, recovery banner, last ride. */
@Composable
fun HomeRoute(
    onOpenPreRide: () -> Unit,
    onOpenRide: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenBackup: () -> Unit = {},
    tipContent: (@Composable () -> Unit)? = null,
    /** The sensor tiles open the Sensors tab (novice test, D-86). */
    onOpenSensors: () -> Unit = {},
    viewModel: HomeViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val uriHandler = LocalUriHandler.current
    LifecycleResumeEffect(Unit) {
        viewModel.onEvent(HomeUiEvent.Refresh)
        onPauseOrDispose {}
    }
    ObserveEffects(viewModel.effects) { effect ->
        when (effect) {
            HomeEffect.OpenPreRide -> onOpenPreRide()
            HomeEffect.OpenRide -> onOpenRide()
            HomeEffect.OpenBackup -> onOpenBackup()
            is HomeEffect.OpenUrl -> uriHandler.openUri(effect.url)
        }
    }
    HomeScreen(
        state = state,
        onEvent = viewModel::onEvent,
        modifier = modifier,
        tipContent = tipContent,
        onOpenSensors = onOpenSensors,
    )
}

/** [tipContent]: the first-ride tip of the guide (Phase 4G), from the app. */
@Composable
fun HomeScreen(
    state: HomeUiState,
    onEvent: (HomeUiEvent) -> Unit,
    modifier: Modifier = Modifier,
    tipContent: (@Composable () -> Unit)? = null,
    onOpenSensors: () -> Unit = {},
) {
    var isDiscardDialogShown by remember { mutableStateOf(false) }
    val format = remember(state.unitSystem) { UnitFormatter(state.unitSystem) }
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(RideCompTheme.colors.bg)
            .verticalScroll(rememberScrollState())
            .padding(
                start = RideCompDimens.screenPaddingHorizontal,
                end = RideCompDimens.screenPaddingHorizontal,
                top = RideCompDimens.screenPaddingTop,
                bottom = RideCompDimens.screenPaddingBottom,
            ),
        verticalArrangement = Arrangement.spacedBy(RideCompDimens.blockGap),
    ) {
        HomeHeader(state.battery)
        tipContent?.invoke()
        val recoverable = state.recoverable
        if (recoverable != null) {
            RecoveryCard(recoverable, format, onEvent, onDiscard = { isDiscardDialogShown = true })
        } else {
            state.backupReminder?.let { BackupReminderCard(it, onEvent) }
        }
        state.update?.let { UpdateCard(it, onEvent) }
        state.mapOffer?.let { MapOfferCard(it, onEvent) }
        StatusTiles(state, onOpenSensors)
        if (state.isRideInProgress) {
            Text(
                text = stringResource(R.string.ride_home_in_progress),
                style = RideCompTheme.typography.cardTitle,
                color = RideCompTheme.colors.ok,
            )
            BigButton(
                text = stringResource(R.string.ride_home_open_ride),
                onClick = { onEvent(HomeUiEvent.OpenRide) },
                size = BigButtonSize.HERO,
                iconRes = UiR.drawable.ic_play,
            )
        } else if (state.recoverable == null) {
            // With an unsaved ride waiting, its card decides first: no second ride to start by mistake.
            state.plan?.let { PlanSessionCard(it, onEvent) }
            BigButton(
                text = stringResource(R.string.ride_start_button),
                onClick = { onEvent(HomeUiEvent.StartRide) },
                size = BigButtonSize.HERO,
                iconRes = UiR.drawable.ic_play,
            )
        }
        state.lastRide?.let { LastRideCard(it, format) }
    }
    if (isDiscardDialogShown) {
        ConfirmDialog(
            title = stringResource(R.string.ride_recovery_discard_title),
            message = stringResource(R.string.ride_recovery_discard_message),
            confirmLabel = stringResource(R.string.ride_recovery_discard),
            dismissLabel = stringResource(R.string.ride_cancel),
            onConfirm = {
                isDiscardDialogShown = false
                onEvent(HomeUiEvent.Recover(RecoveryChoice.DISCARD))
            },
            onDismiss = { isDiscardDialogShown = false },
        )
    }
}

/** Today in the followed training plan (T7.4b): the session with "Mulai sesi", or a rest day. */
@Composable
private fun PlanSessionCard(plan: ActivePlan, onEvent: (HomeUiEvent) -> Unit) {
    val today = plan.today ?: return
    val colors = RideCompTheme.colors
    RideCompCard(tone = CardTone.OK) {
        Text(
            text = stringResource(
                R.string.home_plan_label,
                plan.plan.displayName(),
                today.weekNumber,
                plan.plan.weeks.size,
            ).uppercase(),
            style = RideCompTheme.typography.dataLabel,
            color = colors.ok,
        )
        val session = today.session
        if (session == null) {
            Text(
                text = stringResource(R.string.home_plan_rest),
                style = RideCompTheme.typography.body,
                color = colors.textPrimary,
            )
            return@RideCompCard
        }
        Text(
            text = stringResource(R.string.home_plan_today, session.summary()),
            style = RideCompTheme.typography.cardTitle,
            color = colors.textPrimary,
        )
        Text(
            text = stringResource(session.intensity.feelRes()),
            style = RideCompTheme.typography.caption,
            color = colors.textSecondary,
            modifier = Modifier.padding(bottom = RideCompDimens.spacingSm),
        )
        BigButton(
            text = stringResource(R.string.home_plan_start),
            onClick = { onEvent(HomeUiEvent.StartRide) },
            size = BigButtonSize.SECONDARY,
            style = BigButtonStyle.SECONDARY,
        )
    }
}

/** "Ride**Comp**" logo and the phone battery (13-UI-SCREENS #3). */
@Composable
private fun HomeHeader(battery: BatteryStatus?) {
    val colors = RideCompTheme.colors
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = buildAnnotatedString {
                append(stringResource(R.string.home_logo_first))
                withStyle(SpanStyle(color = colors.accent)) { append(stringResource(R.string.home_logo_second)) }
            },
            style = RideCompTheme.typography.title,
            color = colors.textPrimary,
            modifier = Modifier.weight(1f).semantics { heading() },
        )
        battery?.let {
            Text(
                text = stringResource(R.string.home_battery, it.percent),
                style = RideCompTheme.typography.caption,
                color = colors.textSecondary,
            )
        }
    }
}

/** Unsaved ride after a crash/kill (T1.6): resume, save or discard (2-step). */
@Composable
private fun RecoveryCard(ride: Activity, format: UnitFormatter, onEvent: (HomeUiEvent) -> Unit, onDiscard: () -> Unit) {
    RideCompCard(tone = CardTone.WARNING) {
        Text(
            text = stringResource(R.string.ride_recovery_message),
            style = RideCompTheme.typography.cardTitle,
            color = RideCompTheme.colors.warnText,
        )
        Text(
            text = rideSummary(ride, format),
            style = RideCompTheme.typography.body,
            color = RideCompTheme.colors.textTertiary,
        )
        // Resume is what most riders want: full width; three buttons in a row cut the labels (novice test).
        BigButton(
            text = stringResource(R.string.ride_recovery_resume),
            onClick = { onEvent(HomeUiEvent.Recover(RecoveryChoice.RESUME)) },
            style = BigButtonStyle.WARN,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(RideCompDimens.spacingS)) {
            BigButton(
                text = stringResource(R.string.ride_recovery_save),
                onClick = { onEvent(HomeUiEvent.Recover(RecoveryChoice.SAVE)) },
                style = BigButtonStyle.WARN_OUTLINE,
                modifier = Modifier.weight(1f),
            )
            BigButton(
                text = stringResource(R.string.ride_recovery_discard),
                onClick = onDiscard,
                style = BigButtonStyle.TEXT_DANGER,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/**
 * Four status tiles (13-UI-SCREENS #3): GPS permission, then saved sensors with their last battery. A
 * sensor tile opens the Sensors tab, so "Tambah" leads straight to pairing.
 */
@Composable
private fun StatusTiles(state: HomeUiState, onOpenSensors: () -> Unit) {
    val gpsTile = when (state.gps) {
        GpsAvailability.READY -> TileValue(stringResource(R.string.home_tile_gps_ready), StatusTone.OK)
        GpsAvailability.NO_PERMISSION -> TileValue(
            stringResource(R.string.home_tile_gps_no_permission),
            StatusTone.DANGER,
        )
        GpsAvailability.DISABLED -> TileValue(stringResource(R.string.home_tile_gps_off), StatusTone.DANGER)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(RideCompDimens.spacingS)) {
        StatusTile(UiR.drawable.ic_location, stringResource(R.string.home_tile_gps), gpsTile, Modifier.weight(1f))
        SensorKind.entries.forEach { kind ->
            StatusTile(
                iconRes = kind.tileIcon(),
                label = stringResource(kind.tileLabel()),
                value = sensorTileValue(state.sensors, kind),
                modifier = Modifier.weight(1f),
                onClick = onOpenSensors,
            )
        }
    }
}

private data class TileValue(val text: String, val tone: StatusTone)

@Composable
private fun sensorTileValue(sensors: List<SavedSensor>, kind: SensorKind): TileValue {
    val sensor = sensors.firstOrNull { kind in it.kinds }
    return when {
        sensor == null && kind == SensorKind.POWER ->
            TileValue(stringResource(R.string.home_tile_power_estimate), StatusTone.INACTIVE)
        sensor == null -> TileValue(stringResource(R.string.home_tile_none), StatusTone.INACTIVE)
        else -> TileValue(
            sensor.lastBatteryPercent?.let { stringResource(R.string.home_battery_percent, it) }
                ?: stringResource(R.string.home_tile_saved),
            if (sensor.isBatteryLow) StatusTone.WARN else StatusTone.OK,
        )
    }
}

@Composable
private fun StatusTile(
    @DrawableRes iconRes: Int,
    label: String,
    value: TileValue,
    modifier: Modifier,
    onClick: (() -> Unit)? = null,
) {
    val colors = RideCompTheme.colors
    val tint = when (value.tone) {
        StatusTone.OK -> colors.ok
        StatusTone.WARN -> colors.warnText
        StatusTone.DANGER -> colors.danger
        StatusTone.INACTIVE -> colors.disabled
    }
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(RideCompDimens.cornerRadiusSmall))
            .background(colors.surface)
            .thenIf(onClick != null) { clickable(role = Role.Button) { onClick?.invoke() } }
            .padding(vertical = RideCompDimens.spacingSm, horizontal = RideCompDimens.spacingXs)
            .semantics(mergeDescendants = true) {},
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(RideCompDimens.spacingXs),
    ) {
        Icon(painterResource(iconRes), contentDescription = null, tint = tint)
        Text(
            text = label,
            style = RideCompTheme.typography.caption.copy(fontWeight = FontWeight.Bold),
            color = if (value.tone == StatusTone.INACTIVE) colors.textSecondary else colors.textPrimary,
            maxLines = 1,
        )
        Text(
            text = value.text,
            style = RideCompTheme.typography.navLabel,
            color = if (value.tone == StatusTone.INACTIVE) colors.textSecondary else tint,
            maxLines = 1,
        )
    }
}

private fun SensorKind.tileIcon(): Int = when (this) {
    SensorKind.HEART_RATE -> UiR.drawable.ic_heart
    SensorKind.SPEED_CADENCE -> UiR.drawable.ic_cadence
    SensorKind.POWER -> UiR.drawable.ic_power
}

@StringRes
private fun SensorKind.tileLabel(): Int = when (this) {
    SensorKind.HEART_RATE -> R.string.home_tile_hr
    SensorKind.SPEED_CADENCE -> R.string.home_tile_cadence
    SensorKind.POWER -> R.string.home_tile_power
}

/** Last ride (13-UI-SCREENS #3): distance, moving time, average speed. */
@Composable
private fun LastRideCard(ride: Activity, format: UnitFormatter) {
    RideCompCard {
        Text(
            text = stringResource(R.string.home_last_ride_title, ride.name).uppercase(),
            style = RideCompTheme.typography.dataLabel,
            color = RideCompTheme.colors.textSecondary,
        )
        Row {
            LastRideStat(format.distance(ride.totals.distanceM), format.distanceUnit, Modifier.weight(1f))
            LastRideStat(
                format.duration(ride.totals.movingS * Units.MS_PER_SECOND),
                stringResource(R.string.home_last_ride_moving),
                Modifier.weight(1f),
            )
            LastRideStat(
                format.speed(ride.totals.avgSpeedMps),
                stringResource(R.string.home_last_ride_avg, format.speedUnit),
                Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun LastRideStat(value: String, caption: String, modifier: Modifier) {
    Column(modifier = modifier) {
        Text(text = value, style = RideCompTheme.typography.statValue, color = RideCompTheme.colors.textPrimary)
        Text(text = caption, style = RideCompTheme.typography.caption, color = RideCompTheme.colors.textSecondary)
    }
}

/** "12 Nov 06:12 · 12,40 km · 0:31:05" for the recovery card. */
private fun rideSummary(ride: Activity, format: UnitFormatter): String = listOf(
    START_FORMAT.format(Instant.ofEpochMilli(ride.startedAtMs).atZone(ZoneId.systemDefault())),
    "${format.distance(ride.totals.distanceM)} ${format.distanceUnit}",
    format.duration(ride.totals.movingS * Units.MS_PER_SECOND),
).joinToString(SEPARATOR)

private val START_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM HH:mm")
private const val SEPARATOR = " · "

/** Shown when opening the app, never by a background job (12-FEATURE-SPECS §12.4, BAT-05). */
@Composable
private fun BackupReminderCard(reminder: BackupReminder, onEvent: (HomeUiEvent) -> Unit) {
    val days = reminder.daysSinceBackup
    RideCompCard(tone = CardTone.WARNING) {
        Text(
            text = if (days == null) {
                pluralStringResource(R.plurals.home_backup_never, reminder.newActivities, reminder.newActivities)
            } else {
                pluralStringResource(R.plurals.home_backup_days, days.toInt(), days.toInt())
            },
            style = RideCompTheme.typography.cardTitle,
            color = RideCompTheme.colors.warnText,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(RideCompDimens.spacingS)) {
            BigButton(
                text = stringResource(R.string.home_backup_now),
                onClick = { onEvent(HomeUiEvent.OpenBackup) },
                style = BigButtonStyle.WARN,
                modifier = Modifier.weight(1f),
            )
            BigButton(
                text = stringResource(R.string.home_backup_later),
                onClick = { onEvent(HomeUiEvent.SnoozeBackupReminder) },
                style = BigButtonStyle.WARN_OUTLINE,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/**
 * "Versi 0.9.2 tersedia" (T10.9, redesigned T10.14): a dark card in the app's style, a download icon in an
 * accent circle, a "BARU" tag, a "Unduh pembaruan" link and ✕ to put this version off. A link, not a filled
 * button: an optional update must not compete with MULAI RIDE below it.
 */
@Composable
private fun UpdateCard(release: AppRelease, onEvent: (HomeUiEvent) -> Unit) {
    val colors = RideCompTheme.colors
    val closeLabel = stringResource(R.string.home_backup_later)
    RideCompCard {
        Row(horizontalArrangement = Arrangement.spacedBy(RideCompDimens.spacingSm)) {
            Box(
                modifier = Modifier
                    .size(RideCompDimens.iconTileSize)
                    .clip(CircleShape)
                    .background(colors.accentTint),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(UiR.drawable.ic_download),
                    contentDescription = null,
                    tint = colors.accent,
                    modifier = Modifier.size(RideCompDimens.iconSize),
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(RideCompDimens.spacingXs),
            ) {
                Text(
                    text = stringResource(R.string.home_update_new).uppercase(),
                    style = RideCompTheme.typography.dataLabel,
                    color = colors.accent,
                )
                Text(
                    text = stringResource(R.string.home_update_title, release.version),
                    style = RideCompTheme.typography.cardTitle,
                    color = colors.textPrimary,
                )
                Text(
                    text = stringResource(R.string.home_update_body),
                    style = RideCompTheme.typography.caption,
                    color = colors.textSecondary,
                )
                LinkText(
                    text = stringResource(R.string.home_update_download),
                    onClick = { onEvent(HomeUiEvent.OpenUpdate) },
                )
            }
            Box(
                modifier = Modifier
                    .size(RideCompDimens.touchTargetMin)
                    .clickable(role = Role.Button) { onEvent(HomeUiEvent.DismissUpdate) }
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

/**
 * "Peta offline untuk DI Yogyakarta" (T11.12): the region of the last ride, its size, and download, resume or
 * update; progress while downloading with "Batal"; ✕ = later. Like the update card: a link, not a button.
 */
@Composable
private fun MapOfferCard(offer: MapRegionOffer, onEvent: (HomeUiEvent) -> Unit) {
    val colors = RideCompTheme.colors
    val context = LocalContext.current
    val region = offer.state.region
    val download = offer.state.download
    val size = Formatter.formatShortFileSize(context, region.sizeBytes)
    RideCompCard {
        Row(horizontalArrangement = Arrangement.spacedBy(RideCompDimens.spacingSm)) {
            Box(
                modifier = Modifier
                    .size(RideCompDimens.iconTileSize)
                    .clip(CircleShape)
                    .background(colors.okTint),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(UiR.drawable.ic_map),
                    contentDescription = null,
                    tint = colors.ok,
                    modifier = Modifier.size(RideCompDimens.iconSize),
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(RideCompDimens.spacingXs),
            ) {
                Text(
                    text = stringResource(R.string.home_map_title, region.name),
                    style = RideCompTheme.typography.cardTitle,
                    color = colors.textPrimary,
                )
                Text(
                    text = mapOfferBody(offer, size),
                    style = RideCompTheme.typography.caption,
                    color = if (download is MapDownload.Failed) colors.warnText else colors.textSecondary,
                )
                if (download is MapDownload.Running) DownloadBar(download.downloadedBytes, download.totalBytes)
                LinkText(
                    text = stringResource(mapOfferAction(offer)),
                    onClick = {
                        onEvent(if (download is MapDownload.Running) HomeUiEvent.CancelMap else HomeUiEvent.DownloadMap)
                    },
                )
            }
            if (download !is MapDownload.Running) {
                val closeLabel = stringResource(R.string.home_backup_later)
                Box(
                    modifier = Modifier
                        .size(RideCompDimens.touchTargetMin)
                        .clickable(role = Role.Button) { onEvent(HomeUiEvent.DismissMap) }
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
}

@Composable
private fun mapOfferBody(offer: MapRegionOffer, size: String): String {
    val context = LocalContext.current
    return when (val download = offer.state.download) {
        is MapDownload.Running -> stringResource(
            R.string.home_map_downloading,
            Formatter.formatShortFileSize(context, download.downloadedBytes),
            Formatter.formatShortFileSize(context, download.totalBytes),
        )
        is MapDownload.Paused -> stringResource(
            R.string.home_map_paused,
            percent(download.downloadedBytes, download.totalBytes),
        )
        is MapDownload.Failed -> stringResource(download.error.messageRes())
        null -> if (offer.state.status == MapRegionStatus.UPDATE_AVAILABLE) {
            stringResource(R.string.home_map_update_body, size)
        } else {
            stringResource(R.string.home_map_body, size)
        }
    }
}

@StringRes
private fun mapOfferAction(offer: MapRegionOffer): Int = when (offer.state.download) {
    is MapDownload.Running -> R.string.home_map_cancel
    is MapDownload.Paused -> R.string.home_map_resume
    is MapDownload.Failed -> R.string.home_map_retry
    null -> if (offer.state.status ==
        MapRegionStatus.UPDATE_AVAILABLE
    ) {
        R.string.home_map_update
    } else {
        R.string.home_map_download
    }
}

@StringRes
private fun MapPackError.messageRes(): Int = when (this) {
    MapPackError.NO_NETWORK -> R.string.home_map_error_network
    MapPackError.NEEDS_WIFI -> R.string.home_map_error_wifi
    MapPackError.RIDING -> R.string.home_map_error_riding
    MapPackError.DOWNLOAD_FAILED -> R.string.home_map_error_download
    MapPackError.CHECKSUM, MapPackError.NOT_PMTILES -> R.string.home_map_error_file
    MapPackError.NO_SPACE -> R.string.home_map_error_space
    MapPackError.TOTAL_LIMIT -> R.string.home_map_error_limit
}

private fun percent(done: Long, total: Long): Int = (done * PERCENT / total.coerceAtLeast(1)).toInt()

/** A thin progress bar like the storage bar of Settings → Map. */
@Composable
private fun DownloadBar(done: Long, total: Long) {
    val colors = RideCompTheme.colors
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(RideCompDimens.storageBarHeight)
            .clip(RoundedCornerShape(RideCompDimens.storageBarHeight))
            .background(colors.divider),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth((done.toFloat() / total.coerceAtLeast(1)).coerceIn(0f, 1f))
                .fillMaxHeight()
                .background(colors.ok),
        )
    }
}

private const val PERCENT = 100

@ThemePreviews
@Composable
private fun HomeScreenPreview() {
    RideCompPreview {
        HomeScreen(
            state = HomeUiState(
                battery = BatteryStatus(86, isCharging = false),
                update = AppRelease("0.9.3", "https://github.com/wahyurama-creator/Ridecomp/releases/tag/v0.9.3"),
                mapOffer = MapRegionOffer(
                    MapRegionState(
                        MapRegion("di-yogyakarta", "DI Yogyakarta", MapBounds(109.9, -8.4, 110.8, -7.5), emptyList()),
                        MapRegionStatus.NOT_INSTALLED,
                        MapDownload.Running(downloadedBytes = 12_000_000, totalBytes = 21_300_000),
                        isHere = true,
                    ),
                    "maps-2026-11",
                ),
                sensors = persistentListOf(
                    SavedSensor("A", "HRM", setOf(SensorKind.HEART_RATE), lastBatteryPercent = 82),
                    SavedSensor("B", "CSC", setOf(SensorKind.SPEED_CADENCE), lastBatteryPercent = 12),
                ),
            ),
            onEvent = {},
        )
    }
}
