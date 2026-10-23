# DeviceAtlas Phase 4 engineering report

Date: 2026-10-23 (Asia/Tehran). Acceptance: **READY FOR FINAL CODE REVIEW**, with
the runtime/device and manual-validation limits below. No remote delivery is
authorized or performed.

## A. Executive summary

Implemented an explicitly started Quick Performance Check within Diagnostics:
three real Rust workloads, versioned bounded JNI, a separate foreground session,
thermal/battery/memory checks, monitoring exclusion, partial-result semantics,
English/Persian UI, and regression coverage. The existing five destinations,
Phase 1–3 features, pinned toolchain, four ABIs, and release optimization remain.
This is a custom application microbenchmark, not a standardized hardware score.

## B. Initial Git state

Started on clean `master` at `a5eb5e090681c9040352d1b97d573f71eece1da8`.
Fetched origin and verified `origin/master` matched that accepted Phase 3 merge;
the ancestor check passed. Created `feature/phase-4-safe-benchmarks` from fetched
master. No pre-existing Phase 4 edits were overwritten. The unpublished branch
has no upstream. Master remains at the accepted SHA.

## C. Architecture

One existing Android module and Rust crate remain. `BenchmarkViewModel` owns
`BenchmarkSession` through its Activity `viewModelScope`. Kotlin owns Android
readings, lifecycle, session deadline, cancellation, transport, and presentation.
Rust owns algorithms, actual completed work, active elapsed timing, integrity,
throughput, and summaries. `DiagnosticsCoordinator` is a process-local lease
guard with no scope, worker, platform Context, or result storage. Singleton lease
admission also protects against a new Activity appearing before old JNI retires.

See [session/safety design](architecture/safe-benchmarking.md) and
[exact native protocol](architecture/native-benchmark-processing.md).

## D. Workload definitions

All algorithms are version 1, single-worker, deterministic fixed-input workloads.

| Workload | Exact work and verification | Reported unit |
| --- | --- | --- |
| Integer | 256 wrapping-u64 add/rotate/multiply/XOR steps per block; seed `0x123456789abcdef0`; expected block digest `0x64f96c555cec2f08` | Algorithm iterations/s |
| Floating point | 256 bounded dependent f64 x/y updates from 0.5/0.25; expected final-state bits `0x3fee02f9da8c4cb4`; finite guard | Algorithm iterations/s, never FLOPS |
| Buffer copy | Source byte i = i modulo 251; sequential copy between two reusable Rust buffers; full equality after every copy and compact sampled checksum | Payload bytes once, converted to MiB/s |

CPU chunk cap: 1,999,872 iterations; copy cap: 64 copies. Native budget maximum:
20 ms, with production requests 15 ms normally or 5 ms conservatively. Native
buffer cap: 4 MiB each / 8 MiB combined. Production uses 256 KiB each normally or
64 KiB each conservatively. Setup and full-copy verification are outside active
copy timing; buffers are freed on return. No disk or user files are involved.

## E. Native Rust

`benchmark.rs` uses `Instant`, black-boxed real work, checked work/duration
accumulation, fallible bounded buffer reservation, known-answer kernel checks,
finite output guards, and bounded cooperative loops. Buffer initialization
checks deadlines every 4 KiB. No Rust thread, JVM-reference retention, global
native session, raw buffer-pointer lifetime, or external framework was added.
All 28 existing/new Rust tests pass on API 26 x86_64 in both debug and optimized
release builds. Windows host execution remains blocked by missing `link.exe`.

## F. JNI

`processBenchmark` adds an independent v1 gateway: exactly six request longs,
zero doubles for a chunk or two doubles per completed repetition (1..3), and
exactly ten output doubles. Header fields are version, operation, workload,
iteration/repetition cap, microsecond budget, and bytes per buffer. Bounds are
validated before copying variable payloads. Fixed stack transport arrays are
small. Result identity, counts, duration, rates, verification, and checksum halves
are validated before typed Kotlin results are created.

Statuses are 0 success, 3 shape/null, 4 JNI/allocation failure, 5 contained panic,
6 numeric/input/integrity/no-completed-work failure, 7 operation/workload, and
8 version. Rejected requests leave output unchanged; any nonzero status causes
Kotlin to discard the whole output. JNI exceptions are checked after operations.
Shared panic containment prevents ordinary unwinding across JNI. The original
mean, hardware, and monitoring protocols and exports remain compatible.

## G. Benchmark session

Typed Idle/Active/Completed/Stopped/Failed state carries phase, workload, actual
completed chunks/runs, monotonic start and elapsed time, safety observations,
conservative mode, results, admission eligibility, and retirement status.
Start clears previous session data and reserves admission; duplicate starts are
rejected. Stop invalidates generation and freezes elapsed time immediately.
Clear cancels and waits asynchronously for owned work before returning Idle.

The 15-second wall deadline includes queued initial dispatch, monitoring
retirement, preflight, warmup, measurement, and summaries. One warmup chunk per
workload is discarded. Three measured repetitions use four chunks normally or
one conservatively. Total accepted active measurement time per workload is at
most two seconds. Partial repetitions are discarded without extrapolation;
completed repetitions are preserved and clearly marked partial on a stopped suite.

## H. Safety

SEVERE-or-higher thermal status or valid headroom >= 1 stops further work.
Battery below 20 percent refuses/stops. Low memory or available memory below
32 MiB stops; invalid/nonpositive numeric memory fails. Below 128 MiB selects
smaller work. Missing battery, headroom, thermal, or memory information remains
unavailable and selects conservative bounds. Unsupported sensors never imply a
cool or safe device. Later loss of information also reduces subsequent chunks.

One synchronized ten-second headroom cache is shared by both capability readers,
including unavailable values. NaN is unavailable; values above 1 are not clamped.
Safety is checked before warmup, between chunks, and before completion. Cached
headroom and latest thermal status cannot guarantee immediate detection of every
physical thermal change. No battery-temperature-to-CPU-temperature inference exists.

## I. Lifecycle

The shell combines current Diagnostics panel visibility and Activity RESUMED
outside retained navigation entries. Switching panels, tabs, back navigation,
Settings/About, pause/background, or owner destruction stops work. Generation and
coroutine checks discard late output. Jobs retain ownership until actual native
return and owner-dispatched completion cleanup. Cleanup also handles cancellation
before first dispatch and cancellation of the owning scope. Rotation never
restarts a benchmark; completed in-memory results may survive in its ViewModel.
New ViewModels/processes start Idle. Measurements never enter SavedStateHandle.

Virtual-time barriers demonstrate that acquisition/chunk counters stop increasing
after visibility/background loss. Instrumentation verifies actual visibility
gates, screen selection, recreation without auto-start, and existing monitoring
recreation/background behavior. Full process-kill recovery was not separately
driven on a physical device; construction tests prove the new owner begins Idle.

## J. Monitoring exclusion

Reservation closes monitoring admission before invoking the active monitoring
owner's stop callback. Benchmark work awaits monitoring's real retirement.
Monitoring cannot start while a benchmark lease is active or retiring. Fake
source/chunk counters and controlled noncancellable barriers verify both
directions, rapid taps, canceled work, late output, and successor ownership.
The coordinator guard is enforced below UI buttons and has no background worker.
Phase 3 remains a read-only observer with its existing state machine.

## K. Results and statistics

Rust computes rates from actual work and positive active nanoseconds, then
minimum, maximum, median, actual valid count, and `(max-min)/median*100` variation.
One-run variation is zero; two-run median averages both values. Invalid input
is rejected, never fabricated or silently replaced. Units are separate and no
overall score exists. UI distinguishes session wall time from summed native
active elapsed time, displays exact work and runs, and labels debug results.
Active elapsed timing can include OS descheduling and timer/branch overhead; it
is not thread CPU time. Caches, scheduling, power modes, compiler optimization,
and current thermal conditions limit repeatability. Emulator rates are functional
evidence only and are not representative physical-device benchmark results.

## L. UI and accessibility

Diagnostics now has Live Monitoring and Quick Performance Check selection,
preserving all five top-level destinations. Quick Check has explicit consent,
readiness readings and unavailable reasons, Start/Stop/Clear, discrete actual
progress, elapsed time, workload context, separate result cards, and limitations.
Existing Material 3 cards, responsive width, wrapping controls, localized numbers,
bidi rate isolation, semantic headings, polite phase/workload/terminal live
regions, and stable controls/focus restoration are used. No custom animation or
per-chunk screen-reader announcement was introduced. Persian/RTL at 200% fonts
and both themes are covered by instrumentation. Manual TalkBack/foldable review
and physical contrast/accessibility validation remain NOT RUN.

## M. Localization

Preserved all 287 prior paired string keys; added 61, for **348 English/Persian
pairs**. XML parsing confirmed identical key sets and positional placeholder
parity. Existing AppCompat/system language and theme preferences are preserved.
Persian uses Persian ی/ک and appropriate نیم‌فاصله, with technical units retained.
Guidance was loaded from the current Persian-writing upstream universal guide.

## N. Verification

Commands below use the repository's pinned JDK 25 and SDK/NDK environment. Logs,
APKs, binaries, and temporary signing output are ignored local evidence and are
not committed. No Phase 4 remote CI run was triggered because push is forbidden.

| Check | Result |
| --- | --- |
| `cargo fmt --manifest-path native/Cargo.toml --all -- --check` | PASS |
| `cargo clippy --manifest-path native/Cargo.toml --locked --all-targets -- -D warnings` | PASS |
| `cargo test --manifest-path native/Cargo.toml --locked` on Windows | BLOCKED: MSVC `link.exe` missing; not counted as pass |
| `cargo ndk --platform 26 -t x86_64 test --locked --no-run` from `native/`, then adb execution | PASS: 28 debug tests, zero failures |
| Same cross-compilation with `--release`, then adb execution | PASS: 28 optimized tests, zero failures |
| `:app:testDebugUnitTest` | PASS: 105 JVM tests, zero failures/errors/skips |
| `:app:lintDebug :app:lintRelease` | PASS: zero errors; two existing warnings |
| `:app:assembleDebug :app:assembleRelease :app:bundleRelease` | PASS; R8/resource shrinking preserved |
| `:app:assembleDebugAndroidTest :app:build` | PASS |
| `:app:assembleDebug --offline` | PASS |
| Explicit API 26 x86_64 install / AndroidJUnitRunner | PASS: 59 tests, zero failures |
| Framework-only probe against temporary debug-signed minified release | PASS: one probe; three tiny real workloads and legacy JNI |
| Four ABI libraries in debug APK, release APK, and AAB | PASS |
| `llvm-nm -D --defined-only` for all four release libraries | PASS: mean/hardware/monitoring/benchmark exports |
| `zipalign -c -P 16 -v 4 app-release-unsigned.apk` | PASS |
| Release ARM64/x86_64 ELF LOAD alignment | PASS: 0x4000 |
| Real 16 KiB-page runtime | NOT RUN |
| Physical device / API 33 ARM64 performance smoke | NOT RUN; no physical device attached |
| Phase 4 hosted CI | NOT RUN; no push performed |
| Manifest/privacy diff and `git diff --check` | PASS |

Combined Android validation:

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:lintRelease `
  :app:assembleDebug :app:assembleRelease :app:bundleRelease `
  :app:assembleDebugAndroidTest :app:build --max-workers=2 --console=plain
.\gradlew.bat :app:assembleDebug --offline --max-workers=2 --console=plain
```

The actual configured default runner is
`io.github.masoudd2159.deviceatlas.test/androidx.test.runner.AndroidJUnitRunner`.
Both APKs were installed using explicit `adb -s emulator-5554 install -r` and
executed with `adb -s emulator-5554 shell am instrument -w -r <runner>`.
Acceptance requires the positive `OK (59 tests)` count, not adb/Gradle exit alone.
The known connected-test installer anomaly was bypassed; it was not reinvestigated.

For minified release, a copy of the unsigned APK was signed with the existing
local debug key; production signing configuration and unsigned output were not
modified. Launch returned `Status: ok`. A debug AndroidX runner initially failed
because R8 legitimately renamed/removed its expected Kotlin runtime class names.
The release-only **framework** probe avoids those dependencies and discovers the
kept bridge receiver by type, so no production keep rule was widened. Build it:

```powershell
.\gradlew.bat :app:assembleDebugAndroidTest `
  '-PdeviceAtlasTestRunner=io.github.masoudd2159.deviceatlas.BenchmarkReleaseInstrumentation' `
  --max-workers=2 --console=plain
```

Install that test APK and run
`io.github.masoudd2159.deviceatlas.test/io.github.masoudd2159.deviceatlas.BenchmarkReleaseInstrumentation`.
Require `tests=1`, `failures=0`, its success stream, and final instrumentation
result. Rebuild without the property to restore AndroidJUnitRunner. Tiny requests
use 256 arithmetic iterations or one 4 KiB copy, never a full automatic benchmark.
The debug app and default test APK are restored after release validation.

One final tiny release probe recorded the following actual completed work on the
API 26 x86_64 emulator, using algorithm v1. These very short timings are functional
evidence, not repeatable or representative performance estimates. This direct
JNI probe is not a full production session and makes no thermal/battery claim.

| Workload | Completed work | Native active elapsed duration |
| --- | --- | --- |
| Integer | 256 iterations | 13,200 ns |
| Floating point | 256 iterations | 83,800 ns |
| Sequential copy | 4,096 bytes | 12,200 ns |

Two initial UI regressions from placing a large benchmark card before monitoring
were reproduced by instrumentation, corrected with panel selection and explicit
scrolling in the structural screen test, and rerun successfully. Existing lint
warnings are manifest `localeConfig` below API 33 and SharedPreferences `UseKtx`;
no lint suppression was added. Existing Gradle/plugin/JDK warnings remain.

## O. Performance safety

Bounds are enforced in native header validation and Kotlin session admission,
not inferred from measured emulator speed. Production buffers total at most
512 KiB; protocol maximum totals 8 MiB. Work counters and native nanoseconds are
real measured values. Cooperative checks and finite caps prevent unbounded loops.
JNI return latency under OS suspension and allocation stalls cannot be strictly
guaranteed. Quantitative battery-drain/power profiling and deliberate overheating
tests were not performed. Allocation-failure fault injection was not performed;
the native allocation path uses fallible reservation and typed failure status.

## P. Privacy and security

Production manifest and permissions are unchanged. No network, telemetry,
identifiers, other-app observations, user files, benchmark persistence/export,
services, WakeLocks, hidden APIs, root, performance mode manipulation, GPU work,
storage stress, or all-core saturation was introduced. Transport uses fixed
small arrays; memory buffers are Rust-owned, bounded, and released promptly.
The release probe and runner override exist only for validation, not app flows.

## Q. Changed files

- `native/src/benchmark.rs`: kernels, verification, timing, bounds, summaries/tests.
- `native/src/bridge.rs`, `lib.rs`, and Kotlin `NativeBridge.kt`: independent JNI ABI.
- `foundation/benchmark/Benchmark.kt`, `DiagnosticsCoordinator.kt`: typed contracts and leases.
- `data/benchmark/AndroidBenchmarkSafetySource.kt`: public platform preflight mapping.
- `data/monitoring/ThermalHeadroomReader.kt`, `AndroidMonitoringSource.kt`: shared throttle/cache.
- `data/nativecore/RustBenchmarkProcessor.kt`, `di/BenchmarkModule.kt`: validated off-main gateway and bindings.
- `presentation/benchmark/BenchmarkSession.kt`, `BenchmarkViewModel.kt`: session ownership and orchestration.
- `presentation/monitoring/MonitoringSession.kt`, `MonitoringViewModel.kt`: minimal lease integration.
- `MainActivity.kt`, `presentation/shell/DeviceAtlasApp.kt`, `presentation/benchmark/BenchmarkPanel.kt`: Activity wiring, panel selection/lifecycle, UI.
- Both `strings.xml` files: 61 paired localized strings.
- JVM tests: `BenchmarkSessionTest`, `RustBenchmarkProcessorTest`, `HeadroomCacheTest`, `AndroidBenchmarkSafetySourceTest`.
- Instrumentation: `BenchmarkIntegrationTest`, `BenchmarkLifecycleTest`, `BenchmarkScreenTest`, `BenchmarkReleaseGatewayTest`, framework `BenchmarkReleaseInstrumentation`; one scroll fix in `PhaseOneAppTest`.
- `app/build.gradle.kts`: optional validation-runner property with unchanged default runner/toolchain.
- Architecture overview, two focused architecture documents, this report, validation index: implementation/methodology/evidence.

## R. Local commits

Implementation is split into native/JNI, Android orchestration/safety, UI and
localization, regression/release validation, and documentation commits:

| Commit | Scope |
| --- | --- |
| `8315445` | `feat(rust): add bounded benchmark workloads and JNI protocol` |
| `53af7a5` | `feat(benchmark): enforce foreground safety and monitoring exclusion` |
| `8361add` | `feat(ui): add localized quick performance diagnostics` |
| `33faa46` | `test(benchmark): cover safety retirement JNI and minified release` |

The subsequent documentation commit and final HEAD are recorded in the delivery
message; `git log master..HEAD --oneline` is the authoritative inventory. Generated
Graphify output, skills/hooks, binaries, logs, temporary files, and machine paths
are excluded from commits.

## S. Final Git state

Final branch: `feature/phase-4-safe-benchmarks`. Master baseline remains
`a5eb5e090681c9040352d1b97d573f71eece1da8`. Working changes are committed locally;
final status and local commit inventory are checked after documentation commit.
No push, merge, rebase, tag, release, remote branch mutation, or Phase 5 work.

## T. Remaining limitations

Windows host Rust tests need the MSVC linker. Android execution covered API 26
x86_64; API 33 ARM64 and broader OEM/API behavior are not physically verified.
All ABIs build, but build success is not four-ABI runtime coverage. Static
alignment is not a real 16 KiB-page execution test. Manual TalkBack, foldable,
quantitative power, battery drain, and sustained real-device repeatability tests
remain NOT RUN. No performance ranking or representativeness is inferred from
emulator results. The known Gradle installer anomaly remains an external test
procedure limitation; explicit installs/runners provide positive-count evidence.

`graphify update .` refreshed the local AST graph without an LLM/API call. Its
parser reported partial extraction in three unchanged baseline Kotlin files
(`PlatformValues.kt`, `Hardware.kt`, `Monitoring.kt`) and no symbols for three
build/configuration files. These are graph coverage limitations, not compiler
failures. Graph output remains local and excluded from commits; source and
executed tests, rather than graph completeness, establish implementation evidence.

## U. Phase 4 acceptance

**READY FOR FINAL CODE REVIEW.** The implementation and available local checks
pass. Missing hardware/manual/power coverage remains explicitly unverified and
does not become a performance or safety certification.

## V. Phase 5 handoff

Possible separately authorized work: broader physical-device validation,
reproducibility characterization, accessibility/manual-device review, and a
separately justified design for any additional workload. No Phase 5 feature,
score/history/export infrastructure, GPU/disk stress, or remote delivery is part
of this execution.
