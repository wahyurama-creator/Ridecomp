package com.ridecomp.feature.history.viewmodel

import androidx.compose.runtime.Immutable
import androidx.lifecycle.viewModelScope
import com.ridecomp.core.config.Units
import com.ridecomp.core.model.Activity
import com.ridecomp.core.model.Lap
import com.ridecomp.core.model.UnitSystem
import com.ridecomp.core.source.DispatcherProvider
import com.ridecomp.core.usecase.ChartPoint
import com.ridecomp.core.usecase.DeleteActivityUseCase
import com.ridecomp.core.usecase.ExportFormat
import com.ridecomp.core.usecase.GetLapsUseCase
import com.ridecomp.core.usecase.LoadRideChartUseCase
import com.ridecomp.core.usecase.ObserveActivityUseCase
import com.ridecomp.core.usecase.ObserveRideSettingsUseCase
import com.ridecomp.core.usecase.SaveActivityFileUseCase
import com.ridecomp.core.usecase.ShareActivityFileUseCase
import com.ridecomp.core.usecase.SuggestExportFileNameUseCase
import com.ridecomp.core.util.Outcome
import com.ridecomp.ui.component.ChartValue
import com.ridecomp.ui.udf.UdfViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Outcome message of the last file action, shown once. */
enum class FileMessage { EXPORT_FAILED, SAVED }

@Immutable
data class ActivityDetailUiState(
    val activity: Activity? = null,
    val isMissing: Boolean = false,
    val laps: ImmutableList<Lap> = persistentListOf(),
    /** Charts vs distance (km/mi) in display units, downsampled ≤ ~1000 points; only those with data. */
    val charts: ImmutableList<RideChart> = persistentListOf(),
    val unitSystem: UnitSystem = UnitSystem.METRIC,
    val isWorking: Boolean = false,
    val message: FileMessage? = null,
)

sealed interface ActivityDetailUiEvent {
    data class Share(val format: ExportFormat) : ActivityDetailUiEvent

    /** "Save to Downloads": the document was picked via SAF. */
    data class SaveTo(val format: ExportFormat, val destinationUri: String) : ActivityDetailUiEvent

    data object Delete : ActivityDetailUiEvent

    data object MessageShown : ActivityDetailUiEvent
}

sealed interface ActivityDetailEffect {
    data object Deleted : ActivityDetailEffect
}

/** Activity detail (T1.13): totals, charts, laps; export/share FIT & GPX (T1.14); delete. */
/** Use cases of the detail screen, grouped to keep the constructor short (KT-05). */
data class ActivityDetailUseCases(
    val observeActivity: ObserveActivityUseCase,
    val getLaps: GetLapsUseCase,
    val loadChart: LoadRideChartUseCase,
    val observeSettings: ObserveRideSettingsUseCase,
    val share: ShareActivityFileUseCase,
    val save: SaveActivityFileUseCase,
    val suggestName: SuggestExportFileNameUseCase,
    val delete: DeleteActivityUseCase,
)

class ActivityDetailViewModel(
    private val activityId: Long,
    useCases: ActivityDetailUseCases,
    private val dispatchers: DispatcherProvider,
) : UdfViewModel<ActivityDetailUiState, ActivityDetailUiEvent, ActivityDetailEffect>(ActivityDetailUiState()) {
    private val observeActivity = useCases.observeActivity
    private val getLaps = useCases.getLaps
    private val loadChart = useCases.loadChart
    private val share = useCases.share
    private val save = useCases.save
    private val suggestName = useCases.suggestName
    private val delete = useCases.delete

    init {
        viewModelScope.launch {
            observeActivity(activityId).collect { activity ->
                updateState { copy(activity = activity, isMissing = activity == null) }
            }
        }
        viewModelScope.launch {
            useCases.observeSettings().collect { settings ->
                updateState { copy(unitSystem = settings.unitSystem) }
                loadDetails(settings.unitSystem)
            }
        }
    }

    /** File name to propose in the SAF dialog. */
    suspend fun suggestedFileName(format: ExportFormat): String? = suggestName(activityId, format)

    override fun onEvent(event: ActivityDetailUiEvent) {
        when (event) {
            is ActivityDetailUiEvent.Share -> runFileAction(successMessage = null) { share(activityId, event.format) }
            is ActivityDetailUiEvent.SaveTo -> runFileAction(FileMessage.SAVED) {
                save(activityId, event.format, event.destinationUri)
            }
            ActivityDetailUiEvent.Delete -> viewModelScope.launch {
                delete(activityId)
                sendEffect(ActivityDetailEffect.Deleted)
            }
            ActivityDetailUiEvent.MessageShown -> updateState { copy(message = null) }
        }
    }

    private suspend fun loadDetails(unitSystem: UnitSystem) {
        val laps = getLaps(activityId).toImmutableList()
        // Downsampling and conversion are CPU work: off the main thread (02-DESIGN §6).
        val charts = withContext(dispatchers.default) { loadChart(activityId).toCharts(unitSystem) }
        updateState { copy(laps = laps, charts = charts) }
    }

    private fun runFileAction(successMessage: FileMessage?, action: suspend () -> Outcome<*, *>) {
        updateState { copy(isWorking = true) }
        viewModelScope.launch {
            val message = when (action()) {
                is Outcome.Success -> successMessage
                is Outcome.Failure -> FileMessage.EXPORT_FAILED
            }
            updateState { copy(isWorking = false, message = message) }
        }
    }
}

/** Charts of the detail screen (02-DESIGN §3.7, T2.11 adds the sensor charts). */
enum class ChartKind { SPEED, ELEVATION, HEART_RATE, CADENCE, POWER }

@Immutable
data class RideChart(val kind: ChartKind, val values: ImmutableList<ChartValue>)

/** Converts samples to display-unit charts; a chart without at least two values is left out. */
internal fun List<ChartPoint>.toCharts(unitSystem: UnitSystem): ImmutableList<RideChart> {
    val isMetric = unitSystem == UnitSystem.METRIC
    val distanceDivisor = if (isMetric) Units.METERS_PER_KM else Units.METERS_PER_MILE
    val speedFactor = if (isMetric) Units.KMH_PER_MPS else Units.MPH_PER_MPS
    val altitudeDivisor = if (isMetric) 1.0 else Units.METERS_PER_FOOT
    return ChartKind.entries.mapNotNull { kind ->
        val values = map { point ->
            val y = when (kind) {
                ChartKind.SPEED -> point.speedMps?.times(speedFactor)
                ChartKind.ELEVATION -> point.altitudeM?.div(altitudeDivisor)
                ChartKind.HEART_RATE -> point.heartRateBpm?.toDouble()
                ChartKind.CADENCE -> point.cadenceRpm?.toDouble()
                ChartKind.POWER -> point.powerW?.toDouble()
            }
            ChartValue(point.distanceM / distanceDivisor, y)
        }
        values.takeIf { list -> list.count { it.y != null } >= MIN_CHART_VALUES }
            ?.let { RideChart(kind, it.toImmutableList()) }
    }.toImmutableList()
}

/** A line needs two points. */
private const val MIN_CHART_VALUES = 2
