# Validation evidence

Phase 4 commands, positive test counts, release probe procedure, native packaging,
and outstanding coverage are recorded in [the Phase 4 report](phase-4-report.md).

## Phase 3 final review corrections — 2026-10-23

The [final correction section](phase-3-report.md#phase-3--final-code-review-corrections-and-integration-readiness)
supersedes the initial local delivery record below. Fresh Debug JVM execution:
79 tests, zero failures/errors/skips. Debug/release lint and builds, R8/AAB/full
build and offline assembly pass. Native source/protocols, manifest, toolchain and
287 paired resource keys remain unchanged. Exact final device/CI/integration
evidence is recorded in the correction section and final delivery report. Earlier
results below retain their historical meaning and are not claimed as reruns.

## Phase 3 local acceptance — 2026-10-23

See [Phase 3 report](phase-3-report.md) for commands, unsuccessful attempts and limits.
Latest evidence: 69 debug JVM tests; 53 direct instrumentation tests on each API 26
x86_64/API 33 ARM64; 23 Rust tests executed on each runtime; debug/minified release/
AAB/full/offline builds and lint pass. No hosted Phase 3 run or Gradle-connected
instrumentation success is claimed. Windows host Rust execution remains blocked
by missing MSVC link.exe. Static 64-bit 16 KiB ELF/APK ZIP alignment passes; genuine
16 KiB execution was not run. Normal minified monitoring and legacy mean succeeded
on both devices. Historical Phase 0/1/2 evidence below remains unchanged.

## Current Phase 2 acceptance — 2026-10-23

**ACCEPTED — ready for integration**, with documented noncritical tooling and
coverage limits. Reviewed SHA: `6a1abf1ba138ffc70fe48fdb43284068659f0147`.
Hosted [run 39855515251](https://github.com/masoudd2159/DeviceAtlas/actions/runs/39855515251)
is confirmed SUCCESS for that SHA: Android/four-ABI JNI, Linux Rust host checks
(19 tests), Windows PowerShell and PowerShell 7 exporters (252 assertions each).
Hosted Android verification builds/tests/lints the project but runs no device
instrumentation. Prior local review evidence is 51 JVM tests and 48 direct-run
instrumentation tests on each API 26 x86_64 and API 33 ARM64 runtime, all passing.
No heavy suites were rerun solely for closure documentation.

The Gradle connected installer can fail with `INSTALL_FAILED_ALREADY_EXISTS`
while returning exit 0 and executing zero tests. Use the explicit selected-device
replacement-install/AndroidJUnitRunner path and require a positive test count,
complete runner success and no failures; see the
[current acceptance and fallback](phase-2-report.md#current-acceptance--2026-10-23).
Windows MSVC host-linker absence, genuine 16 KiB runtime testing, complete manual
accessibility/foldable/external-display QA and broader OEM/API coverage remain
explicit limits. Final merge/smoke/remote CI evidence belongs to the closure
delivery report; the cited run establishes the reviewed baseline only.

## Historical foundation validation — 2026-10-22

Phase 1 validation is recorded in [the Phase 1 report](phase-1-report.md).
Its correction evidence is in the [review-closure section](phase-1-report.md#phase-1-review-closure).
The Phase 0 development evidence below is preserved unchanged.

Historical add-only foundation record. Current Phase 0 behavior and results are in
[the final verification report](phase-0-final-verification.md), using NDK
30.0.16248370 and Build Tools 37.0.0. Versions and results below describe the older
development runs only.

For the latest closure checks and export-helper regression results, see the
**Closure addendum** in the final verification report. This file retains the
original development evidence unchanged.

Work was performed locally on `codex/android-rust-foundation`. No push, merge,
or pull request was performed. Application ID, namespace, minSdk 26, compile/target
SDK 37, version code/name, launcher assets, and existing backup policy were preserved.

## Environment

- Windows 11 x64; JDK 25.0.2; Gradle 9.8.2; AGP 9.4.1.
- Android SDK configured by the existing ignored `local.properties`.
- NDK r29 / 29.0.14206865.
- Rust 1.99.1 with rustfmt, Clippy, and all four Android targets.
- Connected Samsung SM-A325F, Android 13 / API 33, arm64-v8a.
- Cargo was not initially on the shell PATH. Validation added the existing
  `%USERPROFILE%\.cargo\bin` to the process PATH only.
- `cargo install cargo-ndk --version 4.1.2 --locked` failed because the MSVC
  linker `link.exe` is absent. Instead, validation used the official Windows x64
  prebuilt release under ignored `build/tools/`; its SHA-256 matched GitHub's
  release metadata:
  `e2687c647748a8fb1c06981a4fc39376a5b5f640474db3f73de7ae247b7c55e0`.
  That directory was added to the process PATH only. It is not a committed build
  dependency or a required absolute path in the project configuration.

## Executed checks

Commands below use `gradlew.bat` on Windows. All Gradle validation after the
initial diagnostic pass used a single invocation at a time and `--max-workers=2`.

| Command / check | Result |
| --- | --- |
| `gradlew.bat --version` | Passed: Gradle 9.8.2, launcher/daemon JDK 25 |
| `gradlew.bat :app:tasks --all` | Passed on the original configuration; identified debug/release tasks |
| `:app:assembleDebug` | Passed |
| `:app:assembleRelease` | Passed, R8 and resource shrinking enabled; unsigned APK |
| `:app:bundleRelease` | Passed; AAB generated without production signing |
| `:app:assembleDebugAndroidTest` | Passed |
| `:app:testDebugUnitTest` | Passed: 5 tests, 0 failures/errors/skips |
| `:app:lintDebug` and `:app:lintRelease` | Passed: both reports say “No issues found.” |
| `:app:build` | Passed |
| `:app:connectedDebugAndroidTest` | Passed: 4 tests, 0 failures/errors/skips, on Android 13 |
| Native Gradle tasks, debug/release | Passed for arm64-v8a, armeabi-v7a, x86_64, x86 |
| Repeated combined build/test/lint command | Passed in 1s; configuration cache reused, native tasks up-to-date |
| `cargo fmt --manifest-path native/Cargo.toml -- --check` | Passed, exit 0 |
| `cargo check --manifest-path native/Cargo.toml --locked` | Passed, exit 0 |
| `cargo clippy --manifest-path native/Cargo.toml --locked --all-targets -- -D warnings` | Passed, exit 0 |
| `cargo test --manifest-path native/Cargo.toml --locked` | Attempted; blocked by missing Windows MSVC `link.exe` |
| Rust tests cross-compiled and executed on Android | Passed: 2 tests, 0 failures |
| APK contents / native symbol inspection | All four Rust libraries packaged; JNI export present |
| ELF alignment | arm64-v8a and x86_64 release LOAD segments have 0x4000 (16 KiB) alignment |
| `zipalign -c -P 16 -v 4 app-release-unsigned.apk` | Passed: “Verification successful” |
| Minified release runtime | Temporary debug-signed copy installed and launched; UI showed JNI result 42 |
| `git diff --check` | Passed |

The main combined validation command was:

```powershell
.\gradlew.bat :app:assembleDebug :app:assembleRelease :app:bundleRelease :app:assembleDebugAndroidTest :app:testDebugUnitTest :app:lintDebug :app:lintRelease --max-workers=2 --console=plain
.\gradlew.bat :app:build :app:connectedDebugAndroidTest --max-workers=2 --warning-mode=all --console=plain
```

To run the Rust tests without the unavailable Windows host linker, validation
ran the following from `native/`, with the pinned NDK in `ANDROID_NDK_HOME`:

```sh
cargo ndk --platform 26 -t arm64-v8a test --locked --no-run
```

The resulting `native/target/aarch64-linux-android/debug/deps/deviceatlas_core-*`
test executable was pushed with adb to `/data/local/tmp/deviceatlas-rust-tests`,
made executable, run, and removed. Both smoke/negative-value and overflow tests
passed. This is Android execution, not a successful Windows host test run.

For release runtime verification, a copy of the unsigned release APK was signed
using the local Android debug key. The launched UI was checked with uiautomator.
The temporary device XML was removed and the debug APK was reinstalled afterward.
Production signing configuration and the unsigned release APK were unchanged.

## Coverage and limits

- JVM tests cover loading → success, unexpected JNI result, linkage failure,
  repository failure, and cancellation without converting cancellation to error.
- Instrumentation covers identity, Hilt/Compose Activity startup, repository → JNI
  → Rust returning 42, signed values, and overflow.
- Runtime execution was verified on arm64 Android 13 only. All four ABIs built;
  API 26, x86/x86_64 emulators, and a 16 KiB-page device were not available for
  runtime testing. Static APK/ELF alignment checks do not replace a 16 KiB runtime test.
- Windows host Rust tests need Visual Studio Build Tools with C++ and Windows SDK.
- Build-tool warnings remain: deprecated `Configuration.setVisible(boolean)` use
  in plugin configuration, and protobuf's `sun.misc.Unsafe` use under JDK 25.
  No project source calls those APIs. No warnings were suppressed.
- Initial diagnostic runs encountered a Gradle script import error and an
  incorrect cargo-ndk platform flag, both fixed. The initial pass included overlapping
  Gradle invocations and source changes, and encountered lint FIR, KSP missing
  directory, and AAPT2 errors. The subsequent controlled run passed every Android
  build/test/lint task; no lint checks were disabled and no caches were deleted.

## Local evidence

Generated evidence is ignored rather than committed:

- `build/foundation-validation.log`, `build/foundation-connected.log`
- `build/foundation-cache.log`
- `app/build/reports/tests/testDebugUnitTest/index.html`
- `app/build/reports/lint-results-debug.html` and `lint-results-release.html`
- `app/build/outputs/androidTest-results/connected/`
- `app/build/outputs/apk/debug/app-debug.apk`
- `app/build/outputs/apk/release/app-release-unsigned.apk`
- `app/build/outputs/bundle/release/app-release.aab`

These results describe this local validation session; they are not CI, publication,
or production signing results.

## Follow-up: Android Studio could not launch Cargo

The IDE's `:app:assembleDebug` failed with `CreateProcess error=2` for `cargo`.
The original validation had used a temporary shell PATH; Android Studio did not
inherit it. The native task now discovers Cargo in `CARGO_HOME/bin` or the standard
user `.cargo/bin`, while retaining the explicit `cargoExecutable` override and
inherited PATH fallback. Both native subprocesses receive Cargo's bin directory
on PATH so that rustup and cargo-ndk can also be located.

The previously verified official cargo-ndk 4.1.2 executables were copied to this
machine's standard user `.cargo/bin` directory without overwriting existing files.
No system or user PATH was changed, and no machine-specific path was committed.

`gradlew.bat :app:assembleDebug --max-workers=2 --console=plain` then passed with
the original shell PATH, rebuilding Rust for all four ABIs and packaging the APK.
Evidence: `build/foundation-ide-cargo.log`.
