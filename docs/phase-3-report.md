# Phase 3 engineering delivery — 2026-10-23

## Phase 3 — Final Code Review Corrections and Integration Readiness

Reviewed baseline: `b0d4b6302964403092616b15d24c06f147f850fe`, with all three original
Phase 3 commits intact. Local/remote master were
`a9689f73df76516d6be1ee7746cb8b5c94282a3f`; feature matched its remote and the tree
was clean. GitHub run [39927309528](https://github.com/masoudd2159/DeviceAtlas/actions/runs/39927309528)
was independently verified as SUCCESS for the reviewed SHA, with Android/four-ABI,
Linux Rust and both PowerShell jobs successful. It is baseline evidence only.

Correction source commit: `420ea983158a9bd2ae821802b9c554bcb72ff9f4`
(`fix(monitoring): freeze stop time and confine completion cleanup`). Original
commits were not amended, rebased or squashed.

### Elapsed-time correction

The old Stop changed status without measuring time since the monotonic start.
New regression assertions reproduced four stale-duration failures before the fix.
The session now retains its start timestamp and freezes elapsed milliseconds at
the Running→Stopped transition, before cancellation. This covers USER, HIDDEN,
BACKGROUND, DESTROYED and explicit cancellation paths; normal limit/error completion
uses the same safe elapsed calculation. Repeated Stop does not extend time, Reset
returns it to zero, and a fresh Start establishes a new baseline. Generation checks
prevent late samples or completion from changing frozen time/results.

Ordered nonnegative nanosecond operands make subtraction overflow-free. Invalid
or regressed clocks preserve already published duration; forward jumps are capped
at 600,000 ms. A 3.5-second stop freezes 3,500 ms even between samples. The existing
UI presents whole seconds. No second timer, polling, persistence or sampling
schedule change was introduced.

### Completion threading and owner destruction

The official [Job.invokeOnCompletion contract](https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines/-job/invoke-on-completion.html)
does not guarantee callback context and permits concurrent invocation. Directly
reading job ownership or publishing StateFlow there was therefore unsafe for the
main-confined session, even when ordinary Android runs appeared to finish on Main.

The callback now only posts a finite Runnable through an injectable owner dispatcher,
`Dispatchers.Main.immediate` in production. All ownership reads/writes, identity
checks, start-time release and StateFlow publication occur inside that owner task.
It uses [dispatch with EmptyCoroutineContext](https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines/-coroutine-dispatcher/dispatch.html),
not a coroutine launch in an already canceled ViewModel scope. Consequently cleanup
still executes after parent cancellation without another scope, blocking join,
background session or deadlock. The Android Main looper remains the owner.

Stop invalidates generation and freezes state immediately, but retains its Job until
actual completion **and** the owner handoff. A new Start cannot overlap retiring
platform/JNI work. Cleanup checks Job identity, so a stale notification cannot
clear a successor. Completion before first dispatch is covered without relying on
the coroutine body/finally. `canStart` also requires active scope and an open owner.
ViewModel.onCleared calls permanent `close()`, which disables eligibility, stops
with DESTROYED where still Running and cancels acquisition. Cleanup remains able
to release the completed child, but neither canceled scope nor closed session can
restart. No externally registered listener/receiver or native reference exists.

### Focused regressions and fresh verification

Ten new JVM tests extend the existing suite to **79**. Coverage includes exact Stop
time/repeated Stop, HIDDEN/BACKGROUND time and acquisition counts, Reset/new baseline,
invalid/regressed/extreme clocks, failure timing without a successful sample,
worker-thread completion, stale handoff replay and owner destruction. Existing
deadline, cancellation-before-dispatch, no-overlap and late-native tests remain;
the in-flight source test now also checks frozen duration after late completion.
The worker test executes a canceled initial continuation on a real worker and
asserts that it only queues cleanup; StateFlow collectors observe owner-thread
publication only. Controlled queues, virtual time and explicit completion replace
thread sleeps. The real Android control test adds three rapid Start→Stop cycles.

| Fresh check | Result |
| --- | --- |
| :app:testDebugUnitTest | 79 PASS; 0 failures/errors/skips; newly executed after correction |
| :app:lintDebug :app:lintRelease | PASS; 0 errors and the same two existing warnings |
| :app:assembleDebug :app:assembleRelease :app:bundleRelease :app:assembleDebugAndroidTest :app:build | PASS, including release R8; phase3-corrections-final-validation.log |
| :app:assembleDebug --offline | PASS; phase3-corrections-offline.log |
| Full direct instrumentation, API 26 x86_64 | 53 PASS, 40.373 s; phase3-corrections-final-instrumentation-emulator-5554.log |
| Full direct instrumentation, API 33 ARM64 | 53 PASS, 95.532 s; phase3-corrections-final-instrumentation-RF8R50GVKYL.log |
| cargo fmt / Clippy, locked all-targets with warnings denied | PASS |
| Native/Rust/JNI source diff from reviewed SHA | Empty; no protocol/algorithm changes |
| Fresh release packaging/exports | Four ABIs; mean/processHardware/processMonitoring exports present in each |
| APK ZIP / ELF alignment | PASS; 64-bit LOAD alignment 16,384; 32-bit unchanged 4,096 |
| Resource consuming-placeholder/key parity | PASS, 287 keys each; English escaped percent and Persian literal percent preserved |
| Manifest/toolchain/workflow/resource diffs | Empty; no new permissions/dependencies/persistence/telemetry/identifiers |

Gradle used gradlew.bat with --max-workers=2 --console=plain. Device execution used
explicit selected-device replacement installs and AndroidJUnitRunner. Both final
logs contain 53 starts, 53 successful per-test completions and OK (53 tests).
The Gradle connected installer was not used and its previous anomaly remains
documented. Native source was unchanged, so the historical 23 Android Rust tests
per runtime remain historical; no new native test execution is claimed here.
Likewise normal minified-runtime smoke below is historical, while the minified
release build and fresh debug JNI/device suite above are new checks. The current
local JVM result is the Debug suite; no separate release JVM suite is claimed.

The unchanged Linux Rust CI job must validate the exact final pushed SHA before
integration. Final source review covered start/stop/reset/eligibility/completion,
visibility, ViewModel disposal, immutable publication, JNI compatibility and privacy;
no remaining high-confidence material defect was found in these changed paths.
Graphify was AST-updated, retaining its documented partial Kotlin extraction limits.
Generated graphs, logs, binaries and temporary scripts are not committed.

### Integration gate and delivery evidence

Local correction/verification gates pass. Normal feature push, exact-final-feature
CI validation, a non-fast-forward master merge, tree/ancestry checks, merged-master
smoke and normal master push are explicitly authorized for this closure. The final
feature SHA (which includes this documentation commit), run IDs/job outcomes, merge
SHA/parents and remote-master verification are recorded in the integration delivery
report rather than placing a future/self-referential commit SHA in this commit.
The previous green run is never substituted for corrected-HEAD CI.

Existing noncritical limits remain: Windows MSVC host linker unavailable; no genuine
16 KiB-page runtime; limited API/OEM coverage; no full manual TalkBack/foldable audit
or quantitative battery-drain profiling. No Phase 4 implementation is included.

## Initial Phase 3 delivery record (historical, reviewed b0d4b63)

The original implementation report below is preserved. Its local-only Git/CI state
and test counts describe that earlier delivery, not the correction/integration run.

## A. Executive summary

**READY FOR FINAL CODE REVIEW.** Phase 3 adds real, explicitly started foreground
monitoring to Diagnostics: public Android API acquisition, Rust-owned derived
values/statistics, bounded JNI, ephemeral history, charts, English/Persian resources
and lifecycle regression tests. No Phase 4, networking, permissions, persistence,
export or background service was added.

Sampling is immediate and then five seconds after completed work, with a ten-minute
deadline and maximum 120 retained observations. Charts use actual timestamps and
missing values remain unavailable. Start begins a fresh session; Stop retains
results; Reset cancels before clearing. Returning never restarts acquisition.

## B. Initial Git state

Preflight ran status, current branch, branch tracking, fetch, HEAD/origin/master
resolution, recent log and diff checks. The tree was clean. Master, origin/master
and HEAD matched accepted Phase 2 baseline
`a9689f73df76516d6be1ee7746cb8b5c94282a3f`. A new branch
`feature/phase-3-foreground-diagnostics` was created from it. No existing Phase 3
work or accepted commits were overwritten. The supplied accepted master CI run is
[39912398614](https://github.com/masoudd2159/DeviceAtlas/actions/runs/39912398614);
that is baseline evidence, not a Phase 3 CI result. Work remained local.

## C–D. Architecture and monitoring data matrix

MainActivity owns a separate Hilt MonitoringViewModel. Its main-confined
MonitoringSession uses viewModelScope and existing qualified IO/native dispatchers.
The shell combines Diagnostics visibility and Activity RESUMED **outside** retained
Navigation 3 entries. HardwareViewModel and the manually refreshed Phase 2 reader
remain unchanged. No Activity/Context enters the new ViewModel; the source receives
application Context only.

AndroidMonitoringSource → RawObservation → MonitoringSession →
RustMonitoringProcessor → processMonitoring → monitoring.rs → immutable StateFlow
→ Compose controls/readings/charts.

The [source/API/raw-unit/processing/display/availability matrix](architecture/foreground-monitoring.md#acquisition-and-provenance)
documents every metric. Implemented observations: total/available RAM, available
percentage, memory threshold/low-memory flag, battery level/status/temperature/
voltage/signed current, process CPU time/one-core equivalent use, thermal status
and headroom. Each uses the bounded session schedule. Static storage/display/sensor
information remains in Phase 2. Optional live charge/energy counters, storage
history and API-37 freeMem were not added.

API 26–28 mark thermal status unsupported; API 26–29 mark headroom unsupported.
Unknown categories, missing extras, absent batteries and property sentinels stay
independently unavailable. Available RAM can include reclaimable memory; process
CPU can exceed 100% and never represents total device CPU use; signed current
retains charge/discharge direction; battery temperature is not CPU temperature;
headroom is a ratio, not Celsius. No health score is produced.

## E–F. Session and lifecycle proof

Start requires both gates and no retiring job. Duplicate Start does nothing.
Acquisition is sequential, with fixed delay after work, so slow work cannot create
a backlog. Coroutine timeout and monotonic elapsed checks enforce ten minutes.
Stop invalidates a generation token before cancellation. Reset stops then clears
history/statistics. A successor cannot start until canceled work exits. Published
collections are immutable copies; oldest samples are evicted at 120. Derived CPU
points are retained instead of recomputed from a truncated raw window.

Rotation/language recreation stops on pause and may retain completed history in
the surviving ViewModel. A new process/ViewModel starts Idle with no history.
The policy stops even on pause, which is stricter than ON_STOP. Tab/back handlers
also stop synchronously before navigation. There is no automatic resume.

`MonitoringLifecycleTest.navigationAndBackgroundStopActualAcquisitionAndDoNotResume`
uses a counting source and virtual time on Android. It hides the monitoring panel,
advances twenty seconds, restores visibility, starts again, lowers the host
lifecycle to CREATED, advances time, and resumes. Acquisition counts do not grow
while hidden/backgrounded or merely on returning. This proves source cancellation,
not just suspended rendering.

`MonitoringScreenTest.realDiagnosticsControlsNavigationBackgroundAndRecreation`
tests real MainActivity/Hilt/navigation: idle entry, Start/real observation, Stop,
Reset, Settings/tab exit, background and recreation. The restored UI is Stopped
with Start enabled and Stop disabled. JVM tests additionally cover duplicate Start,
deadline/120-point cap, delayed source calls, non-overlap, uninterruptible source
completion after Stop, late native completion after Reset, invalid timing, typed
native failure, cancellation and no resume.

Partial observations continue independently. Three consecutive wholly unavailable
numeric samples stop with collection failure; native failures stop with typed
errors. Voluntary/lifecycle stops are not failures.

## G–H. Rust and JNI

The [native protocol specification](architecture/native-monitoring-processing.md)
defines the new independent v1 header, operations, shapes, status codes and bounds.
Legacy mean and hardware signatures/IDs/shapes are preserved.

Rust checks nonnegative CPU counters/timestamps, checked deltas and positive elapsed
time, converting before multiplication. Missing baseline is distinct from an invalid
interval; rollback produces no negative percentage and the next raw pair can
recover. Memory/battery validation reuses hardware.rs. Headroom accepts finite
nonnegative ratios including values above one. Summary availability masks exclude
missing observations from values and denominators. Min/max/latest/count and the
existing compensated/overflow-resistant mean cover at most 120 retained points.
Means are simple sample averages, not time-weighted. No smoothing or prediction.

JNI validates its header/counts/exact shapes before payload copies, uses fixed stack
buffers, bounds allocations and contains panics. Region-operation exceptions are
checked immediately; no pointer or reference is retained. Only fully initialized
successful results are published. Kotlin rejects failures/nonfinite output and
checks cancellation before/after the bounded native call. The existing R8 native
method rule covers the new export.

## I–J. UI, localization and accessibility

Diagnostics preserves hardware availability and the original [2,4,6] Rust mean.
Monitoring adds stable wrapping Material controls, a status-only polite live
region, elapsed seconds, last valid timestamp, count, independent current readings
and RAM/CPU/battery-temperature charts. Charts show actual monotonic positions,
discrete dots and unsmoothed segments; missing values or gaps over ten seconds
break the line. Unsupported empty charts are omitted. Single/constant values and
extreme finite ranges are handled safely.

Textual min/max/sample-average/latest-valid/count equivalents and chart descriptions
support screen readers. Status transitions do not announce every metric tick.
Existing headings, themes and touch targets are reused. Locale-aware numbers/times
and bidi isolation support mixed Persian/Latin units. There are **287 paired keys**
(55 added), with matching positional placeholders. Current upstream Persian Writing
guidance was retrieved before drafting localization. Device fixtures cover Persian
RTL, 1.8× fonts, light/dark themes and missing/constant/one-point charts.

## K. Executed validation

Generated logs/binaries are ignored local evidence under build/ or app/build/;
none is committed. Instrumentation success required positive counts and every
per-test completion, not merely shell exit zero.

| Check | Actual result / evidence |
| --- | --- |
| cargo fmt --manifest-path native/Cargo.toml --all -- --check | PASS |
| cargo clippy --manifest-path native/Cargo.toml --locked --all-targets -- -D warnings | PASS |
| cargo test --manifest-path native/Cargo.toml --locked, Windows host | BLOCKED: missing MSVC link.exe; not a host PASS |
| cargo ndk --platform 26 -t arm64-v8a -t x86_64 test --locked --no-run, from native/ | PASS; phase3-rust-android-build.log |
| Actual Rust executables on API 33 ARM64 and API 26 x86_64 | **23/23 PASS each**; phase3-rust-aarch64-linux-android.log, phase3-rust-x86_64-linux-android.log |
| :app:testDebugUnitTest | **69 PASS**, 0 failures/errors/skips; app/build/test-results/testDebugUnitTest |
| :app:lintDebug :app:lintRelease | PASS, 0 errors; two existing warnings in unchanged manifest/preferences code |
| :app:assembleDebug :app:assembleRelease :app:bundleRelease :app:assembleDebugAndroidTest :app:build | PASS; phase3-review-validation.log, includes R8 and release JVM tests |
| :app:testDebugUnitTest :app:assembleDebug --offline | PASS after final additional unit regression; phase3-offline.log |
| Direct full instrumentation, API 26 x86_64 | **53 PASS**, 35.836 s; phase3-final-instrumentation-emulator-5554.log |
| Direct full instrumentation, Samsung API 33 ARM64 | **53 PASS**, 95.104 s; phase3-final-instrumentation-RF8R50GVKYL.log |
| Four release native libraries, all three JNI exports | PASS; phase3-native-inspection/results.txt |
| Static ELF LOAD alignment | 64-bit: 16,384; 32-bit: unchanged 4,096 |
| zipalign -c -P 16 4 app-release-unsigned.apk | PASS, exit 0 |
| Debug-signed minified release copy, API 26 and API 33 | PASS: normal cold launch, Start/real processed sample/Stop, original mean 4.0; phase3-release-*.xml, phase3-arm64-release-*.xml |
| Resource XML/key/placeholder parity | PASS, 287 keys per language |
| git diff --check | PASS |

Gradle invocations used gradlew.bat and --max-workers=2 --console=plain. All four
native ABIs build in debug/release. Established toolchain pins were preserved.
Only the already pinned coroutines-test dependency was added to androidTest.

Direct instrumentation used explicit serials RF8R50GVKYL and emulator-5554:

```text
adb -s <serial> install -r app/build/outputs/apk/debug/app-debug.apk
adb -s <serial> install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb -s <serial> shell am instrument -w -r io.github.masoudd2159.deviceatlas.test/androidx.test.runner.AndroidJUnitRunner
```

Rust executables were pushed to an app-specific /data/local/tmp path, executed and
removed. Release outputs retain their existing unsigned status; only an ignored
copy was debug-signed for smoke. Debug APKs were restored afterward.

## L. Unsuccessful attempts and CI distinctions

The first cargo-ndk test invocation from the repository root could not find
Cargo.toml; using native/ corrected it. Windows host tests remain linker-blocked.

The first phone suite ran while Dozing: 53 executed, 34 UI failures reporting
missing Compose hierarchies. Waking/dismissing the nonsecure keyguard allowed the
entire suite to pass, followed by another complete final-source pass. The original
failed log remains phase3-instrumentation-RF8R50GVKYL.log and is not called a pass.

An exploratory debug test-APK invocation against minified release crashed before
tests because its runner expected unminified kotlin.jvm.internal.Intrinsics.
It executed zero tests and is **not** release instrumentation evidence
(phase3-release-arm64-jni.log). Normal minified UI smoke then passed on both runtimes,
including actual monitoring/JNI and mean 4.0. No keep rules were weakened to support
that mismatched debug runner.

No Gradle connected-test PASS is claimed; direct installation avoided the previously
documented installer anomaly. No hosted Phase 3 run occurred because no push was
authorized. The unchanged workflow retains the accepted pins, official SDK package
platforms;android-37.0, Linux Rust checks, Android/four-ABI checks and both exporter jobs.

## M–O. Release, privacy, power and review

Four libraries contain all legacy/new JNI exports. Normal minified startup/sample/
mean succeeded on both runtimes. APK ZIP and 64-bit ELF alignment pass. Static
alignment does not establish genuine 16 KiB-page execution.

Merged permissions retain only the existing app-local
DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION; no sensitive/runtime/network permission
was added. No service/worker, registered battery receiver, wakelock, keep-screen flag,
network client, identifier, telemetry, database or export was introduced. The null
sticky receiver registers nothing. Production code logs no snapshots/time series.
Fixtures remain test-only. One sampler, fixed delay, bounded history/native work,
no inventory polling, no backlog and no animations bound resource use. No measured
battery-drain claim is made; no power profiler or stress workload was run.

Review found and fixed one rapid-tap race: canceling before the coroutine's first
dispatch can bypass its finally block. Job-completion cleanup now handles even
never-entered work; a regression proves immediate Start→Stop/Reset cannot strand
ownership. Summary native failures also stop the session. No remaining
high-confidence material defect was found in the inspected diff.

Graphify queries preceded exploration and AST updates followed changes. Three Kotlin
files have partial extraction warnings; some build metadata yields no symbols.
Graphify locates shell→visibility→session edges but cannot fully trace injected
interface paths; a route through Inject is not proof of data flow. Source, Hilt
compilation and tests establish boundaries. Generated graph/skill/hook files remain
local, unstaged and uncommitted.

## P. Remaining limitations

- Windows native host execution requires MSVC; hosted Phase 3 checks await a
  separately authorized push. Android native execution is separate valid evidence.
- Genuine 16 KiB runtime and API 29/30/37/broader OEM execution: NOT RUN.
- Full manual TalkBack/focus/switch-access audit, foldables, external displays and
  quantitative power profiling: NOT RUN. Automated semantics/RTL/font/theme checks
  do not replace those audits.
- OEM battery/thermal availability varies and remains explicit. Elapsed duration
  updates at sample boundaries rather than through a second timer.
- Debug-runner-against-minified-release is unsupported by the present setup;
  normal minified runtime smoke passed independently.

These are documented coverage/platform limits; core Phase 3 work is implemented.

## Q. Changed files

New contracts are in foundation/monitoring; Hardware.kt/HardwareLabels.kt add two
compatible unavailable reasons. AndroidMonitoringSource provides acquisition;
RustMonitoringProcessor/NativeBridge the gateway; MonitoringModule Hilt bindings;
MonitoringSession/ViewModel/Panel the owner, gate, controls and charts. MainActivity
and DeviceAtlasApp integrate the independent capability. Two string files add
55 paired keys. Native monitoring.rs/bridge.rs/lib.rs implement processing/export.

Two JVM test files cover session/native boundaries; MonitoringIntegrationTest,
MonitoringLifecycleTest and MonitoringScreenTest cover real platform/JNI, counting
lifecycle, charts and real navigation. app/build.gradle.kts adds the existing
coroutines test artifact to androidTest. Architecture overview/hardware/Rust-boundary
documents, two new monitoring documents, validation.md and this report describe
implemented behavior and evidence. No old tests or historical reports were removed.

## R–S. Local commits and final Git state

Logical local native, Android/UI/test and documentation commits are reported with
exact SHAs in the final chat and `git log master..HEAD --oneline`. The report avoids
embedding its own commit SHA. Master remains the baseline above. Final whitespace,
staged-diff and clean-status checks exclude all generated artifacts. No push,
merge, force-push, tag, release, publication or branch deletion occurred.

## T–U. Acceptance and Phase 4 handoff

**READY FOR FINAL CODE REVIEW**, supported by the evidence and limitations above.
Independent review should focus on lifecycle ownership, JNI compatibility and
missing-value semantics. Future work can broaden device/accessibility/power
evidence or design separately authorized diagnostics/benchmarks. Phase 4 was not started.
