package io.github.masoudd2159.deviceatlas

import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.core.os.LocaleListCompat
import io.github.masoudd2159.deviceatlas.data.hardware.platformText
import io.github.masoudd2159.deviceatlas.foundation.hardware.HardwareDatum
import io.github.masoudd2159.deviceatlas.foundation.hardware.HardwareValue
import io.github.masoudd2159.deviceatlas.presentation.hardware.reasonLabel
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class HardwareScreenTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private var originalLocales = LocaleListCompat.getEmptyLocaleList()
    private var originalTheme: String? = null

    @Before fun isolatePreferences() {
        compose.runOnUiThread {
            originalLocales = AppCompatDelegate.getApplicationLocales()
            originalTheme = compose.activity.getSharedPreferences("appearance", 0).getString("theme", null)
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("en"))
        }
        compose.waitUntil(15_000) {
            compose.activity.resources.configuration.locales[0].language == "en" &&
                compose.onAllNodesWithTag("nav_SETTINGS").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("nav_SETTINGS").performClick()
        compose.onNodeWithText(compose.activity.getString(R.string.appearance)).performClick()
        compose.onNodeWithText(compose.activity.getString(R.string.light)).performClick()
        compose.waitUntil(10_000) { compose.activity.getSharedPreferences("appearance", 0).getString("theme", null) == "light" }
        compose.onNodeWithTag("nav_OVERVIEW").performClick()
    }

    @After fun restorePreferences() {
        compose.runOnUiThread {
            val editor = compose.activity.getSharedPreferences("appearance", 0).edit()
            originalTheme?.let { editor.putString("theme", it) } ?: editor.remove("theme")
            editor.commit()
            AppCompatDelegate.setApplicationLocales(originalLocales)
        }
    }

    private fun assertPlatformModel(scroll: Boolean = false) {
        val expected = when (val value = platformText(Build.MODEL)) {
            is HardwareValue.Available -> (value.value as HardwareDatum.Text).text
            is HardwareValue.Unavailable -> compose.activity.getString(reasonLabel(value.reason))
        }
        val model = compose.onNode(hasText(expected) and hasAnyAncestor(hasTestTag("hardware_MODEL")))
        if (scroll) model.performScrollTo()
        model.assertIsDisplayed()
    }

    @Test fun overviewShowsActualModelRefreshesAndSurvivesRecreation() {
        compose.waitUntil(15_000) { compose.onAllNodesWithTag("hardware_MODEL").fetchSemanticsNodes().isNotEmpty() }
        assertPlatformModel(scroll = true)
        compose.onNodeWithTag("hardware_refresh").performScrollTo().performClick()
        compose.activityRule.scenario.recreate()
        compose.waitUntil(15_000) { compose.onAllNodesWithTag("hardware_MODEL").fetchSemanticsNodes().isNotEmpty() }
        assertPlatformModel(scroll = true)
    }

    @Test fun deviceLazySectionsSensorsAndDiagnosticsAreConnected() {
        compose.onNodeWithTag("nav_DEVICE").performClick()
        compose.waitUntil(15_000) { compose.onAllNodesWithTag("hardware_device_list").fetchSemanticsNodes().isNotEmpty() }
        compose.waitUntil(15_000) { compose.onAllNodesWithTag("hardware_MODEL").fetchSemanticsNodes().isNotEmpty() }
        assertPlatformModel()
        compose.onNodeWithTag("hardware_device_list").performScrollToNode(hasTestTag("hardware_STORAGE"))
        compose.onNodeWithTag("hardware_STORAGE").assertIsDisplayed()
        compose.onNodeWithTag("nav_SENSORS").performClick()
        compose.onNodeWithTag("hardware_sensor_list").assertIsDisplayed()
        compose.onNodeWithTag("nav_DIAGNOSTICS").performClick()
        compose.onNodeWithTag("hardware_integrity").assertExists()
    }
}
