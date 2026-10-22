package io.github.masoudd2159.deviceatlas

import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.core.os.LocaleListCompat
import org.junit.After
import org.junit.Rule
import org.junit.Test

class HardwareScreenTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @After fun restoreLocale() {
        compose.runOnUiThread { AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList()) }
    }

    @Test fun overviewShowsActualModelRefreshesAndSurvivesRecreation() {
        compose.runOnUiThread { AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("en")) }
        compose.waitUntil(15_000) { compose.onAllNodesWithTag("hardware_MODEL").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText(Build.MODEL.trim()).performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("hardware_refresh").performScrollTo().performClick()
        compose.activityRule.scenario.recreate()
        compose.waitUntil(15_000) { compose.onAllNodesWithTag("hardware_MODEL").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText(Build.MODEL.trim()).performScrollTo().assertIsDisplayed()
    }

    @Test fun deviceLazySectionsSensorsAndDiagnosticsAreConnected() {
        compose.onNodeWithTag("nav_DEVICE").performClick()
        compose.waitUntil(15_000) { compose.onAllNodesWithTag("hardware_device_list").fetchSemanticsNodes().isNotEmpty() }
        compose.waitUntil(15_000) { compose.onAllNodesWithTag("hardware_MODEL").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText(Build.MODEL.trim()).assertIsDisplayed()
        compose.onNodeWithTag("hardware_device_list").performScrollToNode(hasTestTag("hardware_STORAGE"))
        compose.onNodeWithTag("hardware_STORAGE").assertIsDisplayed()
        compose.onNodeWithTag("nav_SENSORS").performClick()
        compose.onNodeWithTag("hardware_sensor_list").assertIsDisplayed()
        compose.onNodeWithTag("nav_DIAGNOSTICS").performClick()
        compose.onNodeWithTag("hardware_integrity").assertExists()
    }
}
