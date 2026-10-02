package app.nightbrief.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
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
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import app.nightbrief.app.ui.common.SectionCard
import app.nightbrief.app.ui.common.SiteChip
import app.nightbrief.app.ui.common.SiteStrip
import app.nightbrief.app.ui.gear.GearEditor
import app.nightbrief.app.ui.theme.NightBriefTheme
import app.nightbrief.app.ui.tonight.NightDetail
import androidx.compose.ui.res.stringResource
import app.nightbrief.app.R
import app.nightbrief.app.ui.week.NightRow
import app.nightbrief.astro.IssPass
import app.nightbrief.gear.GearCatalog
import app.nightbrief.score.NightPlanner
import app.nightbrief.score.NightReport
import app.nightbrief.score.OutlookNight
import app.nightbrief.score.WeeklyOutlook
import app.nightbrief.sites.Site
import app.nightbrief.weather.Forecast
import app.nightbrief.weather.HourlyWeather
import org.junit.Rule
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

/**
 * Phone frames for the README. After recording, copy the PNGs to `docs/screenshots/`:
 * `./gradlew :app:recordPaparazziDebug --tests app.nightbrief.app.ui.ReadmeScreenshotTest`
 */
class ReadmeScreenshotTest {
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
    fun tonight() {
        val report = night(home, tonight).copy(meteor = null, issPasses = emptyList())
        val pointScore = night(point, tonight).scoreValue
        paparazzi.snapshot("tonight") {
            Phone("Tonight", "Tonight", actions = {
                IconButton(onClick = {}) { Icon(Icons.Filled.Refresh, "Refresh forecast") }
                IconButton(onClick = {}) { Icon(Icons.Filled.Settings, "Settings") }
            }) { padding ->
                Column(
                    Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()),
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

    @Test
    fun nightVision() {
        val report = night(home, tonight).copy(meteor = null, issPasses = emptyList())
        val pointScore = night(point, tonight).scoreValue
        paparazzi.snapshot("night-vision") {
            Phone(
                title = "Tonight",
                tab = "Tonight",
                nightVision = true,
                actions = {
                    IconButton(onClick = {}) {
                        Icon(
                            Icons.Filled.Visibility,
                            stringResource(R.string.night_vision_on),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                    IconButton(onClick = {}) { Icon(Icons.Filled.Refresh, stringResource(R.string.refresh_forecast)) }
                    IconButton(onClick = {}) { Icon(Icons.Filled.Settings, stringResource(R.string.settings)) }
                },
            ) { padding ->
                Column(
                    Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()),
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

    @Test
    fun meteors() {
        val planned = night(home, tonight)
        val report = planned.copy(
            issPasses = listOf(
                IssPass(
                    rise = Instant.parse("2024-08-13T02:10:00Z"),
                    set = Instant.parse("2024-08-13T02:16:00Z"),
                    peak = Instant.parse("2024-08-13T02:13:00Z"),
                    peakAltitudeDeg = 62.0,
                    peakAzimuthDeg = 140.0,
                ),
            ),
        )
        paparazzi.snapshot("meteors") {
            Phone("Tonight", "Tonight") { padding ->
                Box(Modifier.padding(padding).fillMaxSize().clipToBounds()) {
                    Column(Modifier.offset(y = (-360).dp)) {
                        NightDetail(report, alternative = null, onOpenAlternative = {})
                    }
                }
            }
        }
    }

    @Test
    fun week() {
        val outlook = weekOutlook()
        paparazzi.snapshot("week") {
            Phone("This week", "Week") { padding ->
                LazyColumn(
                    Modifier.padding(padding).fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 16.dp),
                ) {
                    item {
                        SiteStrip(
                            sites = listOf(
                                SiteChip(home.id, home.name, outlook.best?.score, true),
                                SiteChip(point.id, point.name, weekOutlook(point).best?.score, false),
                            ),
                            selectedId = home.id,
                            onSelect = {},
                        )
                    }
                    item {
                        SectionCard(modifier = Modifier.padding(horizontal = 16.dp)) {
                            Text(outlook.headline(), style = MaterialTheme.typography.titleLarge)
                            Text(
                                stringResource(R.string.week_footnote),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    items(outlook.nights, key = { it.date.toString() }) { night ->
                        NightRow(
                            night = night,
                            isBest = night == outlook.best,
                            modifier = Modifier.padding(horizontal = 16.dp),
                            onClick = {},
                        )
                    }
                }
            }
        }
    }

    @Test
    fun gear() {
        paparazzi.snapshot("gear") {
            Phone("Gear", "Gear") { padding ->
                Column(
                    Modifier
                        .padding(padding)
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp),
                ) {
                    Spacer(Modifier.height(8.dp))
                    GearEditor(kit, onChange = {})
                }
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun Phone(
        title: String,
        tab: String,
        nightVision: Boolean = false,
        actions: @Composable RowScope.() -> Unit = {},
        content: @Composable (PaddingValues) -> Unit,
    ) {
        NightBriefTheme(nightVision = nightVision) {
            Scaffold(
                containerColor = MaterialTheme.colorScheme.background,
                topBar = {
                    TopAppBar(
                        title = { Text(title) },
                        actions = actions,
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                    )
                },
                bottomBar = {
                    NavigationBar {
                        tabs.forEach { (label, icon) ->
                            NavigationBarItem(
                                selected = label == tab,
                                onClick = {},
                                icon = { Icon(icon, contentDescription = null) },
                                label = { Text(label) },
                            )
                        }
                    }
                },
            ) { padding -> content(padding) }
        }
    }

    private fun night(site: Site, date: LocalDate): NightReport =
        NightPlanner.plan(site, date, forecast(), kit)

    private fun weekOutlook(site: Site = home): WeeklyOutlook {
        val nights = (0 until 7).map { offset ->
            val report = night(site, tonight.plusDays(offset.toLong()))
            OutlookNight(
                report.date,
                report.scoreValue,
                report.ephemeris.moonIllumination,
                report.ephemeris.darkness,
                report.coverage,
            )
        }
        return WeeklyOutlook(site, nights)
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
        val tabs: List<Pair<String, ImageVector>> = listOf(
            "Tonight" to Icons.Filled.NightsStay,
            "Week" to Icons.Filled.DateRange,
            "Sites" to Icons.Filled.Place,
            "Gear" to Icons.Filled.PhotoCamera,
        )
    }
}
