package app.nightbrief.weather

import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Why a forecast fetch failed, in terms the UI can show.
 * The exception message stays technical. [userText] is the sentence on screen.
 */
enum class ForecastProblem {
    /** DNS or a connection that never opened. */
    OFFLINE,
    /** The service asked us to slow down (HTTP 429). */
    BUSY,
    /** The call exceeded its deadline. */
    TIMEOUT,
    /** Any other HTTP or parse failure. */
    UNAVAILABLE,
    ;

    fun userText(): String = when (this) {
        OFFLINE -> "No connection"
        BUSY -> "Weather service busy — try again shortly"
        TIMEOUT -> "The weather service timed out"
        UNAVAILABLE -> "Couldn't load the forecast"
    }

    companion object {
        fun of(error: Throwable): ForecastProblem {
            var current: Throwable? = error
            while (current != null) {
                when (current) {
                    is java.net.UnknownHostException,
                    is java.net.ConnectException,
                    is java.net.NoRouteToHostException,
                    is java.net.PortUnreachableException,
                    -> return OFFLINE
                    is java.net.SocketTimeoutException -> return TIMEOUT
                }
                val message = current.message
                if (message != null && message.contains("timeout", ignoreCase = true)) return TIMEOUT
                current = current.cause
            }
            return UNAVAILABLE
        }
    }
}

class WeatherApiException(
    message: String,
    cause: Throwable? = null,
    val problem: ForecastProblem = ForecastProblem.UNAVAILABLE,
) : IOException(message, cause) {
    /** Plain status for Tonight, Week, and the digest. Not the raw exception text. */
    fun userText(): String = problem.userText()

    companion object {
        fun transport(error: IOException): WeatherApiException =
            WeatherApiException(error.message ?: "request failed", error, ForecastProblem.of(error))
    }
}

fun defaultHttpClient(timing: NetworkTimingListener? = null): OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(15, TimeUnit.SECONDS)
    .readTimeout(20, TimeUnit.SECONDS)
    .callTimeout(30, TimeUnit.SECONDS)
    .followSslRedirects(false)
    .apply { if (timing != null) addInterceptor(timing.asInterceptor()) }
    .build()

/**
 * Observes one HTTP round trip: the full URL, wall-clock duration, and status code
 * (null when the call threw before a response arrived).
 *
 * Pure JVM so `:core-weather` stays Android-free; Android owners (e.g. `AppGraph`)
 * attach a listener that logs. Keep query details out of the log line: URLs carry
 * precise coordinates.
 */
fun interface NetworkTimingListener {
    fun onFinished(url: String, durationMs: Long, code: Int?)
}

private fun NetworkTimingListener.asInterceptor(): Interceptor = Interceptor { chain ->
    val request = chain.request()
    val start = System.nanoTime()
    try {
        val response = chain.proceed(request)
        onFinished(request.url.toString(), (System.nanoTime() - start) / 1_000_000, response.code)
        response
    } catch (e: Exception) {
        onFinished(request.url.toString(), (System.nanoTime() - start) / 1_000_000, null)
        throw e
    }
}

internal const val DEFAULT_MAX_RESPONSE_BYTES = 10 * 1024 * 1024

internal suspend fun OkHttpClient.getString(
    url: String,
    maxBytes: Int = DEFAULT_MAX_RESPONSE_BYTES,
): String =
    suspendCancellableCoroutine { cont ->
        val call = newCall(Request.Builder().url(url).header("User-Agent", "NightBrief/0.1").build())
        cont.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (cont.isActive) {
                    cont.resumeWithException(
                        if (e is WeatherApiException) e else WeatherApiException.transport(e),
                    )
                }
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (!it.isSuccessful) {
                        val problem = if (it.code == 429) ForecastProblem.BUSY else ForecastProblem.UNAVAILABLE
                        if (cont.isActive) {
                            cont.resumeWithException(
                                WeatherApiException("HTTP ${it.code} from ${call.request().url.host}", problem = problem),
                            )
                        }
                        return
                    }
                    try {
                        val body = readBoundedUtf8(it, maxBytes, call.request().url.host)
                        if (cont.isActive) cont.resume(body)
                    } catch (e: IOException) {
                        if (cont.isActive) {
                            cont.resumeWithException(
                                if (e is WeatherApiException) e else WeatherApiException.transport(e),
                            )
                        }
                    }
                }
            }
        })
    }

private fun readBoundedUtf8(response: Response, maxBytes: Int, host: String): String {
    val body = response.body ?: return ""
    val declared = body.contentLength()
    if (declared > maxBytes) {
        throw WeatherApiException("Response from $host exceeds $maxBytes bytes")
    }
    val out = ByteArrayOutputStream()
    body.byteStream().use { input ->
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            total += read
            if (total > maxBytes) {
                throw WeatherApiException("Response from $host exceeds $maxBytes bytes")
            }
            out.write(buffer, 0, read)
        }
    }
    return String(out.toByteArray(), StandardCharsets.UTF_8)
}
