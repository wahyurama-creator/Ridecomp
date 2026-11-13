package com.ridecomp.core.model

/** Limits of the safety feature (12-FEATURE-SPECS §5, SEC-SMS-03). */
public object SafetyConfig {
    public const val MAX_CONTACTS: Int = 3
    public const val NAME_MAX_LENGTH: Int = 40

    /** ITU-T E.164: at most 15 digits; shorter than this cannot be a phone number. */
    public const val PHONE_MIN_DIGITS: Int = 6
    public const val PHONE_MAX_DIGITS: Int = 15

    /** Test SMS at most once per minute (SEC-SMS-03). */
    public const val TEST_SMS_MIN_INTERVAL_MS: Long = 60_000L
}

/**
 * Emergency contact (05-DATA-MODEL `emergency_contacts`). Third-party personal data: never logged,
 * exported or shared (SEC-SMS-05).
 */
public data class EmergencyContact(
    val id: Long = 0,
    val displayName: String,
    /** Normalized: optional leading `+`, then digits only ([PhoneNumbers.normalize]). */
    val phone: String,
    val sortOrder: Int = 0,
) {
    /** Never print the number (SEC-SMS-05). */
    override fun toString(): String = "EmergencyContact(id=$id)"
}

/** Safety screen settings (stored in `settings`). Disabled until at least one contact exists. */
public data class SafetySettings(val isEnabled: Boolean = false, val riderName: String = "")

public object PhoneNumbers {
    private const val PLUS = '+'

    /**
     * Keeps an optional leading `+` and the digits; spaces, dashes, dots and brackets are dropped. Returns
     * null when the result is not a plausible number (SEC-IN-01: external input is validated).
     */
    public fun normalize(raw: String): String? {
        val trimmed = raw.trim()
        val hasPlus = trimmed.startsWith(PLUS)
        val body = if (hasPlus) trimmed.drop(1) else trimmed
        if (body.any { !it.isDigit() && it !in SEPARATORS }) return null
        val digits = body.filter(Char::isDigit)
        return digits.takeIf { it.length in SafetyConfig.PHONE_MIN_DIGITS..SafetyConfig.PHONE_MAX_DIGITS }
            ?.let { if (hasPlus) "$PLUS$it" else it }
    }

    private val SEPARATORS = setOf(' ', '-', '.', '(', ')')
}

/** Stored outcome of an incident (05-DATA-MODEL `incidents.outcome`). */
public enum class IncidentOutcome { PENDING, CANCELLED, SENT, MANUAL_SOS, FAILED }

/** One incident row; no phone numbers or message texts are stored here (SEC-SMS-05). */
public data class IncidentRecord(
    val id: Long = 0,
    val activityId: Long,
    val detectedAtMs: Long,
    val peakG: Double?,
    val speedBeforeMps: Double?,
    val outcome: IncidentOutcome,
    val smsSentCount: Int = 0,
)
