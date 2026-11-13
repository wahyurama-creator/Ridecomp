package com.ridecomp.core.engine.safety

import com.ridecomp.core.config.Units
import com.ridecomp.core.model.EmergencyContact
import com.ridecomp.core.model.IncidentOutcome
import com.ridecomp.core.model.IncidentRecord
import com.ridecomp.core.model.LocationFix
import com.ridecomp.core.model.RideEvent
import com.ridecomp.core.model.RideEventType
import com.ridecomp.core.repository.ActivityRepository
import com.ridecomp.core.repository.IncidentRepository
import com.ridecomp.core.repository.SafetyRepository
import com.ridecomp.core.source.Logger
import com.ridecomp.core.source.SafetyMessages
import com.ridecomp.core.source.SmsGateway
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Timing and hard limits of the incident flow (12-FEATURE-SPECS §5.3, SEC-SMS-03). */
public object IncidentFlowConfig {
    public const val COUNTDOWN_MS: Long = 30_000L

    /** Manual SOS (long press): short countdown. */
    public const val SOS_COUNTDOWN_MS: Long = 5_000L
    public const val LOCATION_UPDATE_AFTER_MS: Long = 5L * Units.MS_PER_MINUTE
    public const val RETRY_INTERVAL_MS: Long = 60_000L
    public const val MAX_RETRIES: Int = 5

    /** Hard cap per contact per incident: alert + location update + "I am safe". */
    public const val MAX_SMS_PER_CONTACT: Int = 3
}

/** What the ride screen shows about an incident. */
public sealed interface IncidentState {
    public data object None : IncidentState

    /** Full-screen countdown with "I am OK" and "Send now" (12-FEATURE-SPECS §5.3 step 1). */
    public data class Countdown(val deadlineMs: Long, val isManual: Boolean) : IncidentState

    /** Messages went out (or are being retried); "I am safe" is offered. */
    public data class Alerted(val sentContacts: Int, val failedContacts: Int, val isRetrying: Boolean) : IncidentState
}

/** Actions on an incident, from the ride screen or the notification. */
public enum class IncidentAction { CANCEL, SEND_NOW, SAFE, SOS }

/**
 * The incident flow (12-FEATURE-SPECS §5.3): countdown → SMS to every contact → one location update after
 * 5 minutes → "I am safe". Failed sends are retried every 60 s, at most 5 times; never more than
 * [IncidentFlowConfig.MAX_SMS_PER_CONTACT] messages per contact per incident (SEC-SMS-03). The only
 * caller of [SmsGateway] besides the test SMS (SEC-SMS-02). Calls are serialized by a mutex; sending may
 * take seconds, so the caller runs it outside the recording lock.
 */
public class IncidentManager(
    private val safety: SafetyRepository,
    private val sms: SmsGateway,
    private val messages: SafetyMessages,
    private val incidents: IncidentRepository,
    private val activities: ActivityRepository,
    private val logger: Logger,
) {
    private val lock = Mutex()
    private val _state = MutableStateFlow<IncidentState>(IncidentState.None)
    public val state: StateFlow<IncidentState> = _state.asStateFlow()

    private var activityId: Long? = null
    private var record: IncidentRecord? = null
    private var isManual = false
    private var deadlineMs = 0L
    private val sentPerContact = mutableMapOf<Long, Int>()
    private val failedContacts = mutableSetOf<Long>()
    private var retries = 0
    private var nextRetryAtMs: Long? = null
    private var firstSentAtMs: Long? = null
    private var isUpdateSent = false

    /** True when incident detection is on and someone can be alerted. */
    public suspend fun isArmed(): Boolean = safety.getSettings().isEnabled && safety.getContacts().isNotEmpty()

    /** Automatic detection: starts the 30 s countdown (ignored while an incident is already open). */
    public suspend fun onDetected(activityId: Long, detection: IncidentDetection, nowMs: Long): Unit = lock.withLock {
        if (_state.value != IncidentState.None || !isArmed()) return@withLock
        open(activityId, nowMs, isManual = false, detection)
        appendEvent(nowMs, RideEventType.INCIDENT_DETECTED)
    }

    public suspend fun onAction(action: IncidentAction, activityId: Long?, nowMs: Long, fix: LocationFix?): Unit =
        lock.withLock {
            when (action) {
                IncidentAction.SOS -> if (_state.value == IncidentState.None && activityId != null && isArmed()) {
                    open(activityId, nowMs, isManual = true, detection = null)
                }
                IncidentAction.CANCEL -> if (_state.value is IncidentState.Countdown) cancel(nowMs)
                IncidentAction.SEND_NOW -> if (_state.value is IncidentState.Countdown) sendAlerts(nowMs, fix)
                IncidentAction.SAFE -> if (_state.value is IncidentState.Alerted) markSafe()
            }
        }

    /** Once per second: countdown expiry, retries and the location update. */
    public suspend fun tick(nowMs: Long, fix: LocationFix?): Unit = lock.withLock {
        when (_state.value) {
            IncidentState.None -> Unit
            is IncidentState.Countdown -> if (nowMs >= deadlineMs) sendAlerts(nowMs, fix)
            is IncidentState.Alerted -> {
                retryIfDue(nowMs, fix)
                sendUpdateIfDue(nowMs, fix)
            }
        }
    }

    /** The ride ended: an open countdown is cancelled; sent alerts stay as they are. */
    public suspend fun close(nowMs: Long): Unit = lock.withLock {
        if (_state.value is IncidentState.Countdown) cancel(nowMs)
        clear()
    }

    private suspend fun open(activityId: Long, nowMs: Long, isManual: Boolean, detection: IncidentDetection?) {
        clear()
        this.activityId = activityId
        this.isManual = isManual
        deadlineMs = nowMs + if (isManual) IncidentFlowConfig.SOS_COUNTDOWN_MS else IncidentFlowConfig.COUNTDOWN_MS
        val newRecord = IncidentRecord(
            activityId = activityId,
            detectedAtMs = detection?.impactAtMs ?: nowMs,
            peakG = detection?.peakG,
            speedBeforeMps = detection?.speedBeforeMps,
            outcome = IncidentOutcome.PENDING,
        )
        record = newRecord.copy(id = incidents.save(newRecord))
        _state.value = IncidentState.Countdown(deadlineMs, isManual)
    }

    private suspend fun cancel(nowMs: Long) {
        saveRecord(IncidentOutcome.CANCELLED)
        appendEvent(nowMs, RideEventType.INCIDENT_CANCELLED)
        clear()
    }

    private suspend fun sendAlerts(nowMs: Long, fix: LocationFix?) {
        val contacts = safety.getContacts()
        val text = messages.incident(safety.getSettings().riderName, nowMs, fix?.latDeg, fix?.lonDeg, fix?.accuracyM)
        sendTo(contacts, text)
        nextRetryAtMs = nowMs + IncidentFlowConfig.RETRY_INTERVAL_MS
        if (sentPerContact.isNotEmpty()) firstSentAtMs = nowMs
        afterSend(nowMs)
    }

    private suspend fun retryIfDue(nowMs: Long, fix: LocationFix?) {
        val dueAt = nextRetryAtMs ?: return
        if (failedContacts.isEmpty() || retries >= IncidentFlowConfig.MAX_RETRIES || nowMs < dueAt) return
        retries++
        val contacts = safety.getContacts().filter { it.id in failedContacts }
        val text = messages.incident(safety.getSettings().riderName, nowMs, fix?.latDeg, fix?.lonDeg, fix?.accuracyM)
        sendTo(contacts, text)
        nextRetryAtMs = nowMs + IncidentFlowConfig.RETRY_INTERVAL_MS
        if (firstSentAtMs == null && sentPerContact.isNotEmpty()) firstSentAtMs = nowMs
        afterSend(nowMs)
    }

    private suspend fun sendUpdateIfDue(nowMs: Long, fix: LocationFix?) {
        val firstSent = firstSentAtMs ?: return
        if (isUpdateSent || nowMs - firstSent < IncidentFlowConfig.LOCATION_UPDATE_AFTER_MS) return
        isUpdateSent = true
        val text = messages.incident(safety.getSettings().riderName, nowMs, fix?.latDeg, fix?.lonDeg, fix?.accuracyM)
        sendTo(alertedContacts(), text, isRetryable = false)
        publishAlerted()
    }

    private suspend fun markSafe() {
        sendTo(alertedContacts(), messages.safe(safety.getSettings().riderName), isRetryable = false)
        saveRecord(record?.outcome ?: IncidentOutcome.SENT)
        clear()
    }

    private suspend fun alertedContacts(): List<EmergencyContact> =
        safety.getContacts().filter { (sentPerContact[it.id] ?: 0) > 0 }

    /** Sends [text] to [contacts] within the per-contact cap; failures are remembered for retry. */
    private suspend fun sendTo(contacts: List<EmergencyContact>, text: String, isRetryable: Boolean = true) {
        contacts.forEach { contact ->
            val sent = sentPerContact[contact.id] ?: 0
            if (sent >= IncidentFlowConfig.MAX_SMS_PER_CONTACT) return@forEach
            if (sms.send(contact.phone, text)) {
                sentPerContact[contact.id] = sent + 1
                failedContacts -= contact.id
            } else if (isRetryable) {
                failedContacts += contact.id
            }
        }
    }

    private suspend fun afterSend(nowMs: Long) {
        if (sentPerContact.isNotEmpty()) appendEvent(nowMs, RideEventType.SOS_SENT)
        if (failedContacts.isNotEmpty()) {
            appendEvent(nowMs, RideEventType.SMS_FAILED)
            logger.warn(TAG) { "Emergency SMS failed for ${failedContacts.size} contact(s)" }
        }
        val outcome = when {
            sentPerContact.isEmpty() -> IncidentOutcome.FAILED
            isManual -> IncidentOutcome.MANUAL_SOS
            else -> IncidentOutcome.SENT
        }
        saveRecord(outcome)
        publishAlerted()
    }

    private fun publishAlerted() {
        _state.value = IncidentState.Alerted(
            sentContacts = sentPerContact.size,
            failedContacts = failedContacts.size,
            isRetrying = failedContacts.isNotEmpty() && retries < IncidentFlowConfig.MAX_RETRIES,
        )
    }

    private suspend fun saveRecord(outcome: IncidentOutcome) {
        val current = record ?: return
        val updated = current.copy(outcome = outcome, smsSentCount = sentPerContact.values.sum())
        incidents.save(updated)
        record = updated
    }

    private suspend fun appendEvent(nowMs: Long, type: RideEventType) {
        val id = activityId ?: return
        activities.appendEvents(id, listOf(RideEvent(nowMs, type)))
    }

    private fun clear() {
        _state.value = IncidentState.None
        activityId = null
        record = null
        isManual = false
        sentPerContact.clear()
        failedContacts.clear()
        retries = 0
        nextRetryAtMs = null
        firstSentAtMs = null
        isUpdateSent = false
    }

    private companion object {
        const val TAG = "IncidentManager"
    }
}
