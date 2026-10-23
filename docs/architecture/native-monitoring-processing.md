# Native monitoring processing — protocol v1

The new `NativeBridge.processMonitoring(LongArray?, DoubleArray?, DoubleArray?)`
is separate from `processHardware` and `mean`; their signatures, operation IDs,
buffer shapes and status codes are unchanged. The existing R8 native-method rule
already covers the new export. All calls run on the injected native dispatcher.

## Header and bounded shapes

Every input begins with `[version=1, operation, count]`. Shape validation happens
before JNI copies any payload, allocates a result, or reads an output. Count is
validated as a signed integer before conversion. JNI copies via region APIs into
fixed stack buffers of 123 longs and 1,200 doubles; it retains no pointers or JVM
references. Rust owns bounded temporary/result vectors only (at most 120 values
per statistic and 50 output doubles).

| Operation | Input longs | Input doubles | Output doubles |
| --- | --- | --- | --- |
| 1: process observation, count=1 | 15 | 1 | 20 |
| 2: summarize, count=0..120 | 3+count | 10×count | 50 |

Observation payload after the header:

1. Monotonic nanoseconds.
2. Process CPU milliseconds.
3. Total RAM bytes.
4. Available RAM bytes.
5. Low-memory threshold bytes.
6. Battery level.
7. Battery scale.
8. Battery temperature in tenths Celsius.
9. Battery millivolts.
10. Signed battery microamps.
11. Previous monotonic nanoseconds, or MIN for no baseline.
12. Previous CPU milliseconds, or MIN for no baseline.

The sole real is thermal headroom. Missing raw integers are MIN on the wire only;
their typed Android unavailable reason is preserved by Kotlin and takes precedence
over numeric validation. Missing headroom sends a placeholder with its explicit
unavailable state retained outside the wire; it can never be published as zero.

Output metric order is explicit `LiveMetric.wire`: total RAM GiB, available RAM GiB,
available percent, threshold GiB, battery percent, battery °C, battery V, signed
battery mA, process CPU core-equivalent percent, headroom ratio. Each has two slots
`[status, value]`; 0=valid, 6=invalid, 9=baseline absent. Unavailable slots contain
initialized zero payloads that Kotlin never treats as measurements. All returned
slots must be finite. Individual invalid observations do not invalidate siblings.

Summary longs after the header are one ten-bit availability mask per row. Doubles
are row-major ten-slot processed measurements. Only set bits participate; missing
slots never enter statistics, including the denominator. A set bit with a nonfinite
value rejects the entire request before output. Invalid mask bits are rejected.
For each metric the output is `[minimum, maximum, mean, latestValid, validCount]`.
Count zero maps to typed INSUFFICIENT, never a numeric zero observation. Count one
is meaningful and yields that observation for each statistic. Summaries use simple
sample averages, not time weighting, and cover the retained rolling window.

## Arithmetic and errors

Memory/battery validation reuses `hardware::process`; Kotlin contains no parallel
calculation engine. CPU checks nonnegative counters/timestamps and uses checked
subtraction. Elapsed nanoseconds must be positive; CPU delta must be nonnegative.
The ratio converts operands before multiplication, avoiding integer overflow.
Zero CPU delta is valid, values above 100% are retained, and a rollback yields an
invalid interval. The current raw observation becomes the next baseline so the
next valid pair can recover. Missing previous CPU data yields baseline absent.

Thermal headroom must be finite and nonnegative; values above one stay intact.
Android categories remain Kotlin-owned and use API guards. Native summaries reuse
the crate's compensated/overflow-resistant mean. No interpolation, smoothing,
prediction, extrapolation or device health scoring is performed.

Whole-call status codes: 0 success; 3 invalid/null buffer/count/shape; 4 JNI failure;
5 contained panic; 6 invalid numeric summary payload; 7 unsupported operation;
8 unsupported version. A Rust result is fully computed and checked before the
single output region write. Rejected calls leave output untouched. JNI exceptions
are immediately checked, cleared and mapped to failure. A failure during the JNI
write is reported as failure; caller never reads that buffer on nonzero status.

Debug and release use unwind-compatible panic containment. Gateway linkage and
security failures map to NATIVE_UNAVAILABLE; unexpected statuses/nonfinite output
map to NATIVE_FAILED. Cancellation is rethrown before/after the bounded synchronous
native call. The session invalidates its generation before canceling, discards late
results and does not start a successor while a retiring call is still owned.

Tests cover checked CPU boundaries, multicore values, memory relationships,
headroom NaN/infinity/>1, missing/constant/signed/extreme summaries, 0/1/2/120/121
counts, malformed headers, null/incorrect buffers, unchanged failure output,
nonfinite defense, and the legacy ABI. Actual execution evidence is recorded in
[the Phase 3 report](../phase-3-report.md).
