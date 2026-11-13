package app.nightbrief.app.ui

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import app.nightbrief.app.AppViewModel
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.lang.reflect.Modifier

/**
 * `MainActivity` gets its ViewModel through `by viewModels()`, and the activity's
 * `SavedStateViewModelFactory` looks up exactly these two reflective signatures. A constructor
 * parameter with a default value looks harmless to direct test calls but removes the signature,
 * so the app dies on launch with `NoSuchMethodException`. Keep the factory contract covered.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class AppViewModelFactoryTest {
    @Test
    fun theActivityFactoryCanFindASavedStateConstructor() {
        val constructor = AppViewModel::class.java.getConstructor(
            Application::class.java,
            SavedStateHandle::class.java,
        )
        assertNotNull("the (Application, SavedStateHandle) constructor is gone", constructor)
        assertTrue(
            "the factory needs a public constructor, found ${Modifier.toString(constructor.modifiers)}",
            Modifier.isPublic(constructor.modifiers),
        )
    }
}
