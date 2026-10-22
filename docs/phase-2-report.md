# DeviceAtlas Phase 2 engineering report — 2026-10-22

## A. Executive summary

Phase 2 implements real, read-only hardware discovery in the existing application.
Kotlin acquires supported public Android observations; Rust validates numerical
relationships and computes capacities, battery conversions and display ratios.
Overview, Device, Sensors and Diagnostics consume actual snapshots. Unsupported
fields are explained instead of invented. The Phase 1 shell, locale ownership,
themes, Navigation 3, Hilt and original mean integration remain.

All P2-01 through P2-12 deliverables are implemented and verified on the available
runtimes, with the explicit environment/manual limitations in O. Final evidence:
48 JVM tests, 36 instrumentation tests on each Android runtime, 19 native tests on
each runtime, debug/minified release/AAB builds and local release execution.

The toolchain and four ABIs are unchanged. No new dependency, module, runtime
permission, identifier collection, networking, persistence of snapshots, sensor
sampling, benchmark or background service was added. Phase 3 was not started.

## B. Git state

Accepted baseline: `f99a48e7ca63134ffd395499c80d68271544cd80`, matching freshly
fetched origin/master and local master at inspection. The initial working tree
was clean. Work is on `feature/phase-2-device-intelligence`. Only local commits
are authorized; no push, pull request, merge, branch deletion or release occurred.
Commit details and final status are recorded in P and the final delivery message.

## C. Architecture

The existing single Android module and Rust crate remain. New foundation/hardware
contracts provide sealed availability/results, explicit sources/freshness, typed
raw numerical requests and processed values. data/hardware owns public collectors
and serialized processing; data/nativecore adds RustHardwareProcessor.
HardwareModule binds the collector, processor and reader with injected IO/Default
dispatchers. presentation/hardware supplies the shared Activity ViewModel and
stateless information/sensor UI. MainActivity provides state to the existing shell.

Acquisition is on IO, bounded native calculations on Default. Raw metadata stays
Kotlin-owned. Mutable framework objects never enter presentation. Production maps
and lists are defensive, unmodifiable copies. A reader mutex serializes collection,
and an active ViewModel job coalesces repeated refresh requests. The ViewModel uses
viewModelScope, propagates cancellation and checks activity before publication.

Successful session metadata is cached in memory. Dynamic information refreshes
on foreground resume of an information page or explicit action; ordinary tab
changes reuse the snapshot. The old capture time stays visible while refreshing
or after overall failure. Per-field failures remain isolated in a new snapshot.
No global coroutine scope, polling loop or Activity Context is retained.

## D. Hardware data matrix

The complete [field/source matrix](architecture/hardware-collection.md) enumerates
every implemented field, actual Android API, API minimum, raw unit, processing
owner/display semantics, missing-value handling and associated test suites.
It also documents source provenance, session versus snapshot freshness, immutable
contracts, privacy and how to extend the collectors. The app minimum stays 26.

## E. Rust implementation

hardware.rs implements capacity validation and checked subtraction; binary GiB
conversion; availability/unavailability percentages; battery level/scale, Celsius,
volts, signed milliamps, mAh and Wh; positive/nonnegative numerical validation;
and integer aspect-ratio reduction. Invalid inputs fail rather than clamp.
Long bytes remain integral until an intentional floating-point conversion.
NaN/Infinity and contradictory relationships are rejected, including extreme inputs.

The [native contract](architecture/native-hardware-processing.md) specifies all
13 operations and statuses. Version 1 transports fixed long[4], double[1] and
double[5] buffers (80 payload bytes), checks exact sizes, initializes every output
slot, checks/clears JNI exceptions and contains panics. No hardware-path native
heap allocation, pinned array, retained reference or Java object graph is used.
Kotlin checks native success slots for finiteness and maps linkage/load errors.

The original mean ABI and all 12 numerical regressions remain. Its allocation now
has an explicit 1,048,576-element limit, using existing invalid-buffer status 3.
Pure native tests additionally cover the hardware boundary and panic containment.
No dependency or Cargo lockfile change was needed. The R8 rule already preserves
all NativeBridge native methods; minified execution verifies both exports.

## F. Device and OS

Manufacturer, brand, model, device/product names, hardware/board family, Android
release/API, build ID and security patch come from public Build/Build.VERSION.
These are product/OS metadata, not unique device identifiers. Blank or unknown
values are not reported; text is bounded and stripped of control/override chars.
No serial, fingerprint, Android ID, telephony, advertising or MAC identifier is read.

## G. CPU, RAM and storage

Supported ordered ABI lists, primary ABI, 32/64-bit lists and process-available
logical processors are real platform observations. SoC manufacturer/model are
guarded at API 31, without fallback guesses or marketing-name lookup. Processor
availability is not labeled as physical core count, frequency or cluster topology.

RAM uses MemoryInfo kernel-visible total, available estimate, low-memory flag and
threshold. Labels explain reclaimable memory and the difference from marketed RAM
and exact app usage. Storage measures the filesystem containing context.dataDir
with StatFs; it does not enumerate files/volumes or claim all physical capacity.
Unavailable capacity includes filesystem reserves. Exact Long bytes remain in the
processed capacity model; Rust owns normalized GiB and percentage calculations.

## H. Battery

A null-receiver sticky battery query obtains presence, level/scale, reported
status/source/health, temperature and voltage. There is no registered receiver.
BatteryManager adds optional charge, signed current and energy. Cycle count is
guarded at API 34. An absent battery suppresses other battery values; absent extras,
unknown enums and unsupported properties retain honest unavailable states.

Review found the Android 8 integer-property narrowing behavior: its long sentinel
can become zero in getIntProperty. All optional properties now use the public
getLongProperty accessor, preserving Long.MIN_VALUE; focused instrumentation
covers unavailable properties on the actual API 26 runtime. Current sign is kept,
and health remains a framework category without invented wear/lifetime percentages.

## I. Display and graphics

One default-display Mode snapshot supplies pixel dimensions and nominal refresh
rate. Rust reduces the pixel ratio and validates finite positive Hz. Logical app
density and HDR capability are labeled separately. Mode pixels are not window
dimensions, Hz is not measured FPS, and density is not used to invent panel inches.
System configuration supplies OpenGL ES support; PackageManager's Vulkan feature
supplies its packed API version. GPU marketing identity is explicitly unsupported;
no unnecessary GL context, renderer query, vendor guess or benchmark is introduced.

## J. Sensors

SensorManager.TYPE_ALL provides the accessible inventory, grouped by framework
type in a LazyColumn. Each sensor has an index scoped to that returned collection,
not a persistent tracking ID. Details expand on demand and preserve saveable state.
Name/vendor/version, reporting mode, wake-up capability, current metadata and
applicable range/resolution/delays are shown with units. Unstandardized types do
not receive invented range units. Delay rows apply only to continuous/on-change
modes. An empty inventory is valid. No sensor listener or restricted stream is read.

## K. UI, localization and accessibility

Overview summarizes actual identity, Android, architecture, memory, storage,
battery, display and sensor count. Device contains eight readable sections;
Sensors is a lazy expandable inventory; Diagnostics exposes availability/invalid
and native-failure counts plus the preserved [2,4,6] mean integration check.
Settings, locale and theme behavior remain intact.

Both languages contain 226 strings with key/placeholder parity. Exhaustive enum
resource mappings cover sections, fields, reasons, battery states, sensor labels
and units. Locale-aware number/date/percent formatting stays in Kotlin; tiny
nonzero measurements use scientific notation, and technical metadata uses LTR.
Existing blue Material 3 components, headings, large touch controls, adaptive
navigation and scroll behavior are reused. Persian 200% font rendering is tested.

Visual inspection caught overlapping capacity details under a Box wrapper despite
the earlier passing suites. The wrapper is now a vertically spaced Column, with a
new bounds-based Compose regression that checks the three rows do not overlap.

## L. Verification

Commands use the pinned toolchains, JDK 25 and max-workers=2. Local evidence logs
under ignored build/ are development artifacts, not committed reports or hosted CI.
Android serial selection was explicit for each invocation. The complete baseline
suite was retained; final successful runs include the added visual bounds test.

| Check / exact command | Result |
|---|---|
| `gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:lintRelease --max-workers=2 --console=plain` (included in combined build) | PASS: 48 JVM tests, 0 failures; both lint variants, 0 errors and 2 existing warnings each |
| `gradlew.bat :app:assembleDebug :app:assembleRelease :app:bundleRelease :app:assembleDebugAndroidTest :app:build --max-workers=2 --console=plain` (combined with tests/lint) | PASS after final layout correction; all four native ABIs, R8/resource shrinking, APK/AAB |
| `gradlew.bat :app:dependencies --configuration releaseRuntimeClasspath --max-workers=2 --console=plain` | PASS; unchanged dependencies, resolved graph inspected |
| `gradlew.bat :app:assembleDebug --offline --max-workers=2 --console=plain` | PASS; repeated checks reuse configuration cache; 43 tasks up-to-date on warm repeat |
| `cargo fmt --manifest-path native/Cargo.toml --all -- --check` | PASS |
| `cargo clippy --manifest-path native/Cargo.toml --locked --all-targets -- -D warnings` | PASS |
| `cargo test --manifest-path native/Cargo.toml --locked` | BLOCKED: attempted, missing Windows MSVC link.exe (exit 101) |
| `cargo ndk --platform 26 -t arm64-v8a test --locked --no-run` and `-t x86_64` from native/ | PASS: standalone test executables cross-built with the pinned NDK |
| Standalone Rust executables deployed to temporary app-specific test path and run with `--test-threads=1` | PASS: API 33 ARM64 and API 26 x86_64, 19 each, 0 failures; temporary executables removed |
| `gradlew.bat :app:connectedDebugAndroidTest --max-workers=2 --console=plain` with ANDROID_SERIAL=emulator-5554 | FAIL at install: INSTALL_FAILED_ALREADY_EXISTS; Gradle returned success despite runner error; not counted as successful execution |
| `adb -s <selected runtime> install -r <debug APK>` and test APK; `adb -s <selected runtime> shell am instrument -w io.github.masoudd2159.deviceatlas.test/androidx.test.runner.AndroidJUnitRunner` | PASS: final complete suites, API 26 x86_64 36/36 (29.231s), API 33 ARM64 36/36 (68.645s) |
| NDK `llvm-readelf --dyn-syms` and `-lW` on each generated release library | PASS: both JNI exports on all four ABIs; 64-bit LOAD alignment 0x4000; only libc/libdl NEEDED entries |
| APK/AAB ZIP entry inspection | PASS: arm64-v8a, armeabi-v7a, x86_64, x86 libdeviceatlas_core.so entries |
| SDK `zipalign -c -P 16 -v 4 app/build/outputs/apk/release/app-release-unsigned.apk` | PASS after final release rebuild: verification successful |
| Temporary debug-signed minified release copy installed/launched on both runtimes | PASS: hardware processing failures=0 and original Rust integration mean=4.0; debug APK restored afterward |
| Merged/packaged manifest with SDK aapt permissions inspection | PASS: only existing internal signature receiver permission; backup/extraction exclusions unchanged |
| `actionlint.exe -shellcheck= -pyflakes= .github/workflows/verify.yml` | PASS; ShellCheck/Pyflakes NOT RUN; only one SDK package argument changed |
| Official Google repository2-3.xml | PASS: exact platforms;android-37.0, build-tools;37.0.0 and ndk;30.0.16248370 entries confirmed |
| `graphify update .`, query/path/explain | PASS with extractor limitations in O; final graph 949 nodes, 1828 edges, 57 communities; AST-only, no API/token cost; excluded from Git |
| Final diff/whitespace, code/native safety, resource parity and staged-file review | PASS; no generated graphs, local hooks/skills, paths, binaries or signing material staged |
| Hosted feature-branch CI | NOT RUN: no push authorized |
| Actual 16 KiB-page runtime | NOT RUN: physical getconf reports 4096; emulator smaps reports 4 kB |
| Full manual accessibility and physical foldable/external-display matrix | NOT RUN; automated coverage and explicit gaps described in O |

The two lint warnings are pre-existing localeConfig behavior below API 33 and a
SharedPreferences KTX style suggestion. They are not suppressed. Initial compile
errors, the empty unit-label translation, an unknown-manufacturer test assumption,
test-fixture semantics/scroll assumptions and capacity overlap were corrected;
the final executed suites above pass. Windows host linking and the connected-task
installer anomaly remain distinct from application test results.

Final local logs: phase2-layout-verification.log, phase2-instrument-api26-closure.log,
phase2-instrument-api33-closure.log, phase2-rust-*-final.log,
phase2-release-smoke-closure.log, phase2-offline-closure.log and
phase2-graph-closure.log in build/. SDK package verification used the live official
catalog; no hosted success is inferred from it.

## M. Security and privacy review

The merged/packaged manifest still has only the existing app-private signature
permission for AndroidX non-exported receivers. No network or sensitive runtime
permission appears. Backup/cloud/device-transfer restrictions remain unchanged;
only the launcher Activity is exported. No manifest changes or runtime dependencies
were introduced. The resolved release runtime dependency graph was inspected.

Collectors use permission-free public services without hidden reflection, root,
shell property scraping, external-file access or identifiers. No full snapshot/raw
exception logging, report export, database, telemetry or network client was added.
Native libraries depend only on Android libc/libdl. Native bounds, output/status,
JNI exception, sign and panic behavior have focused regression coverage. The
application's attack surface remains a local read-only information UI.

Confirmed review issues (battery sentinel handling, mixed display-mode reads and
capacity-row overlap) were fixed. Tests and visual verification, rather than
fabricated platform fallbacks, establish the resulting behavior. There is no known
unresolved critical application defect; remaining test limitations are in O.

## N. Changed files

34 changed/new files, all scoped to Phase 2:

```text
.github/workflows/verify.yml
README.md
app/src/androidTest/java/io/github/masoudd2159/deviceatlas/HardwareIntegrationTest.kt
app/src/androidTest/java/io/github/masoudd2159/deviceatlas/HardwareRenderingTest.kt
app/src/androidTest/java/io/github/masoudd2159/deviceatlas/HardwareScreenTest.kt
app/src/main/java/io/github/masoudd2159/deviceatlas/MainActivity.kt
app/src/main/java/io/github/masoudd2159/deviceatlas/data/hardware/AndroidHardwareCollector.kt
app/src/main/java/io/github/masoudd2159/deviceatlas/data/hardware/PlatformValues.kt
app/src/main/java/io/github/masoudd2159/deviceatlas/data/hardware/ProcessedHardwareReader.kt
app/src/main/java/io/github/masoudd2159/deviceatlas/data/nativecore/NativeBridge.kt
app/src/main/java/io/github/masoudd2159/deviceatlas/data/nativecore/RustHardwareProcessor.kt
app/src/main/java/io/github/masoudd2159/deviceatlas/di/HardwareModule.kt
app/src/main/java/io/github/masoudd2159/deviceatlas/foundation/hardware/Hardware.kt
app/src/main/java/io/github/masoudd2159/deviceatlas/presentation/hardware/HardwareLabels.kt
app/src/main/java/io/github/masoudd2159/deviceatlas/presentation/hardware/HardwareScreens.kt
app/src/main/java/io/github/masoudd2159/deviceatlas/presentation/hardware/HardwareViewModel.kt
app/src/main/java/io/github/masoudd2159/deviceatlas/presentation/shell/AtlasScreens.kt
app/src/main/java/io/github/masoudd2159/deviceatlas/presentation/shell/DeviceAtlasApp.kt
app/src/main/res/values-fa/strings.xml
app/src/main/res/values/strings.xml
app/src/test/java/io/github/masoudd2159/deviceatlas/data/hardware/HardwareDataTest.kt
app/src/test/java/io/github/masoudd2159/deviceatlas/data/nativecore/RustHardwareProcessorTest.kt
app/src/test/java/io/github/masoudd2159/deviceatlas/presentation/hardware/HardwareViewModelTest.kt
docs/architecture/hardware-collection.md
docs/architecture/localization.md
docs/architecture/native-hardware-processing.md
docs/architecture/overview.md
docs/architecture/phase-2-plan.md
docs/architecture/rust-first-boundaries.md
docs/phase-2-report.md
docs/validation.md
native/src/bridge.rs
native/src/hardware.rs
native/src/lib.rs
```

## O. Remaining limitations

- Windows host Rust tests cannot link without MSVC link.exe; Android native tests
  provide separate executed evidence, not a claim that host prerequisites were fixed.
- The Gradle connected runner encountered INSTALL_FAILED_ALREADY_EXISTS and logged
  an installation failure while returning success. Direct explicit-target adb
  install -r plus AndroidJUnitRunner execution supplies the actual instrumentation
  results; the Gradle task's exit code is not used as proof of tests passing.
- No new hosted CI run was triggered. Only the canonical SDK-install argument was
  changed and locally validated. A future authorized push must verify downloads.
- Both available runtimes use 4 KiB pages. Static 16 KiB packaging alignment is
  verified separately; real 16 KiB-page runtime execution was not performed.
- API 34+ cycle reporting, a battery-less physical device, external-display and
  physical foldable/tablet posture behavior lack dedicated real-device execution.
  Guards and missing/invalid protocol paths have compilation and regression coverage.
- Full manual TalkBack spoken traversal, hardware-keyboard focus traversal and a
  dedicated real-device maximum-font matrix were not performed. Compose semantics,
  Persian 200% font layouts and adaptive navigation have automated coverage.
- OEM values may be absent/inconsistent. Current display scope is the default
  display, not an inferred currently attached app display. Optional GPU identity,
  supported-mode list, retail capacity and advertised RAM are not collected.
- Graphify 0.9.93's AST updater warns about partial extraction of two valid Kotlin
  files (Hardware.kt and PlatformValues.kt), three declarative files without
  symbols, and unsupported XML. Compiler/lint and direct source review are the
  authoritative checks; graph relationships are not treated as runtime proofs.

## P. Local commits

| SHA | Message | Scope |
|---|---|---|
| 7f28761 | feat(core): add typed hardware metrics and bounded Rust JNI processing | Contracts, pure metrics, fixed JNI, original mean allocation bound, gateway tests |
| 35bc6d8 | feat(android): collect and process public device capabilities | Public collectors, field isolation, battery sentinel handling, DI and data tests |
| 397c0a5 | feat(ui): connect bilingual hardware screens and foreground refresh | Shared ViewModel, four screens, 226 paired strings, lifecycle and instrumented tests |
| 7d2404c | fix(ci): install the verified Android API 37 SDK package | One verified SDK Manager package identifier |

The final `docs: document Phase 2 architecture and verified delivery` commit
contains this report, updated architecture/README/validation links and a trailing
blank-line cleanup in PlatformValues.kt. Its SHA is in the final delivery message
and `git log master..HEAD`; a commit cannot embed its own SHA. Generated graphs,
local skills/hooks, build logs, screenshots, binaries and signing files are absent
from all commits. The feature branch is delivered with a clean working tree.

## Q. Definition of done

All milestones are PASS within the documented test environments; P2-11 retains
the explicit BLOCKED/NOT RUN checks in L/O. An unreported OEM value is an expected
availability outcome, not a failed milestone.

| Milestone | Implemented deliverable |
|---|---|
| P2-01 | Typed availability/source/freshness contracts and public collector architecture |
| P2-02 | Real product and Android OS metadata |
| P2-03 | Real SoC/ABI metadata and accurately labeled logical processors |
| P2-04 | Kernel memory and app-data filesystem with Rust metrics |
| P2-05 | Real battery snapshot, optional electrical fields and sentinel handling |
| P2-06 | Default display, logical density, HDR and public graphics capabilities |
| P2-07 | Read-only, grouped sensor capability inventory and details |
| P2-08 | Validated Rust metrics, bounded JNI, preserved mean and safe errors |
| P2-09 | Connected Overview, Device, Sensors and Diagnostics; Settings preserved |
| P2-10 | English/Persian, RTL/LTR, lifecycle collection, accessibility and adaptation |
| P2-11 | Available build/native/JVM/instrumentation/release checks; limitations in O |
| P2-12 | Architecture/source/protocol docs, security review and local commits |

## R. Phase 3 readiness

Typed snapshots, source-aware unavailable outcomes, a testable public collector
boundary, bounded native processing and shared foreground state can support later
features. Live monitoring, deeper diagnostics and benchmarks require separate
scope, lifecycle/power/privacy design and additional validation. None is enabled
or implemented by this phase.

---

# Phase 2 — Final Code Review Corrections

Date: 2026-10-23. The report above preserves the original implementation evidence.
Results below concern only the final review corrections, starting from
`f75eef8f3894bbaa727757b9affdbfa093dd894a`.

## A. Executive summary

Four focused corrections finalize the existing Phase 2 implementation: consistent
initial loading/failure/retry presentation; refresh-scoped sensor expansion;
OEM-safe real-device assertions; and explicit Diagnostics inventory status.
No hardware collector, native algorithm, dependency, toolchain or permission changed.

## B. Initial state

Preflight found feature/phase-2-device-intelligence checked out, clean, and equal
to origin at the reviewed SHA. All five Phase 2 commits were present. Local and
remote master were `f99a48e7ca63134ffd395499c80d68271544cd80`. Fetch found no
unexpected commits. GitHub confirmed [baseline run 39850034616](https://github.com/masoudd2159/DeviceAtlas/actions/runs/39850034616)
was a successful push run for f75eef8: Android/four-ABI, Rust host, Windows
PowerShell exporter and PowerShell 7 exporter all succeeded.

## C. Confirmed defects and reproduction

| Severity in this task | Source | Root cause and impact | Reproduction / correction |
|---|---|---|---|
| High | HardwareScreens.kt / HardwareRefresh | Independent failure/no-snapshot conditions implied an ended failed operation was still loading | Initial-failure fixtures in both locales failed before correction; use one mutually exclusive status policy |
| Medium | HardwareScreens.kt / SensorCard and lazy item keys | Positional keys plus type/index saveable inputs can reuse expansion for a different same-type sensor | Two sensors with identical type/name/vendor but different metadata were reordered at the same timestamp; old code retained an expanded card; inventory revision now scopes all sensor item state |
| Medium, test portability | HardwareScreenTest.kt | Assertions expected Build.MODEL.trim() even when the sanitizer rejects that metadata, and one test inherited locale/theme | Assertions now use platformText and scoped localized unavailable reasons; deterministic fixtures cover null/blank/unknown/control characters; per-test preferences are isolated/restored |
| Low–medium | HardwareScreens.kt / HardwareIntegrity | Whole-inventory unavailable state was excluded from field flattening and silently omitted | Empty/nonempty/unavailable inventory fixtures failed before correction; separate localized inventory status row now reports the outcome |

The first targeted Compose run executed 12 cases with 8 expected failures against
the old rendering/keys (revision field was present but not yet consumed by those
keys). The reorder test reproduced a state-association bug for same-type sensors;
it does not claim Android normally reorders its inventory. The distinct-type
accelerometer/gyroscope example is insufficient by itself because existing
rememberSaveable type inputs can reset that case.

## D. UI state correction

| Snapshot | Refreshing | Failed | Presentation |
|---|---|---|---|
| None | false | false | Honest not-yet-loaded prompt and enabled Refresh |
| None | true | either | Loading only; action disabled, indeterminate progress semantics |
| None | false | true | Initial load failed and enabled Retry; no loading text |
| Present | false | false | Original capture time, no stale status message |
| Present | true | either | Refreshing with original capture time; action disabled |
| Present | false | true | Failed refresh with original capture time and existing data |

Successful retry clears failure/loading. Active work takes precedence over a stale
failure flag. The polite live region remains; Settings/navigation are never blocked
by a global loading screen. HardwareReviewRegressionTest covers initial failure
in English/Persian, loading/action semantics, initial retry and recovery with data.

## E. Sensor identity and restoration policy

HardwareUiState carries a presentation-only Long sensorInventoryRevision. Each
successful available inventory replacement, including an equal/empty list at the
same timestamp, advances it with checked arithmetic. Failed refresh or unavailable
inventory does not invent an available-inventory revision. Lazy item keys and
rememberSaveable inputs include revision plus the unique collection index.

This deliberately collapses details for a new inventory instead of pretending
that type/name/vendor is a reliable sensor identity. Same-type/name/vendor
duplicates remain independent. Ordinary recomposition and saved-instance-state
restoration within the same revision preserve legitimate expansion. An Activity
resume that produces a fresh inventory resets it, even if its contents look equal.
SavedStateHandle saves only the revision counter; a newly recreated ViewModel
advances beyond that saved counter before displaying new inventory, preventing
collision with restored expansion from a prior owner. No hardware observation,
sensor fingerprint, persistent sensor ID or registry is saved.

Tests cover same-type reorder, same timestamp, normal recomposition, identical
duplicates, StateRestorationTester restoration, equal successful replacements,
failed/unavailable results and restored-owner counter advancement. Existing
Activity recreation/navigation tests remain in the complete runtime suites.

## F. OEM portability

HardwareScreenTest derives expected model text with the exact production
platformText sanitizer. Available text is checked inside hardware_MODEL; missing
metadata uses reasonLabel and current localized resources. No second sanitizer,
OEM constant or weakened launch-only assertion is introduced. Tests compare real
Build.MODEL with real production output. Fake-only Persian fixtures cover missing,
blank, unknown and long mixed technical metadata.

Before each real-device test, original app locales/theme are saved, English and
Light are selected through the existing locale/UI paths, and locale/persistence
completion is awaited. Teardown restores original preferences. Fake Compose tests
use DeviceConfigurationOverride for locale rather than global app language changes.

## G. Diagnostics

Inventory is a separate row: successful query plus sensor count (zero is valid),
or unavailable plus its specific localized reason. Field totals continue to count
device fields and individual sensor metadata only; inventory is excluded and the
scope is explained. Partial sensor metadata failures still contribute to invalid,
native-error and unavailable-field counts. Tests cover empty inventories in both
languages, nonempty inventory with partial errors and unavailable inventory.
The original mean integration check remains unchanged.

## H. Gradle connected-test investigation

One controlled API 26 emulator attempt selected only the new regression class.
It reproduced INSTALL_FAILED_ALREADY_EXISTS installing the already-present app
APK; the task again returned exit 0 without executing the requested tests. This
is recorded as a failed installation, not a successful test run.

Inspection of the installed Android test engine 1.0.1 installer bytecode shows
that -r/-d flags are conditional on forceReinstall. Explicit adb install -r of the
same app/test APKs succeeds, so this evidence does not indicate a signing mismatch.
The complete upstream cause of the runner configuration/false-success behavior
was not established. No Gradle/AGP pin, SDK bootstrap, test option or device setting
was changed, and no personal app was uninstalled or cleared. The verified fallback
is explicit device selection, replacement installation of this app's APKs, and
direct AndroidJUnitRunner execution. A Gradle exit code alone remains insufficient.

## I. Architecture and Rust/JNI

Android acquisition, reader mutex, qualified dispatchers, Hilt, Navigation 3 and
Rust-first numeric processing remain intact. Native sources and JNI declarations
have no diff from the reviewed baseline. viewModelScope remains the job owner;
duplicate refreshes coalesce, cancellation propagates and ensureActive prevents
late publication. Only UI revision state uses the existing SavedStateHandle API;
there is no new storage framework, polling loop, background service or hardware ID.

## J. Fresh verification

Fresh command/evidence record (local build logs and generated XML remain ignored):

| Command / check | Actual result | Evidence |
|---|---|---|
| `gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:assembleRelease :app:bundleRelease :app:lintDebug :app:lintRelease :app:build --max-workers=2 --console=plain` | PASS; 51 JVM tests, 0 failures; debug/release/test APK/AAB; 0 lint errors and the same 2 existing warnings per variant | build/review2-final-build.log; app/build/test-results/testDebugUnitTest/TEST-*.xml |
| Direct AndroidJUnitRunner, new HardwareReviewRegressionTest only, before renderer/key fixes | Expected FAIL; 12 cases, 8 failures, including same-type reorder | build/review2-compose-red.log |
| Direct AndroidJUnitRunner, focused class after fixes | PASS; 12 cases, 0 failures | build/review2-focused-green.log and .xml |
| Full explicit AndroidJUnitRunner on selected API 26 x86_64 emulator | PASS; 48 tests, 0 failures/errors/skips, 32.672s | build/review2-api26-final.log and .xml |
| Full explicit AndroidJUnitRunner on selected API 33 ARM64 device | PASS; 48 tests, 0 failures/errors/skips, 85.353s | build/review2-api33-final.log and .xml |
| `gradlew.bat :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=io.github.masoudd2159.deviceatlas.HardwareReviewRegressionTest --max-workers=2 --console=plain`, ANDROID_SERIAL set to API 26 emulator | FAIL at app installation; zero requested tests executed despite exit 0 | build/review2-connected-reproduction.log |
| `gradlew.bat :app:assembleDebug --offline --max-workers=2 --console=plain` | PASS; configuration cache reused, no dependency/native downloads | build/review2-offline.log |
| `cargo fmt --manifest-path native/Cargo.toml --all -- --check` | PASS | Tool output in this execution |
| `cargo clippy --manifest-path native/Cargo.toml --locked --all-targets -- -D warnings` | PASS | Tool output in this execution |
| `cargo test --manifest-path native/Cargo.toml --locked` | BLOCKED; Windows link.exe missing, exit 101 | build/review2-rust-host.log |
| Four-ABI APK/AAB entries and both JNI symbols on every release .so | PASS | SDK ZIP inspection / llvm-readelf in this execution |
| `zipalign -c -P 16 -v 4 app/build/outputs/apk/release/app-release-unsigned.apk` | PASS | Verification successful in this execution |
| Packaged release permissions via aapt | PASS; existing app-private signature receiver permission only | SDK aapt in this execution |
| Temporary debug-signed minified release runtime check on both devices | PASS; launch/navigation, zero native-processing failures, original mean=4.0; debug app restored | build/review2-release-smoke.log |
| `graphify update .` | PASS with the existing extractor limitations; AST-only, no API cost; 997 nodes/1965 edges/61 communities | build/review2-graph.log, untracked/ignored graph output |
| Genuine 16 KiB-page execution, complete manual TalkBack/physical foldable matrix | NOT RUN; unchanged environment coverage limits | Original report O and final delivery report |

The direct path installs only this app/test APK with `adb -s <selected device>
install -r`, then runs `adb -s <selected device> shell am instrument -w -r
io.github.masoudd2159.deviceatlas.test/androidx.test.runner.AndroidJUnitRunner`.
Local JUnit XML is converted from the raw runner's per-test status events, with
the final OK test count checked against recorded cases; it is not misrepresented
as output from the failed Gradle connected task. The ignored helper fails on any
test failure or incomplete runner output. All original tests are retained.

The first lint invocation encountered a Kotlin/UAST resolver internal failure;
subsequent analysis exposed configuration-awareness errors in the new test fixture
and a test-only SavedStateHandle constructor default warning. Fixtures now use
LocalConfiguration/stringResource, and production Hilt supplies SavedStateHandle
without a test constructor default. Final lint passes without disabling detectors,
adding a baseline or changing any pinned version. The original 19-per-runtime
native execution above remains historical evidence: native/JNI sources did not
change, so those device-native binaries were not needlessly rerun. Fresh hosted
Rust results belong to the final pushed SHA only when CI confirms them.

## K. Localization and accessibility

Six paired strings bring English/Persian resources to 232 keys each. Existing
LocalizationTest checks parity, duplicates, placeholders, nonempty translations
and Persian characters. Refresh retains a polite live region and now exposes
indeterminate progress semantics; Retry/Refresh is disabled during active work.
English/Persian fake tests are configuration-aware (LocalConfiguration and
stringResource). Existing Persian 200% font, RTL, themes and navigation checks remain.

## L. Privacy and security

The manifest, dependencies, backups, native methods and toolchain files are
unchanged. No permission, networking, telemetry, hidden API, unique identifier,
sensor sampling or snapshot export was added. The saveable revision is only a
local UI counter. No hardware data is put in SavedStateHandle. Generated Graphify
artifacts, local runners, XML/log evidence, binaries, private paths and signing
material remain ignored development artifacts and are not staged.

## M. Changed files

Nine files changed; no native, collector, build pin, manifest or workflow diff:

```text
app/src/main/java/io/github/masoudd2159/deviceatlas/presentation/hardware/HardwareScreens.kt
app/src/main/java/io/github/masoudd2159/deviceatlas/presentation/hardware/HardwareViewModel.kt
app/src/main/res/values/strings.xml
app/src/main/res/values-fa/strings.xml
app/src/androidTest/java/io/github/masoudd2159/deviceatlas/HardwareReviewRegressionTest.kt
app/src/androidTest/java/io/github/masoudd2159/deviceatlas/HardwareScreenTest.kt
app/src/test/java/io/github/masoudd2159/deviceatlas/presentation/hardware/HardwareViewModelTest.kt
docs/architecture/hardware-collection.md
docs/phase-2-report.md
```

## N. Local commits

| SHA | Commit / scope |
|---|---|
| 57e3f97b00f10e7d8d5c14013aea342bfac7a2a3 | fix(ui): correct hardware status and scope sensor expansion to inventory — state policy, inventory diagnostics/revision, paired strings, 12 Compose and 3 JVM regression additions |
| ab82c8faa2500ad0907a95c1ce22877b072f277d | test(android): harden hardware assertions across OEM devices — sanitizer-based expectations, locale/theme setup and restoration |

The subsequent `docs: finalize Phase 2 review corrections and verification`
commit contains this appendix and the architecture policy update; its SHA is
reported in the final delivery and Git log. All original five Phase 2 commits
remain unchanged; no amend, rebase or history rewrite occurred.

## O. Push verification

The user authorized a normal push of this feature branch only. The final delivery
report records the push result and full verified remote HEAD after a fresh fetch.
This documentation commit cannot contain its own SHA. Master is not pushed.

## P. Hosted CI

Baseline run 39850034616 is verified successful for f75eef8. New CI must be checked
after this correction/documentation commit is pushed. Its exact final SHA, run URL
and all job outcomes are recorded in the final delivery report; the earlier
baseline run must not be substituted as evidence for the corrected HEAD.

## Q. Final Git safety

Delivery requires local HEAD equal to origin/feature/phase-2-device-intelligence,
clean status, and unchanged master f99a48e. Only task files are staged, with diff
whitespace checks. No force-push, master push, tag, release, merge or Phase 3 work
is authorized or performed. The delivery report contains the final Git inspection.

## R. Final Phase 2 assessment

Final readiness is determined from the corrected local verification and the exact
pushed SHA's CI status in the delivery report. Unavailable OEM fields, Windows host
linker absence and a missing genuine 16 KiB runtime remain documented environment
or platform limits, not fabricated successful tests.

## S. Phase 3 handoff

Device discovery and Rust processing remain available for future separately scoped
development. This task introduces no live monitoring, benchmark or Phase 3 feature.
