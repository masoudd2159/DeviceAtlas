package io.github.masoudd2159.deviceatlas

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.text.intl.LocaleList
import io.github.masoudd2159.deviceatlas.data.hardware.platformText
import io.github.masoudd2159.deviceatlas.foundation.hardware.*
import io.github.masoudd2159.deviceatlas.foundation.hardware.HardwareValue.*
import io.github.masoudd2159.deviceatlas.presentation.hardware.*
import io.github.masoudd2159.deviceatlas.presentation.theme.DeviceAtlasTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.text.NumberFormat

/** Only test fixtures are synthetic. No global app locale or real hardware is modified. */
class HardwareReviewRegressionTest {
    @get:Rule val compose = createComposeRule()
    private val snapshot = HardwareSnapshot(emptyMap(), Available(emptyList()), 123)
    private val labels = mutableMapOf<Int, String>()

    private fun content(locale: String, body: @androidx.compose.runtime.Composable () -> Unit) {
        compose.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.Locales(LocaleList(locale))) {
                listOf(R.string.hw_initial_failed, R.string.hw_loading, R.string.hw_refresh_failed,
                    R.string.hw_refreshing, R.string.hw_not_loaded, R.string.hw_sensor_inventory).forEach {
                    labels[it] = stringResource(it)
                }
                val format = NumberFormat.getNumberInstance(LocalConfiguration.current.locales[0])
                labels[R.string.hw_inventory_available] = stringResource(R.string.hw_inventory_available, format.format(0))
                labels[R.string.hw_inventory_unavailable] = stringResource(R.string.hw_inventory_unavailable,
                    stringResource(R.string.hw_reason_collection_failed))
                labels[R.string.hw_field_counts] = stringResource(R.string.hw_field_counts, format.format(1), format.format(3))
                labels[R.string.hw_invalid_count] = stringResource(R.string.hw_invalid_count, format.format(1))
                labels[R.string.hw_native_count] = stringResource(R.string.hw_native_count, format.format(1))
                DeviceAtlasTheme(dynamicColor = false) { body() }
            }
        }
    }

    private fun initialFailure(locale: String) {
        content(locale) { HardwareRefresh(HardwareUiState(failed = true), {}) }
        compose.onNodeWithText(labels.getValue(R.string.hw_initial_failed)).assertIsDisplayed()
        compose.onNodeWithText(labels.getValue(R.string.hw_loading)).assertDoesNotExist()
        compose.onNodeWithTag("hardware_refresh").assertIsEnabled().assertHasClickAction()
    }
    @Test fun initialFailureEnglish() = initialFailure("en")
    @Test fun initialFailurePersian() = initialFailure("fa")

    @Test fun initialLoadingDisablesRefreshAndHasProgressSemantics() {
        content("en") { HardwareRefresh(HardwareUiState(refreshing = true), {}) }
        compose.onNodeWithText(labels.getValue(R.string.hw_loading)).assertIsDisplayed().assert(
            SemanticsMatcher.expectValue(SemanticsProperties.ProgressBarRangeInfo, ProgressBarRangeInfo.Indeterminate))
        compose.onNodeWithText(labels.getValue(R.string.hw_initial_failed)).assertDoesNotExist()
        compose.onNodeWithText(labels.getValue(R.string.hw_refreshing)).assertDoesNotExist()
        compose.onNodeWithTag("hardware_refresh").assertIsNotEnabled()
    }

    @Test fun failedRetryRecoversWithoutLosingPreviousSnapshotTimestamp() {
        val state = mutableStateOf(HardwareUiState(snapshot, failed = true))
        var captured = ""
        content("fa") {
            captured = stringResource(R.string.hw_captured, java.text.DateFormat.getDateTimeInstance(
                java.text.DateFormat.SHORT, java.text.DateFormat.MEDIUM,
                LocalConfiguration.current.locales[0]).format(java.util.Date(123)))
            HardwareRefresh(state.value) { state.value = state.value.copy(refreshing = true, failed = false) }
        }
        compose.onNodeWithText(labels.getValue(R.string.hw_refresh_failed)).assertIsDisplayed()
        compose.onNodeWithText(captured).assertIsDisplayed()
        compose.onNodeWithTag("hardware_refresh").performClick()
        compose.onNodeWithText(labels.getValue(R.string.hw_refreshing)).assertIsDisplayed()
        compose.onNodeWithText(labels.getValue(R.string.hw_refresh_failed)).assertDoesNotExist()
        compose.onNodeWithTag("hardware_refresh").assertIsNotEnabled()
        compose.onNodeWithText(captured).assertIsDisplayed()
        compose.runOnIdle { state.value = state.value.copy(refreshing = false) }
        compose.onNodeWithText(labels.getValue(R.string.hw_refresh_failed)).assertDoesNotExist()
        compose.onNodeWithText(labels.getValue(R.string.hw_loading)).assertDoesNotExist()
        compose.onNodeWithText(captured).assertIsDisplayed()
    }

    @Test fun initialRetryThenSuccessRemovesFailureAndLoading() {
        val state = mutableStateOf(HardwareUiState(failed = true))
        content("en") { HardwareRefresh(state.value) { state.value = HardwareUiState(refreshing = true) } }
        compose.onNodeWithTag("hardware_refresh").performClick()
        compose.onNodeWithText(labels.getValue(R.string.hw_loading)).assertIsDisplayed()
        compose.onNodeWithText(labels.getValue(R.string.hw_initial_failed)).assertDoesNotExist()
        compose.runOnIdle { state.value = HardwareUiState(snapshot) }
        compose.onNodeWithText(labels.getValue(R.string.hw_loading)).assertDoesNotExist()
        compose.onNodeWithTag("hardware_refresh").assertIsEnabled()
    }

    @Test fun availableEmptyInventoryIsSuccessfulEnglish() {
        content("en") { HardwareIntegrity(HardwareUiState(snapshot)) }
        compose.onNodeWithText(labels.getValue(R.string.hw_inventory_available)).assertIsDisplayed()
    }
    @Test fun availableEmptyInventoryIsSuccessfulPersian() {
        content("fa") { HardwareIntegrity(HardwareUiState(snapshot)) }
        compose.onNodeWithText(labels.getValue(R.string.hw_inventory_available)).assertIsDisplayed()
    }
    @Test fun unavailableInventoryIsReportedWithItsReason() {
        content("fa") { HardwareIntegrity(HardwareUiState(snapshot.copy(sensors = Unavailable(UnavailableReason.COLLECTION_FAILED)))) }
        compose.onNodeWithText(labels.getValue(R.string.hw_inventory_unavailable)).assertIsDisplayed()
        compose.onNodeWithText(labels.getValue(R.string.hw_inventory_available)).assertDoesNotExist()
    }
    @Test fun nonemptyInventoryPreservesPartialInvalidAndNativeCounts() {
        val partial = SensorCapability(0, 1, mapOf(
            SensorField.POWER to Unavailable(UnavailableReason.NATIVE_FAILED),
            SensorField.VENDOR to Unavailable(UnavailableReason.NOT_REPORTED)))
        val state = HardwareUiState(snapshot.copy(fields = mapOf(
            HardwareField.MODEL to Available(HardwareDatum.Text("Reported model")),
            HardwareField.BATTERY_LEVEL to Unavailable(UnavailableReason.INVALID_RESPONSE)), sensors = Available(listOf(partial))))
        content("en") { HardwareIntegrity(state) }
        compose.onNodeWithText("Inventory queried successfully. Sensors reported: 1").assertIsDisplayed()
        listOf(R.string.hw_field_counts, R.string.hw_invalid_count, R.string.hw_native_count).forEach {
            compose.onNodeWithText(labels.getValue(it)).assertIsDisplayed()
        }
    }

    private fun sensor(index: Int, version: Int) = SensorCapability(index, 1, mapOf(
        SensorField.NAME to Available(HardwareDatum.Text("Same name")),
        SensorField.VENDOR to Available(HardwareDatum.Text("Same vendor")),
        SensorField.VERSION to Available(HardwareDatum.Number(version.toDouble(), UnitKind.NONE))))

    @Test fun reorderedSameTypeSensorsDoNotInheritExpansionEvenAtSameTimestamp() {
        val state = mutableStateOf(HardwareUiState(snapshot.copy(sensors = Available(listOf(sensor(0, 1), sensor(1, 2))))))
        content("en") { HardwareSensorsScreen(state.value, {}) }
        compose.onAllNodesWithText("Show sensor details")[0].performScrollTo().performClick()
        compose.onNodeWithText("Hide sensor details").assertExists()
        compose.runOnIdle { state.value = state.value.copy(refreshing = true) }
        compose.onNodeWithText("Hide sensor details").assertExists() // ordinary recomposition
        compose.runOnIdle {
            state.value = HardwareUiState(snapshot.copy(sensors = Available(listOf(sensor(0, 2), sensor(1, 1)))), sensorInventoryRevision = 1)
        }
        compose.onNodeWithText("Hide sensor details").assertDoesNotExist()
    }

    @Test fun duplicateSensorsExpandIndependentlyAndRestoreWithinSameRevision() {
        val state = HardwareUiState(snapshot.copy(sensors = Available(listOf(sensor(0, 1), sensor(1, 1)))))
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.Locales(LocaleList("en"))) {
                DeviceAtlasTheme(dynamicColor = false) { HardwareSensorsScreen(state, {}) }
            }
        }
        compose.onAllNodesWithText("Show sensor details")[0].performScrollTo().performClick()
        compose.onNodeWithText("Hide sensor details").assertExists()
        compose.onNodeWithText("Show sensor details").assertExists()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("Hide sensor details").assertExists()
        compose.onNodeWithText("Show sensor details").assertExists()
    }

    @Test fun unusableModelFixturesUseTheProductionSanitizerAndLocalizedExplanation() {
        val raw = mutableStateOf<String?>(null)
        var reason = ""
        content("fa") {
            reason = stringResource(R.string.hw_reason_not_reported)
            HardwareDeviceScreen(HardwareUiState(snapshot.copy(fields = mapOf(HardwareField.MODEL to platformText(raw.value)))), {})
        }
        fun assertModel() {
            compose.onNodeWithTag("hardware_device_list").performScrollToNode(hasTestTag("hardware_MODEL"))
            compose.onNode(hasText(reason) and hasAnyAncestor(hasTestTag("hardware_MODEL"))).assertExists()
        }
        assertModel()
        compose.runOnIdle { raw.value = " UNKNOWN " }; assertModel()
        compose.runOnIdle { raw.value = "  " }; assertModel()
        compose.runOnIdle { raw.value = "  Long technical Model 123\u202e  " }
        compose.onNode(hasText("Long technical Model 123") and hasAnyAncestor(hasTestTag("hardware_MODEL"))).assertExists()
    }
}
