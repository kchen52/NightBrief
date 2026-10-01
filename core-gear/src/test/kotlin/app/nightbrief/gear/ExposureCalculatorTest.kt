package app.nightbrief.gear

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExposureCalculatorTest {
    private val r7 = GearCatalog.canonR7
    private val sigma = GearCatalog.sigma10to18

    @Test
    fun canonR7SensorProperties() {
        assertEquals(1.6, r7.cropFactor, 0.02)
        assertEquals(3.2, r7.pixelPitchUm, 0.05)
        assertEquals(SensorFormat.APS_C, r7.format)
    }

    @Test
    fun fullFrameCropIsOne() {
        val a7 = GearCatalog.bodies.first { it.id == "sony-a7iii" }
        assertEquals(1.0, a7.cropFactor, 0.02)
        assertEquals(SensorFormat.FULL_FRAME, a7.format)
    }

    @Test
    fun rule500ForR7At10mm() {
        assertEquals(31.1, ExposureCalculator.rule500(10.0, r7.cropFactor), 0.5)
    }

    @Test
    fun npfIsStricterThan500RuleOnHighResolutionSensor() {
        val npf = ExposureCalculator.npf(10.0, 2.8, r7.pixelPitchUm)
        assertEquals((35 * 2.8 + 30 * r7.pixelPitchUm) / 10.0, npf, 1e-9)
        assertTrue(npf < ExposureCalculator.rule500(10.0, r7.cropFactor))
    }

    @Test
    fun npfGrowsTowardsThePole() {
        val equator = ExposureCalculator.npf(14.0, 2.8, 5.9, 0.0)
        val dec60 = ExposureCalculator.npf(14.0, 2.8, 5.9, 60.0)
        assertEquals(equator * 2, dec60, 1e-6)
    }

    @Test
    fun shutterRoundsDownToCameraStops() {
        assertEquals(15.0, ExposureCalculator.roundDownToStandardShutter(19.4), 0.0)
        assertEquals(20.0, ExposureCalculator.roundDownToStandardShutter(20.0), 0.0)
        assertEquals(1.0, ExposureCalculator.roundDownToStandardShutter(0.3), 0.0)
    }

    @Test
    fun exampleKitMilkyWaySuggestion() {
        val s = ExposureCalculator.bestFor(
            GearCatalog.exampleKit, idealFocalFullFrameMm = 24.0, minFocalFullFrameMm = 12.0,
            bortle = 3, brightMoon = false,
        )!!
        assertEquals(sigma, s.lens)
        assertEquals(15.0, s.focalMm, 0.1) // 24mm FF-equivalent on a 1.6x body
        assertEquals(2.8, s.aperture, 0.0)
        assertEquals(10.0, s.shutterSeconds, 0.0)
        assertTrue(s.iso in 3200..6400)
        assertTrue(s.reachesTarget)
    }

    @Test
    fun brighterSkiesLowerIso() {
        val dark = ExposureCalculator.suggestIso(15.0, 2.8, bortle = 2, brightMoon = false)
        val suburb = ExposureCalculator.suggestIso(15.0, 2.8, bortle = 6, brightMoon = false)
        val moonlit = ExposureCalculator.suggestIso(15.0, 2.8, bortle = 6, brightMoon = true)
        assertTrue(dark > suburb)
        assertTrue(suburb > moonlit)
    }

    @Test
    fun wideLensDoesNotReachDeepSkyTarget() {
        val s = ExposureCalculator.bestFor(
            GearCatalog.exampleKit, idealFocalFullFrameMm = 200.0, minFocalFullFrameMm = 50.0,
            bortle = 4, brightMoon = false,
        )!!
        assertFalse(s.reachesTarget)
        assertEquals(18.0, s.focalMm, 0.0)
    }

    @Test
    fun picksTelephotoWhenAvailable() {
        val tele = GearCatalog.lenses.first { it.id == "samyang-135-f2" }
        val kit = GearCatalog.exampleKit.addLens(tele)
        val s = ExposureCalculator.bestFor(kit, 200.0, 50.0, bortle = 4, brightMoon = false)!!
        assertEquals(tele, s.lens)
        assertTrue(s.reachesTarget)
    }

    @Test
    fun variableApertureInterpolates() {
        val kit = GearCatalog.lenses.first { it.id == "kit-18-55" }
        assertEquals(3.5, kit.maxApertureAt(18.0), 1e-9)
        assertEquals(5.6, kit.maxApertureAt(55.0), 1e-9)
        assertEquals("f/3.5–5.6", kit.apertureLabel)
        assertEquals("18–55mm", kit.focalLabel)
    }

    @Test
    fun emptyKitHasNoSuggestion() {
        assertEquals(null, ExposureCalculator.bestFor(GearKit(), 24.0, 12.0, 4, false))
    }
}
