package com.ridecomp.platform.strava

import com.ridecomp.core.config.StravaConfig
import com.ridecomp.core.model.StravaApp
import com.ridecomp.core.model.StravaError
import com.ridecomp.core.source.DispatcherProvider
import com.ridecomp.core.source.Logger
import com.ridecomp.core.source.StravaApi
import com.ridecomp.core.source.StravaGrant
import com.ridecomp.core.util.Outcome
import com.ridecomp.core.util.asFailure
import com.ridecomp.core.util.asSuccess
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Base64
import javax.net.ssl.HttpsURLConnection
import kotlinx.coroutines.withContext

/**
 * Strava over HTTPS with the platform's `HttpsURLConnection` (no HTTP library, D-79): system trust only
 * (network_security_config), timeouts, no redirects followed, replies size-limited and validated
 * (SEC-NET-01..04). Tokens and replies are never logged, only the status code.
 */
class HttpStravaApi(private val dispatchers: DispatcherProvider, private val logger: Logger) : StravaApi {
    override suspend fun exchangeCode(app: StravaApp, code: String): Outcome<StravaGrant, StravaError> = grant(
        app,
        listOf(GRANT_TYPE to "authorization_code", CODE to code),
    )

    override suspend fun refresh(app: StravaApp, refreshToken: String): Outcome<StravaGrant, StravaError> = grant(
        app,
        listOf(GRANT_TYPE to "refresh_token", REFRESH_TOKEN to refreshToken),
    )

    override suspend fun revoke(app: StravaApp, token: String): Outcome<Unit, StravaError> {
        val basic = Base64.getEncoder().encodeToString("${app.clientId}:${app.clientSecret}".toByteArray())
        val reply = send(
            HttpRequest(
                url = StravaConfig.REVOKE_URL,
                headers = mapOf(AUTHORIZATION to "$BASIC $basic"),
                body = form(listOf(TOKEN to token, TOKEN_TYPE_HINT to REFRESH_TOKEN)),
                contentType = FORM_CONTENT_TYPE,
            ),
        )
        return when (reply) {
            is Outcome.Success -> Unit.asSuccess()
            is Outcome.Failure -> reply
        }
    }

    private suspend fun grant(app: StravaApp, params: List<Pair<String, String>>): Outcome<StravaGrant, StravaError> {
        val body = form(listOf(CLIENT_ID to app.clientId, CLIENT_SECRET to app.clientSecret) + params)
        return when (val reply = send(HttpRequest(StravaConfig.TOKEN_URL, emptyMap(), body, FORM_CONTENT_TYPE))) {
            is Outcome.Success -> StravaJson.grant(reply.value)?.asSuccess() ?: StravaError.BAD_RESPONSE.asFailure()
            is Outcome.Failure -> reply
        }
    }

    /** POSTs [request] and returns the reply text of a 2xx answer. */
    internal suspend fun send(request: HttpRequest): Outcome<String, StravaError> = withContext(dispatchers.io) {
        val connection = try {
            URL(request.url).openConnection() as? HttpsURLConnection
        } catch (error: IOException) {
            logger.warn(TAG, error) { "Could not open a connection" }
            null
        } ?: return@withContext StravaError.OFFLINE.asFailure()
        try {
            connection.apply {
                requestMethod = POST
                connectTimeout = StravaConfig.CONNECT_TIMEOUT_MS
                readTimeout = StravaConfig.READ_TIMEOUT_MS
                instanceFollowRedirects = false
                useCaches = false
                doOutput = true
                setRequestProperty(CONTENT_TYPE, request.contentType)
                setRequestProperty(ACCEPT, JSON_CONTENT_TYPE)
                request.headers.forEach { (name, value) -> setRequestProperty(name, value) }
                setFixedLengthStreamingMode(request.body.size)
            }
            connection.outputStream.use { it.write(request.body) }
            val status = connection.responseCode
            if (status !in HTTP_OK_RANGE) {
                logger.info(TAG) { "Strava replied $status" }
                return@withContext status.toError().asFailure()
            }
            connection.inputStream.readLimited()?.asSuccess() ?: StravaError.BAD_RESPONSE.asFailure()
        } catch (error: IOException) {
            logger.warn(TAG, error) { "Strava request failed" }
            StravaError.OFFLINE.asFailure()
        } finally {
            connection.disconnect()
        }
    }

    private fun InputStream.readLimited(): String? = use { input ->
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(BUFFER_BYTES)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            out.write(buffer, 0, read)
            if (out.size() > StravaConfig.MAX_RESPONSE_BYTES) return null
        }
        out.toString(StandardCharsets.UTF_8.name())
    }

    private fun Int.toError(): StravaError = when (this) {
        HttpURLConnection.HTTP_BAD_REQUEST, HttpURLConnection.HTTP_UNAUTHORIZED, HttpURLConnection.HTTP_FORBIDDEN ->
            StravaError.UNAUTHORIZED
        HTTP_TOO_MANY_REQUESTS -> StravaError.RATE_LIMITED
        in HTTP_SERVER_ERROR_RANGE -> StravaError.SERVER
        else -> StravaError.BAD_RESPONSE
    }

    private fun form(params: List<Pair<String, String>>): ByteArray = params
        .joinToString("&") { (key, value) -> "$key=${URLEncoder.encode(value, StandardCharsets.UTF_8.name())}" }
        .toByteArray(StandardCharsets.UTF_8)

    internal class HttpRequest(
        val url: String,
        val headers: Map<String, String>,
        val body: ByteArray,
        val contentType: String,
    )

    private companion object {
        const val TAG = "Strava"
        const val POST = "POST"
        const val CONTENT_TYPE = "Content-Type"
        const val ACCEPT = "Accept"
        const val AUTHORIZATION = "Authorization"
        const val BASIC = "Basic"
        const val FORM_CONTENT_TYPE = "application/x-www-form-urlencoded"
        const val JSON_CONTENT_TYPE = "application/json"
        const val CLIENT_ID = "client_id"
        const val CLIENT_SECRET = "client_secret"
        const val GRANT_TYPE = "grant_type"
        const val CODE = "code"
        const val REFRESH_TOKEN = "refresh_token"
        const val TOKEN = "token"
        const val TOKEN_TYPE_HINT = "token_type_hint"
        const val HTTP_TOO_MANY_REQUESTS = 429
        const val BUFFER_BYTES = 8 * 1024
        val HTTP_OK_RANGE = 200..299
        val HTTP_SERVER_ERROR_RANGE = 500..599
    }
}
