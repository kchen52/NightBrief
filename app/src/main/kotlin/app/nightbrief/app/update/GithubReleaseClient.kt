package app.nightbrief.app.update

import app.nightbrief.weather.defaultHttpClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.nio.charset.StandardCharsets
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
    private val baseUrl: String = DEFAULT_BASE_URL,
) {
    private val baseHttpUrl: HttpUrl? = baseUrl.toHttpUrlOrNull()
    private val isProductionGitHub: Boolean =
        baseHttpUrl?.scheme == "https" && baseHttpUrl.host.equals("api.github.com", ignoreCase = true)

    private val apiHttp: OkHttpClient = http.newBuilder()
        .followSslRedirects(false)
        .build()

    private val downloadHttp: OkHttpClient = http.newBuilder()
        .callTimeout(10, TimeUnit.MINUTES)
        .readTimeout(2, TimeUnit.MINUTES)
        .followRedirects(true)
        .followSslRedirects(false)
        .addNetworkInterceptor { chain ->
            val url = chain.request().url
            if (!isAllowedDownloadUrl(url)) {
                throw IOException("Disallowed update download host: ${url.host}")
            }
            chain.proceed(chain.request())
        }
        .build()

    /**
     * Latest stable release newer than [currentVersion], or null.
     * Pre-releases are excluded by GitHub's `/releases/latest` endpoint.
     */
    suspend fun checkStable(currentVersion: String): AvailableUpdate? {
        val release = getJson("$baseUrl/repos/$owner/$repo/releases/latest")
            ?.let { runCatching { GithubRelease.parseRelease(it) }.getOrNull() }
            ?: return null
        if (!isValidReleaseUrls(release)) return null
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
        if (!isValidReleaseUrls(release)) return null
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
     * Returns null when the download fails, exceeds [MAX_APK_BYTES], redirects
     * to a disallowed host, or is truncated.
     */
    suspend fun download(
        update: AvailableUpdate,
        destDir: File,
        onProgress: ((Float) -> Unit)? = null,
    ): File? = withContext(Dispatchers.IO) {
        val parsedUrl = update.apkUrl.toHttpUrlOrNull() ?: return@withContext null
        if (!isAllowedDownloadUrl(parsedUrl)) return@withContext null
        if (update.apkBytes > MAX_APK_BYTES) return@withContext null
        if (!destDir.isDirectory && !destDir.mkdirs()) return@withContext null
        destDir.listFiles()
            ?.filter { it.name.startsWith("update-") && it.name.endsWith(".apk") }
            ?.forEach { it.delete() }
        val dest = File(destDir, "update-${update.id.replace(Regex("[^A-Za-z0-9._-]"), "_")}.apk")
        val tmp = File(destDir, "${dest.name}.tmp")
        try {
            val request = Request.Builder()
                .url(parsedUrl)
                .header("User-Agent", "NightBrief/0.1")
                .header("Accept", "application/octet-stream")
                .build()
            downloadHttp.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    tmp.delete()
                    return@withContext null
                }
                val body = response.body ?: return@withContext null
                val contentLength = body.contentLength()
                if (contentLength > MAX_APK_BYTES) {
                    tmp.delete()
                    return@withContext null
                }
                val total = contentLength.takeIf { it > 0 } ?: update.apkBytes
                var received = 0L
                tmp.outputStream().use { out ->
                    body.byteStream().use { input ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            received += read
                            if (received > MAX_APK_BYTES) {
                                tmp.delete()
                                return@withContext null
                            }
                            out.write(buffer, 0, read)
                            if (total > 0) onProgress?.invoke((received.toDouble() / total).toFloat().coerceIn(0f, 1f))
                        }
                    }
                }
                if (received <= 0L || (total > 0 && received < total)) {
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

    internal fun isAllowedDownloadUrl(url: HttpUrl): Boolean {
        if (url.scheme == "https" && url.host.lowercase() in ALLOWED_ASSET_HOSTS) {
            return true
        }
        val base = baseHttpUrl
        if (!isProductionGitHub && base != null) {
            return url.scheme == base.scheme &&
                url.host.equals(base.host, ignoreCase = true) &&
                url.port == base.port
        }
        return false
    }

    private fun isValidReleaseUrls(release: GithubRelease.ParsedRelease): Boolean {
        val apkUrl = release.apkUrl.toHttpUrlOrNull() ?: return false
        if (!isAllowedDownloadUrl(apkUrl)) return false
        if (release.apkBytes > MAX_APK_BYTES) return false
        if (isProductionGitHub && !GithubRelease.isSafeReleasePageUrl(release.htmlUrl)) return false
        return true
    }

    private suspend fun getJson(url: String): String? =
        try {
            suspendCancellableCoroutine { cont ->
                val call = apiHttp.newCall(
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
                            if (!it.isSuccessful) {
                                if (cont.isActive) cont.resumeWithException(IOException("HTTP ${it.code}"))
                                return
                            }
                            try {
                                val body = readBoundedUtf8(it, MAX_JSON_BYTES)
                                if (cont.isActive) cont.resume(body)
                            } catch (e: IOException) {
                                if (cont.isActive) cont.resumeWithException(e)
                            }
                        }
                    }
                })
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }

    companion object {
        const val DEFAULT_BASE_URL = "https://api.github.com"
        const val MAX_JSON_BYTES = 2 * 1024 * 1024
        const val MAX_APK_BYTES = 100L * 1024 * 1024

        private val ALLOWED_ASSET_HOSTS = setOf(
            "github.com",
            "objects.githubusercontent.com",
            "release-assets.githubusercontent.com",
            "github-releases.githubusercontent.com",
        )

        private fun readBoundedUtf8(response: Response, maxBytes: Int): String {
            val body = response.body ?: return ""
            val declared = body.contentLength()
            if (declared > maxBytes) throw IOException("Response exceeds $maxBytes bytes")
            val out = ByteArrayOutputStream()
            body.byteStream().use { input ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var total = 0
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    total += read
                    if (total > maxBytes) throw IOException("Response exceeds $maxBytes bytes")
                    out.write(buffer, 0, read)
                }
            }
            return String(out.toByteArray(), StandardCharsets.UTF_8)
        }

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
