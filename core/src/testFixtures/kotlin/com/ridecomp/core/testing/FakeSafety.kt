package com.ridecomp.core.testing

import com.ridecomp.core.engine.safety.AccelSample
import com.ridecomp.core.model.EmergencyContact
import com.ridecomp.core.model.IncidentRecord
import com.ridecomp.core.model.SafetySettings
import com.ridecomp.core.repository.IncidentRepository
import com.ridecomp.core.repository.SafetyRepository
import com.ridecomp.core.source.AccelerometerSource
import com.ridecomp.core.source.PickedContact
import com.ridecomp.core.source.PickedContactReader
import com.ridecomp.core.source.SafetyMessages
import com.ridecomp.core.source.SmsGateway
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart

public class FakeSafetyRepository : SafetyRepository {
    public val settings: MutableStateFlow<SafetySettings> = MutableStateFlow(SafetySettings())
    public val contacts: MutableStateFlow<List<EmergencyContact>> = MutableStateFlow(emptyList())
    private var lastTestSmsAtMs: Long? = null
    private var nextId = 1L

    override fun observeSettings(): Flow<SafetySettings> = settings

    override suspend fun getSettings(): SafetySettings = settings.value

    override suspend fun saveSettings(settings: SafetySettings) {
        this.settings.value = settings
    }

    override fun observeContacts(): Flow<List<EmergencyContact>> = contacts

    override suspend fun getContacts(): List<EmergencyContact> = contacts.value

    override suspend fun addContact(contact: EmergencyContact) {
        contacts.value = contacts.value + contact.copy(id = nextId++)
    }

    override suspend fun removeContact(id: Long) {
        contacts.value = contacts.value.filterNot { it.id == id }
    }

    override suspend fun getLastTestSmsAtMs(): Long? = lastTestSmsAtMs

    override suspend fun setLastTestSmsAtMs(atMs: Long) {
        lastTestSmsAtMs = atMs
    }
}

/** Records every message; [failingPhones] simulate no signal / no credit. */
public class FakeSmsGateway(public var isPermitted: Boolean = true) : SmsGateway {
    public val sent: MutableList<Pair<String, String>> = mutableListOf()
    public var attempts: Int = 0
        private set
    public val failingPhones: MutableSet<String> = mutableSetOf()

    override fun hasPermission(): Boolean = isPermitted

    override suspend fun send(phone: String, text: String): Boolean {
        attempts++
        if (phone in failingPhones) return false
        sent += phone to text
        return true
    }
}

public class FakeSafetyMessages : SafetyMessages {
    override fun test(riderName: String): String = "$TEST $riderName"

    override fun incident(riderName: String, atMs: Long, latDeg: Double?, lonDeg: Double?, accuracyM: Double?): String =
        "$INCIDENT $riderName $latDeg,$lonDeg"

    override fun safe(riderName: String): String = "$SAFE $riderName"

    public companion object {
        public const val TEST: String = "TEST"
        public const val INCIDENT: String = "INCIDENT"
        public const val SAFE: String = "SAFE"
    }
}

public class FakePickedContactReader(public var result: PickedContact? = null) : PickedContactReader {
    override suspend fun read(uri: String): PickedContact? = result
}

/** Tests emit samples; [activeCollectors] proves the sensor is released when not recording (BAT-01). */
public class FakeAccelerometerSource(override val hasAccelerometer: Boolean = true) : AccelerometerSource {
    public val samplesFlow: MutableSharedFlow<AccelSample> = MutableSharedFlow(extraBufferCapacity = 256)
    public var activeCollectors: Int = 0
        private set

    override fun samples(): Flow<AccelSample> = samplesFlow
        .onStart { activeCollectors++ }
        .onCompletion { activeCollectors-- }
}

public class FakeIncidentRepository : IncidentRepository {
    public val saved: MutableMap<Long, IncidentRecord> = linkedMapOf()
    private var nextId = 1L

    override suspend fun save(incident: IncidentRecord): Long {
        val id = if (incident.id == 0L) nextId++ else incident.id
        saved[id] = incident.copy(id = id)
        return id
    }

    override suspend fun getForActivity(activityId: Long): List<IncidentRecord> =
        saved.values.filter { it.activityId == activityId }
}
