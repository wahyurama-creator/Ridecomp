package com.ridecomp.feature.training.viewmodel

import android.graphics.Bitmap
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.lifecycle.viewModelScope
import com.ridecomp.core.engine.share.ShareCardConfig
import com.ridecomp.core.engine.share.ShareCardLine
import com.ridecomp.core.engine.share.ShareFormat
import com.ridecomp.core.engine.share.SharePoint
import com.ridecomp.core.engine.share.ShareStat
import com.ridecomp.core.model.RideTotals
import com.ridecomp.core.model.UnitSystem
import com.ridecomp.core.source.DispatcherProvider
import com.ridecomp.core.usecase.LoadShareCardUseCase
import com.ridecomp.core.usecase.ObserveRideSettingsUseCase
import com.ridecomp.core.usecase.ShareCardData
import com.ridecomp.core.usecase.ShareCardImageUseCase
import com.ridecomp.core.util.runCatchingCancellable
import com.ridecomp.ui.udf.NoEffect
import com.ridecomp.ui.udf.UdfViewModel
import java.io.ByteArrayOutputStream
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Immutable
data class ShareCardUiState(
    val data: ShareCardData? = null,
    val format: ShareFormat = ShareFormat.STORY,
    val isDark: Boolean = true,
    /** Privacy (§11): first and last 500 m hidden, on by default. */
    val isEndsHidden: Boolean = true,
    val isTrackShown: Boolean = true,
    val stats: ImmutableList<ShareStat> = persistentListOf(),
    /** The line as drawn, already trimmed and projected. */
    val line: ImmutableList<SharePoint> = persistentListOf(),
    val unitSystem: UnitSystem = UnitSystem.METRIC,
    val isSharing: Boolean = false,
    /** The last share failed (out of memory, no app to share with…). */
    val isShareFailed: Boolean = false,
    val isMissing: Boolean = false,
) {
    /** Statistics this ride has data for, in display order. */
    val availableStats: ImmutableList<ShareStat>
        get() = data?.activity?.totals?.let(::available).orEmpty().toImmutableList()
}

sealed interface ShareCardUiEvent {
    data class SetFormat(val format: ShareFormat) : ShareCardUiEvent

    data class SetDark(val isDark: Boolean) : ShareCardUiEvent

    data class SetEndsHidden(val isHidden: Boolean) : ShareCardUiEvent

    data class SetTrackShown(val isShown: Boolean) : ShareCardUiEvent

    /** Adds or removes a statistic (at most 4). */
    data class ToggleStat(val stat: ShareStat) : ShareCardUiEvent

    /** "Bagikan": the card is drawn at full size, then [Captured]. */
    data object Rendering : ShareCardUiEvent

    /** The card drawn at its real pixel size. */
    data class Captured(val image: ImageBitmap) : ShareCardUiEvent
}

/** Share card editor (T4.5, 13-UI-SCREENS #21, 12-FEATURE-SPECS §11). */
class ShareCardViewModel(
    activityId: Long,
    load: LoadShareCardUseCase,
    observeSettings: ObserveRideSettingsUseCase,
    private val share: ShareCardImageUseCase,
    private val dispatchers: DispatcherProvider,
) : UdfViewModel<ShareCardUiState, ShareCardUiEvent, NoEffect>(ShareCardUiState()) {
    init {
        viewModelScope.launch {
            val data = load(activityId)
            if (data == null) {
                updateState { copy(isMissing = true) }
                return@launch
            }
            val stats = available(data.activity.totals).take(ShareCardConfig.MAX_STATS)
            updateState { copy(data = data, stats = stats.toImmutableList()) }
            refreshLine()
        }
        viewModelScope.launch {
            observeSettings().collect { settings -> updateState { copy(unitSystem = settings.unitSystem) } }
        }
    }

    override fun onEvent(event: ShareCardUiEvent) {
        // The card is being captured: options stay as they are so the image matches the preview.
        if (currentState.isSharing && event !is ShareCardUiEvent.Captured) return
        when (event) {
            is ShareCardUiEvent.SetFormat -> updateState { copy(format = event.format) }
            is ShareCardUiEvent.SetDark -> updateState { copy(isDark = event.isDark) }
            is ShareCardUiEvent.SetEndsHidden -> {
                updateState { copy(isEndsHidden = event.isHidden) }
                viewModelScope.launch { refreshLine() }
            }
            is ShareCardUiEvent.SetTrackShown -> updateState { copy(isTrackShown = event.isShown) }
            is ShareCardUiEvent.ToggleStat -> updateState { copy(stats = toggled(stats, event.stat).toImmutableList()) }
            ShareCardUiEvent.Rendering -> updateState { copy(isSharing = true, isShareFailed = false) }
            is ShareCardUiEvent.Captured -> if (currentState.isSharing) shareCard(event.image)
        }
    }

    /** A failure (memory, storage, no app to share with) is shown, never a crash; the button comes back. */
    private fun shareCard(image: ImageBitmap) {
        viewModelScope.launch {
            val result = runCatchingCancellable { share(encodePng(image)) }
            updateState { copy(isSharing = false, isShareFailed = result.isFailure) }
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
        val line = withContext(dispatchers.default) { ShareCardLine.of(data.line, isHidden) }
        updateState { copy(line = line.toImmutableList()) }
    }

    private fun toggled(current: List<ShareStat>, stat: ShareStat): List<ShareStat> = when {
        stat in current -> current - stat
        current.size >= ShareCardConfig.MAX_STATS -> current
        else -> ShareStat.entries.filter { it in current || it == stat }
    }
}

/** Statistics with data: heart rate and power only when measured (estimated power never shows, §11). */
private fun available(totals: RideTotals): List<ShareStat> = ShareStat.entries.filter { stat ->
    when (stat) {
        ShareStat.DISTANCE, ShareStat.TIME, ShareStat.ASCENT, ShareStat.AVG_SPEED -> true
        ShareStat.AVG_HEART_RATE -> totals.avgHeartRateBpm != null
        ShareStat.AVG_POWER -> totals.avgPowerW != null
    }
}

/** PNG is lossless; the value is ignored by the encoder but required by the API. */
private const val PNG_QUALITY = 100
