package com.ridecomp.feature.settings.di

import com.ridecomp.feature.settings.viewmodel.AndroidLicenseTextReader
import com.ridecomp.feature.settings.viewmodel.AppUpdateUseCases
import com.ridecomp.feature.settings.viewmodel.AppUpdateViewModel
import com.ridecomp.feature.settings.viewmodel.BackupUseCases
import com.ridecomp.feature.settings.viewmodel.BackupViewModel
import com.ridecomp.feature.settings.viewmodel.DisplayViewModel
import com.ridecomp.feature.settings.viewmodel.GuideViewModel
import com.ridecomp.feature.settings.viewmodel.LanguageViewModel
import com.ridecomp.feature.settings.viewmodel.LicenseTextReader
import com.ridecomp.feature.settings.viewmodel.OfflineMapRegionUseCases
import com.ridecomp.feature.settings.viewmodel.OfflineMapsUseCases
import com.ridecomp.feature.settings.viewmodel.OfflineMapsViewModel
import com.ridecomp.feature.settings.viewmodel.OnboardingViewModel
import com.ridecomp.feature.settings.viewmodel.OssLicensesViewModel
import com.ridecomp.feature.settings.viewmodel.ProfileViewModel
import com.ridecomp.feature.settings.viewmodel.RideSettingsViewModel
import com.ridecomp.feature.settings.viewmodel.SafetyUseCases
import com.ridecomp.feature.settings.viewmodel.SafetyViewModel
import com.ridecomp.feature.settings.viewmodel.SettingsUseCases
import com.ridecomp.feature.settings.viewmodel.SettingsViewModel
import com.ridecomp.feature.settings.viewmodel.StravaUseCases
import com.ridecomp.feature.settings.viewmodel.StravaViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/** ViewModels of the settings feature; use cases come from the app graph. */
val settingsFeatureModule = module {
    viewModel { ProfileViewModel(get(), get(), get()) }
    factory { SettingsUseCases(get(), get(), get(), get(), get(), get(), get(), get(), get()) }
    viewModel { SettingsViewModel(get()) }
    viewModel { LanguageViewModel(get(), get()) }
    viewModel { DisplayViewModel(get(), get()) }
    factory { OfflineMapRegionUseCases(get(), get(), get(), get(), get()) }
    factory { OfflineMapsUseCases(get(), get(), get(), get(), get(), get(), get(), get(), get(), get()) }
    viewModel { OfflineMapsViewModel(get()) }
    viewModel { RideSettingsViewModel(get(), get()) }
    viewModel { OnboardingViewModel(get(), get(), get()) }
    viewModel { GuideViewModel(get(), get(), get(), get()) }
    factory { SafetyUseCases(get(), get(), get(), get(), get(), get(), get()) }
    viewModel { SafetyViewModel(get()) }
    factory { BackupUseCases(get(), get(), get(), get(), get(), get()) }
    viewModel { BackupViewModel(get()) }
    factory<LicenseTextReader> { AndroidLicenseTextReader(androidContext()) }
    viewModel { OssLicensesViewModel(get(), get()) }
    factory { AppUpdateUseCases(get(), get(), get()) }
    viewModel { AppUpdateViewModel(get()) }
    factory { StravaUseCases(get(), get(), get(), get(), get()) }
    viewModel { StravaViewModel(get()) }
}
