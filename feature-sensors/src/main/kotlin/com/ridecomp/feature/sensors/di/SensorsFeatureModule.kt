package com.ridecomp.feature.sensors.di

import com.ridecomp.feature.sensors.viewmodel.SensorsViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/** ViewModels of the sensors feature; use cases come from the app graph. */
val sensorsFeatureModule = module {
    viewModel { SensorsViewModel(get(), get(), get(), get(), get()) }
}
