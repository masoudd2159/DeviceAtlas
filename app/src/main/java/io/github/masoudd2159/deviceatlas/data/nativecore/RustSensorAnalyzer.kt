package io.github.masoudd2159.deviceatlas.data.nativecore

import io.github.masoudd2159.deviceatlas.di.NativeDispatcher
import io.github.masoudd2159.deviceatlas.foundation.interactive.*
import javax.inject.Inject
import kotlinx.coroutines.*

class RustSensorAnalyzer internal constructor(private val dispatcher: CoroutineDispatcher, private val load: () -> Unit,
    private val invoke: (LongArray, DoubleArray, DoubleArray) -> Int) : SensorAnalyzer {
    @Inject constructor(@NativeDispatcher dispatcher: CoroutineDispatcher) : this(dispatcher,
        { System.loadLibrary("deviceatlas_core") }, NativeBridge::processSensor)
    override suspend fun summarize(kind: SensorKind, samples: List<SensorReading>): SensorSummary = withContext(dispatcher) {
        fun valid(test: Boolean) { if (!test) throw InteractiveFailure(InteractiveUnavailable.INVALID_DATA) }
        valid(samples.size in 1..64 && samples.all { it.values.size == kind.dimensions && it.values.all(Double::isFinite) })
        try {
            ensureActive(); load()
            val output = DoubleArray(7) { Double.NaN }
            val code = invoke(longArrayOf(1, 1, kind.dimensions.toLong(), samples.size.toLong()),
                samples.flatMap { it.values }.toDoubleArray(), output)
            ensureActive()
            if (code != 0) throw InteractiveFailure(InteractiveUnavailable.NATIVE)
            valid(output.all(Double::isFinite) && output[0] == 1.0 && output[1] == samples.size.toDouble() &&
                output[2] <= output[3] && output[4] in output[2]..output[3] && output[5] in output[2]..output[3] &&
                output[6] == output[3] - output[2] && (kind.dimensions == 1 || output[2] >= 0))
            SensorSummary(output[1].toInt(), output[2], output[3], output[4], output[5], output[6])
        } catch (cancelled: CancellationException) { throw cancelled
        } catch (failure: InteractiveFailure) { throw failure
        } catch (_: LinkageError) { throw InteractiveFailure(InteractiveUnavailable.NATIVE)
        } catch (_: RuntimeException) { throw InteractiveFailure(InteractiveUnavailable.NATIVE) }
    }
}
