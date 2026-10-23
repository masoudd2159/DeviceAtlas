package io.github.masoudd2159.deviceatlas.foundation.benchmark

import androidx.annotation.MainThread
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first

/** Process-local, Main-confined leases include canceled/retiring work, even if
 * a different Activity owner is opened before old JNI returns. This guard owns
 * no coroutine, platform context, workload, or results. */
@MainThread
@Singleton
class DiagnosticsCoordinator @Inject constructor() {
    data class Ownership(val monitoring: Any? = null, val benchmark: Any? = null)
    private val mutable = MutableStateFlow(Ownership())
    val ownership = mutable.asStateFlow()
    private val listeners = mutableSetOf<() -> Unit>()
    fun observe(listener: () -> Unit) { listeners += listener }
    fun removeObserver(listener: () -> Unit) { listeners -= listener }
    private fun update(value: Ownership) { mutable.value = value; listeners.toList().forEach { it() } }
    private var stopActiveMonitoring: (() -> Unit)? = null
    fun acquireMonitoring(stop: () -> Unit = {}): Any? {
        if (mutable.value != Ownership()) return null
        stopActiveMonitoring = stop
        return Any().also { update(mutable.value.copy(monitoring = it)) }
    }
    fun releaseMonitoring(lease: Any) {
        if (mutable.value.monitoring === lease) {
            stopActiveMonitoring = null
            update(mutable.value.copy(monitoring = null))
        }
    }
    fun reserveBenchmark(): Any? {
        if (mutable.value.benchmark != null) return null
        val lease = Any()
        update(mutable.value.copy(benchmark = lease))
        stopActiveMonitoring?.invoke()
        return lease
    }
    suspend fun awaitMonitoringRetired() { ownership.first { it.monitoring == null } }
    fun releaseBenchmark(lease: Any) {
        if (mutable.value.benchmark === lease) update(mutable.value.copy(benchmark = null))
    }
    fun monitoringAvailable() = mutable.value == Ownership()
    fun benchmarkAvailable() = mutable.value.benchmark == null
}
