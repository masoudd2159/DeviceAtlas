package io.github.masoudd2159.deviceatlas.data.nativecore

import io.github.masoudd2159.deviceatlas.foundation.MeanFailure
import io.github.masoudd2159.deviceatlas.foundation.MeanResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

/** These isolate Kotlin validation/mapping. Real JNI is tested on Android. */
@OptIn(ExperimentalCoroutinesApi::class)
class RustNativeMathTest {
    private val dispatcher = StandardTestDispatcher()

    @Test fun rejectsEmptyInputBeforeLoading() = runTest(dispatcher) {
        val gateway = RustNativeMath(dispatcher, { error("must not load") }, { _, _ -> error("must not call") })
        assertEquals(failure(MeanFailure.EMPTY_INPUT), gateway.mean(doubleArrayOf()))
    }

    @Test fun rejectsNonFiniteInputsBeforeLoading() = runTest(dispatcher) {
        val gateway = RustNativeMath(dispatcher, { error("must not load") }, { _, _ -> error("must not call") })
        listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY).forEach {
            assertEquals(failure(MeanFailure.NON_FINITE_INPUT), gateway.mean(doubleArrayOf(1.0, it)))
        }
    }

    @Test fun loadsBeforeInvokingAndMapsOutput() = runTest(dispatcher) {
        var loaded = false
        val gateway = RustNativeMath(dispatcher, { loaded = true }, { input, output ->
            assertTrue(loaded)
            assertArrayEquals(doubleArrayOf(2.0, 4.0), input, 0.0)
            output[0] = 3.0
            0
        })
        assertEquals(MeanResult.Success(3.0), gateway.mean(doubleArrayOf(2.0, 4.0)))
    }

    @Test fun mapsAllNativeFailureStatusesWithoutReadingOutput() = runTest(dispatcher) {
        mapOf(1 to MeanFailure.EMPTY_INPUT, 2 to MeanFailure.NON_FINITE_INPUT,
            3 to MeanFailure.NATIVE_FAILURE, 4 to MeanFailure.NATIVE_FAILURE,
            5 to MeanFailure.NATIVE_FAILURE, 999 to MeanFailure.NATIVE_FAILURE).forEach { (status, reason) ->
            val gateway = RustNativeMath(dispatcher, {}, { _, output -> output[0] = Double.NaN; status })
            assertEquals(failure(reason), gateway.mean(doubleArrayOf(1.0)))
        }
    }

    @Test fun rejectsNonFiniteSuccessOutput() = runTest(dispatcher) {
        listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY).forEach { value ->
            val gateway = RustNativeMath(dispatcher, {}, { _, output -> output[0] = value; 0 })
            assertEquals(failure(MeanFailure.NATIVE_FAILURE), gateway.mean(doubleArrayOf(1.0)))
        }
    }

    @Test fun mapsUnavailableLibraryWithoutCallingNative() = runTest(dispatcher) {
        val gateway = RustNativeMath(dispatcher, { throw UnsatisfiedLinkError("missing") },
            { _, _ -> error("must not call") })
        assertEquals(failure(MeanFailure.LIBRARY_UNAVAILABLE), gateway.mean(doubleArrayOf(1.0)))
    }

    @Test fun mapsLoadSecurityFailure() = runTest(dispatcher) {
        val gateway = RustNativeMath(dispatcher, { throw SecurityException() }, { _, _ -> 0 })
        assertEquals(failure(MeanFailure.LIBRARY_UNAVAILABLE), gateway.mean(doubleArrayOf(1.0)))
    }

    @Test fun mapsMissingNativeSymbol() = runTest(dispatcher) {
        val gateway = RustNativeMath(dispatcher, {}, { _, _ -> throw UnsatisfiedLinkError("symbol") })
        assertEquals(failure(MeanFailure.LIBRARY_UNAVAILABLE), gateway.mean(doubleArrayOf(1.0)))
    }

    @Test fun mapsUnexpectedNativeException() = runTest(dispatcher) {
        val gateway = RustNativeMath(dispatcher, {}, { _, _ -> error("failed") })
        assertEquals(failure(MeanFailure.NATIVE_FAILURE), gateway.mean(doubleArrayOf(1.0)))
    }

    @Test fun cancellationPropagates() = runTest(dispatcher) {
        val gateway = RustNativeMath(dispatcher, {}, { _, _ -> throw CancellationException("cancelled") })
        try {
            gateway.mean(doubleArrayOf(1.0))
            fail("Cancellation must propagate")
        } catch (expected: CancellationException) {
            assertEquals("cancelled", expected.message)
        }
    }

    private fun failure(reason: MeanFailure) = MeanResult.Failure(reason)
}
