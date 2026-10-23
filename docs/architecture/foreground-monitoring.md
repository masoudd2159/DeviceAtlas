# Foreground monitoring — Phase 3

Phase 3 adds read-only observation to Diagnostics. Phase 2's manually refreshed
hardware reader, activity-owned HardwareViewModel, Navigation 3, and JNI methods
remain intact. No metrics are written to disk, preferences or SavedStateHandle.

## Ownership and lifetime

`MainActivity` owns a separate Hilt `MonitoringViewModel`. It owns exactly one
`MonitoringSession` in `viewModelScope`. The shell's `MonitoringVisibility` binds
the actual Activity lifecycle **outside** the retained Navigation 3 entries and
combines RESUMED with `navigation.current == DIAGNOSTICS`. Losing either gate
cancels the sampler. Tab/back handlers also stop synchronously before navigation.
Callbacks are removed on composition disposal. There are no platform listeners,
battery receivers, services, work requests or wakelocks to leave registered.

Idle → Running requires explicit Start with both gates open and no retiring job.
Start clears the previous session and its CPU baseline. Duplicate Start does
nothing. Stop immediately marks Stopped and invalidates a generation token before
canceling. Stop freezes duration from the session's monotonic start at the actual
transition time; repeated Stop cannot extend it. Reset clears elapsed time to zero,
and Start establishes a new baseline. Invalid/regressed clocks preserve already
published duration; ordered nonnegative subtraction prevents overflow and duration
is capped at 600,000 ms. No second timer is introduced. Running may
also become Failed for native/acquisition failures or malformed timing. Three
consecutive completely unavailable samples stop with a collection failure; valid
partial samples continue. Voluntary/lifecycle stops are not errors.

Sampling is immediate, then delayed **5 seconds after completed work**; this
conservative fixed-delay policy may take longer than five seconds and never queues
a backlog. A coroutine deadline and monotonic elapsed check enforce **10 minutes**.
The history evicts oldest entries using `takeLast(120)` and publishes immutable
copies. CPU baselines are separate from retained derived history, so eviction does
not recompute a previously derived first point. Summaries describe retained history.

All session transitions run on Main. Acquisition uses the existing qualified IO
dispatcher; JNI uses the qualified native/Default dispatcher. Cancellation is
checked after acquisition and JNI and immediately before publication. A retiring
job stays owned through completion and its Main-dispatched cleanup handoff, even
when canceled before first dispatch. The completion handler only posts a finite
Runnable via the injected owner dispatcher (Main.immediate in production); it
does not inspect or mutate session state on the completing thread. The task uses
EmptyCoroutineContext, so cleanup still runs after viewModelScope cancellation.
It checks Job identity before releasing ownership, preventing stale cleanup from
clearing a successor. No new coroutine scope or blocking join is introduced.
Rapid Stop/Start cannot overlap uninterruptible work; generation checks reject
late results. canStart requires no owned Job, active scope, visibility/foreground
eligibility and an open owner. ViewModel.onCleared permanently closes the session,
freezes DESTROYED stops and cancels work; it cannot be restarted after disposal.

Rotation/language recreation stops on pause; the surviving Activity ViewModel may
retain completed history. It never auto-resumes. A new ViewModel/process starts
Idle with no history. The app deliberately stops even on pause (stricter than stop)
and requires Start on returning to the foreground.

## Acquisition and provenance

All sources run only inside an active session. AndroidMonitoringSource reads APIs
sequentially, independently protecting each metric. `monotonicNanos` is captured
next to the process CPU reading; `completedNanos` bounds acquisition skew. These
are observations over a small acquisition window, not an atomic system snapshot.
`wallMillis` is display-only and may jump with the user's clock. Sequence numbers
restart at zero on Start. Source metadata/units are fixed by the typed fields:

| Raw field | Public source / minimum API | Raw unit | Rust / display | Availability |
| --- | --- | --- | --- | --- |
| totalBytes, availableBytes | ActivityManager.getMemoryInfo / 1, totalMem / 16 | bytes | Validate total > 0, 0 ≤ available ≤ total; GiB and percent | Missing service, collection error, invalid relationship |
| thresholdBytes, lowMemory | MemoryInfo / 1 | bytes, boolean | GiB; flag stays Kotlin | Independent of capacity calculation |
| cpuMillis | Process.getElapsedCpuTime / 1 | process CPU ms | Checked delta / elapsed ns × 100,000,000; one-core equivalent percent | First/missing baseline, counter rollback, zero/reversed time |
| batteryLevel, batteryScale | ACTION_BATTERY_CHANGED / 5 | level/scale integers | Validate ratio; percent | Missing extra, absent battery, invalid scale |
| temperatureTenths | BatteryManager.EXTRA_TEMPERATURE / 5 | tenths °C | Validate above absolute zero; °C | Missing extra or invalid value |
| voltageMillivolts | EXTRA_VOLTAGE / 5 | mV | Positive; V | Missing extra or invalid value |
| currentMicroamps | BatteryManager.getLongProperty(CURRENT_NOW) / 21 | signed µA | Signed mA, zero preserved | Long.MIN_VALUE and Int.MIN_VALUE unavailable |
| chargingStatus | EXTRA_STATUS / 5 | platform category | Kotlin enum, localized | Unknown status remains not reported |
| thermalStatus | PowerManager.currentThermalStatus / 29 | category | Kotlin maps all seven documented constants | Unsupported on 26–28; unknown remains not reported |
| headroom | PowerManager.getThermalHeadroom(0) / 30 | ratio | Finite nonnegative ratio; >1 retained | Unsupported on 26–29; NaN temporary; infinity/negative invalid |

All implemented sources use the bounded five-second schedule. Static storage,
display and sensor inventories remain in Phase 2 and are not polled by this source.
Optional charge/energy counters and API-37 freeMem are not added to monitoring.

Metric regression evidence: native `capacities_and_relationships` and
`validates_memory_battery_and_headroom_independently` cover RAM/ratios/battery;
`cpu_baseline_zero_backward_and_multicore` covers process CPU; native electrical
unit tests cover signed current/voltage/temperature. Headroom finite/nonnegative/>1
cases are tested independently. `publicCollectorAndRustProduceRealIndependentObservations`
compares real total RAM with MemoryInfo and checks API 26 thermal guards; it also
executes the packaged CPU/summary path. Missing-value gateway tests preserve
individual reasons, and chart/lifecycle fixtures cover partial observations.

Available RAM may include reclaimable memory. Total minus available is not all
apps' memory consumption. CPU is this app process, can exceed 100%, and has no
physical-device-capacity normalization. Positive battery current enters the
battery, negative leaves it. Battery temperature is not CPU/GPU/SoC temperature.
Headroom around/above 1 relates to severe throttling; it is not Celsius or a health
score. Unknown OEM fields do not imply hardware failure.

## Presentation and privacy

Diagnostics keeps its hardware-integrity section and original [2,4,6] JNI mean.
Monitoring adds stable Start/Stop/Clear buttons, status, elapsed seconds (updated
at sample boundaries while running, then frozen at actual Stop), last valid wall time, retained count, current metrics and
RAM/CPU/battery-temperature charts. Start explicitly begins a fresh session.

Charts plot actual monotonic timestamps, dots and straight sampled segments.
Missing observations and intervals over ten seconds break the line. Constant and
single-point histories are centered. Y normalization scales before subtraction to
avoid overflow. The oldest retained point is at the left regardless of text
direction; the axis says oldest-to-newest. Units, unavailable reasons and
min/max/simple sample average/latest valid/count are textual equivalents. Empty
unsupported charts are omitted. Status alone uses a polite live region, avoiding
five-second TalkBack floods. Material controls wrap for large fonts; layout uses
existing theme cards and locale-aware formatting with bidi isolation.

No permissions, network, identifiers, other-app data, persistence, export,
notifications, sensors, workloads, or additional libraries are introduced.

## Extension rules

Add a typed raw field with documented source/API/unit and isolated availability.
Add platform-independent calculations in Rust, version any incompatible wire
change, keep fixed limits and cancellation checks, and test gate transitions and
partial failures before adding UI. Do not add an automatic background owner.

## Official contracts reviewed

- [MemoryInfo](https://developer.android.com/reference/android/app/ActivityManager.MemoryInfo)
- [Process](https://developer.android.com/reference/android/os/Process)
- [SystemClock](https://developer.android.com/reference/android/os/SystemClock)
- [BatteryManager](https://developer.android.com/reference/android/os/BatteryManager)
- [PowerManager](https://developer.android.com/reference/android/os/PowerManager)
- [Compose lifecycle](https://developer.android.com/develop/ui/compose/lifecycle)
