package io.github.masoudd2159.deviceatlas.data.hardware

import io.github.masoudd2159.deviceatlas.foundation.hardware.*
import io.github.masoudd2159.deviceatlas.foundation.hardware.HardwareValue.Available
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Serializes platform reads. Raw metadata stays Kotlin-owned; all numbers pass Rust. */
@Singleton
class ProcessedHardwareReader @Inject constructor(
    private val collector: HardwareCollector, private val processor: HardwareProcessor,
) : HardwareReader {
    private val lock = Mutex()
    override suspend fun read(): HardwareSnapshot = lock.withLock {
        val raw = collector.collect()
        suspend fun convert(value: HardwareValue<HardwareDatum>): HardwareValue<HardwareDatum> =
            if (value is Available && value.value is HardwareDatum.Numeric) processor.process(value.value.request) else value
        val fields = linkedMapOf<HardwareField, HardwareValue<HardwareDatum>>()
        raw.fields.forEach { (key,value) -> fields[key] = convert(value) }
        val sensors = when (val inventory = raw.sensors) {
            is HardwareValue.Unavailable -> inventory
            is Available -> Available(immutableList(inventory.value.map { sensor ->
                val converted = linkedMapOf<SensorField, HardwareValue<HardwareDatum>>()
                sensor.fields.forEach { (key,value) -> converted[key] = convert(value) }
                sensor.copy(fields = immutableMap(converted))
            }))
        }
        raw.copy(fields = immutableMap(fields), sensors = sensors)
    }
}
