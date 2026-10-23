package io.github.masoudd2159.deviceatlas.presentation.monitoring

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.core.text.BidiFormatter
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.masoudd2159.deviceatlas.R
import io.github.masoudd2159.deviceatlas.foundation.hardware.HardwareValue
import io.github.masoudd2159.deviceatlas.foundation.hardware.HardwareValue.*
import io.github.masoudd2159.deviceatlas.foundation.hardware.UnavailableReason
import io.github.masoudd2159.deviceatlas.foundation.monitoring.*
import io.github.masoudd2159.deviceatlas.presentation.components.*
import io.github.masoudd2159.deviceatlas.presentation.hardware.number
import io.github.masoudd2159.deviceatlas.presentation.hardware.reasonLabel
import java.text.DateFormat
import java.util.Date
import kotlin.math.abs

/** Bound to the shell's Activity lifecycle, outside retained NavDisplay entries. */
@Composable
fun MonitoringVisibility(session: MonitoringSession, visible: Boolean) {
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner, session, visible) {
        fun update(reason: StopReason = StopReason.BACKGROUND) = session.setEligibility(
            visible, owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED), reason)
        val observer = LifecycleEventObserver { _, event ->
            update(if (event == Lifecycle.Event.ON_DESTROY) StopReason.DESTROYED else StopReason.BACKGROUND)
        }
        owner.lifecycle.addObserver(observer)
        update()
        onDispose {
            owner.lifecycle.removeObserver(observer)
            session.setEligibility(false, false)
        }
    }
}

@Composable
fun MonitoringPanel(state: MonitoringState, start: () -> Unit, stop: () -> Unit, reset: () -> Unit) {
    InformationCard {
        SectionHeader(stringResource(R.string.monitor_title))
        Text(stringResource(R.string.monitor_policy))
        val status = statusText(state)
        Text(status, Modifier.testTag("monitor_status").semantics {
            liveRegion = LiveRegionMode.Polite; stateDescription = status
        })
        // Stable buttons preserve focus when status changes; wrap on compact/large-font layouts.
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(start, enabled = state.canStart, modifier = Modifier.testTag("monitor_start")) { Text(stringResource(R.string.monitor_start)) }
            OutlinedButton(stop, enabled = state.status is MonitoringStatus.Running, modifier = Modifier.testTag("monitor_stop")) { Text(stringResource(R.string.monitor_stop)) }
            TextButton(reset, modifier = Modifier.testTag("monitor_reset")) { Text(stringResource(R.string.monitor_reset)) }
        }
        Text(stringResource(R.string.monitor_elapsed, number(state.elapsedMillis / 1000)))
        Text(stringResource(R.string.monitor_count, number(state.history.size)))
        val last = state.history.lastOrNull { it.values.values.any { value -> value is Available } }
        val locale = LocalConfiguration.current.locales[0]
        Text(stringResource(R.string.monitor_last, last?.let {
            DateFormat.getTimeInstance(DateFormat.MEDIUM, locale).format(Date(it.raw.wallMillis))
        } ?: stringResource(R.string.monitor_no_samples)))
    }
    val latest = state.history.lastOrNull()
    if (latest == null) {
        Text(stringResource(R.string.monitor_no_samples))
        return
    }
    InformationCard {
        SectionHeader(stringResource(R.string.monitor_current))
        LiveMetric.entries.forEach { metric ->
            InformationRow(stringResource(label(metric)), valueText(latest.values[metric] ?: Unavailable(UnavailableReason.NOT_REPORTED), metric))
        }
        InformationRow(stringResource(R.string.monitor_low_memory), when (val flag = latest.raw.lowMemory) {
            is Available -> stringResource(if (flag.value) R.string.monitor_yes else R.string.monitor_no)
            is Unavailable -> stringResource(reasonLabel(flag.reason))
        })
        InformationRow(stringResource(R.string.monitor_charging), when (val charging = latest.raw.chargingStatus) {
            is Available -> stringResource(when (charging.value) {
                ChargingStatus.CHARGING -> R.string.hw_category_charging
                ChargingStatus.DISCHARGING -> R.string.hw_category_discharging
                ChargingStatus.FULL -> R.string.hw_category_full
                ChargingStatus.NOT_CHARGING -> R.string.hw_category_not_charging
            })
            is Unavailable -> stringResource(reasonLabel(charging.reason))
        })
        InformationRow(stringResource(R.string.monitor_thermal), when (val thermal = latest.raw.thermalStatus) {
            is Available -> stringResource(when (thermal.value) {
                ThermalStatus.NONE -> R.string.monitor_thermal_none
                ThermalStatus.LIGHT -> R.string.monitor_thermal_light
                ThermalStatus.MODERATE -> R.string.monitor_thermal_moderate
                ThermalStatus.SEVERE -> R.string.monitor_thermal_severe
                ThermalStatus.CRITICAL -> R.string.monitor_thermal_critical
                ThermalStatus.EMERGENCY -> R.string.monitor_thermal_emergency
                ThermalStatus.SHUTDOWN -> R.string.monitor_thermal_shutdown
            })
            is Unavailable -> stringResource(reasonLabel(thermal.reason))
        })
        Text(stringResource(R.string.monitor_scope), style = MaterialTheme.typography.bodySmall)
    }
    listOf(LiveMetric.AVAILABLE_RAM, LiveMetric.PROCESS_CPU, LiveMetric.BATTERY_TEMPERATURE).forEach { metric ->
        // Unsupported metrics retain a textual reason in current readings, without empty charts.
        if (state.history.any { it.values[metric] is Available }) MetricChart(state, metric)
    }
}

@Composable
private fun statusText(state: MonitoringState): String = when (val status = state.status) {
    MonitoringStatus.Idle -> stringResource(if (state.cleared) R.string.monitor_cleared else R.string.monitor_idle)
    MonitoringStatus.Running -> stringResource(R.string.monitor_running)
    is MonitoringStatus.Failed -> stringResource(R.string.monitor_failed, stringResource(reasonLabel(status.reason)))
    is MonitoringStatus.Stopped -> stringResource(when (status.reason) {
        StopReason.USER -> R.string.monitor_stopped
        StopReason.HIDDEN -> R.string.monitor_hidden
        StopReason.BACKGROUND -> R.string.monitor_background
        StopReason.DESTROYED -> R.string.monitor_destroyed
        StopReason.LIMIT -> R.string.monitor_limit
        StopReason.CANCELED -> R.string.monitor_canceled
    })
}

@Composable
private fun MetricChart(state: MonitoringState, metric: LiveMetric) {
    val summary = state.summaries[metric]
    val title = stringResource(label(metric))
    val text = if (summary is Available) {
        val s = summary.value
        stringResource(R.string.monitor_summary, valueText(Available(s.minimum), metric),
            valueText(Available(s.maximum), metric), valueText(Available(s.average), metric),
            valueText(Available(s.latest), metric), number(s.count))
    } else stringResource(reasonLabel((summary as? Unavailable)?.reason ?: UnavailableReason.INSUFFICIENT))
    val color = MaterialTheme.colorScheme.primary
    val axis = MaterialTheme.colorScheme.outlineVariant
    val points = remember(state.history, metric) { state.history.map { it.raw.monotonicNanos to (it.values[metric] as? Available)?.value } }
    val description = stringResource(R.string.monitor_chart_description, title, text)
    InformationCard {
        SectionHeader(title)
        Text(text)
        Canvas(Modifier.fillMaxWidth().height(120.dp).testTag("chart_${metric.name}")
            .semantics { contentDescription = description }) {
            val values = points.mapNotNull { it.second }
            val scale = values.maxOfOrNull { abs(it) }?.takeIf { it > 0 } ?: 1.0
            val low = values.minOrNull()?.div(scale) ?: 0.0
            val high = values.maxOrNull()?.div(scale) ?: 0.0
            val first = points.first().first
            val duration = (points.last().first - first).coerceAtLeast(1).toDouble()
            drawLine(axis, Offset(4f, size.height - 4f), Offset(size.width - 4f, size.height - 4f))
            var previous: Pair<Long, Offset>? = null
            points.forEach { (time, value) ->
                if (value == null) previous = null
                else {
                    val x = 4f + ((time - first) / duration).toFloat() * (size.width - 8f)
                    val y = if (high == low) size.height / 2f else 4f + (1 - (value / scale - low) / (high - low)).toFloat() * (size.height - 8f)
                    val point = Offset(x, y)
                    previous?.let { (lastTime, lastPoint) ->
                        if (time - lastTime <= SAMPLE_INTERVAL_MS * 2 * 1_000_000) drawLine(color, lastPoint, point, strokeWidth = 3f)
                    }
                    drawCircle(color, radius = 4f, center = point)
                    previous = time to point
                }
            }
        }
        Text(stringResource(R.string.monitor_axis, number(0), number((points.last().first - points.first().first) / 1_000_000_000)))
        Text(stringResource(R.string.monitor_sample_average), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun valueText(value: HardwareValue<Double>, metric: LiveMetric): String = when (value) {
    is Unavailable -> stringResource(reasonLabel(value.reason))
    is Available -> BidiFormatter.getInstance(LocalConfiguration.current.locales[0]).unicodeWrap(stringResource(when (metric) {
        LiveMetric.TOTAL_RAM, LiveMetric.AVAILABLE_RAM, LiveMetric.MEMORY_THRESHOLD -> R.string.monitor_gib
        LiveMetric.AVAILABLE_PERCENT, LiveMetric.BATTERY_LEVEL, LiveMetric.PROCESS_CPU -> R.string.monitor_percent
        LiveMetric.BATTERY_TEMPERATURE -> R.string.monitor_celsius
        LiveMetric.BATTERY_CURRENT -> R.string.monitor_ma
        LiveMetric.BATTERY_VOLTAGE -> R.string.monitor_volt
        LiveMetric.THERMAL_HEADROOM -> R.string.monitor_ratio
    }, number(value.value)))
}

private fun label(metric: LiveMetric): Int = when (metric) {
    LiveMetric.TOTAL_RAM -> R.string.monitor_total_ram
    LiveMetric.AVAILABLE_RAM -> R.string.monitor_available_ram
    LiveMetric.AVAILABLE_PERCENT -> R.string.monitor_available_percent
    LiveMetric.MEMORY_THRESHOLD -> R.string.monitor_threshold
    LiveMetric.BATTERY_LEVEL -> R.string.monitor_battery_level
    LiveMetric.BATTERY_TEMPERATURE -> R.string.monitor_temperature
    LiveMetric.BATTERY_VOLTAGE -> R.string.monitor_voltage
    LiveMetric.BATTERY_CURRENT -> R.string.monitor_current_battery
    LiveMetric.PROCESS_CPU -> R.string.monitor_cpu
    LiveMetric.THERMAL_HEADROOM -> R.string.monitor_headroom
}
