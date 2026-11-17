package com.ridecomp.feature.routes

import app.cash.turbine.test
import com.ridecomp.core.engine.route.GpxError
import com.ridecomp.core.model.UnitSystem
import com.ridecomp.core.testing.FakeActivityAnalysisRepository
import com.ridecomp.core.testing.FakeActivityRepository
import com.ridecomp.core.testing.FakeClock
import com.ridecomp.core.testing.FakeGpxDocumentSource
import com.ridecomp.core.testing.FakeNetworkStatusSource
import com.ridecomp.core.testing.FakeOfflineMapRepository
import com.ridecomp.core.testing.FakeRouteRepository
import com.ridecomp.core.testing.FakeSegmentRepository
import com.ridecomp.core.testing.FakeSettingsRepository
import com.ridecomp.core.testing.TestDispatcherProvider
import com.ridecomp.core.usecase.AnalysisRequests
import com.ridecomp.core.usecase.CreateSegmentFromClimbUseCase
import com.ridecomp.core.usecase.DeleteRouteUseCase
import com.ridecomp.core.usecase.GetRouteGuidanceUseCase
import com.ridecomp.core.usecase.GetRoutePointsUseCase
import com.ridecomp.core.usecase.ImportGpxUseCase
import com.ridecomp.core.usecase.ObserveMapTilesUseCase
import com.ridecomp.core.usecase.ObserveOfflineMapsUseCase
import com.ridecomp.core.usecase.ObserveRideSettingsUseCase
import com.ridecomp.core.usecase.ObserveRouteUseCase
import com.ridecomp.core.usecase.ObserveRoutesUseCase
import com.ridecomp.core.usecase.RenameRouteUseCase
import com.ridecomp.core.usecase.RequestReanalysisUseCase
import com.ridecomp.core.usecase.ReverseRouteUseCase
import com.ridecomp.core.util.Outcome
import com.ridecomp.feature.routes.viewmodel.RouteDetailEffect
import com.ridecomp.feature.routes.viewmodel.RouteDetailUiEvent
import com.ridecomp.feature.routes.viewmodel.RouteDetailUseCases
import com.ridecomp.feature.routes.viewmodel.RouteDetailViewModel
import com.ridecomp.feature.routes.viewmodel.RoutesEffect
import com.ridecomp.feature.routes.viewmodel.RoutesUiEvent
import com.ridecomp.feature.routes.viewmodel.RoutesViewModel
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

@OptIn(ExperimentalCoroutinesApi::class)
class RoutesViewModelsTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val routes = FakeRouteRepository()
    private val documents = FakeGpxDocumentSource()
    private val settings = FakeSettingsRepository()
    private val clock = FakeClock()
    private val gpx = """<gpx><rte><name>Loop</name><rtept lat="0" lon="0"><ele>10</ele></rtept>
        <rtept lat="0.01" lon="0"><ele>40</ele></rtept></rte></gpx>"""

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun routesViewModel() = RoutesViewModel(
        ObserveRoutesUseCase(routes),
        ObserveRideSettingsUseCase(settings),
        ImportGpxUseCase(documents, routes, clock, TestDispatcherProvider(dispatcher)),
    )

    @Test
    fun `import opens the new route, a bad file shows the reason`() = runTest {
        documents.documents["ok"] = gpx
        documents.documents["bad"] = "<gpx>"
        val viewModel = routesViewModel()

        viewModel.effects.test {
            viewModel.onEvent(RoutesUiEvent.Import("ok", "Route"))
            assertIs<RoutesEffect.Imported>(awaitItem())
        }
        assertEquals(listOf("Loop"), viewModel.uiState.value.routes.map { it.name })

        viewModel.onEvent(RoutesUiEvent.Import("bad", "Route"))
        assertEquals(GpxError.MALFORMED, viewModel.uiState.value.importError)
        viewModel.onEvent(RoutesUiEvent.ErrorShown)
        assertEquals(null, viewModel.uiState.value.importError)
    }

    @Test
    fun `detail shows the elevation profile, reverses and deletes`() = runTest {
        documents.documents["ok"] = gpx
        val imported = ImportGpxUseCase(documents, routes, clock, TestDispatcherProvider(dispatcher))("ok", "R")
        val id = assertIs<Outcome.Success<Long>>(imported).value
        val viewModel = RouteDetailViewModel(
            id,
            RouteDetailUseCases(
                ObserveRouteUseCase(routes),
                GetRoutePointsUseCase(routes),
                GetRouteGuidanceUseCase(routes, TestDispatcherProvider(dispatcher)),
                ObserveOfflineMapsUseCase(FakeOfflineMapRepository()),
                ObserveRideSettingsUseCase(settings),
                RenameRouteUseCase(routes),
                ReverseRouteUseCase(routes, clock, TestDispatcherProvider(dispatcher)),
                DeleteRouteUseCase(routes),
                CreateSegmentFromClimbUseCase(
                    routes,
                    FakeSegmentRepository(FakeActivityRepository()),
                    RequestReanalysisUseCase(
                        FakeActivityAnalysisRepository(FakeActivityRepository()),
                        AnalysisRequests(),
                    ),
                    clock,
                ),
                ObserveMapTilesUseCase(FakeNetworkStatusSource(), settings, FakeOfflineMapRepository()),
            ),
            TestDispatcherProvider(dispatcher),
        )

        assertEquals(UnitSystem.METRIC, viewModel.uiState.value.unitSystem)
        assertEquals(listOf(10.0, 40.0), viewModel.uiState.value.elevation.map { it.y })
        viewModel.effects.test {
            viewModel.onEvent(RouteDetailUiEvent.Reverse("Loop (reverse)"))
            assertIs<RouteDetailEffect.Open>(awaitItem())
            viewModel.onEvent(RouteDetailUiEvent.Delete)
            assertEquals(RouteDetailEffect.Deleted, awaitItem())
        }
        assertTrue(viewModel.uiState.value.isMissing)
    }
}
