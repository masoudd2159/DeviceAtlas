# Rust-first boundaries

| Responsibility | Owner |
| --- | --- |
| Algorithms, normalization, validation rules, derived metrics | Pure Rust core |
| Public hardware acquisition and permissions | Android/Kotlin |
| Lifecycle, UI state, navigation, formatting, localization | Kotlin/Compose |
| User preferences | Android; never translated Rust strings |
| Loading/dispatching and JVM result conversion | Kotlin JNI gateway |
| Checked memory access, panic containment and computation | Rust JNI bridge/core |

Phase 2 adds public Android hardware collection and pure Rust numerical
processing. [Hardware sources/models](hardware-collection.md) describe acquisition,
availability and refresh ownership; the [native hardware contract](native-hardware-processing.md)
specifies the bounded versioned JNI method. The preserved mean uses known test input
`[2, 4, 6]` and labels its result as an integration check, never a measurement.
Kotlin's finite/nonempty checks defend the interop boundary; authoritative
validation and the mean algorithm remain independently tested in Rust.

`NativeBridge.mean(DoubleArray?, DoubleArray?): Int` remains unchanged. The output
buffer must contain exactly one slot. Status 0 writes a finite result; 1 means
empty input; 2 nonfinite input; 3 invalid/null buffer; 4 JNI/allocation failure;
5 contained panic. Kotlin exposes typed `MeanResult`, mapping invalid/unknown
statuses and nonfinite output to native failure and loading/symbol/security
errors to library unavailable. Output is read only on success.

JNI runs on the injected Default dispatcher. Region operations copy into a
fallibly allocated Rust vector (bounded to 1,048,576 elements); hardware operations
use fixed stack arrays. Pointers, pinned arrays and JVM references are
never retained. JNI exceptions are checked/cleared and mapped at the boundary.
Rust unwinding is contained before returning to the JVM in debug and release.

Cancellation is rethrown by the gateway/ViewModel. A small synchronous JNI call
cannot be interrupted midway; Kotlin cancellation is observed when the dispatcher
returns, and cancelled scopes do not publish a late result. Future long-running
native algorithms must introduce an explicit cooperative cancellation contract.

The existing crate now contains hardware.rs for actual capacity, battery, display
and numeric validation rules, without artificial forwarding modules or dependencies.
All four ABI targets, release keep rules and original error regressions remain.

Phase 3 extends this boundary with `monitoring.rs` and a separate bounded
`processMonitoring` protocol. Rust owns process CPU deltas and core-equivalent
percent, numeric validation, unit conversion, and missing-aware min/max/mean/latest/
count over at most 120 retained samples. Kotlin owns acquisition, monotonic
scheduling, explicit foreground/visibility gates, status categories and rendering.
See [monitoring protocol](native-monitoring-processing.md) for wire shapes and
[foreground ownership](foreground-monitoring.md) for cancellation and lifetime.
