package io.github.masoudd2159.deviceatlas

import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.lifecycle.*
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.masoudd2159.deviceatlas.foundation.benchmark.*
import io.github.masoudd2159.deviceatlas.foundation.hardware.HardwareValue.*
import io.github.masoudd2159.deviceatlas.foundation.monitoring.*
import io.github.masoudd2159.deviceatlas.presentation.benchmark.*
import io.github.masoudd2159.deviceatlas.presentation.components.ResponsiveContent
import io.github.masoudd2159.deviceatlas.presentation.theme.DeviceAtlasTheme
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTestApi::class)
class BenchmarkLifecycleTest {
    @get:Rule val compose=createComposeRule()
    private class Owner : LifecycleOwner {
        val registry=LifecycleRegistry(this)
        override val lifecycle: Lifecycle get() = registry
    }
    @Test fun explicitStartVisibilityBackgroundStopAndReturnNeverResumes() {
        val scheduler=TestCoroutineScheduler()
        val dispatcher=StandardTestDispatcher(scheduler)
        val scope=CoroutineScope(SupervisorJob()+dispatcher)
        val owner=Owner(); val visible=mutableStateOf(true)
        var calls=0
        val processor=object : BenchmarkProcessor {
            override suspend fun chunk(workload: Workload,conservative: Boolean): BenchmarkChunk {
                calls++; delay(100); return BenchmarkChunk(BenchmarkRun(256,1_000_000),1,2)
            }
            override suspend fun summarize(workload: Workload,runs: List<BenchmarkRun>) = error("No run should finish")
        }
        val session=BenchmarkSession(BenchmarkSafetySource { SafetyReading(Available(80.0),Available(ThermalStatus.NONE),
            Available(0.2),Available(512L shl 20),Available(false)) },processor,MonotonicClock { scheduler.currentTime*1_000_000 },
            DiagnosticsCoordinator(),scope,dispatcher)
        compose.runOnIdle { owner.registry.currentState=Lifecycle.State.RESUMED }
        compose.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                DeviceAtlasTheme(dynamicColor=false) {
                    BenchmarkVisibility(session,visible.value)
                    ResponsiveContent {
                        Button({ visible.value=!visible.value },Modifier.testTag("bench_switch")) { Text("Switch destination") }
                        BenchmarkPanel(session.state.collectAsState().value,false,session::start,{session.stop()},session::reset)
                    }
                }
            }
        }
        compose.runOnIdle { scheduler.runCurrent(); assertEquals(0,calls) }
        compose.onNodeWithTag("bench_start").performScrollTo().performClick()
        compose.runOnIdle { scheduler.runCurrent(); assertEquals(1,calls) }
        compose.onNodeWithTag("bench_switch").performScrollTo().performClick()
        compose.runOnIdle { scheduler.advanceTimeBy(1000); scheduler.runCurrent(); assertEquals(1,calls) }
        compose.onNodeWithTag("bench_switch").performClick()
        compose.runOnIdle { scheduler.advanceTimeBy(1000); scheduler.runCurrent(); assertEquals(1,calls) }
        compose.onNodeWithTag("bench_start").performScrollTo().performClick()
        compose.runOnIdle { scheduler.runCurrent(); assertEquals(2,calls); owner.registry.currentState=Lifecycle.State.CREATED }
        compose.runOnIdle { scheduler.advanceTimeBy(1000); scheduler.runCurrent(); assertEquals(2,calls); owner.registry.currentState=Lifecycle.State.RESUMED }
        compose.runOnIdle { scheduler.advanceTimeBy(1000); scheduler.runCurrent(); assertEquals(2,calls) }
        compose.onNodeWithTag("bench_reset").performScrollTo().performClick()
        compose.runOnIdle { scheduler.runCurrent(); assertEquals(BenchmarkStatus.Idle,session.state.value.status); session.close(); scope.cancel(); scheduler.runCurrent() }
    }
    @Test fun persianRtlLargeTextAndBothThemesRenderHonestResults() {
        val dark=mutableStateOf(false)
        val state=BenchmarkState(status=BenchmarkStatus.Completed,results=Workload.entries.map { workload ->
            BenchmarkResult(workload,List(3) { BenchmarkRun(256,1_000_000) },768,3_000_000,256_000.0,250_000.0,260_000.0,3.9)
        })
        compose.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.FontScale(2f)) {
                DeviceConfigurationOverride(DeviceConfigurationOverride.Locales(androidx.compose.ui.text.intl.LocaleList("fa"))) {
                    DeviceAtlasTheme(darkTheme=dark.value,dynamicColor=false) {
                        ResponsiveContent { BenchmarkPanel(state,false,{},{},{}) }
                    }
                }
            }
        }
        compose.onNodeWithTag("bench_reset").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("نرخ کپی ترتیبی بافر درون فرایند").performScrollTo().assertIsDisplayed()
        compose.runOnIdle { dark.value=true }
        compose.onNodeWithText("نرخ کپی ترتیبی بافر درون فرایند").assertIsDisplayed()
    }
}
