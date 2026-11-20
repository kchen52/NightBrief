package app.nightbrief.app.update

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class ApkInstallerTest {
    @Test
    fun installRejectsAndDeletesUnverifiableApk() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val fakeApk = File(context.cacheDir, "fake-update.apk").apply {
            writeText("not a real apk")
        }
        assertFalse(ApkInstaller.verifyApk(context, fakeApk))
        assertFalse(ApkInstaller.install(context, fakeApk))
        assertFalse("rejected apk should be deleted", fakeApk.exists())
    }
}
