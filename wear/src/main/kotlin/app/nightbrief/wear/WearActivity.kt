package app.nightbrief.wear

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.nightbrief.score.WearGlance
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class WearActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            var glance by remember { mutableStateOf<WearGlance?>(null) }
            LaunchedEffect(Unit) {
                glance = withContext(Dispatchers.IO) { WearGlanceLoader.load(this@WearActivity) }
            }
            val current = glance
            if (current == null) {
                Box(Modifier.fillMaxSize().background(Color(0xFF12141C))) {
                    Column(
                        Modifier.align(Alignment.Center).padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        CircularProgressIndicator(color = Color(0xFFB7BDD0), strokeWidth = 3.dp)
                        Text(
                            stringResource(R.string.wear_loading),
                            color = Color(0xFFB7BDD0),
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            } else {
                WearGlanceScreen(current)
            }
        }
    }
}
