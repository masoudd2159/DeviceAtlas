package io.github.masoudd2159.deviceatlas

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import org.junit.Rule
import org.junit.Test

class BenchmarkScreenTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    @Test fun diagnosticsPanelNavigationAndRecreationNeverAutoStart() {
        compose.waitUntil(15_000) { compose.onAllNodesWithTag("nav_DIAGNOSTICS").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("nav_DIAGNOSTICS").performClick()
        compose.onNodeWithTag("diagnostics_benchmark").performScrollTo().performClick()
        compose.onNodeWithTag("bench_status").assertTextEquals(compose.activity.getString(R.string.bench_idle))
        compose.onNodeWithTag("bench_start").performScrollTo().assertIsEnabled()
        compose.onNodeWithTag("bench_stop").assertIsNotEnabled()
        compose.activityRule.scenario.recreate()
        compose.waitUntil(15_000) { compose.onAllNodesWithTag("bench_status").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("bench_status").assertTextEquals(compose.activity.getString(R.string.bench_idle))
        compose.onNodeWithTag("nav_SETTINGS").performClick()
        compose.onNodeWithTag("nav_DIAGNOSTICS").performClick()
        compose.onNodeWithTag("bench_status").assertTextEquals(compose.activity.getString(R.string.bench_idle))
        compose.onNodeWithTag("diagnostics_monitor").performScrollTo().performClick()
        compose.onNodeWithTag("monitor_start").performScrollTo().assertIsEnabled()
    }
}
