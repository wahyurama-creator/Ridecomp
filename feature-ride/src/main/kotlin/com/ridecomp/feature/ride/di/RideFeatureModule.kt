package com.ridecomp.feature.ride.di

import com.ridecomp.feature.ride.viewmodel.ActivityProfileEditorViewModel
import com.ridecomp.feature.ride.viewmodel.ActivityProfilesViewModel
import com.ridecomp.feature.ride.viewmodel.BikeEditorUseCases
import com.ridecomp.feature.ride.viewmodel.BikeEditorViewModel
import com.ridecomp.feature.ride.viewmodel.BikesViewModel
import com.ridecomp.feature.ride.viewmodel.ComponentEditorUseCases
import com.ridecomp.feature.ride.viewmodel.ComponentEditorViewModel
import com.ridecomp.feature.ride.viewmodel.DataPagesEditorViewModel
import com.ridecomp.feature.ride.viewmodel.HomeDeviceUseCases
import com.ridecomp.feature.ride.viewmodel.HomeMapUseCases
import com.ridecomp.feature.ride.viewmodel.HomeUpdateUseCases
import com.ridecomp.feature.ride.viewmodel.HomeUseCases
import com.ridecomp.feature.ride.viewmodel.HomeViewModel
import com.ridecomp.feature.ride.viewmodel.PreRideBikeUseCases
import com.ridecomp.feature.ride.viewmodel.PreRideOptionUseCases
import com.ridecomp.feature.ride.viewmodel.PreRideUseCases
import com.ridecomp.feature.ride.viewmodel.PreRideViewModel
import com.ridecomp.feature.ride.viewmodel.RideMapUseCases
import com.ridecomp.feature.ride.viewmodel.RideTrainerUseCases
import com.ridecomp.feature.ride.viewmodel.RideUseCases
import com.ridecomp.feature.ride.viewmodel.RideViewModel
import com.ridecomp.feature.ride.viewmodel.SummaryViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/** ViewModels of the ride feature; use cases come from the app graph. */
val rideFeatureModule = module {
    factory { HomeDeviceUseCases(get(), get(), get()) }
    factory { HomeUpdateUseCases(get(), get(), get()) }
    factory { HomeMapUseCases(get(), get(), get()) }
    factory { HomeUseCases(get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get()) }
    viewModel { HomeViewModel(get()) }
    factory { PreRideOptionUseCases(get(), get(), get(), get(), get()) }
    factory { PreRideBikeUseCases(get(), get(), get(), get()) }
    factory { PreRideUseCases(get(), get(), get(), get(), get(), get()) }
    viewModel { params -> PreRideViewModel(params.getOrNull(), get()) }
    factory { RideUseCases(get(), get(), get(), get(), get(), get(), get(), get()) }
    factory { RideMapUseCases(get(), get()) }
    factory { RideTrainerUseCases(get(), get(), get()) }
    viewModel { RideViewModel(get(), get(), get(), get()) }
    viewModel { SummaryViewModel(get(), get(), get(), get(), get()) }
    viewModel { params -> DataPagesEditorViewModel(params.getOrNull(), get(), get(), get()) }
    viewModel { ActivityProfilesViewModel(get(), get(), get()) }
    viewModel { params -> ActivityProfileEditorViewModel(params.get(), get(), get()) }
    viewModel { BikesViewModel(get(), get(), get(), get()) }
    factory { BikeEditorUseCases(get(), get(), get(), get(), get(), get(), get()) }
    factory { ComponentEditorUseCases(get(), get(), get(), get(), get()) }
    viewModel { params -> ComponentEditorViewModel(params.get(), get(), get()) }
    viewModel { params -> BikeEditorViewModel(params.get(), get()) }
}
