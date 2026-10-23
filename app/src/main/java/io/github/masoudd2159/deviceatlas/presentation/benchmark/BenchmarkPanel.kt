package io.github.masoudd2159.deviceatlas.presentation.benchmark

import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.core.text.BidiFormatter
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.masoudd2159.deviceatlas.BuildConfig
import io.github.masoudd2159.deviceatlas.R
import io.github.masoudd2159.deviceatlas.foundation.benchmark.*
import io.github.masoudd2159.deviceatlas.foundation.hardware.HardwareValue
import io.github.masoudd2159.deviceatlas.foundation.hardware.HardwareValue.*
import io.github.masoudd2159.deviceatlas.presentation.components.*
import io.github.masoudd2159.deviceatlas.presentation.hardware.number
import io.github.masoudd2159.deviceatlas.presentation.hardware.reasonLabel

@Composable
fun BenchmarkVisibility(session: BenchmarkSession, visible: Boolean) {
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner, session, visible) {
        fun update(event: Lifecycle.Event? = null) = session.setEligibility(visible,
            owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED), event == Lifecycle.Event.ON_DESTROY)
        val observer = LifecycleEventObserver { _, event -> update(event) }
        owner.lifecycle.addObserver(observer); update()
        onDispose { owner.lifecycle.removeObserver(observer); session.setEligibility(false, false) }
    }
}

@Composable
fun BenchmarkPanel(state: BenchmarkState, monitoringBusy: Boolean, start: () -> Unit, stop: () -> Unit, reset: () -> Unit) {
    val focus = remember { FocusRequester() }
    var wasActive by remember { mutableStateOf(false) }
    LaunchedEffect(state.status) {
        if (wasActive && state.status != BenchmarkStatus.Active) focus.requestFocus()
        wasActive = state.status == BenchmarkStatus.Active
    }
    InformationCard {
        SectionHeader(stringResource(R.string.bench_title))
        Text(stringResource(R.string.bench_consent))
        Text(stringResource(R.string.bench_limits))
        if (monitoringBusy) Text(stringResource(R.string.bench_monitoring))
        val status = when (val status = state.status) {
            BenchmarkStatus.Idle -> stringResource(R.string.bench_idle)
            BenchmarkStatus.Active -> stringResource(when (state.phase) {
                BenchmarkPhase.CHECKING -> R.string.bench_checking
                BenchmarkPhase.WARMUP -> R.string.bench_warmup
                BenchmarkPhase.MEASURING -> R.string.bench_measuring
            })
            BenchmarkStatus.Completed -> stringResource(R.string.bench_completed)
            is BenchmarkStatus.Stopped -> stringResource(when (status.reason) {
                BenchmarkStop.USER -> R.string.bench_stopped
                BenchmarkStop.HIDDEN -> R.string.bench_hidden
                BenchmarkStop.BACKGROUND -> R.string.bench_background
                BenchmarkStop.DESTROYED -> R.string.bench_destroyed
                BenchmarkStop.TIMEOUT -> R.string.bench_timeout
                BenchmarkStop.THERMAL -> R.string.bench_thermal_stop
                BenchmarkStop.BATTERY -> R.string.bench_battery_stop
                BenchmarkStop.MEMORY -> R.string.bench_memory_stop
                BenchmarkStop.CANCELED -> R.string.bench_canceled
            })
            is BenchmarkStatus.Failed -> stringResource(when (status.reason) {
                BenchmarkError.NATIVE_UNAVAILABLE -> R.string.bench_native_unavailable
                BenchmarkError.NATIVE_FAILED -> R.string.bench_native_failed
                BenchmarkError.INVALID_RESULT -> R.string.bench_invalid
                BenchmarkError.SAFETY_UNAVAILABLE -> R.string.bench_safety_failed
            })
        }
        val announcement = if (state.status == BenchmarkStatus.Active && state.workload != null)
            "$status: ${stringResource(workloadLabel(state.workload))}" else status
        Text(announcement, Modifier.testTag("bench_status").semantics {
            liveRegion = LiveRegionMode.Polite; stateDescription = announcement
        })
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(start, enabled = state.canStart, modifier = Modifier.testTag("bench_start")) { Text(stringResource(R.string.bench_start)) }
            OutlinedButton(stop, enabled = state.status == BenchmarkStatus.Active, modifier = Modifier.testTag("bench_stop")) { Text(stringResource(R.string.bench_stop)) }
            TextButton(reset, modifier = Modifier.testTag("bench_reset").focusRequester(focus)) { Text(stringResource(R.string.bench_reset)) }
        }
        if (state.retiring) Text(stringResource(R.string.bench_retiring))
        Text(stringResource(R.string.bench_elapsed, number(state.elapsedMillis / 1000.0)))
        if (state.plannedChunks > 0) {
            val progress = stringResource(R.string.bench_progress, number(state.completedChunks), number(state.plannedChunks), number(state.completedRuns))
            LinearProgressIndicator(progress = { state.completedChunks.toFloat() / state.plannedChunks },
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = progress })
            Text(progress)
        }
        Text(stringResource(if (BuildConfig.DEBUG) R.string.bench_debug else R.string.bench_release))
        Text(stringResource(R.string.bench_context, number(Build.VERSION.SDK_INT),
            System.getProperty("os.arch") ?: stringResource(R.string.bench_unknown)))
        Text(stringResource(R.string.bench_limitations), style = MaterialTheme.typography.bodySmall)
    }
    InformationCard {
        SectionHeader(stringResource(R.string.bench_readiness))
        val reading = state.safety
        if (reading == null) Text(stringResource(R.string.bench_safety_pending))
        else {
            InformationRow(stringResource(R.string.bench_battery), safetyValue(reading.batteryPercent) { stringResource(R.string.monitor_percent, number(it)) })
            InformationRow(stringResource(R.string.bench_thermal), safetyValue(reading.thermal) { stringResource(when (it) {
                io.github.masoudd2159.deviceatlas.foundation.monitoring.ThermalStatus.NONE -> R.string.monitor_thermal_none
                io.github.masoudd2159.deviceatlas.foundation.monitoring.ThermalStatus.LIGHT -> R.string.monitor_thermal_light
                io.github.masoudd2159.deviceatlas.foundation.monitoring.ThermalStatus.MODERATE -> R.string.monitor_thermal_moderate
                io.github.masoudd2159.deviceatlas.foundation.monitoring.ThermalStatus.SEVERE -> R.string.monitor_thermal_severe
                io.github.masoudd2159.deviceatlas.foundation.monitoring.ThermalStatus.CRITICAL -> R.string.monitor_thermal_critical
                io.github.masoudd2159.deviceatlas.foundation.monitoring.ThermalStatus.EMERGENCY -> R.string.monitor_thermal_emergency
                io.github.masoudd2159.deviceatlas.foundation.monitoring.ThermalStatus.SHUTDOWN -> R.string.monitor_thermal_shutdown
            }) })
            InformationRow(stringResource(R.string.bench_headroom), safetyValue(reading.headroom) { stringResource(R.string.monitor_ratio, number(it)) })
            InformationRow(stringResource(R.string.bench_memory), safetyValue(reading.availableBytes) { stringResource(R.string.bench_bytes, number(it)) })
            InformationRow(stringResource(R.string.monitor_low_memory), safetyValue(reading.lowMemory) { stringResource(if (it) R.string.monitor_yes else R.string.monitor_no) })
            Text(stringResource(if (state.conservative) R.string.bench_conservative else R.string.bench_safety_scope))
        }
    }
    state.results.forEach { result ->
        InformationCard {
            SectionHeader(stringResource(workloadLabel(result.workload)))
            val unit = stringResource(if (result.workload == Workload.MEMORY) R.string.bench_mib_s else R.string.bench_iterations_s)
            val locale = LocalConfiguration.current.locales[0]
            @Composable fun rate(value: Double) = BidiFormatter.getInstance(locale).unicodeWrap("${number(value)} $unit")
            InformationRow(stringResource(R.string.bench_median), rate(result.median))
            InformationRow(stringResource(R.string.bench_minimum), rate(result.minimum))
            InformationRow(stringResource(R.string.bench_maximum), rate(result.maximum))
            InformationRow(stringResource(R.string.bench_variation), stringResource(R.string.monitor_percent, number(result.variationPercent)))
            InformationRow(stringResource(R.string.bench_runs), number(result.runs.size))
            InformationRow(stringResource(if (result.workload == Workload.MEMORY) R.string.bench_copied_bytes else R.string.bench_iterations), number(result.totalWork))
            InformationRow(stringResource(R.string.bench_active), stringResource(R.string.bench_ms, number(result.activeNanos / 1_000_000.0)))
            Text(stringResource(if (state.status == BenchmarkStatus.Completed && result.runs.size == 3) R.string.bench_result_complete else R.string.bench_result_partial))
            Text(stringResource(if (result.workload == Workload.MEMORY) R.string.bench_memory_method else R.string.bench_cpu_method), style = MaterialTheme.typography.bodySmall)
        }
    }
}

private fun workloadLabel(workload: Workload) = when (workload) {
    Workload.INTEGER -> R.string.bench_integer
    Workload.FLOATING -> R.string.bench_floating
    Workload.MEMORY -> R.string.bench_transfer
}
@Composable private fun <T> safetyValue(value: HardwareValue<T>, format: @Composable (T) -> String): String = when (value) {
    is Available -> format(value.value)
    is Unavailable -> stringResource(reasonLabel(value.reason))
}
