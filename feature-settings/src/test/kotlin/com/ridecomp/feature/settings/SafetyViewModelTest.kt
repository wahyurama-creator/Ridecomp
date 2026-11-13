package com.ridecomp.feature.settings

import app.cash.turbine.test
import com.ridecomp.core.source.PickedContact
import com.ridecomp.core.testing.FakeClock
import com.ridecomp.core.testing.FakePickedContactReader
import com.ridecomp.core.testing.FakeSafetyMessages
import com.ridecomp.core.testing.FakeSafetyRepository
import com.ridecomp.core.testing.FakeSmsGateway
import com.ridecomp.core.usecase.AddEmergencyContactUseCase
import com.ridecomp.core.usecase.AddPickedContactUseCase
import com.ridecomp.core.usecase.ObserveSafetyUseCase
import com.ridecomp.core.usecase.RemoveEmergencyContactUseCase
import com.ridecomp.core.usecase.SafetyError
import com.ridecomp.core.usecase.SaveRiderNameUseCase
import com.ridecomp.core.usecase.SendTestSmsUseCase
import com.ridecomp.core.usecase.SetSafetyEnabledUseCase
import com.ridecomp.feature.settings.viewmodel.SafetyEffect
import com.ridecomp.feature.settings.viewmodel.SafetyMessage
import com.ridecomp.feature.settings.viewmodel.SafetyUiEvent
import com.ridecomp.feature.settings.viewmodel.SafetyUseCases
import com.ridecomp.feature.settings.viewmodel.SafetyViewModel
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

@OptIn(ExperimentalCoroutinesApi::class)
class SafetyViewModelTest {
    private val repository = FakeSafetyRepository()
    private val sms = FakeSmsGateway(isPermitted = false)
    private val reader = FakePickedContactReader()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(): SafetyViewModel {
        val add = AddEmergencyContactUseCase(repository)
        return SafetyViewModel(
            SafetyUseCases(
                ObserveSafetyUseCase(repository),
                SetSafetyEnabledUseCase(repository, sms),
                SaveRiderNameUseCase(repository),
                add,
                AddPickedContactUseCase(reader, add),
                RemoveEmergencyContactUseCase(repository),
                SendTestSmsUseCase(repository, sms, FakeSafetyMessages(), FakeClock()),
            ),
        )
    }

    @Test
    fun `enabling asks for contacts first, then for the sms permission only once`() = runTest {
        val viewModel = viewModel()

        viewModel.onEvent(SafetyUiEvent.Enable(true))
        assertEquals(SafetyMessage.Error(SafetyError.NO_CONTACTS), viewModel.uiState.value.message)

        viewModel.onEvent(SafetyUiEvent.NewPhone("0812 3456 789"))
        viewModel.onEvent(SafetyUiEvent.AddTyped)
        assertEquals("", viewModel.uiState.value.newPhone)
        viewModel.effects.test {
            viewModel.onEvent(SafetyUiEvent.Enable(true))
            assertEquals(SafetyEffect.RequestSmsPermission, awaitItem())
        }
        // Permission denied: no second dialog, a message instead (SEC-SMS-01).
        viewModel.onEvent(SafetyUiEvent.SmsPermissionResult)
        assertEquals(SafetyMessage.Error(SafetyError.NO_SMS_PERMISSION), viewModel.uiState.value.message)
        assertFalse(viewModel.uiState.value.isEnabled)

        sms.isPermitted = true
        viewModel.onEvent(SafetyUiEvent.SmsPermissionResult)
        assertTrue(viewModel.uiState.value.isEnabled)
    }

    @Test
    fun `picked contacts are added and the test sms reports how many were sent`() = runTest {
        sms.isPermitted = true
        reader.result = PickedContact("Ibu", "+62 811 1111 111")
        val viewModel = viewModel()

        viewModel.onEvent(SafetyUiEvent.Picked("content://com.android.contacts/data/1"))
        viewModel.onEvent(SafetyUiEvent.RiderName("Rama"))
        viewModel.onEvent(SafetyUiEvent.SendTest)

        assertEquals(listOf("Ibu"), viewModel.uiState.value.contacts.map { it.displayName })
        assertEquals(SafetyMessage.TestSent(1), viewModel.uiState.value.message)
        assertEquals("Rama", repository.settings.value.riderName)
        viewModel.onEvent(SafetyUiEvent.MessageShown)
        viewModel.onEvent(SafetyUiEvent.Remove(viewModel.uiState.value.contacts.single().id))
        assertTrue(viewModel.uiState.value.contacts.isEmpty())
    }
}
