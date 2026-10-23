package io.github.masoudd2159.deviceatlas

import androidx.compose.runtime.*
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.lifecycle.*
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.masoudd2159.deviceatlas.foundation.hardware.*
import io.github.masoudd2159.deviceatlas.foundation.hardware.HardwareValue.*
import io.github.masoudd2159.deviceatlas.foundation.monitoring.*
import io.github.masoudd2159.deviceatlas.presentation.components.ResponsiveContent
import io.github.masoudd2159.deviceatlas.presentation.monitoring.*
import io.github.masoudd2159.deviceatlas.presentation.theme.DeviceAtlasTheme
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Counts source calls with virtual time: hiding rendering alone cannot pass this test. */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTestApi::class)
class MonitoringLifecycleTest {
    @get:Rule val compose = createComposeRule()
    private class Owner : LifecycleOwner {
        val registry = LifecycleRegistry(this)
        override val lifecycle: Lifecycle get() = registry
    }

    @Test fun navigationAndBackgroundStopActualAcquisitionAndDoNotResume() {
        val scheduler = TestCoroutineScheduler()
        val scope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(scheduler))
        val visible = mutableStateOf(true)
        val owner = Owner()
        var calls = 0
        val source = MonitoringSource { sequence -> calls++; fixture(sequence, scheduler.currentTime * 1_000_000) }
        val processor = object : MonitoringProcessor {
            override suspend fun sample(raw: RawObservation, previous: RawObservation?) =
                ProcessedObservation(raw, mapOf(LiveMetric.AVAILABLE_RAM to Available(2.0)))
            override suspend fun summarize(history: List<ProcessedObservation>) = emptyMap<LiveMetric, HardwareValue<MetricSummary>>()
        }
        val session = MonitoringSession(source, processor, MonotonicClock { scheduler.currentTime * 1_000_000 }, scope)
        compose.runOnUiThread { owner.registry.currentState = Lifecycle.State.RESUMED }
        compose.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                DeviceAtlasTheme(dynamicColor = false) {
                    MonitoringVisibility(session, visible.value)
                    val state by session.state.collectAsState()
                    ResponsiveContent {
                        Button({ visible.value = !visible.value }, Modifier.testTag("switch_page")) { Text("Switch page") }
                        if (visible.value) MonitoringPanel(state, session::start, { session.stop() }, session::reset)
                    }
                }
            }
        }
        compose.runOnIdle { scheduler.advanceTimeBy(20_000); scheduler.runCurrent(); assertEquals(0, calls) }
        compose.onNodeWithTag("monitor_start").performClick()
        compose.runOnIdle { scheduler.runCurrent(); assertEquals(1, calls) }
        compose.onNodeWithTag("switch_page").performClick()
        compose.runOnIdle { scheduler.advanceTimeBy(20_000); scheduler.runCurrent(); assertEquals(1, calls) }
        compose.onNodeWithTag("switch_page").performClick()
        compose.runOnIdle { scheduler.advanceTimeBy(20_000); scheduler.runCurrent(); assertEquals(1, calls) }
        compose.onNodeWithTag("monitor_start").performClick()
        compose.runOnIdle { scheduler.runCurrent(); assertEquals(2, calls); owner.registry.currentState = Lifecycle.State.CREATED }
        compose.runOnIdle { scheduler.advanceTimeBy(20_000); scheduler.runCurrent(); assertEquals(2, calls); owner.registry.currentState = Lifecycle.State.RESUMED }
        compose.runOnIdle { scheduler.advanceTimeBy(20_000); scheduler.runCurrent(); assertEquals(2, calls) }
        compose.onNodeWithTag("monitor_reset").performClick()
        compose.runOnIdle { assertTrue(session.state.value.history.isEmpty()); scope.cancel(); scheduler.runCurrent() }
    }

    @Test fun chartsHandleMissingConstantSinglePointsPersianLargeTextAndThemes() {
        val dark = mutableStateOf(false)
        val history = listOf(
            ProcessedObservation(fixture(0,0), mapOf(LiveMetric.AVAILABLE_RAM to Available(2.0))),
            ProcessedObservation(fixture(1,5_000_000_000), mapOf(LiveMetric.AVAILABLE_RAM to Unavailable(UnavailableReason.TEMPORARY))),
            ProcessedObservation(fixture(2,10_000_000_000), mapOf(LiveMetric.AVAILABLE_RAM to Available(2.0), LiveMetric.BATTERY_TEMPERATURE to Available(-5.0))))
        val summary = mapOf(LiveMetric.AVAILABLE_RAM to Available(MetricSummary(2.0,2.0,2.0,2.0,2)))
        compose.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.FontScale(1.8f)) {
                DeviceConfigurationOverride(DeviceConfigurationOverride.Locales(androidx.compose.ui.text.intl.LocaleList("fa"))) {
                    DeviceAtlasTheme(darkTheme = dark.value, dynamicColor = false) {
                        ResponsiveContent { MonitoringPanel(MonitoringState(history = history, summaries = summary), {}, {}, {}) }
                    }
                }
            }
        }
        compose.onNodeWithTag("monitor_reset").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("chart_AVAILABLE_RAM").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("chart_BATTERY_TEMPERATURE").performScrollTo().assertIsDisplayed()
        compose.runOnIdle { dark.value = true }
        compose.onNodeWithTag("chart_BATTERY_TEMPERATURE").assertIsDisplayed()
    }
}

private fun fixture(sequence: Long, nanos: Long): RawObservation {
    val missing = Unavailable(UnavailableReason.NOT_REPORTED)
    return RawObservation(sequence,nanos,nanos,0,Available(0L),Available(8L shl 30),Available(2L shl 30),
        Available(1L shl 30),missing,missing,missing,missing,missing,missing,Available(false),missing,missing)
}
