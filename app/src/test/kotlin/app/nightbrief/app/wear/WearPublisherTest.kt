package app.nightbrief.app.wear

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class WearPublisherTest {
    @Test
    fun pushDoesNotThrowWhenPlayServicesAreMissing() = runBlocking {
        WearPublisher.push(ApplicationProvider.getApplicationContext())
    }
}
