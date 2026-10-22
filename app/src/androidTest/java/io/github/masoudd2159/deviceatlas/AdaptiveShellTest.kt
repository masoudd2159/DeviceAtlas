package io.github.masoudd2159.deviceatlas

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.unit.dp
import io.github.masoudd2159.deviceatlas.foundation.AppLanguage
import io.github.masoudd2159.deviceatlas.presentation.navigation.Destination
import io.github.masoudd2159.deviceatlas.presentation.navigation.rememberAppNavigationState
import io.github.masoudd2159.deviceatlas.presentation.settings.SettingsUiState
import io.github.masoudd2159.deviceatlas.presentation.shell.DeviceAtlasApp
import io.github.masoudd2159.deviceatlas.presentation.theme.DeviceAtlasTheme
import io.github.masoudd2159.deviceatlas.presentation.navigation.AppNavigationState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.luminance
import io.github.masoudd2159.deviceatlas.presentation.theme.FoundationLightColors
import io.github.masoudd2159.deviceatlas.presentation.theme.FoundationDarkColors
import android.os.Build
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class AdaptiveShellTest {
    @get:Rule val compose = createComposeRule()

    @Test fun compactBarBecomesRailAndKeepsSelectedDestination() {
        val expanded = mutableStateOf(false)
        compose.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(
                if (expanded.value) DpSize(1000.dp, 900.dp) else DpSize(390.dp, 800.dp))) {
                DeviceAtlasTheme(dynamicColor = false) {
                    DeviceAtlasApp(SettingsUiState(loaded = true), AppLanguage.ENGLISH, {}, {}, {})
                }
            }
        }
        val overview = compose.onNodeWithTag("nav_OVERVIEW").fetchSemanticsNode().boundsInRoot
        val settings = compose.onNodeWithTag("nav_SETTINGS").fetchSemanticsNode().boundsInRoot
        assertEquals(overview.center.y, settings.center.y, 1f)
        compose.onNodeWithTag("nav_SETTINGS").performClick()
        compose.runOnIdle { expanded.value = true }
        compose.onNodeWithTag("nav_SETTINGS").assertIsSelected()
        val railOverview = compose.onNodeWithTag("nav_OVERVIEW").fetchSemanticsNode().boundsInRoot
        val railSettings = compose.onNodeWithTag("nav_SETTINGS").fetchSemanticsNode().boundsInRoot
        assertEquals(railOverview.center.x, railSettings.center.x, 1f)
        assertTrue(railSettings.center.y > railOverview.center.y)
        compose.onNodeWithTag("page_SETTINGS").assertExists()
    }

    @Test fun savesTypedNestedStackAndSelectionAcrossSavedStateRestoration() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.Locales(LocaleList("en"))) {
                val navigation = rememberAppNavigationState()
                DeviceAtlasTheme(dynamicColor = false) {
                    DeviceAtlasApp(SettingsUiState(loaded = true), AppLanguage.ENGLISH, {}, {}, {}, navigation)
                }
            }
        }
        compose.onNodeWithTag("nav_SETTINGS").performClick()
        compose.onNodeWithText("Language").performClick()
        compose.onNodeWithTag("page_LANGUAGE").assertExists()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("page_LANGUAGE").assertExists()
        compose.onNodeWithTag("nav_SETTINGS").assertIsSelected()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithTag("page_SETTINGS").assertExists()
    }

    @Test fun largeFontRtlSettingsRemainScrollableAndActionable() {
        compose.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.FontScale(2f)) {
                DeviceConfigurationOverride(DeviceConfigurationOverride.Locales(LocaleList("fa"))) {
                    DeviceAtlasTheme(darkTheme = true, dynamicColor = false) {
                        DeviceAtlasApp(SettingsUiState(loaded = true), AppLanguage.PERSIAN, {}, {}, {})
                    }
                }
            }
        }
        compose.onNodeWithTag("nav_SETTINGS").performClick()
        compose.onNodeWithText("دربارهٔ برنامه").performScrollTo().assertIsDisplayed().performClick()
        compose.onNodeWithText("نسخهٔ برنامه").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("بازگشت").assertHasClickAction()
    }

    @Test fun entryViewModelSurvivesInactiveTabAndClearsWhenPopped() {
        lateinit var navigation: AppNavigationState
        val models = mutableMapOf<Destination, EntryModel>()
        compose.setContent {
            navigation = rememberAppNavigationState()
            NavDisplay(entries = navigation.decoratedEntries(entryProvider {
                entry<Destination> { destination ->
                    val model = viewModel { EntryModel().also { models[destination] = it } }
                    Text(destination.name + model.cleared)
                }
            }), onBack = navigation::back)
        }
        compose.runOnIdle { navigation.navigate(Destination.ABOUT) }
        compose.waitForIdle()
        val about = models.getValue(Destination.ABOUT)
        compose.runOnIdle { navigation.navigate(Destination.DEVICE) }
        compose.waitForIdle()
        assertFalse(about.cleared)
        compose.runOnIdle { navigation.navigate(Destination.SETTINGS) }
        compose.waitForIdle()
        assertSame(about, models.getValue(Destination.ABOUT))
        compose.runOnIdle { navigation.back() }
        compose.waitForIdle()
        assertTrue(about.cleared)
        compose.runOnIdle { navigation.navigate(Destination.ABOUT) }
        compose.waitForIdle()
        assertNotSame(about, models.getValue(Destination.ABOUT))
    }

    @Test fun dynamicThemeUsesPlatformPaletteOrFallbackAndReadableContent() {
        val dark = mutableStateOf(false)
        compose.setContent {
            DeviceAtlasTheme(darkTheme = dark.value, dynamicColor = true) {
                val context = LocalContext.current
                val expected = if (Build.VERSION.SDK_INT >= 31) {
                    if (dark.value) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
                } else if (dark.value) FoundationDarkColors else FoundationLightColors
                val actual = MaterialTheme.colorScheme
                assertEquals(expected.primary, actual.primary)
                assertEquals(expected.surface, actual.surface)
                val a = actual.surface.luminance()
                val b = actual.onSurface.luminance()
                assertTrue((maxOf(a,b) + 0.05f) / (minOf(a,b) + 0.05f) >= 4.5f)
                Text("Theme palette")
            }
        }
        compose.runOnIdle { dark.value = true }
        compose.waitForIdle()
    }

    private class EntryModel : ViewModel() {
        var cleared = false
            private set
        override fun onCleared() { cleared = true }
    }
}
