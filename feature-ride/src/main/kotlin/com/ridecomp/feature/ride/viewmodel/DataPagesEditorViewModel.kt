package com.ridecomp.feature.ride.viewmodel

import androidx.compose.runtime.Immutable
import androidx.lifecycle.viewModelScope
import com.ridecomp.core.model.DataPageLayout
import com.ridecomp.core.model.DataPageLimits
import com.ridecomp.core.model.DataPagesLayout
import com.ridecomp.core.model.withFieldCount
import com.ridecomp.core.usecase.ObserveDataPagesUseCase
import com.ridecomp.core.usecase.ResetDataPagesUseCase
import com.ridecomp.core.usecase.SaveDataPagesUseCase
import com.ridecomp.feature.ride.model.DefaultDataPages
import com.ridecomp.feature.ride.model.RideMetric
import com.ridecomp.feature.ride.model.RideMetricKeys
import com.ridecomp.feature.ride.model.toLayout
import com.ridecomp.ui.udf.UdfViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@Immutable
data class DataPagesEditorUiState(
    val pages: ImmutableList<DataPageLayout> = persistentListOf(),
    val selectedPage: Int = 0,
    /** Field whose metric is being chosen, or null when the picker is closed. */
    val pickingField: Int? = null,
    val isLoaded: Boolean = false,
) {
    val page: DataPageLayout? get() = pages.getOrNull(selectedPage)
    val canAddPage: Boolean get() = pages.size < DataPageLimits.MAX_PAGES
    val canRemovePage: Boolean get() = pages.size > 1
}

sealed interface DataPagesEditorUiEvent {
    data class SelectPage(val index: Int) : DataPagesEditorUiEvent

    data object AddPage : DataPagesEditorUiEvent

    data object RemovePage : DataPagesEditorUiEvent

    data class SetFieldCount(val count: Int) : DataPagesEditorUiEvent

    data class SetFirstFullWidth(val isFullWidth: Boolean) : DataPagesEditorUiEvent

    data class PickField(val index: Int) : DataPagesEditorUiEvent

    data class ChooseMetric(val metric: RideMetric) : DataPagesEditorUiEvent

    data object DismissPicker : DataPagesEditorUiEvent

    data object Save : DataPagesEditorUiEvent

    data object ResetToDefaults : DataPagesEditorUiEvent
}

sealed interface DataPagesEditorEffect {
    data object Saved : DataPagesEditorEffect
}

/**
 * Data page editor (T2.8, 02-DESIGN §3.10): add/remove pages, choose 1–8 fields per page and the metric
 * of each field. Edits stay local until saved.
 */
class DataPagesEditorViewModel(
    /** Edits this profile's own pages, or the general pages when null (T2.13). */
    private val profileId: Long?,
    private val observeDataPages: ObserveDataPagesUseCase,
    private val saveDataPages: SaveDataPagesUseCase,
    private val resetDataPages: ResetDataPagesUseCase,
) : UdfViewModel<DataPagesEditorUiState, DataPagesEditorUiEvent, DataPagesEditorEffect>(DataPagesEditorUiState()) {
    init {
        viewModelScope.launch {
            val stored = observeDataPages(RideMetricKeys.ALL, profileId).first() ?: DefaultDataPages.ALL.toLayout()
            updateState { copy(pages = stored.pages.toImmutableList(), isLoaded = true) }
        }
    }

    override fun onEvent(event: DataPagesEditorUiEvent) {
        when (event) {
            is DataPagesEditorUiEvent.SelectPage ->
                updateState { copy(selectedPage = event.index.coerceIn(0, pages.lastIndex), pickingField = null) }
            DataPagesEditorUiEvent.AddPage -> addPage()
            DataPagesEditorUiEvent.RemovePage -> removePage()
            is DataPagesEditorUiEvent.SetFieldCount ->
                editPage { withFieldCount(event.count, FILLER_KEYS) }
            is DataPagesEditorUiEvent.SetFirstFullWidth -> editPage { copy(isFirstFullWidth = event.isFullWidth) }
            is DataPagesEditorUiEvent.PickField -> updateState { copy(pickingField = event.index) }
            is DataPagesEditorUiEvent.ChooseMetric -> chooseMetric(event.metric)
            DataPagesEditorUiEvent.DismissPicker -> updateState { copy(pickingField = null) }
            DataPagesEditorUiEvent.Save -> save()
            DataPagesEditorUiEvent.ResetToDefaults -> reset()
        }
    }

    private fun addPage() {
        if (!currentState.canAddPage) return
        updateState { copy(pages = (pages + DefaultDataPages.NEW_PAGE).toImmutableList(), selectedPage = pages.size) }
    }

    private fun removePage() {
        if (!currentState.canRemovePage) return
        updateState {
            val remaining = pages.filterIndexed { index, _ -> index != selectedPage }
            copy(pages = remaining.toImmutableList(), selectedPage = selectedPage.coerceAtMost(remaining.lastIndex))
        }
    }

    private fun chooseMetric(metric: RideMetric) {
        val field = currentState.pickingField ?: return
        editPage { copy(fields = fields.mapIndexed { index, key -> if (index == field) metric.key else key }) }
        updateState { copy(pickingField = null) }
    }

    private fun editPage(edit: DataPageLayout.() -> DataPageLayout) {
        updateState {
            copy(
                pages = pages.mapIndexed { index, page ->
                    if (index ==
                        selectedPage
                    ) {
                        page.edit()
                    } else {
                        page
                    }
                }.toImmutableList(),
            )
        }
    }

    private fun save() {
        viewModelScope.launch {
            saveDataPages(DataPagesLayout(currentState.pages), RideMetricKeys.ALL, profileId)
            sendEffect(DataPagesEditorEffect.Saved)
        }
    }

    private fun reset() {
        viewModelScope.launch {
            resetDataPages(profileId)
            // A profile falls back to the general pages, the general pages to the built-in defaults.
            val restored = observeDataPages(RideMetricKeys.ALL, profileId).first() ?: DefaultDataPages.ALL.toLayout()
            updateState { copy(pages = restored.pages.toImmutableList(), selectedPage = 0, pickingField = null) }
        }
    }

    private companion object {
        /** Metrics used to fill new fields, most useful first. */
        val FILLER_KEYS: List<String> = DefaultDataPages.MAIN.metrics.map { it.key } + RideMetric.entries.map { it.key }
    }
}
