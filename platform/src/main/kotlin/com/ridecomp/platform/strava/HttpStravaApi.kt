package com.ridecomp.platform.strava

import com.ridecomp.core.config.StravaConfig
import com.ridecomp.core.model.StravaApp
import com.ridecomp.core.model.StravaError
import com.ridecomp.core.source.Clock
import com.ridecomp.core.source.DispatcherProvider
import com.ridecomp.core.source.Logger
import com.ridecomp.core.source.StravaApi
import com.ridecomp.core.source.StravaGrant
import com.ridecomp.core.source.StravaUploadReply
import com.ridecomp.core.source.StravaUploadRequest
import com.ridecomp.core.util.Outcome
import com.ridecomp.core.util.asFailure
import com.ridecomp.core.util.asSuccess
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.UUID
import javax.net.ssl.HttpsURLConnection
import kotlinx.coroutines.withContext

/**
 * Strava over HTTPS with the platform's `HttpsURLConnection` (no HTTP library, D-79): system trust only
 * (network_security_config), timeouts, no redirects followed, replies size-limited and validated
 * (SEC-NET-01..04). Tokens and replies are never logged, only the status code.
 */
class HttpStravaApi(
    private val clock: Clock,
    private val dispatchers: DispatcherProvider,
    private val logger: Logger,
) : StravaApi {
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
                contentType = FORM_CONTENT_TYPE,
                writeBody = { out -> out.write(form(listOf(TOKEN to token, TOKEN_TYPE_HINT to REFRESH_TOKEN))) },
            ),
        )
        return when (reply) {
            is Outcome.Success -> Unit.asSuccess()
            is Outcome.Failure -> reply
        }
    }

    override suspend fun upload(
        accessToken: String,
        request: StravaUploadRequest,
    ): Outcome<StravaUploadReply, StravaError> {
        val boundary = BOUNDARY_PREFIX + UUID.randomUUID().toString().replace("-", "")
        val fields = listOf(
            DATA_TYPE to StravaConfig.DATA_TYPE_FIT,
            NAME to request.name,
            EXTERNAL_ID to request.externalId,
            SPORT_TYPE to StravaConfig.SPORT_TYPE,
        )
        val reply = send(
            HttpRequest(
                url = apiUrl(UPLOADS_PATH),
                headers = mapOf(AUTHORIZATION to "$BEARER $accessToken"),
                contentType = "$MULTIPART_CONTENT_TYPE$boundary",
                writeBody = { out -> writeMultipart(out, boundary, fields, request) },
            ),
        )
        return reply.toUploadReply()
    }

    override suspend fun uploadStatus(accessToken: String, uploadId: Long): Outcome<StravaUploadReply, StravaError> =
        send(
            HttpRequest(
                url = apiUrl("$UPLOADS_PATH/$uploadId"),
                headers = mapOf(AUTHORIZATION to "$BEARER $accessToken"),
                method = GET,
            ),
        ).toUploadReply()

    private fun Outcome<String, StravaError>.toUploadReply(): Outcome<StravaUploadReply, StravaError> = when (this) {
        is Outcome.Success -> StravaJson.uploadReply(value)?.asSuccess() ?: StravaError.BAD_RESPONSE.asFailure()
        is Outcome.Failure -> this
    }

    /** The REST base URL moves to api-v3.strava.com in 2027 (T5.4). */
    private fun apiUrl(path: String): String = StravaConfig.apiBaseUrl(clock.nowMs()) + path

    /** Text fields, then the FIT file streamed from disk (never fully in memory). */
    private fun writeMultipart(
        out: OutputStream,
        boundary: String,
        fields: List<Pair<String, String>>,
        request: StravaUploadRequest,
    ) {
        fun line(text: String = "") = out.write("$text$CRLF".toByteArray(StandardCharsets.UTF_8))
        fields.forEach { (name, value) ->
            line("--$boundary")
            line("$CONTENT_DISPOSITION: form-data; name=\"$name\"")
            line()
            line(value)
        }
        line("--$boundary")
        line("$CONTENT_DISPOSITION: form-data; name=\"$FILE\"; filename=\"${request.fileName.safeFileName()}\"")
        line("$CONTENT_TYPE: $OCTET_STREAM")
        line()
        File(request.filePath).inputStream().use { it.copyTo(out, BUFFER_BYTES) }
        line()
        line("--$boundary--")
    }

    /** Header-safe file name (no quotes or line breaks). */
    private fun String.safeFileName(): String = filter { it.isLetterOrDigit() || it in SAFE_NAME_CHARS }

    private suspend fun grant(app: StravaApp, params: List<Pair<String, String>>): Outcome<StravaGrant, StravaError> {
        val body = form(listOf(CLIENT_ID to app.clientId, CLIENT_SECRET to app.clientSecret) + params)
        val request = HttpRequest(StravaConfig.TOKEN_URL, emptyMap(), FORM_CONTENT_TYPE, writeBody = { it.write(body) })
        return when (val reply = send(request)) {
            is Outcome.Success -> StravaJson.grant(reply.value)?.asSuccess() ?: StravaError.BAD_RESPONSE.asFailure()
            is Outcome.Failure -> reply
        }
    }

    /** Sends [request] and returns the reply text of a 2xx answer. */
    internal suspend fun send(request: HttpRequest): Outcome<String, StravaError> = withContext(dispatchers.io) {
        val connection = try {
            URL(request.url).openConnection() as? HttpsURLConnection
        } catch (error: IOException) {
            logger.warn(TAG, error) { "Could not open a connection" }
            null
        } ?: return@withContext StravaError.OFFLINE.asFailure()
        try {
            connection.apply {
                requestMethod = request.method
                connectTimeout = StravaConfig.CONNECT_TIMEOUT_MS
                readTimeout = StravaConfig.READ_TIMEOUT_MS
                instanceFollowRedirects = false
                useCaches = false
                setRequestProperty(ACCEPT, JSON_CONTENT_TYPE)
                request.headers.forEach { (name, value) -> setRequestProperty(name, value) }
            }
            request.writeBody?.let { write ->
                connection.doOutput = true
                connection.setRequestProperty(CONTENT_TYPE, request.contentType)
                // Streamed in chunks: a long ride's FIT file is never held in memory (MEM-01).
                connection.setChunkedStreamingMode(0)
                connection.outputStream.use(write)
            }
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
        val contentType: String? = null,
        val method: String = POST,
        val writeBody: ((OutputStream) -> Unit)? = null,
    )

    private companion object {
        const val TAG = "Strava"
        const val POST = "POST"
        const val GET = "GET"
        const val BEARER = "Bearer"
        const val UPLOADS_PATH = "/uploads"
        const val MULTIPART_CONTENT_TYPE = "multipart/form-data; boundary="
        const val BOUNDARY_PREFIX = "RideComp"
        const val CONTENT_DISPOSITION = "Content-Disposition"
        const val OCTET_STREAM = "application/octet-stream"
        const val CRLF = "\r\n"
        const val DATA_TYPE = "data_type"
        const val NAME = "name"
        const val EXTERNAL_ID = "external_id"
        const val SPORT_TYPE = "sport_type"
        const val FILE = "file"
        const val SAFE_NAME_CHARS = "._-"
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
