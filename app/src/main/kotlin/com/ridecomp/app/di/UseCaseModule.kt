package com.ridecomp.app.di

import com.ridecomp.core.usecase.CompleteOnboardingUseCase
import com.ridecomp.core.usecase.DeleteActivityUseCase
import com.ridecomp.core.usecase.EstimateHrMaxUseCase
import com.ridecomp.core.usecase.ExportFileStore
import com.ridecomp.core.usecase.ExportFitUseCase
import com.ridecomp.core.usecase.ExportGpxUseCase
import com.ridecomp.core.usecase.ExportedFileHandler
import com.ridecomp.core.usecase.GetLapsUseCase
import com.ridecomp.core.usecase.GetRecoverableRideUseCase
import com.ridecomp.core.usecase.HasProfileUseCase
import com.ridecomp.core.usecase.LoadRideChartUseCase
import com.ridecomp.core.usecase.ObserveActivityUseCase
import com.ridecomp.core.usecase.ObserveHistoryCountUseCase
import com.ridecomp.core.usecase.ObserveHistoryPageUseCase
import com.ridecomp.core.usecase.ObserveOnboardingDoneUseCase
import com.ridecomp.core.usecase.ObserveProfileUseCase
import com.ridecomp.core.usecase.ObserveRideSettingsUseCase
import com.ridecomp.core.usecase.RenameActivityUseCase
import com.ridecomp.core.usecase.SaveProfileUseCase
import com.ridecomp.core.usecase.SaveRideSettingsUseCase
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
}
