package com.ridecomp.app.di

import com.ridecomp.core.usecase.CompleteOnboardingUseCase
import com.ridecomp.core.usecase.DeleteActivityUseCase
import com.ridecomp.core.usecase.EstimateHrMaxUseCase
import com.ridecomp.core.usecase.ExportActivityUseCase
import com.ridecomp.core.usecase.ExportFileStore
import com.ridecomp.core.usecase.ExportFitUseCase
import com.ridecomp.core.usecase.ExportGpxUseCase
import com.ridecomp.core.usecase.ExportedFileHandler
import com.ridecomp.core.usecase.ForgetSensorUseCase
import com.ridecomp.core.usecase.GetLapsUseCase
import com.ridecomp.core.usecase.GetRecoverableRideUseCase
import com.ridecomp.core.usecase.HandleRecoveredRideUseCase
import com.ridecomp.core.usecase.HasProfileUseCase
import com.ridecomp.core.usecase.LoadRideChartUseCase
import com.ridecomp.core.usecase.ObserveActivityUseCase
import com.ridecomp.core.usecase.ObserveDataPagesUseCase
import com.ridecomp.core.usecase.ObserveGpsStatusUseCase
import com.ridecomp.core.usecase.ObserveHistoryCountUseCase
import com.ridecomp.core.usecase.ObserveHistoryPageUseCase
import com.ridecomp.core.usecase.ObserveLiveRideUseCase
import com.ridecomp.core.usecase.ObserveOnboardingDoneUseCase
import com.ridecomp.core.usecase.ObserveProfileUseCase
import com.ridecomp.core.usecase.ObserveRideSettingsUseCase
import com.ridecomp.core.usecase.ObserveSavedSensorsUseCase
import com.ridecomp.core.usecase.PairSensorUseCase
import com.ridecomp.core.usecase.PerformRideActionUseCase
import com.ridecomp.core.usecase.ReadBatteryUseCase
import com.ridecomp.core.usecase.RenameActivityUseCase
import com.ridecomp.core.usecase.ResetDataPagesUseCase
import com.ridecomp.core.usecase.SaveActivityFileUseCase
import com.ridecomp.core.usecase.SaveDataPagesUseCase
import com.ridecomp.core.usecase.SaveProfileUseCase
import com.ridecomp.core.usecase.SaveRideSettingsUseCase
import com.ridecomp.core.usecase.ScanSensorsUseCase
import com.ridecomp.core.usecase.ShareActivityFileUseCase
import com.ridecomp.core.usecase.StartRideUseCase
import com.ridecomp.core.usecase.SuggestExportFileNameUseCase
import com.ridecomp.platform.export.AndroidExportFileStore
import com.ridecomp.platform.export.AndroidExportedFileHandler
import java.time.ZoneId
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/** Use cases are stateless (ARCH-04): a new instance per injection is fine. */
val useCaseModule = module {
    single<ExportFileStore> { AndroidExportFileStore(androidContext(), get()) }
    single<ExportedFileHandler> { AndroidExportedFileHandler(androidContext(), get()) }
    factory { ZoneId.systemDefault() }

    factory { GetRecoverableRideUseCase(get()) }
    factory { ObserveHistoryPageUseCase(get()) }
    factory { ObserveHistoryCountUseCase(get()) }
    factory { ObserveActivityUseCase(get()) }
    factory { GetLapsUseCase(get()) }
    factory { RenameActivityUseCase(get()) }
    factory { DeleteActivityUseCase(get()) }
    factory { LoadRideChartUseCase(get()) }
    factory { ExportFitUseCase(get(), get(), get(), get()) }
    factory { ExportGpxUseCase(get(), get(), get()) }
    factory { ObserveProfileUseCase(get()) }
    factory { HasProfileUseCase(get()) }
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
    factory { ExportActivityUseCase(get(), get()) }
    factory { ShareActivityFileUseCase(get(), get()) }
    factory { SaveActivityFileUseCase(get(), get()) }
    factory { SuggestExportFileNameUseCase(get(), get()) }
    factory { ObserveSavedSensorsUseCase(get()) }
    factory { ScanSensorsUseCase(get()) }
    factory { PairSensorUseCase(get(), get()) }
    factory { ForgetSensorUseCase(get()) }
    factory { ObserveDataPagesUseCase(get()) }
    factory { SaveDataPagesUseCase(get()) }
    factory { ResetDataPagesUseCase(get()) }
}
