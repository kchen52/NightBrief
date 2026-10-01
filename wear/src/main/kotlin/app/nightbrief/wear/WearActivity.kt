package app.nightbrief.wear

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import app.nightbrief.score.WearGlance
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class WearActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            var glance by remember { mutableStateOf<WearGlance?>(null) }
            androidx.compose.runtime.LaunchedEffect(Unit) {
                glance = withContext(Dispatchers.IO) { WearGlanceLoader.load(this@WearActivity) }
            }
            val current = glance
            if (current == null) {
                Box(Modifier.fillMaxSize().background(Color(0xFF12141C)))
            } else {
                WearGlanceScreen(current)
            }
        }
    }
}
