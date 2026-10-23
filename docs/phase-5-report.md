# DeviceAtlas Phase 5 engineering report

Date: 2026-10-23. Scope: local implementation and verification, pending independent
review. This is not a hardware-health certification or a release approval.

## A. Executive summary

Implemented all four interactive categories under Diagnostics → Interactive Tests:
manual static display inspection, bounded touch coverage/multi-touch, selected
foreground sensor response, and brief explicitly requested vibration. Kotlin owns
platform/UI/lifecycle work; Rust computes bounded sensor statistics through an
independent versioned JNI method. Results distinguish platform observations from
manual feedback and remain in memory. No overall health score, history or export
was introduced. All P5-01 through P5-13 implementation milestones are covered;
the manual/device limitations below are explicitly unverified.

## B. Initial Git state

The working tree was clean on master. Fetch completed, and both HEAD and
origin/master were `7689f88db48975167aa9c517367dd7159d2731e0`, the supplied accepted
Phase 4 baseline. The accepted commit was verified as an ancestor of origin/master.
Branch/status/log/diff preflight completed before creating
`feature/phase-5-interactive-diagnostics`. No previous Phase 5 work needed recovery.
Master, prior phase history and pinned dependency/toolchain files were preserved.

## C. Architecture

The existing single app module, Hilt, Compose, Navigation 3 and Activity-owned
ViewModels remain. New typed contracts live in `foundation/interactive`;
application-context platform gateways in `data/interactive`; the native adapter in
`data/nativecore`; orchestration/rendering in `presentation/interactive`.
`InteractiveModule` binds the three testable boundaries. No generic plugin or
diagnostics framework was added. Detailed ownership is in
[interactive diagnostics](architecture/interactive-diagnostics.md).

## D. Display diagnostics

Eight static patterns: red, green, blue, white, black, mid-gray, grayscale gradient,
and checkerboard. Explicit Start resets to red; Previous/Next wrap manually.
Stop/Exit and system navigation remain available. The active limit is two minutes.
No animation, brightness override, screen-timeout bypass, hidden bars or immersive
trap exists. Pattern colors are application rendering requests, not calibrated
physical emissions. Manual observations never imply automatic pixel detection.
JVM tests exercise ordering, wrap, explicit intent, no cycling, deadline and feedback;
Compose tests render patterns, switch, and exit.

## E. Touch diagnostics

An actual canvas-bound 8×12 or 12×8 grid holds at most 96 visited cells. Half-open
pixel bounds reject negative, nonfinite and outside coordinates. Geometry changes
reset coverage/contact state with an explanation. Clear Grid resets observations
and maximum contacts. Compose uses stable PointerId values during a gesture;
release/cancellation remove contacts, and reuse in a later gesture is legitimate.
Ten markers are retained/rendered while counts include all reported contacts.
There are no trajectories or pressure metadata. Coverage is an observed fraction
of the app surface, not digitizer health or coverage of Android system UI.
JVM tests cover bounds, identity, caps and resize; instrumentation sends a tap and
two simultaneous pointers and checks final contact counts.

## F. Sensor diagnostics

Supported default sensors are accelerometer (m/s²), gyroscope (rad/s), light (lux)
and proximity (reported cm). The public SensorManager gateway requests 100,000 µs
sampling on the main looper. This is a hint; actual delivery may differ. Only one
selected listener runs after explicit admission. Null default sensor, failed
registration, no reading, valid readings and native/platform unavailability remain
distinct. Event values are immediately copied; SensorEvent objects are never kept.

Limits: 64 valid accepted events or 30 seconds, whichever comes first; at most one
summary/UI publication per 250 ms, with no JNI per callback. The whole accepted
window is retained rather than a rolling history. At 10 Hz it commonly completes
in roughly six seconds. Invalid readings are ignored/countable, not converted to
zero. Latest X/Y/Z or scalar values, units, observation age, sensor range and
min/max/mean/range are shown. Zero lux and unchanged values are not faults;
acceleration includes gravity. Proximity has no invented universal threshold.
Gentle instructions and skip feedback avoid physically demanding gestures.

## G. Rust sensor processing

`native/src/diagnostics.rs` validates dimensions, finite values and count, computes
scalar or vector-magnitude minimum/maximum/mean/latest/count/range, and reuses the
compensated mean. Chained hypot avoids squaring overflow and preserves tiny values.
Unrepresentable magnitude/range is rejected; missing samples never become zero.
Six new pure Rust test cases include scalar/vector extremes, protocol errors,
NaN/Infinity, 64/65 limits and invalid vector lengths. Existing Rust tests remain.

## H. JNI

New `processSensor` v1: four longs `[1,1,dimensions,count]`, exactly count×dimensions
input doubles (maximum 192), and seven output doubles. Fixed stack buffers, null/
length/shape checks, exception handling and existing panic containment guard the
boundary. Output is consumed only on success and validated for finite values,
version/count, ordered bounds, range and nonnegative magnitudes. No platform
references/global state survive a call. Legacy mean/processHardware/
processMonitoring/processBenchmark signatures and semantics are unchanged.
See the [wire contract](architecture/native-sensor-analysis.md).

## I. Haptics

API 26–30 uses Vibrator; API 31+ uses VibratorManager.defaultVibrator. Presence is
checked with hasVibrator. Only explicit pulse presses after explicit Start request
a 50 ms createOneShot effect at DEFAULT_AMPLITUDE; the gateway enforces a 100 ms
hard ceiling. A monotonic ledger enforces a two-second cooldown and five accepted
pulses per rolling minute, including test restarts under the same owner. No pulse
is queued or repeated. Stop/background/disposal cancel the owned effect.

`VIBRATE` is the only added permission and is normal-protection, without a runtime
dialog. API success only records a requested pulse; felt/not-felt/unsure/skipped is
manual feedback. Automated haptics use fakes. No physical pulse was requested in
this validation session.

## J. Cross-session coordination

The existing DiagnosticsCoordinator gains one interactive reservation and stop
callbacks without replacing its synchronization model. Interactive Start stops
and awaits monitoring/benchmark retirement. Benchmark Start stops and awaits
interactive retirement. Monitoring keeps idle-only admission; shell switching
stops the interactive owner and re-enables explicit Start after cleanup.
Reservations may coexist while retiring, but acquisition/native workloads cannot.
JVM tests cover both predecessors, reverse benchmark admission, monitoring rejection
during retirement, canceled admission, rapid cancel, a new owner and late native
completion. Existing Phase 3/4 coordination tests continue to pass.

## K. Lifecycle

Panel visibility and Activity RESUMED jointly gate admission outside retained
Navigation 3 entries. Stop, selection change, navigation, pause, destruction,
deadline and fatal errors retire work. Generation tokens retire callbacks/results.
Listener cleanup occurs immediately on Stop; ownership remains until canceled
native work returns. Lazy jobs canceled before first dispatch release via a
completion Runnable even if the ViewModel scope is canceled. The deadline also
includes delayed first dispatch. No event automatically restarts a test.
An actual Activity recreation/navigation test proves the display surface does not
resume; deterministic fake-source tests prove listener/haptic cleanup.

## L. UI

The third Diagnostics selector and four category chips preserve all five top-level
destinations. Existing Material 3 surfaces, theme selection, dynamic color and
responsive scrolling are reused. Pattern and measurement state is session-local;
only shell category selection is saveable. A compact four-category summary keeps
manual feedback separate from platform outcomes, coverage and sample counts.

## M. Accessibility

Semantic headings, accessible controls, localized canvas descriptions, textual
coverage/contact/value summaries and focus return surround physical test surfaces.
Visited cells have check strokes as well as color. Only status transitions use a
polite live region; sensor events and pointer moves do not announce individually.
English/default-font and Persian/200%-font/light/dark Compose scenarios pass.
Manual TalkBack, switch access, focus traversal with assistive technology, and
physical multi-touch usability remain NOT RUN. Skipping never means hardware fault.

## N. Localization

English and Persian each contain **421 matching keys**, up from 348: 73 new keys.
The existing resource parity test passes, including formatting argument order,
nonempty translations and rejection of Arabic kaf/yeh. Existing locale/theme
preferences and Follow System remain. Numbers reuse the locale-aware formatter;
technical axes/units remain readable. Persian guidance was loaded from the live
upstream guide; no new localization/preference system was created.

## O. Security and privacy review

Reviewed the complete Phase 5 changes for permission, component, context, lifetime,
buffer, logging and persistence issues. Only VIBRATE was added. The merged release
manifest also retains the existing private receiver permission; no networking,
dangerous permission, high-rate sensor permission, service, wake lock, storage,
identifier collection or telemetry was added. The launcher remains the existing
exported Activity; no exported component was introduced. Measurements and touch
data remain in memory. R8 rules and native/LTO build configuration are unchanged.
No high-confidence unresolved safety/correctness defect was found in this self-review;
independent review is still pending.

Review corrections: the previous permission assertion was updated to require the
exact approved permission set and normal protection; deadline accounting was
extended to first-dispatch delay; gateway checks now reject inconsistent ranges;
normal completion also retires callback generations. Regression tests cover these
paths. No lint rule, existing numeric test, or security restriction was disabled.

## P. Kotlin tests

**PASS: 132 JVM tests, zero failures/errors/skips**, including the 105 previous
tests and 27 new tests. New coverage spans display, touch, sensor acquisition,
bounded/throttled state, source errors, manual feedback, haptic limits, transport,
late results, cross-session reservations and monotonic deadlines. Tests use
kotlinx-coroutines-test, virtual time, fake sources and injected dispatchers.

## Q. Rust tests

| Check | Result |
| --- | --- |
| cargo fmt --all -- --check | PASS |
| cargo clippy --locked --all-targets -- -D warnings | PASS |
| Windows cargo test --locked | BLOCKED: MSVC link.exe missing |
| API 26 x86_64 debug Rust execution | PASS: 34 tests, zero failures |
| API 33 ARM64 debug Rust execution | PASS: 34 tests, zero failures |
| API 26 x86_64 optimized Rust execution | PASS: 34 tests, zero failures |
| API 33 ARM64 optimized Rust execution | PASS: 34 tests, zero failures |

Android test binaries were built using cargo ndk --platform 26 with --locked
test --no-run (and --release for optimized), pushed to an app-independent temporary
test path, executed through adb, and removed. Cross-compilation alone was not
counted as runtime success. Host Clippy success is not host test execution.

## R. Android instrumentation

**PASS: OK (65 tests)** on the API 26 x86_64 emulator, zero failures.
**PASS: OK (65 tests)** on the attached API 33 ARM64 physical device, zero failures.
Six new tests join the prior 59: real sensor/JNI integration and malformed buffers,
fake-source lifecycle/two-pointer controls, Persian/large-font/themes, and real
Activity recreation/navigation. Legacy launch/navigation/hardware/monitoring/
benchmark tests remain passing. Real sensor acquisition is permitted to report
typed absence/failure rather than assuming every device has every sensor.

The first run on each runtime had 64 tests and one **FAIL**: the historical
zero-hardware-permission assertion rejected the newly authorized VIBRATE permission.
The assertion was replaced with an exact allowlist plus normal-protection check;
the added recreation test brought the final suite to 65. These initial failures
are not reported as passes. Positive final test counts were obtained through
explicit app/test installs and AndroidJUnitRunner, bypassing the documented
INSTALL_FAILED_ALREADY_EXISTS Gradle installer anomaly.

## S. Build and release verification

**PASS** for the combined command:

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:lintRelease `
  :app:assembleDebug :app:assembleRelease :app:bundleRelease `
  :app:assembleDebugAndroidTest :app:build --max-workers=2 --console=plain
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug --offline --max-workers=2 --console=plain
```

Lint: **PASS**, zero errors and two existing warnings (API-33 localeConfig and a
SharedPreferences KTX suggestion). A transient integer-state hint was corrected.
Hilt, Compose, resources, Kotlin, Navigation 3, R8 and resource shrinking build.
All pinned toolchain versions, API 26 minimum, API 37 target/compile and JVM 17
remain as accepted; Gradle uses its configured JDK 25 criteria.

| Release check | Result |
| --- | --- |
| Debug APK, minified release APK, release AAB | PASS |
| arm64-v8a, armeabi-v7a, x86_64 and x86 packaged in all three | PASS |
| Five JNI exports present in all four release libraries | PASS |
| zipalign -c -P 16 4 release APK | PASS |
| ARM64/x86_64 LOAD alignment | PASS: 0x4000 (16 KiB) |
| Existing ARMv7/x86 LOAD alignment | PASS: preserved 0x1000 (4 KiB); not claimed as 16 KiB |
| Real 16 KiB-page runtime | NOT RUN |
| Minified launch and framework native probe on both runtimes | PASS: tests=1, failures=0 per runtime |
| Hosted CI for this branch | NOT RUN: no push authorized/performed |

A temporary copy of the unsigned release APK was signed with the existing local
debug key. The framework-only BenchmarkReleaseInstrumentation probe checks scalar/
vector sensor processing and all legacy JNI methods without weakening R8 rules.
The optional runner property was used only to build the validation test APK, then
the default AndroidJUnitRunner build and debug APK were restored on both runtimes.
Production signing and unsigned release outputs were not changed. No release,
tag, upload or publication occurred.

## T. Manual test evidence

| Human/device-dependent check | Result |
| --- | --- |
| Human confirmation of visible display quality/defects | NOT RUN / NOT VERIFIED |
| Physically felt vibration | NOT RUN / NOT VERIFIED; no real pulse sent |
| Sensor calibration or absolute accuracy | NOT RUN; outside diagnostic claims |
| Manual TalkBack/switch-access/focus review | NOT RUN |
| Foldable, cutout, external display and unusual OEM checks | NOT RUN |
| Dedicated power/battery instrumentation | NOT RUN |

Automated rendering, pointer events and received API data do not replace these
manual observations. Neither device is certified healthy by this work.

## U. Changed files

Paths below are repository-relative; existing phase reports were preserved.

| File | Purpose |
| --- | --- |
| app/src/main/AndroidManifest.xml | Normal VIBRATE permission only |
| app/src/main/java/io/github/masoudd2159/deviceatlas/MainActivity.kt | Activity-owned interactive ViewModel |
| foundation/interactive/Interactive.kt (under the same Java package) | Typed states, feedback, sensor contracts and bounded touch mapping |
| foundation/benchmark/DiagnosticsCoordinator.kt | Interactive reservations and safe retirement |
| presentation/benchmark/BenchmarkSession.kt | Stop callback for interactive admission |
| presentation/interactive/InteractiveSession.kt | Main-confined bounded session owner |
| presentation/interactive/InteractiveViewModel.kt | Hilt/ViewModel scope ownership |
| presentation/interactive/InteractivePanel.kt | Four test surfaces, feedback, summaries and lifecycle gate |
| presentation/shell/DeviceAtlasApp.kt | Third Diagnostics panel and navigation cleanup |
| data/interactive/AndroidInteractiveSensors.kt | One selected public foreground listener |
| data/interactive/AndroidInteractiveHaptics.kt | API-guarded short vibration gateway |
| data/nativecore/NativeBridge.kt | Independent sensor JNI declaration |
| data/nativecore/RustSensorAnalyzer.kt | Defensive dispatch and result conversion |
| di/InteractiveModule.kt | Hilt bindings |
| app/src/main/res/values/strings.xml | 73 English strings |
| app/src/main/res/values-fa/strings.xml | 73 Persian translations |
| native/src/diagnostics.rs | Pure bounded scalar/vector processing and tests |
| native/src/bridge.rs | Checked processSensor export and null-environment regression |
| native/src/lib.rs | Diagnostics module registration |
| app/src/test/.../data/nativecore/RustSensorAnalyzerTest.kt | Native transport/result/error tests |
| app/src/test/.../presentation/interactive/InteractiveSessionTest.kt | Virtual-clock lifecycle/coordination/safety tests |
| app/src/test/.../presentation/interactive/TouchCoverageTest.kt | Geometry, identity and bounded coverage tests |
| app/src/androidTest/.../InteractiveIntegrationTest.kt | Real JNI, malformed buffers, public sensor access |
| app/src/androidTest/.../InteractiveLifecycleTest.kt | Compose gestures, fake cleanup, Persian large text/themes |
| app/src/androidTest/.../InteractiveNavigationTest.kt | Actual Activity recreation and navigation |
| app/src/androidTest/.../ExampleInstrumentedTest.kt | Exact permission set and VIBRATE protection |
| app/src/androidTest/.../BenchmarkReleaseInstrumentation.java | Minified sensor scalar/vector regression probe |
| README.md | Current Phase 5 capability links |
| docs/architecture/interactive-diagnostics.md | Platform, lifecycle, privacy and safety design |
| docs/architecture/native-sensor-analysis.md | Exact JNI protocol and numerical semantics |
| docs/architecture/rust-first-boundaries.md | Phase 5 ownership extension |
| docs/architecture/safe-benchmarking.md | Cross-phase coordinator extension |
| docs/validation.md | Current evidence alongside historical foundation results |
| docs/phase-5-report.md | This delivery report |

No APK, AAB, native binary, test log, temporary script, device identifier or
generated Graphify output is included in commits.

## V. Local commits

| Commit | Scope |
| --- | --- |
| 1d5b969 | feat(rust): add bounded sensor diagnostics and versioned JNI |
| c75be42 | feat(diagnostics): add foreground display touch sensor and haptic tests |
| 6a27a15 | test(diagnostics): cover lifecycle coordination permissions and release JNI |
| Documentation commit following the above | docs: document Phase 5 architecture and verification |

The documentation commit cannot contain its own content-derived hash. Its exact
SHA and final feature HEAD are provided by the final delivery message and
`git log master..HEAD --oneline`.

## W. Final Git state and analysis output

The final branch is `feature/phase-5-interactive-diagnostics`; source,
tests and this documentation are committed locally. Master remains
`7689f88db48975167aa9c517367dd7159d2731e0`. Final status/diff checks are performed
after the documentation commit and reported in the delivery message. No push,
merge, tag, release or Phase 6 implementation occurred.

Graphify queries preceded source navigation and `graphify update .` refreshed the
local AST graph. The update reports parser limitations in three pre-existing
Kotlin files (PlatformValues, Hardware, Monitoring) and zero-symbol unsupported
build/config inputs; those are analysis-tool warnings, not compiler results.
Generated graph output remains local and unstaged. Compiler/test evidence above
is independent of graph coverage.

## X. Remaining limitations

Windows host Rust execution needs the MSVC linker. Native Android debug/optimized
execution supplies separate runtime evidence. ARMv7/x86 packaging/exports build,
but those 32-bit ABIs were not runtime-tested. Static alignment does not establish
16 KiB-page device compatibility. Sensor support/rate/system restrictions vary by
OEM, and no calibration or vibration perception is automatically proven.
Manual assistive-technology, display quality, foldable/external-display and power
checks remain for review. No remote CI run was triggered.

## Y. Phase 5 acceptance

**READY FOR FINAL CODE REVIEW.** All four categories are implemented, bounded,
localized, integrated with existing ownership, and verified by passing JVM,
Android Rust, Compose/instrumentation and minified release checks. There is no
known blocking correctness/safety defect. This acceptance is readiness for an
independent review, with explicitly unverified manual/device-specific behavior;
it does not claim production release readiness or physical hardware health.

## Z. Phase 6 handoff

No Phase 6 work was implemented. Future stabilization/release preparation should
complete the manual accessibility, visible-pattern and felt-vibration checks;
exercise additional OEM/missing-sensor devices, resizable/foldable windows and a
real 16 KiB-page runtime; run Windows host Rust after linker installation; and
obtain independent review/authorized hosted CI before considering release steps.
The current work remains local for that review.
