package io.github.masoudd2159.deviceatlas.data.interactive

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.masoudd2159.deviceatlas.foundation.interactive.*
import javax.inject.Inject

class AndroidInteractiveSensors @Inject constructor(@ApplicationContext context: Context) : InteractiveSensors {
    private val manager = context.getSystemService(SensorManager::class.java)
    override fun open(kind: SensorKind, receive: (SensorReading) -> Unit): Pair<SensorInfo, SensorSubscription> {
        val manager = manager ?: throw InteractiveFailure(InteractiveUnavailable.PLATFORM)
        val sensor = try { manager.getDefaultSensor(when (kind) {
            SensorKind.ACCELEROMETER -> Sensor.TYPE_ACCELEROMETER
            SensorKind.GYROSCOPE -> Sensor.TYPE_GYROSCOPE
            SensorKind.LIGHT -> Sensor.TYPE_LIGHT
            SensorKind.PROXIMITY -> Sensor.TYPE_PROXIMITY
        }) } catch (_: RuntimeException) { throw InteractiveFailure(InteractiveUnavailable.PLATFORM) }
            ?: throw InteractiveFailure(InteractiveUnavailable.NOT_PRESENT)
        var active = true
        val listener = object : SensorEventListener {
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
            override fun onSensorChanged(event: SensorEvent) {
                if (active && event.sensor.type == sensor.type) {
                    // Copy only the selected public channels, never the reusable framework event.
                    receive(SensorReading(event.values.take(kind.dimensions).map { it.toDouble() }, SystemClock.elapsedRealtimeNanos(), event.accuracy))
                }
            }
        }
        try {
            if (!manager.registerListener(listener, sensor, 100_000, Handler(Looper.getMainLooper())))
                throw InteractiveFailure(InteractiveUnavailable.REGISTRATION_FAILED)
        } catch (_: RuntimeException) {
            active = false
            manager.unregisterListener(listener)
            throw InteractiveFailure(InteractiveUnavailable.REGISTRATION_FAILED)
        }
        return SensorInfo(sensor.maximumRange.toDouble().takeIf { it.isFinite() && it >= 0 }) to object : SensorSubscription {
            override fun close() { if (active) { active = false; manager.unregisterListener(listener) } }
        }
    }
}
