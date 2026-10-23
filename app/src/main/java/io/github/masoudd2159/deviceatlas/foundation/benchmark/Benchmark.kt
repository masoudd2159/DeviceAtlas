package io.github.masoudd2159.deviceatlas.foundation.benchmark

import io.github.masoudd2159.deviceatlas.foundation.hardware.HardwareValue
import io.github.masoudd2159.deviceatlas.foundation.monitoring.ThermalStatus

const val BENCHMARK_LIMIT_MS = 15_000L
enum class Workload(val wire: Long) { INTEGER(1), FLOATING(2), MEMORY(3) }
enum class BenchmarkPhase { CHECKING, WARMUP, MEASURING }
enum class BenchmarkStop { USER, HIDDEN, BACKGROUND, DESTROYED, TIMEOUT, THERMAL, BATTERY, MEMORY, CANCELED }
enum class BenchmarkError { NATIVE_UNAVAILABLE, NATIVE_FAILED, INVALID_RESULT, SAFETY_UNAVAILABLE }
sealed interface BenchmarkStatus {
    data object Idle : BenchmarkStatus
    data object Active : BenchmarkStatus
    data object Completed : BenchmarkStatus
    data class Stopped(val reason: BenchmarkStop) : BenchmarkStatus
    data class Failed(val reason: BenchmarkError) : BenchmarkStatus
}
data class SafetyReading(
    val batteryPercent: HardwareValue<Double>, val thermal: HardwareValue<ThermalStatus>,
    val headroom: HardwareValue<Double>, val availableBytes: HardwareValue<Long>,
    val lowMemory: HardwareValue<Boolean>,
)
data class BenchmarkRun(val work: Long, val nanos: Long)
data class BenchmarkChunk(val run: BenchmarkRun, val checksumHigh: Long, val checksumLow: Long)
data class BenchmarkResult(
    val workload: Workload, val runs: List<BenchmarkRun>, val totalWork: Long, val activeNanos: Long,
    val median: Double, val minimum: Double, val maximum: Double, val variationPercent: Double,
)
data class BenchmarkState(
    val status: BenchmarkStatus = BenchmarkStatus.Idle, val phase: BenchmarkPhase = BenchmarkPhase.CHECKING,
    val workload: Workload? = null, val completedChunks: Int = 0, val plannedChunks: Int = 0,
    val completedRuns: Int = 0, val startedAtNanos: Long? = null, val elapsedMillis: Long = 0,
    val safety: SafetyReading? = null, val conservative: Boolean = false,
    val results: List<BenchmarkResult> = emptyList(), val canStart: Boolean = false,
    val retiring: Boolean = false,
)
fun interface BenchmarkSafetySource { suspend fun read(): SafetyReading }
interface BenchmarkProcessor {
    suspend fun chunk(workload: Workload, conservative: Boolean): BenchmarkChunk
    suspend fun summarize(workload: Workload, runs: List<BenchmarkRun>): BenchmarkResult
}
class BenchmarkFailure(val reason: BenchmarkError) : RuntimeException()
