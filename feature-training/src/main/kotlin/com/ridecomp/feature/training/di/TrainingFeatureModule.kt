package com.ridecomp.feature.training.di

import com.ridecomp.feature.training.viewmodel.NewRecordsViewModel
import com.ridecomp.feature.training.viewmodel.RecordsViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/** ViewModels of the training feature (records, later segments and training load); use cases from the app graph. */
val trainingFeatureModule = module {
    viewModel { RecordsViewModel(get(), get(), get()) }
    viewModel { params -> NewRecordsViewModel(params.get(), get(), get()) }
}
