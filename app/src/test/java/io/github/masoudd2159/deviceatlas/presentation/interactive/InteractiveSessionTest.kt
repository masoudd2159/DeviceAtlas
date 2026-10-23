package io.github.masoudd2159.deviceatlas.presentation.interactive

import io.github.masoudd2159.deviceatlas.foundation.benchmark.DiagnosticsCoordinator
import io.github.masoudd2159.deviceatlas.foundation.interactive.*
import io.github.masoudd2159.deviceatlas.foundation.monitoring.MonotonicClock
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class InteractiveSessionTest {
    private class Rig(val test: TestScope) {
        val dispatcher = StandardTestDispatcher(test.testScheduler)
        val coordinator = DiagnosticsCoordinator()
        var registrations = 0; var active = 0; var maximumActive = 0; var closes = 0
        var pulses = 0; var cancellations = 0; var duration = 0L
        var sourceFailure: InteractiveUnavailable? = null
        var hapticFailure: InteractiveUnavailable? = null
        var callback: ((SensorReading) -> Unit)? = null
        var analysisWait: (suspend () -> Unit)? = null
        var analyses = 0
        var clockOffsetNanos = 0L
        val clock = MonotonicClock { test.testScheduler.currentTime * 1_000_000 + clockOffsetNanos }
        val sensors = object : InteractiveSensors {
            override fun open(kind: SensorKind, receive: (SensorReading) -> Unit): Pair<SensorInfo, SensorSubscription> {
                sourceFailure?.let { throw InteractiveFailure(it) }
                registrations++; active++; maximumActive = maxOf(maximumActive, active); callback = receive
                var closed = false
                return SensorInfo(10.0) to object : SensorSubscription {
                    override fun close() { if (!closed) { closed = true; active--; closes++ } }
                }
            }
        }
        val haptics = object : InteractiveHaptics {
            override fun pulse(millis: Long) { hapticFailure?.let { throw InteractiveFailure(it) }; pulses++; duration = millis }
            override fun cancel() { cancellations++ }
        }
        val analyzer = SensorAnalyzer { _, samples ->
            analyses++; analysisWait?.invoke()
            val values = samples.map { it.values.first() }
            SensorSummary(samples.size, values.min(), values.max(), values.average(), values.last(), values.max() - values.min())
        }
        fun owner() = InteractiveSession(sensors, haptics, analyzer, clock, coordinator, test.backgroundScope, dispatcher)
        val session = owner()
        fun start(kind: InteractiveKind) { session.setEligibility(true, true); session.select(kind); session.start(); test.runCurrent() }
        fun emit(value: Double = 1.0) { callback?.invoke(SensorReading(List(session.state.value.sensor.dimensions) { value }, clock.nanos(), 0)) }
        fun tick(millis: Long = 250) { test.advanceTimeBy(millis); test.runCurrent() }
    }
    @Test fun idleRequiresExplicitStartAndNewOwnerIsIdle() = runTest {
        val r = Rig(this); r.session.start(); runCurrent(); assertEquals(InteractiveStatus.Idle,r.session.state.value.status)
        r.session.setEligibility(true,true); runCurrent(); assertEquals(0,r.registrations)
        r.start(InteractiveKind.SENSOR); assertEquals(1,r.registrations)
        assertEquals(InteractiveStatus.Idle,r.owner().state.value.status); r.session.close(); runCurrent()
    }
    @Test fun displayOrderingManualControlAndTimeout() = runTest {
        val r = Rig(this); r.start(InteractiveKind.DISPLAY)
        assertEquals(DisplayPattern.RED,r.session.state.value.pattern)
        r.tick(1000); assertEquals(DisplayPattern.RED,r.session.state.value.pattern)
        DisplayPattern.entries.drop(1).forEach { r.session.pattern(1); assertEquals(it,r.session.state.value.pattern) }
        r.session.pattern(1); assertEquals(DisplayPattern.RED,r.session.state.value.pattern)
        r.session.pattern(-1); assertEquals(DisplayPattern.GRID,r.session.state.value.pattern)
        r.tick(119000); assertEquals(InteractiveStatus.Stopped(InteractiveStop.TIMEOUT),r.session.state.value.status)
        assertEquals(0,r.registrations); assertEquals(0,r.pulses)
    }
    @Test fun manualObservationsSurviveSelectionButAreNotHardwareFacts() = runTest {
        val r = Rig(this); r.start(InteractiveKind.DISPLAY); r.session.stop(); runCurrent()
        r.session.observe(ManualObservation.POSSIBLE_ISSUE); r.session.select(InteractiveKind.TOUCH)
        assertEquals(ManualObservation.POSSIBLE_ISSUE,r.session.state.value.results.getValue(InteractiveKind.DISPLAY).observation)
        assertEquals(ManualObservation.NOT_TESTED,r.owner().state.value.results.getValue(InteractiveKind.DISPLAY).observation)
    }
    @Test fun touchOnlyCountsDuringExplicitSession() = runTest {
        val r = Rig(this); r.session.resizeTouch(800,1200)
        r.session.contacts(listOf(Contact(1,10f,10f))); assertTrue(r.session.state.value.touch.visited.isEmpty())
        r.start(InteractiveKind.TOUCH); r.session.contacts(listOf(Contact(1,10f,10f),Contact(2,799f,1199f)))
        assertEquals(2,r.session.state.value.touch.visited.size); assertEquals(2,r.session.state.value.touch.maximumContacts)
        r.session.contacts(listOf(Contact(2,400f,600f))); assertEquals(1,r.session.state.value.touch.activeCount)
        r.session.cancelContacts(); assertEquals(0,r.session.state.value.touch.activeCount)
        r.session.contacts(listOf(Contact(1,20f,20f))); assertEquals(2,r.session.state.value.touch.maximumContacts)
        r.session.clearTouch(); assertTrue(r.session.state.value.touch.visited.isEmpty()); assertEquals(0,r.session.state.value.touch.maximumContacts)
        r.session.close(); runCurrent()
    }
    @Test fun sensorAbsenceAndRegistrationFailureAreDistinct() = runTest {
        for (reason in listOf(InteractiveUnavailable.NOT_PRESENT,InteractiveUnavailable.REGISTRATION_FAILED)) {
            val r = Rig(this); r.sourceFailure = reason; r.start(InteractiveKind.SENSOR)
            assertEquals(InteractiveStatus.Unavailable(reason),r.session.state.value.status)
            assertEquals(0,r.active); assertEquals(DiagnosticsCoordinator.Ownership(),r.coordinator.ownership.value)
            r.session.close()
        }
    }
    @Test fun noEventsTimeoutDoesNotInventAReading() = runTest {
        val r = Rig(this); r.start(InteractiveKind.SENSOR); r.tick(30000)
        assertNull(r.session.state.value.summary); assertNull(r.session.state.value.reading)
        assertEquals(1,r.closes); assertEquals(0,r.active); assertEquals(InteractiveStatus.Stopped(InteractiveStop.TIMEOUT),r.session.state.value.status)
    }
    @Test fun samplesAreCopiedBoundedAndUiIsThrottled() = runTest {
        val r = Rig(this); r.start(InteractiveKind.SENSOR)
        val values = mutableListOf(1.0,2.0,3.0)
        r.callback!!(SensorReading(values,r.clock.nanos(),0)); values[0] = 1000.0
        r.tick(249); assertEquals(0,r.analyses); assertNull(r.session.state.value.reading)
        r.tick(1); assertEquals(1,r.analyses); assertEquals(1.0,r.session.state.value.reading!!.values[0],0.0)
        repeat(1000) { r.emit(it.toDouble()) }; r.tick()
        assertEquals(64,r.session.state.value.summary!!.count); assertEquals(2,r.analyses)
        assertEquals(InteractiveStatus.Completed,r.session.state.value.status); assertEquals(1,r.closes)
    }
    @Test fun invalidSamplesAreRejectedAndZeroRemainsValid() = runTest {
        val r = Rig(this); r.start(InteractiveKind.SENSOR)
        r.emit(Double.NaN); r.emit(Double.POSITIVE_INFINITY); r.emit(0.0); r.tick()
        assertEquals(2,r.session.state.value.rejectedSamples); assertEquals(1,r.session.state.value.summary!!.count)
        assertEquals(0.0,r.session.state.value.summary!!.latest,0.0); r.session.close(); runCurrent()
    }
    @Test fun stopRetiresCallbacksAndNeverAutomaticallyRestarts() = runTest {
        val r = Rig(this); r.start(InteractiveKind.SENSOR); val late = r.callback!!
        r.emit(); r.tick(); r.session.stop(); runCurrent()
        val stopped = r.session.state.value
        late(SensorReading(listOf(9.0,9.0,9.0),r.clock.nanos(),0)); r.tick(1000)
        assertEquals(stopped,r.session.state.value); assertEquals(1,r.closes)
        r.session.setEligibility(true,true); r.tick(1000); assertEquals(1,r.registrations)
    }
    @Test fun backgroundNavigationAndDestructionUnregisterExactlyOnce() = runTest {
        for (background in listOf(true,false)) {
            val r = Rig(this); r.start(InteractiveKind.SENSOR)
            r.session.setEligibility(!background,false); runCurrent(); assertEquals(1,r.closes)
            r.session.close(); runCurrent(); assertEquals(1,r.closes)
        }
    }
    @Test fun changingSensorStopsAndRequiresNewStart() = runTest {
        val r = Rig(this); r.start(InteractiveKind.SENSOR)
        r.session.selectSensor(SensorKind.LIGHT); runCurrent(); assertEquals(0,r.active); assertNull(r.session.state.value.reading)
        r.tick(1000); assertEquals(1,r.registrations)
        r.session.start(); runCurrent(); r.emit(0.0); r.tick()
        assertEquals(1,r.session.state.value.reading!!.values.size); assertEquals(1,r.maximumActive)
        r.session.close(); runCurrent()
    }
    @Test fun hapticRequiresStartAndExplicitPulseWithCooldownAcrossRestarts() = runTest {
        val r = Rig(this); r.session.pulse(); r.start(InteractiveKind.HAPTIC); assertEquals(0,r.pulses)
        repeat(50) { r.session.pulse() }; assertEquals(1,r.pulses); assertEquals(50L,r.duration)
        r.session.stop(); runCurrent(); assertEquals(1,r.cancellations)
        r.session.start(); runCurrent(); r.session.pulse(); assertEquals(1,r.pulses)
        r.tick(2000); r.session.pulse(); assertEquals(2,r.pulses)
        r.session.setEligibility(true,false); runCurrent(); assertEquals(2,r.cancellations)
        r.session.pulse(); assertEquals(2,r.pulses)
    }
    @Test fun fivePulseRollingMinuteLimitAndNoQueuedRequests() = runTest {
        val r = Rig(this); r.start(InteractiveKind.HAPTIC)
        repeat(5) { r.session.pulse(); r.tick(2000) }; assertEquals(5,r.pulses)
        r.session.pulse(); assertEquals(5,r.pulses)
        r.tick(50000); assertEquals(5,r.pulses)
        r.session.pulse(); assertEquals(6,r.pulses); r.session.close(); runCurrent()
    }
    @Test fun hapticAbsenceAndApiFailureAreUnavailable() = runTest {
        for (failure in listOf(InteractiveUnavailable.NOT_PRESENT,InteractiveUnavailable.PLATFORM)) {
            val r = Rig(this); r.hapticFailure = failure; r.start(InteractiveKind.HAPTIC); r.session.pulse(); runCurrent()
            assertEquals(InteractiveStatus.Unavailable(failure),r.session.state.value.status)
            assertFalse(r.session.state.value.pulseRequested); assertEquals(0,r.pulses); r.session.close()
        }
    }
    @Test fun pendingMonitoringAndBenchmarkRetireBeforeSensorRegistration() = runTest {
        for (benchmark in listOf(false,true)) {
            val r = Rig(this); var stopRequested = false
            val lease = if (benchmark) r.coordinator.reserveBenchmark { stopRequested = true }!!
                else r.coordinator.acquireMonitoring { stopRequested = true }!!
            r.start(InteractiveKind.SENSOR); assertTrue(stopRequested); assertEquals(0,r.registrations)
            if (benchmark) r.coordinator.releaseBenchmark(lease) else r.coordinator.releaseMonitoring(lease)
            runCurrent(); assertEquals(1,r.registrations); r.session.close(); runCurrent()
        }
    }
    @Test fun pendingAdmissionCanceledByNavigationDoesNotStartLater() = runTest {
        val r = Rig(this); val lease = r.coordinator.reserveBenchmark()!!
        r.start(InteractiveKind.SENSOR); r.session.setEligibility(false,true); runCurrent()
        r.coordinator.releaseBenchmark(lease); runCurrent(); assertEquals(0,r.registrations)
        assertEquals(DiagnosticsCoordinator.Ownership(),r.coordinator.ownership.value)
    }
    @Test fun cancelBeforeFirstDispatchDoesNotLeakLease() = runTest {
        val r = Rig(this); r.session.setEligibility(true,true); r.session.start(); r.session.stop(); runCurrent()
        assertEquals(DiagnosticsCoordinator.Ownership(),r.coordinator.ownership.value)
        repeat(5) { r.session.start(); r.session.stop(); runCurrent() }
        assertTrue(r.session.state.value.canStart)
    }
    @Test fun delayedFirstDispatchCannotExtendTheMonotonicDeadline() = runTest {
        val r = Rig(this); r.session.setEligibility(true,true); r.session.select(InteractiveKind.SENSOR); r.session.start()
        r.clockOffsetNanos = 31_000_000_000L; runCurrent()
        assertEquals(0,r.registrations); assertEquals(InteractiveStatus.Stopped(InteractiveStop.TIMEOUT),r.session.state.value.status)
        assertEquals(DiagnosticsCoordinator.Ownership(),r.coordinator.ownership.value)
    }
    @Test fun nativeRetirementBlocksNewOwnerAndMonitoringAndBenchmark() = runTest {
        val r = Rig(this); val release = CompletableDeferred<Unit>()
        r.analysisWait = { withContext(NonCancellable) { release.await() } }
        r.start(InteractiveKind.SENSOR); r.emit(); r.tick()
        r.session.stop(); runCurrent(); assertEquals(0,r.active)
        assertNotNull(r.coordinator.ownership.value.interactive)
        assertNull(r.coordinator.acquireMonitoring())
        val other = r.owner(); other.setEligibility(true,true); other.start(); runCurrent()
        assertEquals(InteractiveStatus.Idle,other.state.value.status)
        val benchmark = r.coordinator.reserveBenchmark()!!
        var admitted = false
        val waiter = backgroundScope.launch(r.dispatcher) { r.coordinator.awaitMonitoringRetired(); admitted = true }
        runCurrent(); assertFalse(admitted)
        release.complete(Unit); runCurrent(); assertTrue(admitted)
        assertNull(r.session.state.value.summary); r.coordinator.releaseBenchmark(benchmark)
        waiter.cancel(); other.close(); r.session.close()
    }
    @Test fun sensorToBenchmarkStopsListenerAndHapticSwitchNeverStartsAutomatically() = runTest {
        val r = Rig(this); r.start(InteractiveKind.SENSOR)
        val benchmark = r.coordinator.reserveBenchmark()!!; runCurrent()
        assertEquals(0,r.active); assertEquals(InteractiveStatus.Stopped(InteractiveStop.CONFLICT),r.session.state.value.status)
        r.coordinator.releaseBenchmark(benchmark); r.session.select(InteractiveKind.HAPTIC); r.tick(1000)
        assertEquals(0,r.pulses); r.session.close()
    }
}
