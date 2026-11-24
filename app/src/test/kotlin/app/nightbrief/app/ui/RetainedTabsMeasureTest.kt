package app.nightbrief.app.ui

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class RetainedTabsMeasureTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun hiddenTabsAreNotMeasuredOnRelayoutOrPushWhileStateStaysWarm() {
        val measures = mutableMapOf<String, Int>()
        var active by mutableStateOf<String?>(Routes.Tonight)
        var bottomPad by mutableStateOf(0.dp)
        var tonightSavedCounter = 0

        fun tab(route: String, label: String) = TabContent { bottom ->
            val persisted = remember { ++tonightSavedCounter; route }
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(bottom = bottom)
                    .layout { measurable, constraints ->
                        measures[route] = (measures[route] ?: 0) + 1
                        val placeable = measurable.measure(constraints)
                        layout(placeable.width, placeable.height) { placeable.place(0, 0) }
                    },
            ) {
                Text("$label-$persisted")
            }
        }

        val tonight = tab(Routes.Tonight, "Tonight")
        val week = tab(Routes.Week, "Week")
        val sites = tab(Routes.Sites, "Sites")
        val gear = tab(Routes.Gear, "Gear")

        compose.setContent {
            RetainedTabs(
                active = active,
                bottom = bottomPad,
                tonight = tonight,
                week = week,
                sites = sites,
                gear = gear,
            )
        }
        compose.waitForIdle()

        // Visit all 4 tabs so all 4 are retained in composition.
        for (route in listOf(Routes.Week, Routes.Sites, Routes.Gear, Routes.Tonight)) {
            compose.runOnIdle { active = route }
            compose.waitForIdle()
        }
        compose.onNodeWithText("Tonight-${Routes.Tonight}").assertIsDisplayed()
        assertEquals("all 4 tabs should have composed exactly once", 4, tonightSavedCounter)

        // Trigger a relayout with all 4 tabs retained and Tonight active.
        val beforeRelayout = measures.toMap()
        compose.runOnIdle { bottomPad = 12.dp }
        compose.waitForIdle()
        val relayoutDeltas = listOf(Routes.Tonight, Routes.Week, Routes.Sites, Routes.Gear).associateWith {
            (measures[it] ?: 0) - (beforeRelayout[it] ?: 0)
        }
        assertEquals(1, relayoutDeltas[Routes.Tonight])
        assertEquals(0, relayoutDeltas[Routes.Week])
        assertEquals(0, relayoutDeltas[Routes.Sites])
        assertEquals(0, relayoutDeltas[Routes.Gear])

        // Push a screen (active = null) and trigger relayout: 0 tabs should be measured.
        compose.runOnIdle { active = null }
        compose.waitForIdle()
        val beforePushRelayout = measures.toMap()
        compose.runOnIdle { bottomPad = 24.dp }
        compose.waitForIdle()
        val pushDeltas = listOf(Routes.Tonight, Routes.Week, Routes.Sites, Routes.Gear).sumOf {
            (measures[it] ?: 0) - (beforePushRelayout[it] ?: 0)
        }
        assertEquals("no retained tab should be measured while a pushed screen is active", 0, pushDeltas)

        // Return to Tonight: still warm (remembered state was not recreated).
        compose.runOnIdle { active = Routes.Tonight }
        compose.waitForIdle()
        compose.onNodeWithText("Tonight-${Routes.Tonight}").assertIsDisplayed()
        assertEquals("returning to a retained tab must not recreate its composition", 4, tonightSavedCounter)
    }
}
