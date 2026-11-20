package app.nightbrief.app.update

import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class GithubReleaseTest {
    @Test
    fun parseReleasePicksApkAndSkipsDraft() {
        val draft = """
            {"draft":true,"tag_name":"v0.2.0","html_url":"https://example/r","assets":[
              {"name":"nightbrief-v0.2.0.apk","browser_download_url":"https://example/a.apk","size":12}
            ]}
        """.trimIndent()
        assertNull(GithubRelease.parseRelease(draft))

        val ok = """
            {"draft":false,"prerelease":false,"tag_name":"v0.2.0",
             "html_url":"https://example/r","target_commitish":"abc1234deadbeef",
             "assets":[{"name":"notes.txt","browser_download_url":"https://example/n"},
                       {"name":"nightbrief-v0.2.0.apk","browser_download_url":"https://example/a.apk","size":42}]}
        """.trimIndent()
        val release = GithubRelease.parseRelease(ok)!!
        assertEquals("v0.2.0", release.tag)
        assertEquals("https://example/a.apk", release.apkUrl)
        assertEquals(42L, release.apkBytes)
        assertEquals("abc1234deadbeef", release.targetCommitish)
    }

    @Test
    fun checkStableOffersOnlyNewerVersions() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse().setBody(
                    """
                    {"draft":false,"tag_name":"v0.2.0","html_url":"https://example/r",
                     "assets":[{"name":"app.apk","browser_download_url":"${server.url("app.apk")}","size":9}]}
                    """.trimIndent(),
                ),
            )
            val client = GithubReleaseClient("o", "r", baseUrl = server.url("/").toString().trimEnd('/'))
            val update = client.checkStable("0.1.1")!!
            assertEquals("0.2.0", update.label)
            server.enqueue(
                MockResponse().setBody(
                    """
                    {"draft":false,"tag_name":"v0.2.0","html_url":"https://example/r",
                     "assets":[{"name":"app.apk","browser_download_url":"${server.url("app.apk")}","size":9}]}
                    """.trimIndent(),
                ),
            )
            assertNull(client.checkStable("0.2.0"))
        }
    }

    @Test
    fun checkDevelopComparesShortSha() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse().setBody(
                    """
                    {"draft":false,"prerelease":true,"tag_name":"develop-latest",
                     "html_url":"https://example/r","target_commitish":"abcdef0123456789",
                     "assets":[{"name":"nightbrief-develop-abcdef0.apk",
                                "browser_download_url":"${server.url("app.apk")}","size":8}]}
                    """.trimIndent(),
                ),
            )
            server.enqueue(
                MockResponse().setBody(
                    """
                    {"draft":false,"prerelease":true,"tag_name":"develop-latest",
                     "html_url":"https://example/r","target_commitish":"abcdef0123456789",
                     "assets":[{"name":"nightbrief-develop-abcdef0.apk",
                                "browser_download_url":"${server.url("app.apk")}","size":8}]}
                    """.trimIndent(),
                ),
            )
            val client = GithubReleaseClient("o", "r", baseUrl = server.url("/").toString().trimEnd('/'))
            val update = client.checkDevelop("1111111")!!
            assertEquals("abcdef0", update.label)
            assertEquals("develop:abcdef0", update.id)
            assertNull(client.checkDevelop("abcdef0"))
        }
    }

    @Test
    fun downloadWritesApkAndReportsProgress() = runBlocking {
        MockWebServer().use { server ->
            val bytes = ByteArray(20) { it.toByte() }
            server.enqueue(MockResponse().setBody(okio.Buffer().write(bytes)))
            val update = AvailableUpdate(
                label = "deadbee",
                id = "develop:deadbee",
                htmlUrl = "https://example/r",
                apkUrl = server.url("nightbrief.apk").toString(),
                apkBytes = bytes.size.toLong(),
            )
            val client = GithubReleaseClient("o", "r", baseUrl = server.url("/").toString().trimEnd('/'))
            val dir = File.createTempFile("nb-upd", "dir").apply {
                delete()
                mkdirs()
            }
            val progress = mutableListOf<Float>()
            val file = client.download(update, dir) { progress += it }!!
            assertTrue(file.isFile)
            assertEquals(bytes.toList(), file.readBytes().toList())
            assertTrue("expected progress callbacks, got $progress", progress.isNotEmpty())
            dir.deleteRecursively()
            Unit
        }
    }

    @Test
    fun safeReleasePageUrlAllowsOnlyHttpsGithub() {
        assertTrue(GithubRelease.isSafeReleasePageUrl("https://github.com/kchen52/NightBrief/releases/tag/v1.0.1"))
        assertFalse(GithubRelease.isSafeReleasePageUrl("http://github.com/kchen52/NightBrief/releases/tag/v1.0.1"))
        assertFalse(GithubRelease.isSafeReleasePageUrl("https://evil.example/NightBrief"))
        assertFalse(GithubRelease.isSafeReleasePageUrl("javascript:alert(1)"))
        assertFalse(GithubRelease.isSafeReleasePageUrl("intent://evil#Intent;end"))
    }

    @Test
    fun parseReleaseRejectsNonHttpSchemes() {
        val badHtml = """
            {"draft":false,"tag_name":"v2.0.0","html_url":"javascript:alert(1)",
             "assets":[{"name":"app.apk","browser_download_url":"https://github.com/o/r/a.apk","size":10}]}
        """.trimIndent()
        assertNull(GithubRelease.parseRelease(badHtml))

        val badApk = """
            {"draft":false,"tag_name":"v2.0.0","html_url":"https://github.com/o/r",
             "assets":[{"name":"app.apk","browser_download_url":"file:///etc/passwd","size":10}]}
        """.trimIndent()
        assertNull(GithubRelease.parseRelease(badApk))
    }

    @Test
    fun productionClientAllowsOnlyHttpsGitHubAssetHosts() {
        val client = GithubReleaseClient("kchen52", "NightBrief")
        assertTrue(client.isAllowedDownloadUrl("https://github.com/kchen52/NightBrief/releases/download/v1.0.1/app.apk".toHttpUrl()))
        assertTrue(client.isAllowedDownloadUrl("https://objects.githubusercontent.com/github-production-release-asset-123".toHttpUrl()))
        assertTrue(client.isAllowedDownloadUrl("https://release-assets.githubusercontent.com/asset.apk".toHttpUrl()))
        assertFalse(client.isAllowedDownloadUrl("http://github.com/kchen52/NightBrief/releases/download/v1.0.1/app.apk".toHttpUrl()))
        assertFalse(client.isAllowedDownloadUrl("https://evil.example/nightbrief.apk".toHttpUrl()))
    }

    @Test
    fun downloadRejectsDisallowedHostAndRedirects() = runBlocking {
        MockWebServer().use { server ->
            val dir = File.createTempFile("nb-upd-sec", "dir").apply {
                delete()
                mkdirs()
            }
            try {
                val client = GithubReleaseClient("o", "r", baseUrl = server.url("/").toString().trimEnd('/'))
                val disallowed = AvailableUpdate(
                    label = "2.0.0",
                    id = "v2.0.0",
                    htmlUrl = "https://github.com/o/r",
                    apkUrl = "https://evil.example/evil.apk",
                    apkBytes = 10,
                )
                assertNull(client.download(disallowed, dir))

                server.enqueue(
                    MockResponse()
                        .setResponseCode(302)
                        .addHeader("Location", "https://evil.example/redirected.apk"),
                )
                val redirected = AvailableUpdate(
                    label = "2.0.0",
                    id = "v2.0.0",
                    htmlUrl = "https://github.com/o/r",
                    apkUrl = server.url("nightbrief.apk").toString(),
                    apkBytes = 10,
                )
                assertNull(client.download(redirected, dir))
            } finally {
                dir.deleteRecursively()
            }
        }
    }

    @Test
    fun downloadRejectsOversizedApk() = runBlocking {
        MockWebServer().use { server ->
            val dir = File.createTempFile("nb-upd-size", "dir").apply {
                delete()
                mkdirs()
            }
            try {
                val client = GithubReleaseClient("o", "r", baseUrl = server.url("/").toString().trimEnd('/'))
                val oversized = AvailableUpdate(
                    label = "2.0.0",
                    id = "v2.0.0",
                    htmlUrl = "https://github.com/o/r",
                    apkUrl = server.url("huge.apk").toString(),
                    apkBytes = GithubReleaseClient.MAX_APK_BYTES + 1,
                )
                assertNull(client.download(oversized, dir))
            } finally {
                dir.deleteRecursively()
            }
        }
    }
}
