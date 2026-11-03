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
        applyTonightIntent(intent, restoringState = savedInstanceState != null)
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
        applyTonightIntent(intent, restoringState = false)
    }

    private fun applyTonightIntent(intent: Intent, restoringState: Boolean) {
        if (!shouldOpenTonight(intent, restoringState)) return
        val siteId = intent.getStringExtra(DigestNotifier.EXTRA_SITE_ID)
        if (siteId != null) vm.selectSite(siteId) else vm.selectSite(null)
        openTonight.intValue++
    }

    companion object {
        /** Widget tap. Opens Tonight on the primary site, including when the task is already showing another tab. */
        const val EXTRA_OPEN_TONIGHT = "app.nightbrief.extra.OPEN_TONIGHT"
    }
}

/**
 * True when this launch should show Tonight.
 * A widget tap does, even if the activity is restoring the Sites back stack.
 * A digest does on a fresh start; restoring an existing task keeps the screen the user left.
 */
internal fun shouldOpenTonight(intent: Intent, restoringState: Boolean): Boolean {
    if (intent.getBooleanExtra(MainActivity.EXTRA_OPEN_TONIGHT, false)) return true
    return intent.getStringExtra(DigestNotifier.EXTRA_SITE_ID) != null && !restoringState
}
