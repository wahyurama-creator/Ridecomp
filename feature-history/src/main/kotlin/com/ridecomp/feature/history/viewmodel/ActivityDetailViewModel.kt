package com.ridecomp.feature.history.viewmodel

import androidx.compose.runtime.Immutable
import androidx.lifecycle.viewModelScope
import com.ridecomp.core.config.Units
import com.ridecomp.core.model.Activity
import com.ridecomp.core.model.Lap
import com.ridecomp.core.model.StravaStatus
import com.ridecomp.core.model.StravaUpload
import com.ridecomp.core.model.UnitSystem
import com.ridecomp.core.source.DispatcherProvider
import com.ridecomp.core.usecase.ActivityAnalysis
import com.ridecomp.core.usecase.AnalysisSectionKind
import com.ridecomp.core.usecase.AnalysisStatKind
import com.ridecomp.core.usecase.ChartPoint
import com.ridecomp.core.usecase.DeleteActivityUseCase
import com.ridecomp.core.usecase.ExportFormat
import com.ridecomp.core.usecase.GetLapsUseCase
import com.ridecomp.core.usecase.LoadActivityAnalysisUseCase
import com.ridecomp.core.usecase.ObserveActivityUseCase
import com.ridecomp.core.usecase.ObserveMapTilesUseCase
import com.ridecomp.core.usecase.ObserveRideSettingsUseCase
import com.ridecomp.core.usecase.ObserveStravaStatusUseCase
import com.ridecomp.core.usecase.ObserveStravaUploadUseCase
import com.ridecomp.core.usecase.QueueStravaUploadUseCase
import com.ridecomp.core.usecase.SaveActivityFileUseCase
import com.ridecomp.core.usecase.ShareActivityFileUseCase
import com.ridecomp.core.usecase.SuggestExportFileNameUseCase
import com.ridecomp.core.usecase.isDuration
import com.ridecomp.core.usecase.valueOf
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
    /** Sections in page order, charts vs distance (km/mi) in display units, ≤ 300 points; only those with data. */
    val sections: ImmutableList<DetailSection> = persistentListOf(),
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
    val loadAnalysis: LoadActivityAnalysisUseCase,
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
    private val loadAnalysis = useCases.loadAnalysis
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
        // Downsampling, section numbers and conversion are CPU work: off the main thread (02-DESIGN §6).
        // The ride's own totals (moving average, max) feed the sections, so both show the same numbers.
        val totals = observeActivity(activityId).first()?.totals
        val (sections, route) = withContext(dispatchers.default) {
            val analysis = loadAnalysis(activityId, totals)
            analysis.toSections(unitSystem) to analysis.points.toRoute()
        }
        updateState { copy(laps = laps, sections = sections, route = route) }
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

/** A number of a section, in display units (times stay in seconds). */
@Immutable
data class SectionStat(val kind: AnalysisStatKind, val value: Double)

/** A section of the detail page (T11.2): its chart and its numbers, in display units. */
@Immutable
data class DetailSection(
    val kind: AnalysisSectionKind,
    val values: ImmutableList<ChartValue>,
    val stats: ImmutableList<SectionStat> = persistentListOf(),
)

/** Converts the analysis to display units: km or mi along, km/h or mph, m or ft. */
internal fun ActivityAnalysis.toSections(unitSystem: UnitSystem): ImmutableList<DetailSection> {
    val isMetric = unitSystem == UnitSystem.METRIC
    val distanceDivisor = if (isMetric) Units.METERS_PER_KM else Units.METERS_PER_MILE
    val speedFactor = if (isMetric) Units.KMH_PER_MPS else Units.MPH_PER_MPS
    val altitudeFactor = if (isMetric) 1.0 else 1.0 / Units.METERS_PER_FOOT
    return sections.map { section ->
        val factor = when (section.kind) {
            AnalysisSectionKind.SPEED -> speedFactor
            AnalysisSectionKind.ELEVATION -> altitudeFactor
            AnalysisSectionKind.HEART_RATE, AnalysisSectionKind.POWER, AnalysisSectionKind.CADENCE -> 1.0
        }
        DetailSection(
            kind = section.kind,
            values = points.map { point ->
                ChartValue(point.distanceM / distanceDivisor, section.kind.valueOf(point)?.times(factor))
            }.toImmutableList(),
            stats = section.stats.map {
                SectionStat(it.kind, if (it.kind.isDuration) it.value else it.value * factor)
            }.toImmutableList(),
        )
    }.toImmutableList()
}

/** Points with a GPS fix, for the route sketch. */
internal fun List<ChartPoint>.toRoute(): ImmutableList<MapPoint> = mapNotNull { point ->
    val lat = point.latDeg
    val lon = point.lonDeg
    if (lat != null && lon != null) MapPoint(lat, lon) else null
}.toImmutableList()

/** Keeps the network watch across quick configuration changes only (CO-07). */
private const val MAP_STOP_TIMEOUT_MS = 5_000L
