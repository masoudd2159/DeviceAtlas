package io.github.masoudd2159.deviceatlas.foundation.monitoring

import io.github.masoudd2159.deviceatlas.foundation.hardware.HardwareValue
import io.github.masoudd2159.deviceatlas.foundation.hardware.UnavailableReason

const val SAMPLE_INTERVAL_MS = 5_000L
const val SESSION_LIMIT_MS = 600_000L
const val HISTORY_LIMIT = 120

/** Wire order is explicit and independent of enum ordinals. Display units from Rust. */
enum class LiveMetric(val wire: Int) {
    TOTAL_RAM(0), AVAILABLE_RAM(1), AVAILABLE_PERCENT(2), MEMORY_THRESHOLD(3),
    BATTERY_LEVEL(4), BATTERY_TEMPERATURE(5), BATTERY_VOLTAGE(6), BATTERY_CURRENT(7),
    PROCESS_CPU(8), THERMAL_HEADROOM(9),
}

/** APIs are read sequentially between monotonicNanos and completedNanos (snapshot skew).
 * Numeric fields retain Android raw units. No measurements enter SavedStateHandle. */
data class RawObservation(
    val sequence: Long, val monotonicNanos: Long, val completedNanos: Long, val wallMillis: Long,
    val cpuMillis: HardwareValue<Long>, val totalBytes: HardwareValue<Long>,
    val availableBytes: HardwareValue<Long>, val thresholdBytes: HardwareValue<Long>,
    val batteryLevel: HardwareValue<Long>, val batteryScale: HardwareValue<Long>,
    val temperatureTenths: HardwareValue<Long>, val voltageMillivolts: HardwareValue<Long>,
    val currentMicroamps: HardwareValue<Long>, val headroom: HardwareValue<Double>,
    val lowMemory: HardwareValue<Boolean>, val chargingStatus: HardwareValue<ChargingStatus>,
    val thermalStatus: HardwareValue<ThermalStatus>,
)

enum class ThermalStatus { NONE, LIGHT, MODERATE, SEVERE, CRITICAL, EMERGENCY, SHUTDOWN }
enum class ChargingStatus { CHARGING, DISCHARGING, FULL, NOT_CHARGING }
enum class StopReason { USER, HIDDEN, BACKGROUND, DESTROYED, LIMIT, CANCELED }
sealed interface MonitoringStatus {
    data object Idle : MonitoringStatus
    data object Running : MonitoringStatus
    data class Stopped(val reason: StopReason) : MonitoringStatus
    data class Failed(val reason: UnavailableReason) : MonitoringStatus
}
data class ProcessedObservation(val raw: RawObservation, val values: Map<LiveMetric, HardwareValue<Double>>)
data class MetricSummary(val minimum: Double, val maximum: Double, val average: Double, val latest: Double, val count: Int)
data class MonitoringState(
    val status: MonitoringStatus = MonitoringStatus.Idle,
    val history: List<ProcessedObservation> = emptyList(),
    val summaries: Map<LiveMetric, HardwareValue<MetricSummary>> = emptyMap(),
    val elapsedMillis: Long = 0, val canStart: Boolean = false, val cleared: Boolean = false,
)
fun interface MonitoringSource { suspend fun collect(sequence: Long): RawObservation }
interface MonitoringProcessor {
    suspend fun sample(raw: RawObservation, previous: RawObservation?): ProcessedObservation
    suspend fun summarize(history: List<ProcessedObservation>): Map<LiveMetric, HardwareValue<MetricSummary>>
}
fun interface MonotonicClock { fun nanos(): Long }
