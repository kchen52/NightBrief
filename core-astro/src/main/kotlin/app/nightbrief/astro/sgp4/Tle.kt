package app.nightbrief.astro.sgp4

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.DateTimeException
import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.roundToLong

/** A two-line element set could not be read. */
class TleFormatException(message: String) : IllegalArgumentException(message)

/**
 * A parsed two-line element set.
 *
 * [epoch] is the TLE epoch as a UTC instant. Year is the usual NORAD two-digit
 * window (00–56 → 2000s, 57–99 → 1900s) and the day-of-year fraction is UTC.
 * Orbital angles are stored in radians and mean motion in radians per minute,
 * which is what SGP4 consumes.
 */
class Tle internal constructor(
    val line1: String,
    val line2: String,
    val epoch: Instant,
    internal val bstar: Double,
    internal val ndot: Double,
    internal val nddot: Double,
    internal val ecco: Double,
    internal val argpoRad: Double,
    internal val incloRad: Double,
    internal val moRad: Double,
    internal val noKozaiRadPerMin: Double,
    internal val nodeoRad: Double,
    /** Days from 0 January 1950, 0h UTC. This is the SGP4 epoch argument. */
    internal val epochDaysFrom1950: Double,
) {
    companion object {
        private const val DEG2RAD = PI / 180.0
        private const val XPDOTP = 1440.0 / (2.0 * PI)
        private const val JD_JAN0_1950 = 2433281.5
        private const val UNIX_EPOCH_JD = 2440587.5

        fun parse(line1: String, line2: String): Tle {
            val first = line1.trimEnd()
            val second = line2.trimEnd()
            requireLine1(first)
            requireLine2(second)
            verifyChecksum(first, 1)
            verifyChecksum(second, 2)
            return try {
                parseChecked(first, second)
            } catch (e: TleFormatException) {
                throw e
            } catch (e: NumberFormatException) {
                throw TleFormatException("TLE contains a non-numeric field: ${e.message}")
            }
        }

        private fun parseChecked(line1: String, line2: String): Tle {
            val satnum = line1.substring(2, 7)
            if (satnum != line2.substring(2, 7)) {
                throw TleFormatException("TLE object numbers do not match")
            }
            val yearTwo = line1.substring(18, 20).toInt()
            val epochDays = line1.substring(20, 32).toDouble()
            val ndotRaw = line1.substring(33, 43).toDouble()
            val nddotRaw = (line1.substring(44, 45) + "." + line1.substring(45, 50)).toDouble()
            val nexp = line1.substring(50, 52).toInt()
            val bstarRaw = (line1.substring(53, 54) + "." + line1.substring(54, 59)).toDouble()
            val ibexp = line1.substring(59, 61).toInt()

            val inclo = line2.substring(8, 16).toDouble()
            val nodeo = line2.substring(17, 25).toDouble()
            val ecco = ("0." + line2.substring(26, 33).replace(' ', '0')).toDouble()
            val argpo = line2.substring(34, 42).toDouble()
            val mo = line2.substring(43, 51).toDouble()
            val noRevPerDay = line2.substring(52, 63).toDouble()

            val epoch = epochInstant(yearTwo, epochDays)
            val nddot = nddotRaw * 10.0.pow(nexp) / (XPDOTP * 1440.0 * 1440.0)
            val bstar = bstarRaw * 10.0.pow(ibexp)
            return Tle(
                line1 = line1,
                line2 = line2,
                epoch = epoch,
                bstar = bstar,
                ndot = ndotRaw / (XPDOTP * 1440.0),
                nddot = nddot,
                ecco = ecco,
                argpoRad = argpo * DEG2RAD,
                incloRad = inclo * DEG2RAD,
                moRad = mo * DEG2RAD,
                noKozaiRadPerMin = noRevPerDay / XPDOTP,
                nodeoRad = nodeo * DEG2RAD,
                epochDaysFrom1950 = julianDate(epoch) - JD_JAN0_1950,
            )
        }

        private fun epochInstant(twoDigitYear: Int, epochDays: Double): Instant {
            if (epochDays <= 0.0 || epochDays >= 367.0) {
                throw TleFormatException("TLE epoch day is out of range: $epochDays")
            }
            val year = if (twoDigitYear < 57) 2000 + twoDigitYear else 1900 + twoDigitYear
            val whole = floor(epochDays).toInt()
            val fraction = epochDays - whole
            val date = try {
                LocalDate.ofYearDay(year, whole)
            } catch (e: DateTimeException) {
                throw TleFormatException("TLE epoch day $epochDays is not a date in $year")
            }
            // Round to the nearest microsecond, matching Vallado's days2mdhms. The TLE day
            // fraction does not justify a finer instant, and rounding the binary product
            // straight to nanoseconds is off by a nanosecond.
            val micros = (fraction * 86_400.0 * 1_000_000.0).roundToLong()
            return date.atStartOfDay(ZoneOffset.UTC).toInstant().plusNanos(micros * 1_000)
        }

        private fun requireLine1(line: String) {
            if (line.length < 69 ||
                !line.startsWith("1 ") ||
                line[8] != ' ' || line[23] != '.' || line[32] != ' ' || line[34] != '.' ||
                line[43] != ' ' || line[52] != ' ' || line[61] != ' ' || line[63] != ' '
            ) {
                throw TleFormatException("TLE line 1 is not in the 69-character element format")
            }
        }

        private fun requireLine2(line: String) {
            if (line.length < 69 ||
                !line.startsWith("2 ") ||
                line[7] != ' ' || line[11] != '.' || line[16] != ' ' || line[20] != '.' ||
                line[25] != ' ' || line[33] != ' ' || line[37] != '.' || line[42] != ' ' ||
                line[46] != '.' || line[51] != ' '
            ) {
                throw TleFormatException("TLE line 2 is not in the 69-character element format")
            }
        }

        private fun verifyChecksum(line: String, which: Int) {
            val expected = line[68]
            if (!expected.isDigit()) {
                throw TleFormatException("TLE line $which is missing its checksum digit")
            }
            var sum = 0
            for (i in 0 until 68) {
                val c = line[i]
                when {
                    c.isDigit() -> sum += c.digitToInt()
                    c == '-' -> sum += 1
                }
            }
            if (sum % 10 != expected.digitToInt()) {
                throw TleFormatException("TLE line $which checksum does not match")
            }
        }
    }
}

internal fun julianDate(at: Instant): Double =
    at.epochSecond / 86_400.0 + at.nano / 86_400e9 + 2440587.5
