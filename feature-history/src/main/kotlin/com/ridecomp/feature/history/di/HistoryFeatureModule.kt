package com.ridecomp.feature.history.di

import com.ridecomp.feature.history.viewmodel.ActivityDetailUseCases
import com.ridecomp.feature.history.viewmodel.ActivityDetailViewModel
import com.ridecomp.feature.history.viewmodel.ActivityStravaUseCases
import com.ridecomp.feature.history.viewmodel.HistoryViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/** ViewModels of the history feature; use cases come from the app graph. */
val historyFeatureModule = module {
    factory {
        ActivityDetailUseCases(get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get())
    }
    viewModel { HistoryViewModel(get(), get(), get(), get(), get(), get()) }
    factory { ActivityStravaUseCases(get(), get(), get()) }
    viewModel { params ->
        ActivityDetailViewModel(activityId = params.get(), useCases = get(), strava = get(), dispatchers = get())
    }
}
