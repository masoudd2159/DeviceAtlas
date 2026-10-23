package io.github.masoudd2159.deviceatlas.data.nativecore

import io.github.masoudd2159.deviceatlas.foundation.interactive.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RustSensorAnalyzerTest {
    @Test fun versionedTransportAndBoundedSampleShape() = runTest {
        val analyzer = RustSensorAnalyzer(StandardTestDispatcher(testScheduler),{}, { header, values, output ->
            assertArrayEquals(longArrayOf(1,1,3,1),header)
            assertArrayEquals(doubleArrayOf(3.0,4.0,12.0),values,0.0)
            assertTrue(output.all { it.isNaN() })
            doubleArrayOf(1.0,1.0,13.0,13.0,13.0,13.0,0.0).copyInto(output); 0
        })
        assertEquals(13.0,analyzer.summarize(SensorKind.ACCELEROMETER,listOf(SensorReading(listOf(3.0,4.0,12.0),0,0))).latest,0.0)
    }
    @Test fun malformedInputsNeverInvokeNative() = runTest {
        val analyzer = RustSensorAnalyzer(StandardTestDispatcher(testScheduler),{ error("must not load") },{ _,_,_ -> error("must not call") })
        for (samples in listOf(emptyList(), List(65) { SensorReading(listOf(1.0),0,0) },
            listOf(SensorReading(listOf(Double.NaN),0,0)),listOf(SensorReading(listOf(1.0,2.0),0,0)))) {
            try { analyzer.summarize(SensorKind.LIGHT,samples); fail() } catch (failure: InteractiveFailure) {
                assertEquals(InteractiveUnavailable.INVALID_DATA,failure.reason)
            }
        }
    }
    @Test fun unwrittenNonfiniteMalformedAndErrorOutputCannotBecomeSuccess() = runTest {
        val sample = listOf(SensorReading(listOf(0.0),0,0))
        for (mode in 0..6) {
            val analyzer = RustSensorAnalyzer(StandardTestDispatcher(testScheduler),{}, { _,_,out ->
                if (mode != 0) doubleArrayOf(1.0,1.0,0.0,0.0,0.0,0.0,0.0).copyInto(out)
                when (mode) { 1 -> out[0] = 2.0; 2 -> out[1] = 64.0; 3 -> out[4] = Double.POSITIVE_INFINITY; 4 -> out[6] = -1.0; 6 -> out[6] = 1.0 }
                if (mode == 5) 3 else 0
            })
            try { analyzer.summarize(SensorKind.LIGHT,sample); fail() } catch (_: InteractiveFailure) { }
        }
    }
    @Test fun linkageAndCancellationAreDistinct() = runTest {
        val samples = listOf(SensorReading(listOf(1.0),0,0))
        val missing = RustSensorAnalyzer(StandardTestDispatcher(testScheduler),{ throw UnsatisfiedLinkError() },{_,_,_->0})
        try { missing.summarize(SensorKind.LIGHT,samples); fail() } catch (failure: InteractiveFailure) { assertEquals(InteractiveUnavailable.NATIVE,failure.reason) }
        val canceled = RustSensorAnalyzer(StandardTestDispatcher(testScheduler),{ throw CancellationException() },{_,_,_->0})
        try { canceled.summarize(SensorKind.LIGHT,samples); fail() } catch (_: CancellationException) { }
    }
}
