package io.github.masoudd2159.deviceatlas.presentation.monitoring

import io.github.masoudd2159.deviceatlas.foundation.hardware.*
import io.github.masoudd2159.deviceatlas.foundation.hardware.HardwareValue.*
import io.github.masoudd2159.deviceatlas.foundation.monitoring.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.concurrent.thread
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

@OptIn(ExperimentalCoroutinesApi::class)
class MonitoringSessionTest {
    private class Rig(val test: TestScope) {
        var calls = 0
        var active = 0
        var maximumActive = 0
        var wait: (suspend () -> Unit)? = null
        var nativeWait: (suspend () -> Unit)? = null
        var processed: HardwareValue<Double> = Available(1.0)
        var throwSource: RuntimeException? = null
        var timestampOffset = 0L
        val source = MonitoringSource { sequence ->
            calls++; active++; maximumActive = maxOf(active, maximumActive)
            try {
                wait?.invoke()
                throwSource?.let { throw it }
                observation(sequence, test.testScheduler.currentTime * 1_000_000 + timestampOffset)
            } finally { active-- }
        }
        val processor = object : MonitoringProcessor {
            override suspend fun sample(raw: RawObservation, previous: RawObservation?): ProcessedObservation {
                nativeWait?.invoke()
                return ProcessedObservation(raw, immutableMap(LiveMetric.entries.associateWith { processed }))
            }
            override suspend fun summarize(history: List<ProcessedObservation>) =
                LiveMetric.entries.associateWith { Unavailable(UnavailableReason.INSUFFICIENT) }
        }
        val session = MonitoringSession(source, processor, MonotonicClock { test.testScheduler.currentTime * 1_000_000 }, test.backgroundScope,
            StandardTestDispatcher(test.testScheduler))
        fun enable() = session.setEligibility(true, true)
    }

    @Test fun idleAndProcessRecreationNeverAutoStart() = runTest {
        val r = Rig(this)
        assertEquals(MonitoringStatus.Idle, r.session.state.value.status)
        r.session.start(); runCurrent(); assertEquals(0, r.calls)
        r.enable(); runCurrent(); assertEquals(0, r.calls)
        assertTrue(r.session.state.value.canStart)
        assertTrue(Rig(this).session.state.value.history.isEmpty())
    }
    @Test fun explicitStartDuplicateTapsStopAndNoAutomaticResume() = runTest {
        val r = Rig(this); r.enable()
        repeat(8) { r.session.start() }; runCurrent()
        assertEquals(1, r.calls); assertEquals(MonitoringStatus.Running, r.session.state.value.status)
        advanceTimeBy(5_000); runCurrent(); assertEquals(2, r.calls)
        r.session.stop(); runCurrent()
        advanceTimeBy(20_000); runCurrent(); assertEquals(2, r.calls)
        assertEquals(2, r.session.state.value.history.size)
        assertEquals(MonitoringStatus.Stopped(StopReason.USER), r.session.state.value.status)
    }
    @Test fun stopBetweenSamplesFreezesActualMonotonicDurationAndRepeatedStopIsStable() = runTest {
        val r = Rig(this); r.enable(); r.session.start(); runCurrent()
        advanceTimeBy(3_500)
        r.session.stop()
        assertEquals(3_500L, r.session.state.value.elapsedMillis)
        val stopped = r.session.state.value
        advanceTimeBy(20_000); runCurrent(); r.session.stop()
        assertEquals(stopped.elapsedMillis, r.session.state.value.elapsedMillis)
        assertEquals(1, r.calls)
    }
    @Test fun eligibilityStopsFreezeTransitionTimeAndNeverResumeAcquisition() = runTest {
        for ((visible, foreground, reason) in listOf(Triple(false, true, StopReason.HIDDEN), Triple(true, false, StopReason.BACKGROUND))) {
            val r = Rig(this); r.enable(); r.session.start(); runCurrent()
            advanceTimeBy(3_500)
            r.session.setEligibility(visible, foreground)
            assertEquals(MonitoringStatus.Stopped(reason), r.session.state.value.status)
            assertEquals(3_500L, r.session.state.value.elapsedMillis)
            advanceTimeBy(20_000); runCurrent(); r.enable()
            assertEquals(3_500L, r.session.state.value.elapsedMillis)
            assertEquals(1, r.calls)
        }
    }
    @Test fun resetClearsFrozenTimeAndRestartUsesNewBaseline() = runTest {
        val r = Rig(this); r.enable(); r.session.start(); runCurrent()
        advanceTimeBy(3_500); r.session.stop(); runCurrent()
        r.session.reset()
        assertEquals(0L, r.session.state.value.elapsedMillis)
        advanceTimeBy(20_000); r.session.start(); runCurrent()
        advanceTimeBy(2_250); r.session.stop()
        assertEquals(2_250L, r.session.state.value.elapsedMillis)
        assertEquals(0L, Rig(this).session.state.value.elapsedMillis)
    }
    @Test fun foregroundAndNavigationGatesStopAndRequireNewStart() = runTest {
        for ((visible,foreground,reason) in listOf(Triple(false,true,StopReason.HIDDEN), Triple(true,false,StopReason.BACKGROUND))) {
            val r = Rig(this); r.enable(); r.session.start(); runCurrent()
            r.session.setEligibility(visible, foreground); runCurrent()
            val count = r.calls
            advanceTimeBy(10_000); runCurrent(); assertEquals(count, r.calls)
            assertEquals(MonitoringStatus.Stopped(reason), r.session.state.value.status)
            r.enable(); advanceTimeBy(10_000); runCurrent(); assertEquals(count, r.calls)
        }
    }
    @Test fun resetStopsBeforeClearingAndNewStartHasFreshBaseline() = runTest {
        val r = Rig(this); r.enable(); r.session.start(); runCurrent()
        r.session.reset(); runCurrent(); advanceTimeBy(10_000); runCurrent()
        assertTrue(r.session.state.value.history.isEmpty())
        assertTrue(r.session.state.value.cleared)
        r.session.start(); runCurrent()
        assertEquals(0L, r.session.state.value.history.single().raw.sequence)
    }
    @Test fun stopOrResetBeforeFirstDispatchDoesNotStrandJobOwnership() = runTest {
        for (reset in listOf(false, true)) {
            val r = Rig(this); r.enable(); r.session.start()
            if (reset) r.session.reset() else r.session.stop()
            runCurrent()
            assertEquals(0, r.calls)
            assertTrue(r.session.state.value.canStart)
            r.session.start(); runCurrent()
            assertEquals(1, r.calls)
            r.session.stop(); runCurrent()
        }
    }
    @Test fun stopsAtTenMinutesAndRetainsAtMost120Samples() = runTest {
        val r = Rig(this); r.enable(); r.session.start()
        advanceTimeBy(600_000); runCurrent()
        assertEquals(120, r.calls)
        assertEquals(120, r.session.state.value.history.size)
        assertEquals(MonitoringStatus.Stopped(StopReason.LIMIT), r.session.state.value.status)
        assertEquals(600_000L, r.session.state.value.elapsedMillis)
        advanceTimeBy(60_000); runCurrent(); assertEquals(120, r.calls)
    }
    @Test fun uninterruptibleAcquisitionCannotPublishAfterStopOrOverlapRestart() = runTest {
        val r = Rig(this); r.enable()
        val release = CompletableDeferred<Unit>()
        r.wait = { withContext(NonCancellable) { release.await() } }
        r.session.start(); runCurrent(); assertEquals(1, r.calls)
        advanceTimeBy(3_500)
        r.session.stop(); r.session.start(); runCurrent(); assertEquals(1, r.calls)
        assertEquals(3_500L, r.session.state.value.elapsedMillis)
        advanceTimeBy(20_000)
        release.complete(Unit); runCurrent()
        assertEquals(3_500L, r.session.state.value.elapsedMillis)
        assertTrue(r.session.state.value.history.isEmpty())
        assertTrue(r.session.state.value.canStart)
        r.wait = null; r.session.start(); runCurrent()
        assertEquals(2, r.calls); assertEquals(1, r.maximumActive)
    }
    @Test fun slowSamplesDelayInsteadOfCreatingBacklog() = runTest {
        val r = Rig(this); r.enable(); r.wait = { delay(8_000) }
        r.session.start(); advanceTimeBy(13_000); runCurrent()
        assertEquals(2, r.calls); assertEquals(1, r.maximumActive)
        assertEquals(1, r.session.state.value.history.size)
    }
    @Test fun lateNativeResultIsDiscardedAfterReset() = runTest {
        val r = Rig(this); r.enable()
        val release = CompletableDeferred<Unit>()
        r.nativeWait = { withContext(NonCancellable) { release.await() } }
        r.session.start(); runCurrent()
        r.session.reset(); release.complete(Unit); runCurrent()
        assertEquals(MonitoringStatus.Idle, r.session.state.value.status)
        assertTrue(r.session.state.value.history.isEmpty())
        assertTrue(r.session.state.value.summaries.isEmpty())
    }
    @Test fun nativeUnavailableAndNumericFailuresAreTyped() = runTest {
        for (reason in listOf(UnavailableReason.NATIVE_UNAVAILABLE, UnavailableReason.NATIVE_FAILED)) {
            val r = Rig(this); r.processed = Unavailable(reason); r.enable(); r.session.start(); runCurrent()
            assertEquals(MonitoringStatus.Failed(reason), r.session.state.value.status)
            assertTrue(r.session.state.value.history.isEmpty())
        }
    }
    @Test fun allMissingSamplesStopAfterThreeAttempts() = runTest {
        val r = Rig(this); r.processed = Unavailable(UnavailableReason.NOT_REPORTED); r.enable(); r.session.start()
        advanceTimeBy(10_000); runCurrent()
        assertEquals(3, r.calls)
        assertEquals(MonitoringStatus.Failed(UnavailableReason.COLLECTION_FAILED), r.session.state.value.status)
        assertEquals(2, r.session.state.value.history.size)
    }
    @Test fun invalidTimestampsFailWithoutFabricatedSample() = runTest {
        val r = Rig(this); r.timestampOffset = -1; r.enable(); r.session.start(); runCurrent()
        assertEquals(MonitoringStatus.Failed(UnavailableReason.INVALID_RESPONSE), r.session.state.value.status)
    }
    @Test fun acquisitionExceptionStopsAndCancellationIsNotFailure() = runTest {
        val r = Rig(this); r.throwSource = IllegalStateException(); r.enable(); r.session.start(); runCurrent()
        assertEquals(MonitoringStatus.Failed(UnavailableReason.COLLECTION_FAILED), r.session.state.value.status)
        r.throwSource = CancellationException(); r.session.start(); runCurrent()
        assertEquals(MonitoringStatus.Stopped(StopReason.CANCELED), r.session.state.value.status)
    }

    @Test fun completionOnWorkerOnlyEnqueuesCleanupAndPublicationStaysOnOwner() = runTest {
        val execution = QueuedDispatcher()
        val ownerJob = SupervisorJob()
        val owner = Thread.currentThread()
        val publicationThreads = mutableListOf<Thread>()
        var calls = 0
        val r = Rig(this)
        val session = MonitoringSession(MonitoringSource { calls++; observation() }, r.processor,
            MonotonicClock { 0 }, CoroutineScope(ownerJob + execution), StandardTestDispatcher(testScheduler))
        session.state.onEach { publicationThreads += Thread.currentThread() }
            .launchIn(backgroundScope + UnconfinedTestDispatcher(testScheduler))
        session.setEligibility(true, true); session.start(); session.stop()
        val stopped = session.state.value
        // Execute the canceled initial continuation on a real worker: no sampler
        // body is entered, but its completion handler really runs on that worker.
        val failure = java.util.concurrent.atomic.AtomicReference<Throwable?>()
        thread(name = "monitoring-completion-worker") {
            try { execution.take().run() } catch (error: Throwable) { failure.set(error) }
        }.join()
        assertNull(failure.get())
        assertEquals(stopped, session.state.value)
        assertFalse(session.state.value.canStart)
        assertEquals(0, calls)
        assertTrue(publicationThreads.all { it === owner })
        runCurrent()
        assertTrue(session.state.value.canStart)
        assertTrue(publicationThreads.all { it === owner })
        session.close(); ownerJob.cancel()
    }

    @Test fun staleCompletionHandoffCannotClearSuccessorOwnership() = runTest {
        val execution = QueuedDispatcher()
        val handoff = QueuedDispatcher()
        val ownerJob = SupervisorJob()
        val r = Rig(this)
        val session = MonitoringSession(r.source, r.processor, MonotonicClock { 0 }, CoroutineScope(ownerJob + execution), handoff)
        session.setEligibility(true, true); session.start(); session.stop()
        execution.take().run()
        val oldCleanup = handoff.take()
        assertFalse(session.state.value.canStart)
        oldCleanup.run()
        assertTrue(session.state.value.canStart)
        session.start()
        assertFalse(session.state.value.canStart)
        oldCleanup.run() // Replay stale notification after B owns a different Job.
        assertEquals(MonitoringStatus.Running, session.state.value.status)
        assertFalse(session.state.value.canStart)
        session.close(); execution.take().run(); handoff.take().run(); ownerJob.cancel()
        assertFalse(session.state.value.canStart)
    }

    @Test fun destroyedScopeReleasesRetiringWorkWithoutAllowingRestart() = runTest {
        val ownerJob = SupervisorJob()
        val dispatcher = StandardTestDispatcher(testScheduler)
        val r = Rig(this)
        val release = CompletableDeferred<Unit>()
        r.wait = { withContext(NonCancellable) { release.await() } }
        val session = MonitoringSession(r.source, r.processor, MonotonicClock { testScheduler.currentTime * 1_000_000 },
            CoroutineScope(ownerJob + dispatcher), dispatcher)
        session.setEligibility(true, true); session.start(); runCurrent()
        advanceTimeBy(3_500); session.close(); ownerJob.cancel()
        val frozen = session.state.value
        assertEquals(MonitoringStatus.Stopped(StopReason.DESTROYED), frozen.status)
        assertEquals(3_500L, frozen.elapsedMillis)
        assertFalse(frozen.canStart)
        session.setEligibility(true, true); session.start()
        release.complete(Unit); runCurrent()
        assertTrue(ownerJob.isCompleted) // No pending child/native work remains.
        assertEquals(0, r.active)
        assertEquals(frozen, session.state.value)
        session.setEligibility(true, true); session.start()
        advanceTimeBy(20_000); runCurrent()
        assertEquals(1, r.calls)
        assertFalse(session.state.value.canStart)
        assertEquals(frozen, session.state.value)
    }

    @Test fun canceledParentBeforeDispatchCleansUpWithoutLaunchingIntoCanceledScope() = runTest {
        val ownerJob = SupervisorJob()
        val dispatcher = StandardTestDispatcher(testScheduler)
        val r = Rig(this)
        val session = MonitoringSession(r.source, r.processor, MonotonicClock { testScheduler.currentTime * 1_000_000 },
            CoroutineScope(ownerJob + dispatcher), dispatcher)
        session.setEligibility(true, true); session.start(); ownerJob.cancel()
        runCurrent()
        assertEquals(0, r.calls)
        assertEquals(MonitoringStatus.Stopped(StopReason.CANCELED), session.state.value.status)
        assertFalse(session.state.value.canStart)
        assertTrue(ownerJob.isCompleted)
        session.setEligibility(true, true); session.start(); runCurrent()
        assertEquals(0, r.calls)
        assertEquals(MonitoringStatus.Stopped(StopReason.CANCELED), session.state.value.status)
    }

    @Test fun stopGuardsClockRegressionInvalidValuesAndLargeForwardJump() = runTest {
        for ((start, end, expected) in listOf(Triple(100L, Long.MIN_VALUE, 0L),
            Triple(-1L, Long.MAX_VALUE, 0L), Triple(0L, Long.MAX_VALUE, SESSION_LIMIT_MS),
            Triple(Long.MAX_VALUE - 1_000_000, Long.MAX_VALUE, 1L))) {
            var now = start
            val r = Rig(this)
            val session = MonitoringSession(r.source, r.processor, MonotonicClock { now }, backgroundScope,
                StandardTestDispatcher(testScheduler))
            session.setEligibility(true, true); session.start()
            now = end; session.stop(); runCurrent()
            assertEquals(expected, session.state.value.elapsedMillis)
            assertEquals(0, r.calls)
        }
    }

    @Test fun fatalAcquisitionCapturesFailureTimeWithoutSuccessfulSample() = runTest {
        val r = Rig(this); r.enable(); r.wait = { delay(3_500) }; r.throwSource = IllegalStateException()
        r.session.start(); advanceTimeBy(3_500); runCurrent()
        assertEquals(MonitoringStatus.Failed(UnavailableReason.COLLECTION_FAILED), r.session.state.value.status)
        assertEquals(3_500L, r.session.state.value.elapsedMillis)
        assertTrue(r.session.state.value.history.isEmpty())
        assertTrue(r.session.state.value.canStart)
    }
    @Test fun clockRegressionCannotEraseAlreadyPublishedDuration() = runTest {
        var now = 0L
        val r = Rig(this)
        val session = MonitoringSession(r.source, r.processor, MonotonicClock { now }, backgroundScope,
            StandardTestDispatcher(testScheduler))
        session.setEligibility(true, true); session.start(); runCurrent()
        advanceTimeBy(5_000); now = 5_000_000_000; runCurrent()
        assertEquals(5_000L, session.state.value.elapsedMillis)
        now = 3_500_000_000; session.stop(); runCurrent()
        assertEquals(5_000L, session.state.value.elapsedMillis)
    }

    private class QueuedDispatcher : CoroutineDispatcher() {
        private val tasks = ConcurrentLinkedQueue<Runnable>()
        override fun dispatch(context: CoroutineContext, block: Runnable) { tasks.add(block) }
        fun take(): Runnable = checkNotNull(tasks.poll()) { "Expected queued task" }
    }
}

internal fun observation(sequence: Long = 0, nanos: Long = 0) = RawObservation(sequence, nanos, nanos, 0,
    Available(100L), Available(8L shl 30), Available(2L shl 30), Available(1L shl 30), Available(50L), Available(100L),
    Available(253L), Available(4200L), Available(-150000L), Available(1.5), Available(false),
    Available(ChargingStatus.DISCHARGING), Available(ThermalStatus.NONE))
