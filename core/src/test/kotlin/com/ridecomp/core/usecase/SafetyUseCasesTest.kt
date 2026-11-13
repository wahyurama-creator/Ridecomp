package com.ridecomp.core.usecase

import com.ridecomp.core.model.EmergencyContact
import com.ridecomp.core.model.PhoneNumbers
import com.ridecomp.core.model.SafetyConfig
import com.ridecomp.core.source.PickedContact
import com.ridecomp.core.testing.FakeClock
import com.ridecomp.core.testing.FakePickedContactReader
import com.ridecomp.core.testing.FakeSafetyMessages
import com.ridecomp.core.testing.FakeSafetyRepository
import com.ridecomp.core.testing.FakeSmsGateway
import com.ridecomp.core.util.Outcome
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class SafetyUseCasesTest {
    private val repository = FakeSafetyRepository()
    private val sms = FakeSmsGateway()
    private val clock = FakeClock()
    private val add = AddEmergencyContactUseCase(repository)

    @Test
    fun `phone numbers are normalized and validated`() {
        assertEquals("+6281234567890", PhoneNumbers.normalize(" +62 812-3456-7890 "))
        assertEquals("081234567890", PhoneNumbers.normalize("(0812) 3456.7890"))
        assertNull(PhoneNumbers.normalize("12345"))
        assertNull(PhoneNumbers.normalize("0812abc4567"))
        assertNull(PhoneNumbers.normalize("+1234567890123456"))
        assertFalse(EmergencyContact(displayName = "Ibu", phone = "0812345678").toString().contains("0812"))
    }

    @Test
    fun `at most three contacts, invalid numbers are refused`() = runTest {
        assertEquals(Outcome.Failure(SafetyError.INVALID_PHONE), add("Ibu", "abc"))
        repeat(SafetyConfig.MAX_CONTACTS) { assertTrue(add("C$it", "08123456789$it") is Outcome.Success) }
        assertEquals(Outcome.Failure(SafetyError.TOO_MANY_CONTACTS), add("Extra", "0812345678"))
        assertEquals(listOf(0, 1, 2), repository.contacts.value.map { it.sortOrder })
    }

    @Test
    fun `picked contact is read and added, blank names fall back to the number`() = runTest {
        val reader = FakePickedContactReader(PickedContact(displayName = " ", phone = "0812 345 678"))

        AddPickedContactUseCase(reader, add)("content://picked")

        assertEquals("0812345678", repository.contacts.value.single().displayName)
        reader.result = null
        assertEquals(Outcome.Failure(SafetyError.INVALID_PHONE), AddPickedContactUseCase(reader, add)("content://x"))
    }

    @Test
    fun `enabling needs a contact and the sms permission, removing the last contact disables`() = runTest {
        val enable = SetSafetyEnabledUseCase(repository, sms)

        assertEquals(Outcome.Failure(SafetyError.NO_CONTACTS), enable(true))
        add("Ibu", "0812345678")
        sms.isPermitted = false
        assertEquals(Outcome.Failure(SafetyError.NO_SMS_PERMISSION), enable(true))
        sms.isPermitted = true
        assertTrue(enable(true) is Outcome.Success)
        assertTrue(repository.settings.value.isEnabled)

        RemoveEmergencyContactUseCase(repository)(repository.contacts.value.single().id)
        assertFalse(repository.settings.value.isEnabled)
    }

    @Test
    fun `test sms goes to every contact at most once per minute`() = runTest {
        SaveRiderNameUseCase(repository)("  Rama  ")
        add("Ibu", "0811111111")
        add("Ayah", "0822222222")
        val sendTest = SendTestSmsUseCase(repository, sms, FakeSafetyMessages(), clock)

        assertEquals(Outcome.Success(2), sendTest())
        assertEquals("${FakeSafetyMessages.TEST} Rama", sms.sent.first().second)
        assertEquals(Outcome.Failure(SafetyError.TOO_SOON), sendTest())

        clock.advanceBy(SafetyConfig.TEST_SMS_MIN_INTERVAL_MS)
        sms.failingPhones += setOf("0811111111", "0822222222")
        assertEquals(Outcome.Failure(SafetyError.SEND_FAILED), sendTest())
        assertEquals(2, sms.sent.size)
    }
}
