package io.github.masoudd2159.deviceatlas.data.benchmark

import io.github.masoudd2159.deviceatlas.foundation.hardware.HardwareValue.*
import io.github.masoudd2159.deviceatlas.foundation.hardware.UnavailableReason
import io.github.masoudd2159.deviceatlas.foundation.monitoring.MonitoringSource
import io.github.masoudd2159.deviceatlas.presentation.monitoring.observation
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class AndroidBenchmarkSafetySourceTest {
    @Test fun batteryPercentValidationAndAbsenceRemainExplicit() = runTest {
        val missing=Unavailable(UnavailableReason.NOT_PRESENT)
        var raw=observation().copy(batteryLevel=Available(1),batteryScale=Available(4))
        val source=AndroidBenchmarkSafetySource(MonitoringSource { raw })
        assertEquals(Available(25.0),source.read().batteryPercent)
        raw=raw.copy(batteryScale=Available(0))
        assertEquals(Unavailable(UnavailableReason.INVALID_RESPONSE),source.read().batteryPercent)
        raw=raw.copy(batteryLevel=Available(-1),batteryScale=Available(100))
        assertEquals(Unavailable(UnavailableReason.INVALID_RESPONSE),source.read().batteryPercent)
        raw=raw.copy(batteryLevel=missing)
        assertEquals(missing,source.read().batteryPercent)
    }
}
