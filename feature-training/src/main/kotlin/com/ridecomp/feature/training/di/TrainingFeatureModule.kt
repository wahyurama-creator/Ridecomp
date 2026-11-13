package com.ridecomp.feature.training.di

import com.ridecomp.feature.training.viewmodel.NewRecordsViewModel
import com.ridecomp.feature.training.viewmodel.RecordsViewModel
import com.ridecomp.feature.training.viewmodel.SegmentDetailViewModel
import com.ridecomp.feature.training.viewmodel.SegmentEditorViewModel
import com.ridecomp.feature.training.viewmodel.SegmentsViewModel
import com.ridecomp.feature.training.viewmodel.TrainingViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/** ViewModels of the training feature (records, later segments and training load); use cases from the app graph. */
val trainingFeatureModule = module {
    viewModel { RecordsViewModel(get(), get(), get()) }
    viewModel { params -> NewRecordsViewModel(params.get(), get(), get()) }
    viewModel { SegmentsViewModel(get(), get()) }
    viewModel { TrainingViewModel(get(), get(), get()) }
    viewModel { params -> SegmentDetailViewModel(params.get(), get(), get(), get(), get()) }
    viewModel { params -> SegmentEditorViewModel(params.get(), get(), get(), get(), get()) }
}
