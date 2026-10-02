package app.nightbrief.score

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class UnitSystemTest {
    @Test
    fun localeDefaultsToFahrenheitOnlyWhereThatIsEverydayTemperature() {
        assertEquals(UnitSystem.IMPERIAL, UnitSystem.fromLocale(Locale.US))
        assertEquals(UnitSystem.IMPERIAL, UnitSystem.fromLocale(Locale.forLanguageTag("en-PR")))
        assertEquals(UnitSystem.METRIC, UnitSystem.fromLocale(Locale.CANADA))
        assertEquals(UnitSystem.METRIC, UnitSystem.fromLocale(Locale.UK))
        assertEquals(UnitSystem.METRIC, UnitSystem.fromLocale(Locale.ROOT))
    }

    @Test
    fun windDisplayConvertsAndTheStoredUnitStaysKilometres() {
        assertEquals("6", UnitSystem.METRIC.formatWind(6.0))
        assertEquals("6", UnitSystem.METRIC.formatWind(6.4))
        assertEquals("4", UnitSystem.IMPERIAL.formatWind(6.0))
        assertEquals("10", UnitSystem.IMPERIAL.formatWind(UnitSystem.KM_PER_MILE * 10))
        assertEquals("1", UnitSystem.IMPERIAL.formatWind(UnitSystem.KM_PER_MILE * 0.5))
        assertEquals("km/h", UnitSystem.METRIC.windUnit)
        assertEquals("mph", UnitSystem.IMPERIAL.windUnit)
    }

    @Test
    fun temperatureDisplayConvertsFromCelsius() {
        assertEquals("32 °F", UnitSystem.IMPERIAL.formatTemperature(0.0))
        assertEquals("−40 °F", UnitSystem.IMPERIAL.formatTemperature(-40.0))
        assertEquals("212 °F", UnitSystem.IMPERIAL.formatTemperature(100.0))
        assertEquals("25 °F", UnitSystem.IMPERIAL.formatTemperature(-4.0))
        assertEquals("−4 °C", UnitSystem.METRIC.formatTemperature(-4.0))
        assertEquals("4 °F", UnitSystem.IMPERIAL.dewSpread())
        assertEquals("2 °C", UnitSystem.METRIC.dewSpread())
    }
}
