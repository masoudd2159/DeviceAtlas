package io.github.masoudd2159.deviceatlas

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.lifecycle.*
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.masoudd2159.deviceatlas.foundation.benchmark.DiagnosticsCoordinator
import io.github.masoudd2159.deviceatlas.foundation.interactive.*
import io.github.masoudd2159.deviceatlas.foundation.monitoring.MonotonicClock
import io.github.masoudd2159.deviceatlas.presentation.components.ResponsiveContent
import io.github.masoudd2159.deviceatlas.presentation.interactive.*
import io.github.masoudd2159.deviceatlas.presentation.theme.DeviceAtlasTheme
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTestApi::class)
class InteractiveLifecycleTest {
    @get:Rule val compose = createComposeRule()
    private class Owner : LifecycleOwner { val registry = LifecycleRegistry(this); override val lifecycle: Lifecycle get() = registry }
    @Test fun displayTouchSensorHapticAndForegroundLifecycle() {
        val scheduler = TestCoroutineScheduler(); val dispatcher = StandardTestDispatcher(scheduler)
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        val owner = Owner(); val visible = mutableStateOf(true)
        var registrations = 0; var closes = 0; var pulses = 0; var cancels = 0
        val sensors = object : InteractiveSensors {
            override fun open(kind: SensorKind, receive: (SensorReading) -> Unit): Pair<SensorInfo, SensorSubscription> {
                registrations++; return SensorInfo(10.0) to object : SensorSubscription { override fun close() { closes++ } }
            }
        }
        val haptics = object : InteractiveHaptics { override fun pulse(millis: Long) { assertEquals(50L,millis); pulses++ }; override fun cancel() { cancels++ } }
        val session = InteractiveSession(sensors,haptics,SensorAnalyzer { _,_ -> error("No sample supplied") },
            MonotonicClock { scheduler.currentTime*1_000_000 },DiagnosticsCoordinator(),scope,dispatcher)
        compose.runOnIdle { owner.registry.currentState = Lifecycle.State.RESUMED }
        compose.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                DeviceAtlasTheme(dynamicColor=false) {
                    InteractiveVisibility(session,visible.value)
                    ResponsiveContent { InteractivePanel(session.state.collectAsState().value,session) }
                }
            }
        }
        compose.onNodeWithTag("interactive_start").performScrollTo().performClick()
        compose.runOnIdle { scheduler.runCurrent() }
        compose.onNodeWithTag("display_surface").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("display_next").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(DisplayPattern.GREEN,session.state.value.pattern) }
        compose.onNodeWithTag("interactive_exit").performScrollTo().performClick()
        compose.runOnIdle { scheduler.runCurrent() }
        compose.onNodeWithTag("interactive_TOUCH").performScrollTo().performClick()
        compose.onNodeWithTag("interactive_start").performScrollTo().performClick()
        compose.runOnIdle { scheduler.runCurrent() }
        compose.onNodeWithTag("touch_surface").performScrollTo().performTouchInput { click(center) }
        compose.runOnIdle { assertTrue(session.state.value.touch.visited.isNotEmpty()) }
        compose.onNodeWithTag("touch_surface").performTouchInput { down(0,centerLeft); down(1,centerRight); move(); up(0); up(1) }
        compose.runOnIdle { assertEquals(2,session.state.value.touch.maximumContacts); assertEquals(0,session.state.value.touch.activeCount) }
        compose.onNodeWithTag("interactive_SENSOR").performScrollTo().performClick()
        compose.runOnIdle { scheduler.runCurrent(); assertEquals(0,registrations) }
        compose.onNodeWithTag("interactive_start").performScrollTo().performClick()
        compose.runOnIdle { scheduler.runCurrent(); assertEquals(1,registrations); owner.registry.currentState = Lifecycle.State.CREATED }
        compose.runOnIdle { scheduler.runCurrent(); assertEquals(1,closes); owner.registry.currentState = Lifecycle.State.RESUMED }
        compose.runOnIdle { scheduler.advanceTimeBy(1000); scheduler.runCurrent(); assertEquals(1,registrations) }
        compose.onNodeWithTag("interactive_HAPTIC").performScrollTo().performClick()
        compose.onNodeWithTag("interactive_start").performScrollTo().performClick()
        compose.runOnIdle { scheduler.runCurrent(); assertEquals(0,pulses) }
        compose.onNodeWithTag("haptic_pulse").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1,pulses); visible.value = false }
        compose.runOnIdle { scheduler.runCurrent(); assertEquals(1,cancels); visible.value = true }
        compose.runOnIdle { scheduler.runCurrent(); assertEquals(1,pulses); session.close(); scope.cancel(); scheduler.runCurrent() }
    }
    @Test fun persianRtlLargeFontsAndThemesKeepExitAccessible() {
        val dark = mutableStateOf(false)
        compose.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.FontScale(2f)) {
                DeviceConfigurationOverride(DeviceConfigurationOverride.Locales(androidx.compose.ui.text.intl.LocaleList("fa"))) {
                    DeviceAtlasTheme(darkTheme=dark.value,dynamicColor=false) {
                        ResponsiveContent { InteractivePanel(InteractiveState(status=InteractiveStatus.Running),null) }
                    }
                }
            }
        }
        compose.onNodeWithTag("interactive_exit").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("توقف / خروج از آزمون").assertIsDisplayed()
        compose.onNodeWithTag("display_surface").performScrollTo().assertIsDisplayed()
        compose.runOnIdle { dark.value = true }
        compose.onNodeWithTag("interactive_exit").performScrollTo().assertIsDisplayed()
    }
}
