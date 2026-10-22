package io.github.masoudd2159.deviceatlas.data.nativecore

import io.github.masoudd2159.deviceatlas.di.NativeDispatcher
import io.github.masoudd2159.deviceatlas.foundation.MeanFailure
import io.github.masoudd2159.deviceatlas.foundation.MeanResult
import io.github.masoudd2159.deviceatlas.foundation.NativeMath
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

class RustNativeMath internal constructor(
    private val dispatcher: CoroutineDispatcher,
    private val load: () -> Unit,
    private val invoke: (DoubleArray, DoubleArray) -> Int,
) : NativeMath {
    @Inject constructor(@NativeDispatcher dispatcher: CoroutineDispatcher) : this(
        dispatcher,
        { System.loadLibrary("deviceatlas_core") },
        { values, output -> NativeBridge.mean(values, output) },
    )

    override suspend fun mean(values: DoubleArray): MeanResult = withContext(dispatcher) {
        when {
            values.isEmpty() -> MeanResult.Failure(MeanFailure.EMPTY_INPUT)
            values.any { !it.isFinite() } -> MeanResult.Failure(MeanFailure.NON_FINITE_INPUT)
            else -> try {
                // Loading is explicit, idempotent for this class loader, and off the UI thread.
                load()
                val output = DoubleArray(1)
                when (invoke(values, output)) {
                    0 -> if (output[0].isFinite()) MeanResult.Success(output[0])
                        else MeanResult.Failure(MeanFailure.NATIVE_FAILURE)
                    1 -> MeanResult.Failure(MeanFailure.EMPTY_INPUT)
                    2 -> MeanResult.Failure(MeanFailure.NON_FINITE_INPUT)
                    else -> MeanResult.Failure(MeanFailure.NATIVE_FAILURE)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: LinkageError) {
                MeanResult.Failure(MeanFailure.LIBRARY_UNAVAILABLE)
            } catch (_: SecurityException) {
                MeanResult.Failure(MeanFailure.LIBRARY_UNAVAILABLE)
            } catch (_: RuntimeException) {
                MeanResult.Failure(MeanFailure.NATIVE_FAILURE)
            }
        }
    }
}
