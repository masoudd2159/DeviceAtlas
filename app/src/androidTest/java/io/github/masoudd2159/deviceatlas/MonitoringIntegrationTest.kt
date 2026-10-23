package io.github.masoudd2159.deviceatlas

import android.app.ActivityManager
import android.os.Build
import androidx.test.platform.app.InstrumentationRegistry
import io.github.masoudd2159.deviceatlas.data.monitoring.AndroidMonitoringSource
import io.github.masoudd2159.deviceatlas.data.nativecore.NativeBridge
import io.github.masoudd2159.deviceatlas.data.nativecore.RustMonitoringProcessor
import io.github.masoudd2159.deviceatlas.foundation.hardware.HardwareValue.*
import io.github.masoudd2159.deviceatlas.foundation.hardware.UnavailableReason
import io.github.masoudd2159.deviceatlas.foundation.monitoring.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class MonitoringIntegrationTest {
    @Test fun publicCollectorAndRustProduceRealIndependentObservations() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val raw = AndroidMonitoringSource(context, Dispatchers.IO).collect(0)
        assertTrue(raw.completedNanos >= raw.monotonicNanos)
        assertTrue(raw.cpuMillis is Available)
        val info = ActivityManager.MemoryInfo().also(context.getSystemService(ActivityManager::class.java)::getMemoryInfo)
        assertEquals(Available(info.totalMem), raw.totalBytes)
        if (Build.VERSION.SDK_INT < 29) assertEquals(Unavailable(UnavailableReason.UNSUPPORTED), raw.thermalStatus)
        if (Build.VERSION.SDK_INT < 30) assertEquals(Unavailable(UnavailableReason.UNSUPPORTED), raw.headroom)
        val processor = RustMonitoringProcessor(Dispatchers.Default)
        val sample = processor.sample(raw, null)
        assertEquals(Unavailable(UnavailableReason.BASELINE), sample.values[LiveMetric.PROCESS_CPU])
        assertTrue(sample.values[LiveMetric.AVAILABLE_RAM] is Available)
        val next = raw.copy(monotonicNanos = raw.monotonicNanos + 5_000_000_000, cpuMillis = Available((raw.cpuMillis as Available).value + 10000))
        val processed = processor.sample(next, raw)
        assertEquals(Available(200.0), processed.values[LiveMetric.PROCESS_CPU])
        val summary = processor.summarize(listOf(sample, processed))
        assertEquals(1, (summary[LiveMetric.PROCESS_CPU] as Available).value.count)
    }

    @Test fun monitoringJniRejectsMalformedBuffersAndKeepsOutputUntouched() {
        System.loadLibrary("deviceatlas_core")
        val out = DoubleArray(20) { 123.0 }
        val valid = longArrayOf(1,1,1, 5_000_000_000,100,8L shl 30,2L shl 30,1L shl 30,50,100,253,4200,-150000,0,0)
        assertEquals(3, NativeBridge.processMonitoring(null, doubleArrayOf(0.0), out))
        assertEquals(3, NativeBridge.processMonitoring(valid, null, out))
        assertEquals(3, NativeBridge.processMonitoring(valid, doubleArrayOf(0.0), null))
        assertEquals(3, NativeBridge.processMonitoring(longArrayOf(), doubleArrayOf(0.0), out))
        assertEquals(8, NativeBridge.processMonitoring(longArrayOf(2,1,1), doubleArrayOf(0.0), out))
        assertEquals(7, NativeBridge.processMonitoring(longArrayOf(1,9,1), doubleArrayOf(0.0), out))
        assertEquals(3, NativeBridge.processMonitoring(longArrayOf(1,2,121), doubleArrayOf(), out))
        assertEquals(3, NativeBridge.processMonitoring(longArrayOf(1,2,-1), doubleArrayOf(), out))
        assertEquals(3, NativeBridge.processMonitoring(valid, doubleArrayOf(), out))
        assertTrue(out.all { it == 123.0 })
        assertEquals(3, NativeBridge.processMonitoring(valid, doubleArrayOf(0.0), DoubleArray(19)))
        val summary = DoubleArray(50) { 123.0 }
        assertEquals(6, NativeBridge.processMonitoring(longArrayOf(1,2,1,1024), DoubleArray(10), summary))
        assertEquals(6, NativeBridge.processMonitoring(longArrayOf(1,2,1,1), DoubleArray(10) { Double.NaN }, summary))
        assertTrue(summary.all { it == 123.0 })
        assertEquals(0, NativeBridge.processMonitoring(valid, doubleArrayOf(Double.NaN), out))
        assertEquals(6.0, out[18], 0.0); assertEquals(25.0, out[5], 0.0)
        assertTrue(out.all { it.isFinite() })
        assertEquals(0, NativeBridge.processMonitoring(longArrayOf(1,2,0), doubleArrayOf(), summary))
        assertTrue(summary.all { it == 0.0 })
    }
}
