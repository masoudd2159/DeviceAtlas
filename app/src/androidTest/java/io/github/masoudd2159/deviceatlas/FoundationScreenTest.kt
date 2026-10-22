package io.github.masoudd2159.deviceatlas

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.platform.app.InstrumentationRegistry
import io.github.masoudd2159.deviceatlas.presentation.home.HomeScreen
import io.github.masoudd2159.deviceatlas.presentation.home.HomeUiState
import io.github.masoudd2159.deviceatlas.presentation.theme.DeviceAtlasTheme
import org.junit.Rule
import org.junit.Test

class FoundationScreenTest {
    @get:Rule val compose = createComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun rendersFoundationWithLightFallback() = assertFoundation(dark = false)
    @Test fun rendersFoundationWithDarkFallback() = assertFoundation(dark = true)

    private fun assertFoundation(dark: Boolean) {
        compose.setContent {
            DeviceAtlasTheme(darkTheme = dark, dynamicColor = false) {
                HomeScreen(HomeUiState.Loading)
            }
        }
        compose.onNodeWithText(context.getString(R.string.app_name)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.foundation_description)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.native_loading)).assertIsDisplayed()
    }
}
