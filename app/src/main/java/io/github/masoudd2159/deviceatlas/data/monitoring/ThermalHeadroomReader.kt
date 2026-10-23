package io.github.masoudd2159.deviceatlas.data.monitoring

import android.content.Context
import android.os.Build
import android.os.PowerManager
import android.os.SystemClock
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.masoudd2159.deviceatlas.foundation.hardware.HardwareValue
import io.github.masoudd2159.deviceatlas.foundation.hardware.HardwareValue.*
import io.github.masoudd2159.deviceatlas.foundation.hardware.UnavailableReason
import javax.inject.Inject
import javax.inject.Singleton

/** One serialized ten-second cache shared by all diagnostic collectors, including
 * unavailable readings. Cached values retain their meaning, never become zero. */
@Singleton
class ThermalHeadroomReader @Inject constructor(@ApplicationContext private val context: Context) {
    private val cache = HeadroomCache()
    fun read(): HardwareValue<Double> = cache.read(SystemClock.elapsedRealtimeNanos()) {
        if (Build.VERSION.SDK_INT < 30) Unavailable(UnavailableReason.UNSUPPORTED)
        else try {
            val manager = context.getSystemService(PowerManager::class.java)
            val value = manager?.getThermalHeadroom(0)?.toDouble()
            if (value == null || !value.isFinite() || value < 0) Unavailable(UnavailableReason.TEMPORARY)
            else Available(value)
        } catch (_: RuntimeException) { Unavailable(UnavailableReason.COLLECTION_FAILED) }
    }
}

internal class HeadroomCache {
    private var last: Long? = null
    private var value: HardwareValue<Double> = Unavailable(UnavailableReason.TEMPORARY)
    @Synchronized fun read(now: Long, collect: () -> HardwareValue<Double>): HardwareValue<Double> {
        val previous = last
        // A regressed clock cannot permit an early additional poll.
        if (previous != null && (now < previous || now - previous < 10_000_000_000L)) return value
        last = now
        value = collect()
        return value
    }
}
