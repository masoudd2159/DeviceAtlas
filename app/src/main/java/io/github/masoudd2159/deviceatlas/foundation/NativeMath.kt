package io.github.masoudd2159.deviceatlas.foundation

enum class MeanFailure { EMPTY_INPUT, NON_FINITE_INPUT, LIBRARY_UNAVAILABLE, NATIVE_FAILURE }

sealed interface MeanResult {
    data class Success(val value: Double) : MeanResult
    data class Failure(val reason: MeanFailure) : MeanResult
}

/** A testable capability boundary; no storage or Android types in its contract. */
fun interface NativeMath {
    suspend fun mean(values: DoubleArray): MeanResult
}
