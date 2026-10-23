package io.github.masoudd2159.deviceatlas.data.nativecore

import io.github.masoudd2159.deviceatlas.foundation.hardware.*
import io.github.masoudd2159.deviceatlas.foundation.hardware.HardwareValue.*
import io.github.masoudd2159.deviceatlas.foundation.monitoring.*
import io.github.masoudd2159.deviceatlas.presentation.monitoring.observation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class RustMonitoringProcessorTest {
    @Test fun boundedSampleWireAndPartialAvailabilityArePreserved() = runTest {
        var input = longArrayOf()
        val p = RustMonitoringProcessor(StandardTestDispatcher(testScheduler), {}, { a, _, output ->
            input = a; output.fill(0.0); output[17] = 200.0; output[16] = 9.0; 0
        })
        val raw = observation().copy(currentMicroamps = Unavailable(UnavailableReason.NOT_REPORTED))
        val sample = p.sample(raw, null)
        assertEquals(15, input.size); assertEquals(Long.MIN_VALUE, input[13])
        assertEquals(Unavailable(UnavailableReason.NOT_REPORTED), sample.values[LiveMetric.BATTERY_CURRENT])
        assertEquals(Unavailable(UnavailableReason.BASELINE), sample.values[LiveMetric.PROCESS_CPU])
        assertEquals(Available(0.0), sample.values[LiveMetric.BATTERY_LEVEL])
    }
    @Test fun cpuAbove100IsNotClamped() = runTest {
        val p = RustMonitoringProcessor(StandardTestDispatcher(testScheduler), {}, { _, _, out -> out.fill(0.0); out[17] = 250.0; 0 })
        assertEquals(Available(250.0), p.sample(observation(), observation()).values[LiveMetric.PROCESS_CPU])
    }
    @Test fun nativeFailuresAndNonfiniteOutputsCannotAppearAsValues() = runTest {
        for ((code,reason) in listOf(6 to UnavailableReason.INVALID_RESPONSE, 3 to UnavailableReason.NATIVE_FAILED, 5 to UnavailableReason.NATIVE_FAILED)) {
            val p = RustMonitoringProcessor(StandardTestDispatcher(testScheduler), {}, { _, _, _ -> code })
            assertEquals(Unavailable(reason), p.sample(observation(), null).values[LiveMetric.AVAILABLE_RAM])
        }
        val p = RustMonitoringProcessor(StandardTestDispatcher(testScheduler), {}, { _, _, _ -> 0 })
        assertEquals(Unavailable(UnavailableReason.NATIVE_FAILED), p.sample(observation(), null).values[LiveMetric.AVAILABLE_RAM])
    }
    @Test fun unavailableLibraryAndCancellationRemainDistinct() = runTest {
        val p = RustMonitoringProcessor(StandardTestDispatcher(testScheduler), { throw UnsatisfiedLinkError() }, { _,_,_ -> 0 })
        assertEquals(Unavailable(UnavailableReason.NATIVE_UNAVAILABLE), p.sample(observation(), null).values[LiveMetric.PROCESS_CPU])
        val cancel = RustMonitoringProcessor(StandardTestDispatcher(testScheduler), { throw CancellationException() }, { _,_,_ -> 0 })
        try { cancel.sample(observation(), null); fail("cancellation swallowed") } catch (_: CancellationException) { }
    }
    @Test fun summariesSendMasksAndRejectExcessiveHistoryBeforeJni() = runTest {
        var calls = 0
        val p = RustMonitoringProcessor(StandardTestDispatcher(testScheduler), {}, { input, real, out ->
            calls++; assertEquals(2L, input[1]); assertEquals(1L shl 7, input[3]); assertEquals(-150.0, real[7], 0.0)
            out.fill(0.0); out[35]=-150.0; out[36]=-150.0; out[37]=-150.0; out[38]=-150.0; out[39]=1.0; 0
        })
        val s = ProcessedObservation(observation(), mapOf(LiveMetric.BATTERY_CURRENT to Available(-150.0)))
        val summaries = p.summarize(listOf(s))
        assertEquals(Available(MetricSummary(-150.0,-150.0,-150.0,-150.0,1)), summaries[LiveMetric.BATTERY_CURRENT])
        assertEquals(Unavailable(UnavailableReason.INSUFFICIENT), summaries[LiveMetric.PROCESS_CPU])
        p.summarize(List(121) { s }); assertEquals(1,calls)
    }
}
