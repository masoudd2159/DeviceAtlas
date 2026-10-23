package io.github.masoudd2159.deviceatlas.presentation.interactive

import androidx.annotation.MainThread
import io.github.masoudd2159.deviceatlas.foundation.benchmark.DiagnosticsCoordinator
import io.github.masoudd2159.deviceatlas.foundation.interactive.*
import io.github.masoudd2159.deviceatlas.foundation.monitoring.MonotonicClock
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.coroutines.EmptyCoroutineContext

/** Main-confined owner. The lease survives cancellation until native processing retires. */
@MainThread
class InteractiveSession(private val sensors: InteractiveSensors, private val haptics: InteractiveHaptics,
    private val analyzer: SensorAnalyzer, private val clock: MonotonicClock,
    private val coordinator: DiagnosticsCoordinator, private val scope: CoroutineScope,
    private val ownerDispatcher: CoroutineDispatcher = Dispatchers.Main.immediate) {
    private val mutable = MutableStateFlow(InteractiveState())
    val state = mutable.asStateFlow()
    private var eligible = false
    private var closed = false
    private var generation = 0L
    private var job: Job? = null
    private var subscription: SensorSubscription? = null
    private val samples = ArrayList<SensorReading>(64)
    private var latest: SensorReading? = null
    private var rejected = 0
    private val pulses = ArrayDeque<Long>(5)
    private var ownsPulse = false
    private val ownershipChanged: () -> Unit = { publish() }
    init { coordinator.observe(ownershipChanged) }

    fun setEligibility(visible: Boolean, foreground: Boolean, destroyed: Boolean = false) {
        eligible = visible && foreground && !closed
        if (!eligible) stop(when { destroyed -> InteractiveStop.DESTROYED; !visible -> InteractiveStop.HIDDEN; else -> InteractiveStop.BACKGROUND })
        publish()
    }
    fun select(kind: InteractiveKind) {
        if (state.value.kind == kind) return
        stop(InteractiveStop.HIDDEN)
        mutable.value = state.value.copy(kind = kind, status = InteractiveStatus.Idle, reading = null,
            summary = null, ageMillis = null, pulseRequested = false, sensorInfo = null)
        publish()
    }
    fun selectSensor(sensor: SensorKind) {
        if (state.value.sensor == sensor) return
        stop(InteractiveStop.HIDDEN)
        mutable.value = state.value.copy(sensor = sensor, status = InteractiveStatus.Idle, reading = null,
            summary = null, ageMillis = null, sensorInfo = null, rejectedSamples = 0)
        publish()
    }
    fun start() {
        if (!eligible || closed || job != null || !scope.isActive) return
        val lease = coordinator.reserveInteractive { stop(InteractiveStop.CONFLICT) } ?: return
        val token = ++generation
        val kind = state.value.kind
        val sensor = state.value.sensor
        val startedNanos = clock.nanos()
        val limitMillis = if (kind == InteractiveKind.SENSOR) 30_000L else 120_000L
        samples.clear(); latest = null; rejected = 0
        mutable.value = state.value.copy(status = InteractiveStatus.Preparing, pattern = DisplayPattern.RED,
            touch = state.value.touch.copy(visited = emptySet(), contacts = emptyList(), activeCount = 0, maximumContacts = 0, geometryReset = false),
            reading = null, summary = null, sensorInfo = null, ageMillis = null, rejectedSamples = 0, pulseRequested = false,
            results = state.value.results + (kind to InteractiveResult(sensor = if (kind == InteractiveKind.SENSOR) sensor else null)))
        val created = scope.launch(start = CoroutineStart.LAZY) {
            try {
                // Admission is bounded too; visibility/cancellation cannot turn a pending start into a later restart.
                val elapsed = ((clock.nanos() - startedNanos) / 1_000_000).coerceAtLeast(0)
                if (elapsed >= limitMillis) { finish(InteractiveStatus.Stopped(InteractiveStop.TIMEOUT)); return@launch }
                withTimeout(limitMillis - elapsed) {
                    coordinator.awaitInteractiveAdmission()
                    gate(token)
                    if (kind == InteractiveKind.SENSOR) {
                        val opened = sensors.open(sensor) { reading ->
                            if (token == generation && eligible && samples.size < 64) {
                                if (reading.values.size == sensor.dimensions && reading.values.all { it.isFinite() } &&
                                    reading.receivedNanos >= 0 && reading.receivedNanos >= (latest?.receivedNanos ?: 0)) {
                                    val copy = reading.copy(values = reading.values.toList())
                                    samples += copy; latest = copy
                                } else rejected = (rejected + 1).coerceAtMost(Int.MAX_VALUE - 1)
                            }
                        }
                        subscription = opened.second
                        mutable.value = state.value.copy(sensorInfo = opened.first)
                    }
                    gate(token)
                    mutable.value = state.value.copy(status = InteractiveStatus.Running)
                    saveResult()
                    while (isActive) {
                        delay(250)
                        gate(token)
                        if (kind == InteractiveKind.SENSOR) {
                            val snapshot = samples.toList()
                            val reading = latest
                            val summary = if (snapshot.isEmpty()) null else analyzer.summarize(sensor, snapshot)
                            gate(token)
                            mutable.value = state.value.copy(reading = reading, summary = summary,
                                ageMillis = reading?.let { ((clock.nanos() - it.receivedNanos) / 1_000_000).coerceAtLeast(0) }, rejectedSamples = rejected)
                            if (snapshot.size == 64) { finish(InteractiveStatus.Completed); break }
                        }
                        publish()
                    }
                }
            } catch (_: TimeoutCancellationException) {
                if (token == generation) finish(InteractiveStatus.Stopped(InteractiveStop.TIMEOUT))
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (failure: InteractiveFailure) {
                if (token == generation) finish(InteractiveStatus.Unavailable(failure.reason))
            } catch (_: LinkageError) {
                if (token == generation) finish(InteractiveStatus.Unavailable(InteractiveUnavailable.NATIVE))
            } catch (_: RuntimeException) {
                if (token == generation) finish(InteractiveStatus.Unavailable(InteractiveUnavailable.PLATFORM))
            } finally {
                if (token == generation) ++generation
                releaseHardware()
            }
        }
        job = created
        created.invokeOnCompletion {
            ownerDispatcher.dispatch(EmptyCoroutineContext, Runnable {
                if (job === created) {
                    releaseHardware()
                    if (state.value.status == InteractiveStatus.Running || state.value.status == InteractiveStatus.Preparing)
                        finish(InteractiveStatus.Stopped(InteractiveStop.DESTROYED))
                    job = null
                    coordinator.releaseInteractive(lease)
                    publish()
                }
            })
        }
        created.start(); publish()
    }
    fun stop(reason: InteractiveStop = InteractiveStop.USER) {
        if (state.value.status == InteractiveStatus.Running || state.value.status == InteractiveStatus.Preparing) {
            ++generation
            finish(InteractiveStatus.Stopped(reason))
            releaseHardware() // main-looper callbacks are retired before the lease can be released
            job?.cancel()
        }
        publish()
    }
    fun close() { closed = true; eligible = false; stop(InteractiveStop.DESTROYED); coordinator.removeObserver(ownershipChanged) }
    fun pattern(delta: Int) {
        if (state.value.kind == InteractiveKind.DISPLAY && state.value.status == InteractiveStatus.Running) {
            val patterns = DisplayPattern.entries
            mutable.value = state.value.copy(pattern = patterns[Math.floorMod(state.value.pattern.ordinal + delta, patterns.size)])
        }
    }
    fun resizeTouch(width: Int, height: Int) { mutable.value = state.value.copy(touch = state.value.touch.resize(width, height)) }
    fun contacts(points: List<Contact>) {
        if (state.value.kind == InteractiveKind.TOUCH && state.value.status == InteractiveStatus.Running)
            mutable.value = state.value.copy(touch = state.value.touch.update(points))
    }
    fun cancelContacts() { mutable.value = state.value.copy(touch = state.value.touch.copy(contacts = emptyList(), activeCount = 0)) }
    fun clearTouch() {
        mutable.value = state.value.copy(touch = state.value.touch.copy(visited = emptySet(), contacts = emptyList(),
            activeCount = 0, maximumContacts = 0, geometryReset = false))
        saveResult()
    }
    fun observe(observation: ManualObservation) {
        val kind = state.value.kind
        mutable.value = state.value.copy(results = state.value.results + (kind to state.value.results.getValue(kind).copy(observation = observation)))
    }
    fun pulse() {
        if (!eligible || closed || state.value.kind != InteractiveKind.HAPTIC || state.value.status != InteractiveStatus.Running || !pulseAllowed()) return
        try {
            haptics.pulse(50)
            ownsPulse = true
            pulses.addLast(clock.nanos())
            mutable.value = state.value.copy(pulseRequested = true)
            saveResult()
        } catch (failure: InteractiveFailure) { finish(InteractiveStatus.Unavailable(failure.reason)); releaseHardware(); job?.cancel()
        } catch (_: RuntimeException) { finish(InteractiveStatus.Unavailable(InteractiveUnavailable.PLATFORM)); releaseHardware(); job?.cancel() }
        publish()
    }
    private fun pulseAllowed(): Boolean {
        val now = clock.nanos()
        while (pulses.isNotEmpty() && now >= pulses.first() && now - pulses.first() >= 60_000_000_000L) pulses.removeFirst()
        return pulses.size < 5 && (pulses.isEmpty() || now >= pulses.last() && now - pulses.last() >= 2_000_000_000L)
    }
    private fun gate(token: Long) { if (token != generation || !eligible || closed) throw CancellationException() }
    private fun releaseHardware() {
        val old = subscription; subscription = null
        try { old?.close() } finally {
            if (ownsPulse) { ownsPulse = false; haptics.cancel() }
        }
    }
    private fun finish(status: InteractiveStatus) {
        mutable.value = state.value.copy(status = status, touch = state.value.touch.copy(contacts = emptyList(), activeCount = 0))
        saveResult()
    }
    private fun saveResult() {
        val s = state.value
        mutable.value = s.copy(results = s.results + (s.kind to s.results.getValue(s.kind).copy(status = s.status,
            visitedCells = s.touch.visited.size, totalCells = s.touch.cells, maximumContacts = s.touch.maximumContacts,
            summary = s.summary, pulseRequested = s.pulseRequested, sensor = if (s.kind == InteractiveKind.SENSOR) s.sensor else null)))
    }
    private fun publish() { mutable.value = state.value.copy(canStart = eligible && !closed && scope.isActive && job == null &&
        coordinator.ownership.value.interactive == null, retiring = job != null && state.value.status !in listOf(InteractiveStatus.Running, InteractiveStatus.Preparing),
        pulseAllowed = pulseAllowed()) }
}
