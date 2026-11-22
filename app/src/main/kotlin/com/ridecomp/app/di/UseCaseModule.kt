package com.ridecomp.app.di

import com.ridecomp.core.source.DispatcherProvider
import com.ridecomp.core.usecase.AddEmergencyContactUseCase
import com.ridecomp.core.usecase.AddPickedContactUseCase
import com.ridecomp.core.usecase.AnalysisRequests
import com.ridecomp.core.usecase.AnalyzeActivitiesUseCase
import com.ridecomp.core.usecase.AuthorizedStravaApi
import com.ridecomp.core.usecase.BackupWriter
import com.ridecomp.core.usecase.BestEffortsStep
import com.ridecomp.core.usecase.CheckForUpdateUseCase
import com.ridecomp.core.usecase.CheckGpsAvailabilityUseCase
import com.ridecomp.core.usecase.ClearMapCacheUseCase
import com.ridecomp.core.usecase.ClearShareCardsUseCase
import com.ridecomp.core.usecase.CompleteOnboardingUseCase
import com.ridecomp.core.usecase.CompleteStravaAuthUseCase
import com.ridecomp.core.usecase.CreateBackupUseCase
import com.ridecomp.core.usecase.CreateSegmentFromActivityUseCase
import com.ridecomp.core.usecase.CreateSegmentFromClimbUseCase
import com.ridecomp.core.usecase.DeleteActivityProfileUseCase
import com.ridecomp.core.usecase.DeleteActivityUseCase
import com.ridecomp.core.usecase.DeleteBikeUseCase
import com.ridecomp.core.usecase.DeleteComponentUseCase
import com.ridecomp.core.usecase.DeleteCustomPlanUseCase
import com.ridecomp.core.usecase.DeleteMapRegionUseCase
import com.ridecomp.core.usecase.DeleteOfflineMapUseCase
import com.ridecomp.core.usecase.DeleteRouteUseCase
import com.ridecomp.core.usecase.DeleteSegmentUseCase
import com.ridecomp.core.usecase.DisconnectStravaUseCase
import com.ridecomp.core.usecase.DismissMapRegionOfferUseCase
import com.ridecomp.core.usecase.DismissTipUseCase
import com.ridecomp.core.usecase.DismissUpdateUseCase
import com.ridecomp.core.usecase.DuplicateActivityProfileUseCase
import com.ridecomp.core.usecase.EstimateFtpUseCase
import com.ridecomp.core.usecase.EstimateHrMaxUseCase
import com.ridecomp.core.usecase.EvaluateBestEffortsUseCase
import com.ridecomp.core.usecase.ExportActivityUseCase
import com.ridecomp.core.usecase.ExportFileStore
import com.ridecomp.core.usecase.ExportFitUseCase
import com.ridecomp.core.usecase.ExportGpxUseCase
import com.ridecomp.core.usecase.ExportedFileHandler
import com.ridecomp.core.usecase.ExtendTrainingToTodayUseCase
import com.ridecomp.core.usecase.ForgetSensorUseCase
import com.ridecomp.core.usecase.GetActivityKindUseCase
import com.ridecomp.core.usecase.GetAppLanguageUseCase
import com.ridecomp.core.usecase.GetBikeUseCase
import com.ridecomp.core.usecase.GetComponentUseCase
import com.ridecomp.core.usecase.GetCustomPlanUseCase
import com.ridecomp.core.usecase.GetFreeStorageUseCase
import com.ridecomp.core.usecase.GetLapsUseCase
import com.ridecomp.core.usecase.GetLiveSegmentsUseCase
import com.ridecomp.core.usecase.GetLoadInputsUseCase
import com.ridecomp.core.usecase.GetMapCacheInfoUseCase
import com.ridecomp.core.usecase.GetRecoverableRideUseCase
import com.ridecomp.core.usecase.GetRideProfileUseCase
import com.ridecomp.core.usecase.GetRouteGuidanceUseCase
import com.ridecomp.core.usecase.GetRoutePointsUseCase
import com.ridecomp.core.usecase.GetSelectedBikeUseCase
import com.ridecomp.core.usecase.GetShareTargetsUseCase
import com.ridecomp.core.usecase.GetStravaAccessTokenUseCase
import com.ridecomp.core.usecase.HandleRecoveredRideUseCase
import com.ridecomp.core.usecase.HasBarometerUseCase
import com.ridecomp.core.usecase.HasProfileUseCase
import com.ridecomp.core.usecase.ImportGpxUseCase
import com.ridecomp.core.usecase.ImportOfflineMapUseCase
import com.ridecomp.core.usecase.LoadActivityAnalysisUseCase
import com.ridecomp.core.usecase.LoadMapRegionIndexUseCase
import com.ridecomp.core.usecase.LoadRideChartUseCase
import com.ridecomp.core.usecase.LoadRideTrailUseCase
import com.ridecomp.core.usecase.LoadShareCardUseCase
import com.ridecomp.core.usecase.LoadTrackOutlineUseCase
import com.ridecomp.core.usecase.LoadWeekZonesUseCase
import com.ridecomp.core.usecase.LoadWidgetSummaryUseCase
import com.ridecomp.core.usecase.MapLimitCheck
import com.ridecomp.core.usecase.MapPackDownloads
import com.ridecomp.core.usecase.ObserveActivePlanUseCase
import com.ridecomp.core.usecase.ObserveActivityProfileUseCase
import com.ridecomp.core.usecase.ObserveActivityProfilesUseCase
import com.ridecomp.core.usecase.ObserveActivityUseCase
import com.ridecomp.core.usecase.ObserveAppThemeUseCase
import com.ridecomp.core.usecase.ObserveAvailableUpdateUseCase
import com.ridecomp.core.usecase.ObserveBackupReminderUseCase
import com.ridecomp.core.usecase.ObserveBikeChoiceUseCase
import com.ridecomp.core.usecase.ObserveBikeDistanceUseCase
import com.ridecomp.core.usecase.ObserveComponentWearUseCase
import com.ridecomp.core.usecase.ObserveDataPagesUseCase
import com.ridecomp.core.usecase.ObserveGpsStatusUseCase
import com.ridecomp.core.usecase.ObserveGuideUseCase
import com.ridecomp.core.usecase.ObserveHasProfileUseCase
import com.ridecomp.core.usecase.ObserveHistoryCountUseCase
import com.ridecomp.core.usecase.ObserveHistoryPageUseCase
import com.ridecomp.core.usecase.ObserveLastBackupUseCase
import com.ridecomp.core.usecase.ObserveLiveRideUseCase
import com.ridecomp.core.usecase.ObserveMapMobileDataUseCase
import com.ridecomp.core.usecase.ObserveMapRegionOfferUseCase
import com.ridecomp.core.usecase.ObserveMapRegionsUseCase
import com.ridecomp.core.usecase.ObserveMapTilesUseCase
import com.ridecomp.core.usecase.ObserveMonthTotalsUseCase
import com.ridecomp.core.usecase.ObserveOfflineMapsUseCase
import com.ridecomp.core.usecase.ObserveOnboardingDoneUseCase
import com.ridecomp.core.usecase.ObservePersonalRecordsUseCase
import com.ridecomp.core.usecase.ObservePlansUseCase
import com.ridecomp.core.usecase.ObserveProfileChoiceUseCase
import com.ridecomp.core.usecase.ObserveProfileUseCase
import com.ridecomp.core.usecase.ObserveRecordKindsUseCase
import com.ridecomp.core.usecase.ObserveRideResultsUseCase
import com.ridecomp.core.usecase.ObserveRideSettingsUseCase
import com.ridecomp.core.usecase.ObserveRidesOnStravaUseCase
import com.ridecomp.core.usecase.ObserveRouteUseCase
import com.ridecomp.core.usecase.ObserveRoutesUseCase
import com.ridecomp.core.usecase.ObserveSafetyUseCase
import com.ridecomp.core.usecase.ObserveSavedSensorsUseCase
import com.ridecomp.core.usecase.ObserveSegmentEffortsUseCase
import com.ridecomp.core.usecase.ObserveSegmentUseCase
import com.ridecomp.core.usecase.ObserveSegmentsUseCase
import com.ridecomp.core.usecase.ObserveStravaStatusUseCase
import com.ridecomp.core.usecase.ObserveStravaUploadUseCase
import com.ridecomp.core.usecase.ObserveTrainerUseCase
import com.ridecomp.core.usecase.ObserveTrainingUseCase
import com.ridecomp.core.usecase.ObserveUpdateSettingsUseCase
import com.ridecomp.core.usecase.PairSensorUseCase
import com.ridecomp.core.usecase.PerformIncidentActionUseCase
import com.ridecomp.core.usecase.PerformRideActionUseCase
import com.ridecomp.core.usecase.PreviewNewRecordsUseCase
import com.ridecomp.core.usecase.ProbeSensorUseCase
import com.ridecomp.core.usecase.ProcessStravaUploadsUseCase
import com.ridecomp.core.usecase.QueueNewRidesForStravaUseCase
import com.ridecomp.core.usecase.QueueStravaUploadUseCase
import com.ridecomp.core.usecase.ReadBackupSummaryUseCase
import com.ridecomp.core.usecase.ReadBatteryUseCase
import com.ridecomp.core.usecase.ReadRideTrackUseCase
import com.ridecomp.core.usecase.RecomputeTrainingUseCase
import com.ridecomp.core.usecase.RemoveEmergencyContactUseCase
import com.ridecomp.core.usecase.RenameActivityUseCase
import com.ridecomp.core.usecase.RenameRouteUseCase
import com.ridecomp.core.usecase.ReplaceComponentUseCase
import com.ridecomp.core.usecase.RequestReanalysisUseCase
import com.ridecomp.core.usecase.ResetDataPagesUseCase
import com.ridecomp.core.usecase.ResetTipsUseCase
import com.ridecomp.core.usecase.RestartAfterRestoreUseCase
import com.ridecomp.core.usecase.RestoreBackupUseCase
import com.ridecomp.core.usecase.ReverseRouteUseCase
import com.ridecomp.core.usecase.RideSessionUseCase
import com.ridecomp.core.usecase.SaveActivityFileUseCase
import com.ridecomp.core.usecase.SaveActivityProfileUseCase
import com.ridecomp.core.usecase.SaveBikeUseCase
import com.ridecomp.core.usecase.SaveComponentUseCase
import com.ridecomp.core.usecase.SaveCustomPlanUseCase
import com.ridecomp.core.usecase.SaveDataPagesUseCase
import com.ridecomp.core.usecase.SaveProfileUseCase
import com.ridecomp.core.usecase.SaveRideSettingsUseCase
import com.ridecomp.core.usecase.SaveRiderNameUseCase
import com.ridecomp.core.usecase.SaveShareCardUseCase
import com.ridecomp.core.usecase.ScanSensorsUseCase
import com.ridecomp.core.usecase.SegmentsStep
import com.ridecomp.core.usecase.SelectActivityProfileUseCase
import com.ridecomp.core.usecase.SelectBikeUseCase
import com.ridecomp.core.usecase.SendTestSmsUseCase
import com.ridecomp.core.usecase.SetAppLanguageUseCase
import com.ridecomp.core.usecase.SetAppThemeUseCase
import com.ridecomp.core.usecase.SetAutoUpdateCheckUseCase
import com.ridecomp.core.usecase.SetMapCacheLimitUseCase
import com.ridecomp.core.usecase.SetMapMobileDataUseCase
import com.ridecomp.core.usecase.SetRiderLevelUseCase
import com.ridecomp.core.usecase.SetSafetyEnabledUseCase
import com.ridecomp.core.usecase.SetStravaAutoUploadUseCase
import com.ridecomp.core.usecase.SetTrainerTargetUseCase
import com.ridecomp.core.usecase.ShareActivityFileUseCase
import com.ridecomp.core.usecase.ShareCardFiles
import com.ridecomp.core.usecase.ShareCardImageUseCase
import com.ridecomp.core.usecase.SnoozeBackupReminderUseCase
import com.ridecomp.core.usecase.StartPlanUseCase
import com.ridecomp.core.usecase.StartRideUseCase
import com.ridecomp.core.usecase.StartStravaAuthUseCase
import com.ridecomp.core.usecase.StopPlanUseCase
import com.ridecomp.core.usecase.StravaUploadRequests
import com.ridecomp.core.usecase.SuggestBackupFileNameUseCase
import com.ridecomp.core.usecase.SuggestExportFileNameUseCase
import com.ridecomp.core.usecase.TrainingCalendar
import com.ridecomp.core.usecase.TrainingLoadStep
import com.ridecomp.platform.export.AndroidExportFileStore
import com.ridecomp.platform.export.AndroidExportedFileHandler
import com.ridecomp.platform.export.AndroidShareCardFiles
import java.security.SecureRandom
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/** Use cases are stateless (ARCH-04): a new instance per injection is fine. */
val useCaseModule = module {
    single<ExportFileStore> { AndroidExportFileStore(androidContext(), get()) }
    single<ExportedFileHandler> { AndroidExportedFileHandler(androidContext(), get()) }
    single<ShareCardFiles> { AndroidShareCardFiles(androidContext(), get()) }
    factory { ZoneId.systemDefault() }

    factory { GetRecoverableRideUseCase(get()) }
    factory { ObserveHistoryPageUseCase(get()) }
    factory { ObserveHistoryCountUseCase(get()) }
    factory { ObserveMonthTotalsUseCase(get(), get()) }
    factory { ObserveActivityUseCase(get()) }
    factory { GetLapsUseCase(get()) }
    factory { RenameActivityUseCase(get()) }
    factory { DeleteActivityUseCase(get(), get()) }
    factory { LoadRideChartUseCase(get()) }
    factory { LoadActivityAnalysisUseCase(get(), get(), get(), get(), get()) }
    factory { HasBarometerUseCase(get()) }
    factory { EstimateFtpUseCase(get(), get(), get(), get()) }
    factory { ExportFitUseCase(get(), get(), get(), get(), get()) }
    factory { ExportGpxUseCase(get(), get(), get()) }
    factory { ObserveProfileUseCase(get()) }
    factory { HasProfileUseCase(get()) }
    factory { ObserveHasProfileUseCase(get()) }
    factory { SaveProfileUseCase(get(), get()) }
    factory { EstimateHrMaxUseCase(get()) }
    factory { ObserveRideSettingsUseCase(get()) }
    factory { SaveRideSettingsUseCase(get()) }
    factory { ObserveOnboardingDoneUseCase(get()) }
    factory { CompleteOnboardingUseCase(get()) }
    factory { StartRideUseCase(get()) }
    factory { PerformRideActionUseCase(get()) }
    factory { ObserveLiveRideUseCase(get()) }
    factory { HandleRecoveredRideUseCase(get(), get()) }
    factory { ObserveGpsStatusUseCase(get()) }
    factory { ReadBatteryUseCase(get()) }
    factory { CheckGpsAvailabilityUseCase(get()) }
    factory { PerformIncidentActionUseCase(get()) }
    factory { BackupWriter(get(), get(), get(), get(), get()) }
    factory { CreateBackupUseCase(get(), get(), get(), get(), get(), get()) }
    factory { ReadBackupSummaryUseCase(get(), get(), get()) }
    factory { RestoreBackupUseCase(get(), get(), get(), get(), get(), get()) }
    factory { ObserveBackupReminderUseCase(get(), get(), get()) }
    factory { SnoozeBackupReminderUseCase(get(), get()) }
    factory { RestartAfterRestoreUseCase(get()) }
    factory { SuggestBackupFileNameUseCase(get(), get()) }
    factory { ObserveLastBackupUseCase(get()) }
    factory { ImportGpxUseCase(get(), get(), get(), get()) }
    factory { ObserveRoutesUseCase(get()) }
    factory { ObserveRouteUseCase(get()) }
    factory { GetRoutePointsUseCase(get()) }
    factory { GetRouteGuidanceUseCase(get(), get()) }
    factory { ObserveOfflineMapsUseCase(get()) }
    factory { ObserveMapTilesUseCase(get(), get(), get()) }
    factory { GetMapCacheInfoUseCase(get(), get()) }
    factory { SetMapCacheLimitUseCase(get()) }
    factory { ClearMapCacheUseCase(get(), get()) }
    // One owner of pack downloads for the whole app, with its own scope (paused when the app leaves, T11.12).
    factory { MapLimitCheck(get(), get()) }
    single {
        MapPackDownloads(
            get(),
            get(),
            get(),
            get(),
            get(),
            CoroutineScope(
                SupervisorJob() + get<DispatcherProvider>().default,
            ),
        )
    }
    factory { LoadMapRegionIndexUseCase(get()) }
    factory { ObserveMapRegionsUseCase(get(), get(), get(), get(), get()) }
    factory { ObserveMapRegionOfferUseCase(get(), get(), get()) }
    factory { DismissMapRegionOfferUseCase(get()) }
    factory { DeleteMapRegionUseCase(get(), get()) }
    factory { ObserveMapMobileDataUseCase(get()) }
    factory { SetMapMobileDataUseCase(get()) }
    factory { LoadRideTrailUseCase(get()) }
    factory { ImportOfflineMapUseCase(get(), get()) }
    factory { DeleteOfflineMapUseCase(get()) }
    factory { GetFreeStorageUseCase(get()) }
    factory { RenameRouteUseCase(get()) }
    factory { DeleteRouteUseCase(get()) }
    factory { ReverseRouteUseCase(get(), get(), get()) }
    factory { ExportActivityUseCase(get(), get()) }
    factory { ShareActivityFileUseCase(get(), get()) }
    factory { SaveActivityFileUseCase(get(), get()) }
    factory { SuggestExportFileNameUseCase(get(), get()) }
    factory { ObserveSavedSensorsUseCase(get()) }
    factory { ScanSensorsUseCase(get()) }
    factory { ProbeSensorUseCase(get()) }
    factory { PairSensorUseCase(get(), get()) }
    factory { ForgetSensorUseCase(get()) }
    factory { ObserveDataPagesUseCase(get(), get()) }
    factory { SaveDataPagesUseCase(get(), get()) }
    factory { ResetDataPagesUseCase(get(), get()) }
    factory { ObserveActivityProfilesUseCase(get()) }
    factory { ObserveProfileChoiceUseCase(get()) }
    factory { SelectActivityProfileUseCase(get()) }
    factory { GetRideProfileUseCase(get()) }
    factory { ObserveActivityProfileUseCase(get()) }
    factory { SaveActivityProfileUseCase(get()) }
    factory { DuplicateActivityProfileUseCase(get()) }
    factory { DeleteActivityProfileUseCase(get()) }
    factory { DeleteBikeUseCase(get()) }
    factory { GetBikeUseCase(get()) }
    factory { GetSelectedBikeUseCase(get()) }
    factory { ObserveBikeChoiceUseCase(get()) }
    factory { SaveBikeUseCase(get()) }
    factory { SelectBikeUseCase(get()) }
    factory { LoadWidgetSummaryUseCase(get(), get(), get(), get()) }
    factory { ObserveTrainerUseCase(get()) }
    factory { SetTrainerTargetUseCase(get()) }
    factory { ObservePlansUseCase(get()) }
    factory { GetCustomPlanUseCase(get()) }
    factory { ObserveActivePlanUseCase(get(), get()) }
    factory { StartPlanUseCase(get(), get()) }
    factory { StopPlanUseCase(get()) }
    factory { SaveCustomPlanUseCase(get()) }
    factory { DeleteCustomPlanUseCase(get()) }
    factory { RideSessionUseCase(get(), get()) }
    factory { ObserveComponentWearUseCase(get()) }
    factory { ObserveBikeDistanceUseCase(get()) }
    factory { GetComponentUseCase(get()) }
    factory { SaveComponentUseCase(get()) }
    factory { ReplaceComponentUseCase(get(), get()) }
    factory { DeleteComponentUseCase(get()) }
    factory { ObserveSafetyUseCase(get()) }
    factory { SaveRiderNameUseCase(get()) }
    factory { SetSafetyEnabledUseCase(get(), get()) }
    factory { AddEmergencyContactUseCase(get()) }
    factory { AddPickedContactUseCase(get(), get()) }
    factory { RemoveEmergencyContactUseCase(get()) }
    factory { SendTestSmsUseCase(get(), get(), get(), get()) }
    factory { GetActivityKindUseCase(get()) }
    factory { ReadRideTrackUseCase(get(), get()) }
    factory { EvaluateBestEffortsUseCase(get(), get(), get()) }
    // Singles: one request channel and one mutex keep a single analysis running at a time.
    single { AnalysisRequests() }
    factory { RequestReanalysisUseCase(get(), get()) }
    single {
        AnalyzeActivitiesUseCase(
            analysis = get(),
            readTrack = get(),
            steps = listOf(
                BestEffortsStep(get(), get(), get()),
                SegmentsStep(get(), get()),
                TrainingLoadStep(get(), get(), get(), get(), get()),
            ),
            requests = get(),
            logger = get(),
        )
    }
    factory { PreviewNewRecordsUseCase(get(), get(), get()) }
    factory { ObservePersonalRecordsUseCase(get()) }
    factory { ObserveRideResultsUseCase(get(), get()) }
    factory { ObserveRecordKindsUseCase(get()) }
    factory { CreateSegmentFromActivityUseCase(get(), get(), get(), get()) }
    factory { CreateSegmentFromClimbUseCase(get(), get(), get(), get()) }
    factory { DeleteSegmentUseCase(get()) }
    factory { LoadTrackOutlineUseCase(get(), get()) }
    factory { GetLiveSegmentsUseCase(get()) }
    factory { TrainingCalendar(get()) }
    factory { RecomputeTrainingUseCase(get(), get(), get()) }
    factory { ObserveTrainingUseCase(get(), get()) }
    factory { LoadWeekZonesUseCase(get(), get(), get(), get(), get(), get()) }
    factory { ExtendTrainingToTodayUseCase(get(), get()) }
    factory { GetLoadInputsUseCase(get(), get()) }
    factory { LoadShareCardUseCase(get(), get(), get(), get(), get(), get()) }
    factory { SaveShareCardUseCase(get()) }
    factory { ShareCardImageUseCase(get(), get()) }
    factory { GetShareTargetsUseCase(get()) }
    factory { CheckForUpdateUseCase(get(), get(), get(), get()) }
    factory { ObserveAvailableUpdateUseCase(get(), get()) }
    factory { ObserveUpdateSettingsUseCase(get(), get()) }
    factory { SetAutoUpdateCheckUseCase(get()) }
    factory { DismissUpdateUseCase(get()) }
    factory { ClearShareCardsUseCase(get()) }
    factory { ObserveGuideUseCase(get()) }
    factory { SetRiderLevelUseCase(get()) }
    factory { DismissTipUseCase(get()) }
    factory { ResetTipsUseCase(get()) }
    factory { ObserveSegmentsUseCase(get()) }
    factory { ObserveSegmentUseCase(get()) }
    factory { ObserveSegmentEffortsUseCase(get()) }
    factory { ObserveStravaStatusUseCase(get(), get()) }
    factory { GetAppLanguageUseCase(get()) }
    factory { SetAppLanguageUseCase(get()) }
    factory { ObserveAppThemeUseCase(get()) }
    factory { SetAppThemeUseCase(get()) }
    factory { StartStravaAuthUseCase(get(), get(), SecureRandom()) }
    factory { CompleteStravaAuthUseCase(get(), get()) }
    factory { DisconnectStravaUseCase(get(), get()) }
    // Single: one token refresh at a time (Strava rotates refresh tokens).
    single { GetStravaAccessTokenUseCase(get(), get(), get()) }
    single { StravaUploadRequests() }
    factory { ObserveStravaUploadUseCase(get()) }
    factory { ObserveRidesOnStravaUseCase(get()) }
    factory { QueueStravaUploadUseCase(get(), get(), get()) }
    factory { SetStravaAutoUploadUseCase(get(), get()) }
    factory { QueueNewRidesForStravaUseCase(get(), get(), get(), get()) }
    // Single: one queue run at a time.
    factory { AuthorizedStravaApi(get(), get()) }
    single { ProcessStravaUploadsUseCase(get(), get(), get(), get(), get(), get()) }
}
