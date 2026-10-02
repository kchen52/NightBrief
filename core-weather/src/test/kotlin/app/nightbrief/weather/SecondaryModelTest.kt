package app.nightbrief.weather

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class SecondaryModelTest {
    private fun hour(epoch: Long, cloud: Int?) = HourlyWeather(epochSecond = epoch, cloudCover = cloud)

    private fun forecast(vararg clouds: Pair<Long, Int?>) = Forecast(
        43.65, -79.38, "gem_seamless", 0L,
        clouds.map { (epoch, cloud) -> hour(epoch, cloud) },
    )

    @Test
    fun joinsSecondaryCloudByEpoch() {
        val merged = forecast(100L to 10, 200L to 20)
            .withSecondary(forecast(100L to 40, 200L to 50))
        assertEquals(40, merged.hours[0].cloudCoverSecondary)
        assertEquals(50, merged.hours[1].cloudCoverSecondary)
        assertEquals(10, merged.hours[0].cloudCover)
    }

    @Test
    fun hoursTheSecondModelMissesStayNull() {
        val merged = forecast(100L to 10, 200L to 20)
            .withSecondary(forecast(100L to 40))
        assertEquals(40, merged.hours[0].cloudCoverSecondary)
        assertNull(merged.hours[1].cloudCoverSecondary)
    }

    @Test
    fun allNullSecondaryKeepsTheForecast() {
        val primary = forecast(100L to 10)
        assertSame(primary, primary.withSecondary(forecast(100L to null)))
    }

    @Test
    fun emptySecondaryKeepsTheForecast() {
        val primary = forecast(100L to 10)
        assertSame(primary, primary.withSecondary(forecast()))
    }

    @Test
    fun secondaryForUsesAnIndependentCentre() {
        assertEquals(WeatherModel.ICON_SEAMLESS, WeatherModel.secondaryFor(WeatherModel.GEM_SEAMLESS))
        assertEquals(WeatherModel.ICON_SEAMLESS, WeatherModel.secondaryFor(WeatherModel.BEST_MATCH))
    }
}
