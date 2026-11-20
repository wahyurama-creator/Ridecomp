package com.ridecomp.feature.settings.ui

import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ridecomp.core.engine.map.MapCacheLimit
import com.ridecomp.core.engine.map.MapDownload
import com.ridecomp.core.engine.map.MapImportError
import com.ridecomp.core.engine.map.MapPackError
import com.ridecomp.core.engine.map.MapRegionState
import com.ridecomp.core.engine.map.MapRegionStatus
import com.ridecomp.core.engine.map.OfflineMap
import com.ridecomp.core.engine.map.OfflineMapConfig
import com.ridecomp.core.engine.map.OfflineMapLimit
import com.ridecomp.core.engine.map.OnlineMapMode
import com.ridecomp.feature.settings.R
import com.ridecomp.feature.settings.viewmodel.OfflineMapsUiEvent
import com.ridecomp.feature.settings.viewmodel.OfflineMapsUiState
import com.ridecomp.feature.settings.viewmodel.OfflineMapsViewModel
import com.ridecomp.ui.R as UiR
import com.ridecomp.ui.component.AlertBanner
import com.ridecomp.ui.component.AlertSeverity
import com.ridecomp.ui.component.BannerAction
import com.ridecomp.ui.component.BigButton
import com.ridecomp.ui.component.BigButtonSize
import com.ridecomp.ui.component.BigButtonStyle
import com.ridecomp.ui.component.Choice
import com.ridecomp.ui.component.ChoiceChips
import com.ridecomp.ui.component.ConfirmDialog
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
import java.text.DateFormat
import java.util.Date
import kotlinx.collections.immutable.toImmutableList
import org.koin.androidx.compose.koinViewModel

/** PMTiles has no registered MIME type: the picker offers any file and the header is validated. */
private val MAP_MIME_TYPES = arrayOf("application/octet-stream", "application/vnd.pmtiles", "*/*")

/** Offline maps entry point (T3.4, 13-UI-SCREENS #26). */
@Composable
fun OfflineMapsRoute(
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    tipContent: (@Composable () -> Unit)? = null,
    viewModel: OfflineMapsViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val fallbackName = stringResource(R.string.offline_maps_fallback_name)
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.onEvent(OfflineMapsUiEvent.Import(it.toString(), fallbackName)) }
    }
    OfflineMapsScreen(
        state = state,
        onEvent = viewModel::onEvent,
        onImport = { picker.launch(MAP_MIME_TYPES) },
        onBack = onBack,
        modifier = modifier,
        tipContent = tipContent,
    )
}

/**
 * Stateless screen: storage card, saved regions with delete, import. The design's draw-a-box download
 * needs network access, which RideComp does not have; regions are imported from files instead (D-64).
 */
@Composable
fun OfflineMapsScreen(
    state: OfflineMapsUiState,
    onEvent: (OfflineMapsUiEvent) -> Unit,
    onImport: () -> Unit,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    tipContent: (@Composable () -> Unit)? = null,
) {
    ScreenScaffold(
        modifier = modifier,
        title = stringResource(R.string.offline_maps_title),
        onBack = onBack,
        backLabel = stringResource(R.string.onboarding_back_label),
        banner = {
            state.error?.let { error ->
                AlertBanner(
                    message = stringResource(error.messageRes()),
                    severity = AlertSeverity.WARN,
                    actions = listOf(
                        BannerAction(stringResource(R.string.safety_ok)) {
                            onEvent(OfflineMapsUiEvent.ErrorShown)
                        },
                    ),
                )
            }
        },
    ) {
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
            OnlineMapChoice(state.onlineMapMode) { onEvent(OfflineMapsUiEvent.OnlineMode(it)) }
            MapCacheSection(state, onEvent)
            if (state.regions.isNotEmpty()) RegionMapsSection(state, onEvent)
            SectionHeader(stringResource(R.string.maps_offline_header), horizontalPadding = RideCompDimens.none)
            tipContent?.invoke()
            StorageCard(state, onEvent)
            val imported = state.importedMaps
            if (imported.isNotEmpty()) {
                SectionHeader(stringResource(R.string.offline_maps_saved), horizontalPadding = RideCompDimens.none)
                ListCard {
                    imported.forEachIndexed { index, map ->
                        MapRow(map, hasDivider = index < imported.lastIndex) {
                            onEvent(OfflineMapsUiEvent.AskDelete(map))
                        }
                    }
                }
            }
            BigButton(
                text = stringResource(
                    if (state.isImporting) R.string.offline_maps_importing else R.string.offline_maps_import,
                ),
                onClick = onImport,
                isEnabled = !state.isImporting,
                // Optional since the online map (D-90): not the main action of the screen.
                style = BigButtonStyle.SECONDARY,
                size = BigButtonSize.SECONDARY,
                iconRes = UiR.drawable.ic_download,
            )
            HowToCard()
        }
    }
    state.regionDeleteCandidate?.let { region ->
        ConfirmDialog(
            title = stringResource(R.string.offline_maps_delete_title, region.name),
            message = stringResource(R.string.offline_maps_delete_message),
            confirmLabel = stringResource(R.string.offline_maps_delete),
            dismissLabel = stringResource(R.string.backup_cancel),
            onConfirm = { onEvent(OfflineMapsUiEvent.ConfirmDeleteRegion) },
            onDismiss = { onEvent(OfflineMapsUiEvent.DismissDeleteRegion) },
        )
    }
    if (state.isClearCacheAsked) {
        ConfirmDialog(
            title = stringResource(R.string.maps_cache_clear_title),
            message = stringResource(R.string.maps_cache_clear_message),
            confirmLabel = stringResource(R.string.maps_cache_clear),
            dismissLabel = stringResource(R.string.backup_cancel),
            onConfirm = { onEvent(OfflineMapsUiEvent.ConfirmClearCache) },
            onDismiss = { onEvent(OfflineMapsUiEvent.DismissClearCache) },
        )
    }
    state.deleteCandidate?.let { map ->
        ConfirmDialog(
            title = stringResource(R.string.offline_maps_delete_title, map.name),
            message = stringResource(R.string.offline_maps_delete_message),
            confirmLabel = stringResource(R.string.offline_maps_delete),
            dismissLabel = stringResource(R.string.backup_cancel),
            onConfirm = { onEvent(OfflineMapsUiEvent.ConfirmDelete) },
            onDismiss = { onEvent(OfflineMapsUiEvent.DismissDelete) },
        )
    }
}

/** Online map: Wi‑Fi & data, Wi‑Fi only, or off (D-90). */
@Composable
private fun OnlineMapChoice(mode: OnlineMapMode, onSelect: (OnlineMapMode) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(RideCompDimens.spacingS)) {
        ChoiceChips(
            label = stringResource(R.string.maps_online_label),
            choices = OnlineMapMode.entries.map { Choice(it, stringResource(it.labelRes())) }.toImmutableList(),
            selected = mode,
            onSelect = onSelect,
            horizontalPadding = RideCompDimens.none,
            columns = OnlineMapMode.entries.size,
        )
        Text(
            text = stringResource(R.string.maps_online_hint),
            style = RideCompTheme.typography.caption,
            color = RideCompTheme.colors.textSecondary,
        )
    }
}

/**
 * Tile cache (T11.11): maps already seen online show again without internet. Limit 50/150/300 MB, the space
 * used now, and "clear" with confirmation.
 */
@Composable
private fun MapCacheSection(state: OfflineMapsUiState, onEvent: (OfflineMapsUiEvent) -> Unit) {
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(RideCompDimens.spacingS)) {
        ChoiceChips(
            label = stringResource(R.string.maps_cache_label),
            choices = MapCacheLimit.entries.map { limit ->
                Choice(limit, Formatter.formatShortFileSize(context, limit.bytes))
            }.toImmutableList(),
            selected = state.cacheLimit,
            onSelect = { onEvent(OfflineMapsUiEvent.CacheLimit(it)) },
            horizontalPadding = RideCompDimens.none,
            columns = MapCacheLimit.entries.size,
        )
        Text(
            text = stringResource(
                R.string.maps_cache_hint,
                state.cacheBytes?.let { Formatter.formatShortFileSize(context, it) } ?: "–",
            ),
            style = RideCompTheme.typography.caption,
            color = RideCompTheme.colors.textSecondary,
        )
        BigButton(
            text = stringResource(R.string.maps_cache_clear),
            onClick = { onEvent(OfflineMapsUiEvent.AskClearCache) },
            isEnabled = state.hasCachedTiles && !state.isClearingCache,
            style = BigButtonStyle.SECONDARY,
            size = BigButtonSize.SECONDARY,
        )
    }
}

/**
 * Province maps (T11.12): the rider's region first, size and state, and download/resume, cancel, update or
 * delete; Wi‑Fi only unless mobile data is allowed.
 */
@Composable
private fun RegionMapsSection(state: OfflineMapsUiState, onEvent: (OfflineMapsUiEvent) -> Unit) {
    SectionHeader(stringResource(R.string.maps_regions_header), horizontalPadding = RideCompDimens.none)
    ListCard {
        state.regions.forEachIndexed { index, region ->
            RegionRow(region, hasDivider = index < state.regions.lastIndex, onEvent)
        }
        SwitchRow(
            label = stringResource(R.string.maps_regions_mobile_data),
            description = stringResource(R.string.maps_regions_mobile_data_hint),
            isChecked = state.isMobileDataAllowed,
            onCheckedChange = { onEvent(OfflineMapsUiEvent.MobileData(it)) },
        )
    }
}

@Composable
private fun RegionRow(state: MapRegionState, hasDivider: Boolean, onEvent: (OfflineMapsUiEvent) -> Unit) {
    val context = LocalContext.current
    val colors = RideCompTheme.colors
    val region = state.region
    val size = Formatter.formatShortFileSize(context, region.sizeBytes)
    val download = state.download
    val detail = when (download) {
        is MapDownload.Running -> stringResource(
            R.string.maps_regions_downloading,
            Formatter.formatShortFileSize(context, download.downloadedBytes),
            size,
        )
        is MapDownload.Paused -> stringResource(R.string.maps_regions_paused, size)
        is MapDownload.Failed -> if (download.error == MapPackError.TOTAL_LIMIT) {
            stringResource(R.string.maps_regions_limit, size)
        } else {
            stringResource(R.string.maps_regions_failed, size)
        }
        null -> stringResource(state.status.labelRes(), size)
    }
    ListCardRow(
        title = region.name,
        subtitle = if (state.isHere) stringResource(R.string.maps_regions_here, detail) else detail,
        hasDivider = hasDivider,
        hasChevron = false,
        leading = {
            val tint = if (state.status == MapRegionStatus.INSTALLED) colors.ok else colors.textSecondary
            Icon(painterResource(UiR.drawable.ic_map), contentDescription = null, tint = tint)
        },
        trailing = { RegionActions(state, onEvent) },
    )
}

/**
 * Cancel while downloading; otherwise download when the map is missing or outdated, and delete whenever its
 * files are on the phone (also an outdated or same-named imported file, which must stay deletable).
 */
@Composable
private fun RegionActions(state: MapRegionState, onEvent: (OfflineMapsUiEvent) -> Unit) {
    val region = state.region
    val colors = RideCompTheme.colors
    if (state.download is MapDownload.Running) {
        IconSquareButton(
            iconRes = UiR.drawable.ic_close,
            contentDescription = stringResource(R.string.maps_regions_cancel, region.name),
            onClick = { onEvent(OfflineMapsUiEvent.CancelRegion(region)) },
        )
        return
    }
    Row(horizontalArrangement = Arrangement.spacedBy(RideCompDimens.spacingXs)) {
        if (state.status != MapRegionStatus.INSTALLED) {
            IconSquareButton(
                iconRes = UiR.drawable.ic_download,
                contentDescription = stringResource(R.string.maps_regions_download, region.name),
                onClick = { onEvent(OfflineMapsUiEvent.DownloadRegion(region)) },
                tint = colors.accent,
            )
        }
        if (state.status != MapRegionStatus.NOT_INSTALLED) {
            IconSquareButton(
                iconRes = UiR.drawable.ic_trash,
                contentDescription = stringResource(R.string.offline_maps_delete_label, region.name),
                onClick = { onEvent(OfflineMapsUiEvent.AskDeleteRegion(region)) },
                tint = colors.danger,
            )
        }
    }
}

@StringRes
private fun MapRegionStatus.labelRes(): Int = when (this) {
    MapRegionStatus.NOT_INSTALLED -> R.string.maps_regions_not_installed
    MapRegionStatus.INSTALLED -> R.string.maps_regions_installed
    MapRegionStatus.UPDATE_AVAILABLE -> R.string.maps_regions_update
}

/** Most space all offline maps may use: 500 MB, 1 GB or 2 GB (T11.14, default 1 GB). */
@Composable
private fun OfflineLimitChoice(state: OfflineMapsUiState, onEvent: (OfflineMapsUiEvent) -> Unit) {
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(RideCompDimens.spacingS)) {
        ChoiceChips(
            label = stringResource(R.string.offline_maps_limit_label),
            choices = OfflineMapLimit.entries.map { limit ->
                Choice(limit, Formatter.formatShortFileSize(context, limit.bytes))
            }.toImmutableList(),
            selected = state.offlineMapLimit,
            onSelect = { onEvent(OfflineMapsUiEvent.OfflineLimit(it)) },
            horizontalPadding = RideCompDimens.none,
            columns = OfflineMapLimit.entries.size,
        )
        Text(
            text = stringResource(R.string.offline_maps_limit_hint),
            style = RideCompTheme.typography.caption,
            color = RideCompTheme.colors.textSecondary,
        )
    }
}

/** Short name of an online map mode, also used in the Settings summary. */
@StringRes
internal fun OnlineMapMode.labelRes(): Int = when (this) {
    OnlineMapMode.ALWAYS -> R.string.maps_online_always
    OnlineMapMode.WIFI_ONLY -> R.string.maps_online_wifi
    OnlineMapMode.OFF -> R.string.maps_online_off
}

/** "Peta RideComp memakai 186 MB" with a usage bar (13-UI-SCREENS #26). */
@Composable
private fun StorageCard(state: OfflineMapsUiState, onEvent: (OfflineMapsUiEvent) -> Unit) {
    val context = LocalContext.current
    val colors = RideCompTheme.colors
    val free = state.freeBytes
    RideCompCard {
        Text(
            text = stringResource(R.string.offline_maps_storage_title),
            style = RideCompTheme.typography.cardTitle,
            color = colors.textPrimary,
        )
        if (free != null) {
            val total = (free + state.usedBytes).coerceAtLeast(1)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(RideCompDimens.storageBarHeight)
                    .clip(RoundedCornerShape(RideCompDimens.storageBarHeight))
                    .background(colors.divider),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth((state.usedBytes.toFloat() / total).coerceIn(0f, 1f))
                        .fillMaxHeight()
                        .background(colors.accent),
                )
            }
            Text(
                text = stringResource(
                    R.string.offline_maps_storage,
                    Formatter.formatShortFileSize(context, state.usedBytes),
                    Formatter.formatShortFileSize(context, free),
                ),
                style = RideCompTheme.typography.caption,
                color = colors.textSecondary,
            )
            // Little space left: say so before a download or import is refused (T11.14).
            if (free < OfflineMapConfig.LOW_SPACE_WARNING_BYTES) {
                Text(
                    text = stringResource(R.string.offline_maps_low_space),
                    style = RideCompTheme.typography.caption,
                    color = colors.warnText,
                )
            }
        }
    }
    OfflineLimitChoice(state, onEvent)
}

@Composable
private fun MapRow(map: OfflineMap, hasDivider: Boolean, onDelete: () -> Unit) {
    val context = LocalContext.current
    ListCardRow(
        title = map.name,
        subtitle = stringResource(
            R.string.offline_maps_item,
            Formatter.formatShortFileSize(context, map.sizeBytes),
            DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(map.importedAtMs)),
        ),
        hasDivider = hasDivider,
        leading = {
            Icon(painterResource(UiR.drawable.ic_map), contentDescription = null, tint = RideCompTheme.colors.ok)
        },
        trailing = {
            IconSquareButton(
                iconRes = UiR.drawable.ic_trash,
                contentDescription = stringResource(R.string.offline_maps_delete_label, map.name),
                onClick = onDelete,
                tint = RideCompTheme.colors.danger,
            )
        },
    )
}

/** Where region files come from (Protomaps extract of OpenStreetMap, 04-INTEGRATIONS §D). */
@Composable
private fun HowToCard() {
    RideCompCard {
        Row(horizontalArrangement = Arrangement.spacedBy(RideCompDimens.cardGap)) {
            Icon(
                painterResource(UiR.drawable.ic_info),
                contentDescription = null,
                tint = RideCompTheme.colors.textSecondary,
            )
            Column(verticalArrangement = Arrangement.spacedBy(RideCompDimens.spacingXs)) {
                Text(
                    text = stringResource(R.string.offline_maps_howto_title),
                    style = RideCompTheme.typography.bodyStrong,
                    color = RideCompTheme.colors.textPrimary,
                )
                Text(
                    text = stringResource(R.string.offline_maps_howto),
                    style = RideCompTheme.typography.caption,
                    color = RideCompTheme.colors.textTertiary,
                )
            }
        }
    }
}

@StringRes
private fun MapImportError.messageRes(): Int = when (this) {
    MapImportError.NOT_PMTILES -> R.string.offline_maps_error_not_pmtiles
    MapImportError.UNSUPPORTED_VERSION -> R.string.offline_maps_error_version
    MapImportError.NOT_VECTOR -> R.string.offline_maps_error_not_vector
    MapImportError.TOO_LARGE -> R.string.offline_maps_error_too_large
    MapImportError.NO_SPACE -> R.string.offline_maps_error_no_space
    MapImportError.TOTAL_LIMIT -> R.string.offline_maps_error_limit
    MapImportError.READ_FAILED -> R.string.offline_maps_error_read
}

@ThemePreviews
@Composable
private fun OfflineMapsPreview() {
    RideCompPreview {
        OfflineMapsScreen(state = OfflineMapsUiState(freeBytes = 4_000_000_000, isLoaded = true), onEvent = {
        }, onImport = {})
    }
}
