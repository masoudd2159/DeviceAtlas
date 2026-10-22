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
            val navigation = rememberAppNavigationState()
            DeviceAtlasTheme(dynamicColor = false) {
                DeviceAtlasApp(SettingsUiState(loaded = true), AppLanguage.ENGLISH, {}, {}, {}, navigation)
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
}
