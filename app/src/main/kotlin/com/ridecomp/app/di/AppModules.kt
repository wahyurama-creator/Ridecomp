package com.ridecomp.app.di

import com.ridecomp.app.AppViewModel
import com.ridecomp.app.StravaQueue
import com.ridecomp.feature.history.di.historyFeatureModule
import com.ridecomp.feature.ride.di.rideFeatureModule
import com.ridecomp.feature.routes.di.routesFeatureModule
import com.ridecomp.feature.sensors.di.sensorsFeatureModule
import com.ridecomp.feature.settings.di.settingsFeatureModule
import com.ridecomp.feature.training.di.trainingFeatureModule
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/** App-level ViewModels. */
val appModule = module {
    factory { StravaQueue(get(), get(), get()) }
    viewModel { AppViewModel(get(), get(), get(), get(), get(), get()) }
}

/** Every Koin module of the app, wired once in the composition root (ARCH-02). */
val appModules: List<Module> = listOf(
    platformModule,
    dataModule,
    useCaseModule,
    appModule,
    rideFeatureModule,
    historyFeatureModule,
    settingsFeatureModule,
    trainingFeatureModule,
    sensorsFeatureModule,
    routesFeatureModule,
)
