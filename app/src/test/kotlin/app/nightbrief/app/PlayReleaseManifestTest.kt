package app.nightbrief.app

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Google Play rejects a new app that targets below API 36, declares `SCHEDULE_EXACT_ALARM`,
 * or ships a foreground-service permission without a declaration. The digest uses neither.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class PlayReleaseManifestTest {

    @Test
    fun phoneTargetsApi36WithoutExactAlarmOrForegroundService() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        @Suppress("DEPRECATION")
        val info = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
        val names = info.requestedPermissions?.toSet().orEmpty()

        assertEquals(36, context.applicationInfo.targetSdkVersion)
        @Suppress("DEPRECATION")
        assertEquals(2, info.versionCode)
        assertFalse(names.contains(Manifest.permission.SCHEDULE_EXACT_ALARM))
        assertFalse(names.contains(Manifest.permission.FOREGROUND_SERVICE))
        assertFalse(names.contains(Manifest.permission.FOREGROUND_SERVICE_DATA_SYNC))
        assertTrue(names.contains(Manifest.permission.POST_NOTIFICATIONS))
        assertTrue(names.contains(Manifest.permission.RECEIVE_BOOT_COMPLETED))
        assertTrue(names.contains(Manifest.permission.INTERNET))
    }
}
