# Native hardware processing

The original compensated mean and its numerical regressions remain in lib.rs.
hardware.rs owns platform-independent validation and derived calculations.
bridge.rs supplies the mean and new processHardware exports using jni-sys 0.3.0.
No dependencies were added. Rust edition 2024 and all pinned versions remain.

## Version 1 protocol

Kotlin declaration: `processHardware(LongArray?, DoubleArray?, DoubleArray?): Int`.
The JNI symbol is
`Java_io_github_masoudd2159_deviceatlas_data_nativecore_NativeBridge_processHardware`.
The existing NativeBridge R8 rule preserves all native methods, including this one.

Input is exactly long[4] = `[1, operation, first, second]`, plus double[1] =
`[real]`. Output is exactly double[5]. No JSON, Java object graph, sensor inventory
or string crosses JNI. Payload size is always 80 bytes (32 integer input, 8 real
input, 40 output), excluding JVM object overhead. Lengths and nulls are checked
before any region copy. Nonmatching version/operation/length fails; there is no
fallback interpretation of another version. Unused real input is zero and must
still be finite. Unused successful output slots are explicitly zeroed.

| Operation | Integer / real input | Successful output |
|---|---|---|
| 1 capacity | total, available Long bytes | total GiB, available GiB, unavailable GiB, available %, unavailable % |
| 2 level | level, scale | percentage in slot 0 |
| 3 temperature | tenths Celsius | Celsius in slot 0 |
| 4 voltage | millivolts | volts in slot 0 |
| 5 current | signed microamps | signed mA in slot 0 |
| 6 charge | microamp-hours | mAh in slot 0 |
| 7 energy | nanowatt-hours | Wh in slot 0 |
| 8 display | positive int32 pixel width/height | reduced width, reduced height in slots 0–1 |
| 9 nonnegative real | finite nonnegative real | same real in slot 0 |
| 10 positive integer | positive int32 | same value in slot 0 |
| 11 nonnegative integer | nonnegative int32 | same value in slot 0 |
| 12 bytes | nonnegative Long bytes | GiB in slot 0 |
| 13 positive real | finite positive real | same real in slot 0 |

Capacity requires total>0 and 0<=available<=total. Checked subtraction happens
before deliberate floating-point conversion; there is no byte-count multiplication
that can overflow. Dividing before percentage multiplication keeps results finite
at i64::MAX. GiB uses 2^30 bytes; no decimal GB claim is made. Euclid's algorithm
reduces display ratios without multiplication. Temperature rejects values below
absolute zero and outside the platform int input range. Current preserves sign,
including valid zero; integer sentinel values are invalid. Charge/energy require
nonnegative values. No value is silently clamped or classified as device health.
Optional missing fields bypass Rust, retaining the original unavailable reason.

| Status | Meaning / Kotlin mapping |
|---:|---|
| 0 | Successful fully initialized output; Kotlin additionally checks every slot is finite |
| 3 | Invalid/null/incorrect-length buffer → native failed |
| 4 | JNI function table/region operation failure → native failed |
| 5 | Caught Rust panic → native failed |
| 6 | Invalid numerical observation → invalid response |
| 7 | Unsupported operation → native failed (contract mismatch) |
| 8 | Unsupported version → native failed (contract mismatch) |
| Other | Unknown status → native failed; output never read as a result |

Legacy mean statuses 0–5 keep their previous meanings. Its allocation is now
bounded at 1,048,576 doubles (8 MiB); larger buffers return existing status 3.
Its signature, compensated math, successful behavior and `[2,4,6]` result are
unchanged. Kotlin starts new hardware output slots as NaN, preventing an incomplete
native success response from becoming plausible zeros.

JNI reads/writes use checked region APIs and fixed Rust stack arrays, without
pinned buffers, retained handles, raw Java array pointers or native heap allocations
in the hardware path. ExceptionCheck follows every JNI operation; pending JNI
exceptions are cleared and mapped to status 4. catch_unwind contains Rust panics
in both exports. Both build profiles retain unwinding. NativeDispatcher is Default;
Kotlin checks cancellation before and after bounded synchronous native work and
withContext prevents delivery to a cancelled caller. Missing library/symbol or
load security failure becomes NATIVE_UNAVAILABLE, leaving text metadata usable.

Tests cover capacities/ratios, zero/negative/contradictory data, integer extremes,
temperature/electrical units and sentinels, finite floats, display dimensions,
determinism, versions/operations, null and incorrectly sized JNI arrays, unchanged
output on failure, load failures, panic containment and all original mean cases.
See the Phase 2 report for executed suites and runtime limitations.
