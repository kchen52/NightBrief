package app.nightbrief.weather

import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class WeatherApiException(message: String, cause: Throwable? = null) : IOException(message, cause)

fun defaultHttpClient(timing: NetworkTimingListener? = null): OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(15, TimeUnit.SECONDS)
    .readTimeout(20, TimeUnit.SECONDS)
    .callTimeout(30, TimeUnit.SECONDS)
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

internal suspend fun OkHttpClient.getString(url: String): String =
    suspendCancellableCoroutine { cont ->
        val call = newCall(Request.Builder().url(url).header("User-Agent", "NightBrief/0.1").build())
        cont.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                cont.resumeWithException(WeatherApiException("request failed: ${e.message}", e))
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    val body = it.body?.string().orEmpty()
                    if (it.isSuccessful) {
                        cont.resume(body)
                    } else {
                        cont.resumeWithException(WeatherApiException("HTTP ${it.code} from ${call.request().url.host}: ${body.take(200)}"))
                    }
                }
            }
        })
    }
