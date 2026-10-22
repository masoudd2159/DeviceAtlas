# JNI resolves this class and method by name. Preserve the ABI in release builds.
-keep class io.github.masoudd2159.deviceatlas.data.nativecore.NativeBridge {
    native <methods>;
}
