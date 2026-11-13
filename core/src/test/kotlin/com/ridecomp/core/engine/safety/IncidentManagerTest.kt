package com.ridecomp.core.engine.safety

import com.ridecomp.core.model.EmergencyContact
import com.ridecomp.core.model.IncidentOutcome
import com.ridecomp.core.model.LocationFix
import com.ridecomp.core.model.RideEventType
import com.ridecomp.core.model.SafetySettings
import com.ridecomp.core.repository.NewActivity
import com.ridecomp.core.testing.FakeActivityRepository
import com.ridecomp.core.testing.FakeIncidentRepository
import com.ridecomp.core.testing.FakeSafetyMessages
import com.ridecomp.core.testing.FakeSafetyRepository
import com.ridecomp.core.testing.FakeSmsGateway
import com.ridecomp.core.testing.RecordingLogger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/** 07-TEST-PLAN (Deteksi insiden): cancel → no SMS; 3-SMS cap; retries on failure; manual SOS. */
class IncidentManagerTest {
    private val safety = FakeSafetyRepository()
    private val sms = FakeSmsGateway()
    private val incidents = FakeIncidentRepository()
    private val activities = FakeActivityRepository()
    private val logger = RecordingLogger()
    private val manager = IncidentManager(safety, sms, FakeSafetyMessages(), incidents, activities, logger)
    private val detection = IncidentDetection(impactAtMs = T0, peakG = 4.2, speedBeforeMps = 6.0)
    private val fix = LocationFix(T0, -7.78, 110.37, null, 5.0, null)

    private suspend fun setUp(contacts: Int = 2, isEnabled: Boolean = true): Long {
        repeat(contacts) { safety.addContact(EmergencyContact(displayName = "C$it", phone = "08110000000$it")) }
        safety.saveSettings(SafetySettings(isEnabled = isEnabled, riderName = "Rama"))
        return activities.create(NewActivity("u", "Ride", T0))
    }

    private fun events(id: Long) = activities.events[id].orEmpty().map { it.type }

    @Test
    fun `cancelled countdown sends nothing`() = runTest {
        val id = setUp()
        manager.onDetected(id, detection, T0)
        assertTrue(manager.state.value is IncidentState.Countdown)

        manager.onAction(IncidentAction.CANCEL, id, T0 + 10_000, fix)
        manager.tick(T0 + IncidentFlowConfig.COUNTDOWN_MS, fix)

        assertEquals(IncidentState.None, manager.state.value)
        assertEquals(0, sms.attempts)
        assertEquals(listOf(RideEventType.INCIDENT_DETECTED, RideEventType.INCIDENT_CANCELLED), events(id))
        assertEquals(IncidentOutcome.CANCELLED, incidents.saved.values.single().outcome)
    }

    @Test
    fun `countdown expiry alerts every contact, then one location update and the safe message`() = runTest {
        val id = setUp()
        manager.onDetected(id, detection, T0)
        manager.tick(T0 + IncidentFlowConfig.COUNTDOWN_MS - 1, fix)
        assertEquals(0, sms.attempts)

        val sentAt = T0 + IncidentFlowConfig.COUNTDOWN_MS
        manager.tick(sentAt, fix)
        assertEquals(
            IncidentState.Alerted(sentContacts = 2, failedContacts = 0, isRetrying = false),
            manager.state.value,
        )
        assertTrue(sms.sent.all { it.second.startsWith(FakeSafetyMessages.INCIDENT) })

        manager.tick(sentAt + IncidentFlowConfig.LOCATION_UPDATE_AFTER_MS, fix)
        manager.tick(sentAt + 2 * IncidentFlowConfig.LOCATION_UPDATE_AFTER_MS, fix)
        assertEquals(4, sms.sent.size)

        manager.onAction(IncidentAction.SAFE, id, sentAt + 3 * IncidentFlowConfig.LOCATION_UPDATE_AFTER_MS, fix)
        assertEquals(6, sms.sent.size)
        assertTrue(sms.sent.takeLast(2).all { it.second.startsWith(FakeSafetyMessages.SAFE) })
        assertEquals(IncidentState.None, manager.state.value)
        assertEquals(IncidentOutcome.SENT, incidents.saved.values.single().outcome)
        assertEquals(6, incidents.saved.values.single().smsSentCount)
        // Never more than 3 messages per contact per incident (SEC-SMS-03).
        assertTrue(sms.sent.groupBy { it.first }.values.all { it.size <= IncidentFlowConfig.MAX_SMS_PER_CONTACT })
    }

    @Test
    fun `failed sends are retried every minute at most five times`() = runTest {
        val id = setUp(contacts = 1)
        sms.failingPhones += "081100000000"
        manager.onDetected(id, detection, T0)
        val sentAt = T0 + IncidentFlowConfig.COUNTDOWN_MS
        manager.tick(sentAt, fix)
        assertEquals(IncidentOutcome.FAILED, incidents.saved.values.single().outcome)

        repeat(10) { minute -> manager.tick(sentAt + (minute + 1) * IncidentFlowConfig.RETRY_INTERVAL_MS, fix) }

        assertEquals(1 + IncidentFlowConfig.MAX_RETRIES, sms.attempts)
        assertEquals(
            IncidentState.Alerted(sentContacts = 0, failedContacts = 1, isRetrying = false),
            manager.state.value,
        )
        assertTrue(RideEventType.SMS_FAILED in events(id))
    }

    @Test
    fun `a retry that succeeds is recorded as sent`() = runTest {
        val id = setUp(contacts = 1)
        sms.failingPhones += "081100000000"
        manager.onDetected(id, detection, T0)
        manager.onAction(IncidentAction.SEND_NOW, id, T0 + 1_000, fix)

        sms.failingPhones.clear()
        manager.tick(T0 + 1_000 + IncidentFlowConfig.RETRY_INTERVAL_MS, fix)

        assertEquals(IncidentOutcome.SENT, incidents.saved.values.single().outcome)
        assertTrue(RideEventType.SOS_SENT in events(id))
    }

    @Test
    fun `manual sos uses the short countdown`() = runTest {
        val id = setUp()
        manager.onAction(IncidentAction.SOS, id, T0, fix)

        assertEquals(
            IncidentState.Countdown(T0 + IncidentFlowConfig.SOS_COUNTDOWN_MS, isManual = true),
            manager.state.value,
        )
        manager.tick(T0 + IncidentFlowConfig.SOS_COUNTDOWN_MS, fix)
        assertEquals(2, sms.sent.size)
        assertEquals(IncidentOutcome.MANUAL_SOS, incidents.saved.values.single().outcome)
    }

    @Test
    fun `nothing happens when the feature is off or no contact exists`() = runTest {
        val id = setUp(contacts = 0)
        manager.onDetected(id, detection, T0)
        manager.onAction(IncidentAction.SOS, id, T0, fix)
        assertEquals(IncidentState.None, manager.state.value)

        safety.addContact(EmergencyContact(displayName = "C", phone = "0811000000"))
        safety.saveSettings(SafetySettings(isEnabled = false))
        manager.onDetected(id, detection, T0)
        assertEquals(IncidentState.None, manager.state.value)
        assertEquals(0, sms.attempts)
    }

    @Test
    fun `ending the ride cancels an open countdown`() = runTest {
        val id = setUp()
        manager.onDetected(id, detection, T0)

        manager.close(T0 + 1_000)

        assertEquals(IncidentState.None, manager.state.value)
        assertEquals(IncidentOutcome.CANCELLED, incidents.saved.values.single().outcome)
    }

    private companion object {
        const val T0 = 1_700_000_000_000L
    }
}
