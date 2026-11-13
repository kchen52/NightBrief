package app.nightbrief.app.update

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
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
}
