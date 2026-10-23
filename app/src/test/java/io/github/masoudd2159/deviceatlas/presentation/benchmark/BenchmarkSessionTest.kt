package io.github.masoudd2159.deviceatlas.presentation.benchmark

import io.github.masoudd2159.deviceatlas.foundation.benchmark.*
import io.github.masoudd2159.deviceatlas.foundation.hardware.HardwareValue.*
import io.github.masoudd2159.deviceatlas.foundation.hardware.UnavailableReason
import io.github.masoudd2159.deviceatlas.foundation.monitoring.*
import io.github.masoudd2159.deviceatlas.presentation.monitoring.MonitoringSession
import io.github.masoudd2159.deviceatlas.presentation.monitoring.observation
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.concurrent.thread
import kotlin.coroutines.CoroutineContext

@OptIn(ExperimentalCoroutinesApi::class)
class BenchmarkSessionTest {
    private class Rig(val test: TestScope) {
        val coordinator = DiagnosticsCoordinator()
        val dispatcher = StandardTestDispatcher(test.testScheduler)
        var reading = SafetyReading(Available(75.0), Available(ThermalStatus.NONE), Available(0.2), Available(512L shl 20), Available(false))
        var calls = 0
        var active = 0
        var maxActive = 0
        var wait: (suspend () -> Unit)? = null
        var failure: BenchmarkError? = null
        var nanos = 1_000_000L
        val conservativeCalls = mutableListOf<Boolean>()
        val workloads = mutableListOf<Workload>()
        val clock = MonotonicClock { test.testScheduler.currentTime * 1_000_000 }
        val processor = object : BenchmarkProcessor {
            override suspend fun chunk(workload: Workload, conservative: Boolean): BenchmarkChunk {
                calls++; active++; maxActive = maxOf(active, maxActive)
                conservativeCalls += conservative; workloads += workload
                try {
                    wait?.invoke() ?: delay(10)
                    failure?.let { throw BenchmarkFailure(it) }
                    return BenchmarkChunk(BenchmarkRun(256, nanos), 1, 2)
                } finally { active-- }
            }
            override suspend fun summarize(workload: Workload, runs: List<BenchmarkRun>) = BenchmarkResult(
                workload, runs.toList(), runs.sumOf { it.work }, runs.sumOf { it.nanos }, 100.0, 90.0, 110.0, 20.0)
        }
        val session = newSession()
        fun newSession(scope: CoroutineScope = test.backgroundScope, owner: CoroutineDispatcher = dispatcher) =
            BenchmarkSession(BenchmarkSafetySource { reading }, processor, clock, coordinator, scope, owner)
        fun enable() = session.setEligibility(true, true)
        fun finish() { test.advanceTimeBy(2_000); test.runCurrent() }
        fun monitoring(source: MonitoringSource): MonitoringSession {
            val processor = object : MonitoringProcessor {
                override suspend fun sample(raw: RawObservation, previous: RawObservation?) = ProcessedObservation(raw, emptyMap())
                override suspend fun summarize(history: List<ProcessedObservation>) = emptyMap<LiveMetric, io.github.masoudd2159.deviceatlas.foundation.hardware.HardwareValue<MetricSummary>>()
            }
            return MonitoringSession(source, processor, clock, test.backgroundScope, dispatcher, coordinator).also { it.setEligibility(true,true) }
        }
    }

    @Test fun idleExplicitStartNoProcessResurrection() = runTest {
        val r = Rig(this)
        assertEquals(BenchmarkStatus.Idle, r.session.state.value.status)
        r.session.start(); runCurrent(); assertEquals(0,r.calls)
        r.enable(); runCurrent(); assertEquals(0,r.calls)
        r.session.start(); runCurrent(); assertEquals(1,r.calls)
        assertEquals(BenchmarkStatus.Idle,r.newSession().state.value.status)
        r.session.close(); runCurrent()
    }
    @Test fun allWorkloadsThreeActualRunsWarmupExcludedAndBoundedProgress() = runTest {
        val r = Rig(this); r.enable(); repeat(5) { r.session.start() }; r.finish()
        val state = r.session.state.value
        assertEquals(BenchmarkStatus.Completed,state.status)
        assertEquals(39,r.calls); assertEquals(1,r.maxActive)
        assertEquals(39,state.completedChunks); assertEquals(39,state.plannedChunks)
        assertEquals(9,state.completedRuns)
        assertEquals(3,state.results.size)
        state.results.forEach { assertEquals(3,it.runs.size); assertEquals(12 * 256L,it.totalWork) }
        assertEquals(Workload.entries,r.workloads.distinct())
    }
    @Test fun stopFreezesTimeAndNeverAutoResumes() = runTest {
        val r = Rig(this); r.enable(); r.session.start(); runCurrent(); advanceTimeBy(5)
        r.session.stop(); val stopped = r.session.state.value
        assertEquals(BenchmarkStatus.Stopped(BenchmarkStop.USER),stopped.status)
        assertEquals(5L,stopped.elapsedMillis)
        r.finish(); r.enable(); r.finish()
        assertEquals(1,r.calls); assertEquals(stopped.elapsedMillis,r.session.state.value.elapsedMillis)
    }
    @Test fun foregroundVisibilityAndDestructionStopActualChunks() = runTest {
        for ((visible,foreground,reason) in listOf(Triple(false,true,BenchmarkStop.HIDDEN),Triple(true,false,BenchmarkStop.BACKGROUND))) {
            val r = Rig(this); r.enable(); r.session.start(); runCurrent()
            r.session.setEligibility(visible,foreground); r.finish(); r.enable(); r.finish()
            assertEquals(1,r.calls); assertEquals(BenchmarkStatus.Stopped(reason),r.session.state.value.status)
        }
        val r = Rig(this); r.enable(); r.session.start(); runCurrent(); r.session.close(); r.finish()
        r.enable(); r.session.start(); runCurrent(); assertEquals(1,r.calls)
        assertEquals(BenchmarkStatus.Stopped(BenchmarkStop.DESTROYED),r.session.state.value.status)
    }
    @Test fun startRejectedWhileBackgrounded() = runTest {
        val r = Rig(this); r.session.setEligibility(true,false); r.session.start(); runCurrent(); assertEquals(0,r.calls)
    }
    @Test fun stopResetAndParentCancellationBeforeFirstDispatchReleaseOwnership() = runTest {
        for (reset in listOf(false,true)) {
            val r = Rig(this); r.enable(); r.session.start()
            if (reset) r.session.reset() else r.session.stop()
            runCurrent(); assertEquals(0,r.calls); assertTrue(r.session.state.value.canStart)
            r.session.start(); runCurrent(); assertEquals(1,r.calls); r.session.close(); runCurrent()
        }
        val r = Rig(this); val parent = SupervisorJob()
        val session = r.newSession(CoroutineScope(parent + r.dispatcher))
        session.setEligibility(true,true); session.start(); parent.cancel(); runCurrent()
        assertEquals(0,r.calls); assertFalse(session.state.value.canStart)
        assertTrue(r.coordinator.benchmarkAvailable())
    }
    @Test fun nativeFailureAndMalformedNumbersNeverBecomeSuccess() = runTest {
        for (error in BenchmarkError.entries) {
            val r = Rig(this); r.failure = error; r.enable(); r.session.start(); r.finish()
            assertEquals(BenchmarkStatus.Failed(error),r.session.state.value.status)
            assertTrue(r.session.state.value.results.isEmpty())
        }
        val r = Rig(this); r.nanos = 0; r.enable(); r.session.start(); r.finish()
        assertEquals(BenchmarkStatus.Failed(BenchmarkError.INVALID_RESULT),r.session.state.value.status)
    }
    @Test fun thermalStatusAndHeadroomPreflightRejection() = runTest {
        for (reading in listOf(
            SafetyReading(Available(50.0),Available(ThermalStatus.SEVERE),Available(0.1),Available(1L shl 30),Available(false)),
            SafetyReading(Available(50.0),Available(ThermalStatus.NONE),Available(1.5),Available(1L shl 30),Available(false)))) {
            val r = Rig(this); r.reading = reading; r.enable(); r.session.start(); runCurrent()
            assertEquals(BenchmarkStatus.Stopped(BenchmarkStop.THERMAL),r.session.state.value.status); assertEquals(0,r.calls)
        }
    }
    @Test fun batteryAndMemoryPreflightAndInvalidReadings() = runTest {
        val r = Rig(this)
        val cases = listOf(
            r.reading.copy(batteryPercent=Available(19.9)) to BenchmarkStatus.Stopped(BenchmarkStop.BATTERY),
            r.reading.copy(lowMemory=Available(true)) to BenchmarkStatus.Stopped(BenchmarkStop.MEMORY),
            r.reading.copy(availableBytes=Available(1)) to BenchmarkStatus.Stopped(BenchmarkStop.MEMORY),
            r.reading.copy(availableBytes=Available(-1)) to BenchmarkStatus.Failed(BenchmarkError.INVALID_RESULT),
            r.reading.copy(batteryPercent=Available(Double.NaN)) to BenchmarkStatus.Failed(BenchmarkError.INVALID_RESULT),
            r.reading.copy(headroom=Available(Double.POSITIVE_INFINITY)) to BenchmarkStatus.Failed(BenchmarkError.INVALID_RESULT))
        for ((reading,status) in cases) {
            r.reading=reading; r.enable(); r.session.start(); runCurrent()
            assertEquals(status,r.session.state.value.status); assertEquals(0,r.calls)
        }
    }
    @Test fun unavailableSensorsAndLowMemoryCapacityUseSmallerWorkWithoutInventedReadings() = runTest {
        val r = Rig(this)
        val missing = Unavailable(UnavailableReason.UNSUPPORTED)
        r.reading = SafetyReading(missing,missing,missing,missing,missing)
        r.enable(); r.session.start(); r.finish()
        assertEquals(BenchmarkStatus.Completed,r.session.state.value.status)
        assertTrue(r.session.state.value.conservative); assertEquals(12,r.calls)
        assertTrue(r.conservativeCalls.all { it }); assertEquals(missing,r.session.state.value.safety?.batteryPercent)
    }
    @Test fun thermalAndMemoryChangesStopBetweenChunks() = runTest {
        for (thermal in listOf(false,true)) {
            val r = Rig(this); r.enable(); r.session.start(); runCurrent()
            r.reading = if (thermal) r.reading.copy(thermal=Available(ThermalStatus.CRITICAL)) else r.reading.copy(lowMemory=Available(true))
            r.finish(); assertEquals(1,r.calls)
            assertEquals(BenchmarkStatus.Stopped(if (thermal) BenchmarkStop.THERMAL else BenchmarkStop.MEMORY),r.session.state.value.status)
        }
    }
    @Test fun timeoutIncludesPreflightAndOrchestrationAndRetainsCompletedPartialRuns() = runTest {
        val r = Rig(this); r.wait = { delay(4_000) }; r.enable(); r.session.start()
        advanceTimeBy(15_000); runCurrent()
        assertEquals(BenchmarkStatus.Stopped(BenchmarkStop.TIMEOUT),r.session.state.value.status)
        assertEquals(15_000L,r.session.state.value.elapsedMillis)
        assertTrue(r.session.state.value.results.isEmpty())
    }
    @Test fun partialCompletedRunSurvivesStopAndResetClearsIt() = runTest {
        val r = Rig(this); r.enable(); r.session.start(); advanceTimeBy(55); runCurrent()
        assertEquals(1,r.session.state.value.results.single().runs.size)
        r.session.stop(); runCurrent(); assertEquals(1,r.session.state.value.results.size)
        r.session.reset(); assertEquals(BenchmarkStatus.Idle,r.session.state.value.status)
        assertTrue(r.session.state.value.results.isEmpty())
    }
    @Test fun delayedNativeReturnCannotPublishOrOverlapSuccessorAndClearWaits() = runTest {
        val r = Rig(this); val release = CompletableDeferred<Unit>()
        r.wait = { withContext(NonCancellable) { release.await() } }
        r.enable(); r.session.start(); runCurrent(); advanceTimeBy(35)
        r.session.stop(); r.session.reset(); r.session.start(); runCurrent()
        assertEquals(1,r.calls); assertTrue(r.session.state.value.retiring)
        assertFalse(r.coordinator.benchmarkAvailable())
        release.complete(Unit); runCurrent()
        assertEquals(BenchmarkStatus.Idle,r.session.state.value.status); assertTrue(r.session.state.value.results.isEmpty())
        assertTrue(r.session.state.value.canStart)
        r.wait=null; r.session.start(); r.finish(); assertEquals(1,r.maxActive)
    }
    @Test fun monitoringToBenchmarkWaitsForRetiringAcquisitionAndBothDirectionsRejectOverlap() = runTest {
        val r = Rig(this); val release = CompletableDeferred<Unit>()
        var acquisitions=0; var monitoringActive=false
        val monitor = r.monitoring(MonitoringSource { sequence ->
            acquisitions++; monitoringActive=true
            try { withContext(NonCancellable) { release.await() }; observation(sequence,0) }
            finally { monitoringActive=false }
        })
        monitor.start(); runCurrent(); assertTrue(monitoringActive)
        r.enable(); r.session.start(); runCurrent(); assertEquals(0,r.calls)
        repeat(5) { monitor.start(); r.session.start() }; runCurrent(); assertEquals(1,acquisitions)
        release.complete(Unit); runCurrent(); assertFalse(monitoringActive); assertEquals(1,r.calls)
        monitor.start(); runCurrent(); assertEquals(1,acquisitions)
        val nativeRelease = CompletableDeferred<Unit>()
        r.wait = { withContext(NonCancellable) { nativeRelease.await() } }
        advanceTimeBy(10); runCurrent(); r.session.stop(); monitor.start(); runCurrent()
        assertEquals(1,acquisitions)
        nativeRelease.complete(Unit); runCurrent(); monitor.start(); runCurrent()
        assertEquals(2,acquisitions); monitor.close(); runCurrent()
    }
    @Test fun completionOnWorkerOnlyQueuesOwnerCleanupAndStaleHandoffCannotReleaseSuccessor() = runTest {
        val r = Rig(this); val execution = QueuedDispatcher(); val handoff = QueuedDispatcher(); val parent = SupervisorJob()
        val session = r.newSession(CoroutineScope(parent + execution),handoff)
        session.setEligibility(true,true); session.start(); session.stop()
        val stopped = session.state.value
        thread { execution.take().run() }.join()
        assertEquals(stopped,session.state.value); assertFalse(r.coordinator.benchmarkAvailable())
        val cleanup = handoff.take(); cleanup.run(); assertTrue(session.state.value.canStart)
        session.start(); cleanup.run(); assertFalse(r.coordinator.benchmarkAvailable())
        assertEquals(BenchmarkStatus.Active,session.state.value.status)
        session.close(); execution.take().run(); handoff.take().run(); parent.cancel()
        assertTrue(r.coordinator.benchmarkAvailable())
    }
    @Test fun monotonicDeadlineRejectsRegressionAndLargeJump() = runTest {
        val r=Rig(this); var now=0L
        val session=BenchmarkSession(BenchmarkSafetySource { r.reading },r.processor,MonotonicClock { now },r.coordinator,backgroundScope,r.dispatcher)
        session.setEligibility(true,true); session.start(); now=Long.MAX_VALUE; runCurrent()
        assertEquals(BenchmarkStatus.Stopped(BenchmarkStop.TIMEOUT),session.state.value.status); assertEquals(0,r.calls)
        now=100; session.start(); now=1; runCurrent()
        assertEquals(BenchmarkStatus.Stopped(BenchmarkStop.TIMEOUT),session.state.value.status)
    }
    @Test fun newActivityOwnerCannotOverlapOldOwnersRetiringNativeWork() = runTest {
        val r=Rig(this); val release=CompletableDeferred<Unit>()
        r.wait={ withContext(NonCancellable) { release.await() } }
        r.enable(); r.session.start(); runCurrent(); r.session.close()
        val successor=r.newSession(); successor.setEligibility(true,true); successor.start(); runCurrent()
        assertEquals(1,r.calls); assertEquals(BenchmarkStatus.Idle,successor.state.value.status)
        release.complete(Unit); runCurrent(); assertTrue(successor.state.value.canStart)
        r.wait=null; successor.start(); r.finish(); assertEquals(1,r.maxActive)
    }
    @Test fun initialDispatchDelayConsumesTheWholeSessionBudgetIncludingRetirementWait() = runTest {
        val r=Rig(this)
        val lease=checkNotNull(r.coordinator.acquireMonitoring())
        var clockOffset=0L
        val session=BenchmarkSession(BenchmarkSafetySource { r.reading },r.processor,
            MonotonicClock { testScheduler.currentTime*1_000_000+clockOffset },r.coordinator,backgroundScope,r.dispatcher)
        session.setEligibility(true,true); session.start()
        clockOffset=14_000_000_000; runCurrent()
        advanceTimeBy(1_000); runCurrent()
        assertEquals(BenchmarkStatus.Stopped(BenchmarkStop.TIMEOUT),session.state.value.status)
        assertEquals(15_000L,session.state.value.elapsedMillis)
        assertEquals(0,r.calls); r.coordinator.releaseMonitoring(lease)
    }
    @Test fun cumulativeMeasuredWorkloadDurationCannotExceedTwoSeconds() = runTest {
        val r=Rig(this); r.nanos=600_000_000; r.enable(); r.session.start(); r.finish()
        assertEquals(BenchmarkStatus.Stopped(BenchmarkStop.TIMEOUT),r.session.state.value.status)
        assertTrue(r.session.state.value.results.isEmpty())
    }
    private class QueuedDispatcher : CoroutineDispatcher() {
        private val queue=ConcurrentLinkedQueue<Runnable>()
        override fun dispatch(context: CoroutineContext,block: Runnable) { queue.add(block) }
        fun take(): Runnable = checkNotNull(queue.poll())
    }
}
