package com.ridecomp.feature.ride.di

import com.ridecomp.feature.ride.viewmodel.ActivityProfileEditorViewModel
import com.ridecomp.feature.ride.viewmodel.ActivityProfilesViewModel
import com.ridecomp.feature.ride.viewmodel.DataPagesEditorViewModel
import com.ridecomp.feature.ride.viewmodel.HomeViewModel
import com.ridecomp.feature.ride.viewmodel.PreRideViewModel
import com.ridecomp.feature.ride.viewmodel.RideViewModel
import com.ridecomp.feature.ride.viewmodel.SummaryViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/** ViewModels of the ride feature; use cases come from the app graph. */
val rideFeatureModule = module {
    viewModel { HomeViewModel(get(), get(), get(), get(), get()) }
    viewModel { PreRideViewModel(get(), get(), get(), get(), get()) }
    viewModel { RideViewModel(get(), get(), get(), get(), get()) }
    viewModel { SummaryViewModel(get(), get(), get(), get(), get()) }
    viewModel { params -> DataPagesEditorViewModel(params.getOrNull(), get(), get(), get()) }
    viewModel { ActivityProfilesViewModel(get(), get(), get()) }
    viewModel { params -> ActivityProfileEditorViewModel(params.get(), get(), get()) }
}
