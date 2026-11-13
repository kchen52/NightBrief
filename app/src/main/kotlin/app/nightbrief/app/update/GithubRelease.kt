package app.nightbrief.app.update

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/** A newer APK worth offering on the update banner. */
data class AvailableUpdate(
    /** Tag or short SHA shown in the banner. */
    val label: String,
    /** Stable id for skip memory (tag name or develop SHA). */
    val id: String,
    val htmlUrl: String,
    val apkUrl: String,
    val apkBytes: Long = 0,
)

/**
 * Parses GitHub Releases JSON for the in-app updater.
 *
 * Drafts are skipped. The APK asset is chosen by `.apk` suffix, not a fixed name.
 */
object GithubRelease {
    private val json = Json { ignoreUnknownKeys = true }

    fun parseRelease(body: String): ParsedRelease? =
        parseObject(json.parseToJsonElement(body).jsonObject)

    /** Newest non-draft release from a listing body. */
    fun parseNewest(body: String): ParsedRelease? {
        val array = json.parseToJsonElement(body) as? JsonArray ?: return null
        var best: ParsedRelease? = null
        for (element in array) {
            val release = parseObject(element.jsonObject) ?: continue
            if (best == null || SemVer.isNewer(release.tag, best.tag)) best = release
        }
        return best
    }

    fun pickApkAsset(release: JsonObject): Pair<String, Long>? {
        val assets = release["assets"]?.jsonArray ?: return null
        for (element in assets) {
            val asset = element.jsonObject
            val name = asset["name"]?.jsonPrimitive?.contentOrNull.orEmpty().lowercase()
            val url = asset["browser_download_url"]?.jsonPrimitive?.contentOrNull ?: continue
            if (!name.endsWith(".apk")) continue
            val size = asset["size"]?.jsonPrimitive?.longOrNull
                ?: asset["size"]?.jsonPrimitive?.intOrNull?.toLong()
                ?: 0L
            return url to size
        }
        return null
    }

    private fun parseObject(obj: JsonObject): ParsedRelease? {
        if (obj["draft"]?.jsonPrimitive?.booleanOrNull == true) return null
        val tag = obj["tag_name"]?.jsonPrimitive?.contentOrNull ?: return null
        val htmlUrl = obj["html_url"]?.jsonPrimitive?.contentOrNull ?: return null
        val target = obj["target_commitish"]?.jsonPrimitive?.contentOrNull
        val apk = pickApkAsset(obj) ?: return null
        return ParsedRelease(
            tag = tag,
            htmlUrl = htmlUrl,
            targetCommitish = target,
            apkUrl = apk.first,
            apkBytes = apk.second,
            prerelease = obj["prerelease"]?.jsonPrimitive?.booleanOrNull == true,
        )
    }

    data class ParsedRelease(
        val tag: String,
        val htmlUrl: String,
        val targetCommitish: String?,
        val apkUrl: String,
        val apkBytes: Long,
        val prerelease: Boolean,
    )
}
