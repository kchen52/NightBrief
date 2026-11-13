package app.nightbrief.app.ui

import android.app.Application
import app.nightbrief.app.ui.common.Format
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class FormatLocaleTest {
    @Test
    fun formattersFollowALocaleChangeWithoutRestarting() {
        val original = Locale.getDefault()
        try {
            val instant = Instant.parse("2024-08-12T22:30:00Z")
            val date = LocalDate.of(2024, 8, 12)
            val zone = ZoneId.of("UTC")

            Locale.setDefault(Locale.US)
            val usTime = Format.time(instant, zone)
            val usMonth = Format.dayMonth(date)

            Locale.setDefault(Locale.GERMANY)
            val germanTime = Format.time(instant, zone)
            val germanMonth = Format.dayMonth(date)

            assertNotEquals("12-hour and 24-hour clocks should differ", usTime, germanTime)
            assertNotEquals("month labels should follow the locale", usMonth, germanMonth)
        } finally {
            Locale.setDefault(original)
        }
    }
}
