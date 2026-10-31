package app.nightbrief.app.ui.tonight.whimsy

import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2

class ShootingStarTest {
    @Test
    fun tailLiesOnTheFlightAndStaysInTheUpperSky() {
        val width = 400f
        val height = 260f
        val early = shootingStarStreak(width, height, 0.25f)!!
        val late = shootingStarStreak(width, height, 0.75f)!!
        val flightX = late.head.x - early.head.x
        val flightY = late.head.y - early.head.y
        listOf(0.2f, 0.5f, 0.8f).forEach { t ->
            val streak = shootingStarStreak(width, height, t)!!
            val trailX = streak.head.x - streak.tail.x
            val trailY = streak.head.y - streak.tail.y
            val cross = flightX * trailY - flightY * trailX
            val dot = flightX * trailX + flightY * trailY
            assertTrue("cross $cross at $t", abs(cross) < 1f)
            assertTrue("dot $dot at $t", dot > 0f)
            assertTrue("head y ${streak.head.y}", streak.head.y < height * 0.5f)
            assertTrue(streak.head.y > streak.tail.y)
        }
        val angle = atan2(flightY.toDouble(), flightX.toDouble()) * 180.0 / PI
        assertTrue("angle $angle", angle in 20.0..28.0)
        assertTrue(late.head.x > early.head.x)
    }

    @Test
    fun theStreakIsHiddenAtTheEnds() {
        assertNull(shootingStarStreak(400f, 200f, 0f))
        assertNull(shootingStarStreak(400f, 200f, 1f))
        assertNull(shootingStarStreak(0f, 200f, 0.5f))
    }
}
