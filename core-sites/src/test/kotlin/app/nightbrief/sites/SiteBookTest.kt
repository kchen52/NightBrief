package app.nightbrief.sites

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream

class SiteBookTest {
    private val home = Site("home", "Home", 43.65, -79.38, "America/Toronto", bortle = 8)
    private val cottage = Site("cottage", "Cottage", 45.0, -78.5, "America/Toronto", bortle = 3)
    private val longPoint = Site("lp", "Long Point", 42.58, -80.40, "America/Toronto", bortle = 3)

    @Test
    fun firstSiteBecomesPrimary() {
        val book = SiteBook().add(home).add(cottage)
        assertEquals("home", book.primaryId)
        assertEquals(listOf(home, cottage), book.primaryFirst())
    }

    @Test
    fun deletingPrimaryPromotesNextSite() {
        val book = SiteBook().add(home).add(cottage).add(longPoint).delete("home")
        assertEquals("cottage", book.primaryId)
        assertEquals(2, book.sites.size)
    }

    @Test
    fun deletingLastSiteClearsPrimary() {
        val book = SiteBook().add(home).delete("home")
        assertNull(book.primaryId)
    }

    @Test
    fun reorderKeepsPrimary() {
        val book = SiteBook().add(home).add(cottage).add(longPoint).move(2, 0)
        assertEquals(listOf("lp", "home", "cottage"), book.sites.map { it.id })
        assertEquals("home", book.primaryId)
        assertEquals(listOf("home", "lp", "cottage"), book.primaryFirst().map { it.id })
    }

    @Test
    fun setPrimaryAndUpdate() {
        val book = SiteBook().add(home).add(cottage).setPrimary("cottage")
            .update(cottage.copy(name = "Muskoka"))
        assertEquals("Muskoka", book.primary!!.name)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsDuplicateIds() {
        SiteBook().add(home).add(home)
    }

    @Test
    fun bortleFromSqm() {
        assertEquals(BortleClass.B1, BortleClass.fromSqm(22.0))
        assertEquals(BortleClass.B4, BortleClass.fromSqm(21.0))
        assertEquals(BortleClass.B9, BortleClass.fromSqm(17.0))
    }

    @Test
    fun naturalSkyIsBortleOne() {
        assertEquals(BortleClass.B1, BortleClass.fromArtificialBrightness(0.0))
        assertEquals(BortleClass.B9, BortleClass.fromArtificialBrightness(20.0))
    }

    @Test
    fun gridLookup() {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { out ->
            out.writeInt(0x4E424C50)
            out.writeInt(1)
            out.writeDouble(40.0) // south
            out.writeDouble(-82.0) // west
            out.writeDouble(1.0) // cell
            out.writeInt(2) // rows
            out.writeInt(3) // cols
            out.write(byteArrayOf(3, 4, 0, 7, 8, 9))
        }
        val grid = GridBortleLookup.read(ByteArrayInputStream(bytes.toByteArray()))
        assertEquals(3, grid.lookup(40.5, -81.5))
        assertEquals(4, grid.lookup(40.5, -80.5))
        assertNull(grid.lookup(40.5, -79.5)) // no-data cell
        assertEquals(9, grid.lookup(41.9, -79.1))
        assertNull(grid.lookup(10.0, 10.0))
    }
}
