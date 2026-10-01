package app.nightbrief.weather

import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class WeatherApiException(message: String, cause: Throwable? = null) : IOException(message, cause)

fun defaultHttpClient(): OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(15, TimeUnit.SECONDS)
    .readTimeout(20, TimeUnit.SECONDS)
    .callTimeout(30, TimeUnit.SECONDS)
    .build()

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
