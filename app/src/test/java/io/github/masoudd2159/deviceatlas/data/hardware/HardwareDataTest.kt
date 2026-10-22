package io.github.masoudd2159.deviceatlas.data.hardware

import io.github.masoudd2159.deviceatlas.foundation.hardware.*
import io.github.masoudd2159.deviceatlas.foundation.hardware.HardwareValue.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class HardwareDataTest {
    @Test fun sanitizesMissingAndUnusableMetadata() {
        listOf(null, "", "  ", "unknown", " UNKNOWN ").forEach {
            assertEquals(Unavailable(UnavailableReason.NOT_REPORTED), platformText(it))
        }
        assertEquals(Available(HardwareDatum.Text("Model 123")), platformText(" Model 123 "))
        assertEquals(Available(HardwareDatum.Text("AB")), platformText("A\u202e\nB"))
    }

    @Test fun batterySentinelsDoNotBecomeZero() {
        assertEquals(Unavailable(UnavailableReason.NOT_REPORTED), batteryProperty(Int.MIN_VALUE.toLong(), Int.MIN_VALUE.toLong(), MetricOperation.CURRENT, UnitKind.MILLIAMP))
        assertEquals(Unavailable(UnavailableReason.NOT_REPORTED), batteryProperty(Long.MIN_VALUE, Long.MIN_VALUE, MetricOperation.ENERGY, UnitKind.WATT_HOUR))
        val zero = batteryProperty(0, Int.MIN_VALUE.toLong(), MetricOperation.CURRENT, UnitKind.MILLIAMP)
        assertEquals(numeric(MetricOperation.CURRENT, 0, unit = UnitKind.MILLIAMP), zero)
    }

    @Test fun collectorFailuresStayTypedAndCancellationEscapes() {
        assertEquals(Unavailable(UnavailableReason.COLLECTION_FAILED), collectSafely<String> { error("OEM") })
        assertThrows(CancellationException::class.java) { collectSafely<String> { throw CancellationException() } }
    }

    @Test fun readerProcessesOnlyNumericFieldsAndKeepsPartialFailures() = runTest {
        val fields = immutableMap(mapOf(
            HardwareField.MODEL to platformText("Actual platform model"),
            HardwareField.MEMORY to numeric(MetricOperation.CAPACITY, 100, 200),
            HardwareField.BATTERY_LEVEL to Unavailable(UnavailableReason.NOT_PRESENT),
        ))
        var calls = 0
        val reader = ProcessedHardwareReader(HardwareCollector { HardwareSnapshot(fields, Available(emptyList()), 42) },
            HardwareProcessor { calls++; Unavailable(UnavailableReason.INVALID_RESPONSE) })
        val result = reader.read()
        assertEquals(1, calls)
        assertEquals(fields[HardwareField.MODEL], result.fields[HardwareField.MODEL])
        assertEquals(Unavailable(UnavailableReason.INVALID_RESPONSE), result.fields[HardwareField.MEMORY])
        assertEquals(Unavailable(UnavailableReason.NOT_PRESENT), result.fields[HardwareField.BATTERY_LEVEL])
        assertEquals(42, result.capturedAtMillis)
        assertEquals(Available(emptyList<SensorCapability>()), result.sensors)
    }

    @Test fun readerProcessesSensorsWithoutChangingNamesOrIndices() = runTest {
        val sensor = SensorCapability(7, 1, mapOf(SensorField.NAME to platformText("OEM"), SensorField.POWER to numeric(MetricOperation.NONNEGATIVE_REAL, real = 1.5)))
        val reader = ProcessedHardwareReader(HardwareCollector { HardwareSnapshot(emptyMap(), Available(listOf(sensor)), 9) },
            HardwareProcessor { Available(HardwareDatum.Number(it.real, it.unit)) })
        val result = (reader.read().sensors as Available).value.single()
        assertEquals(7, result.index)
        assertEquals(sensor.fields[SensorField.NAME], result.fields[SensorField.NAME])
        assertEquals(Available(HardwareDatum.Number(1.5, UnitKind.NONE)), result.fields[SensorField.POWER])
    }

    @Test fun fieldMetadataHasAccurateScopesAndFreshness() {
        assertEquals(Source.STAT_FS, HardwareField.STORAGE.source)
        assertEquals(Freshness.SNAPSHOT, HardwareField.MEMORY.freshness)
        assertEquals(Freshness.SESSION, HardwareField.MODEL.freshness)
        assertEquals(Source.DEVICE_CONFIGURATION, HardwareField.GLES.source)
        assertEquals(Section.DISPLAY, HardwareField.REFRESH_RATE.section)
    }

    @Test fun publishedCollectionsAreDefensiveCopies() {
        val mutable = mutableMapOf("a" to 1)
        val frozen = immutableMap(mutable)
        mutable["a"] = 2
        assertEquals(1, frozen["a"])
        assertThrows(UnsupportedOperationException::class.java) { (frozen as MutableMap)["b"] = 3 }
    }
}
