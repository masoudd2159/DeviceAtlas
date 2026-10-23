package io.github.masoudd2159.deviceatlas.presentation.monitoring

import io.github.masoudd2159.deviceatlas.foundation.hardware.*
import io.github.masoudd2159.deviceatlas.foundation.hardware.HardwareValue.*
import io.github.masoudd2159.deviceatlas.foundation.monitoring.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

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
        val session = MonitoringSession(source, processor, MonotonicClock { test.testScheduler.currentTime * 1_000_000 }, test.backgroundScope)
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
        r.session.stop(); r.session.start(); runCurrent(); assertEquals(1, r.calls)
        release.complete(Unit); runCurrent()
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
}

internal fun observation(sequence: Long = 0, nanos: Long = 0) = RawObservation(sequence, nanos, nanos, 0,
    Available(100L), Available(8L shl 30), Available(2L shl 30), Available(1L shl 30), Available(50L), Available(100L),
    Available(253L), Available(4200L), Available(-150000L), Available(1.5), Available(false),
    Available(ChargingStatus.DISCHARGING), Available(ThermalStatus.NONE))
