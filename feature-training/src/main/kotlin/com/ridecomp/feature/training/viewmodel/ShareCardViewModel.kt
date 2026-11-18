package com.ridecomp.feature.training.viewmodel

import android.graphics.Bitmap
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.lifecycle.viewModelScope
import com.ridecomp.core.engine.share.ShareBackdrop
import com.ridecomp.core.engine.share.ShareCardConfig
import com.ridecomp.core.engine.share.ShareCardLine
import com.ridecomp.core.engine.share.ShareDetail
import com.ridecomp.core.engine.share.ShareDetails
import com.ridecomp.core.engine.share.ShareFormat
import com.ridecomp.core.engine.share.SharePalette
import com.ridecomp.core.engine.share.SharePoint
import com.ridecomp.core.engine.share.ShareStat
import com.ridecomp.core.engine.share.ShareTarget
import com.ridecomp.core.engine.share.ShareTemplate
import com.ridecomp.core.model.RideTotals
import com.ridecomp.core.model.UnitSystem
import com.ridecomp.core.source.DispatcherProvider
import com.ridecomp.core.usecase.GetShareTargetsUseCase
import com.ridecomp.core.usecase.LoadShareCardUseCase
import com.ridecomp.core.usecase.ObserveMapTilesUseCase
import com.ridecomp.core.usecase.ObserveRideSettingsUseCase
import com.ridecomp.core.usecase.SaveShareCardUseCase
import com.ridecomp.core.usecase.ShareCardData
import com.ridecomp.core.usecase.ShareCardImageUseCase
import com.ridecomp.core.util.runCatchingCancellable
import com.ridecomp.ui.map.MapPoint
import com.ridecomp.ui.udf.NoEffect
import com.ridecomp.ui.udf.UdfViewModel
import java.io.ByteArrayOutputStream
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** What happens with the captured card. */
sealed interface ShareAction {
    /** To one app (Instagram, WhatsApp) or the share sheet. */
    data class Share(val target: ShareTarget = ShareTarget.OTHER) : ShareAction

    /** "Simpan gambar": the SAF document the rider picked. */
    data class SaveTo(val uri: String) : ShareAction
}

/** Loads the rider's photo for the photo template, scaled down to about [maxSidePx] (memory, MEM-06). */
fun interface SharePhotoLoader {
    suspend fun load(uri: String, maxSidePx: Int): ImageBitmap?
}

@Immutable
data class ShareCardUiState(
    val data: ShareCardData? = null,
    val template: ShareTemplate = ShareTemplate.POSTER,
    val palette: SharePalette = SharePalette.SUNSET,
    val format: ShareFormat = ShareFormat.STORY,
    val isDark: Boolean = true,
    /** Classic and poster without a background: a transparent PNG (the sticker always is). */
    val isBackgroundTransparent: Boolean = false,
    /** Privacy (§11): first and last 500 m hidden, on by default. */
    val isEndsHidden: Boolean = true,
    val isTrackShown: Boolean = true,
    val stats: ImmutableList<ShareStat> = persistentListOf(),
    val details: ImmutableSet<ShareDetail> = persistentSetOf(),
    /** The line as drawn, already trimmed and projected. */
    val line: ImmutableList<SharePoint> = persistentListOf(),
    val photo: ImageBitmap? = null,
    val unitSystem: UnitSystem = UnitSystem.METRIC,
    /** Map for the map cards (T10.6): online tiles when allowed, else offline regions. */
    val isMapOnline: Boolean = false,
    val mapRegions: ImmutableList<String> = persistentListOf(),
    /** The trimmed line with its coordinates, used only to place the map cards (privacy as [line]). */
    val trail: ImmutableList<MapPoint> = persistentListOf(),
    /** Share buttons: installed direct apps, then the share sheet. */
    val targets: ImmutableList<ShareTarget> = persistentListOf(ShareTarget.OTHER),
    /** The card is being captured for this action; options are locked meanwhile. */
    val pending: ShareAction? = null,
    /** The last share or save failed (out of memory, storage, no app to share with…). */
    val isShareFailed: Boolean = false,
    val isSaved: Boolean = false,
    val isMissing: Boolean = false,
) {
    val isSharing: Boolean get() = pending != null

    /** True when the exported PNG has no background (preview shows a dark checkerboard behind it). */
    val isTransparent: Boolean
        get() = template.backdrop == ShareBackdrop.TRANSPARENT ||
            (isBackgroundTransparent && (template == ShareTemplate.CLASSIC || template == ShareTemplate.POSTER))

    /** The map cards need a line and a map (online, or an offline region). */
    val hasMap: Boolean get() = trail.size >= 2 && (isMapOnline || mapRegions.isNotEmpty())

    /** Cards of the gallery in the owner's reference order (T10.6); map cards only when there is a map. */
    val templates: ImmutableList<ShareTemplate>
        get() = GALLERY_ORDER.filter { hasMap || it.backdrop != ShareBackdrop.MAP }.toImmutableList()

    /** Statistics this ride has data for, in display order. */
    val availableStats: ImmutableList<ShareStat>
        get() = data?.activity?.totals?.let(::available).orEmpty().toImmutableList()

    /** Details this ride has data for. */
    val availableDetails: ImmutableList<ShareDetail>
        get() = data?.details?.let(::availableDetails).orEmpty().toImmutableList()
}

sealed interface ShareCardUiEvent {
    /** A change of how the card looks: it only changes the state. */
    sealed interface Style : ShareCardUiEvent {
        fun reduce(state: ShareCardUiState): ShareCardUiState
    }

    data class SetTemplate(val template: ShareTemplate) : Style {
        override fun reduce(state: ShareCardUiState) = state.copy(template = template, isSaved = false)
    }

    data class SetPalette(val palette: SharePalette) : Style {
        override fun reduce(state: ShareCardUiState) = state.copy(palette = palette)
    }

    data class SetFormat(val format: ShareFormat) : Style {
        override fun reduce(state: ShareCardUiState) = state.copy(format = format)
    }

    data class SetDark(val isDark: Boolean) : Style {
        override fun reduce(state: ShareCardUiState) = state.copy(isDark = isDark)
    }

    data class SetBackgroundTransparent(val isTransparent: Boolean) : Style {
        override fun reduce(state: ShareCardUiState) = state.copy(isBackgroundTransparent = isTransparent)
    }

    data class SetTrackShown(val isShown: Boolean) : Style {
        override fun reduce(state: ShareCardUiState) = state.copy(isTrackShown = isShown)
    }

    /** Adds or removes a statistic (at most 4). */
    data class ToggleStat(val stat: ShareStat) : Style {
        override fun reduce(state: ShareCardUiState) = state.copy(stats = toggled(state.stats, stat).toImmutableList())
    }

    data class ToggleDetail(val detail: ShareDetail) : Style {
        override fun reduce(state: ShareCardUiState): ShareCardUiState {
            val details = if (detail in state.details) state.details - detail else state.details + detail
            return state.copy(details = details.toImmutableSet())
        }
    }

    data class SetEndsHidden(val isHidden: Boolean) : ShareCardUiEvent

    /** A photo picked with the Photo Picker (no permission needed). */
    data class PhotoPicked(val uri: String) : ShareCardUiEvent

    /** Share or save: the card is drawn at full size, then [Captured]. */
    data class Render(val action: ShareAction) : ShareCardUiEvent

    /** The card drawn at its real pixel size. */
    data class Captured(val image: ImageBitmap) : ShareCardUiEvent
}

/** Use cases of the share card editor. */
data class ShareCardUseCases(
    val load: LoadShareCardUseCase,
    val observeSettings: ObserveRideSettingsUseCase,
    val share: ShareCardImageUseCase,
    val save: SaveShareCardUseCase,
    val targets: GetShareTargetsUseCase,
    val observeMapTiles: ObserveMapTilesUseCase,
)

class ShareCardViewModel(
    activityId: Long,
    private val useCases: ShareCardUseCases,
    private val photos: SharePhotoLoader,
    private val dispatchers: DispatcherProvider,
) : UdfViewModel<ShareCardUiState, ShareCardUiEvent, NoEffect>(ShareCardUiState()) {
    init {
        viewModelScope.launch {
            val data = useCases.load(activityId)
            if (data == null) {
                updateState { copy(isMissing = true) }
                return@launch
            }
            val stats = available(data.activity.totals).take(ShareCardConfig.MAX_STATS)
            val details = availableDetails(data.details).toImmutableSet()
            updateState { copy(data = data, stats = stats.toImmutableList(), details = details) }
            refreshLine()
        }
        viewModelScope.launch {
            useCases.observeSettings().collect { settings -> updateState { copy(unitSystem = settings.unitSystem) } }
        }
        viewModelScope.launch {
            useCases.observeMapTiles().collect { tiles ->
                updateState { copy(isMapOnline = tiles.isOnline, mapRegions = tiles.regionPaths.toImmutableList()) }
            }
        }
        viewModelScope.launch {
            val targets = withContext(dispatchers.io) { useCases.targets() }
            updateState { copy(targets = targets.toImmutableList()) }
        }
    }

    override fun onEvent(event: ShareCardUiEvent) {
        // The card is being captured: options stay as they are so the image matches the preview.
        if (currentState.isSharing && event !is ShareCardUiEvent.Captured) return
        when (event) {
            is ShareCardUiEvent.Style -> updateState { event.reduce(this) }
            is ShareCardUiEvent.SetEndsHidden -> {
                updateState { copy(isEndsHidden = event.isHidden) }
                viewModelScope.launch { refreshLine() }
            }
            is ShareCardUiEvent.PhotoPicked -> viewModelScope.launch {
                val photo = runCatchingCancellable {
                    photos.load(event.uri, ShareCardConfig.STORY_HEIGHT_PX)
                }.getOrNull()
                updateState { copy(photo = photo ?: this.photo, template = ShareTemplate.PHOTO) }
            }
            is ShareCardUiEvent.Render -> updateState {
                copy(pending = event.action, isShareFailed = false, isSaved = false)
            }
            is ShareCardUiEvent.Captured -> currentState.pending?.let { finish(it, event.image) }
        }
    }

    /** A failure (memory, storage, no app to share with) is shown, never a crash; the buttons come back. */
    private fun finish(action: ShareAction, image: ImageBitmap) {
        viewModelScope.launch {
            val result = runCatchingCancellable {
                val png = encodePng(image)
                when (action) {
                    is ShareAction.Share -> useCases.share(png, action.target).let { true }
                    is ShareAction.SaveTo -> useCases.save(action.uri, png)
                }
            }
            val isDone = result.getOrNull() == true
            updateState {
                copy(pending = null, isShareFailed = !isDone, isSaved = isDone && action is ShareAction.SaveTo)
            }
        }
    }

    /** PNG of the card on the CPU dispatcher; the software copy is released right after (MEM-06). */
    private suspend fun encodePng(image: ImageBitmap): ByteArray = withContext(dispatchers.default) {
        val software = requireNotNull(image.asAndroidBitmap().copy(Bitmap.Config.ARGB_8888, false)) {
            "Card bitmap copy failed"
        }
        try {
            ByteArrayOutputStream().use { out ->
                software.compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, out)
                out.toByteArray()
            }
        } finally {
            software.recycle()
        }
    }

    private suspend fun refreshLine() {
        val data = currentState.data ?: return
        val isHidden = currentState.isEndsHidden
        val (line, trail) = withContext(dispatchers.default) {
            ShareCardLine.of(data.line, isHidden) to
                ShareCardLine.trimmed(data.line, isHidden).map { MapPoint(it.latDeg, it.lonDeg) }
        }
        updateState { copy(line = line.toImmutableList(), trail = trail.toImmutableList()) }
    }
}

/** Statistics with data: heart rate and power only when measured (estimated power never shows, §11). */
private fun available(totals: RideTotals): List<ShareStat> = ShareStat.entries.filter { stat ->
    when (stat) {
        ShareStat.DISTANCE, ShareStat.TIME, ShareStat.ASCENT, ShareStat.AVG_SPEED -> true
        ShareStat.AVG_HEART_RATE -> totals.avgHeartRateBpm != null
        ShareStat.AVG_POWER -> totals.avgPowerW != null
        ShareStat.CALORIES -> totals.calories != null
    }
}

/** The owner's references first (map, then the transparent stickers), then the earlier styles (T10.6). */
private val GALLERY_ORDER = listOf(
    ShareTemplate.MAP,
    ShareTemplate.DATE_TICKET,
    ShareTemplate.ROUTE_SIDE_STATS,
    ShareTemplate.SIDE_STATS,
    ShareTemplate.ROUTE_DOTTED_LIST,
    ShareTemplate.DOTTED_LIST,
    ShareTemplate.CENTER_STACK,
    ShareTemplate.ROUTE_UNDER_STACK,
    ShareTemplate.PILLS,
    ShareTemplate.STAT_ROW,
    ShareTemplate.ROUTE_BESIDE_STATS,
    ShareTemplate.STAT_GRID,
    ShareTemplate.ROUTE_OVER_ROW,
    ShareTemplate.BIG_STATS,
    ShareTemplate.ROUTE_LOGO,
    ShareTemplate.STATS_SMALL_ROUTE,
    ShareTemplate.MAP_FRAMED,
    ShareTemplate.POSTER,
    ShareTemplate.PHOTO,
    ShareTemplate.STICKER,
    ShareTemplate.CLASSIC,
)

/** PNG is lossless; the value is ignored by the encoder but required by the API. */
private const val PNG_QUALITY = 100

/** Details with data: zones only with heart rate, badges only with a record. */
private fun availableDetails(details: ShareDetails): List<ShareDetail> = ShareDetail.entries.filter { detail ->
    when (detail) {
        ShareDetail.ELEVATION -> details.elevationM.isNotEmpty()
        ShareDetail.HEART_RATE_ZONES -> details.zonesS.isNotEmpty()
        // One bar alone says nothing: splits from 2 km.
        ShareDetail.SPLITS -> details.splitsS.size >= MIN_SPLITS
        ShareDetail.BADGES -> details.records + details.segmentRecords > 0
    }
}

private fun toggled(current: List<ShareStat>, stat: ShareStat): List<ShareStat> = when {
    stat in current -> current - stat
    current.size >= ShareCardConfig.MAX_STATS -> current
    else -> ShareStat.entries.filter { it in current || it == stat }
}

private const val MIN_SPLITS = 2
