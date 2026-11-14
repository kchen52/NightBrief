package app.nightbrief.data

import app.nightbrief.gear.GearCatalog
import app.nightbrief.sites.Site
import app.nightbrief.sites.SiteBook
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayInputStream

class LibraryTransferTest {
    private val home = Site("home", "Home", 43.65, -79.38, "America/Toronto", bortle = 4)
    private val cottage = Site("cottage", "Cottage", 45.0, -78.5, "America/Toronto", bortle = 3)

    @Test
    fun roundTripKeepsSitesAndGear() {
        val state = AppState(
            sites = SiteBook().add(home, makePrimary = true).add(cottage),
            gear = GearCatalog.exampleKit,
            digestTime = "06:30",
        )
        val decoded = LibraryTransfer.decode(LibraryTransfer.encode(state))
        assertEquals(state.sites, decoded.sites)
        assertEquals(state.gear, decoded.gear)
    }

    @Test
    fun unknownKeysAreIgnored() {
        val encoded = LibraryTransfer.encode(
            AppState(sites = SiteBook().add(home, makePrimary = true), gear = GearCatalog.exampleKit),
        )
        val withExtra = encoded.replaceFirst("{", "{\"extra\":true,")
        val decoded = LibraryTransfer.decode(withExtra)
        assertEquals(home.id, decoded.sites.primaryId)
        assertEquals(GearCatalog.exampleKit, decoded.gear)
    }

    @Test(expected = LibraryFormatException::class)
    fun missingSitesIsRejected() {
        LibraryTransfer.decode("""{"gear":{}}""")
    }

    @Test(expected = LibraryFormatException::class)
    fun missingGearIsRejected() {
        LibraryTransfer.decode("""{"sites":{"sites":[],"primaryId":null}}""")
    }

    @Test(expected = LibraryFormatException::class)
    fun emptyObjectIsRejected() {
        LibraryTransfer.decode("{}")
    }

    @Test(expected = LibraryFormatException::class)
    fun malformedJsonIsRejected() {
        LibraryTransfer.decode("not json")
    }

    @Test(expected = EmptyLibraryException::class)
    fun emptySitesIsRejected() {
        LibraryTransfer.decode(
            """
            {
              "sites": { "sites": [], "primaryId": null },
              "gear": {}
            }
            """.trimIndent(),
        )
    }

    @Test
    fun applyReplacesSitesAndGearAndKeepsDigestSettings() {
        val file = LibraryTransfer.decode(
            LibraryTransfer.encode(
                AppState(
                    sites = SiteBook().add(cottage, makePrimary = true),
                    gear = GearCatalog.exampleKit,
                ),
            ),
        )
        val imported = LibraryTransfer.apply(
            AppState(
                digestTime = "09:00",
                digestEnabled = false,
                notificationSlotNext = 8,
            ),
            file,
        )
        assertEquals("09:00", imported.digestTime)
        assertEquals(false, imported.digestEnabled)
        assertEquals(cottage.id, imported.sites.primaryId)
        assertEquals(GearCatalog.exampleKit, imported.gear)
        assertTrue(imported.notificationSlots.values.all { it >= 8 })
    }

    @Test
    fun readBoundedAcceptsShortAndExactCap() {
        val short = "hello".toByteArray()
        assertEquals("hello", LibraryTransfer.readBounded(ByteArrayInputStream(short), cap = 32))
        assertEquals(
            "hello",
            LibraryTransfer.readBounded(ByteArrayInputStream(short), cap = short.size),
        )
    }

    @Test
    fun readBoundedRejectsOneByteOverCap() {
        val bytes = "abcdef".toByteArray()
        try {
            LibraryTransfer.readBounded(ByteArrayInputStream(bytes), cap = bytes.size - 1)
            fail("expected LibraryFormatException")
        } catch (e: LibraryFormatException) {
            assertTrue(e.message!!.contains("too large"))
        }
    }

    @Test
    fun readBoundedRejectsDefaultCapOverflow() {
        val over = ByteArray(LibraryTransfer.MAX_LIBRARY_BYTES + 1) { 'x'.code.toByte() }
        try {
            LibraryTransfer.readBounded(ByteArrayInputStream(over))
            fail("expected LibraryFormatException")
        } catch (e: LibraryFormatException) {
            assertTrue(e.message!!.contains("too large"))
        }
    }
}
