package app.nightbrief.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import app.nightbrief.app.ui.common.GlassActionCluster
import app.nightbrief.app.ui.common.GlassNavBar
import app.nightbrief.app.ui.common.GlassNavItem
import app.nightbrief.app.ui.common.GlassTopBarBackdrop
import app.nightbrief.app.ui.common.LocalHazeState
import app.nightbrief.app.ui.common.SiteChip
import app.nightbrief.app.ui.common.SiteStrip
import app.nightbrief.app.ui.common.glassNavBarPadding
import app.nightbrief.app.ui.common.glassSource
import app.nightbrief.app.ui.theme.NightBriefTheme
import app.nightbrief.app.ui.tonight.NightDetail
import app.nightbrief.app.ui.week.BestNightCard
import app.nightbrief.app.ui.week.NightRow
import app.nightbrief.app.ui.week.WeekTitle
import app.nightbrief.gear.GearCatalog
import app.nightbrief.score.NightPlanner
import app.nightbrief.score.NightReport
import app.nightbrief.score.OutlookNight
import app.nightbrief.score.WeeklyOutlook
import app.nightbrief.sites.Site
import app.nightbrief.weather.Forecast
import app.nightbrief.weather.HourlyWeather
import dev.chrisbanes.haze.rememberHazeState
import org.junit.Rule
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

/** Haze glass tab bar and Tonight toolbar over the same data as [ReadmeScreenshotTest]. */
class GlassScreenshotTest {
    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_5,
        theme = "android:Theme.Material.NoActionBar",
    )

    private val home = Site("home", "Home", 43.65, -79.38, "America/Toronto", bortle = 4)
    private val point = Site("point", "Long Point", 42.58, -80.38, "America/Toronto", bortle = 3)
    private val tonight = LocalDate.of(2024, 8, 12)
    private val kit = GearCatalog.exampleKit

    @Test
    fun tonightTop() = snapshotTonight("glass-tonight", scrollPx = 0)

    @Test
    fun tonightScrolled() = snapshotTonight("glass-tonight-scrolled", scrollPx = 620)

    @Test
    fun tonightNightVision() = snapshotTonight("glass-night-vision", scrollPx = 620, nightVision = true)

    @OptIn(ExperimentalMaterial3Api::class)
    @Test
    fun week() {
        val outlook = weekOutlook()
        paparazzi.snapshot("glass-week") {
            GlassPhone(tab = "Week") { haze, bottom ->
                Scaffold(
                    containerColor = Color.Transparent,
                    topBar = {
                        TopAppBar(
                            title = { WeekTitle(outlook) },
                            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                        )
                    },
                ) { inner ->
                    LazyColumn(
                        Modifier.padding(inner).fillMaxSize().glassSource(haze),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(bottom = 16.dp + bottom),
                    ) {
                        item {
                            BestNightCard(outlook, Modifier.padding(horizontal = 16.dp))
                        }
                        items(outlook.nights + outlook.nights, key = null) { night ->
                            NightRow(
                                night = night,
                                isBest = night == outlook.best,
                                modifier = Modifier.padding(horizontal = 16.dp),
                                onClick = {},
                                nightName = null,
                            )
                        }
                    }
                }
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    private fun snapshotTonight(name: String, scrollPx: Int, nightVision: Boolean = false) {
        val report = night(home, tonight).copy(meteor = null, issPasses = emptyList())
        val pointScore = night(point, tonight).scoreValue
        paparazzi.snapshot(name) {
            GlassPhone(tab = "Tonight", nightVision = nightVision) { haze, bottom ->
                Scaffold(
                    containerColor = Color.Transparent,
                    topBar = {
                        Box {
                            GlassTopBarBackdrop(Modifier.matchParentSize())
                            TopAppBar(
                                title = { Text("Tonight") },
                                actions = {
                                    GlassActionCluster {
                                        Action(highlighted = nightVision) {
                                            IconButton(onClick = {}) {
                                                Icon(
                                                    Icons.Filled.Visibility,
                                                    "Night vision",
                                                    tint = if (nightVision) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                                )
                                            }
                                        }
                                        Action { IconButton(onClick = {}) { Icon(Icons.Filled.Refresh, "Refresh forecast") } }
                                        Action { IconButton(onClick = {}) { Icon(Icons.Filled.Share, "Share") } }
                                        Action { IconButton(onClick = {}) { Icon(Icons.Filled.Settings, "Settings") } }
                                    }
                                },
                                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                            )
                        }
                    },
                ) { inner ->
                    Column(
                        Modifier
                            .fillMaxSize()
                            .glassSource(haze)
                            .verticalScroll(rememberScrollState(scrollPx))
                            .padding(top = inner.calculateTopPadding(), bottom = bottom),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        SiteStrip(
                            sites = listOf(
                                SiteChip(home.id, home.name, report.scoreValue, true),
                                SiteChip(point.id, point.name, pointScore, false),
                            ),
                            selectedId = home.id,
                            onSelect = {},
                        )
                        NightDetail(report, alternative = null, onOpenAlternative = {})
                    }
                }
            }
        }
    }

    @Composable
    private fun GlassPhone(
        tab: String,
        nightVision: Boolean = false,
        content: @Composable (dev.chrisbanes.haze.HazeState, androidx.compose.ui.unit.Dp) -> Unit,
    ) {
        NightBriefTheme(nightVision = nightVision) {
            val haze = rememberHazeState()
            CompositionLocalProvider(LocalHazeState provides haze) {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    content(haze, glassNavBarPadding().calculateBottomPadding())
                    GlassNavBar(
                        items = tabs.map { (label, icon) -> GlassNavItem(label, icon, label == tab, onClick = {}) },
                        modifier = Modifier.align(Alignment.BottomCenter),
                    )
                }
            }
        }
    }

    private fun night(site: Site, date: LocalDate): NightReport =
        NightPlanner.plan(site, date, forecast(), kit)

    private fun weekOutlook(): WeeklyOutlook {
        val nights = (0 until 7).map { offset ->
            val report = night(home, tonight.plusDays(offset.toLong()))
            OutlookNight(
                report.date,
                report.scoreValue,
                report.ephemeris.moonIllumination,
                report.ephemeris.darkness,
                report.coverage,
            )
        }
        return WeeklyOutlook(home, nights)
    }

    private fun forecast(): Forecast {
        val start = Instant.parse("2024-08-12T00:00:00Z").epochSecond
        return Forecast(
            home.latitude, home.longitude, "best_match", start,
            (0 until 24 * 8).map {
                HourlyWeather(
                    epochSecond = start + it * 3600L, cloudCover = 5, humidity = 40,
                    windKmh = 6.0, gustKmh = 8.0, jetStreamKmh = 70.0,
                    seeing = 2, transparency = 2,
                )
            },
        )
    }

    private companion object {
        val tabs = listOf(
            "Tonight" to Icons.Filled.NightsStay,
            "Week" to Icons.Filled.DateRange,
            "Sites" to Icons.Filled.Place,
            "Gear" to Icons.Filled.PhotoCamera,
        )
    }
}
