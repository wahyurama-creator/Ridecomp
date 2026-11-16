package com.ridecomp.feature.settings

import app.cash.turbine.test
import com.ridecomp.core.model.StravaAccount
import com.ridecomp.core.model.StravaStatus
import com.ridecomp.core.testing.FakeClock
import com.ridecomp.core.testing.FakeStravaAccountStore
import com.ridecomp.core.testing.FakeStravaApi
import com.ridecomp.core.usecase.CompleteStravaAuthUseCase
import com.ridecomp.core.usecase.DisconnectStravaUseCase
import com.ridecomp.core.usecase.ObserveStravaStatusUseCase
import com.ridecomp.core.usecase.SaveStravaAppUseCase
import com.ridecomp.core.usecase.SetStravaAutoUploadUseCase
import com.ridecomp.core.usecase.StartStravaAuthUseCase
import com.ridecomp.core.usecase.StravaAppError
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
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
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

    private fun viewModel() = StravaViewModel(
        StravaUseCases(
            ObserveStravaStatusUseCase(store),
            SaveStravaAppUseCase(store),
            StartStravaAuthUseCase(store, SecureRandom()),
            CompleteStravaAuthUseCase(store, api),
            SetStravaAutoUploadUseCase(store, FakeClock()),
            DisconnectStravaUseCase(store, api),
        ),
    )

    @Test
    fun `form validates, saves and forgets the secret`() = runTest {
        val viewModel = viewModel()
        assertTrue(viewModel.uiState.value.isFormShown)

        viewModel.onEvent(StravaUiEvent.ClientIdChanged("12x"))
        viewModel.onEvent(StravaUiEvent.ClientSecretChanged("a".repeat(40)))
        viewModel.onEvent(StravaUiEvent.SaveApp)
        assertEquals(StravaAppError.INVALID_CLIENT_ID, viewModel.uiState.value.appError)

        viewModel.onEvent(StravaUiEvent.ClientIdChanged("123"))
        viewModel.onEvent(StravaUiEvent.SaveApp)

        val state = viewModel.uiState.value
        assertEquals(StravaStatus.NotConnected("123"), state.status)
        assertFalse(state.isFormShown)
        assertEquals("", state.clientSecret)
    }

    @Test
    fun `connect opens the authorize page and the redirect connects`() = runTest {
        store.account.value = StravaAccount(app = FakeStravaApi.APP)
        val viewModel = viewModel()

        viewModel.effects.test {
            viewModel.onEvent(StravaUiEvent.Connect)
            assertIs<StravaEffect.OpenUrl>(awaitItem())
        }
        val state = requireNotNull(store.account.value.pendingState)
        viewModel.onEvent(StravaUiEvent.Redirect(StravaAuthRedirect("code", state, "activity:write", null)))

        assertEquals(StravaStatus.Connected("12345", "Rider One"), viewModel.uiState.value.status)
        assertEquals(StravaMessage.CONNECTED, viewModel.uiState.value.message)
        viewModel.onEvent(StravaUiEvent.SetAutoUpload(true))
        assertEquals(StravaStatus.Connected("12345", "Rider One", isAutoUpload = true), viewModel.uiState.value.status)

        viewModel.onEvent(StravaUiEvent.Disconnect)
        assertEquals(StravaStatus.NotConnected("12345"), viewModel.uiState.value.status)
        assertEquals(StravaMessage.DISCONNECTED, viewModel.uiState.value.message)
        viewModel.onEvent(StravaUiEvent.MessageShown)
        assertNull(viewModel.uiState.value.message)
    }

    @Test
    fun `denied and unexpected redirects`() = runTest {
        store.account.value = StravaAccount(app = FakeStravaApi.APP, pendingState = "s")
        val viewModel = viewModel()

        viewModel.onEvent(StravaUiEvent.Redirect(StravaAuthRedirect(null, "s", null, "access_denied")))
        assertEquals(StravaMessage.DENIED, viewModel.uiState.value.message)

        viewModel.onEvent(StravaUiEvent.MessageShown)
        viewModel.onEvent(StravaUiEvent.Redirect(StravaAuthRedirect("c", "s", "activity:write", null)))
        assertNull(viewModel.uiState.value.message)
    }

    @Test
    fun `api page link and missing browser`() = runTest {
        val viewModel = viewModel()
        viewModel.effects.test {
            viewModel.onEvent(StravaUiEvent.OpenApiPage)
            assertEquals(StravaEffect.OpenUrl("https://www.strava.com/settings/api"), awaitItem())
        }
        viewModel.onEvent(StravaUiEvent.OpenFailed)
        assertEquals(StravaMessage.NO_BROWSER, viewModel.uiState.value.message)
    }
}
