package app.nightbrief.app.widget

import android.app.Application
import androidx.glance.appwidget.testing.unit.runGlanceAppWidgetUnitTest
import androidx.glance.testing.unit.hasText
import androidx.test.core.app.ApplicationProvider
import app.nightbrief.score.SavedForecast
import app.nightbrief.score.WidgetCopy
import app.nightbrief.weather.ForecastStatus
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class NightBriefWidgetTest {
    private val zone = ZoneId.of("America/Toronto")

    @Test
    fun setupPromptWhenOnboardingIsIncomplete() = runGlanceAppWidgetUnitTest {
        setContext(ApplicationProvider.getApplicationContext())
        provideComposable { NightBriefWidgetContent(WidgetContent.Setup) }
        onNode(hasText("Set up NightBrief")).assertExists()
    }

    @Test
    fun showsTheScoreAndMilkyWayCoreNotUp() = runGlanceAppWidgetUnitTest {
        setContext(ApplicationProvider.getApplicationContext())
        provideComposable {
            NightBriefWidgetContent(
                WidgetContent.Night(
                    siteName = "Home",
                    scoreLine = WidgetCopy.scoreLine(92),
                    milkyWayLine = WidgetCopy.milkyWayLine(null, zone),
                ),
            )
        }
        onNode(hasText("Home")).assertExists()
        onNode(hasText(WidgetCopy.scoreLine(92))).assertExists()
        onNode(hasText("Milky Way core not up")).assertExists()
    }

    @Test
    fun showsWhenAStaleForecastWasSaved() = runGlanceAppWidgetUnitTest {
        setContext(ApplicationProvider.getApplicationContext())
        val saved = SavedForecast.shortLabel(
            ForecastStatus.STALE,
            Instant.parse("2026-10-01T22:40:00Z"),
            zone,
        )!!
        provideComposable {
            NightBriefWidgetContent(
                WidgetContent.Night(
                    siteName = "Home",
                    scoreLine = WidgetCopy.scoreLine(70),
                    milkyWayLine = WidgetCopy.milkyWayLine(null, zone),
                    savedLine = saved,
                ),
            )
        }
        onNode(hasText(saved)).assertExists()
        onNode(hasText(WidgetCopy.scoreLine(70))).assertExists()
    }
}
