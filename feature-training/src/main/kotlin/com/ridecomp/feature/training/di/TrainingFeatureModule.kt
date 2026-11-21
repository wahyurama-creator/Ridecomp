package com.ridecomp.feature.training.di

import com.ridecomp.feature.training.viewmodel.AndroidSharePhotoLoader
import com.ridecomp.feature.training.viewmodel.NewRecordsViewModel
import com.ridecomp.feature.training.viewmodel.PlanBuilderViewModel
import com.ridecomp.feature.training.viewmodel.PlanDetailUseCases
import com.ridecomp.feature.training.viewmodel.PlanDetailViewModel
import com.ridecomp.feature.training.viewmodel.PlansViewModel
import com.ridecomp.feature.training.viewmodel.RecordsViewModel
import com.ridecomp.feature.training.viewmodel.RideResultsViewModel
import com.ridecomp.feature.training.viewmodel.SegmentDetailViewModel
import com.ridecomp.feature.training.viewmodel.SegmentEditorUseCases
import com.ridecomp.feature.training.viewmodel.SegmentEditorViewModel
import com.ridecomp.feature.training.viewmodel.SegmentsViewModel
import com.ridecomp.feature.training.viewmodel.ShareCardUseCases
import com.ridecomp.feature.training.viewmodel.ShareCardViewModel
import com.ridecomp.feature.training.viewmodel.SharePhotoLoader
import com.ridecomp.feature.training.viewmodel.TrainingViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/** ViewModels of the training feature (records, later segments and training load); use cases from the app graph. */
val trainingFeatureModule = module {
    viewModel { RecordsViewModel(get(), get(), get()) }
    viewModel { params -> NewRecordsViewModel(params.get(), get(), get()) }
    viewModel { params -> RideResultsViewModel(params.get(), get(), get()) }
    viewModel { SegmentsViewModel(get(), get()) }
    viewModel { TrainingViewModel(get(), get(), get(), get(), get()) }
    viewModel { PlansViewModel(get(), get()) }
    factory { PlanDetailUseCases(get(), get(), get(), get(), get()) }
    viewModel { params -> PlanDetailViewModel(params.get(), get()) }
    viewModel { params -> PlanBuilderViewModel(params.getOrNull(), get(), get()) }
    factory<SharePhotoLoader> { AndroidSharePhotoLoader(androidContext(), get()) }
    factory { ShareCardUseCases(get(), get(), get(), get(), get(), get()) }
    viewModel { params -> ShareCardViewModel(params.get(), get(), get(), get()) }
    viewModel { params -> SegmentDetailViewModel(params.get(), get(), get(), get(), get()) }
    factory { SegmentEditorUseCases(get(), get(), get(), get(), get()) }
    viewModel { params -> SegmentEditorViewModel(params.get(), params.getOrNull(), get()) }
}
