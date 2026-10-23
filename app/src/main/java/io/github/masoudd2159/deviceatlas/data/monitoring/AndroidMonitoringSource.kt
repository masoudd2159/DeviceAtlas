package io.github.masoudd2159.deviceatlas.data.monitoring

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.os.Process
import android.os.SystemClock
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.masoudd2159.deviceatlas.di.HardwareIoDispatcher
import io.github.masoudd2159.deviceatlas.foundation.hardware.HardwareValue
import io.github.masoudd2159.deviceatlas.foundation.hardware.HardwareValue.*
import io.github.masoudd2159.deviceatlas.foundation.hardware.UnavailableReason
import io.github.masoudd2159.deviceatlas.foundation.monitoring.*
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

/** Sequential, public API observations only. A null receiver reads sticky state without registration. */
class AndroidMonitoringSource @Inject constructor(
    @ApplicationContext private val context: Context,
    @HardwareIoDispatcher private val dispatcher: CoroutineDispatcher,
) : MonitoringSource {
    override suspend fun collect(sequence: Long): RawObservation = withContext(dispatcher) {
        coroutineContext.ensureActive()
        val time = SystemClock.elapsedRealtimeNanos()
        val wall = System.currentTimeMillis()
        val cpu = safely { Available(Process.getElapsedCpuTime()) }
        val memory = safely {
            context.getSystemService(ActivityManager::class.java)?.let { manager ->
                Available(ActivityManager.MemoryInfo().also(manager::getMemoryInfo))
            } ?: Unavailable(UnavailableReason.TEMPORARY)
        }
        val battery = safely {
            val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            when {
                intent == null -> Unavailable(UnavailableReason.TEMPORARY)
                intent.hasExtra(BatteryManager.EXTRA_PRESENT) && !intent.getBooleanExtra(BatteryManager.EXTRA_PRESENT, false) ->
                    Unavailable(UnavailableReason.NOT_PRESENT)
                else -> Available(intent)
            }
        }
        fun extra(key: String): HardwareValue<Long> = battery.transform { intent -> safely {
            if (intent.hasExtra(key)) Available(intent.getIntExtra(key, Int.MIN_VALUE).toLong())
            else Unavailable(UnavailableReason.NOT_REPORTED)
        } }
        val current = battery.transform { safely {
            val manager = context.getSystemService(BatteryManager::class.java)
            if (manager == null) Unavailable(UnavailableReason.TEMPORARY)
            else {
                val value = manager.getLongProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
                if (value == Long.MIN_VALUE || value == Int.MIN_VALUE.toLong()) Unavailable(UnavailableReason.NOT_REPORTED)
                else Available(value)
            }
        } }
        val thermal = safely {
            if (Build.VERSION.SDK_INT < 29) Unavailable(UnavailableReason.UNSUPPORTED)
            else context.getSystemService(PowerManager::class.java)?.let { manager ->
                when (manager.currentThermalStatus) {
                    PowerManager.THERMAL_STATUS_NONE -> Available(ThermalStatus.NONE)
                    PowerManager.THERMAL_STATUS_LIGHT -> Available(ThermalStatus.LIGHT)
                    PowerManager.THERMAL_STATUS_MODERATE -> Available(ThermalStatus.MODERATE)
                    PowerManager.THERMAL_STATUS_SEVERE -> Available(ThermalStatus.SEVERE)
                    PowerManager.THERMAL_STATUS_CRITICAL -> Available(ThermalStatus.CRITICAL)
                    PowerManager.THERMAL_STATUS_EMERGENCY -> Available(ThermalStatus.EMERGENCY)
                    PowerManager.THERMAL_STATUS_SHUTDOWN -> Available(ThermalStatus.SHUTDOWN)
                    else -> Unavailable(UnavailableReason.NOT_REPORTED)
                }
            } ?: Unavailable(UnavailableReason.TEMPORARY)
        }
        val headroom = safely {
            if (Build.VERSION.SDK_INT < 30) Unavailable(UnavailableReason.UNSUPPORTED)
            else context.getSystemService(PowerManager::class.java)?.let {
                val value = it.getThermalHeadroom(0).toDouble()
                if (value.isNaN()) Unavailable(UnavailableReason.TEMPORARY) else Available(value)
            } ?: Unavailable(UnavailableReason.TEMPORARY)
        }
        val charging = extra(BatteryManager.EXTRA_STATUS).transform { value ->
            when (value.toInt()) {
                BatteryManager.BATTERY_STATUS_CHARGING -> Available(ChargingStatus.CHARGING)
                BatteryManager.BATTERY_STATUS_DISCHARGING -> Available(ChargingStatus.DISCHARGING)
                BatteryManager.BATTERY_STATUS_FULL -> Available(ChargingStatus.FULL)
                BatteryManager.BATTERY_STATUS_NOT_CHARGING -> Available(ChargingStatus.NOT_CHARGING)
                else -> Unavailable(UnavailableReason.NOT_REPORTED)
            }
        }
        val level = extra(BatteryManager.EXTRA_LEVEL)
        val scale = extra(BatteryManager.EXTRA_SCALE)
        val temperature = extra(BatteryManager.EXTRA_TEMPERATURE)
        val voltage = extra(BatteryManager.EXTRA_VOLTAGE)
        coroutineContext.ensureActive()
        RawObservation(sequence, time, SystemClock.elapsedRealtimeNanos(), wall, cpu,
            memory.transform { Available(it.totalMem) }, memory.transform { Available(it.availMem) },
            memory.transform { Available(it.threshold) }, level, scale, temperature, voltage, current, headroom,
            memory.transform { Available(it.lowMemory) }, charging, thermal)
    }
}

internal inline fun <A, B> HardwareValue<A>.transform(block: (A) -> HardwareValue<B>): HardwareValue<B> = when (this) {
    is Available -> block(value)
    is Unavailable -> this
}
private inline fun <T> safely(block: () -> HardwareValue<T>): HardwareValue<T> = try { block()
} catch (cancelled: CancellationException) { throw cancelled
} catch (_: SecurityException) { Unavailable(UnavailableReason.PERMISSION_RESTRICTED)
} catch (_: RuntimeException) { Unavailable(UnavailableReason.COLLECTION_FAILED) }
