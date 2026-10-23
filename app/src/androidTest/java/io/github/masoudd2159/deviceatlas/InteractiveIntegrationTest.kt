package io.github.masoudd2159.deviceatlas

import androidx.test.platform.app.InstrumentationRegistry
import io.github.masoudd2159.deviceatlas.data.interactive.AndroidInteractiveSensors
import io.github.masoudd2159.deviceatlas.data.nativecore.NativeBridge
import io.github.masoudd2159.deviceatlas.data.nativecore.RustSensorAnalyzer
import io.github.masoudd2159.deviceatlas.foundation.interactive.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class InteractiveIntegrationTest {
    @Test fun scalarVectorAndLegacyNativeProtocolsExecute() = runBlocking {
        System.loadLibrary("deviceatlas_core")
        val analyzer = RustSensorAnalyzer(Dispatchers.Default)
        val vector = analyzer.summarize(SensorKind.GYROSCOPE,listOf(SensorReading(listOf(-3.0,4.0,12.0),0,0)))
        assertEquals(13.0,vector.latest,0.0)
        val scalar = analyzer.summarize(SensorKind.LIGHT,listOf(SensorReading(listOf(0.0),0,0),SensorReading(listOf(10.0),1,0)))
        assertEquals(5.0,scalar.mean,0.0); assertEquals(10.0,scalar.range,0.0)
        val mean = DoubleArray(1); assertEquals(0,NativeBridge.mean(doubleArrayOf(2.0,4.0),mean)); assertEquals(3.0,mean[0],0.0)
    }
    @Test fun malformedSensorProtocolLeavesOutputsUntouched() {
        System.loadLibrary("deviceatlas_core")
        val out = DoubleArray(7) { 123.0 }; val header = longArrayOf(1,1,1,1)
        assertEquals(3,NativeBridge.processSensor(null,doubleArrayOf(1.0),out))
        assertEquals(3,NativeBridge.processSensor(header,null,out))
        assertEquals(3,NativeBridge.processSensor(header,doubleArrayOf(1.0),null))
        assertEquals(3,NativeBridge.processSensor(header,doubleArrayOf(1.0),DoubleArray(8)))
        assertEquals(6,NativeBridge.processSensor(longArrayOf(2,1,1,1),doubleArrayOf(1.0),out))
        assertEquals(6,NativeBridge.processSensor(longArrayOf(1,2,1,1),doubleArrayOf(1.0),out))
        for (dimensions in listOf(0L,2L,4L,Long.MAX_VALUE))
            assertEquals(3,NativeBridge.processSensor(longArrayOf(1,1,dimensions,1),doubleArrayOf(1.0),out))
        assertEquals(3,NativeBridge.processSensor(longArrayOf(1,1,1,65),DoubleArray(65),out))
        assertEquals(3,NativeBridge.processSensor(header,doubleArrayOf(),out))
        assertEquals(3,NativeBridge.processSensor(header,DoubleArray(193),out))
        assertEquals(2,NativeBridge.processSensor(header,doubleArrayOf(Double.NaN),out))
        assertEquals(2,NativeBridge.processSensor(longArrayOf(1,1,3,1),DoubleArray(3) { Double.MAX_VALUE },out))
        assertTrue(out.all { it == 123.0 })
    }
    @Test fun publicSensorsCanRegisterOrReportTypedAvailabilityAndAlwaysClose() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val source = AndroidInteractiveSensors(instrumentation.targetContext)
            SensorKind.entries.forEach { kind ->
                try { val subscription = source.open(kind) { }; subscription.second.close(); subscription.second.close()
                } catch (failure: InteractiveFailure) {
                    assertTrue(failure.reason in listOf(InteractiveUnavailable.NOT_PRESENT,InteractiveUnavailable.REGISTRATION_FAILED,InteractiveUnavailable.PLATFORM))
                }
            }
        }
    }
}
