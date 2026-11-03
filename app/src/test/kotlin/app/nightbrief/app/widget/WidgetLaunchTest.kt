package app.nightbrief.app.widget

import android.app.Application
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import app.nightbrief.app.MainActivity
import app.nightbrief.app.shouldOpenTonight
import app.nightbrief.work.DigestNotifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class WidgetLaunchTest {
    @Test
    fun widgetTapOpensTonightEvenWhenTheTaskIsRestoringSites() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val intent = widgetOpenIntent(context)
        assertEquals(MainActivity::class.java.name, intent.component?.className)
        assertTrue(intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
        assertTrue(intent.flags and Intent.FLAG_ACTIVITY_CLEAR_TOP != 0)
        assertTrue(intent.getBooleanExtra(MainActivity.EXTRA_OPEN_TONIGHT, false))
        assertTrue(shouldOpenTonight(intent, restoringState = true))
    }

    @Test
    fun aPlainResumeDoesNotJumpToTonight() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val intent = Intent(context, MainActivity::class.java)
        assertFalse(shouldOpenTonight(intent, restoringState = true))
        assertFalse(shouldOpenTonight(intent, restoringState = false))
    }

    @Test
    fun aDigestOpensTonightOnAFreshStartOnly() {
        val intent = Intent().putExtra(DigestNotifier.EXTRA_SITE_ID, "home")
        assertTrue(shouldOpenTonight(intent, restoringState = false))
        assertFalse(shouldOpenTonight(intent, restoringState = true))
    }
}
