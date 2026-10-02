package app.nightbrief.app

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.nightbrief.app.ui.NightBriefNavHost
import app.nightbrief.app.ui.theme.NightBriefTheme
import app.nightbrief.work.DigestNotifier

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()
    private val openTonight = mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        intent.getStringExtra(DigestNotifier.EXTRA_SITE_ID)?.let { id ->
            vm.selectSite(id)
            if (savedInstanceState == null) openTonight.intValue++
        }
        setContent {
            val state by vm.state.collectAsStateWithLifecycle()
            NightBriefTheme(nightVision = state?.nightVision == true) {
                NightBriefNavHost(vm, openTonightSignal = openTonight.intValue)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val id = intent.getStringExtra(DigestNotifier.EXTRA_SITE_ID) ?: return
        vm.selectSite(id)
        openTonight.intValue++
    }
}
