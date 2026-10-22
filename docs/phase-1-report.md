# DeviceAtlas Phase 1 verification — 2026-10-22

Sections A–M below preserve the implementation record through `c6563d4`.
The [review closure](#phase-1-review-closure) is the current correction/quality-gate
record; historical counts and limitations below are not substituted for fresh tests.

## A. Executive summary

Phase 1 implementation is complete. The application has a Material 3 adaptive
shell, typed saved navigation, English/Persian app locales, persistent themes,
Settings subsections and honest structural feature pages. The real Rust/JNI
verification remains in Diagnostics. Hardware collection and Phase 2 are absent.
Available critical Android tests pass on both runtimes. Environment and manual
validation gaps are explicitly listed below; they are not represented as passes.

## B. Git state

Initial working tree was clean on `master` at accepted Phase 0 baseline
`c7e9167dd7155a23b21e45af758e571cb73102f6`. Status, branch tracking, fetch, recent
history, ancestry and whitespace were checked before creating
`feature/phase-1-architecture-designsystem`.

Functional local commits:

- `b29e868`: preference contracts, platform adapter, state ownership and tests.
- `52a334c`: localized Material 3 tokens, shared components and resource checks.
- `68b4d9b`: official locales, typed navigation, adaptive shell and settings.
- `30a7f3c`: navigation/restoration, locale, theme, rotation and adaptive tests.

Documentation is committed separately; use `git log master..HEAD --oneline` for
the final documentation commit. No push, PR, merge, release publication, branch
deletion or baseline change was performed. Generated evidence stays ignored.

## C. Graphify verification

Graphify **0.9.93** was executable in this Codex environment. The project skill
was discoverable in the supplied skills catalog and its local `SKILL.md` was
read/applied. `AGENTS.md`, the local hook registration and existing graph were
checked. The compatibility `hook-check` exits 0 silently; automatic hook execution
was not treated as proof of graph freshness. All analysis commands ran explicitly.

Executed `graphify --version`, `graphify --help`, meaningful baseline `query`,
`graphify extract . --code-only --no-cluster`, milestone/final
`graphify update . --no-cluster`, `path`, `query` and `explain AppNavigationState`.
The final code graph has **542 nodes / 1,029 edges**. Query/explain locates the
actual shell, navigation, settings, gateway and tests, and was checked against
their source. Kotlin/Rust symbols and supported Gradle references are present.

Limitations: XML/resources and some Gradle/Cargo declarative files are not modeled
as symbols. Shared-import paths are not runtime dependency proofs. The diagnostic
reports **0 missing/dangling endpoints, 1 self-loop and 15 possible undirected
same-endpoint collapses** (7 directed); do not interpret the map as a complete
call graph. AST-only updates used **0 LLM/API tokens**; clustering/cohesion scoring
was deliberately not run. Graphs, skill/hook files, interpreter markers and
temporary metadata remain local and uncommitted.

## D. Architecture

One Android module (`:app`) and the intact Rust crate (`native`) remain.
`foundation` defines typed capabilities/preferences; `data` implements Android
adapters; `di` binds real implementations and qualified dispatchers;
`presentation` owns UI state, navigation and rendering. See
[the actual package tree and dependency diagram](architecture/overview.md),
[Rust boundary conventions](architecture/rust-first-boundaries.md) and
[ADRs](architecture/decisions.md).

Direction: Compose → entry/Activity ViewModel → injected capability → off-main
Kotlin gateway → checked JNI → pure Rust. Preference IO uses injected IO; native
computation uses Default. Cancellation propagates. The diagnostics ViewModel is
entry-scoped; application appearance is Activity-scoped. No business arithmetic
was duplicated in Kotlin, no native pointers retained, and no speculative modules
or generic repository/use-case framework was added.

## E. Design system

Existing theme/tokens now include coherent blue light/dark fallback containers,
semantic status pairs, Material typography/shapes, spacing, touch targets,
icon sizing, elevation and readable maximum content width. API 31+ uses Dynamic
Color. Full-row actions/radio choices, cards, information/technical rows,
availability/error states, headings, top bar and responsive content are reused.
Previews cover both languages, phone/tablet and large fonts. Normal fallback
content-pair contrast is tested at ≥4.5:1.

Visual screenshots were inspected for English, Persian, dark theme and a resized
large window. RTL selection and mirrored arrows are correct. A review caught and
fixed inherited purple containers in the blue fallback palette. No measured FPS,
startup benchmark or manual screen-reader result is claimed. See
[design-system conventions](architecture/design-system.md).

## F. Localization

All **50** application strings have English and Persian resources, checked for
key/placeholder parity, nonempty translations, duplicates and Persian character
forms. AppCompatActivity plus AppCompat auto-storage handles API 26–32;
framework per-app locales handle API 33+, advertised by `locales_config.xml`.
There is no separate language preference or conflicting locale wrapper.

Live English/Persian switching, RTL ordering, activity recreation, Follow system,
regional Persian resolution and unsupported-language English fallback pass.
API 33 tests also verify the framework LocaleManager value. Actual process
restart checks verify locale/theme durability separately from saved-state tests.
Only technical values use targeted LTR text. See
[locale ownership and adding a language](architecture/localization.md).

## G. Navigation and shell

Navigation 3 **1.2.0**, AppCompat **1.8.0** and AndroidX Hilt Compose **1.4.0** were
verified as stable in official Android documentation and resolved from existing
repositories. Lifecycle remains **2.11.0**; Dagger Hilt remains **2.60.1**;
serialization plugin matches Kotlin **2.4.21**. Existing toolchain pins remain.

Overview, Device, Sensors, Diagnostics and Settings each retain a saved official
NavBackStack. Language, Appearance and About are real Settings children. Repeated
taps are idempotent; Back pops a child, returns through Settings/Overview, then
exits. Saveable-entry and ViewModel decorators retain inactive-tab state.
NavDisplay owns system/predictive Back. The official Material navigation suite
adapts to current window size/posture with one navigation control. Content scrolls
and stays bounded. All planned features state their unavailability, and the
known-input Rust result explicitly states that it is not hardware data.

See [navigation behavior and extension points](architecture/navigation.md).

## H. Tests and evidence

Commands ran from the repository root unless a different working directory is
stated. Local logs under `build/` and generated reports are ignored, not committed.

| Exact command/check | Result |
| --- | --- |
| `.\gradlew.bat :app:assembleDebug :app:assembleRelease :app:bundleRelease --max-workers=2 --console=plain` | PASS; all four native ABIs, KSP/Hilt, R8/resource shrinking, unsigned APK/AAB |
| `.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:lintRelease --max-workers=2 --console=plain` | PASS; 28 JVM tests, 0 failures; lint 0 errors / 2 advisory warnings each |
| `.\gradlew.bat :app:build --max-workers=2 --console=plain` | PASS; final repeat reuses configuration cache |
| `.\gradlew.bat :app:assembleDebug --offline --max-workers=2 --console=plain` | PASS twice after caching; repeat 43 tasks up-to-date and configuration cache reused |
| `.\gradlew.bat :app:connectedDebugAndroidTest --max-workers=2 --console=plain` | PASS; final per-device XML confirms 22 tests, 0 failures/errors on EACH runtime |
| `.\gradlew.bat :app:assembleDebug :app:assembleRelease :app:bundleRelease :app:testDebugUnitTest :app:lintDebug :app:lintRelease --max-workers=2 --console=plain` | PASS after final source review |
| `.\gradlew.bat :app:dependencies --configuration debugRuntimeClasspath --max-workers=2 --console=plain` | PASS; new stable artifacts resolve; pinned Kotlin/Compose/Lifecycle/Coroutines retained |
| `cargo fmt --manifest-path native/Cargo.toml --all -- --check` | PASS |
| `cargo clippy --manifest-path native/Cargo.toml --locked --all-targets -- -D warnings` | PASS |
| `cargo test --manifest-path native/Cargo.toml --locked` | BLOCKED; attempted, exit 101, Windows MSVC `link.exe` absent |
| `cargo ndk --platform 26 -t arm64-v8a test --locked --no-run` from `native/` | PASS; executable then run on API 33 ARM64: 12 passed |
| `cargo ndk --platform 26 -t x86_64 test --locked --no-run` from `native/` | PASS; executable then run on API 26 x86_64: 12 passed |
| Build Tools `zipalign -c -P 16 -v 4 app/build/outputs/apk/release/app-release-unsigned.apk` | PASS; verification successful |
| NDK `llvm-readelf --dyn-syms` / `-lW` on generated release libraries | PASS; JNI export on all four ABIs; 64-bit LOAD alignment `0x4000` |
| APK/AAB ZIP content inspection | PASS; arm64-v8a, armeabi-v7a, x86_64, x86 libraries |
| Temporary local debug-signed copy of minified release | PASS on EACH runtime; startup, navigation, real Hilt/ViewModel/JNI mean 4.0; debug APK restored |
| `powershell -NoProfile -ExecutionPolicy Bypass -File tools/Test-ExportCodexChanges.ps1` | PASS; 71 assertions on Windows PowerShell 5.1 |
| `git diff --check` / staged review | PASS |

Runtime evidence:

| Runtime | Connected tests | Standalone Rust | Additional checks |
| --- | --- | --- | --- |
| API 26 x86_64 emulator | 22 passed | 12 passed | Live locales, 200% Persian fonts, bar/rail, rotation/restoration, actual force-stop/restart locale/theme, minified release |
| API 33 ARM64 Samsung SM-A325F | 22 passed | 12 passed | Live locales and framework LocaleManager, Dynamic Color, rotation/restoration, actual force-stop/restart locale/theme, minified release |

Connected coverage preserves all Phase 0 checks and adds structural navigation,
Settings, radio/heading semantics, themes, RTL navigation order, language changes,
regional/fallback resources, rotation, saved nested-stack restoration and
compact/expanded windows. JVM coverage preserves native error/cancellation tests
and adds preference success/failure/reload/cancellation, theme resolution,
navigation selection/Back, localization completeness and contrast.

One intermediate connected invocation had an emulator installation conflict after
a separately installed manual-test APK. Gradle misleadingly returned success;
that invocation was not counted as an emulator pass. Subsequent clean runs and
per-device XML confirmed all 22 tests on both devices. Initial test compilation
also required selecting the JUnit4 StateRestorationTester API compatible with the
existing v2 rule. These were resolved without dependency changes or weakened tests.

Relevant final local evidence: `build/phase-1-review-verification.log`,
`build/phase-1-connected-final.log`, `build/phase-1-rust-*-api*.log`,
`build/phase-1-process-restart.log`, `build/phase-1-phone-process-restart.log`, `build/phase-1-release-smoke.log`,
`build/phase-1-offline-cache.log`, `app/build/test-results/testDebugUnitTest/`,
`app/build/outputs/androidTest-results/connected/` and lint reports. Screenshots
remain under ignored `build/phase-1-*.png`.

## I. Privacy, security and performance review

The merged release manifest requests only AndroidX's existing internal signature
receiver permission. No INTERNET, hardware, identifier, telemetry or background
service permission was added. The only new service is disabled/non-exported
AppCompat locale metadata. The existing exported profile-installer receiver is
protected by `android.permission.DUMP`; the launcher remains exported as before.
Backup stays false and all existing cloud/device-transfer domain exclusions remain.

Dependencies contain no account, network, advertising or analytics SDK. Native
source, JNI keep rules, Rust lockfile, ABI configuration and export helper/tests
are unchanged. Only non-sensitive locale/theme preferences persist. No credentials,
machine paths, generated libraries, graph data or signing material are committed.

Static review confirms: no JNI in recomposition, no eager native startup work,
qualified off-main dispatchers, lifecycle collection, bounded small native arrays,
immutable public UI state and cancellation propagation. There are no new sensor
listeners, hardware acquisition, monitoring jobs or UI calculation engine.

## J. Changed-file inventory

Paths below are repository-relative. The Kotlin package prefix is
`app/src/main/java/io/github/masoudd2159/deviceatlas/` and test prefix uses the
corresponding `src/test/java` or `src/androidTest/java` package directory.

- Build: `build.gradle.kts`, `gradle/libs.versions.toml`, `app/build.gradle.kts`.
- Application: `app/src/main/AndroidManifest.xml`, Kotlin `MainActivity.kt`.
- Foundation/data/DI: Kotlin `foundation/AppPreferences.kt`,
  `data/preferences/AndroidAppPreferences.kt`, `di/PreferencesModule.kt`.
- Presentation: Kotlin `presentation/components/AtlasComponents.kt`,
  `presentation/home/HomeScreen.kt`, `presentation/navigation/AppNavigation.kt`,
  `presentation/settings/SettingsViewModel.kt`, `presentation/shell/AtlasScreens.kt`,
  `presentation/shell/DeviceAtlasApp.kt`, `presentation/theme/Tokens.kt`.
- Resources: `app/src/main/res/values/strings.xml`, `values-fa/strings.xml`,
  `values/themes.xml`, `values-night/themes.xml`, `xml/locales_config.xml`, and
  `drawable/ic_back.xml`, `ic_forward.xml`, `ic_overview.xml`, `ic_device.xml`,
  `ic_sensors.xml`, `ic_diagnostics.xml`, `ic_settings.xml` under the same res root.
- JVM tests: `LocalizationTest.kt`, `presentation/navigation/AppNavigationTest.kt`,
  `presentation/settings/SettingsViewModelTest.kt`, `presentation/theme/ThemeContrastTest.kt`.
- Android tests: `AdaptiveShellTest.kt`, `PhaseOneAppTest.kt`, `MainActivityTest.kt`.
- Documentation: `README.md`, `docs/validation.md`, this report, and
  `docs/architecture/implementation-plan.md`, `overview.md`, `rust-first-boundaries.md`,
  `navigation.md`, `design-system.md`, `localization.md`, `decisions.md`.

## K. Remaining limitations

- BLOCKED by environment: default Windows Rust host test linking; MSVC linker is
  missing. Android Rust execution passed independently on both architectures.
- NOT RUN: a real 16 KiB-page runtime, physical foldable/tablet posture testing,
  manual TalkBack spoken traversal, hardware-keyboard traversal and a dedicated
  predictive-back gesture/cancellation test. API 33 programmatic Back and official
  NavDisplay behavior are exercised; that is not a gesture test.
- NOT RUN: instrumented full OS-kill/process-resume scenario with task restoration.
  Saved-state serialization, Activity recreation/rotation and actual preference
  process restart are separate verified checks and are not conflated.
- Two noncritical lint advisories are retained without suppression: API 33-only
  `localeConfig` (older APIs use AppCompat) and `UseKtx` on SharedPreferences edit
  (the Boolean commit result is required; KTX edit returns Unit).
- Existing Gradle plugin deprecation and protobuf `sun.misc.Unsafe` warnings under
  JDK 25 remain. The local signing tool also emits a JDK native-access advisory.
- No performance benchmark or certification-level accessibility claim is made.
- Graphify's structural-model limitations are described in C.

No known critical source/build defect remains after the completed checks.

## L. Definition of done

| Milestone | Status | Evidence |
| --- | --- | --- |
| P1-01 Architecture/Rust boundaries | PASS | Contracts, DI ownership, ADRs, existing Rust/JNI and cancellation regressions |
| P1-02 Design system | PASS | Reused tokens/components, themes, contrast, previews and visual review |
| P1-03 English/Persian | PASS | 50/50 resource coverage, API 26/33 locale switching/RTL and persistence checks |
| P1-04 Navigation | PASS | Typed saved per-tab stacks, nested Back, repeat taps, rotation/restoration tests |
| P1-05 Shell | PASS | Five real structural destinations and functional Settings; no fabricated telemetry |
| P1-06 Adaptation/accessibility | PASS | Official bar/rail, bounded scrollable content, Persian 200% font and semantics; manual gaps in K |
| P1-07 Review/tests/docs | PASS | Available Android/Rust checks, security/performance review, documentation and local commits; host-linker gap in K |

## M. Review and export

Review `git diff master...HEAD`, this report and the architecture docs. Export the
complete changes relative to the unchanged baseline using:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\export-codex-changes.ps1 -Base master
```

The helper writes a review directory and ZIP outside the repository. Its
sensitive-content filtering is heuristic: inspect files, patch and ZIP before
sharing. In this run it accepted 41 of 44 changed files: its private-path heuristic
also matches the legitimate `presentation/home/` package, omitting `HomeScreen.kt`
by path and `README.md`/this report by content. The exporter and all 71 regression
assertions were preserved. A separately reviewed complete 44-file ZIP and full
Git patch were generated outside the repository for complete review; the original
filtered ZIP remains separate. Nothing is uploaded automatically. Push/PR/merge requires separate user
authorization; Phase 2 has not started.

## Phase 1 review closure

This section records the fresh review corrections and verification on 2026-10-22.
It supersedes the historical exporter workaround and relevant test counts above;
the original implementation and Phase 0 records remain intact.

### Initial state and confirmed findings

The active branch was `feature/phase-1-architecture-designsystem`, clean at
`c6563d42bf47871210ea02bd39644eb4a04d2a02`. Its cached origin tracking ref matched
that commit. `master` remained at `c7e9167dd7155a23b21e45af758e571cb73102f6`.
`git fetch origin` was attempted and failed with `Could not resolve host: github.com`
(exit 128); current remote freshness cannot be asserted. No unrelated changes
were present or discarded. No remote write, history rewrite or master change was made.

| Finding | Evidence, impact and correction |
| --- | --- |
| High: exporter private-path false positives | An unbounded Unix-root expression matched legitimate `presentation/home/` components, omitting HomeScreen and documentation. Relative names now have their own validation; actual private absolute roots are bounded in content, historical versions, patches and metadata. All 44 original Phase 1 files now export. |
| Medium: appearance retry reloaded instead of saving | A failing persistence regression showed retry left the intended theme unsaved. `SettingsViewModel` now retains the latest failed theme under the existing mutex and retries persistence. Serialized writes/reloads, startup failures and cancellation have behavioral coverage. |
| Medium: missing CI foundation | No workflow existed. A small workflow now defines Rust, Android/JNI and two PowerShell-edition checks, with explicit prerequisites and least privilege. Hosted execution remains unverified. |
| Medium: exporter prose mistaken for device ID | The actual export rejected README's phrase “structural device” because PowerShell `-match` ignored case in an uppercase serial expression. Case-sensitive uppercase IDs, digit-bearing IDs and adb table rows are checked separately; safe prose and three identifier examples have regressions. |
| Test reliability: stored locale affected English selector | The expanded API 33 suite exposed an English-only assertion under stored Persian locale. The saved-state test now explicitly fixes its own locale; production locale behavior is unchanged. The complete suite then passed. |

No measured or well-founded defect justified replacing `MainActivity`'s
`SideEffect`. It publishes resolved system-bar appearance after composition and
uses current theme values; child navigation state does not inherently recompose
the root. It was retained and bar-icon behavior gained targeted tests. No
recomposition/performance improvement is claimed.

### Exporter correction and complete inventory

`export-codex-changes.ps1` separates excluded names, repository-relative names,
sensitive values and private absolute paths. Unix private user roots require a
token boundary and user component; conservative Windows private-root matching is
retained, including embedded diagnostic text. No extension is whitelisted.
Both current and base blobs, the emitted patch and Git metadata are checked.
Historical symlink blobs are rejected even if their working copy is now regular;
current links/junctions, unsafe output ancestry, traversal, binary/non-UTF-8 input,
size limits, signing/local configuration and removed secrets remain protected.
Git lazy fetch is disabled, and untracked accepted files have explicit metadata.

The original 71 assertions are retained and expanded to **246 passing assertions
on Windows PowerShell 5.1.28000.3262 and PowerShell 7.6.5**. Fixtures verify all four
requested safe paths, copies and patch completeness, metadata, private-path
variants, secrets, historical removals, size/encoding limits, deleted/staged/
unstaged/untracked input, Unicode/spaces/brackets, index/worktree/HEAD preservation,
unique outputs and no network commands. Junction and Git historical-symlink tests
ran; native filesystem symlink creation was unavailable under host permissions
and is explicitly skipped by the harness.

The actual branch was exported against `master` after corrections. **46 of 48
final changed files** are accepted; the `files/` inventory, `git apply --numstat`
patch inventory and changed-file metadata agree. **All 44 original Phase 1 files
are represented.** Exactly two additional tool files are intentionally omitted:

- `export-codex-changes.ps1`: the running exporter excludes itself by existing policy.
- `tools/Test-ExportCodexChanges.ps1`: secret-shaped negative-test fixtures occur
  in selected historical/current content. Filtering them is deliberate; no test
  or source-file bypass was introduced to increase the count.

The final export is a filtered review aid, not a complete source backup or an
exhaustive secret scanner. Review the two tools in Git and inspect the generated
bundle before sharing it. Outputs remain outside the repository; nothing is uploaded.

### CI configuration and verification boundary

`.github/workflows/verify.yml` runs on pushes to `master`, `feature/**`, `codex/**`,
pull requests targeting `master`, and manual dispatch. Linux Rust checks install
Rust 1.99.1/rustfmt/Clippy and execute format, Clippy and host tests. Linux Android
checks configure Temurin JDK 25, Gradle Wrapper validation and safe Gradle caching;
install Platform 37, Build Tools 37.0.0, NDK 30.0.16248370, Rust 1.99.1, all four
Android Rust targets and locked cargo-ndk 4.1.2; and build/test/lint debug, minified
release and release AAB. Windows Server 2022 runs the exporter harness separately
under Windows PowerShell and pwsh. Timeouts are 20/45/10 minutes with cancellable
per-ref concurrency. Downloads remain bounded by pinned prerequisites/job timeouts.

The sole token permission is `contents: read`. Checkout disables persisted
credentials. There is no privileged PR trigger, production signing, deployment,
release, dependency-graph submission or artifact upload. PR caches are read-only.
Checkout records the v4.2.2 immutable revision. The two official setup Actions
use v5 major refs: live documentation/ref verification and resolution to reviewed
immutable SHAs were **BLOCKED by DNS/network failures**. These refs are not claimed
as reviewed immutable revisions; resolve and review them before merge.

PyYAML BaseLoader parsing plus structural assertions validated triggers,
permissions, prerequisites, matrix, timeouts and allowed Actions. Git Bash `-n`
accepted all five Linux run scripts. Equivalent available Android/Rust/exporter
commands were executed locally; these do not execute hosted Action setup or prove
the fresh Linux SDK/download/bootstrap path.

**CI configured locally; GitHub-hosted execution NOT RUN until authorized push.**

### Architecture, UI and restoration review

Kotlin retains Android APIs, lifecycle, UI state, preferences and orchestration;
Rust retains the unchanged deterministic computation core behind the existing JNI
adapter/repository/use-case boundary. No Phase 2 hardware collection, networking,
database, module or dependency expansion was needed. The single preference mutex
also owns pending retry intent; successful saves publish theme changes and
cancellation propagates. AppCompat/framework still own per-app locales; there is
no second locale store. The existing Boolean commit result remains significant.

Graphify 0.9.93 and its project skill/hooks/local excludes were verified. Final
`graphify update .` rebuilt the AST analysis to 592 nodes, 1122 edges and 25
communities. Focused queries and qualified paths confirm SettingsViewModel's
AppPreferences import, RustNativeMath implementing NativeMath, and the shell's
AppNavigationState ownership. `graphify diagnose multigraph` found no dangling
endpoints or exact duplicate edges. This is structural evidence, not proof of
runtime injection or JNI correctness; unsupported XML/build-file symbols and
inferred edges remain extractor limitations. Graph output and local integration
files are excluded from commits.

Typed serializable Navigation 3 keys and official entry decorators continue to
own independent tab stacks, saveable state and entry ViewModels. New tests prove
an inactive entry retains its ViewModel, popping clears it, and re-entry creates
a new one. No competing Back engine was added. Synthetic AndroidX dispatcher
start/progress/cancel/complete events exercise actual NavDisplay handling: cancel
retains a nested Settings page; completion pops it. This is automated protocol
coverage, **not a real edge gesture**. Saved-state recreation, tab preservation,
repeated actions and Activity recreation remain covered.

`tools/Test-TaskRestoration.ps1` adds a separate deterministic check restricted to
an explicitly selected disposable emulator. It backgrounds the app, attempts
`am kill`, and, if needed, signals only the validated old PID under the debug app
UID. On API 26, ActivityManager retained the process, so the successful run used
app-UID SIGKILL. Evidence confirms the old PID disappeared, a new PID appeared,
task ID 142 was retained and nested About returned. This is **killed-process /
existing-task restoration**, not force-stop/relaunch, real memory-pressure/LMK
execution, or API 33 task-restoration evidence. It does not clear data, root the
device or change settings. Temporary UI dumps use an owned GUID filename.

English/Persian resources, placeholder parity, Bidi formatting, RTL/LTR, radio
roles, headings, useful labels/live regions and scrollable large-font content
remain covered by source review and automated checks. Both connected runtimes
pass 26 tests, including 200% Persian font, compact/expanded navigation, theme
selection, system-bar icon appearance and recreation. Dynamic Color tests compare
the available API 33 platform palette and surface/content contrast, with the API
26 fallback; they do not certify every wallpaper. API 33 actual restart checks
confirm stored Persian/dark choices, and fresh screenshots were visually reviewed
for RTL layout, mirrored arrows, legibility and clipping. Minified release startup,
navigation and Hilt/JNI result were exercised on both runtimes using a temporary
local debug-signed copy; debug builds were restored afterward.

Manual spoken TalkBack, hardware-keyboard traversal, real predictive gestures,
physical foldable/tablet posture and broad wallpaper contrast checks remain
**NOT RUN**. Reproducible procedures are in
[the design-system QA checklist](architecture/design-system.md#review-closure-theme-effects-and-manual-qa).

### Fresh verification commands and actual outcomes

All commands below ran locally against corrected app/test source. Subsequent
changes were exporter corrections and documentation. Logs, XML, screenshots,
temporary signing products and validation scripts remain in ignored local build
directories; they are not committed or uploaded.

| Command / check | Outcome |
| --- | --- |
| `.\gradlew.bat :app:assembleDebug :app:assembleRelease :app:bundleRelease --max-workers=2 --console=plain` | PASS: debug, R8/minified release APK and release AAB |
| `.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:lintRelease --max-workers=2 --console=plain` | PASS: 32 JVM tests; each lint variant 0 errors / 2 retained advisories |
| `.\gradlew.bat :app:build --max-workers=2 --console=plain` | PASS |
| `.\gradlew.bat :app:assembleDebug --offline --max-workers=2 --console=plain` (twice) | PASS: repeated invocation reused configuration cache, 43 tasks up-to-date |
| `.\gradlew.bat :app:connectedDebugAndroidTest --offline --max-workers=2 --console=plain` with explicit process-local `ANDROID_SERIAL` set separately for each target | PASS: API 26 x86_64 and API 33 ARM64, 26 tests each, 0 failures in per-device XML |
| `cargo fmt --manifest-path native/Cargo.toml --all -- --check` | PASS |
| `cargo clippy --manifest-path native/Cargo.toml --locked --all-targets -- -D warnings` | PASS |
| `cargo test --manifest-path native/Cargo.toml --locked` | BLOCKED: exit 101, `error: linker link.exe not found`; MSVC host prerequisites unchanged |
| `cargo ndk --platform 26 -t arm64-v8a test --locked --no-run` and `cargo ndk --platform 26 -t x86_64 test --locked --no-run` (from `native`) | PASS: standalone Android test builds; deployed test executables each ran 12 passing tests on the respective runtimes and were removed |
| `powershell -NoProfile -ExecutionPolicy Bypass -File .\tools\Test-ExportCodexChanges.ps1` | PASS: 246 assertions, PowerShell 5.1 |
| `pwsh -NoProfile -File .\tools\Test-ExportCodexChanges.ps1` | PASS: 246 assertions, PowerShell 7 |
| `powershell -NoProfile -ExecutionPolicy Bypass -File .\export-codex-changes.ps1 -Base master` | PASS: 46/48 accepted; all 44 original files; copied/patch/metadata inventory comparison passed |
| `powershell -NoProfile -ExecutionPolicy Bypass -File .\tools\Test-TaskRestoration.ps1 -Serial $env:ANDROID_SERIAL -AdbExecutable $adb` with selected disposable emulator | PASS: killed process / existing-task restoration as defined above |
| SDK `zipalign -c -P 16 -v 4 app/build/outputs/apk/release/app-release-unsigned.apk` | PASS: release ZIP alignment |
| APK/AAB entry and JNI symbol inspection; 64-bit ELF LOAD headers | PASS: all four ABIs, expected JNI exports; 64-bit LOAD alignment 0x4000 |
| Workflow YAML structure and five Bash scripts; final diff whitespace/security review | PASS locally; live Action ref audit BLOCKED; hosted execution NOT RUN |
| Real 16 KiB-page execution | NOT RUN: both available runtimes use 4 KiB pages |

The initial exporter reproduction and theme-retry regression failed as intended
before fixes. An initial connected attempt with no executed instrumentation was
not counted, and an API 33 locale-dependent selector failure was corrected before
rerunning all 26 tests. Only inspected, executed final suites count as passing.
Lint's API-33 localeConfig advisory (AppCompat fallback on older APIs) and UseKtx
advisory (Boolean commit result required) are retained without suppression.
Existing Gradle deprecation/protobuf Unsafe/native-access warnings remain visible.

### Privacy, final review and acceptance

Merged release manifest and backup policy remain unchanged: no network/hardware
permission was added, backup stays disabled, and the existing internal receiver
permission/profile-installer DUMP protection remain. JNI source, algorithms,
four ABI targets, ProGuard rules, application ID and all SDK/build/toolchain pins
are unchanged. No telemetry, service or production signing was added. Staged
content was reviewed for secrets, real device identifiers, private machine paths,
generated binaries and unintended files. Test fixtures are synthetic security
cases, not credentials. Graphify is local analysis output and is not committed.

The correction inventory relative to `c6563d4` is exactly:

- `.github/workflows/verify.yml`
- `export-codex-changes.ps1`, `tools/Test-ExportCodexChanges.ps1`, `tools/Test-TaskRestoration.ps1`
- `app/src/main/java/io/github/masoudd2159/deviceatlas/MainActivity.kt`
- `app/src/main/java/io/github/masoudd2159/deviceatlas/presentation/settings/SettingsViewModel.kt`
- `app/src/test/java/io/github/masoudd2159/deviceatlas/presentation/settings/SettingsViewModelTest.kt`
- `app/src/androidTest/java/io/github/masoudd2159/deviceatlas/AdaptiveShellTest.kt`
- `app/src/androidTest/java/io/github/masoudd2159/deviceatlas/PhaseOneAppTest.kt`
- `README.md`, `docs/validation.md`, `docs/phase-1-report.md`
- `docs/architecture/design-system.md`, `docs/architecture/navigation.md`

Reviewed local implementation commits (the documentation closure commit follows
these; inspect `git log c6563d4..HEAD --oneline` for the complete final sequence):

| SHA | Message |
| --- | --- |
| `f2953ae` | fix(devtools): bound private path filtering and verify complete safe exports |
| `ab4b370` | fix(settings): retry failed theme writes and serialize pending choices |
| `529ca16` | fix(devtools): preserve conservative Windows path screening |
| `60752e7` | ci: add Android Rust and PowerShell verification jobs |
| `4fcdacf` | test(navigation): verify cancellation entry lifetime and killed-task restoration |
| `129215e` | fix(devtools): distinguish ordinary prose from adb identifiers |

**Ready for an authorized push and review. Merge readiness remains conditional**
on reviewing immutable setup-Action revisions and obtaining actual hosted CI
results. No known application defect remains from the completed checks, but this
is not full environment/manual verification: Windows host linking, real 16 KiB
execution, spoken TalkBack, physical keyboard/gesture/foldable checks, real LMK
and API 33 killed-task restoration remain outstanding. No push, PR, merge, tag,
release or Phase 2 work is authorized or performed by this closure.
