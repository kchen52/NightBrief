package app.nightbrief.app.update

import app.nightbrief.weather.defaultHttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Fetches GitHub Releases and downloads APK assets.
 *
 * Unauthenticated public GETs only. A failed check returns null and never
 * fails the briefing. [baseUrl] is injectable for MockWebServer tests.
 */
class GithubReleaseClient(
    private val owner: String,
    private val repo: String,
    private val http: OkHttpClient = defaultHttpClient(),
    private val baseUrl: String = "https://api.github.com",
) {
    private val downloadHttp: OkHttpClient = http.newBuilder()
        .callTimeout(0, TimeUnit.MILLISECONDS)
        .readTimeout(5, TimeUnit.MINUTES)
        .build()

    /**
     * Latest stable release newer than [currentVersion], or null.
     * Pre-releases are excluded by GitHub's `/releases/latest` endpoint.
     */
    suspend fun checkStable(currentVersion: String): AvailableUpdate? {
        val release = getJson("$baseUrl/repos/$owner/$repo/releases/latest")
            ?.let { runCatching { GithubRelease.parseRelease(it) }.getOrNull() }
            ?: return null
        if (!SemVer.isNewer(release.tag, currentVersion)) return null
        return AvailableUpdate(
            label = SemVer.normalise(release.tag),
            id = release.tag,
            htmlUrl = release.htmlUrl,
            apkUrl = release.apkUrl,
            apkBytes = release.apkBytes,
        )
    }

    /**
     * Develop tip when [currentSha] does not match the published commit.
     * [currentSha] of `local` still offers an update so a laptop build can
     * move onto the CI sideload channel once.
     */
    suspend fun checkDevelop(currentSha: String): AvailableUpdate? {
        val release = getJson("$baseUrl/repos/$owner/$repo/releases/tags/develop-latest")
            ?.let { runCatching { GithubRelease.parseRelease(it) }.getOrNull() }
            ?: return null
        val remote = shortSha(release.targetCommitish) ?: shortShaFromAsset(release.apkUrl) ?: return null
        val local = shortSha(currentSha)
        if (local != null && local.equals(remote, ignoreCase = true)) return null
        return AvailableUpdate(
            label = remote,
            id = "develop:$remote",
            htmlUrl = release.htmlUrl,
            apkUrl = release.apkUrl,
            apkBytes = release.apkBytes,
        )
    }

    /**
     * Streams the APK into [destDir], replacing any previous `update-*.apk`.
     * Returns null when the download fails or is truncated.
     */
    suspend fun download(
        update: AvailableUpdate,
        destDir: File,
        onProgress: ((Float) -> Unit)? = null,
    ): File? = withContext(Dispatchers.IO) {
        if (!destDir.isDirectory && !destDir.mkdirs()) return@withContext null
        destDir.listFiles()
            ?.filter { it.name.startsWith("update-") && it.name.endsWith(".apk") }
            ?.forEach { it.delete() }
        val dest = File(destDir, "update-${update.id.replace(Regex("[^A-Za-z0-9._-]"), "_")}.apk")
        val tmp = File(destDir, "${dest.name}.tmp")
        try {
            val request = Request.Builder()
                .url(update.apkUrl)
                .header("User-Agent", "NightBrief/0.1")
                .header("Accept", "application/octet-stream")
                .build()
            downloadHttp.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    tmp.delete()
                    return@withContext null
                }
                val body = response.body ?: return@withContext null
                val total = body.contentLength().takeIf { it > 0 } ?: update.apkBytes
                var received = 0L
                tmp.outputStream().use { out ->
                    body.byteStream().use { input ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            out.write(buffer, 0, read)
                            received += read
                            if (total > 0) onProgress?.invoke((received.toDouble() / total).toFloat().coerceIn(0f, 1f))
                        }
                    }
                }
                if (total > 0 && received < total) {
                    tmp.delete()
                    return@withContext null
                }
                if (!tmp.renameTo(dest)) {
                    tmp.copyTo(dest, overwrite = true)
                    tmp.delete()
                }
                dest
            }
        } catch (_: IOException) {
            tmp.delete()
            null
        }
    }

    private suspend fun getJson(url: String): String? =
        try {
            suspendCancellableCoroutine { cont ->
                val call = http.newCall(
                    Request.Builder()
                        .url(url)
                        .header("User-Agent", "NightBrief/0.1")
                        .header("Accept", "application/vnd.github+json")
                        .build(),
                )
                cont.invokeOnCancellation { call.cancel() }
                call.enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        if (cont.isActive) cont.resumeWithException(e)
                    }

                    override fun onResponse(call: Call, response: Response) {
                        response.use {
                            val body = it.body?.string().orEmpty()
                            if (it.isSuccessful) {
                                if (cont.isActive) cont.resume(body)
                            } else {
                                if (cont.isActive) cont.resumeWithException(IOException("HTTP ${it.code}"))
                            }
                        }
                    }
                })
            }
        } catch (_: Exception) {
            null
        }

    companion object {
        fun shortSha(raw: String?): String? {
            if (raw.isNullOrBlank()) return null
            val trimmed = raw.trim()
            if (trimmed.equals("local", ignoreCase = true)) return null
            if (trimmed.length < 7) return trimmed.lowercase()
            return trimmed.take(7).lowercase()
        }

        private fun shortShaFromAsset(url: String): String? {
            val name = url.substringAfterLast('/').substringBefore('?')
            val match = Regex("""nightbrief-develop-([0-9a-fA-F]{7})""", RegexOption.IGNORE_CASE)
                .find(name)
            return match?.groupValues?.get(1)?.lowercase()
        }
    }
}
