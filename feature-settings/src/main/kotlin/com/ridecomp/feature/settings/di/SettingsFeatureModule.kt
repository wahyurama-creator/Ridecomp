package com.ridecomp.feature.settings.di

import com.ridecomp.feature.settings.viewmodel.BackupUseCases
import com.ridecomp.feature.settings.viewmodel.BackupViewModel
import com.ridecomp.feature.settings.viewmodel.OnboardingViewModel
import com.ridecomp.feature.settings.viewmodel.ProfileViewModel
import com.ridecomp.feature.settings.viewmodel.RideSettingsViewModel
import com.ridecomp.feature.settings.viewmodel.SafetyUseCases
import com.ridecomp.feature.settings.viewmodel.SafetyViewModel
import com.ridecomp.feature.settings.viewmodel.SettingsViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/** ViewModels of the settings feature; use cases come from the app graph. */
val settingsFeatureModule = module {
    viewModel { ProfileViewModel(get(), get(), get()) }
    viewModel { SettingsViewModel(get(), get(), get(), get()) }
    viewModel { RideSettingsViewModel(get(), get()) }
    viewModel { OnboardingViewModel(get(), get()) }
    factory { SafetyUseCases(get(), get(), get(), get(), get(), get(), get()) }
    viewModel { SafetyViewModel(get()) }
    factory { BackupUseCases(get(), get(), get(), get(), get(), get()) }
    viewModel { BackupViewModel(get()) }
}
