package io.github.masoudd2159.deviceatlas

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.masoudd2159.deviceatlas.data.nativecore.NativeBridge
import io.github.masoudd2159.deviceatlas.data.nativecore.RustNativeMath
import io.github.masoudd2159.deviceatlas.foundation.MeanFailure
import io.github.masoudd2159.deviceatlas.foundation.MeanResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NativeCoreSmokeTest {
    @Test fun kotlinGatewayExecutesPackagedRust() = runBlocking {
        assertEquals(MeanResult.Success(4.0),
            RustNativeMath(Dispatchers.Default).mean(doubleArrayOf(2.0, 4.0, 6.0)))
    }

    @Test fun jniHandlesValidValuesAndNumericEdges() {
        System.loadLibrary("deviceatlas_core")
        listOf(
            doubleArrayOf(7.25) to 7.25,
            doubleArrayOf(-4.0, 2.0, 8.0) to 2.0,
            doubleArrayOf(Double.MAX_VALUE, Double.MAX_VALUE) to Double.MAX_VALUE,
            doubleArrayOf(Double.MAX_VALUE, -Double.MAX_VALUE) to 0.0,
            doubleArrayOf(Double.MIN_VALUE) to Double.MIN_VALUE,
        ).forEach { (values, expected) ->
            val output = DoubleArray(1)
            assertEquals(0, NativeBridge.mean(values, output))
            assertEquals(expected, output[0], 0.0)
        }
    }

    @Test fun nativeLayerRejectsInvalidInputIndependentlyOfKotlin() {
        System.loadLibrary("deviceatlas_core")
        val output = doubleArrayOf(123.0)
        assertEquals(1, NativeBridge.mean(doubleArrayOf(), output))
        listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY).forEach {
            assertEquals(2, NativeBridge.mean(doubleArrayOf(1.0, it), output))
        }
        assertEquals(123.0, output[0], 0.0) // Failure must not publish a value.
    }

    @Test fun jniRetainsSmallResidualAfterExtremeCancellation() {
        System.loadLibrary("deviceatlas_core")
        val output = DoubleArray(1)
        assertEquals(0, NativeBridge.mean(doubleArrayOf(Double.MAX_VALUE, 1e-15, -Double.MAX_VALUE), output))
        assertEquals(1e-15 / 3.0, output[0], 1e-30)
        assertEquals(0, NativeBridge.mean(
            doubleArrayOf(Double.MAX_VALUE, Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE, 1e-15), output))
        assertEquals(1e-15 / 5.0, output[0], 1e-30)
    }

    @Test fun nativeLayerRejectsNullAndInvalidOutputBuffers() {
        System.loadLibrary("deviceatlas_core")
        assertEquals(3, NativeBridge.mean(null, DoubleArray(1)))
        assertEquals(3, NativeBridge.mean(doubleArrayOf(1.0), null))
        assertEquals(3, NativeBridge.mean(doubleArrayOf(1.0), DoubleArray(0)))
        assertEquals(3, NativeBridge.mean(doubleArrayOf(1.0), DoubleArray(2)))
    }

    @Test fun repeatedCallsDoNotLeavePendingExceptionsOrModifyInput() {
        System.loadLibrary("deviceatlas_core")
        val input = doubleArrayOf(-2.0, 2.0, 6.0)
        repeat(1000) {
            val output = DoubleArray(1)
            assertEquals(0, NativeBridge.mean(input, output))
            assertEquals(2.0, output[0], 1e-12)
        }
        assertArrayEquals(doubleArrayOf(-2.0, 2.0, 6.0), input, 0.0)
    }

    @Test fun actualMissingLibraryMapsToTypedFailure() = runBlocking {
        val gateway = RustNativeMath(Dispatchers.Default,
            { System.loadLibrary("deviceatlas_deliberately_missing") },
            { _, _ -> error("JNI must not be called") })
        assertEquals(MeanResult.Failure(MeanFailure.LIBRARY_UNAVAILABLE),
            gateway.mean(doubleArrayOf(1.0)))
    }
}
