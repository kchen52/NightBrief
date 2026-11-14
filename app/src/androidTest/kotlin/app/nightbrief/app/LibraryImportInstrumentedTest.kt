package app.nightbrief.app

import android.app.Application
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.nightbrief.data.AppGraph
import app.nightbrief.data.AppState
import app.nightbrief.data.LibraryTransfer
import app.nightbrief.gear.GearCatalog
import app.nightbrief.sites.Site
import app.nightbrief.sites.SiteBook
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Import matrix for PERF-05. Calls [AppViewModel.importLibrary] with FileProvider URIs so the
 * SAF picker and Compose/Espresso are not required (API 37 removes InputManager.getInstance,
 * which breaks Espresso idle). Run with `./gradlew :app:connectedDebugAndroidTest`.
 */
@RunWith(AndroidJUnit4::class)
class LibraryImportInstrumentedTest {
    private lateinit var app: Application
    private lateinit var vm: AppViewModel

    @Before
    fun seedLibrary() = runBlocking {
        app = ApplicationProvider.getApplicationContext()
        AppGraph.get(app).settings.update {
            AppState(
                onboardingComplete = true,
                sites = SiteBook().add(
                    Site("home", "Home", 43.65, -79.38, "America/Toronto", bortle = 7),
                    makePrimary = true,
                ),
                gear = GearCatalog.exampleKit,
                digestEnabled = false,
                bigNightAlertsEnabled = false,
            )
        }
        vm = AppViewModel(app)
        withTimeout(15_000) {
            while (vm.state.value?.onboardingComplete != true) delay(50)
        }
    }

    @Test
    fun importMatrixCoversValidEmptyMalformedOversizedAndRepeatFailure() = runBlocking {
        val originalPrimary = vm.state.value!!.sites.primaryId

        importAndAwait(
            libraryUri(
                LibraryTransfer.encode(
                    AppState(
                        sites = SiteBook().add(
                            Site("cabin", "Cabin", 49.5, -123.5, "America/Vancouver", bortle = 3),
                            makePrimary = true,
                        ),
                        gear = GearCatalog.exampleKit,
                    ),
                ),
            ),
        )
        assertEquals(app.getString(R.string.library_imported), awaitNote())
        vm.consumeLibraryNote()
        withTimeout(10_000) {
            while (vm.state.value?.sites?.primaryId != "cabin") delay(50)
        }
        assertEquals("cabin", vm.state.value!!.sites.primaryId)

        importAndAwait(libraryUri("""{"sites":{"sites":[],"primaryId":null},"gear":{}}"""))
        assertEquals(app.getString(R.string.library_import_empty), awaitNote())
        vm.consumeLibraryNote()
        assertEquals("cabin", vm.state.value!!.sites.primaryId)

        importAndAwait(libraryUri("not a library"))
        assertEquals(app.getString(R.string.library_import_failed), awaitNote())
        vm.consumeLibraryNote()
        assertEquals("cabin", vm.state.value!!.sites.primaryId)

        importAndAwait(
            libraryUri(
                """{"sites":{"sites":[{"id":"x","name":"X","latitude":0.0,"longitude":0.0,"zoneId":"UTC"}],"primaryId":"x"}}""",
            ),
        )
        assertEquals(app.getString(R.string.library_import_failed), awaitNote())
        vm.consumeLibraryNote()
        assertEquals("cabin", vm.state.value!!.sites.primaryId)

        val over = ByteArray(LibraryTransfer.MAX_LIBRARY_BYTES + 1) { 'x'.code.toByte() }
        importAndAwait(bytesUri(over))
        assertEquals(app.getString(R.string.library_import_failed), awaitNote())
        vm.consumeLibraryNote()
        assertEquals("cabin", vm.state.value!!.sites.primaryId)

        importAndAwait(libraryUri("still not json"))
        assertEquals(app.getString(R.string.library_import_failed), awaitNote())
        vm.consumeLibraryNote()
        assertNull(vm.libraryNote.value)

        assertFalse(vm.libraryBusy.value)
        assertEquals("cabin", vm.state.value!!.sites.primaryId)
        assertTrue(originalPrimary != "cabin")
        // Flow still readable after consume.
        assertNull(vm.libraryNote.first())
    }

    private fun libraryUri(text: String): Uri = bytesUri(text.toByteArray())

    private fun bytesUri(bytes: ByteArray): Uri {
        val dir = File(app.cacheDir, "share").apply { mkdirs() }
        val file = File(dir, "library-import-${System.nanoTime()}.json")
        file.writeBytes(bytes)
        return FileProvider.getUriForFile(app, "${app.packageName}.fileprovider", file)
    }

    private suspend fun importAndAwait(uri: Uri) {
        vm.importLibrary(uri)
        withTimeout(10_000) {
            while (vm.libraryBusy.value || vm.libraryNote.value == null) delay(50)
        }
    }

    private fun awaitNote(): String {
        val note = vm.libraryNote.value
        assertTrue("expected a library note", note != null)
        return note!!
    }
}
