package com.ridecomp.feature.settings

import app.cash.turbine.test
import com.ridecomp.core.model.StravaAccount
import com.ridecomp.core.model.StravaSetup
import com.ridecomp.core.model.StravaStatus
import com.ridecomp.core.testing.FakeClock
import com.ridecomp.core.testing.FakeStravaAccountStore
import com.ridecomp.core.testing.FakeStravaApi
import com.ridecomp.core.usecase.CompleteStravaAuthUseCase
import com.ridecomp.core.usecase.DisconnectStravaUseCase
import com.ridecomp.core.usecase.ObserveStravaStatusUseCase
import com.ridecomp.core.usecase.SetStravaAutoUploadUseCase
import com.ridecomp.core.usecase.StartStravaAuthUseCase
import com.ridecomp.core.usecase.StravaAuthRedirect
import com.ridecomp.feature.settings.viewmodel.StravaEffect
import com.ridecomp.feature.settings.viewmodel.StravaMessage
import com.ridecomp.feature.settings.viewmodel.StravaUiEvent
import com.ridecomp.feature.settings.viewmodel.StravaUseCases
import com.ridecomp.feature.settings.viewmodel.StravaViewModel
import java.security.SecureRandom
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

@OptIn(ExperimentalCoroutinesApi::class)
class StravaViewModelTest {
    private val store = FakeStravaAccountStore()
    private val api = FakeStravaApi()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(setup: StravaSetup = FakeStravaApi.SETUP) = StravaViewModel(
        StravaUseCases(
            ObserveStravaStatusUseCase(store, setup),
            StartStravaAuthUseCase(store, setup, SecureRandom()),
            CompleteStravaAuthUseCase(store, api),
            SetStravaAutoUploadUseCase(store, FakeClock()),
            DisconnectStravaUseCase(store, api),
        ),
    )

    @Test
    fun `a build without Strava shows it as unavailable`() = runTest {
        assertEquals(StravaStatus.Unavailable, viewModel(StravaSetup("", "")).uiState.value.status)
    }

    @Test
    fun `connect opens the authorize page, the redirect connects, then auto-upload and disconnect`() = runTest {
        val viewModel = viewModel()
        assertEquals(StravaStatus.NotConnected, viewModel.uiState.value.status)

        viewModel.effects.test {
            viewModel.onEvent(StravaUiEvent.Connect)
            assertIs<StravaEffect.OpenUrl>(awaitItem())
        }
        val state = requireNotNull(store.account.value.pendingState)
        viewModel.onEvent(StravaUiEvent.Redirect(StravaAuthRedirect("code", state, "activity:write", null)))

        assertEquals(StravaStatus.Connected("Rider One"), viewModel.uiState.value.status)
        assertEquals(StravaMessage.CONNECTED, viewModel.uiState.value.message)
        viewModel.onEvent(StravaUiEvent.MessageShown)
        assertNull(viewModel.uiState.value.message)

        viewModel.onEvent(StravaUiEvent.SetAutoUpload(true))
        assertEquals(StravaStatus.Connected("Rider One", isAutoUpload = true), viewModel.uiState.value.status)

        viewModel.onEvent(StravaUiEvent.Disconnect)
        assertEquals(StravaStatus.NotConnected, viewModel.uiState.value.status)
        assertEquals(StravaMessage.DISCONNECTED, viewModel.uiState.value.message)
    }

    @Test
    fun `denied and unexpected redirects`() = runTest {
        store.account.value = StravaAccount(pendingState = "s")
        val viewModel = viewModel()

        viewModel.onEvent(StravaUiEvent.Redirect(StravaAuthRedirect(null, "s", null, "access_denied")))
        assertEquals(StravaMessage.DENIED, viewModel.uiState.value.message)

        viewModel.onEvent(StravaUiEvent.MessageShown)
        viewModel.onEvent(StravaUiEvent.Redirect(StravaAuthRedirect("c", "s", "activity:write", null)))
        assertNull(viewModel.uiState.value.message)

        viewModel.onEvent(StravaUiEvent.OpenFailed)
        assertEquals(StravaMessage.NO_BROWSER, viewModel.uiState.value.message)
    }
}
