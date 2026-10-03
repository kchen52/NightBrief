package app.nightbrief.app.ui

import android.app.Application
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.nightbrief.app.DarkerSkyAccessFilter
import app.nightbrief.app.DarkerSkyUiState
import app.nightbrief.app.ui.theme.NightBriefTheme
import app.nightbrief.app.ui.tonight.DarkerSkyCard
import app.nightbrief.app.ui.tonight.filterByAccess
import app.nightbrief.gear.GearCatalog
import app.nightbrief.score.DarkSkyCandidate
import app.nightbrief.score.NightPlanner
import app.nightbrief.sites.BortleSource
import app.nightbrief.sites.Site
import app.nightbrief.weather.Forecast
import app.nightbrief.weather.HourlyWeather
import app.nightbrief.weather.RoadAccess
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class DarkerSkyUiTest {
    @get:Rule
    val compose = createComposeRule()

    private val home = Site("home", "Home", 43.65, -79.38, "America/Toronto", bortle = 8)
    private val date = LocalDate.of(2024, 8, 12)

    @Test
    fun offersASearchBeforeTheFirstRun() {
        var searched = 0
        show(DarkerSkyUiState(), onSearch = { searched++ })
        compose.onNodeWithText("Find darker sky nearby").assertIsDisplayed().performClick()
        assertEquals(1, searched)
    }

    @Test
    fun showsCandidatesAndSavesOne() {
        var saved: DarkSkyCandidate? = null
        val candidates = listOf(candidate(3, 43.75, -79.10, 28.0, 12))
        show(DarkerSkyUiState(candidates = candidates, searchedSiteId = home.id), onSave = { saved = it })
        compose.onNodeWithText("Show on map").assertIsDisplayed()
        compose.onNodeWithText("Save as site").assertIsDisplayed().performClick()
        assertEquals(candidates.single().site.id, saved?.site?.id)
    }

    @Test
    fun showsTheErrorAndRetries() {
        var searched = 0
        show(DarkerSkyUiState(error = "boom", searchedSiteId = home.id), onSearch = { searched++ })
        compose.onNodeWithText("boom").assertIsDisplayed()
        compose.onNodeWithText("Find darker sky nearby").performClick()
        assertEquals(1, searched)
    }

    @Test
    fun showsTheEmptyState() {
        show(DarkerSkyUiState(candidates = emptyList(), searchedSiteId = home.id))
        compose.onNodeWithText("No darker sky within 60 km of Home.").assertIsDisplayed()
    }

    @Test
    fun showsAccessBadges() {
        val candidates = listOf(
            candidate(3, 43.75, -79.10, 28.0, 12, RoadAccess.DRIVE_UP),
            candidate(4, 43.50, -79.70, 41.0, 7, RoadAccess.HIKE_IN),
        )
        show(DarkerSkyUiState(candidates = candidates, searchedSiteId = home.id))
        // Each label appears twice: once as a filter chip, once as a row badge.
        compose.onAllNodesWithText("Drive-up").assertCountEquals(2)
        compose.onAllNodesWithText("Hike-in").assertCountEquals(2)
    }

    @Test
    fun driveUpFilterHidesHikeInCandidates() {
        val candidates = listOf(
            candidate(3, 43.75, -79.10, 28.0, 12, RoadAccess.DRIVE_UP),
            candidate(4, 43.50, -79.70, 41.0, 7, RoadAccess.HIKE_IN),
        )
        var selected = DarkerSkyAccessFilter.ALL
        show(
            DarkerSkyUiState(candidates = candidates, searchedSiteId = home.id, accessFilter = selected),
            onAccessFilter = { selected = it },
        )
        // Chips compose before the row badges, so index 0 is the Drive-up chip.
        compose.onAllNodesWithText("Drive-up")[0].performClick()
        assertEquals(DarkerSkyAccessFilter.DRIVE_UP, selected)
        val visible = filterByAccess(candidates, DarkerSkyAccessFilter.DRIVE_UP)
        assertEquals(1, visible.size)
        assertEquals(RoadAccess.DRIVE_UP, visible.single().access)
    }

    @Test
    fun strictFiltersExcludeUnknownAccess() {
        val candidates = listOf(candidate(3, 43.75, -79.10, 28.0, 12, RoadAccess.UNKNOWN))
        assertEquals(1, filterByAccess(candidates, DarkerSkyAccessFilter.ALL).size)
        assertEquals(0, filterByAccess(candidates, DarkerSkyAccessFilter.DRIVE_UP).size)
        assertEquals(0, filterByAccess(candidates, DarkerSkyAccessFilter.HIKE_IN).size)
    }

    @Test
    fun emptyAfterFilterOffersShowAll() {
        val candidates = listOf(candidate(4, 43.50, -79.70, 41.0, 7, RoadAccess.HIKE_IN))
        var selected = DarkerSkyAccessFilter.DRIVE_UP
        show(
            DarkerSkyUiState(candidates = candidates, searchedSiteId = home.id, accessFilter = selected),
            onAccessFilter = { selected = it },
        )
        compose.onNodeWithText("No drive-up spots in this search. Try All or Hike-in.").assertIsDisplayed()
        compose.onNodeWithText("Show all").performClick()
        assertEquals(DarkerSkyAccessFilter.ALL, selected)
    }

    private fun show(
        state: DarkerSkyUiState,
        onSearch: (String) -> Unit = {},
        onSave: (DarkSkyCandidate) -> Unit = {},
        onAccessFilter: (DarkerSkyAccessFilter) -> Unit = {},
    ) {
        compose.setContent {
            NightBriefTheme {
                DarkerSkyCard(site = home, state = state, onSearch = onSearch, onSave = onSave, onAccessFilter = onAccessFilter)
            }
        }
    }

    private fun candidate(
        bortle: Int,
        lat: Double,
        lon: Double,
        distanceKm: Double,
        delta: Int,
        access: RoadAccess = RoadAccess.UNKNOWN,
    ): DarkSkyCandidate {
        val site = Site("darksky-$bortle-${lat}_$lon", "candidate", lat, lon, "America/Toronto", bortle = bortle, bortleSource = BortleSource.MAP)
        val report = NightPlanner.plan(site, date, forecast(), GearCatalog.exampleKit)
        return DarkSkyCandidate(site, distanceKm, bortle, report, delta, access)
    }

    private fun forecast(): Forecast {
        val start = Instant.parse("2024-08-12T00:00:00Z").epochSecond
        return Forecast(
            home.latitude, home.longitude, "best_match", start,
            (0 until 72).map {
                HourlyWeather(
                    epochSecond = start + it * 3600L, cloudCover = 5, humidity = 40,
                    windKmh = 6.0, gustKmh = 8.0, jetStreamKmh = 70.0,
                    seeing = 2, transparency = 2,
                )
            },
        )
    }
}
