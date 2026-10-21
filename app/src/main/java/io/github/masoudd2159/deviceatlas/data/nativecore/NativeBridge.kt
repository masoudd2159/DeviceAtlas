package io.github.masoudd2159.deviceatlas.data.nativecore

/** JNI ABI. Keep the Rust export and R8 rule synchronized with this declaration. */
internal object NativeBridge {
    // Nullable buffers allow integration tests to exercise native validation.
    external fun mean(values: DoubleArray?, output: DoubleArray?): Int
}
