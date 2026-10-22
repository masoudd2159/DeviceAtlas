# DeviceAtlas Phase 0 report — 2026-10-22

Current configuration: **NDK 30.0.16248370 and Build Tools 37.0.0**, preserved as
explicitly selected. Current checks and finalization status are in
[the final verification report](phase-0-final-verification.md).

Closure review: the final report's **Closure addendum** records the repeated checks,
reviewed export helper and authorized merge workflow. Earlier results below remain
historical; they are not relabeled as closure runs.

The development record below is historical (NDK r29). It is retained as evidence
of the earlier milestones rather than relabeling those runs as r30 verification.
Branch: `feature/phase-0-foundation`. At that stage no commit, push, merge, PR, or
remote-branch change had been performed.

## 1. Initial findings and baseline

The authoritative local checkout was on `codex/android-rust-foundation`, HEAD
`259b073 build: update project configuration and dependencies`. It already had
the earlier uncommitted Compose/Hilt/StateFlow/add-only JNI foundation, IDE
untracking, Cargo discovery fix, and a local Kotlin **2.4.21** selection.
Those changes were preserved. A source snapshot, status and binary-capable diff
were saved under ignored `build/phase0-baseline/` before implementation.
The requested branch was created without resetting, cleaning or modifying master.

| Baseline command | Actual result |
| --- | --- |
| `git status --short` | Passed; preexisting modified/staged/untracked foundation work |
| `git branch --show-current` | Passed: codex/android-rust-foundation |
| `git log -1 --oneline` | Passed: 259b073 |
| `.\gradlew.bat --version` | Passed: Gradle 9.8.2, JDK 25.0.2; wrapper embedded Kotlin 2.4.10 |
| `.\gradlew.bat help --console=plain` | Passed |
| `.\gradlew.bat :app:assembleDebug --console=plain` | Passed in 3s |

Only `:app` remains. Application ID/namespace, minSdk 26, compile/target SDK 37,
version 1.0/code 1, launcher assets, daemon JDK criteria and existing version pins
were retained. No hardware features or networking were present or added.
Rust 1.99.1, cargo-ndk 4.1.2, all four Android Rust targets, SDK 37 and NDK r29
were available. Cargo was absent from shell PATH, but Gradle discovered its
standard installation. No AVD existed; WHPX acceleration was usable.
Default Windows Rust tests were diagnosed as missing MSVC `link.exe`.

## 2. Versions and compatibility decisions

| Component | Selected/resolved | Official evidence and decision |
| --- | --- | --- |
| AGP / Gradle | 9.4.1 / 9.8.2 | [AGP 9.4](https://developer.android.com/build/releases/agp-9-4-0-release-notes): SDK 37 support, Gradle minimum 9.6, JDK minimum 17. Retained wrapper with SHA-256. |
| Kotlin / Compose Compiler | 2.4.21 / 2.4.21 | [Kotlin releases](https://kotlinlang.org/docs/releases.html), [compiler setup](https://kotlinlang.org/docs/compose-compiler-migration-guide.html). Actual compiler classpath resolves 2.4.21. |
| Built-in Kotlin | Enabled | [AGP migration guide](https://developer.android.com/build/migrate-to-built-in-kotlin). No kotlin-android plugin, kapt or opt-out. |
| Compose BOM | 2026.09.00 | [Official BOM](https://developer.android.com/develop/ui/compose/bom). Same BOM for app and tests; resolved Material 3 1.4.0 and Compose UI 1.12.1. |
| Activity / Lifecycle | 1.13.0 / 2.11.0 | [Activity](https://developer.android.com/jetpack/androidx/releases/activity), [Lifecycle](https://developer.android.com/jetpack/androidx/releases/lifecycle); retained stable selections. |
| Hilt / KSP | 2.60.1 / 2.3.12 | [Hilt KSP setup](https://dagger.dev/hilt/gradle-setup), [KSP release](https://github.com/google/ksp/releases/tag/2.3.12); actual generation/runtime injection pass. |
| Coroutines | 1.11.0 | [Official release](https://github.com/Kotlin/kotlinx.coroutines/releases/tag/1.11.0); app/test versions aligned. |
| JDK / bytecode | 25 / 17 | Existing JDK retained; Java/Kotlin bytecode explicitly 17. Builds run on JDK 25.0.2. |
| NDK | 29.0.14206865 | [Stable NDK history](https://developer.android.com/ndk/downloads/revision_history); existing r29 pin retained. |
| Rust / cargo-ndk | 1.99.1 / 4.1.2 | [Rust release](https://github.com/rust-lang/rust/releases/tag/1.99.1), [cargo-ndk](https://github.com/bbqsrc/cargo-ndk/releases/tag/v4.1.2); exact pins retained. |
| JNI ABI bindings | jni-sys 0.3.0 | [Upstream definitions](https://github.com/jni-rs/jni-sys/blob/v0.3.0/src/lib.rs); exact, dependency-free typed JNI declarations for the few audited calls. |

No broad production upgrades were performed. New Compose testing artifacts are
BOM-managed. The wrapper's embedded Kotlin is separate from the app compiler.
Actual compiler/runtime dependency reports are in `build/phase0/`; successful
debug/release compilation, D8/R8, lint and Hilt generation substantiate this
configuration rather than implying an exhaustive published compatibility matrix.

## 3. Milestones and test design

The sequence was Plan → Test Design → Implement → Review → Verify. Tests were
designed around behavior and failure boundaries before native implementation:
Kotlin validation/loading/status mapping with fakes, ViewModel state/cancellation,
real Compose/JNI device tests, and pure Rust numerical tests.

| Milestone | Implementation and milestone verification |
| --- | --- |
| P0-01 | Baseline help/build passed; retained pins, built-in Kotlin and resolved compiler verified. |
| P0-02 | Dynamic Color on API 31+, light/dark fallback tokens, typography/shapes/spacing, resource strings, heading/live-status semantics, scrolling/insets. Build, five initial JVM tests and six initial Android tests passed before native changes. |
| P0-03 | Real Hilt entry points retained; direct NativeMath capability replaces pass-through repository. Seven ViewModel tests pass, including ViewModel clearing/cancellation. |
| P0-04 | Pure mean, separate JNI bridge, explicit status/output contract, one input copy, fallible allocation, panic containment and typed Kotlin failures. Actual arm64 JNI tests pass. |
| P0-05 | 17 JVM tests, 12 final arm64 Android tests and 10 Rust tests executed on Android pass. Windows default host tests lack linker prerequisites. |
| P0-06 | Backup/permissions hardened and checked against packaged app; individual IDE review; security milestone build/test/lint passed. |
| P0-07 | Debug/release APK, release AAB, lint, full/offline builds, packaging/alignment and real minified-release mean checked. Runtime matrix/limits below. |

No test was removed to hide a failure. The add-only internal smoke test was
superseded by meaningful mean tests covering signed values, extremes, invalid
arrays and loading failures. Kotlin fake-gateway tests are not claimed as JNI
verification. Rust tests independently exercise the mathematical implementation.

## 4. Changed files and architecture

One Android module and the existing `native/` crate remain. No repository,
UseCase, navigation, database, network library or feature module was added.
`NativeMath` is a narrow injected capability and fake seam. The ViewModel owns
immutable StateFlow and coroutine lifetime; the gateway owns loading, validation,
Default dispatcher use and native result mapping. Pure math is separate from JNI.

Added in this phase:
- `foundation/NativeMath.kt`, `data/nativecore/RustNativeMath.kt`.
- `presentation/theme/Tokens.kt`, `native/src/bridge.rs`.
- `FoundationScreenTest.kt`, `RustNativeMathTest.kt`, this report.

Modified:
- App Gradle script and version catalog: BOM-managed UI testing; Cargo offline flag.
- NativeBridge, Hilt bindings, HomeViewModel/HomeScreen/theme and string resources.
- Manifest and both backup XML rules; existing ViewModel/Activity/native/identity tests.
- Cargo manifest/lock/lib.rs, README and crash-dump ignore rules.

Removed from the working baseline: `NativeCoreRepository.kt` and
`RustNativeCoreRepository.kt`, replaced by the direct capability/gateway. These
untracked baseline files are preserved in the ignored source snapshot.
Previously staged IDE deletions and starter cleanup remain unchanged in scope.
The final inventory addendum lists paths; no unrelated user work was discarded.

## 5. Exact commands and actual results

Commands run from the project root on Windows 11 x64/JDK 25.0.2/SDK 37/NDK r29
unless noted. Shell Cargo is invoked from `%USERPROFILE%/.cargo/bin/cargo.exe`;
Gradle finds it without shell PATH edits.

| Command | Actual result |
| --- | --- |
| `.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:connectedDebugAndroidTest --max-workers=2 --console=plain` | Passed for Compose milestone and again after mean integration. |
| `.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug --max-workers=2 --console=plain` | Security milestone passed; no lint issues. |
| `.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug :app:connectedDebugAndroidTest :app:assembleRelease :app:bundleRelease --max-workers=2 --warning-mode=all --console=plain` | Passed: 17 JVM and 12 Android tests; release shrinking and unsigned APK/AAB. |
| `.\gradlew.bat :app:assembleDebug --offline --max-workers=2 --console=plain` | Passed; native task executed with Cargo offline. |
| `.\gradlew.bat :app:dependencies --configuration kotlinCompilerClasspath --console=plain` | Passed: compiler 2.4.21. |
| `.\gradlew.bat :app:dependencies --configuration debugRuntimeClasspath --offline --console=plain` | Passed: resolved runtime versions recorded. |
| `cargo generate-lockfile --manifest-path native/Cargo.toml` | Passed: one exact JNI ABI dependency. |
| `cargo check --manifest-path native/Cargo.toml --locked` | Passed. |
| `cargo fmt --manifest-path native/Cargo.toml --all -- --check` | Passed. |
| `cargo clippy --manifest-path native/Cargo.toml --locked --all-targets -- -D warnings` | Passed. |
| `cargo test --manifest-path native/Cargo.toml --locked` | Attempted, failed: missing default Windows MSVC link.exe. Not a successful host run. |
| From native/: `cargo ndk --platform 26 -t arm64-v8a test --locked --no-run` | Passed: actual Rust test executable built. |
| `adb -s <phone> push <test-binary> /data/local/tmp/deviceatlas-phase0-tests`; `adb -s <phone> shell chmod 700 /data/local/tmp/deviceatlas-phase0-tests`; `adb -s <phone> shell /data/local/tmp/deviceatlas-phase0-tests` | Passed: 10 Rust tests; temporary binary removed. |
| `llvm-readelf -l <release-so>` and `llvm-readelf -Ws <release-so>` for all ABIs | Mean export present; primary 64-bit LOAD segments aligned to 0x4000. |
| `zipalign -c -P 16 -v 4 app/build/outputs/apk/release/app-release-unsigned.apk` | Verification successful. |
| APK ZIP entry inspection | All four expected Rust libraries in debug and release. |
| Separate debug-signed release copy, adb install/start and uiautomator check | Passed: actual minified release UI displayed test mean 4.0. XML removed and debug APK restored. Production signing unchanged. |

Rust cases: positive/negative/mixed values, singleton, empty, NaN/±infinity,
maximum finite repetition, opposite extremes, cancellation precision, zeros and
subnormal values. Android tests also exercise null/output buffers, 1,000 repeated
calls, input preservation, actual missing-library behavior, Hilt/Compose content,
identity, permissions and disabled backup.

## 6. Native packaging and runtime matrix

Primary ABIs are arm64-v8a and x86_64. Additional baseline ABIs armeabi-v7a and
x86 are retained as build targets. All four compile and package in debug/release.

| Runtime | Verified evidence |
| --- | --- |
| arm64-v8a, Samsung SM-A325F, Android 13/API 33 | 12 Android tests, 10 standalone Rust tests and minified release mean 4.0 |
| x86_64 | Built and packaged; runtime unverified because emulator installation did not finish |
| armeabi-v7a, x86 | Built/packaged; runtime execution not claimed |
| API 26 or 16 KiB-page device | Runtime unavailable; static 64-bit ELF/APK alignment passed |

## 7. Privacy, security and Git hygiene

App source declares no permissions. Merged app contains only AndroidX's internal
signature-protected receiver permission, tested by an allowlist assertion.
No INTERNET/hardware permissions, identifiers, telemetry SDKs, network code or
background monitoring were added. No measurements are fabricated.

Backup is disabled and all supported domains are excluded in legacy rules and
Android 12+ cloud/D2D rules. [Android documents OEM differences](https://developer.android.com/identity/data/autobackup)
for device migration; this is conservative platform configuration, not a universal
OEM guarantee. Phase 0 stores no user data.

JNI review: thread-local valid JVM environment only; null/buffer checks; one
Rust-owned input copy; fallible allocation; immediate exception check/clear with
explicit status; no retained pointers/global references, pinned arrays or Android
framework operations. Vec memory is dropped on errors/unwinding; Rust unwinding
is caught at the boundary. No numeric sentinel communicates an error.

Every previously tracked IDE file was individually inspected. Local files remain:

| File under .idea/ | Review/decision; previous untracking preserved |
| --- | --- |
| .gitignore | Redundant with root IDE ignore |
| AndroidProjectSystem.xml | Regenerable IDE project-system selection |
| checkstyle-idea.xml | Plugin preferences/bundled Sun/Google checks, no custom project rules |
| compiler.xml | IDE target 25; explicit Gradle bytecode 17 is authoritative |
| dbnavigator.xml | Database IDE plugin settings unrelated to the app |
| deploymentTargetSelector.xml | Local deployment target state |
| git_toolbox_prj.xml | IDE plugin preference state |
| gradle.xml | IDE linking/migration metadata, derivable from Gradle |
| misc.xml | IDE SDK/project-type metadata |
| runConfigurations.xml | Ignored producer preferences, no named shared launch config |
| vcs.xml | Portable but auto-derivable Git root mapping |

Wrapper assets, Cargo.lock, source and required configuration remain. Local SDK/
signing files, build/native outputs, Rust targets, Graphify output and crash dumps
are ignored. No generated native binary is intended for Git.

Graphify 0.9.93 was installed already. `graphify codex install --project` succeeded;
the project skill/references, AGENTS.md and no-op-compatible hook were reviewed.
`graphify . --code-only --no-cluster` and `graphify update . --no-cluster` ran.
Graph relationships informed the direct capability boundary and did not replace
source review. Project-scoped tooling is kept locally and excluded separately
through `.git/info/exclude`; no temporary graph is intended for Git.

## 8. Environment failures and risks

- Default Windows host Rust tests require MSVC linker/Windows SDK. An additional
  GNU Rust target was evaluated without changing the default Rust version or
  toolchain. Stable LLD could run but MinGW import libraries were absent. No
  unstable flags or linker workaround was committed. Install normal Windows C++
  prerequisites and rerun the host command.
- The large Google APIs emulator image download failed in Android CLI's native
  downloader around 78%. No runtime test was claimed from that attempt.
- The legacy sdkmanager wrapper split a semicolon package name; replacement
  Android CLI accepted slash-separated names. A smaller official Android 35
  AOSP ATD image downloaded, but its emulator dependency download also failed.
  A final request explicitly naming installed emulator 37.2.12 still restarted
  that dependency download; the extra attempt was stopped. No AVD was created
  or emulator started, and no emulator test is claimed.
- Upstream Configuration.setVisible and JDK 25 protobuf Unsafe warnings remain;
  no warnings/errors were suppressed. Deprecated Compose test rules were moved
  to supported v2 APIs.
- API 26, 16 KiB runtime, exhaustive OEM migration, production signing and
  publication remain outside verified coverage. No hardware features were added.

## 9. Evidence

Ignored `build/phase0/` contains milestone/final logs, compiler/runtime classpaths,
Rust test output, native packaging/alignment metadata, release-runtime result
and IDE review metadata. Standard Gradle XML/HTML results remain under
`app/build/reports`, `test-results`, and `outputs/androidTest-results/connected`.
Do not commit raw device serials or environment logs.

## 10. Definition of Done

Implementation and arm64 validation are verified. **Phase 0 is not fully complete:
final verification is partial.** Default Windows host Rust tests need the C++
linker/SDK, and x86_64 runtime execution remains unverified. Packaged libraries
alone are not runtime proof. Pending work: install the normal Windows C++
prerequisites and an x86_64 Android runtime, then rerun host Rust and connected
JNI/Compose tests. No code workaround or skipped test conceals either limitation.

### Final addendum

- `.\gradlew.bat :app:lintRelease :app:build --max-workers=2 --console=plain`
  passed: 117 tasks, including both lint variants and full build/check.
- Final `cargo fmt` and Clippy commands above returned 0; default host `cargo test`
  returned 101 with missing `link.exe`. Android Rust execution passed all 10 cases.
- Final arm64 XML: 17 JVM tests and 12 Android tests, zero failures/errors/skips.
- The minified release APK displayed test mean 4.0; production release artifacts
  remain unsigned. Temporary UI XML was removed and debug APK restored.
- Backup configuration is compiled into the app and package-tested. Actual
  cloud backup/OEM migration transport behavior was not exercised.
- SDK installation attempts used the existing SDK root and did not alter project
  JDK, NDK or compiler pins. Download caches may contain incomplete installer data.

## Appendix: exact paths changed from the working baseline

The earlier uncommitted starter-to-foundation changes remain in the overall Git
status. This table isolates the additional Phase 0 delta against the saved source
snapshot, including removal of two previously untracked repository files.

| Change | Repository-relative path |
| --- | --- |
| Added this phase | app/src/androidTest/java/io/github/masoudd2159/deviceatlas/FoundationScreenTest.kt |
| Added this phase | app/src/main/java/io/github/masoudd2159/deviceatlas/data/nativecore/RustNativeMath.kt |
| Added this phase | app/src/main/java/io/github/masoudd2159/deviceatlas/foundation/NativeMath.kt |
| Added this phase | app/src/main/java/io/github/masoudd2159/deviceatlas/presentation/theme/Tokens.kt |
| Added this phase | app/src/test/java/io/github/masoudd2159/deviceatlas/data/nativecore/RustNativeMathTest.kt |
| Added this phase | docs/phase-0-report.md |
| Added this phase | native/src/bridge.rs |
| Modified this phase | .gitignore |
| Modified this phase | app/build.gradle.kts |
| Modified this phase | app/src/androidTest/java/io/github/masoudd2159/deviceatlas/ExampleInstrumentedTest.kt |
| Modified this phase | app/src/androidTest/java/io/github/masoudd2159/deviceatlas/MainActivityTest.kt |
| Modified this phase | app/src/androidTest/java/io/github/masoudd2159/deviceatlas/NativeCoreSmokeTest.kt |
| Modified this phase | app/src/main/AndroidManifest.xml |
| Modified this phase | app/src/main/java/io/github/masoudd2159/deviceatlas/data/nativecore/NativeBridge.kt |
| Modified this phase | app/src/main/java/io/github/masoudd2159/deviceatlas/di/NativeCoreModule.kt |
| Modified this phase | app/src/main/java/io/github/masoudd2159/deviceatlas/presentation/home/HomeScreen.kt |
| Modified this phase | app/src/main/java/io/github/masoudd2159/deviceatlas/presentation/home/HomeViewModel.kt |
| Modified this phase | app/src/main/java/io/github/masoudd2159/deviceatlas/presentation/theme/Theme.kt |
| Modified this phase | app/src/main/res/values/strings.xml |
| Modified this phase | app/src/main/res/xml/backup_rules.xml |
| Modified this phase | app/src/main/res/xml/data_extraction_rules.xml |
| Modified this phase | app/src/test/java/io/github/masoudd2159/deviceatlas/presentation/home/HomeViewModelTest.kt |
| Modified this phase | docs/validation.md |
| Modified this phase | gradle/libs.versions.toml |
| Modified this phase | native/Cargo.lock |
| Modified this phase | native/Cargo.toml |
| Modified this phase | native/src/lib.rs |
| Modified this phase | README.md |
| Removed from baseline | app/src/main/java/io/github/masoudd2159/deviceatlas/data/repository/RustNativeCoreRepository.kt |
| Removed from baseline | app/src/main/java/io/github/masoudd2159/deviceatlas/domain/repository/NativeCoreRepository.kt |
