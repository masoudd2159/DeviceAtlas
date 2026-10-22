# Hardware collection and compatibility

The shipping baseline is API 26. The following table gives the platform API's
minimum level, not a lower app minSdk. Every numeric observation passes through
Rust before presentation. Kotlin sanitizes platform text and maps framework enums;
it does not compute hardware ratios, capacity conversions or electrical units.

All rows are permission-free. No identifier, file content or physical-storage
inventory is collected. API guards apply to SoC fields (31) and cycle count (34).
No hidden API, shell, sysfs or hardware-marketing database is used.

Test abbreviations: **D** HardwareDataTest (sanitation, missing states, field
isolation and scope); **R** Rust hardware unit tests (ranges/conversions);
**J** RustHardwareProcessorTest and HardwareIntegrationTest (JNI mapping and
actual platform relationships); **V** HardwareViewModelTest; **U**
HardwareScreenTest/HardwareRenderingTest plus preserved Phase 1 UI suites.
Tests cover shared mechanisms; not every individual OEM field can be forced
into every failure state on the available devices.

| Field | Actual public source | API minimum | Raw unit | Processing / display | Unavailable handling | Tests |
|---|---|---:|---|---|---|---|
| Manufacturer | Build.MANUFACTURER | 4 | Text | Kotlin sanitized metadata | Blank/unknown → not reported | D, J, U |
| Brand | Build.BRAND | 1 | Text | Kotlin metadata | Same | D, J |
| Model | Build.MODEL | 1 | Text | Kotlin metadata, technical LTR | Same | D, J, U |
| Device codename | Build.DEVICE | 1 | Text | Nonunique product metadata | Same | D |
| Product | Build.PRODUCT | 1 | Text | Nonunique product metadata | Same | D |
| Hardware family | Build.HARDWARE | 8 | Text | No SoC-name inference | Same | D |
| Board family | Build.BOARD | 1 | Text | OEM board label | Same | D |
| Android release | Build.VERSION.RELEASE | 1 | Text | Reported version, no marketing mapping | Same | D, J |
| API level | Build.VERSION.SDK_INT | 4 | Integer | Rust positive integer | Invalid → invalid response | R, J |
| OS build ID | Build.ID | 1 | Text | Nonunique OS build identifier; never fingerprint | Not reported | D |
| Security patch | Build.VERSION.SECURITY_PATCH | 23 | Date text | As reported by OS | Not reported | D |
| SoC manufacturer | Build.SOC_MANUFACTURER | 31 | Text | No inferred fallback | Below 31 → unsupported; unknown → not reported | D, J |
| SoC model | Build.SOC_MODEL | 31 | Text | No marketing-name database | Same | D, J |
| Supported ABIs | Build.SUPPORTED_ABIS | 21 | Ordered ABI strings | Platform order retained | Blank → not reported | D, J |
| 32-bit ABIs | Build.SUPPORTED_32_BIT_ABIS | 21 | ABI strings | Platform list | Empty → not present | D |
| 64-bit ABIs | Build.SUPPORTED_64_BIT_ABIS | 21 | ABI strings | Platform list | Empty → not present | D |
| Primary ABI | First Build.SUPPORTED_ABIS entry | 21 | ABI text | Platform preference, not process ABI | Empty → not reported | D, J |
| Process-available logical processors | Runtime.availableProcessors | 1 | Count | Rust positive integer; not physical core count | Invalid response | R, J |
| Kernel-visible RAM total | ActivityManager.MemoryInfo.totalMem | 16 | Long bytes | Rust GiB and relationships | Failed service / invalid total | D, R, J, U |
| Available RAM estimate | MemoryInfo.availMem | 1 | Long bytes | Rust GiB and available percentage | Negative or above total → invalid | R, J, U |
| RAM remainder | Derived total minus available | — | Checked Long subtraction | Rust unavailable GiB/percent; not exact app usage | Related invalid values propagate | R, J |
| Low-memory flag | MemoryInfo.lowMemory | 1 | Boolean | Android-reported state | Service failure | D |
| Low-memory threshold | MemoryInfo.threshold | 1 | Long bytes | Rust GiB | Negative → invalid | R |
| App-data filesystem total | StatFs(context.dataDir).totalBytes | 18 | Long bytes | Rust GiB; filesystem scope | Per-field exception or invalid capacity | D, R, J, U |
| Filesystem available | StatFs.availableBytes | 18 | Long bytes | Available to apps, not all free blocks | Negative/above total → invalid | R, J |
| Filesystem unavailable | Derived total minus available | — | Long bytes | Rust GiB and unavailable percentage, includes reserves | Related invalid values propagate | R, J |
| Battery presence | ACTION_BATTERY_CHANGED / EXTRA_PRESENT | 5 | Boolean | Explicit false remains false | Missing extra → not reported | D, J |
| Battery level | EXTRA_LEVEL / EXTRA_SCALE | 5 | Integer pair | Rust 0–100 percentage; no clamping | Missing / invalid scale or relationship | R, J, U |
| Battery status | EXTRA_STATUS | 5 | Framework enum | Kotlin localized category | Unknown → not reported | D, J |
| Charging source | EXTRA_PLUGGED | 5 | Framework enum | Unplugged, AC, USB, wireless, dock | Unknown → not reported | D, J |
| Reported battery health | EXTRA_HEALTH | 5 | Framework enum | Category, never battery wear percent | Unknown → not reported | D, J |
| Temperature | EXTRA_TEMPERATURE | 5 | Tenths Celsius | Rust °C; rejects below absolute zero | Missing / invalid | R, J |
| Voltage | EXTRA_VOLTAGE | 5 | Millivolts | Rust volts; must be positive | Missing / invalid zero | R, J |
| Charge counter | BatteryManager.getLongProperty(CHARGE_COUNTER) | 21 | Microamp-hours | Rust mAh; nonnegative | Long.MIN_VALUE → not reported | D, R, J |
| Current now | getLongProperty(CURRENT_NOW) | 21 | Signed microamps | Rust signed mA; positive net charging | Unsupported sentinel; no absolute-value conversion | D, R, J |
| Energy counter | getLongProperty(ENERGY_COUNTER) | 21 | Nanowatt-hours | Rust Wh; nonnegative | Unsupported sentinel | D, R, J |
| Charge cycles | EXTRA_CYCLE_COUNT | 34 | Integer count | Rust nonnegative integer | Below 34 unsupported; absent not reported | R, J |
| Default display mode | DisplayManager.getDisplay(DEFAULT_DISPLAY).mode | 17 / 23 | Pixel width/height | One immutable mode read; Rust reduced aspect ratio | No display / invalid dimensions | R, J, U |
| Nominal refresh rate | Same Display.Mode.refreshRate | 23 | Float Hz | Rust finite positive validation; never FPS | Invalid / missing mode | R, J |
| App logical density | Resources.displayMetrics.densityDpi | 4 | Integer dpi | Rust positive validation; not physical DPI | Invalid | R, U |
| HDR support | Default Display.hdrCapabilities.supportedHdrTypes | 24 | Capability array | Kotlin boolean, no panel inference | Null → not reported | D, J |
| OpenGL ES | ActivityManager.deviceConfigurationInfo.glEsVersion | 4 | System version string | System support capability | Undefined → not reported | D, J |
| Vulkan | PackageManager.FEATURE_VULKAN_HARDWARE_VERSION / FeatureInfo.version | 24 | Packed platform version | Kotlin decodes platform version bits | Feature absent / version not reported | D, J |
| GPU marketing identity | No collector enabled | — | — | Explicit unsupported state; no GL context | Unsupported | U |
| Sensor inventory / type | SensorManager.getSensorList(TYPE_ALL), Sensor.type | 3 | Framework type | Grouped lazy inventory; count is not quality | Empty is valid; service failure separate | D, J, U |
| Sensor name/vendor/version | Sensor.name / vendor / version | 3 | Text / integer | Sanitized Kotlin text, Rust version validation | Individual field failure isolated | D, R, J |
| Sensor current metadata | Sensor.power | 3 | mA | Rust finite nonnegative validation | Invalid response | R, J |
| Sensor range/resolution | Sensor.maximumRange / resolution | 3 | Sensor-type units | Rust finite nonnegative validation; documented types only | Other types → unsupported | R, J, U |
| Wake-up capability | Sensor.isWakeUpSensor | 21 | Boolean | Reported capability | Per-field failure | D, J |
| Reporting mode | Sensor.reportingMode | 21 | Framework enum | Rust integer check, Kotlin enum label | Future mode → not reported label | D, R, J |
| Reporting delays | Sensor.minDelay / maxDelay | 9 / 21 | Microseconds | Rust nonnegative; continuous/on-change only | Other reporting modes → unsupported | R, J |

## Availability, provenance and freshness

`HardwareValue` separates available values from nine explicit unavailability
reasons. Available zero values only enter fields where zero has meaning (e.g.
discharged battery, signed current at rest, zero available capacity, on-change
delay). A zero voltage, zero total capacity or zero display dimension is invalid.
Text is trimmed, bounded to 512 characters and stripped of control and directional
override characters; blank and Build.UNKNOWN become not reported. No raw exception
text or complete snapshot is logged or shown.

Each hardware key owns a Source and SESSION/SNAPSHOT freshness tag. MetricRequest
operations specify raw units; processed numeric data carries UnitKind. Capacity
retains exact Long input bytes alongside Rust's explicitly floating-point GiB
conversion and ratios. DisplayMode retains integer pixels and reduced integer
ratio. Sensor fields have the fixed SensorManager provenance. The snapshot time
describes the dynamic collection batch, not an atomic hardware measurement or
fresh collection of cached session metadata.

Independent field operations catch RuntimeException as COLLECTION_FAILED, without
asserting an undocumented permission requirement. Cancellation propagates. No
current field needs PERMISSION_RESTRICTED, but the contract retains it for future
documented APIs. Null services are temporary, an absent display/battery is not
present, and unsupported guarded APIs stay unsupported. A battery intent with
explicit presence=false suppresses the other battery measurements.

The Android 8 implementation of getIntProperty narrows queryProperty's long
sentinel to int, yielding zero. Reading all optional properties with the public
getLongProperty accessor preserves Long.MIN_VALUE and avoids claiming an
unsupported current/charge reading is zero. Additional integer sentinel handling
covers OEM behavior. No hidden registrar API is called.

## Ownership and extension

Application Context and an injected IO dispatcher belong to
AndroidHardwareCollector. A singleton ProcessedHardwareReader mutex serializes
collection; successful static metadata is cached for the process, transient
failures are retried. Each refresh re-enumerates sensor capabilities to account
for accessible inventory changes, without registering listeners.

MainActivity owns one HardwareViewModel shared by Overview, Device, Sensors and
Diagnostics. ON_RESUME on an information page and explicit Refresh request work;
first entry with no snapshot also requests data. Tab changes reuse the snapshot.
Settings does not initiate collection. Concurrent refresh requests coalesce into
the active job. Previous values and their original timestamp stay visible during
refresh and on overall failure; partial failures carry their own reason in the
new batch. ViewModel cancellation prevents late publication. No Flow creates a
polling loop, unmanaged scope, service or retained Activity context.

To add a field: define its typed key/source/freshness and raw unit, collect via a
public API with guards and field isolation, add a Rust operation only if new
numeric semantics require one, provide English/Persian labels, extend matrix and
boundary/source tests. No forwarding use-case or additional Android module is
required.

## References verified during implementation

- [Build reference](https://developer.android.com/reference/android/os/Build)
- [MemoryInfo reference](https://developer.android.com/reference/android/app/ActivityManager.MemoryInfo)
- [BatteryManager reference](https://developer.android.com/reference/android/os/BatteryManager)
- [Android 8 BatteryManager implementation](https://android.googlesource.com/platform/frameworks/base/+/android-8.0.0_r1/core/java/android/os/BatteryManager.java)
- [Sensor reference](https://developer.android.com/reference/android/hardware/Sensor)
- [Display.Mode reference](https://developer.android.com/reference/android/view/Display.Mode)
- [Official SDK catalog](https://dl.google.com/android/repository/repository2-3.xml)
