package io.github.masoudd2159.deviceatlas.data.nativecore

import io.github.masoudd2159.deviceatlas.di.NativeDispatcher
import io.github.masoudd2159.deviceatlas.foundation.hardware.*
import io.github.masoudd2159.deviceatlas.foundation.hardware.HardwareValue.*
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

class RustHardwareProcessor internal constructor(
    private val dispatcher: CoroutineDispatcher,
    private val load: () -> Unit,
    private val invoke: (LongArray, DoubleArray, DoubleArray) -> Int,
) : HardwareProcessor {
    @Inject constructor(@NativeDispatcher dispatcher: CoroutineDispatcher) : this(
        dispatcher, { System.loadLibrary("deviceatlas_core") }, NativeBridge::processHardware,
    )

    override suspend fun process(request: MetricRequest): HardwareValue<HardwareDatum> = withContext(dispatcher) {
        try {
            coroutineContext.ensureActive()
            load()
            val output = DoubleArray(5) { Double.NaN }
            val status = invoke(longArrayOf(1, request.operation.wireId, request.first, request.second),
                doubleArrayOf(request.real), output)
            coroutineContext.ensureActive()
            when {
                status == 6 -> Unavailable(UnavailableReason.INVALID_RESPONSE)
                status != 0 || output.any { !it.isFinite() } -> Unavailable(UnavailableReason.NATIVE_FAILED)
                else -> Available(when (request.operation) {
                    MetricOperation.CAPACITY -> HardwareDatum.Capacity(request.first, request.second,
                        output[0], output[1], output[2], output[3], output[4])
                    MetricOperation.DISPLAY -> HardwareDatum.DisplayMode(request.first, request.second, output[0].toLong(), output[1].toLong())
                    else -> HardwareDatum.Number(output[0], request.unit)
                })
            }
        } catch (cancelled: CancellationException) { throw cancelled
        } catch (_: LinkageError) { Unavailable(UnavailableReason.NATIVE_UNAVAILABLE)
        } catch (_: SecurityException) { Unavailable(UnavailableReason.NATIVE_UNAVAILABLE)
        } catch (_: RuntimeException) { Unavailable(UnavailableReason.NATIVE_FAILED) }
    }
}
