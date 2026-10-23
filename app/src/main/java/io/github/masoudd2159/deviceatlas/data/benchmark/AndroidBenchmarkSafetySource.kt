package io.github.masoudd2159.deviceatlas.data.benchmark

import io.github.masoudd2159.deviceatlas.foundation.benchmark.*
import io.github.masoudd2159.deviceatlas.foundation.hardware.HardwareValue.*
import io.github.masoudd2159.deviceatlas.foundation.hardware.UnavailableReason
import io.github.masoudd2159.deviceatlas.foundation.monitoring.MonitoringSource
import javax.inject.Inject

/** Reuses the public platform reader, not the Phase 3 session state machine. */
class AndroidBenchmarkSafetySource @Inject constructor(private val source: MonitoringSource) : BenchmarkSafetySource {
    override suspend fun read(): SafetyReading {
        val raw = source.collect(0)
        val level = raw.batteryLevel
        val scale = raw.batteryScale
        val battery = when {
            level is Unavailable -> level
            scale is Unavailable -> scale
            level is Available && scale is Available && scale.value > 0 && level.value in 0..scale.value ->
                Available(level.value.toDouble() / scale.value * 100.0)
            else -> Unavailable(UnavailableReason.INVALID_RESPONSE)
        }
        return SafetyReading(battery, raw.thermalStatus, raw.headroom, raw.availableBytes, raw.lowMemory)
    }
}
