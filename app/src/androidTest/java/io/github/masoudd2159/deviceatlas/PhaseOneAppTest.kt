package io.github.masoudd2159.deviceatlas

import android.view.View
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.LocaleList
import android.app.LocaleManager
import android.os.Build
import java.util.Locale
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.core.os.LocaleListCompat
import androidx.activity.BackEventCompat
import androidx.core.view.WindowCompat
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class PhaseOneAppTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Before fun useEnglish() {
        compose.runOnUiThread { AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("en")) }
        waitForPage("OVERVIEW")
    }

    @After fun restorePreferences() {
        compose.runOnUiThread { AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList()) }
        compose.activity.getSharedPreferences("appearance", 0).edit().clear().commit()
    }

    @Test fun launchesOverviewAndNavigatesAllStructuralDestinations() {
        compose.onNodeWithText(text(R.string.overview_title)).assertIsDisplayed()
        listOf("DEVICE" to R.string.device_description, "SENSORS" to R.string.sensors_description,
            "DIAGNOSTICS" to R.string.diagnostics_description).forEach { (destination, description) ->
            compose.onNodeWithTag("nav_$destination").performClick()
            waitForPage(destination)
            compose.onNodeWithText(text(description)).performScrollTo().assertIsDisplayed()
        }
        compose.onNodeWithTag("nav_SETTINGS").performClick()
        waitForPage("SETTINGS")
        compose.onNodeWithText(text(R.string.preferences)).assertIsDisplayed()
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        waitForPage("OVERVIEW")
    }

    @Test fun nestedBackRepeatedTapsThemeAndRecreation() {
        settingsChild(R.string.appearance, "APPEARANCE")
        compose.onNodeWithText(text(R.string.dark)).performClick()
        compose.waitUntil(10_000) {
            compose.activity.getSharedPreferences("appearance", 0).getString("theme", null) == "dark"
        }
        compose.onNodeWithText(text(R.string.dark)).assertIsSelected()
        compose.activityRule.scenario.recreate()
        waitForPage("APPEARANCE")
        compose.onNodeWithText(text(R.string.dark)).assertIsSelected()
        compose.onNodeWithTag("nav_DEVICE").performClick()
        repeat(3) { compose.onNodeWithTag("nav_DEVICE").performClick() }
        compose.onNodeWithTag("nav_SETTINGS").performClick()
        waitForPage("APPEARANCE")
        compose.onNodeWithContentDescription(text(R.string.back)).performClick()
        waitForPage("SETTINGS")
        compose.onNodeWithText(text(R.string.about)).performClick()
        waitForPage("ABOUT")
        compose.onNodeWithText(BuildConfig.VERSION_NAME).assertIsDisplayed()
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        waitForPage("SETTINGS")
    }

    @Test fun runtimePersianAndEnglishSwitchingPersistsAndChangesDirection() {
        settingsChild(R.string.language, "LANGUAGE")
        compose.onNodeWithText(text(R.string.persian)).performClick()
        waitForLocale("fa")
        waitForPage("LANGUAGE")
        assertEquals(View.LAYOUT_DIRECTION_RTL, compose.activity.resources.configuration.layoutDirection)
        if (Build.VERSION.SDK_INT >= 33) {
            assertEquals("fa", compose.activity.getSystemService(LocaleManager::class.java).applicationLocales[0].language)
        }
        compose.onNodeWithText("فارسی").assertIsSelected()
        assertTrue(compose.onNodeWithTag("nav_OVERVIEW").fetchSemanticsNode().boundsInRoot.center.x >
            compose.onNodeWithTag("nav_SETTINGS").fetchSemanticsNode().boundsInRoot.center.x)
        compose.activityRule.scenario.recreate()
        waitForPage("LANGUAGE")
        assertEquals("fa", compose.activity.resources.configuration.locales[0].language)
        compose.onNodeWithText(text(R.string.english)).performClick()
        waitForLocale("en")
        waitForPage("LANGUAGE")
        assertEquals(View.LAYOUT_DIRECTION_LTR, compose.activity.resources.configuration.layoutDirection)
        compose.onNodeWithText("English").assertIsSelected()
        compose.onNodeWithText(text(R.string.follow_system)).performClick()
        compose.waitUntil(10_000) { AppCompatDelegate.getApplicationLocales().isEmpty }
        waitForPage("LANGUAGE")
    }

    @Test fun themeChoicesAndHeadingsExposeAccessibleState() {
        settingsChild(R.string.appearance, "APPEARANCE")
        listOf(R.string.light, R.string.dark, R.string.follow_system).forEach { label ->
            compose.onNodeWithText(text(label)).performClick()
            compose.waitForIdle()
            compose.onNodeWithText(text(label)).assertIsSelected()
            compose.onNodeWithText(text(label)).assertHasClickAction()
        }
        compose.onNodeWithText(text(R.string.theme)).assert(
            SemanticsMatcher.keyIsDefined(androidx.compose.ui.semantics.SemanticsProperties.Heading))
    }

    @Test fun rotationPreservesSelectedDestinationAndNestedStack() {
        settingsChild(R.string.about, "ABOUT")
        val original = compose.activity.requestedOrientation
        try {
            compose.runOnUiThread { compose.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
            compose.waitUntil(15_000) {
                compose.activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
            }
            waitForPage("ABOUT")
            compose.onNodeWithContentDescription(text(R.string.back)).performClick()
            waitForPage("SETTINGS")
        } finally {
            compose.runOnUiThread { compose.activity.requestedOrientation = original }
        }
    }

    @Test fun syntheticPredictiveBackCancellationKeepsNestedStackThenCompletionPops() {
        settingsChild(R.string.language, "LANGUAGE")
        val back = compose.activity.onBackPressedDispatcher
        compose.runOnUiThread {
            back.dispatchOnBackStarted(BackEventCompat(0f, 300f, 0f, BackEventCompat.EDGE_LEFT))
            back.dispatchOnBackProgressed(BackEventCompat(80f, 300f, 0.4f, BackEventCompat.EDGE_LEFT))
        }
        compose.waitForIdle()
        compose.runOnUiThread { back.dispatchOnBackCancelled() }
        waitForPage("LANGUAGE")
        compose.onNodeWithText(text(R.string.english)).assertIsDisplayed()
        compose.runOnUiThread {
            back.dispatchOnBackStarted(BackEventCompat(0f, 300f, 0f, BackEventCompat.EDGE_LEFT))
            back.dispatchOnBackProgressed(BackEventCompat(120f, 300f, 0.7f, BackEventCompat.EDGE_LEFT))
        }
        compose.waitForIdle()
        compose.runOnUiThread { back.onBackPressed() }
        waitForPage("SETTINGS")
        compose.runOnUiThread { back.onBackPressed() }
        waitForPage("OVERVIEW")
    }

    @Test fun systemBarIconContrastFollowsSelectedThemeAndSystemMode() {
        settingsChild(R.string.appearance, "APPEARANCE")
        val systemIsLight = compose.activity.resources.configuration.uiMode and
            Configuration.UI_MODE_NIGHT_MASK != Configuration.UI_MODE_NIGHT_YES
        listOf(R.string.dark to false, R.string.light to true, R.string.follow_system to systemIsLight).forEach { (label, lightIconsBackground) ->
            compose.onNodeWithText(text(label)).performClick()
            compose.waitUntil(10_000) {
                val controller = WindowCompat.getInsetsController(compose.activity.window, compose.activity.window.decorView)
                controller.isAppearanceLightStatusBars == lightIconsBackground &&
                    controller.isAppearanceLightNavigationBars == lightIconsBackground
            }
        }
        compose.activityRule.scenario.recreate()
        waitForPage("APPEARANCE")
        val controller = WindowCompat.getInsetsController(compose.activity.window, compose.activity.window.decorView)
        assertEquals(systemIsLight, controller.isAppearanceLightStatusBars)
        assertEquals(systemIsLight, controller.isAppearanceLightNavigationBars)
    }

    @Test fun resourceLocalesResolvePersianAndUnsupportedLanguagesFallBackToEnglish() {
        fun localized(tag: String): android.content.Context {
            val configuration = Configuration(compose.activity.resources.configuration)
            configuration.setLocales(LocaleList(Locale.forLanguageTag(tag)))
            return compose.activity.createConfigurationContext(configuration)
        }
        assertEquals("تنظیمات", localized("fa-IR").getString(R.string.settings))
        assertEquals(localized("en").getString(R.string.overview_description),
            localized("de-DE").getString(R.string.overview_description))
    }

    private fun settingsChild(label: Int, page: String) {
        compose.onNodeWithTag("nav_SETTINGS").performClick()
        waitForPage("SETTINGS")
        compose.onNodeWithText(text(label)).performClick()
        waitForPage(page)
    }

    private fun text(resource: Int) = compose.activity.getString(resource)
    private fun waitForLocale(language: String) {
        compose.waitUntil(15_000) { compose.activity.resources.configuration.locales[0].language == language }
        compose.waitForIdle()
    }
    private fun waitForPage(page: String) {
        compose.waitUntil(15_000) {
            compose.onAllNodesWithTag("page_$page").fetchSemanticsNodes().isNotEmpty()
        }
        compose.waitForIdle()
    }
}
