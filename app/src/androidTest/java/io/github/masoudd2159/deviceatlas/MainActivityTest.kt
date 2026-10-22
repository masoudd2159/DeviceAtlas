package io.github.masoudd2159.deviceatlas

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import org.junit.Rule
import org.junit.Test

class MainActivityTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun launchesWithHiltAndCompose() {
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodesWithText(compose.activity.getString(R.string.app_name)).fetchSemanticsNodes().isNotEmpty()
        }
        // The preserved integration check is now reached through Diagnostics.
        compose.onNodeWithTag("nav_DIAGNOSTICS").performClick()
        val expected = compose.activity.getString(R.string.native_ready, 4.0)
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodesWithText(expected).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText(expected).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(compose.activity.getString(R.string.app_name)).performScrollTo().assertIsDisplayed()
    }
}
