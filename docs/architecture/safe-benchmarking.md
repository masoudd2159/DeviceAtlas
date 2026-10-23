# Safe foreground benchmarking

Phase 4 is an optional application microbenchmark capability inside Diagnostics.
The Live Monitoring / Quick Performance Check selector preserves the five
top-level destinations. Selecting a panel never starts work. All results are
temporary, process-local observations, with no score, comparison dataset, export,
history, network request, hardware setting change, or new permission.

## Ownership

`BenchmarkViewModel` is Activity-owned and supplies `viewModelScope` to the
Main-confined `BenchmarkSession`. It is separate from Phase 3 `MonitoringSession`.
Kotlin owns user intent, Android safety readings, monotonic session deadlines,
visibility, dispatch, cancellation, and immutable presentation state. Rust owns
workloads, verification, active elapsed timing, rates, and statistics.

`DiagnosticsCoordinator` is a process-local singleton **lease guard**, with no
worker, coroutine scope, Context, or results. It prevents overlap even when a new
Activity owner appears while an old owner's synchronous native call is retiring.
Sessions register finite Main-confined eligibility observers and remove them on
owner disposal. A monitoring lease holds only its actual owner's stop callback.

Benchmark admission reserves its lease before asking that monitoring owner to
stop. The benchmark awaits release of the monitoring acquisition/processing job
before reading safety or entering Rust. The reservation rejects monitoring
starts and other benchmark starts. Disabled buttons are supplementary; both
session boundaries enforce admission independently of Compose.

A canceled job retains its lease until `invokeOnCompletion` posts cleanup to the
owner dispatcher. Cleanup uses a finite Runnable rather than launching into a
possibly canceled owner scope. Identity checks make a stale completion harmless.
Canceled work cannot publish results or release a successor's ownership.

## Session policy

- Explicit Start is the only admission path. New processes/ViewModels start Idle.
- Overall wall-clock budget: 15,000 ms, including initial dispatch delay,
  monitoring retirement, preflight, warmup, measurements, and summaries.
- One native worker call at a time. No detached Kotlin/Rust workers.
- One discarded warmup chunk per workload (5 ms conservative / 15 ms normal).
- Three measured repetitions per workload. Each repetition aggregates one chunk
  in conservative mode or four chunks normally. Normal plan: 39 chunks including
  warmup; conservative plan: 12. Progress counts completed chunks and valid runs.
- Native request maximum: 20 ms; production requests are 5 or 15 ms. Each CPU
  kernel completes a bounded 256-step block before checking the native deadline.
- Total accepted active measurement duration per workload cannot exceed 2 s.
  Incomplete repetitions are discarded; already completed runs remain partial.
- Stop invalidates the generation, freezes elapsed time, cancels the job, and
  prohibits successor work until retirement. Clear Results waits asynchronously
  for that release, then returns Idle and removes all results.

The shell's gate is outside retained Navigation 3 entries and combines the
selected Diagnostics panel with the Activity RESUMED state. Panel changes, other
tabs, Settings/About, back navigation, pause/background, and owner disposal stop
work. Rotation stops active work; a surviving ViewModel can retain completed
in-memory results. Navigation/panel selection can be saved, measurements cannot.
There is no automatic resume, SavedStateHandle measurement, or disk persistence.

Synchronous JNI is not instantaneously cancellable. Its in-flight bounded call
must return before ownership can be released. OS suspension, allocator stalls,
and scheduling prevent an absolute real-time return guarantee. The UI stops
publication immediately; Rust checks elapsed deadlines between bounded work
units, including every 4 KiB of source-buffer initialization. Memory allocation
and a single copy/full equality check remain bounded synchronous operations.

## Safety readings

`AndroidBenchmarkSafetySource` reuses the public `MonitoringSource` collector,
not the Phase 3 session engine. Acquisitions run on IO; native work runs on the
qualified Default dispatcher. No framework API enters Rust.

| Reading | Admission/continuation policy |
| --- | --- |
| Thermal status (API 29+) | SEVERE or higher stops before further chunks |
| Thermal headroom (API 30+) | Valid value >= 1 stops; values above 1 remain meaningful |
| Battery percentage | Valid value below 20 refuses/stops; invalid values are rejected |
| Android low-memory flag | True stops |
| Available memory | Invalid/nonpositive fails; below 32 MiB stops; below 128 MiB selects smaller work |
| Unsupported/missing readings | Remain explicitly unavailable, select conservative work, never imply safety |

`ThermalHeadroomReader` has one serialized singleton cache shared by monitoring
and benchmark reads. Both successful and unavailable results are cached for at
least ten seconds; no independent collectors bypass this limit. A regressed
clock cannot allow an early poll. NaN/nonfinite readings remain unavailable.
The UI identifies cached headroom and displays the last reading, not a safety
guarantee. Battery temperature remains a battery measurement in Phase 3, never a
CPU/GPU temperature or a substitute for thermal status.

Safety is checked before warmup, between measured chunks, and before completion.
Missing information or reduced available memory at a later check also reduces
subsequent chunk time/buffer size. The original chunk plan remains bounded; no
extra repetitions are added. API 26–28 therefore use 5 ms chunks, one per run,
and two 64 KiB buffers. Normal buffers are two 256 KiB buffers. Production total
temporary buffers are at most 512 KiB; the native protocol rejects totals over
8 MiB. Buffers are allocated/freed per JNI chunk and reused within that chunk.

## Results and accessibility

Separate cards report integer iterations/s, floating-point iterations/s, and
in-process sequential copy MiB/s. They show actual completed work, summed native
active duration, valid run count, median/minimum/maximum, range/median variation,
and complete/partial status. Warmup is excluded. Results include workload v1,
debug/release context, API level, and process architecture. Debug values are
explicitly development measurements and are not comparable to release values.

Active duration is elapsed time around native measured blocks, not thread CPU
time. It includes scheduling/preemption and timer/dispatch overhead within those
blocks. Session wall time also includes setup, safety, JNI, verification, and
summary overhead. Neither scope is labeled as the other. Three medians from
unlike units are never combined. Higher throughput means more of that specific
workload per second. Cache effects, scheduling, system load, power mode, compiler
optimization, and thermal conditions limit repeatability and comparability.

English/Persian paired resources and positional placeholders use the existing
locale system. Numeric formatting uses the current locale and rate strings use
bidi isolation. Existing Material 3 cards, headings, responsive width, wrapping
buttons, and text rows support RTL, dark/light themes, and large fonts. A polite
live region announces phase/workload/terminal changes, not every chunk. Stable
controls and focus restoration to Clear Results keep terminal actions reachable.
Progress is discrete and introduces no custom motion.

## Verification and extension

See [Phase 4 evidence](../phase-4-report.md) and the
[native protocol](native-benchmark-processing.md). Virtual-time tests cover
deadlines, retirement, late publication, both exclusion directions, cross-owner
admission, and unknown/unsafe readings. Instrumentation uses fake engines for
lifecycle/layout tests and tiny real native calls for JNI smoke coverage.

An extension must define an algorithm version, exact units, verification method,
native bounds, safety policy, finite statistical behavior, protocol validation,
and regression tests before adding a UI control. GPU, disk writes, all-core
stress, leaderboards, history, and uploads are outside Phase 4.

References: [Android benchmarking](https://developer.android.com/topic/performance/benchmarking/benchmarking-overview),
[Microbenchmark](https://developer.android.com/topic/performance/benchmarking/microbenchmark-overview),
[Macrobenchmark](https://developer.android.com/topic/performance/benchmarking/macrobenchmark-overview),
[PowerManager](https://developer.android.com/reference/android/os/PowerManager),
[thermal APIs](https://developer.android.com/games/optimize/adpf/thermal),
[lifecycle](https://developer.android.com/topic/libraries/architecture/lifecycle),
[Job completion](https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines/-job/invoke-on-completion.html).
Custom DeviceAtlas workloads are not certified AndroidX benchmark results.
