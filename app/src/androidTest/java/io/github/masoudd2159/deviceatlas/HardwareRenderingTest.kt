package io.github.masoudd2159.deviceatlas

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.text.intl.LocaleList
import io.github.masoudd2159.deviceatlas.foundation.hardware.*
import io.github.masoudd2159.deviceatlas.foundation.hardware.HardwareValue.*
import io.github.masoudd2159.deviceatlas.presentation.hardware.*
import io.github.masoudd2159.deviceatlas.presentation.theme.DeviceAtlasTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Deterministic UI fixtures only; production collectors never use these values. */
class HardwareRenderingTest {
    @get:Rule val compose = createComposeRule()
    private val snapshot = HardwareSnapshot(
        mapOf(HardwareField.MODEL to Available(HardwareDatum.Text("A very long model name with Latin text 123")),
            HardwareField.BATTERY_LEVEL to Unavailable(UnavailableReason.NOT_REPORTED)),
        Available(emptyList()), 123,
    )

    @Test fun persianLargeFontHardwareSectionsAndRefreshRemainAccessible() {
        var refreshes = 0
        compose.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.FontScale(2f)) {
                DeviceConfigurationOverride(DeviceConfigurationOverride.Locales(LocaleList("fa"))) {
                    DeviceAtlasTheme(darkTheme = true, dynamicColor = false) {
                        HardwareDeviceScreen(HardwareUiState(snapshot), { refreshes++ })
                    }
                }
            }
        }
        compose.onNodeWithTag("hardware_refresh").performClick()
        compose.runOnIdle { assertEquals(1, refreshes) }
        compose.onNodeWithTag("hardware_device_list").performScrollToNode(hasTestTag("hardware_MODEL"))
        compose.onNodeWithTag("hardware_MODEL").assertIsDisplayed()
        compose.onNodeWithTag("hardware_device_list").performScrollToNode(hasTestTag("hardware_BATTERY_LEVEL"))
        compose.onNode(hasText("دستگاه این مقدار را گزارش نمی‌کند") and
            hasAnyAncestor(hasTestTag("hardware_BATTERY_LEVEL"))).assertExists()
    }

    @Test fun honestEmptySensorInventoryHasNoMeasurementRows() {
        compose.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.Locales(LocaleList("en"))) {
                DeviceAtlasTheme(dynamicColor = false) { HardwareSensorsScreen(HardwareUiState(snapshot), {}) }
            }
        }
        compose.onNodeWithTag("hardware_sensor_list").performScrollToNode(hasText("Android reports no accessible sensors."))
        compose.onNodeWithText("Android reports no accessible sensors.").assertIsDisplayed()
    }

    @Test fun capacityDetailsAreVerticallySeparatedRatherThanOverlapping() {
        val capacity = HardwareDatum.Capacity(8L shl 30, 2L shl 30, 8.0, 2.0, 6.0, 25.0, 75.0)
        val state = HardwareUiState(snapshot.copy(fields = mapOf(HardwareField.MEMORY to Available(capacity))))
        compose.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.Locales(LocaleList("en"))) {
                DeviceAtlasTheme(dynamicColor = false) { HardwareDeviceScreen(state, {}) }
            }
        }
        compose.onNodeWithTag("hardware_device_list").performScrollToNode(hasTestTag("hardware_MEMORY"))
        val total = compose.onNodeWithText("Total: 8 GiB").fetchSemanticsNode().boundsInRoot
        val available = compose.onNodeWithText("Available estimate: 2 GiB (25%)").fetchSemanticsNode().boundsInRoot
        val unavailable = compose.onNodeWithText("Unavailable capacity: 6 GiB (75%)").fetchSemanticsNode().boundsInRoot
        assertTrue("Available row overlaps total row", available.top >= total.bottom)
        assertTrue("Unavailable row overlaps available row", unavailable.top >= available.bottom)
    }
}
