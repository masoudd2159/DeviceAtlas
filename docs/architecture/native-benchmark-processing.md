# Native benchmark protocol v1

The kept `NativeBridge.processBenchmark(LongArray?, DoubleArray?, DoubleArray?)`
returns a status and writes exactly ten result doubles only after successful
calculation. The mean, hardware, and monitoring entry points are unchanged.
`native/src/benchmark.rs` contains safe Rust algorithms and statistics;
`bridge.rs` contains the small unsafe JNI array boundary and panic containment.

## Shapes and limits

The header is exactly six signed longs:

| Index | Meaning |
| --- | --- |
| 0 | Protocol version: 1 |
| 1 | Operation: 1 = measured chunk, 2 = summarize |
| 2 | Workload: 1 integer, 2 floating point, 3 sequential buffer copy |
| 3 | Chunk iteration/copy cap, or summary repetition count |
| 4 | Chunk budget in microseconds, or zero for summary |
| 5 | Bytes per memory buffer, or zero otherwise |

Chunks require an empty real array. CPU caps are positive multiples of 256 up
to 1,999,872; memory caps are 1..64 copies. Budget is 1..20,000 microseconds.
Memory buffers are 4,096..4,194,304 bytes **each**, at most 8 MiB combined.
CPU requests require zero buffer bytes. Summary counts are 1..3, with exactly
`2 * count` doubles: pairs of completed work and active nanoseconds. Header
budget/buffer fields must be zero for summaries. No caller can raise these caps.

Chunk output:

```text
[protocol=1, workload, algorithm=1, completedWork, activeNanos, rate,
 checksumHigh32, checksumLow32, verified=1, reserved=0]
```

Summary output:

```text
[protocol=1, workload, algorithm=1, totalWork, totalActiveNanos,
 medianRate, minimumRate, maximumRate, variationPercent, validCount]
```

All integer-valued fields fit exactly in doubles at these bounds. Checksum halves
preserve all 64 bits. Kotlin validates finite fields, identity/version, positive
integral counts and durations, cap/multiple constraints, verification flag,
summary totals/count, and rate ordering before constructing typed results.
Summary pairs require integral positive work <= 2^32 and duration <= 2 s;
invalid/missing observations cause rejection rather than fabricated samples.

Statuses: 0 success; 3 invalid/null buffer or shape; 4 JNI/allocation failure;
5 contained panic; 6 invalid numeric/input/integrity/zero-completed-work result;
7 unknown operation/workload; 8 unsupported version. Legacy statuses retain
their original meanings. Error output is never interpreted as a numeric result.
JNI function-table availability, pending exceptions, array lengths, and each
read/write are checked. Input rejection happens before output writes. A JVM
write failure returns an error even if the JVM has touched output; Kotlin always
discards the entire output on nonzero status.

## Exact workloads

**Integer v1:** Start each block with `0x123456789abcdef0`. For i = 0..255,
compute `((x + i).rotate_left(13) * 0x9e3779b97f4a7c15) XOR (old_x >> 7)`
with explicit wrapping u64 addition/multiplication. One iteration is one such
algorithm step, not a CPU instruction. The expected final block digest is
`0x64f96c555cec2f08`. Black-boxed inputs/state/output prevent dead-code removal.
Every completed block is checked, counted as 256 iterations, and accumulated
into a wrapping checksum. Checked counters cannot overflow under the caps.

**Floating v1:** Start each block with x = 0.5 and y = 0.25. Repeat 256 times:
`x = x * 1.0000001192092896 + 0.125`; if x > 16, subtract 16;
`y = y * 0.5 + x * 0.25`. These bounded, dependent f64 updates use black boxes;
finite state and seed range are checked. The final `(x+y).to_bits()` is
`0x3fee02f9da8c4cb4`, verified for every block. Tests check independent scalar
references and fixed known answers in debug and optimized Android execution.
There is no division by zero, FMA operation, FLOPS claim, or extrapolation.

**Copy v1:** Allocate two bounded Rust u8 vectors with fallible reservation. Fill
source byte i with `i % 251` outside active timing. Check the chunk deadline
between each 4 KiB initialization segment. Each measured operation copies the
entire source into the reusable destination with `copy_from_slice`. Outside the
copy timer, fully compare destination and source, then accumulate compact
first/middle/last-byte state XOR buffer length into a checksum. Full equality,
not the sampled checksum alone, verifies every copy. Actual work is
`completedCopies * bufferBytes`, counting payload bytes once. No buffer crosses
JNI or survives the call. This is in-process copy throughput affected by caches
and library routines, never maximum physical RAM bandwidth.

## Timing and summaries

Rust `Instant` measures each complete arithmetic block or buffer copy. Durations
are checked and summed; zero duration/work is an error. Allocation, initialization,
checksum/full-copy verification, JNI, safety readings, and Kotlin orchestration
are outside active timing. The floating kernel's finite guard and normal timed
dispatch/timer overhead remain within its measured block. The native call's
cooperative deadline covers setup and the loop, including verification overhead.
It is checked before every block/copy; a time-limited chunk reports only fully
completed, verified work. A slow OS thread may return after its requested budget.

Rate is `work / activeNanos * 1e9`, divided by 1,048,576 for copy MiB/s. Rust
sorts the one to three real repetition rates; median is the middle rate or mean
of two middle rates. Variation is `(maximum - minimum) / median * 100`.
Single-run variation is zero. Every output must be finite and positive where
required. Larger values mean higher throughput of that same workload. Kotlin
aggregates exact chunk counts/durations into a repetition and delegates all
rate/statistical calculations back to Rust. Warmup is discarded entirely.

There is no Rust thread, mutable global session, retained JNI reference, unsafe
buffer pointer lifetime, external benchmark framework, unbounded allocation, or
filesystem workload. `catch_unwind` contains ordinary unwinding panics under the
preserved project profile; process-aborting failures cannot be made recoverable.
