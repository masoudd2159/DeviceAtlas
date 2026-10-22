# DeviceAtlas

DeviceAtlas is an offline-first Android device-information and monitoring project.
**Phase 1 implements the application foundation**: an adaptive Material 3 shell,
typed Navigation 3, English/Persian with runtime locale selection, persistent
System/Light/Dark themes, and honest structural device/sensor/diagnostics pages.
The verified Hilt → ViewModel → Rust/JNI mean using test inputs `[2.0, 4.0, 6.0]`
remains available under Diagnostics. No hardware data is read or simulated.

Application ID and namespace: `io.github.masoudd2159.deviceatlas`.
Minimum SDK **26**; compile/target SDK **37**; version **1.0 / 1**.

## Pinned toolchain

| Component | Selected version |
| --- | --- |
| AGP / Gradle | 9.4.1 / 9.8.2 |
| Gradle daemon JDK / Java-Kotlin bytecode | 25 / 17 |
| Kotlin and Compose Compiler plugin | 2.4.21 |
| Compose BOM | 2026.09.00 |
| Resolved Material 3 / Compose UI | 1.4.0 / 1.12.1 |
| AndroidX Activity / Lifecycle | 1.13.0 / 2.11.0 |
| Hilt / KSP | 2.60.1 / 2.3.12 |
| Coroutines | 1.11.0 |
| NDK | 30.0.16248370 (r30), explicitly selected |
| Android Build Tools | 37.0.0, explicitly selected |
| Rust / cargo-ndk | 1.99.1 / 4.1.2 |
| Rust JNI ABI bindings | jni-sys 0.3.0 |

AGP 9 built-in Kotlin remains enabled. Do not add `org.jetbrains.kotlin.android`
or kapt. The Compose Compiler plugin and resolved Kotlin compiler are both 2.4.21;
the Gradle wrapper's own embedded Kotlin version is separate. See the official
compatibility evidence and actual dependency reports in
[the final verification report](docs/phase-0-final-verification.md).

## Setup

Install Android Studio compatible with AGP 9.4, JDK 25, SDK Platform 37,
Build Tools 37.0.0, Platform Tools, and NDK 30.0.16248370. Set `ANDROID_HOME`
or let Android Studio create the ignored `local.properties` with `sdk.dir`.
The existing Gradle daemon criteria select JDK 25; Java/Kotlin sources target 17.

Install rustup. From the project, Cargo selects the version, rustfmt, Clippy,
and Android targets pinned in `rust-toolchain.toml`. Install the build helper:

```sh
cargo install cargo-ndk --version 4.1.2 --locked
```

Windows host tests and building cargo-ndk from source need Visual Studio Build
Tools with Desktop development with C++ and Windows SDK. An official prebuilt
cargo-ndk is also available from its
[release page](https://github.com/bbqsrc/cargo-ndk/releases/tag/v4.1.2);
verify its SHA-256 using the release metadata.

Gradle finds Cargo in `CARGO_HOME/bin` or `~/.cargo/bin` and adds that directory
to native subprocess PATH. This supports Android Studio without terminal PATH
edits. Put cargo-ndk there too. A custom Cargo executable can be selected with
`-PcargoExecutable=C:/path/to/cargo.exe`; inherited PATH is the final fallback.

## Build

On Windows use `.\gradlew.bat` instead of `./gradlew`:

```sh
./gradlew help
./gradlew :app:assembleDebug
./gradlew :app:assembleRelease :app:bundleRelease
```

No manual Cargo build is required. The public AGP variant source API wires
`buildRustDebug` / `buildRustRelease` into JNI packaging with explicit inputs
and outputs. Builds use API 26, the pinned NDK, Cargo.lock and `--locked`.
With dependencies cached, `./gradlew :app:assembleDebug --offline` also passes
`--offline` to Cargo. Initial dependency/toolchain downloads require connectivity;
the application itself has no network capability.

Generated JNI libraries:
`app/build/generated/rust/<variant>/jniLibs/<abi>/libdeviceatlas_core.so`.
Cargo intermediates: `app/build/rust-target/<variant>`.
Release APK/AAB artifacts are unsigned until production signing is configured
outside version control. R8/resource shrinking and the JNI name keep rule are enabled.

Primary supported ABIs: **arm64-v8a** and **x86_64**.
Additional build targets retained from the local baseline: **armeabi-v7a**, **x86**.
See the report for the actual runtime-tested ABI/device matrix.

## Architecture and UI

There is one Android module, `:app`, and one existing Rust crate, `native/`.
Package boundaries keep platform UI/preferences separate from the Rust-first
computational capability. No empty feature modules, repository framework, database
or network framework is introduced. See [architecture](docs/architecture/overview.md)
and the [Phase 1 verification report](docs/phase-1-report.md).

```text
MainActivity + DeviceAtlasApplication     Hilt/AppCompat/Compose, official app locales
presentation/shell + navigation          Adaptive shell, typed saved per-tab stacks
presentation/settings                    Application theme state and persistence actions
presentation/home                        Entry-scoped Rust verification state and UI
presentation/theme + components          Material 3 tokens, shared UI, previews
foundation/NativeMath                     Typed math capability and result contract
data/nativecore                          Explicit loading, validation, JNI gateway
foundation/AppPreferences + data/preferences  Android-only theme persistence
di                                       Capability bindings, Default/IO dispatchers
native/src/lib.rs                        Pure Rust mean and numerical unit tests
native/src/bridge.rs                      JNI array/status boundary
```

`NativeMath` is the capability seam used by Hilt and isolated tests. The ViewModel
owns UI state and coroutine lifetime; Compose uses `collectAsStateWithLifecycle`.
Native calls execute on an injected Default dispatcher. Cancellation is propagated.
Screens have heading/live-status/radio semantics, scroll support, complete
English/Persian resources, fallback color schemes and system-inset handling.
AppCompat owns locale persistence on API 26–32 and delegates to framework per-app
language settings on API 33+. Navigation and Settings survive recreation; theme
and language survive process restart. See [localization](docs/architecture/localization.md),
[navigation](docs/architecture/navigation.md) and [design system](docs/architecture/design-system.md).

## JNI contract

The gateway validates non-empty, finite input and explicitly loads
`deviceatlas_core`. Rust validates again, computes a compensated mean with overflow-safe fallbacks,
and writes a finite output only on success.

Native signature: `NativeBridge.mean(DoubleArray?, DoubleArray?): Int`.
The output array must have length one.

| Status | Meaning |
| --- | --- |
| 0 | Success; output contains the mean |
| 1 | Empty input |
| 2 | Non-finite input |
| 3 | Null/invalid buffer |
| 4 | JNI or allocation failure |
| 5 | Contained Rust panic |

Kotlin exposes `MeanResult.Success` or a typed `MeanResult.Failure`; unknown
statuses or non-finite success output map to native failure. Load/symbol/security
failures map to library unavailable. No NaN, infinity, or magic numeric sentinel
is used to communicate failure.

JNI region operations copy input once into a fallibly allocated Rust Vec and
copy one result back. No array is pinned and no JNI pointer/global reference is
retained. Rust-owned memory is dropped on every return/unwind path. The native
boundary contains Rust unwinding and maps pending JNI exceptions to a failure
status. It does not call Android framework hardware APIs.

## Tests

```sh
./gradlew :app:testDebugUnitTest
./gradlew :app:lintDebug
./gradlew :app:connectedDebugAndroidTest
cargo test --manifest-path native/Cargo.toml --locked
cargo fmt --manifest-path native/Cargo.toml --all -- --check
cargo clippy --manifest-path native/Cargo.toml --locked --all-targets -- -D warnings
```

Connected tests require a supported Android runtime. They check Compose content,
the real Hilt → ViewModel → JNI result, native array/error handling, repeated
calls, actual library-loading failure, application identity, permissions, and
backup configuration. Kotlin gateway tests use injected call/load functions and
**do not establish JNI execution**. Rust tests exercise math independently of JNI.

## Privacy, backups, and limitations

The app requests no INTERNET, hardware, identifier, analytics, or monitoring
permissions. AndroidX contributes only its internal signature-protected receiver
permission. No telemetry SDK, background monitoring, hardware extraction, or fake
hardware values are present.

Backup is disabled. Legacy and Android 12+ cloud/device-transfer rules exclude
all supported data domains. Android/OEM migration behavior can differ, so this is
a conservative platform policy rather than a universal device-transfer guarantee.
Phase 1 stores only non-sensitive appearance and application-locale preferences.

Windows default host Rust tests currently need the missing MSVC linker. Actual
Android execution passes on ARM64/API 33 and x86_64/API 26: 26 connected tests and
12 standalone Rust tests on each runtime. Both use 4 KiB pages. A 16 KiB-page
runtime remains unverified; APK/64-bit ELF alignment passes static checks.
Upstream Gradle/protobuf tool warnings are documented without suppressions.

See [the Phase 1 report](docs/phase-1-report.md) for current results, exact commands
and remaining test-environment gaps. The
[Phase 0 final verification report](docs/phase-0-final-verification.md) retains
historical compatibility evidence.
[docs/phase-0-report.md](docs/phase-0-report.md) and
[docs/validation.md](docs/validation.md) retain the historical development record.

IDE state, local SDK/signing configuration, build/native outputs, Graphify output,
and crash dumps are ignored. The project-scoped Graphify skill/hook installation
is retained locally and excluded through `.git/info/exclude`; it is not part of
the intended application source changes. Required wrapper assets and Cargo.lock
remain source-controlled inputs.

## Development Tools — Exporting Codex Changes

Run the helper from anywhere inside this repository with Windows PowerShell 5.1
or PowerShell 7 and Git installed:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\export-codex-changes.ps1 -Base master
```

The example invocation assumes the repository root. Omit `-Base` to compare with
HEAD. The helper combines staged and unstaged changes into their final working
versions, includes non-ignored untracked text files, and records tracked deletions.
It creates a uniquely named `codex-changes-<project>-<timestamp>-<id>/` folder and
matching ZIP in the repository's parent directory. No existing output is overwritten.
Use `-OutputParent 'D:\Review Exports'` for another existing directory outside the
repository. The Git working tree, index, history and caller location are preserved.

The bundle contains `files/` with accepted current files, `changes.patch` with
accepted tracked changes, filtered `git-status.txt` and `changed-files.txt`, and
`README.txt` with the resolved base commit and exclusion count. Untracked files
are copies, not patch additions. Renames are represented as delete/add. The
running exporter excludes itself, as in the original utility.

Generated output, IDE/tool state, local configuration, signing/credential stores,
logs, binary/non-UTF-8 files, links, and suspected secrets/private paths/device
identifiers are excluded, including from patches and metadata. Both the selected
base and current content are checked so removed credentials do not leak through a
diff. Defaults are 1 MiB per file version and 10 MiB total input across base/current
versions; adjust with `-MaxFileBytes` and `-MaxTotalBytes` (maximum 100 MiB each).
These limits can omit large source files; the bundle is a filtered review aid,
not a backup. Avoid editing files during export.

Detection is conservative and heuristic: inspect the folder, patch and metadata
before manually sharing the ZIP with ChatGPT. Arbitrary secrets or identifiers
can evade detection; legitimate examples can be excluded. The helper performs no
upload, fetch, push or other remote operation. Review bundles moved back into the
repository are ignored. To run the isolated regression checks without changing
this repository:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\tools\Test-ExportCodexChanges.ps1
pwsh -NoProfile -File .\tools\Test-ExportCodexChanges.ps1
```

The review-corrected exporter distinguishes repository-relative paths from private
absolute paths. Legitimate `presentation/home/` references are retained; current
and historical secrets, private paths, links, generated/binary files and unsafe
names remain filtered. Both PowerShell editions pass 246 regression assertions
on this host. The running exporter still intentionally excludes itself; inspect
the final accepted-file inventory and ZIP before sharing.

## CI and review quality gates

[The CI workflow](.github/workflows/verify.yml) defines Linux Rust host checks,
Android JVM/lint/debug/minified-release/AAB checks with all four JNI ABIs, and
Windows PowerShell 5.1/7 exporter checks. It installs the exact project SDK, Build
Tools, NDK, Rust and cargo-ndk prerequisites and uses JDK 25. It has read-only
repository permissions, cancellable concurrency and bounded job timeouts; no
production signing, deployment or artifact upload is configured.

Hosted run [39832385114](https://github.com/masoudd2159/DeviceAtlas/actions/runs/39832385114)
failed workflow validation before any jobs were created: a step's shell field
cannot use the matrix context. The local correction uses fixed shells with matrix
conditions; actionlint now passes. Both official v5 setup Actions were resolved,
their configured inputs checked and their immutable revisions pinned. YAML,
permissions, runner labels and Bash syntax were checked locally. **Hosted execution
of the corrected workflow remains NOT RUN pending an authorized push.**

Current review evidence: **32 JVM tests, 26 connected tests per runtime, 12 Rust
tests per runtime, and 246 exporter assertions per PowerShell edition**. The
Windows MSVC linker gap remains separate from the proposed Linux CI host job.
These are prior verification results, not newly rerun suites. See
[final acceptance](docs/phase-1-report.md#final-phase-1-closure-and-phase-2-readiness)
for the current decision and fresh checks; the earlier review closure preserves
commands, manual gaps and the killed-process/existing-task restoration result.

**Phase 1 accepted with documented verification limitations. Ready for Phase 2 planning.**
Phase 2 implementation has not started. Hosted CI and unavailable manual/device
environments remain explicit follow-up risks rather than universal prerequisites
for accepting this engineering foundation.

An installed debug app on an explicitly selected disposable emulator can run the
additional restoration check (adb must be on PATH):

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\tools\Test-TaskRestoration.ps1 -Serial $env:ANDROID_SERIAL
```

The check backgrounds DeviceAtlas, attempts ActivityManager termination, and uses
an app-UID signal if necessary. It requires an absent old PID, a new PID, the same
task ID and a restored nested About page. It never force-stops, clears app data,
changes device settings, or runs against a physical-device serial.
