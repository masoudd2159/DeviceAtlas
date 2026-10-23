package io.github.masoudd2159.deviceatlas.presentation.monitoring

import androidx.annotation.MainThread
import io.github.masoudd2159.deviceatlas.foundation.hardware.*
import io.github.masoudd2159.deviceatlas.foundation.hardware.HardwareValue.*
import io.github.masoudd2159.deviceatlas.foundation.monitoring.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.coroutines.EmptyCoroutineContext

/** Main-confined state machine. The owning ViewModel supplies its structured scope.
 * A canceled job remains owned until it exits, preventing overlap on rapid Stop/Start. */
@MainThread
class MonitoringSession(
    private val source: MonitoringSource, private val processor: MonitoringProcessor,
    private val clock: MonotonicClock, private val scope: CoroutineScope,
    private val ownerDispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
) {
    private val mutable = MutableStateFlow(MonitoringState())
    val state = mutable.asStateFlow()
    private var job: Job? = null
    private var eligible = false
    private var generation = 0L
    private var startedAtNanos: Long? = null
    private var closed = false

    fun setEligibility(visible: Boolean, foreground: Boolean, reason: StopReason = StopReason.BACKGROUND) {
        eligible = !closed && visible && foreground
        if (!eligible) stop(if (!visible) StopReason.HIDDEN else reason)
        publishEligibility()
    }

    fun start() {
        if (!eligible || job != null || !scope.isActive) return
        val token = ++generation
        val started = clock.nanos()
        startedAtNanos = started
        mutable.value = MonitoringState(status = MonitoringStatus.Running)
        val created = scope.launch(start = CoroutineStart.LAZY) {
            try {
                withTimeout(SESSION_LIMIT_MS) {
                    var previous: RawObservation? = null
                    var sequence = 0L
                    var failedSamples = 0
                    while (isActive) {
                        val elapsed = elapsed(started)
                        if (elapsed >= SESSION_LIMIT_MS) break
                        val raw = source.collect(sequence++)
                        ensureActive()
                        // Reject malformed source timing instead of inventing timestamps.
                        if (raw.monotonicNanos < started || raw.completedNanos < raw.monotonicNanos ||
                            (previous != null && raw.monotonicNanos <= previous.monotonicNanos)) {
                            throw SampleFailure(UnavailableReason.INVALID_RESPONSE)
                        }
                        val sample = processor.sample(raw, previous)
                        ensureActive()
                        val nativeFailure = sample.values.values.filterIsInstance<Unavailable>()
                            .firstOrNull { it.reason == UnavailableReason.NATIVE_UNAVAILABLE || it.reason == UnavailableReason.NATIVE_FAILED }
                        if (nativeFailure != null) throw SampleFailure(nativeFailure.reason)
                        failedSamples = if (sample.values.values.none { it is Available }) failedSamples + 1 else 0
                        if (failedSamples >= 3) throw SampleFailure(UnavailableReason.COLLECTION_FAILED)
                        val history = immutableList((mutable.value.history + sample).takeLast(HISTORY_LIMIT))
                        val summaries = processor.summarize(history)
                        ensureActive()
                        summaries.values.filterIsInstance<Unavailable>().firstOrNull {
                            it.reason == UnavailableReason.NATIVE_UNAVAILABLE || it.reason == UnavailableReason.NATIVE_FAILED
                        }?.let { throw SampleFailure(it.reason) }
                        if (token != generation || !eligible) return@withTimeout
                        mutable.value = mutable.value.copy(history = history, summaries = immutableMap(summaries), elapsedMillis = elapsed(started))
                        previous = raw
                        // Delay after work: slow acquisition never queues a backlog.
                        delay(minOf(SAMPLE_INTERVAL_MS, (SESSION_LIMIT_MS - elapsed(started)).coerceAtLeast(1)))
                    }
                }
                if (token == generation) finish(MonitoringStatus.Stopped(StopReason.LIMIT), started)
            } catch (_: TimeoutCancellationException) {
                if (token == generation) finish(MonitoringStatus.Stopped(StopReason.LIMIT), started)
            } catch (cancelled: CancellationException) {
                if (token == generation) finish(MonitoringStatus.Stopped(StopReason.CANCELED), started)
                throw cancelled
            } catch (failure: SampleFailure) {
                if (token == generation) finish(MonitoringStatus.Failed(failure.reason), started)
            } catch (_: LinkageError) {
                if (token == generation) finish(MonitoringStatus.Failed(UnavailableReason.NATIVE_UNAVAILABLE), started)
            } catch (_: RuntimeException) {
                if (token == generation) finish(MonitoringStatus.Failed(UnavailableReason.COLLECTION_FAILED), started)
            }
        }
        job = created
        // A canceled coroutine may never enter its body/finally. Completion also
        // releases ownership for Start→Stop/Reset before the first dispatch.
        created.invokeOnCompletion {
            // Completion has no guaranteed execution context. Post a finite
            // Runnable, not a launch in the possibly already canceled owner scope.
            ownerDispatcher.dispatch(EmptyCoroutineContext, Runnable {
                if (job === created) {
                    if (mutable.value.status is MonitoringStatus.Running) {
                        finish(MonitoringStatus.Stopped(StopReason.CANCELED), started)
                    }
                    job = null
                    startedAtNanos = null
                    publishEligibility()
                }
            })
        }
        created.start()
    }

    fun stop(reason: StopReason = StopReason.USER) {
        if (mutable.value.status is MonitoringStatus.Running) {
            ++generation
            mutable.value = mutable.value.copy(status = MonitoringStatus.Stopped(reason),
                elapsedMillis = startedAtNanos?.let(::elapsed) ?: mutable.value.elapsedMillis)
            job?.cancel()
        }
        publishEligibility()
    }

    fun reset() {
        stop()
        mutable.value = MonitoringState(canStart = canStart(), cleared = true)
    }

    /** Permanent owner disposal; retiring work remains owned until its completion handoff. */
    fun close() {
        closed = true
        eligible = false
        stop(StopReason.DESTROYED)
    }

    private fun elapsed(started: Long): Long {
        val now = clock.nanos()
        val published = mutable.value.elapsedMillis.coerceIn(0, SESSION_LIMIT_MS)
        // Nonnegative, ordered operands make subtraction overflow-free. An invalid
        // clock cannot erase already published duration or produce a negative one.
        return if (started >= 0 && now >= started) ((now - started) / 1_000_000).coerceIn(published, SESSION_LIMIT_MS)
        else published
    }
    private fun finish(status: MonitoringStatus, started: Long) { mutable.value = mutable.value.copy(status = status, elapsedMillis = elapsed(started)) }
    private fun canStart() = !closed && eligible && job == null && scope.isActive
    private fun publishEligibility() { mutable.value = mutable.value.copy(canStart = canStart()) }
    private class SampleFailure(val reason: UnavailableReason) : RuntimeException()
}
