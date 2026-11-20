package com.ridecomp.feature.ride

import com.ridecomp.core.source.RideController
import com.ridecomp.core.testing.FakeMapPackDownloader
import com.ridecomp.core.testing.FakeMapPackStateRepository
import com.ridecomp.core.testing.FakeMapRegionIndexSource
import com.ridecomp.core.testing.FakeNetworkStatusSource
import com.ridecomp.core.testing.FakeOfflineMapRepository
import com.ridecomp.core.testing.FakeRidePositionRepository
import com.ridecomp.core.testing.FakeSettingsRepository
import com.ridecomp.core.usecase.DismissMapRegionOfferUseCase
import com.ridecomp.core.usecase.LoadMapRegionIndexUseCase
import com.ridecomp.core.usecase.MapLimitCheck
import com.ridecomp.core.usecase.MapPackDownloads
import com.ridecomp.core.usecase.ObserveMapRegionOfferUseCase
import com.ridecomp.core.usecase.ObserveMapRegionsUseCase
import com.ridecomp.feature.ride.viewmodel.HomeMapUseCases
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

/** An empty region index: no map offer on home (the offer itself is tested in MapPackUseCasesTest). */
internal fun emptyHomeMapUseCases(controller: RideController): HomeMapUseCases {
    val state = FakeMapPackStateRepository()
    val maps = FakeOfflineMapRepository()
    val downloads = MapPackDownloads(
        FakeMapPackDownloader(maps),
        state,
        FakeNetworkStatusSource(),
        controller,
        MapLimitCheck(maps, FakeSettingsRepository()),
        CoroutineScope(Dispatchers.Unconfined),
    )
    val regions = ObserveMapRegionsUseCase(
        LoadMapRegionIndexUseCase(FakeMapRegionIndexSource()),
        maps,
        state,
        FakeRidePositionRepository(),
        downloads,
    )
    return HomeMapUseCases(
        ObserveMapRegionOfferUseCase(regions, state, controller),
        downloads,
        DismissMapRegionOfferUseCase(state),
    )
}
