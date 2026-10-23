package io.github.masoudd2159.deviceatlas

import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.core.os.LocaleListCompat
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class InteractiveNavigationTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Before fun english() {
        compose.runOnUiThread { AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("en")) }
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("page_OVERVIEW").fetchSemanticsNodes().isNotEmpty() }
    }
    @After fun restoreLocale() {
        compose.runOnUiThread { AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList()) }
    }
    @Test fun actualActivityRecreationAndNavigationStopWithoutResuming() {
        compose.onNodeWithTag("nav_DIAGNOSTICS").performClick()
        compose.onNodeWithTag("diagnostics_interactive").performScrollTo().performClick()
        compose.onNodeWithTag("display_surface").assertDoesNotExist()
        compose.onNodeWithTag("interactive_start").performScrollTo().performClick()
        compose.onNodeWithTag("display_surface").performScrollTo().assertIsDisplayed()
        compose.activityRule.scenario.recreate()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("interactive_start").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("display_surface").assertDoesNotExist()
        compose.onNodeWithTag("interactive_start").performScrollTo().assertIsEnabled().performClick()
        compose.onNodeWithTag("display_surface").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("nav_SETTINGS").performClick()
        compose.onNodeWithTag("nav_DIAGNOSTICS").performClick()
        compose.onNodeWithTag("display_surface").assertDoesNotExist()
        compose.onNodeWithTag("interactive_start").performScrollTo().assertIsEnabled()
    }
}
