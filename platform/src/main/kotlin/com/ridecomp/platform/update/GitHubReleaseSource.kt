package com.ridecomp.platform.update

import android.content.Context
import com.ridecomp.core.engine.update.AppRelease
import com.ridecomp.core.engine.update.AppVersion
import com.ridecomp.core.engine.update.UpdateConfig
import com.ridecomp.core.source.DispatcherProvider
import com.ridecomp.core.source.Logger
import com.ridecomp.core.usecase.AppInfoSource
import com.ridecomp.core.usecase.ReleaseSource
import com.ridecomp.platform.system.TrafficTags
import com.ridecomp.platform.system.withTrafficTag
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import javax.net.ssl.HttpsURLConnection
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException

/**
 * Reads the project's GitHub releases over HTTPS (T10.9, D-103): no account, no token, nothing about the rider
 * is sent. The reply is untrusted input: size-limited, the tag must be a plain version and the page must be a
 * release page of the project (SEC-IN). Any failure gives null and the app simply shows no update.
 */
class GitHubReleaseSource(private val dispatchers: DispatcherProvider, private val logger: Logger) : ReleaseSource {
    override suspend fun latest(): AppRelease? = withContext(dispatchers.io) {
        withTrafficTag(TrafficTags.UPDATE_CHECK) { fetch() }
    }

    private fun fetch(): AppRelease? {
        val connection = URL(UpdateConfig.RELEASES_API_URL).openConnection() as HttpsURLConnection
        return try {
            connection.apply {
                connectTimeout = UpdateConfig.CONNECT_TIMEOUT_MS
                readTimeout = UpdateConfig.READ_TIMEOUT_MS
                instanceFollowRedirects = false
                setRequestProperty(ACCEPT, GITHUB_JSON)
            }
            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                connection.inputStream.readLimited()?.let(::newest)
            } else {
                logger.info(TAG) { "Releases replied ${connection.responseCode}" }
                null
            }
        } catch (error: IOException) {
            logger.warn(TAG, error) { "Release check failed" }
            null
        } finally {
            connection.disconnect()
        }
    }

    /** The first published (not draft) release with a valid version and page. */
    private fun newest(json: String): AppRelease? = try {
        val releases = JSONArray(json)
        (0 until releases.length()).asSequence()
            .map(releases::getJSONObject)
            .filterNot { it.optBoolean(DRAFT) }
            .map { AppRelease(it.optString(TAG_NAME).removePrefix(V), it.optString(HTML_URL)) }
            .firstOrNull {
                AppVersion.parse(it.version) != null &&
                    it.pageUrl.startsWith(UpdateConfig.RELEASE_PAGE_PREFIX)
            }
    } catch (error: JSONException) {
        logger.warn(TAG, error) { "Unreadable release list" }
        null
    }

    private fun InputStream.readLimited(): String? = use { input ->
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(BUFFER_BYTES)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            out.write(buffer, 0, read)
            if (out.size() > UpdateConfig.MAX_RESPONSE_BYTES) return null
        }
        out.toString(StandardCharsets.UTF_8.name())
    }

    private companion object {
        const val TAG = "Update"
        const val ACCEPT = "Accept"
        const val GITHUB_JSON = "application/vnd.github+json"
        const val DRAFT = "draft"
        const val TAG_NAME = "tag_name"
        const val HTML_URL = "html_url"
        const val V = "v"
        const val BUFFER_BYTES = 8 * 1024
    }
}

/** The installed version name from the package (as the backup manifest does). */
class AndroidAppInfoSource(private val context: Context) : AppInfoSource {
    override val versionName: String by lazy {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
    }
}
