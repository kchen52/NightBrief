package app.nightbrief.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class AppGraphSanitizeTest {
    @Test
    fun openMeteoKeepsModelAndCoarseLocation() {
        assertEquals(
            "api.open-meteo.com/v1/forecast models=gem_seamless ~43.7,-79.4",
            AppGraph.sanitizeUrl(
                "https://api.open-meteo.com/v1/forecast?latitude=43.6532&longitude=-79.3832" +
                    "&hourly=cloud_cover&models=gem_seamless",
            ),
        )
    }

    @Test
    fun sevenTimerKeepsCoarseLocation() {
        assertEquals(
            "www.7timer.info/bin/api.pl ~43.7,-79.4",
            AppGraph.sanitizeUrl(
                "https://www.7timer.info/bin/api.pl?lon=-79.383&lat=43.653&product=astro&output=json",
            ),
        )
    }

    @Test
    fun hostOnlyUrlsPassThrough() {
        assertEquals(
            "services.swpc.noaa.gov/products/noaa-planetary-k-index-forecast.json",
            AppGraph.sanitizeUrl("https://services.swpc.noaa.gov/products/noaa-planetary-k-index-forecast.json"),
        )
    }

    @Test
    fun preciseCoordinatesNeverAppear() {
        val line = AppGraph.sanitizeUrl(
            "https://api.open-meteo.com/v1/forecast?latitude=43.6532&longitude=-79.3832&hourly=cloud_cover",
        )
        assertFalse("precise latitude leaked into log line: $line", line.contains("43.6532"))
        assertFalse("precise longitude leaked into log line: $line", line.contains("-79.3832"))
    }
}
