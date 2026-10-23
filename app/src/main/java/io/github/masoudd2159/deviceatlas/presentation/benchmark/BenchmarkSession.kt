package io.github.masoudd2159.deviceatlas.presentation.benchmark

import androidx.annotation.MainThread
import io.github.masoudd2159.deviceatlas.foundation.benchmark.*
import io.github.masoudd2159.deviceatlas.foundation.hardware.HardwareValue.*
import io.github.masoudd2159.deviceatlas.foundation.hardware.immutableList
import io.github.masoudd2159.deviceatlas.foundation.monitoring.MonotonicClock
import io.github.masoudd2159.deviceatlas.foundation.monitoring.ThermalStatus
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlin.coroutines.EmptyCoroutineContext

/** Main-confined orchestration; retiring ownership includes synchronous JNI.
 * No lifecycle event, completion callback, or result can restart work. */
@MainThread
class BenchmarkSession(
    private val safety: BenchmarkSafetySource, private val processor: BenchmarkProcessor,
    private val clock: MonotonicClock, private val coordinator: DiagnosticsCoordinator,
    private val scope: CoroutineScope, private val ownerDispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
) {
    private val mutable = MutableStateFlow(BenchmarkState())
    val state = mutable.asStateFlow()
    private var job: Job? = null
    private var generation = 0L
    private var eligible = false
    private var closed = false
    private var clearPending = false
    private var started = 0L
    private val ownershipChanged: () -> Unit = { publishEligibility() }
    init { coordinator.observe(ownershipChanged) }

    fun setEligibility(visible: Boolean, foreground: Boolean, destroyed: Boolean = false) {
        eligible = !closed && visible && foreground
        if (!eligible) stop(when { destroyed -> BenchmarkStop.DESTROYED; !visible -> BenchmarkStop.HIDDEN; else -> BenchmarkStop.BACKGROUND })
        publishEligibility()
    }
    fun start() {
        if (!eligible || closed || job != null || !scope.isActive) return
        val lease = coordinator.reserveBenchmark { stop(BenchmarkStop.HIDDEN) } ?: return
        val token = ++generation
        started = clock.nanos()
        clearPending = false
        mutable.value = BenchmarkState(status = BenchmarkStatus.Active, startedAtNanos = started)
        val created = scope.launch(start = CoroutineStart.LAZY) {
            try {
                withTimeout((BENCHMARK_LIMIT_MS - elapsed()).coerceAtLeast(1)) {
                    gate(token)
                    coordinator.awaitMonitoringRetired()
                    gate(token)
                    val initial = checkSafety(token)
                    val conservative = initial.batteryPercent !is Available || initial.thermal !is Available ||
                        initial.headroom !is Available || initial.availableBytes !is Available || initial.lowMemory !is Available ||
                        initial.availableBytes.value < 128L * 1024 * 1024
                    val chunksPerRun = if (conservative) 1 else 4
                    mutable.value = mutable.value.copy(conservative = conservative, plannedChunks = 3 * (1 + 3 * chunksPerRun))
                    for (workload in Workload.entries) {
                        gate(token)
                        mutable.value = mutable.value.copy(workload = workload, phase = BenchmarkPhase.WARMUP)
                        checkSafety(token)
                        processor.chunk(workload, mutable.value.conservative) // one bounded warmup; never counted
                        gate(token); progress()
                        mutable.value = mutable.value.copy(phase = BenchmarkPhase.MEASURING)
                        val runs = mutableListOf<BenchmarkRun>()
                        repeat(3) {
                            var work = 0L
                            var nanos = 0L
                            repeat(chunksPerRun) {
                                checkSafety(token)
                                val chunk = processor.chunk(workload, mutable.value.conservative)
                                gate(token)
                                if (chunk.run.work <= 0 || chunk.run.nanos <= 0) throw BenchmarkFailure(BenchmarkError.INVALID_RESULT)
                                work = Math.addExact(work, chunk.run.work)
                                nanos = Math.addExact(nanos, chunk.run.nanos)
                                if (nanos + runs.sumOf { it.nanos } > 2_000_000_000L) throw SafetyStop(BenchmarkStop.TIMEOUT)
                                progress()
                                yield()
                            }
                            runs += BenchmarkRun(work, nanos)
                            val result = processor.summarize(workload, immutableList(runs))
                            gate(token)
                            val results = mutable.value.results.filterNot { it.workload == workload } + result
                            mutable.value = mutable.value.copy(results = immutableList(results), completedRuns = mutable.value.completedRuns + 1)
                        }
                    }
                    checkSafety(token)
                    finish(BenchmarkStatus.Completed)
                }
            } catch (_: TimeoutCancellationException) { if (token == generation) finish(BenchmarkStatus.Stopped(BenchmarkStop.TIMEOUT))
            } catch (cancelled: CancellationException) {
                if (token == generation) finish(BenchmarkStatus.Stopped(BenchmarkStop.CANCELED))
                throw cancelled
            } catch (stop: SafetyStop) { if (token == generation) finish(BenchmarkStatus.Stopped(stop.reason))
            } catch (failure: BenchmarkFailure) { if (token == generation) finish(BenchmarkStatus.Failed(failure.reason))
            } catch (_: LinkageError) { if (token == generation) finish(BenchmarkStatus.Failed(BenchmarkError.NATIVE_UNAVAILABLE))
            } catch (_: RuntimeException) { if (token == generation) finish(BenchmarkStatus.Failed(BenchmarkError.SAFETY_UNAVAILABLE)) }
        }
        job = created
        created.invokeOnCompletion {
            // Do not launch cleanup into a canceled ViewModel scope.
            ownerDispatcher.dispatch(EmptyCoroutineContext, Runnable {
                if (job === created) {
                    if (mutable.value.status == BenchmarkStatus.Active) finish(BenchmarkStatus.Stopped(BenchmarkStop.CANCELED))
                    job = null
                    coordinator.releaseBenchmark(lease)
                    if (clearPending) { mutable.value = BenchmarkState(); clearPending = false }
                    publishEligibility()
                }
            })
        }
        created.start()
    }
    fun stop(reason: BenchmarkStop = BenchmarkStop.USER) {
        if (mutable.value.status == BenchmarkStatus.Active) {
            ++generation
            finish(BenchmarkStatus.Stopped(reason))
            job?.cancel()
        }
        publishEligibility()
    }
    fun reset() {
        stop()
        if (job == null) mutable.value = BenchmarkState() else clearPending = true
        publishEligibility()
    }
    fun close() { closed = true; eligible = false; stop(BenchmarkStop.DESTROYED); coordinator.removeObserver(ownershipChanged) }
    private suspend fun gate(token: Long) {
        currentCoroutineContext().ensureActive()
        if (token != generation || !eligible || closed) throw CancellationException()
        val now = clock.nanos()
        if (started < 0 || now < started || elapsed() >= BENCHMARK_LIMIT_MS) throw SafetyStop(BenchmarkStop.TIMEOUT)
    }
    private suspend fun checkSafety(token: Long): SafetyReading {
        gate(token)
        val reading = safety.read()
        gate(token)
        mutable.value = mutable.value.copy(safety = reading, elapsedMillis = elapsed())
        val thermal = (reading.thermal as? Available)?.value
        val headroom = (reading.headroom as? Available)?.value
        val battery = (reading.batteryPercent as? Available)?.value
        val memory = (reading.availableBytes as? Available)?.value
        if (battery == null || thermal == null || headroom == null || memory == null ||
            reading.lowMemory !is Available || memory < 128L * 1024 * 1024) {
            mutable.value = mutable.value.copy(conservative = true)
        }
        if (headroom != null && (!headroom.isFinite() || headroom < 0) ||
            battery != null && (!battery.isFinite() || battery !in 0.0..100.0) || memory != null && memory <= 0) {
            throw BenchmarkFailure(BenchmarkError.INVALID_RESULT)
        }
        if (thermal != null && thermal >= ThermalStatus.SEVERE || headroom != null && headroom >= 1.0) throw SafetyStop(BenchmarkStop.THERMAL)
        if (battery != null && battery < 20.0) throw SafetyStop(BenchmarkStop.BATTERY)
        if ((reading.lowMemory as? Available)?.value == true || memory != null && memory < 32L * 1024 * 1024) throw SafetyStop(BenchmarkStop.MEMORY)
        return reading
    }
    private fun elapsed(): Long {
        val now = clock.nanos()
        return if (started >= 0 && now >= started) ((now - started) / 1_000_000).coerceIn(mutable.value.elapsedMillis, BENCHMARK_LIMIT_MS)
            else mutable.value.elapsedMillis
    }
    private fun progress() { mutable.value = mutable.value.copy(completedChunks = mutable.value.completedChunks + 1, elapsedMillis = elapsed()) }
    private fun finish(status: BenchmarkStatus) { mutable.value = mutable.value.copy(status = status, elapsedMillis = elapsed()) }
    private fun publishEligibility() { mutable.value = mutable.value.copy(
        canStart = !closed && eligible && job == null && scope.isActive && coordinator.benchmarkAvailable(),
        retiring = job != null && mutable.value.status != BenchmarkStatus.Active) }
    private class SafetyStop(val reason: BenchmarkStop) : RuntimeException()
}
