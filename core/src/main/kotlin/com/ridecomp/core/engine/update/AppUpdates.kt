package com.ridecomp.core.engine.update

/** In-app update check (T10.9, D-103): RideComp is sideloaded, so it looks at its own GitHub releases. */
public object UpdateConfig {
    /** Automatic checks happen at most this often, when the app is opened (never in the background). */
    public const val CHECK_INTERVAL_MS: Long = 24L * 60 * 60 * 1_000

    /** Only a release page of the project's own repository is ever opened. */
    public const val RELEASE_PAGE_PREFIX: String = "https://github.com/wahyurama-creator/Ridecomp/releases/"

    /** The project's releases, newest first (pre-releases included, unlike `/releases/latest`). */
    public const val RELEASES_API_URL: String =
        "https://api.github.com/repos/wahyurama-creator/Ridecomp/releases?per_page=5"

    /** The list of releases is small; a bigger reply is refused (SEC-IN). */
    public const val MAX_RESPONSE_BYTES: Int = 256 * 1_024
    public const val CONNECT_TIMEOUT_MS: Int = 10_000
    public const val READ_TIMEOUT_MS: Int = 15_000
}

/** A release name such as "0.9.1" or "v1.2.0", compared part by part; a "-debug" style suffix is ignored. */
public class AppVersion private constructor(private val parts: List<Int>) : Comparable<AppVersion> {
    override fun compareTo(other: AppVersion): Int {
        val size = maxOf(parts.size, other.parts.size)
        for (i in 0 until size) {
            val difference = parts.getOrElse(i) { 0 } - other.parts.getOrElse(i) { 0 }
            if (difference != 0) return difference
        }
        return 0
    }

    override fun equals(other: Any?): Boolean = other is AppVersion && compareTo(other) == 0

    override fun hashCode(): Int = parts.dropLastWhile { it == 0 }.hashCode()

    override fun toString(): String = parts.joinToString(".")

    public companion object {
        private val PATTERN = Regex("""^[vV]?(\d{1,4}(?:\.\d{1,4}){0,3})(?:[-+][0-9A-Za-z.\-]*)?$""")

        /** Null for anything that is not a plain version (a tag from the network is untrusted input). */
        public fun parse(text: String): AppVersion? =
            PATTERN.matchEntire(text.trim())?.groupValues?.get(1)?.split(".")?.map(String::toInt)?.let(::AppVersion)
    }
}

/** A published release: its version and the page to download it from. */
public data class AppRelease(val version: String, val pageUrl: String)

/** What the app remembers about update checks (settings rows). */
public data class UpdatePreferences(
    /** Look for a new version when the app is opened, at most once a day (on by default). */
    val isAutoCheckEnabled: Boolean = true,
    val lastCheckMs: Long? = null,
    /** The newest release seen at the last successful check. */
    val latest: AppRelease? = null,
    /** "Nanti" on the home card hides that version (a newer one shows again). */
    val dismissedVersion: String? = null,
)

/** Result of "Cek sekarang". */
public enum class UpdateCheckResult { UPDATE_AVAILABLE, UP_TO_DATE, FAILED }
