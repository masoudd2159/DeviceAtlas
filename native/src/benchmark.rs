//! DeviceAtlas workload v1. Application microbenchmarks, never hardware scores.
use std::hint::black_box;
use std::time::{Duration, Instant};

pub const MAX_ITERATIONS: i64 = 1_999_872; // largest 256-step multiple below two million
pub const MAX_BUFFER: i64 = 4 * 1024 * 1024; // two buffers: <= 8 MiB total
pub const MAX_BUDGET_US: i64 = 20_000;
const BATCH: u64 = 256;
const SEED: u64 = 0x1234_5678_9abc_def0;
const INTEGER_DIGEST: u64 = 0x64f9_6c55_5cec_2f08;
const FLOATING_DIGEST: u64 = 0x3fee_02f9_da8c_4cb4;

/// Fixed header: version, operation (1 chunk/2 summary), workload (1..3),
/// iteration cap / repetition count, budget microseconds, bytes per buffer.
pub fn shape(header: &[i64]) -> Result<usize, i32> {
    if header.len() != 6 {
        return Err(3);
    }
    if header[0] != 1 {
        return Err(8);
    }
    if !(1..=3).contains(&header[2]) {
        return Err(7);
    }
    match header[1] {
        1 => {
            if !(1..=MAX_ITERATIONS).contains(&header[3])
                || !(1..=MAX_BUDGET_US).contains(&header[4])
            {
                return Err(6);
            }
            if (header[2] == 3 && (!(4096..=MAX_BUFFER).contains(&header[5]) || header[3] > 64))
                || (header[2] != 3
                    && (header[5] != 0
                        || header[3] < BATCH as i64
                        || header[3] % BATCH as i64 != 0))
            {
                return Err(6);
            }
            Ok(0)
        }
        2 if (1..=3).contains(&header[3]) && header[4] == 0 && header[5] == 0 => {
            Ok(header[3] as usize * 2)
        }
        2 => Err(6),
        _ => Err(7),
    }
}

fn integer(seed: u64) -> u64 {
    let mut x = black_box(seed);
    for i in 0..BATCH {
        x = black_box(
            x.wrapping_add(i)
                .rotate_left(13)
                .wrapping_mul(0x9e37_79b9_7f4a_7c15)
                ^ (x >> 7),
        );
    }
    black_box(x)
}

fn floating(seed: f64) -> Result<f64, i32> {
    if !seed.is_finite() || !(0.0..=1.0).contains(&seed) {
        return Err(6);
    }
    let mut x = black_box(seed);
    let mut y = black_box(0.25);
    for _ in 0..BATCH {
        x = black_box(x * 1.000_000_119_209_289_6 + 0.125);
        if x > 16.0 {
            x -= 16.0;
        }
        y = black_box(y * 0.5 + x * 0.25);
    }
    let result = black_box(x + y);
    if result.is_finite() {
        Ok(result)
    } else {
        Err(6)
    }
}

fn buffer(size: usize) -> Result<Vec<u8>, i32> {
    let mut value = Vec::new();
    value.try_reserve_exact(size).map_err(|_| 4)?;
    value.resize(size, 0);
    Ok(value)
}

fn rate(workload: i64, work: f64, nanos: f64) -> Result<f64, i32> {
    // Also bound summary inputs before calculating; exact integers fit in f64.
    if !work.is_finite()
        || !nanos.is_finite()
        || work <= 0.0
        || nanos <= 0.0
        || work.fract() != 0.0
        || nanos.fract() != 0.0
        || work > 4_294_967_296.0
        || nanos > 2_000_000_000.0
    {
        return Err(6);
    }
    let divisor = if workload == 3 { 1_048_576.0 } else { 1.0 };
    let value = work / nanos * 1e9 / divisor;
    if value.is_finite() && value > 0.0 {
        Ok(value)
    } else {
        Err(6)
    }
}

pub fn process(header: &[i64], real: &[f64]) -> Result<[f64; 10], i32> {
    let size = shape(header)?;
    if real.len() != size {
        return Err(3);
    }
    let workload = header[2];
    if header[1] == 2 {
        return summary(workload, real);
    }
    let deadline = Instant::now();
    let budget = Duration::from_micros(header[4] as u64);
    let mut src = if workload == 3 {
        buffer(header[5] as usize)?
    } else {
        Vec::new()
    };
    let mut dst = if workload == 3 {
        buffer(header[5] as usize)?
    } else {
        Vec::new()
    };
    for (block, slice) in src.chunks_mut(4096).enumerate() {
        if deadline.elapsed() >= budget {
            return Err(6);
        }
        for (offset, v) in slice.iter_mut().enumerate() {
            *v = ((block * 4096 + offset) % 251) as u8;
        }
    }
    // Independent scalar reference values are checked by tests; verification is
    // outside active timing. No JVM references or buffers survive this call.
    let mut count = 0_u64;
    let mut active = Duration::ZERO;
    let mut checksum = 0_u64;
    let cap = header[3] as u64;
    while count < cap && deadline.elapsed() < budget {
        let start = Instant::now();
        let digest = match workload {
            1 => integer(SEED),
            2 => floating(0.5)?.to_bits(),
            3 => {
                black_box(&mut dst).copy_from_slice(black_box(&src));
                0
            }
            _ => return Err(7),
        };
        active = active.checked_add(start.elapsed()).ok_or(6)?;
        match workload {
            1 if digest != INTEGER_DIGEST => return Err(6),
            2 if digest != FLOATING_DIGEST => return Err(6),
            3 => {
                // Verify every copy and force observation before another copy.
                if black_box(&dst) != &src {
                    return Err(6);
                }
                // Full equality is the integrity check. Carry compact sampled
                // state without an additional full-buffer checksum pass.
                let sampled = u64::from(dst[0])
                    | (u64::from(dst[dst.len() / 2]) << 8)
                    | (u64::from(dst[dst.len() - 1]) << 16);
                checksum = checksum.wrapping_add(sampled ^ dst.len() as u64);
            }
            _ => checksum = checksum.wrapping_add(digest),
        }
        count = count
            .checked_add(if workload == 3 { 1 } else { BATCH })
            .ok_or(6)?;
    }
    let work = if workload == 3 {
        count.checked_mul(header[5] as u64).ok_or(6)?
    } else {
        count
    };
    let nanos = u64::try_from(active.as_nanos()).map_err(|_| 6)?;
    let throughput = rate(workload, work as f64, nanos as f64)?;
    Ok([
        1.0,
        workload as f64,
        1.0,
        work as f64,
        nanos as f64,
        throughput,
        (checksum >> 32) as f64,
        (checksum as u32) as f64,
        1.0,
        0.0,
    ])
}

// Scalar reference with no measured input. Separate expression order checks
// the measured fixed-size kernel, including optimized builds.
#[cfg(test)]
fn integer_reference() -> u64 {
    let mut x = SEED;
    for i in 0..BATCH {
        let old = x;
        x = (old.wrapping_add(i).rotate_left(13)).wrapping_mul(0x9e37_79b9_7f4a_7c15);
        x ^= old >> 7;
    }
    x
}
#[cfg(test)]
fn floating_reference() -> u64 {
    let mut x = 0.5_f64;
    let mut y = 0.25_f64;
    for _ in 0..BATCH {
        x = x * 1.000_000_119_209_289_6 + 0.125;
        if x > 16.0 {
            x -= 16.0;
        }
        y = y * 0.5 + x * 0.25;
    }
    (x + y).to_bits()
}

fn summary(workload: i64, samples: &[f64]) -> Result<[f64; 10], i32> {
    let mut rates = [0.0_f64; 3];
    let mut work = 0.0;
    let mut nanos = 0.0;
    let count = samples.len() / 2;
    for (i, pair) in samples.as_chunks::<2>().0.iter().enumerate() {
        rates[i] = rate(workload, pair[0], pair[1])?;
        work += pair[0];
        nanos += pair[1];
    }
    rates[..count].sort_by(f64::total_cmp);
    let minimum = rates[0];
    let maximum = rates[count - 1];
    let median = if count == 2 {
        (minimum + maximum) / 2.0
    } else {
        rates[count / 2]
    };
    let variation = (maximum - minimum) / median * 100.0;
    let out = [
        1.0,
        workload as f64,
        1.0,
        work,
        nanos,
        median,
        minimum,
        maximum,
        variation,
        count as f64,
    ];
    if out.iter().all(|v| v.is_finite()) {
        Ok(out)
    } else {
        Err(6)
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn kernels_match_independent_references() {
        assert_eq!(integer(SEED), 0x64f9_6c55_5cec_2f08);
        assert_eq!(floating(0.5).unwrap().to_bits(), 0x3fee_02f9_da8c_4cb4);
        assert_eq!(integer(SEED), integer_reference());
        assert_eq!(floating(0.5).unwrap().to_bits(), floating_reference());
        assert_eq!(integer(SEED), integer(SEED));
        assert!(floating(f64::NAN).is_err());
        assert!(floating(f64::INFINITY).is_err());
        assert!(floating(-1.0).is_err());
    }
    #[test]
    fn real_chunks_count_only_completed_work() {
        for workload in 1..=3 {
            let cap = if workload == 3 { 2 } else { 512 };
            let size = if workload == 3 { 4096 } else { 0 };
            let out = process(&[1, 1, workload, cap, 20_000, size], &[]).unwrap();
            assert!(out[3] > 0.0);
            assert!(
                out[3]
                    <= if workload == 3 {
                        (cap * size) as f64
                    } else {
                        cap as f64
                    }
            );
            assert!(out[4] > 0.0 && out[5] > 0.0);
            assert_eq!(out[8], 1.0);
        }
    }
    #[test]
    fn all_bounds_and_protocol_errors() {
        assert_eq!(shape(&[]), Err(3));
        assert_eq!(shape(&[2, 1, 1, 256, 1, 0]), Err(8));
        assert_eq!(shape(&[1, 9, 1, 256, 1, 0]), Err(7));
        assert_eq!(shape(&[1, 1, 4, 256, 1, 0]), Err(7));
        for cap in [0, -1, MAX_ITERATIONS + 1, i64::MAX] {
            assert!(shape(&[1, 1, 1, cap, 1, 0]).is_err());
        }
        assert!(shape(&[1, 1, 1, 1_999_872, 20_000, 0]).is_ok());
        for size in [0, -1, MAX_BUFFER + 1, i64::MAX] {
            assert!(shape(&[1, 1, 3, 1, 1, size]).is_err());
        }
        assert!(shape(&[1, 1, 3, 64, 20_000, MAX_BUFFER]).is_ok());
        assert!(shape(&[1, 1, 3, 65, 1, 4096]).is_err());
        for budget in [0, -1, MAX_BUDGET_US + 1, i64::MAX] {
            assert!(shape(&[1, 1, 1, 256, budget, 0]).is_err());
        }
        assert!(process(&[1, 1, 1, 256, 1000, 0], &[0.0]).is_err());
    }
    #[test]
    fn summary_statistics_units_and_invalid_observations() {
        let out = process(
            &[1, 2, 1, 3, 0, 0],
            &[10.0, 1_000_000.0, 30.0, 1_000_000.0, 20.0, 1_000_000.0],
        )
        .unwrap();
        assert_eq!(&out[5..], &[20_000.0, 10_000.0, 30_000.0, 100.0, 3.0]);
        let two = process(&[1, 2, 1, 2, 0, 0], &[10.0, 1_000_000.0, 30.0, 1_000_000.0]).unwrap();
        assert_eq!(two[5], 20_000.0);
        let one = process(&[1, 2, 3, 1, 0, 0], &[1_048_576.0, 1_000_000_000.0]).unwrap();
        assert_eq!(one[5], 1.0);
        assert_eq!(one[8], 0.0);
        assert_eq!(one[9], 1.0);
        for v in [0.0, -1.0, f64::NAN, f64::INFINITY, f64::MAX, 0.5] {
            assert!(process(&[1, 2, 1, 1, 0, 0], &[v, 100.0]).is_err());
            assert!(process(&[1, 2, 1, 1, 0, 0], &[100.0, v]).is_err());
        }
        assert!(process(&[1, 2, 1, 0, 0, 0], &[]).is_err());
    }
    #[test]
    fn maximum_memory_is_bounded_and_verified() {
        let out = process(&[1, 1, 3, 1, 20_000, MAX_BUFFER], &[]);
        // Slow debug allocation may consume the entire chunk budget: rejection
        // is honest; a successful result must contain exactly one real copy.
        if let Ok(out) = out {
            assert_eq!(out[3], MAX_BUFFER as f64);
        }
    }
}
