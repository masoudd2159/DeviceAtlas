# Phase 0 final engineering verification — 2026-10-22

The initial verification record below describes commit `c4bc18b`. The **Closure
addendum** at the end records the later helper review and merge preparation.

The Android foundation passes the available build, lint, unit, JNI, Compose and
runtime checks with the deliberately selected **NDK 30.0.16248370** and **Build
Tools 37.0.0**. Neither version was substituted. Windows Rust host tests remain
blocked by missing Microsoft prerequisites, and actual 16 KiB-page execution is
unverified. Phase 0 therefore has documented environment coverage gaps; this is
not a claim of exhaustive completion or release readiness.

## Baseline and process

Work followed Inspect → Plan → Test Design → Fix → Review → Verify → Commit → Push.
The initial branch was `feature/phase-0-foundation`, HEAD
`259b07324c195b70dfbb40eab76dabf1bc5713bc`. Existing uncommitted Phase 0 source,
documentation, configuration and intended IDE untracking were preserved and
reviewed. The unrelated untracked `export-codex-changes.ps1` was left untouched
and excluded from the commit. Baseline status and a patch were retained locally
under ignored `build/final-phase0/`. Master was not modified.

The plan was to validate the selected tools first, test numerical/JNI boundaries,
correct proven defects, recover missing runtime coverage, and only then stage
reviewed Phase 0 files. No Phase 1 work or broad dependency upgrade was performed.

## Exact toolchain and compatibility

| Component | Actual selection / evidence |
| --- | --- |
| AGP / Gradle | 9.4.1 / 9.8.2; wrapper SHA-256 pinned |
| Kotlin / Compose Compiler | 2.4.21 / 2.4.21; AGP built-in Kotlin, no kotlin-android plugin or kapt |
| Compose BOM / Material 3 / UI | 2026.09.00 / 1.4.0 / 1.12.1 |
| Activity / Lifecycle | 1.13.0 / 2.11.0 |
| Hilt / KSP | 2.60.1 / 2.3.12; generated code and actual injection exercised |
| Coroutines | 1.11.0 |
| Gradle daemon JDK / bytecode | 25.0.2 / Java and Kotlin 17; Gradle embedded Kotlin 2.4.10 is separate |
| SDK / min SDK | compile and target 37 / minimum 26 |
| NDK | **30.0.16248370 (r30)**, installed revision confirmed from source.properties |
| Build Tools | **37.0.0**, installed revision confirmed; its zipalign used below |
| Rust / cargo-ndk | 1.99.1 / 4.1.2, pinned toolchain and checked build-helper version |
| JNI bindings | jni-sys exactly 0.3.0, Cargo.lock retained |

[AGP 9.4 documentation](https://developer.android.com/build/releases/agp-9-4-0-release-notes)
documents SDK 37 support, Gradle minimum 9.6 and JDK minimum 17. This project keeps
its newer existing wrapper/JDK and explicitly selected Build Tools. The
[built-in Kotlin guide](https://developer.android.com/build/migrate-to-built-in-kotlin)
and [Compose compiler setup](https://kotlinlang.org/docs/compose-compiler-migration-guide.html)
support retaining AGP Kotlin and matching the Compose compiler plugin to Kotlin.
The [Compose BOM](https://developer.android.com/develop/ui/compose/bom) manages app
and UI-test versions together. Existing dependency decisions and primary-source
links are preserved in [the development report](phase-0-report.md).

The [official r30 changelog](https://github.com/android/ndk/wiki/Changelog-r30)
was reviewed; all four Rust Android targets compiled with this exact NDK and API
26 through cargo-ndk. Successful builds, runtime tests, R8, and alignment checks
provide project-specific compatibility evidence; they do not imply every possible
device/tool combination is certified.

## Corrections and architecture review

The earlier normalized mean lost a representable small residual between extreme
values. Two regression tests were executed before the correction and failed on
Android: `[MAX, 1e-15, -MAX]` and `[MAX, MAX, -MAX, -MAX, 1e-15]` returned zero.
The implementation now prefers compensated summation in original units, uses
count-scaled summation on partial overflow, and retains a normalized last fallback.
Clamping to the finite input range handles rounding at MAX. Tests cover positive,
negative, mixed, singleton, empty, NaN/infinities, cancellation, subnormals, zero,
and repeated extreme values (counts 2–64). This remains rounded f64 arithmetic,
not arbitrary-precision arithmetic. A connected JNI regression verifies the fix
through the actual packaged library on both primary ABIs.

The reviewed foundation retains one Android module and one Rust crate:

- ComponentActivity/Compose entry point and minimal Material 3 screen; light/dark,
  Dynamic Color on API 31+, fallback colors, typography/shape/spacing tokens,
  resource strings, heading/live-region semantics, scrolling and edge-to-edge insets.
- Hilt application/activity/ViewModel and a used NativeMath capability binding.
  The ViewModel owns immutable StateFlow state; Compose collects with lifecycle.
  Cancellation propagates and clearing the ViewModel cancels its work.
- RustNativeMath validates inputs, loads explicitly off the UI thread, and maps
  status/load failures to typed results. No Repository or UseCase decoration.
- No monitoring, device extraction, navigation, database, networking or new module.

## Commands and actual results

Commands below ran on Windows with this pinned toolchain. Cargo commands used the
existing Cargo executable under `%USERPROFILE%/.cargo/bin`; no project workaround
for the absent Windows linker was introduced. Raw local outputs remain ignored
under `build/final-phase0/` and Android test/build report directories.

| Exact command | Status and evidence |
| --- | --- |
| `git status --short`, `git branch --show-current`, `git log -1 --oneline`, `git diff`, `git diff --cached` | PASS: inspected baseline and final intended changes; branch preserved |
| `.\gradlew.bat :app:assembleDebug :app:assembleRelease :app:bundleRelease --max-workers=2 --console=plain` | PASS, repeated after numerical correction; all four ABIs, debug APK, minified/shrunk unsigned release APK and AAB |
| `.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:lintRelease --max-workers=2 --console=plain` | PASS: 17 JVM tests, zero failures/errors/skips; both Lint reports say no issues found |
| `.\gradlew.bat :app:build --max-workers=2 --console=plain` | PASS, 113 tasks |
| `.\gradlew.bat :app:assembleDebug --offline --max-workers=2 --console=plain` | PASS twice; Cargo offline propagation and configuration-cache reuse confirmed |
| `.\gradlew.bat :app:dependencies --configuration debugRuntimeClasspath --offline --console=plain` | PASS, cached dependency resolution |
| `.\gradlew.bat :app:connectedDebugAndroidTest --max-workers=2 --console=plain` | Final run PASS: 13 tests on each of two runtimes, zero failures/errors/skips in both per-device XML reports |
| `cargo test --manifest-path native/Cargo.toml --locked` | BLOCKED (environment), executed and exited 101: linker `link.exe` not found |
| `cargo fmt --manifest-path native/Cargo.toml --all -- --check` | PASS |
| `cargo clippy --manifest-path native/Cargo.toml --locked --all-targets -- -D warnings` | PASS; type/lint checking does not establish host executable linkage |
| `cargo ndk --platform 26 -t arm64-v8a test --locked --no-run` (working directory `native`) | PASS; test executable then pushed and executed on ARM64: 12 passed |
| `cargo ndk --platform 26 -t x86_64 test --locked --no-run` (working directory `native`) | PASS; test executable then pushed and executed on API 26 x86_64: 12 passed |
| `zipalign.exe -c -P 16 -v 4 app/build/outputs/apk/debug/app-debug.apk` | PASS using Build Tools 37.0.0 |
| `zipalign.exe -c -P 16 -v 4 app/build/outputs/apk/release/app-release-unsigned.apk` | PASS using Build Tools 37.0.0 |
| `graphify update . --no-cluster` | PASS; graph remained local/ignored; extraction limitations do not replace source inspection |
| `git diff --check`, `git diff --cached --check` | PASS at final quality gate |

The first connected invocation printed BUILD SUCCESSFUL/exit 0 but reported an
emulator installer failure (INSTALL_FAILED_ALREADY_EXISTS following manual startup
validation). It is **not counted as a successful test run**. After runner cleanup,
the repeated command produced complete passing per-runtime XML reports. No tests
were weakened or removed to obtain that result. Upstream Java Unsafe/protobuf
warnings remain visible rather than being suppressed.

## Runtime and packaging evidence

| Runtime | ABI / pages | Actual execution |
| --- | --- | --- |
| Samsung SM-A325F, Android 13, API 33 | arm64-v8a / 4 KiB | 13 connected tests plus 12 standalone Rust tests PASS |
| AOSP default Android 8.0 image, API 26, revision 1; emulator 37.2.12 | x86_64 / 4 KiB | Startup PASS; 13 connected tests plus 12 standalone Rust tests PASS |
| 16 KiB-page Android kernel | unavailable | NOT RUN; no suitable installed runtime |
| armeabi-v7a / x86 | packaged, not separately executed | Runtime coverage NOT RUN; additional ABIs retained |

The official API 26 x86_64 image was installed in one bounded attempt using the
official command-line tools 19.0 downloader after the earlier downloader failures.
The tools archive was checked against the published repository SHA-1. The existing
emulator and official avdmanager created `DeviceAtlas_Phase0_API26_x86_64`; no
compiler, project SDK, NDK or Build Tools version was altered. Only this test AVD
was started. The reusable AVD/image remain installed locally.

Connected tests cover Compose content, actual Hilt → ViewModel → JNI success,
valid/invalid native arrays including null/empty/non-finite values, repeated calls,
unmodified input, actual missing-library behavior, identity, permissions and backup
policy. Post-test filtered Logcat showed no AndroidRuntime/native fatal crash
entries in the captured window. This is bounded evidence, not proof of no possible
resource or concurrency defect.

Both APKs contain `libdeviceatlas_core.so` for arm64-v8a, armeabi-v7a, x86_64 and
x86. NDK r30 llvm-readelf confirmed the expected JNI export on all four libraries
and LOAD segment alignment of at least 16 KiB for the two 64-bit libraries. The
32-bit libraries retain their 4 KiB alignment. ZIP alignment passes above are
static checks; neither substitutes for a 16 KiB runtime. Release R8 outputs and
JNI keep rules were inspected; connected instrumentation used the debug app.
Unsigned release artifacts were not published or represented as release-runtime tests.

## Windows host blocker

The active Rust host is x86_64-pc-windows-msvc, stable 1.99.1. Visual Studio C++
Build Tools, the MSVC linker and Windows SDK were not found. The Cargo diagnostic
states `error: linker link.exe not found` and requests Visual C++ prerequisites.
The current process is not elevated; installing those system prerequisites needs
an administrator context, so no unattended system installation/UAC or alternate
linker hack was attempted. Install the official
[Microsoft C++ Build Tools](https://learn.microsoft.com/en-us/windows/dev-environment/rust/setup)
with Desktop development with C++ and a Windows SDK, then rerun the exact host
test command above. Android Rust test success is recorded separately and does
not resolve this Windows host coverage gap.

## JNI safety, security and Git hygiene

JNI uses the correct system ABI and typed function-table bindings, with no retained
JNIEnv, array pointer, global reference or mutable native global. Null handles and
output length are checked; array regions copy once into a fallibly allocated,
initialized Rust Vec and copy one result only on success. No array is pinned.
JNI exceptions are checked immediately and mapped to explicit statuses. Rust
unwinding is contained at the exported boundary; owned memory drops on return or
unwind. Fatal process aborts/system OOM are not claimed to be recoverable. Callers
must obey the JVM handle/thread contract and must not mutate shared input during
the synchronous call. Numerical values are never used as error sentinels.

The source manifest requests no permissions; AndroidX contributes its internal
signature-protected receiver permission only. No INTERNET/hardware permissions,
telemetry SDKs, identifiers, services, network clients or data collection exist.
Backup is disabled with explicit exclusions in legacy/cloud/device-transfer rules;
OEM transport behavior remains outside universal guarantees. Phase 0 persists no
user data. No sensitive logs, local paths, device serials, credentials, signing
materials, generated binaries or graphs are included in the intended commit.

Each tracked IDE file was inspected. Plugin database/checkstyle/GitToolBox settings,
deployment selections, IDE compiler/JBR choices, generated project/Gradle/VCS and
run-producer state are unnecessary build inputs. The eleven `.idea` files are
untracked while their local copies remain. `.gitignore` covers IDE/local SDK,
Gradle/native/Rust builds, signing materials, captures/crash dumps and Graphify
output. Gradle Wrapper assets, daemon criteria and Cargo.lock remain maintained
inputs. Local Graphify skill/hooks are retained outside Git; local project
instructions now explicitly prohibit committing generated graphs unless designated
as maintained artifacts by the user.

## Changed-file inventory and milestone assessment

The commit includes the reviewed pre-existing foundation plus this final correction:

- Added `.gitattributes`, `rust-toolchain.toml`, `native/Cargo.toml`,
  `native/Cargo.lock`, `native/src/lib.rs`, `native/src/bridge.rs`,
  `app/proguard-rules.pro` and three documents under `docs/`.
- Added application sources: DeviceAtlasApplication, MainActivity, NativeBridge,
  RustNativeMath, NativeCoreModule, NativeMath, HomeViewModel, HomeScreen, Theme
  and Tokens under the existing application package.
- Added FoundationScreenTest, MainActivityTest, NativeCoreSmokeTest,
  RustNativeMathTest and HomeViewModelTest; replaced the sample instrumented test
  content with identity/privacy assertions.
- Updated README, .gitignore, root/app Gradle scripts, version catalog, wrapper
  checksum, manifest, string/theme and backup resources.
- Removed the placeholder ExampleUnitTest, legacy colors and unused sample
  keepRules/rules.keep; stopped tracking all eleven `.idea` files.
- Preserved identity, minimum SDK, four ABIs, wrapper assets, user-selected NDK/
  Build Tools, unrelated script, and local IDE copies.

P0-01 toolchain/build, P0-02 Compose, P0-03 Hilt/ViewModel, P0-04 JNI and P0-06
privacy/hygiene pass the available checks. P0-05/P0-07 retain the Windows host and
16 KiB runtime limitations above. Both primary ABIs and minimum API now have real
runtime coverage. No known source/build/test failure remains in runnable checks.
The user explicitly permits finalization with documented environment-only
blockers. Git finalization targets only `feature/phase-0-foundation` with a normal
non-force push; commit/push outcome is reported separately with the actual SHA
and remote verification. No PR, merge, master push or release is authorized here.

## Closure addendum — final review and merge preparation

The user subsequently authorized committing the intentional export helper and a
normal merge/push to master after quality checks. This supersedes the earlier
Git-operation restriction above. Inspection found feature and origin/feature at
`c4bc18b2fc3c0ad26d492b771936e0045c60774c`, local/origin master at `259b073`,
and only the intentional export helper untracked. Fetch revealed no unexpected
master changes. All source and selected configuration were reviewed again;
NDK 30.0.16248370, Build Tools 37.0.0, identity, SDK levels, dependencies and four
ABIs remain unchanged. No new application/native defect or Phase 1 work was added.

The original helper copied unrestricted file content and binary diffs, included
the absolute project path in metadata and reused timestamp output with Force.
The reviewed helper now uses UTF-8/NUL-safe Git process capture and literal
pathspecs, unique output names without overwriting, bounded text input, filtered
patches/metadata, and base/current privacy checks. Source files live under files/
so a source README.txt cannot collide with bundle metadata. Staged/unstaged
content, untracked files, deletions, ZIP output and running-helper exclusion are
preserved. It does not mutate Git state, upload or run remote Git operations.
README documents actual parameters, output, conservative exclusions and manual
review limitations. Review ZIPs/folders are ignored without ignoring the helper.

`tools/Test-ExportCodexChanges.ps1` creates a unique disposable repository outside
this checkout. **71 assertions pass on Windows PowerShell 5.1.28000.3262 and
PowerShell 7.6.5**. Coverage includes literal brackets/spaces/Unicode, current
staged-plus-unstaged content, committed-base selection, empty files, renames,
deletions, untracked/ignored files, force-added generated/configuration files,
binary/invalid UTF-8, per-file/total budgets, historical/current secrets,
credential stores, private paths/device patterns, sensitive filenames, unique
ZIPs, invalid bases, rejected repository output paths, unchanged HEAD/status and
byte-for-byte unchanged index. Regression development exposed and corrected a
multiple-Git-executable discovery issue before committing. No third-party test
dependency was added. Only generated fixture directories are cleaned up.

Closure verification on the feature branch repeated every Android command in
the command table above: full build; debug/release APK and release bundle;
17 JVM tests; both Lint variants; and offline debug build. **All PASS** with
configuration-cache reuse. Connected tests were explicitly selected using the
process-only ANDROID_SERIAL environment variable, restored afterward: **13 PASS
on ARM64/API 33 and 13 PASS on x86_64/API 26**, each with separate zero-failure,
zero-error, zero-skip XML evidence. No serial is recorded in committed reports.
Both standalone Android Rust executables were rebuilt/selected and run again:
**12 PASS on each runtime**. Rust fmt and Clippy PASS. The exact host cargo test
command was retried and remains **BLOCKED**, exit 101, missing MSVC link.exe.
No C++ Build Tools/Windows SDK installation was found and the process is not
elevated. Install Microsoft Desktop development with C++ plus Windows SDK, then
rerun the documented host command; no linker workaround was introduced.

The available phone reports `getconf PAGE_SIZE` = 4096. The official API 26
emulator lacks getconf; /proc/self/smaps reports KernelPageSize/MMUPageSize 4 kB.
Only that official x86_64 image is installed. No readily available 16 KiB runtime
exists and no additional large downloads were attempted. Actual 16 KiB execution
remains **NOT RUN**, never inferred from alignment. Prior four-ABI packaging and
static alignment checks were repeated: both APK ZIP checks PASS with Build Tools
37.0.0; all four JNI exports are present and both 64-bit ELF LOAD alignments are
16 KiB. Post-test filtered Logcat contains no AndroidRuntime/native fatal entries
in the captured window. Application/native inputs remain unchanged.

Local raw closure evidence is ignored under build/phase0-closure. The previous
reports retain their historical results. The foundation meets the user's merge
gate with documented environment-only limitations, rather than exhaustive
runtime coverage: Windows host tests, 16 KiB runtime, separate 32-bit execution,
OEM backup transport and production signing remain unverified. The reviewed
single-module foundation is ready for Phase 1 planning; no Phase 1 implementation
is included. The normal merge must retain the feature branch, preserve source
trees, repeat checks on merged master and verify remote SHAs; the actual commit,
merge and push outcomes are reported after those operations execute.
