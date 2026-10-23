package io.github.masoudd2159.deviceatlas.data.nativecore

import io.github.masoudd2159.deviceatlas.di.NativeDispatcher
import io.github.masoudd2159.deviceatlas.foundation.benchmark.*
import io.github.masoudd2159.deviceatlas.foundation.hardware.immutableList
import javax.inject.Inject
import kotlinx.coroutines.*
import kotlin.coroutines.coroutineContext

class RustBenchmarkProcessor internal constructor(
    private val dispatcher: CoroutineDispatcher, private val load: () -> Unit,
    private val invoke: (LongArray, DoubleArray, DoubleArray) -> Int,
) : BenchmarkProcessor {
    @Inject constructor(@NativeDispatcher dispatcher: CoroutineDispatcher) : this(
        dispatcher, { System.loadLibrary("deviceatlas_core") }, NativeBridge::processBenchmark)

    override suspend fun chunk(workload: Workload, conservative: Boolean): BenchmarkChunk {
        val cap = if (workload == Workload.MEMORY) 64L else 1_999_872L
        val bytes = if (workload == Workload.MEMORY) { if (conservative) 65_536L else 262_144L } else 0L
        val out = call(longArrayOf(1, 1, workload.wire, cap, if (conservative) 5_000 else 15_000, bytes), doubleArrayOf())
        val maxWork = if (bytes > 0) bytes * cap else cap
        valid(out[0] == 1.0 && out[1] == workload.wire.toDouble() && out[2] == 1.0 &&
            integer(out[3], 1, maxWork) && integer(out[4], 1, 2_000_000_000) && out[5] > 0 &&
            integer(out[6], 0, 0xffff_ffffL) && integer(out[7], 0, 0xffff_ffffL) && out[8] == 1.0 && out[9] == 0.0)
        valid(if (bytes > 0) out[3].toLong() % bytes == 0L else out[3].toLong() % 256 == 0L)
        return BenchmarkChunk(BenchmarkRun(out[3].toLong(), out[4].toLong()), out[6].toLong(), out[7].toLong())
    }

    override suspend fun summarize(workload: Workload, runs: List<BenchmarkRun>): BenchmarkResult {
        valid(runs.size in 1..3 && runs.all { it.work in 1..4_294_967_296L && it.nanos in 1..2_000_000_000L })
        val out = call(longArrayOf(1, 2, workload.wire, runs.size.toLong(), 0, 0),
            runs.flatMap { listOf(it.work.toDouble(), it.nanos.toDouble()) }.toDoubleArray())
        valid(out[0] == 1.0 && out[1] == workload.wire.toDouble() && out[2] == 1.0 &&
            out[3] == runs.sumOf { it.work }.toDouble() && out[4] == runs.sumOf { it.nanos }.toDouble() &&
            out[6] > 0 && out[7] >= out[6] && out[5] in out[6]..out[7] && out[8] >= 0 && out[9] == runs.size.toDouble())
        return BenchmarkResult(workload, immutableList(runs), out[3].toLong(), out[4].toLong(), out[5], out[6], out[7], out[8])
    }

    private suspend fun call(input: LongArray, real: DoubleArray): DoubleArray = withContext(dispatcher) {
        try {
            coroutineContext.ensureActive(); load()
            val output = DoubleArray(10) { Double.NaN }
            val status = invoke(input, real, output)
            coroutineContext.ensureActive()
            if (status != 0) throw BenchmarkFailure(if (status == 6) BenchmarkError.INVALID_RESULT else BenchmarkError.NATIVE_FAILED)
            valid(output.all { it.isFinite() })
            output
        } catch (cancelled: CancellationException) { throw cancelled
        } catch (failure: BenchmarkFailure) { throw failure
        } catch (_: LinkageError) { throw BenchmarkFailure(BenchmarkError.NATIVE_UNAVAILABLE)
        } catch (_: SecurityException) { throw BenchmarkFailure(BenchmarkError.NATIVE_UNAVAILABLE)
        } catch (_: RuntimeException) { throw BenchmarkFailure(BenchmarkError.NATIVE_FAILED) }
    }
    private fun integer(value: Double, min: Long, max: Long) = value % 1.0 == 0.0 && value >= min && value <= max
    private fun valid(value: Boolean) { if (!value) throw BenchmarkFailure(BenchmarkError.INVALID_RESULT) }
}
