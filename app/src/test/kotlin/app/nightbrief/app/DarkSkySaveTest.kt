package app.nightbrief.app

import app.nightbrief.gear.GearCatalog
import app.nightbrief.score.DarkSkyCandidate
import app.nightbrief.score.NightPlanner
import app.nightbrief.sites.Site
import app.nightbrief.sites.SiteBook
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class DarkSkySaveTest {
    private val home = Site("home", "Toronto", 43.6532, -79.3832, "America/Toronto")
    private val spot = Site(
        "darksky-43.653--79.010",
        "Bortle 4 · 30 km E",
        43.6526,
        -79.0103,
        "America/Toronto",
        bortle = 4,
    )

    @Test
    fun aSavedSpotIsRecognizedAfterItGetsANewId() {
        val saved = spot.copy(id = "uuid-from-the-first-save")
        val book = SiteBook().add(home, makePrimary = true).add(saved)
        val ids = savedDarkSkyIds(book, listOf(candidate(spot)))
        assertEquals(setOf(spot.id), ids)
    }

    @Test
    fun aDifferentPlaceIsNotTreatedAsSaved() {
        val book = SiteBook().add(home, makePrimary = true)
        assertTrue(savedDarkSkyIds(book, listOf(candidate(spot))).isEmpty())
    }

    private fun candidate(site: Site) = DarkSkyCandidate(
        site = site,
        distanceKm = 30.0,
        bortle = 4,
        report = NightPlanner.plan(site, LocalDate.of(2026, 11, 3), null, GearCatalog.exampleKit),
        delta = null,
    )
}
