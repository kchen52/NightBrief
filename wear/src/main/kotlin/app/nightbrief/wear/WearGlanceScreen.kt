package app.nightbrief.wear

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.nightbrief.score.Verdict
import app.nightbrief.score.WearGlance

private val Background = Color(0xFF12141C)
private val TextPrimary = Color(0xFFF4F6FB)
private val TextMuted = Color(0xFFB7BDD0)

@Composable
fun WearGlanceScreen(glance: WearGlance, modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxSize()
            .background(Background)
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (!glance.ready) {
            Text(
                glance.verdictText,
                color = TextPrimary,
                fontSize = 16.sp,
                textAlign = TextAlign.Center,
            )
        } else {
            Text(glance.siteName, color = TextMuted, fontSize = 14.sp, textAlign = TextAlign.Center)
            Text(
                glance.scoreText,
                color = TextPrimary,
                fontSize = 48.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Text(
                glance.verdictText,
                color = verdictColor(glance.verdictText),
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
            glance.savedText?.let { saved ->
                Text(saved, color = TextMuted, fontSize = 12.sp, textAlign = TextAlign.Center)
            }
        }
    }
}

private fun verdictColor(verdict: String): Color = when (verdict) {
    Verdict.GO.label -> Color(0xFF4ADE80)
    Verdict.MAYBE.label -> Color(0xFFFBBF24)
    Verdict.NO_GO.label -> Color(0xFFF87171)
    else -> TextMuted
}
