package app.nightbrief.app.ui.common

import androidx.compose.ui.text.font.FontWeight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class EmphasizeTest {
    @Test
    fun boldsTimesTemperaturesAndUnitsAndLeavesWordsIntact() {
        val raw = "Dress for −4 °C at 8:24 PM. 7Timer stays whole. 4h 43m, 17°, and 56%."
        val text = emphasize(raw)
        assertEquals(raw, text.text)
        val bold = text.spanStyles
            .filter { it.item.fontWeight == FontWeight.Bold }
            .map { text.text.substring(it.start, it.end) }
        assertEquals(listOf("−4 °C", "8:24 PM", "4h", "43m", "17°", "56%"), bold)
        assertFalse(bold.any { it.contains("7") && it.contains("Timer") })
    }

    @Test
    fun boldsBareNumbersWithoutSplittingAWord() {
        val text = emphasize("ISO 1600 · f/2.8 · scale 1 best")
        val bold = text.spanStyles.map { text.text.substring(it.start, it.end) }
        assertEquals(listOf("1600", "2.8", "1"), bold)
    }
}
