package com.ridecomp.feature.history.viewmodel

import androidx.compose.runtime.Immutable
import androidx.lifecycle.viewModelScope
import com.ridecomp.core.config.Units
import com.ridecomp.core.model.Activity
import com.ridecomp.core.model.Lap
import com.ridecomp.core.model.RideTotals
import com.ridecomp.core.model.StravaStatus
import com.ridecomp.core.model.StravaUpload
import com.ridecomp.core.model.UnitSystem
import com.ridecomp.core.source.DispatcherProvider
import com.ridecomp.core.usecase.ChartPoint
import com.ridecomp.core.usecase.DeleteActivityUseCase
import com.ridecomp.core.usecase.ExportFormat
import com.ridecomp.core.usecase.GetLapsUseCase
import com.ridecomp.core.usecase.LoadRideChartUseCase
import com.ridecomp.core.usecase.ObserveActivityUseCase
import com.ridecomp.core.usecase.ObserveMapTilesUseCase
import com.ridecomp.core.usecase.ObserveRideSettingsUseCase
import com.ridecomp.core.usecase.ObserveStravaStatusUseCase
import com.ridecomp.core.usecase.ObserveStravaUploadUseCase
import com.ridecomp.core.usecase.QueueStravaUploadUseCase
import com.ridecomp.core.usecase.SaveActivityFileUseCase
import com.ridecomp.core.usecase.ShareActivityFileUseCase
import com.ridecomp.core.usecase.SuggestExportFileNameUseCase
import com.ridecomp.core.util.Outcome
import com.ridecomp.ui.component.ChartValue
import com.ridecomp.ui.map.MapPoint
import com.ridecomp.ui.udf.UdfViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
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
    val charts: ImmutableList<DetailChart> = persistentListOf(),
    /** The ride's line for the route sketch (same downsampling as the charts); empty without GPS. */
    val route: ImmutableList<MapPoint> = persistentListOf(),
    val unitSystem: UnitSystem = UnitSystem.METRIC,
    val isWorking: Boolean = false,
    val message: FileMessage? = null,
    /** Null while loading; the upload row needs a connected account (T5.3). */
    val stravaStatus: StravaStatus? = null,
    val stravaUpload: StravaUpload? = null,
)

sealed interface ActivityDetailUiEvent {
    data class Share(val format: ExportFormat) : ActivityDetailUiEvent

    /** "Save to Downloads": the document was picked via SAF. */
    data class SaveTo(val format: ExportFormat, val destinationUri: String) : ActivityDetailUiEvent

    data object Delete : ActivityDetailUiEvent

    /** Queue (or retry) the upload to Strava. */
    data object UploadToStrava : ActivityDetailUiEvent

    data object MessageShown : ActivityDetailUiEvent
}

sealed interface ActivityDetailEffect {
    data object Deleted : ActivityDetailEffect
}

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
    val observeMapTiles: ObserveMapTilesUseCase,
)

/** Activity detail (T1.13): totals, charts, laps; export/share FIT & GPX (T1.14); delete. */
/** Strava upload of the ride (T5.3). */
data class ActivityStravaUseCases(
    val observeStatus: ObserveStravaStatusUseCase,
    val observeUpload: ObserveStravaUploadUseCase,
    val queueUpload: QueueStravaUploadUseCase,
)

class ActivityDetailViewModel(
    private val activityId: Long,
    useCases: ActivityDetailUseCases,
    private val strava: ActivityStravaUseCases,
    private val dispatchers: DispatcherProvider,
) : UdfViewModel<ActivityDetailUiState, ActivityDetailUiEvent, ActivityDetailEffect>(ActivityDetailUiState()) {
    /** The track on the online map while there is internet, else the sketch (D-90); watched only while shown. */
    val isOnlineMap: StateFlow<Boolean> = useCases.observeMapTiles()
        .map { it.isOnline }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(MAP_STOP_TIMEOUT_MS), false)

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
            strava.observeStatus().collect { status -> updateState { copy(stravaStatus = status) } }
        }
        viewModelScope.launch {
            strava.observeUpload(activityId).collect { upload -> updateState { copy(stravaUpload = upload) } }
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
            ActivityDetailUiEvent.UploadToStrava -> viewModelScope.launch { strava.queueUpload(activityId) }
        }
    }

    private suspend fun loadDetails(unitSystem: UnitSystem) {
        val laps = getLaps(activityId).toImmutableList()
        // Downsampling and conversion are CPU work: off the main thread (02-DESIGN §6).
        // The ride's own totals (moving average, max) head the charts, so both show the same numbers.
        val totals = observeActivity(activityId).first()?.totals
        val (charts, route) = withContext(dispatchers.default) {
            val points = loadChart(activityId)
            points.toCharts(unitSystem, totals) to points.toRoute()
        }
        updateState { copy(laps = laps, charts = charts, route = route) }
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

/** A summary number shown above a chart. */
enum class ChartStatKind { AVG, MAX, MIN }

@Immutable
data class ChartStat(val kind: ChartStatKind, val value: Double)

@Immutable
data class DetailChart(
    val kind: ChartKind,
    val values: ImmutableList<ChartValue>,
    val stats: ImmutableList<ChartStat> = persistentListOf(),
)

/** Converts samples to display-unit charts; a chart without at least two values is left out. */
internal fun List<ChartPoint>.toCharts(unitSystem: UnitSystem, totals: RideTotals? = null): ImmutableList<DetailChart> {
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
            ?.let {
                DetailChart(kind, it.toImmutableList(), kind.stats(it.mapNotNull(ChartValue::y), totals, speedFactor))
            }
    }.toImmutableList()
}

/**
 * Summary of a chart in display units: elevation shows its range; the others their average and maximum,
 * taken from the ride totals when it has them (the chart is downsampled), else from the chart. Cadence
 * averages only while pedaling (zeros left out).
 */
private fun ChartKind.stats(ys: List<Double>, totals: RideTotals?, speedFactor: Double): ImmutableList<ChartStat> {
    fun pair(avg: Double?, max: Double?) = listOfNotNull(
        avg?.let { ChartStat(ChartStatKind.AVG, it) },
        ChartStat(ChartStatKind.MAX, max ?: ys.max()),
    ).toImmutableList()
    return when (this) {
        ChartKind.ELEVATION ->
            persistentListOf(ChartStat(ChartStatKind.MIN, ys.min()), ChartStat(ChartStatKind.MAX, ys.max()))
        ChartKind.SPEED -> pair(
            avg = totals?.avgSpeedMps?.takeIf { it > 0 }?.times(speedFactor) ?: ys.filter { it > 0 }.averageOrNull(),
            max = totals?.maxSpeedMps?.takeIf { it > 0 }?.times(speedFactor),
        )
        ChartKind.HEART_RATE -> pair(
            avg = totals?.avgHeartRateBpm?.toDouble() ?: ys.average(),
            max = totals?.maxHeartRateBpm?.toDouble(),
        )
        ChartKind.CADENCE -> pair(
            avg = totals?.avgCadenceRpm?.toDouble() ?: ys.filter { it > 0 }.averageOrNull(),
            max = null,
        )
        ChartKind.POWER -> pair(
            avg = totals?.avgPowerW?.toDouble() ?: ys.average(),
            max = totals?.maxPowerW?.toDouble(),
        )
    }
}

private fun List<Double>.averageOrNull(): Double? = takeIf { it.isNotEmpty() }?.average()

/** Points with a GPS fix, for the route sketch. */
internal fun List<ChartPoint>.toRoute(): ImmutableList<MapPoint> = mapNotNull { point ->
    val lat = point.latDeg
    val lon = point.lonDeg
    if (lat != null && lon != null) MapPoint(lat, lon) else null
}.toImmutableList()

/** A line needs two points. */
private const val MIN_CHART_VALUES = 2

/** Keeps the network watch across quick configuration changes only (CO-07). */
private const val MAP_STOP_TIMEOUT_MS = 5_000L
