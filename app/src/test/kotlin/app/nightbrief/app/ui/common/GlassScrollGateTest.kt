package app.nightbrief.app.ui.common

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GlassScrollGateTest {
    @Test
    fun staysUpUntilEveryReporterStops() {
        val gate = GlassScrollGate()
        val page = Any()
        val hours = Any()
        assertFalse(gate.moving)
        gate.report(page, true)
        gate.report(hours, true)
        assertTrue(gate.moving)
        gate.report(page, false)
        assertTrue("hour row still moving", gate.moving)
        gate.report(hours, false)
        assertFalse(gate.moving)
    }

    @Test
    fun firstRouteDoesNotSettleAndASwitchDoes() {
        val gate = GlassScrollGate()
        gate.holdFor("tonight")
        assertFalse(gate.settling)
        gate.holdFor("week")
        assertTrue(gate.settling)
        gate.releaseFlat()
        assertFalse(gate.settling)
        gate.holdFor("week")
        assertFalse("same tab again", gate.settling)
    }

    @Test
    fun returningFromAPushedScreenSettles() {
        val gate = GlassScrollGate()
        gate.holdFor("tonight")
        gate.holdFor(null)
        gate.holdFor("tonight")
        assertTrue(gate.settling)
    }

    @Test
    fun aStoppedReporterDoesNotClearAnother() {
        val gate = GlassScrollGate()
        val page = Any()
        val hours = Any()
        gate.report(page, true)
        gate.report(hours, false)
        assertTrue(gate.moving)
        gate.report(page, true)
        assertTrue(gate.moving)
    }
}
