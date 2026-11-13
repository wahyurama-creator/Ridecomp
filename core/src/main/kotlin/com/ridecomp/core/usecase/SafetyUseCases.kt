package com.ridecomp.core.usecase

import com.ridecomp.core.model.EmergencyContact
import com.ridecomp.core.model.PhoneNumbers
import com.ridecomp.core.model.SafetyConfig
import com.ridecomp.core.model.SafetySettings
import com.ridecomp.core.repository.SafetyRepository
import com.ridecomp.core.source.Clock
import com.ridecomp.core.source.PickedContactReader
import com.ridecomp.core.source.SafetyMessages
import com.ridecomp.core.source.SmsGateway
import com.ridecomp.core.util.Outcome
import com.ridecomp.core.util.asFailure
import com.ridecomp.core.util.asSuccess
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/** What the Safety screen shows (02-DESIGN §3.10). */
public data class SafetyState(val settings: SafetySettings, val contacts: List<EmergencyContact>) {
    public val canAddContact: Boolean get() = contacts.size < SafetyConfig.MAX_CONTACTS
}

public class ObserveSafetyUseCase(private val repository: SafetyRepository) {
    public operator fun invoke(): Flow<SafetyState> =
        combine(repository.observeSettings(), repository.observeContacts(), ::SafetyState)
}

public class SaveRiderNameUseCase(private val repository: SafetyRepository) {
    public suspend operator fun invoke(name: String) {
        val settings = repository.getSettings()
        repository.saveSettings(settings.copy(riderName = name.trim().take(SafetyConfig.NAME_MAX_LENGTH)))
    }
}

public enum class SafetyError {
    NO_CONTACTS,
    NO_SMS_PERMISSION,
    INVALID_PHONE,
    TOO_MANY_CONTACTS,
    TOO_SOON,
    SEND_FAILED,
}

/**
 * Turns incident detection on or off. It can only be on with at least one contact and the SMS permission
 * (asked only now, SEC-SMS-01).
 */
public class SetSafetyEnabledUseCase(private val repository: SafetyRepository, private val sms: SmsGateway) {
    public suspend operator fun invoke(isEnabled: Boolean): Outcome<Unit, SafetyError> {
        val error = when {
            !isEnabled -> null
            repository.getContacts().isEmpty() -> SafetyError.NO_CONTACTS
            !sms.hasPermission() -> SafetyError.NO_SMS_PERMISSION
            else -> null
        }
        if (error != null) return error.asFailure()
        repository.saveSettings(repository.getSettings().copy(isEnabled = isEnabled))
        return Unit.asSuccess()
    }
}

/** Adds a typed or picked contact after validating the number; at most [SafetyConfig.MAX_CONTACTS]. */
public class AddEmergencyContactUseCase(private val repository: SafetyRepository) {
    public suspend operator fun invoke(displayName: String, rawPhone: String): Outcome<Unit, SafetyError> {
        val phone = PhoneNumbers.normalize(rawPhone)
        val contacts = repository.getContacts()
        return when {
            phone == null -> SafetyError.INVALID_PHONE.asFailure()
            contacts.size >= SafetyConfig.MAX_CONTACTS -> SafetyError.TOO_MANY_CONTACTS.asFailure()
            else -> {
                val name = displayName.trim().take(SafetyConfig.NAME_MAX_LENGTH).ifBlank { phone }
                repository.addContact(EmergencyContact(displayName = name, phone = phone, sortOrder = contacts.size))
                Unit.asSuccess()
            }
        }
    }
}

/** Reads a contact returned by the system picker and adds it. */
public class AddPickedContactUseCase(
    private val reader: PickedContactReader,
    private val add: AddEmergencyContactUseCase,
) {
    public suspend operator fun invoke(uri: String): Outcome<Unit, SafetyError> {
        val picked = reader.read(uri) ?: return SafetyError.INVALID_PHONE.asFailure()
        return add(picked.displayName, picked.phone)
    }
}

/** Removing the last contact also turns the feature off (no SMS without a recipient). */
public class RemoveEmergencyContactUseCase(private val repository: SafetyRepository) {
    public suspend operator fun invoke(id: Long) {
        repository.removeContact(id)
        if (repository.getContacts().isEmpty()) {
            repository.saveSettings(repository.getSettings().copy(isEnabled = false))
        }
    }
}

/** "Send test SMS" to every contact, labelled TEST, at most once per minute (SEC-SMS-03). */
public class SendTestSmsUseCase(
    private val repository: SafetyRepository,
    private val sms: SmsGateway,
    private val messages: SafetyMessages,
    private val clock: Clock,
) {
    public suspend operator fun invoke(): Outcome<Int, SafetyError> {
        val contacts = repository.getContacts()
        val now = clock.nowMs()
        val last = repository.getLastTestSmsAtMs()
        val error = when {
            contacts.isEmpty() -> SafetyError.NO_CONTACTS
            !sms.hasPermission() -> SafetyError.NO_SMS_PERMISSION
            last != null && now - last < SafetyConfig.TEST_SMS_MIN_INTERVAL_MS -> SafetyError.TOO_SOON
            else -> null
        }
        if (error != null) return error.asFailure()
        repository.setLastTestSmsAtMs(now)
        val text = messages.test(repository.getSettings().riderName)
        val sent = contacts.count { sms.send(it.phone, text) }
        return if (sent > 0) sent.asSuccess() else SafetyError.SEND_FAILED.asFailure()
    }
}
