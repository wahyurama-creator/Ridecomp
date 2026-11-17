package com.ridecomp.feature.routes.di

import com.ridecomp.feature.routes.viewmodel.RouteDetailUseCases
import com.ridecomp.feature.routes.viewmodel.RouteDetailViewModel
import com.ridecomp.feature.routes.viewmodel.RoutesViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/** ViewModels of the routes feature; use cases come from the app graph. */
val routesFeatureModule = module {
    viewModel { RoutesViewModel(get(), get(), get()) }
    factory { RouteDetailUseCases(get(), get(), get(), get(), get(), get(), get(), get(), get(), get()) }
    viewModel { params -> RouteDetailViewModel(routeId = params.get(), useCases = get(), dispatchers = get()) }
}
