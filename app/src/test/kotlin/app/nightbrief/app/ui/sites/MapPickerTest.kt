package app.nightbrief.app.ui.sites

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import app.nightbrief.app.SiteDraft
import app.nightbrief.app.ui.common.Format
import app.nightbrief.app.ui.theme.NightBriefTheme
import app.nightbrief.weather.PlaceMatch
import app.nightbrief.weather.PlaceSearchResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class MapPickerTest {
    @get:Rule
    val compose = createComposeRule()

    private val banff = PlaceMatch("Banff", 51.1784, -115.5708, region = "Alberta", country = "Canada")

    @Test
    fun confirmButtonStaysOnScreenOnceASpotIsChosen() {
        compose.setContent {
            NightBriefTheme {
                MapPickerDialog(
                    initialLat = 43.6532,
                    initialLon = -79.3832,
                    onDismiss = {},
                    searchPlaces = { PlaceSearchResult() },
                    onPick = { _, _, _ -> },
                )
            }
        }
        val label = "Use ${Format.coordinates(43.6532, -79.3832)}"
        val button = compose.onNodeWithText(label)
        button.assertIsDisplayed()
        assertFullyVisible(button)
        compose.onNodeWithText("Map data © OpenStreetMap contributors").assertIsDisplayed()
        compose.onNodeWithText("Place names by Open-Meteo and GeoNames").assertIsDisplayed()
        compose.onNodeWithText("Search for a place").assertIsDisplayed()
    }

    @Test
    fun aPlaceNameMovesThePinAndConfirmsThoseCoordinates() {
        var picked: Triple<Double, Double, String?>? = null
        compose.setContent {
            NightBriefTheme {
                MapPickerDialog(
                    initialLat = null,
                    initialLon = null,
                    onDismiss = {},
                    searchPlaces = { query ->
                        if (query == "Banf") PlaceSearchResult(listOf(banff)) else PlaceSearchResult()
                    },
                    onPick = { lat, lon, name -> picked = Triple(lat, lon, name) },
                )
            }
        }
        compose.onNodeWithText("Search for a place").performTextInput("Banf")
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithContentDescription("Banff, Alberta, Canada").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithContentDescription("Banff, Alberta, Canada").performClick()
        val label = "Use ${Format.coordinates(banff.latitude, banff.longitude)}"
        val button = compose.onNodeWithText(label)
        button.assertIsDisplayed()
        assertFullyVisible(button)
        button.performClick()
        assertEquals(Triple(banff.latitude, banff.longitude, "Banff"), picked)
    }

    @Test
    fun aFailedSearchLeavesTheMapAndSaysSo() {
        compose.setContent {
            NightBriefTheme {
                MapPickerDialog(
                    initialLat = null,
                    initialLon = null,
                    onDismiss = {},
                    searchPlaces = { PlaceSearchResult(failed = true) },
                    onPick = { _, _, _ -> },
                )
            }
        }
        compose.onNodeWithText("Search for a place").performTextInput("Banff")
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithText("Couldn't search places. Tap the map instead.").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Tap the map to choose a spot").assertIsDisplayed()
    }

    @Test
    fun aBlankSiteNameTakesThePlaceAndATypedNameStays() {
        var draft by mutableStateOf(SiteDraft())
        compose.setContent {
            NightBriefTheme {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                SiteForm(
                    draft = draft,
                    onChange = { draft = it },
                    lookupBortle = { _, _ -> null },
                    lookupTimeZone = { _, _ -> null },
                    searchPlaces = { PlaceSearchResult(listOf(banff)) },
                    showPrimaryToggle = false,
                    showDigestOverride = false,
                    globalDigestTime = "08:00",
                )
                }
            }
        }
        compose.onNodeWithText("Pick on map").performScrollTo().performClick()
        compose.onNodeWithText("Search for a place").performTextInput("Ban")
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithContentDescription("Banff, Alberta, Canada").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithContentDescription("Banff, Alberta, Canada").performClick()
        compose.onNodeWithText("Use ${Format.coordinates(banff.latitude, banff.longitude)}").performClick()
        compose.waitUntil(timeoutMillis = 5_000) { draft.name == "Banff" }
        assertEquals("51.17840", draft.latitude)
        assertEquals("-115.57080", draft.longitude)

        draft = draft.copy(name = "Cabin")
        compose.onNodeWithText("Pick on map").performScrollTo().performClick()
        compose.onNodeWithText("Search for a place").performTextInput("Ban")
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithContentDescription("Banff, Alberta, Canada").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithContentDescription("Banff, Alberta, Canada").performClick()
        compose.onNodeWithText("Use ${Format.coordinates(banff.latitude, banff.longitude)}").performClick()
        compose.waitUntil(timeoutMillis = 5_000) { draft.latitude == "51.17840" }
        assertEquals("Cabin", draft.name)
        assertNull(draft.bortle)
    }

    private fun assertFullyVisible(node: SemanticsNodeInteraction) {
        val visible = node.getBoundsInRoot()
        val full = node.getUnclippedBoundsInRoot()
        assertEquals("confirm button was clipped at the bottom", full.bottom.value, visible.bottom.value, 0.5f)
        assertEquals("confirm button was clipped at the top", full.top.value, visible.top.value, 0.5f)
    }
}
