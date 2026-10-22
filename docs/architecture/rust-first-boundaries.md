# Rust-first boundaries

| Responsibility | Owner |
| --- | --- |
| Algorithms, normalization, validation rules, derived metrics | Pure Rust core |
| Future hardware acquisition and permissions | Android/Kotlin |
| Lifecycle, UI state, navigation, formatting, localization | Kotlin/Compose |
| User preferences | Android; never translated Rust strings |
| Loading/dispatching and JVM result conversion | Kotlin JNI gateway |
| Checked memory access, panic containment and computation | Rust JNI bridge/core |

Phase 1 adds no hardware collection. The preserved mean uses known test input
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
fallibly allocated Rust vector; pointers, pinned arrays and JVM references are
never retained. JNI exceptions are checked/cleared and mapped at the boundary.
Rust unwinding is contained before returning to the JVM in debug and release.

Cancellation is rethrown by the gateway/ViewModel. A small synchronous JNI call
cannot be interrupted midway; Kotlin cancellation is observed when the dispatcher
returns, and cancelled scopes do not publish a late result. Future long-running
native algorithms must introduce an explicit cooperative cancellation contract.

No Rust split or artificial computational feature is justified for this phase.
All four ABI targets, release keep rules and error regression tests remain.
