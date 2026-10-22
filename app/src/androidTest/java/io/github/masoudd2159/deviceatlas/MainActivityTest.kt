package io.github.masoudd2159.deviceatlas

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test

class MainActivityTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun launchesWithHiltAndCompose() {
        val expected = compose.activity.getString(R.string.native_ready, 4.0)
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodesWithText(expected).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText(compose.activity.getString(R.string.app_name)).assertIsDisplayed()
        compose.onNodeWithText(expected).assertIsDisplayed()
    }
}
