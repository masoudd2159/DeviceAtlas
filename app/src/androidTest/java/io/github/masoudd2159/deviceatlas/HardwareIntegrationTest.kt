package io.github.masoudd2159.deviceatlas

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorManager
import android.os.Build
import android.os.StatFs
import android.os.BatteryManager
import androidx.test.platform.app.InstrumentationRegistry
import io.github.masoudd2159.deviceatlas.data.hardware.AndroidHardwareCollector
import io.github.masoudd2159.deviceatlas.data.hardware.ProcessedHardwareReader
import io.github.masoudd2159.deviceatlas.data.hardware.platformText
import io.github.masoudd2159.deviceatlas.data.nativecore.NativeBridge
import io.github.masoudd2159.deviceatlas.data.nativecore.RustHardwareProcessor
import io.github.masoudd2159.deviceatlas.foundation.hardware.*
import io.github.masoudd2159.deviceatlas.foundation.hardware.HardwareValue.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class HardwareIntegrationTest {
    private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun acquiredProductAndAbiMetadataMatchFramework() = runBlocking {
        val snapshot = AndroidHardwareCollector(context, Dispatchers.IO).collect()
        assertEquals(platformText(Build.MANUFACTURER), snapshot.fields[HardwareField.MANUFACTURER])
        assertEquals(platformText(Build.MODEL), snapshot.fields[HardwareField.MODEL])
        assertEquals(Available(HardwareDatum.Text(Build.SUPPORTED_ABIS.joinToString(", "))), snapshot.fields[HardwareField.ABIS])
        if (Build.VERSION.SDK_INT < 31) assertEquals(Unavailable(UnavailableReason.UNSUPPORTED), snapshot.fields[HardwareField.SOC_MODEL])
        if (Build.VERSION.SDK_INT < 34) assertEquals(Unavailable(UnavailableReason.UNSUPPORTED), snapshot.fields[HardwareField.CYCLES])
    }

    @Test fun unsupportedBatteryPropertiesCannotAppearAsGenuineZerosOnApi26() = runBlocking {
        val manager = context.getSystemService(BatteryManager::class.java)
        val snapshot = AndroidHardwareCollector(context, Dispatchers.IO).collect()
        listOf(HardwareField.CURRENT to BatteryManager.BATTERY_PROPERTY_CURRENT_NOW,
            HardwareField.CHARGE to BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER,
            HardwareField.ENERGY to BatteryManager.BATTERY_PROPERTY_ENERGY_COUNTER).forEach { (field, id) ->
            if (manager.getLongProperty(id) == Long.MIN_VALUE) {
                assertTrue(snapshot.fields[field] is Unavailable)
            }
        }
    }

    @Test fun filesystemScopeSensorsAndDerivedMemoryAreReal() = runBlocking {
        val snapshot = ProcessedHardwareReader(AndroidHardwareCollector(context, Dispatchers.IO), RustHardwareProcessor(Dispatchers.Default)).read()
        val storage = ((snapshot.fields.getValue(HardwareField.STORAGE) as Available).value as HardwareDatum.Capacity)
        assertEquals(StatFs(context.dataDir.absolutePath).totalBytes, storage.totalBytes)
        assertEquals(100.0, storage.availablePercent + storage.unavailablePercent, 1e-9)
        val memory = ((snapshot.fields.getValue(HardwareField.MEMORY) as Available).value as HardwareDatum.Capacity)
        assertTrue(memory.totalBytes > 0)
        assertTrue(memory.availableBytes in 0..memory.totalBytes)
        assertEquals(memory.availableBytes.toDouble() / memory.totalBytes * 100.0, memory.availablePercent, 1e-9)
        val expected = context.getSystemService(SensorManager::class.java).getSensorList(Sensor.TYPE_ALL)
        val reported = (snapshot.sensors as Available).value
        assertEquals(expected.size, reported.size)
        assertEquals(expected.map { it.type }, reported.map { it.type })
        assertTrue(snapshot.fields.values.none { it is Available && it.value is HardwareDatum.Numeric })
    }

    @Test fun hardwareJniValidatesBoundsNullVersionOperationAndNumbers() {
        System.loadLibrary("deviceatlas_core")
        val valid = longArrayOf(1, 1, 8L shl 30, 2L shl 30)
        val out = DoubleArray(5) { 123.0 }
        assertEquals(3, NativeBridge.processHardware(null, doubleArrayOf(0.0), out))
        assertEquals(3, NativeBridge.processHardware(valid, null, out))
        assertEquals(3, NativeBridge.processHardware(valid, doubleArrayOf(0.0), null))
        listOf(0, 3, 5, 100).forEach { assertEquals(3, NativeBridge.processHardware(LongArray(it), doubleArrayOf(0.0), out)) }
        listOf(0, 4, 6).forEach { assertEquals(3, NativeBridge.processHardware(valid, doubleArrayOf(0.0), DoubleArray(it))) }
        assertEquals(3, NativeBridge.processHardware(valid, DoubleArray(2), out))
        assertEquals(3, NativeBridge.mean(DoubleArray(1_048_577), DoubleArray(1)))
        assertEquals(8, NativeBridge.processHardware(longArrayOf(2,1,1,1), doubleArrayOf(0.0), out))
        assertEquals(7, NativeBridge.processHardware(longArrayOf(1,99,1,1), doubleArrayOf(0.0), out))
        assertEquals(6, NativeBridge.processHardware(longArrayOf(1,1,1,2), doubleArrayOf(0.0), out))
        assertEquals(6, NativeBridge.processHardware(valid, doubleArrayOf(Double.NaN), out))
        assertArrayEquals(DoubleArray(5) { 123.0 }, out, 0.0)
        assertEquals(0, NativeBridge.processHardware(valid, doubleArrayOf(0.0), out))
        assertArrayEquals(doubleArrayOf(8.0,2.0,6.0,25.0,75.0), out, 0.0)
    }

    @Test fun repeatedHardwareCallsPreserveInputsAndCurrentSign() = runBlocking {
        val gateway = RustHardwareProcessor(Dispatchers.Default)
        repeat(100) {
            assertEquals(Available(HardwareDatum.Number(-150.0, UnitKind.MILLIAMP)),
                gateway.process(MetricRequest(MetricOperation.CURRENT, -150000, unit = UnitKind.MILLIAMP)))
        }
        val absent = RustHardwareProcessor(Dispatchers.Default, { throw UnsatisfiedLinkError() }, { _,_,_ -> error("unreachable") })
        assertEquals(Unavailable(UnavailableReason.NATIVE_UNAVAILABLE), absent.process(MetricRequest(MetricOperation.LEVEL)))
    }
}
