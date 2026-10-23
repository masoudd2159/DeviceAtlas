package io.github.masoudd2159.deviceatlas.data.nativecore

/** JNI ABI. Keep the Rust export and R8 rule synchronized with this declaration. */
internal object NativeBridge {
    // Nullable buffers allow integration tests to exercise native validation.
    external fun mean(values: DoubleArray?, output: DoubleArray?): Int
    external fun processHardware(input: LongArray?, real: DoubleArray?, output: DoubleArray?): Int
    external fun processMonitoring(input: LongArray?, real: DoubleArray?, output: DoubleArray?): Int
    external fun processBenchmark(input: LongArray?, real: DoubleArray?, output: DoubleArray?): Int
}
