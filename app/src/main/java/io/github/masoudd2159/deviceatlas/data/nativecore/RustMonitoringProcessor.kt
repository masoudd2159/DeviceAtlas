package io.github.masoudd2159.deviceatlas.data.nativecore

import io.github.masoudd2159.deviceatlas.di.NativeDispatcher
import io.github.masoudd2159.deviceatlas.foundation.hardware.*
import io.github.masoudd2159.deviceatlas.foundation.hardware.HardwareValue.*
import io.github.masoudd2159.deviceatlas.foundation.monitoring.*
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

class RustMonitoringProcessor internal constructor(
    private val dispatcher: CoroutineDispatcher, private val load: () -> Unit,
    private val invoke: (LongArray, DoubleArray, DoubleArray) -> Int,
) : MonitoringProcessor {
    @Inject constructor(@NativeDispatcher dispatcher: CoroutineDispatcher) : this(
        dispatcher, { System.loadLibrary("deviceatlas_core") }, NativeBridge::processMonitoring)

    override suspend fun sample(raw: RawObservation, previous: RawObservation?): ProcessedObservation {
        fun HardwareValue<Long>?.wire() = (this as? Available)?.value ?: Long.MIN_VALUE
        val input = longArrayOf(1, 1, 1, raw.monotonicNanos, raw.cpuMillis.wire(), raw.totalBytes.wire(),
            raw.availableBytes.wire(), raw.thresholdBytes.wire(), raw.batteryLevel.wire(), raw.batteryScale.wire(),
            raw.temperatureTenths.wire(), raw.voltageMillivolts.wire(), raw.currentMicroamps.wire(),
            previous?.monotonicNanos ?: Long.MIN_VALUE, previous?.cpuMillis.wire())
        val result = call(input, doubleArrayOf((raw.headroom as? Available)?.value ?: 0.0), 20)
        val dependencies = mapOf(
            LiveMetric.TOTAL_RAM to listOf(raw.totalBytes),
            LiveMetric.AVAILABLE_RAM to listOf(raw.totalBytes, raw.availableBytes),
            LiveMetric.AVAILABLE_PERCENT to listOf(raw.totalBytes, raw.availableBytes),
            LiveMetric.MEMORY_THRESHOLD to listOf(raw.thresholdBytes),
            LiveMetric.BATTERY_LEVEL to listOf(raw.batteryLevel, raw.batteryScale),
            LiveMetric.BATTERY_TEMPERATURE to listOf(raw.temperatureTenths),
            LiveMetric.BATTERY_VOLTAGE to listOf(raw.voltageMillivolts),
            LiveMetric.BATTERY_CURRENT to listOf(raw.currentMicroamps),
            LiveMetric.PROCESS_CPU to listOf(raw.cpuMillis), LiveMetric.THERMAL_HEADROOM to listOf(raw.headroom))
        return ProcessedObservation(raw, immutableMap(LiveMetric.entries.associateWith { metric ->
            dependencies.getValue(metric).filterIsInstance<Unavailable>().firstOrNull() ?: when (result) {
                is Unavailable -> result
                is Available -> {
                    val offset = metric.wire * 2
                    when (result.value[offset]) {
                        0.0 -> Available(result.value[offset + 1])
                        6.0 -> Unavailable(UnavailableReason.INVALID_RESPONSE)
                        9.0 -> Unavailable(UnavailableReason.BASELINE)
                        else -> Unavailable(UnavailableReason.NATIVE_FAILED)
                    }
                }
            }
        }))
    }

    override suspend fun summarize(history: List<ProcessedObservation>): Map<LiveMetric, HardwareValue<MetricSummary>> {
        if (history.size > HISTORY_LIMIT) return LiveMetric.entries.associateWith { Unavailable(UnavailableReason.INVALID_RESPONSE) }
        val input = LongArray(3 + history.size)
        input[0] = 1; input[1] = 2; input[2] = history.size.toLong()
        val real = DoubleArray(history.size * 10)
        history.forEachIndexed { index, sample -> LiveMetric.entries.forEach { metric ->
            val v = sample.values[metric]
            if (v is Available) {
                input[3 + index] = input[3 + index] or (1L shl metric.wire)
                real[index * 10 + metric.wire] = v.value
            }
        } }
        val result = call(input, real, 50)
        return immutableMap(LiveMetric.entries.associateWith { metric -> when (result) {
            is Unavailable -> result
            is Available -> {
                val o = metric.wire * 5
                val v = result.value
                val count = v[o + 4]
                when {
                    count == 0.0 -> Unavailable(UnavailableReason.INSUFFICIENT)
                    count < 0 || count > history.size || count % 1.0 != 0.0 || v[o] > v[o + 1] ||
                        v[o + 2] !in v[o]..v[o + 1] || v[o + 3] !in v[o]..v[o + 1] -> Unavailable(UnavailableReason.NATIVE_FAILED)
                    else -> Available(MetricSummary(v[o], v[o + 1], v[o + 2], v[o + 3], count.toInt()))
                }
            }
        } })
    }

    private suspend fun call(input: LongArray, real: DoubleArray, size: Int): HardwareValue<DoubleArray> = withContext(dispatcher) {
        try {
            coroutineContext.ensureActive(); load()
            val output = DoubleArray(size) { Double.NaN }
            val status = invoke(input, real, output)
            coroutineContext.ensureActive()
            when {
                status == 6 -> Unavailable(UnavailableReason.INVALID_RESPONSE)
                status != 0 || output.any { !it.isFinite() } -> Unavailable(UnavailableReason.NATIVE_FAILED)
                else -> Available(output)
            }
        } catch (cancelled: CancellationException) { throw cancelled
        } catch (_: LinkageError) { Unavailable(UnavailableReason.NATIVE_UNAVAILABLE)
        } catch (_: SecurityException) { Unavailable(UnavailableReason.NATIVE_UNAVAILABLE)
        } catch (_: RuntimeException) { Unavailable(UnavailableReason.NATIVE_FAILED) }
    }
}
