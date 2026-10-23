package io.github.masoudd2159.deviceatlas.data.monitoring

import io.github.masoudd2159.deviceatlas.foundation.hardware.HardwareValue.*
import io.github.masoudd2159.deviceatlas.foundation.hardware.UnavailableReason
import org.junit.Assert.*
import org.junit.Test

class HeadroomCacheTest {
    @Test fun sharedTenSecondWindowPreservesMissingAndValuesAboveOne() {
        val cache=HeadroomCache(); var calls=0
        fun read(time: Long) = cache.read(time) { calls++; if(calls==1) Unavailable(UnavailableReason.TEMPORARY) else Available(1.5) }
        assertTrue(read(0) is Unavailable)
        assertTrue(read(5_000_000_000) is Unavailable)
        assertTrue(read(9_999_999_999) is Unavailable); assertEquals(1,calls)
        assertEquals(Available(1.5),read(10_000_000_000)); assertEquals(2,calls)
        read(0); read(19_999_999_999); assertEquals(2,calls)
    }
}
