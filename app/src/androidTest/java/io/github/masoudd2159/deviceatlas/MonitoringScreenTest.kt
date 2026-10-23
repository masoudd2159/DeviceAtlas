package io.github.masoudd2159.deviceatlas

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import org.junit.Rule
import org.junit.Test

class MonitoringScreenTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun realDiagnosticsControlsNavigationBackgroundAndRecreation() {
        compose.waitUntil(15_000) { compose.onAllNodesWithTag("nav_DIAGNOSTICS").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("nav_DIAGNOSTICS").performClick()
        compose.onNodeWithTag("monitor_status").assertTextEquals(compose.activity.getString(R.string.monitor_idle))
        repeat(3) {
            compose.onNodeWithTag("monitor_start").performClick()
            compose.onNodeWithTag("monitor_stop").performClick()
            compose.onNodeWithTag("monitor_status").assertTextEquals(compose.activity.getString(R.string.monitor_stopped))
            compose.onNodeWithTag("monitor_start").assertIsEnabled()
        }
        compose.onNodeWithTag("monitor_start").performClick()
        compose.waitUntil(15_000) { compose.onAllNodesWithText(compose.activity.getString(R.string.monitor_current)).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("monitor_stop").performClick()
        compose.onNodeWithTag("monitor_status").assertTextEquals(compose.activity.getString(R.string.monitor_stopped))
        compose.onNodeWithTag("monitor_reset").performClick()
        compose.onNodeWithTag("monitor_status").assertTextEquals(compose.activity.getString(R.string.monitor_cleared))
        compose.onNodeWithTag("monitor_start").performClick()
        compose.onNodeWithTag("nav_SETTINGS").performClick()
        compose.onNodeWithTag("nav_DIAGNOSTICS").performClick()
        compose.onNodeWithTag("monitor_status").assertTextEquals(compose.activity.getString(R.string.monitor_hidden))
        compose.onNodeWithTag("monitor_start").performScrollTo().performClick()
        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        compose.onNodeWithTag("monitor_status").assertTextEquals(compose.activity.getString(R.string.monitor_background))
        compose.onNodeWithTag("monitor_start").performScrollTo().performClick()
        compose.activityRule.scenario.recreate()
        compose.waitUntil(15_000) { compose.onAllNodesWithTag("monitor_status").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("monitor_status").assertTextEquals(compose.activity.getString(R.string.monitor_background))
        compose.onNodeWithTag("monitor_start").assertIsEnabled()
        compose.onNodeWithTag("monitor_stop").assertIsNotEnabled()
    }
}
