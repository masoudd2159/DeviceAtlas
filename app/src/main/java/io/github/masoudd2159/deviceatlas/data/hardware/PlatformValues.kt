package io.github.masoudd2159.deviceatlas.data.hardware

import io.github.masoudd2159.deviceatlas.foundation.hardware.*
import io.github.masoudd2159.deviceatlas.foundation.hardware.HardwareValue.*

internal fun platformText(text: String?): HardwareValue<HardwareDatum> {
    val clean = text?.trim()?.filterNot { it.isISOControl() || it in '\u202a'..'\u202e' || it in '\u2066'..'\u2069' }?.take(512)
    return if (clean.isNullOrBlank() || clean.equals("unknown", true)) Unavailable(UnavailableReason.NOT_REPORTED)
        else Available(HardwareDatum.Text(clean))
}

internal fun numeric(operation: MetricOperation, first: Long = 0, second: Long = 0,
    real: Double = 0.0, unit: UnitKind = UnitKind.NONE): HardwareValue<HardwareDatum> =
    Available(HardwareDatum.Numeric(MetricRequest(operation, first, second, real, unit)))

internal fun batteryProperty(value: Long, sentinel: Long, operation: MetricOperation, unit: UnitKind): HardwareValue<HardwareDatum> =
    if (value == sentinel) Unavailable(UnavailableReason.NOT_REPORTED) else numeric(operation, value, unit = unit)

internal inline fun <T> collectSafely(block: () -> HardwareValue<T>): HardwareValue<T> = try { block() }
    catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
    catch (_: RuntimeException) { Unavailable(UnavailableReason.COLLECTION_FAILED) }

