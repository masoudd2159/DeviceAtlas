# Native sensor analysis v1

`NativeBridge.processSensor(LongArray?, DoubleArray?, DoubleArray?): Int` is a
separate synchronous ABI; mean, hardware, monitoring and benchmark exports retain
their existing shapes and semantics. The existing NativeBridge R8 native-method
rule covers the new export without widening production keep rules.

Header: exactly four longs `[version=1, operation=1, dimensions=1|3, count=1..64]`.
Input: exactly `dimensions × count` doubles, with at most 192 values. Vector
channels are ordered X/Y/Z per sample. Output: exactly seven doubles:

| Slot | Meaning |
| --- | --- |
| 0 | Version, 1 |
| 1 | Valid accepted observation count |
| 2 | Minimum scalar or vector magnitude |
| 3 | Maximum scalar or vector magnitude |
| 4 | Arithmetic mean |
| 5 | Latest scalar or vector magnitude |
| 6 | Maximum minus minimum |

`diagnostics::shape` validates the bounded header before JNI copies values. The
bridge checks nulls, exact array lengths, required function-table entries, and
JNI exceptions. Fixed stack arrays hold four longs and up to 192 doubles. There
are no retained JNI handles, platform references, threads, or native global state.
Rust panics are caught in the existing boundary for debug and release.

Status 0 means the entire result was written successfully; 2 means nonfinite
input/derived result, 3 invalid/null buffers or dimensions/count, 4 JNI failure,
5 contained panic, and 6 unsupported version/operation/header shape. Any failed
numerical or protocol validation returns before writing the output. A JNI write
exception is returned as failure and the caller does not consume output.

Scalar samples must all be finite. Missing data is represented by no batch, not
zero or NaN. Zero and negative scalar readings are numerically valid. Vectors use
chained `hypot`, preserving tiny/large finite values without squaring overflow.
Unrepresentable magnitudes or ranges are rejected. The arithmetic mean reuses
the existing compensated, overflow-aware implementation. Statistics describe only
the bounded accepted observation window, not calibration or a hardware diagnosis.

The Kotlin gateway checks dimensions, count, finite values, output version/count,
finite result slots, min/mean/max/latest bounds, nonnegative vector magnitude,
and range consistency. Output starts as NaN to expose unwritten results. Calls
run on the injected native dispatcher and rethrow cancellation; the session holds
its coordinator lease until a canceled synchronous native call actually returns.

Pure Rust tests cover shape errors, empty/missing/oversized input, 64 samples,
signed/zero scalars, counts, nonfinite input, vectors, tiny and extreme numbers,
overflow, null environment and panic containment. JVM tests validate transport,
unwritten/invalid responses, linkage, and cancellation. Instrumentation exercises
real JNI arrays including null/oversized buffers. The framework release probe
checks scalar/vector processing through the minified target and retained ABI.
