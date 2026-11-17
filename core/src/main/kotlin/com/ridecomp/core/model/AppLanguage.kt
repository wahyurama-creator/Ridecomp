package com.ridecomp.core.model

/** Language of the app's texts (owner request): chosen in onboarding and Settings, or the phone's. */
public enum class AppLanguage(
    /** BCP 47 tag; null = follow the phone. */
    public val tag: String?,
) {
    SYSTEM(null),
    INDONESIAN("id"),
    ENGLISH("en"),
    ;

    public companion object {
        /** The stored tag back to a language; unknown or empty = follow the phone. */
        public fun fromTag(tag: String?): AppLanguage =
            entries.firstOrNull { it.tag != null && it.tag == tag?.substringBefore('-') } ?: SYSTEM
    }
}
