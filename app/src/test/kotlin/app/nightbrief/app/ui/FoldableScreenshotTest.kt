package app.nightbrief.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import app.nightbrief.app.ui.icons.NightBriefIcons
import app.nightbrief.app.R
import app.nightbrief.app.ui.common.SiteChip
import app.nightbrief.app.ui.common.SiteStrip
import app.nightbrief.app.ui.theme.NightBriefTheme
import app.nightbrief.app.ui.tonight.NightDetail
import app.nightbrief.app.ui.common.Format
import app.nightbrief.app.ui.week.BestNightCard
import app.nightbrief.app.ui.week.NightRow
import app.nightbrief.gear.GearCatalog
import app.nightbrief.score.BigNightAlerts
import app.nightbrief.score.NightNames
import app.nightbrief.score.NightPlanner
import app.nightbrief.score.NightReport
import app.nightbrief.score.OutlookNight
import app.nightbrief.score.WeeklyOutlook
import app.nightbrief.sites.Site
import app.nightbrief.weather.Forecast
import app.nightbrief.weather.HourlyWeather
import java.time.Instant
import java.time.LocalDate
import org.junit.Rule
import org.junit.Test

/**
 * Unfolded inner display, 1968×2184 at 420 dpi (about 750×832 dp), with the
 * same 80dp rail the nav host uses. Goldens live in `src/test/snapshots`.
 *
 * Record with
 * `./gradlew :app:recordPaparazziDebug --tests app.nightbrief.app.ui.FoldableScreenshotTest`
 * and check with the matching `verifyPaparazziDebug` task. Record on Linux;
 * a Mac render differs by font and locale.
 */
class FoldableScreenshotTest {
    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL.copy(
            screenWidth = 1968,
            screenHeight = 2184,
            xdpi = 420,
            ydpi = 420,
        ),
        theme = "android:Theme.Material.NoActionBar",
    )

    private val home = Site("home", "Home", 43.65, -79.38, "America/Toronto", bortle = 4)
    private val point = Site("point", "Long Point", 42.58, -80.38, "America/Toronto", bortle = 3)
    private val tonight = LocalDate.of(2024, 8, 12)
    private val kit = GearCatalog.exampleKit

    @Test
    fun unfoldedTonight() {
        val report = night(home, tonight).copy(meteor = null, issPasses = emptyList())
        val pointScore = night(point, tonight).scoreValue
        paparazzi.snapshot("unfoldedTonight") {
            FoldFrame(stringResource(R.string.nav_tonight), stringResource(R.string.nav_tonight)) {
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
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
    fun unfoldedWeek() {
        val outlook = weekOutlook()
        val nightNames = NightNames.titles(outlook.nights, BigNightAlerts.threshold)
        paparazzi.snapshot("unfoldedWeek") {
            FoldFrame(
                stringResource(R.string.week_title),
                stringResource(R.string.nav_week),
                subtitle = Format.daySpan(outlook.nights.first().date, outlook.nights.last().date),
            ) {
                val columns = rememberWindowForm().weekColumns
                LazyVerticalGrid(
                    columns = GridCells.Fixed(columns),
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
                ) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        SiteStrip(
                            sites = listOf(
                                SiteChip(home.id, home.name, outlook.best?.score, true, bestNight = outlook.best?.date),
                                SiteChip(point.id, point.name, weekOutlook(point).best?.score, false, bestNight = weekOutlook(point).best?.date),
                            ),
                            selectedId = home.id,
                            onSelect = {},
                            summarizeBestNight = true,
                        )
                    }
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        BestNightCard(outlook)
                    }
                    items(outlook.nights, key = { it.date.toString() }) { night ->
                        NightRow(
                            night = night,
                            isBest = night == outlook.best,
                            modifier = Modifier,
                            onClick = {},
                            nightName = nightNames[night.date],
                        )
                    }
                }
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun FoldFrame(title: String, selected: String, subtitle: String? = null, content: @Composable () -> Unit) {
        NightBriefTheme {
            Row(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                NavigationRail(Modifier.fillMaxHeight()) {
                    tabs.forEach { (label, icon) ->
                        NavigationRailItem(
                            selected = label == selected,
                            onClick = {},
                            icon = { Icon(icon, contentDescription = null) },
                            label = { Text(label) },
                        )
                    }
                }
                Scaffold(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    containerColor = MaterialTheme.colorScheme.background,
                    topBar = {
                        TopAppBar(
                            title = {
                                Column {
                                    Text(title)
                                    if (subtitle != null) {
                                        Text(
                                            subtitle,
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = MaterialTheme.colorScheme.background,
                            ),
                        )
                    },
                ) { padding ->
                    Box(Modifier.padding(padding).fillMaxSize()) { content() }
                }
            }
        }
    }

    private val tabs: List<Pair<String, ImageVector>>
        @Composable get() = listOf(
            stringResource(R.string.nav_tonight) to NightBriefIcons.Tonight,
            stringResource(R.string.nav_week) to NightBriefIcons.Week,
            stringResource(R.string.nav_sites) to NightBriefIcons.Sites,
            stringResource(R.string.nav_gear) to NightBriefIcons.Gear,
        )

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
}
