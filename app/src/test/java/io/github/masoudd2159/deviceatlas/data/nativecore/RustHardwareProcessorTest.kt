package io.github.masoudd2159.deviceatlas.data.nativecore

import io.github.masoudd2159.deviceatlas.foundation.hardware.*
import io.github.masoudd2159.deviceatlas.foundation.hardware.HardwareValue.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class RustHardwareProcessorTest {
    @Test fun sendsVersionedIntegersWithoutLosingBytePrecision() = runTest {
        val gateway = RustHardwareProcessor(StandardTestDispatcher(testScheduler), {}, { input, real, output ->
            assertArrayEquals(longArrayOf(1, 1, Long.MAX_VALUE, 1), input)
            assertEquals(1, real.size); assertEquals(5, output.size)
            doubleArrayOf(8.0, 2.0, 6.0, 25.0, 75.0).copyInto(output); 0
        })
        val value = gateway.process(MetricRequest(MetricOperation.CAPACITY, Long.MAX_VALUE, 1))
        assertEquals(Long.MAX_VALUE, ((value as Available).value as HardwareDatum.Capacity).totalBytes)
    }

    @Test fun mapsEveryNativeErrorAndUnknownStatusWithoutUsingOutput() = runTest {
        listOf(1,2,3,4,5,6,7,8,99).forEach { status ->
            val gateway = RustHardwareProcessor(StandardTestDispatcher(testScheduler), {}, { _,_,_ -> status })
            assertEquals(Unavailable(if (status == 6) UnavailableReason.INVALID_RESPONSE else UnavailableReason.NATIVE_FAILED),
                gateway.process(MetricRequest(MetricOperation.LEVEL)))
        }
    }

    @Test fun rejectsPartialOrNonFiniteSuccess() = runTest {
        for (invalid in listOf(Double.NaN, Double.POSITIVE_INFINITY)) {
            val gateway = RustHardwareProcessor(StandardTestDispatcher(testScheduler), {}, { _,_,out -> out.fill(0.0); out[0] = invalid; 0 })
            assertEquals(Unavailable(UnavailableReason.NATIVE_FAILED), gateway.process(MetricRequest(MetricOperation.LEVEL)))
        }
        val gateway = RustHardwareProcessor(StandardTestDispatcher(testScheduler), {}, { _,_,out -> out[0] = 12.0; 0 })
        assertEquals(Unavailable(UnavailableReason.NATIVE_FAILED), gateway.process(MetricRequest(MetricOperation.LEVEL)))
    }

    @Test fun mapsLoadingAndSymbolFailures() = runTest {
        for (error in listOf(UnsatisfiedLinkError(), SecurityException())) {
            val gateway = RustHardwareProcessor(StandardTestDispatcher(testScheduler), { throw error }, { _,_,_ -> fail("must not call"); 0 })
            assertEquals(Unavailable(UnavailableReason.NATIVE_UNAVAILABLE), gateway.process(MetricRequest(MetricOperation.LEVEL)))
        }
        val gateway = RustHardwareProcessor(StandardTestDispatcher(testScheduler), {}, { _,_,_ -> throw UnsatisfiedLinkError() })
        assertEquals(Unavailable(UnavailableReason.NATIVE_UNAVAILABLE), gateway.process(MetricRequest(MetricOperation.LEVEL)))
    }

    @Test fun cancellationPropagates() = runTest {
        val gateway = RustHardwareProcessor(StandardTestDispatcher(testScheduler), { throw CancellationException() }, { _,_,_ -> 0 })
        try { gateway.process(MetricRequest(MetricOperation.LEVEL)); fail("cancellation swallowed") } catch (_: CancellationException) { }
    }
}
