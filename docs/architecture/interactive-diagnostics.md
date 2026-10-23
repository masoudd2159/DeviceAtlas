# Interactive diagnostics

Phase 5 adds **Interactive Tests** alongside Live Monitoring and Quick Performance
Check inside Diagnostics. The five top-level Navigation 3 destinations are
unchanged. Entry, selection, recomposition, and return from background never start
hardware work. All results are in memory, owned by an Activity ViewModel; no
SavedStateHandle, preference, database, log history, export, or service is used.

## Ownership and lifecycle

`InteractiveViewModel` supplies `viewModelScope` to a Main-confined
`InteractiveSession`. `InteractiveVisibility` lives outside retained navigation
entries, combining the selected panel with Activity RESUMED. Pause, navigation,
category/sensor change, owner disposal, explicit Stop, and timeout stop work.
Rotation stops the test while the surviving ViewModel may retain observations.
A new ViewModel/process starts Idle. The shell saves only its selected panel.

The existing singleton `DiagnosticsCoordinator` gains an interactive lease and
stop callbacks for benchmarks and interactive work. Interactive admission reserves
ownership, asks monitoring/benchmark work to stop, and waits for their retirement.
Benchmark admission likewise stops interactive work and waits for its lease.
Monitoring retains its existing explicit-start, idle-only admission policy: panel
switching stops interactive work, and Start becomes available after retirement.
There is no queued restart or automatic admission after a rejected Start.

Reservations can coexist during retirement; active acquisition/workloads cannot.
Sensor unregister happens immediately on Stop. The generation token invalidates
callbacks and results, but the lease stays held until any synchronous native call
returns and the coroutine completes. Completion posts cleanup to the owner
dispatcher, independently of the possibly canceled ViewModel scope. Lazy jobs
canceled before first dispatch still release their lease. Deadlines include
initial dispatch and admission time using a monotonic clock.

## Display

Eight static canvases render red, green, blue, white, black, mid-gray, a grayscale
gradient, and a checker pattern. Only Previous/Next change the pattern. Start
resets to red. A two-minute deadline bounds inspection. Controls remain outside
the canvas, in normal Material 3 surfaces with scrolling and system insets.
Brightness, screen timeout, system bars, gesture navigation and device settings
are untouched. There is no animation, immersive mode, screenshot acquisition,
camera access, pixel-defect detection, or calibrated color claim.

Manual observations distinguish no noticeable issue, a possible issue, unsure,
unable/skipped, and not tested. A user's judgment never becomes an automatic pass.

## Touch

The actual Compose canvas has an 8×12 grid in portrait geometry or 12×8 when wider
than tall: 96 cells, below the 128-cell cap. Pixel coordinates map only inside the
surface's half-open bounds. Nonfinite, negative and outside points do not mark a
cell. Any size change resets observations and contacts, with an explanatory label;
no inferred remapping is performed. Clear Grid also resets maximum contacts.

Compose pointer input tracks `PointerId.value`, never pointer index. Each event
replaces the currently pressed contacts. Release removes contacts; coroutine
cancellation/disposal clears them. Pointer IDs can be reused in later gestures.
Only ten positions are retained/rendered; active and maximum counts include every
distinct reported contact. Visited flags are bounded by the grid, with no path
history, pressure, stylus metadata, persistence or trace export. Rendering uses
check-shaped strokes plus counts, rather than a color-only result. Coverage is
the observed fraction of this app surface, not a digitizer health percentage.
System UI and gesture-reserved display areas cannot be tested by the app.

## Sensors

`AndroidInteractiveSensors` uses application Context and public SensorManager
APIs. It selects only the default accelerometer (m/s²), gyroscope (rad/s), light
(lux), or proximity (reported cm) sensor. Absence and failed registration have
different typed statuses. Sensor maximum range is shown where finite/available;
there is no universal proximity near/far threshold. Acceleration includes gravity;
zero lux and unchanged readings are legitimate observations, not hardware faults.

One main-looper listener registers only after explicit admission. The requested
100,000 µs interval is a 10 Hz hint, not a delivery guarantee. Each callback copies
only the one or three required values plus arrival time/accuracy. It never retains
SensorEvent. Invalid/nonfinite/malformed observations are rejected, with a bounded
rejection counter. Session tokens and a deactivated subscription reject late
callbacks. Unregister is idempotent and performed on Stop, pause, navigation,
selection change, completion, and disposal. No dedicated thread is needed.

A session accepts at most **64 valid events**, then completes after the next
bounded summary update, or stops at **30 seconds**, whichever comes first.
This deliberately keeps the entire accepted observation window bounded rather
than silently replacing earlier samples. At the requested frequency it will
often complete in roughly six seconds. Every 250 ms at most, a copied batch goes
to Rust on the injected native dispatcher; there is no JNI call per event.
Incoming events during analysis can fill the next batch but cannot grow past 64.
Stop discards any not-yet-published batch; already published observations remain.
Values, units, maximum range, count, latest observation age, and min/max/mean/range
are available as text. Three-axis summaries describe magnitudes, while latest
X/Y/Z values retain their axes. Accuracy is never a calibration certification.
No reading at timeout is explicitly inconclusive. Gentle handling instructions
do not require shaking, striking, spinning, or physically stressing the device.

## Haptics

`AndroidInteractiveHaptics` uses `VibratorManager.defaultVibrator` on API 31+ and
the public Vibrator service on API 26–30. `hasVibrator` distinguishes absence.
`VibrationEffect.createOneShot` uses DEFAULT_AMPLITUDE; no amplitude-control
capability or system setting is assumed. The controller requires an explicitly
started foreground haptic session **and a separate press for each 50 ms pulse**.
The platform gateway refuses durations outside 1–100 ms. No pulse occurs on entry
or Start, and no waveform repeats.

A monotonic rolling ledger enforces at least two seconds between accepted pulses
and at most five pulses per sixty seconds, across test restarts within the owner.
Repeated taps are rejected, never queued. Stop, navigation, pause, error, and
disposal cancel the owned vibrator effect. The gateway retains only the vibrator
it used and does not invoke a manager-wide cancellation. Android cannot confirm
whether the effect was felt; system settings can suppress it. Separate manual
feedback records felt, not felt, unsure, skipped, or not tested.

The only added manifest permission is `android.permission.VIBRATE`, required for
physical vibrator access. It is normal-protection, with no runtime permission
dialog. The existing private receiver signature permission remains. No sensitive,
network, high-rate sensor, foreground service, wake-lock, or storage permission
is introduced. Automated tests use fake haptics and never vibrate the device.

## Presentation and accessibility

Material 3 cards and the existing responsive scrolling layout support compact
screens, large text, light/dark themes, and RTL. Semantic headings, accessible
Start/Stop/Exit/Skip, translated pattern names, textual touch/sensor results,
manual feedback and focus return accompany the canvases. Status transitions use
a polite live region; sensor updates and pointer movement are not live regions.
The physical touch gesture itself may be inaccessible with assistive touch
exploration; skipping is legitimate and never a hardware defect. No test traps
focus in immersive content. Manual TalkBack and foldable/cutout evaluation remain
separate from automated semantics checks.

## Official API references

- [SensorManager registration and sampling hints](https://developer.android.com/reference/android/hardware/SensorManager)
- [Sensor lifecycle and interpretation](https://developer.android.com/develop/sensors-and-location/sensors/sensors_overview)
- [Compose pointer input](https://developer.android.com/develop/ui/compose/touch-input/pointer-input/understand-gestures)
- [VibratorManager](https://developer.android.com/reference/android/os/VibratorManager)
- [VibrationEffect](https://developer.android.com/reference/android/os/VibrationEffect)
- [Vibrator](https://developer.android.com/reference/android/os/Vibrator)
