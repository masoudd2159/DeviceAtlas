//! Monitoring v1: bounded primitive protocol, independent of hardware v1.
//! Missing integers use MIN only on the wire; every result has an explicit status.
use crate::hardware;

pub const METRICS: usize = 10;
pub const LIMIT: usize = 120;
pub const SAMPLE_LONGS: usize = 15; // header[3], raw[10], previous[2]
pub const INVALID: f64 = 6.0;
pub const BASELINE: f64 = 9.0;

/// Validate shapes before any JNI region copy or allocation.
pub fn shape(header: [i64; 3]) -> Result<(usize, usize, usize), i32> {
    let [version, operation, count] = header;
    if version != 1 {
        return Err(8);
    }
    match operation {
        1 if count == 1 => Ok((SAMPLE_LONGS, 1, METRICS * 2)),
        2 if (0..=LIMIT as i64).contains(&count) => {
            let n = count as usize;
            Ok((3 + n, n * METRICS, METRICS * 5))
        }
        1 | 2 => Err(3),
        _ => Err(7),
    }
}

/// CPU milliseconds / elapsed nanoseconds, one-core equivalent (may exceed 100%).
pub fn cpu(now: i64, cpu: i64, previous_time: i64, previous_cpu: i64) -> Result<f64, i32> {
    if now < 0 || cpu < 0 {
        return Err(6);
    }
    if previous_time == i64::MIN || previous_cpu == i64::MIN {
        return Err(9);
    }
    if previous_time < 0 || previous_cpu < 0 {
        return Err(6);
    }
    let wall = now.checked_sub(previous_time).filter(|x| *x > 0).ok_or(6)?;
    let used = cpu.checked_sub(previous_cpu).filter(|x| *x >= 0).ok_or(6)?;
    Ok(used as f64 / wall as f64 * 100_000_000.0)
}

pub fn process(input: &[i64], real: &[f64]) -> Result<Vec<f64>, i32> {
    let header: [i64; 3] = input.get(..3).ok_or(3)?.try_into().map_err(|_| 3)?;
    let (longs, doubles, _) = shape(header)?;
    if input.len() != longs || real.len() != doubles {
        return Err(3);
    }
    if header[1] == 1 {
        // time, cpu ms, RAM total/available/threshold bytes, level/scale,
        // battery tenths C, millivolts, signed microamps, previous time/cpu.
        let a = &input[3..];
        let mut out = vec![0.0; METRICS * 2];
        let mut set = |index: usize, result: Result<f64, i32>| match result {
            Ok(v) if v.is_finite() => {
                out[index * 2] = 0.0;
                out[index * 2 + 1] = v;
            }
            Ok(_) => out[index * 2] = INVALID,
            Err(e) => out[index * 2] = f64::from(e),
        };
        let numeric = |op, x, y, slot| {
            hardware::process([1, op, x, y], 0.0)
                .map(|v| v[slot])
                .map_err(|_| 6)
        };
        set(
            0,
            numeric(12, a[2], 0, 0).and_then(|v| if a[2] > 0 { Ok(v) } else { Err(6) }),
        );
        set(1, numeric(1, a[2], a[3], 1));
        set(2, numeric(1, a[2], a[3], 3));
        set(3, numeric(12, a[4], 0, 0));
        set(4, numeric(2, a[5], a[6], 0));
        set(5, numeric(3, a[7], 0, 0));
        set(6, numeric(4, a[8], 0, 0));
        set(7, numeric(5, a[9], 0, 0));
        set(8, cpu(a[0], a[1], a[10], a[11]));
        set(
            9,
            if real[0].is_finite() && real[0] >= 0.0 {
                Ok(real[0])
            } else {
                Err(6)
            },
        );
        Ok(out)
    } else {
        let count = header[2] as usize;
        let masks = &input[3..];
        if masks.iter().any(|m| *m < 0 || *m >= (1 << METRICS)) {
            return Err(6);
        }
        let mut out = vec![0.0; METRICS * 5];
        for metric in 0..METRICS {
            let mut valid = Vec::with_capacity(count);
            for row in 0..count {
                if masks[row] & (1 << metric) != 0 {
                    let v = real[row * METRICS + metric];
                    if !v.is_finite() {
                        return Err(6);
                    }
                    valid.push(v);
                }
            }
            if !valid.is_empty() {
                let result = &mut out[metric * 5..metric * 5 + 5];
                result[0] = valid.iter().copied().fold(f64::INFINITY, f64::min);
                result[1] = valid.iter().copied().fold(f64::NEG_INFINITY, f64::max);
                result[2] = crate::mean(&valid).map_err(|_| 6)?;
                result[3] = *valid.last().ok_or(6)?;
                result[4] = valid.len() as f64;
            }
        }
        Ok(out)
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    fn sample() -> Vec<i64> {
        vec![
            1,
            1,
            1,
            5_000_000_000,
            100,
            8 << 30,
            2 << 30,
            1 << 30,
            50,
            100,
            253,
            4200,
            -150000,
            0,
            0,
        ]
    }
    #[test]
    fn cpu_baseline_zero_backward_and_multicore() {
        assert_eq!(cpu(5, 0, i64::MIN, i64::MIN), Err(9));
        assert_eq!(cpu(0, 0, 0, 0), Err(6));
        assert_eq!(cpu(1, 0, 2, 0), Err(6));
        assert_eq!(cpu(2, 0, 1, 1), Err(6));
        assert_eq!(cpu(5_000_000_000, 0, 0, 0), Ok(0.0));
        assert_eq!(cpu(5_000_000_000, 10000, 0, 0), Ok(200.0));
        assert_eq!(cpu(i64::MAX, i64::MAX, 0, 0), Ok(100_000_000.0));
        assert_eq!(cpu(i64::MAX, 0, i64::MIN + 1, 0), Err(6));
    }
    #[test]
    fn validates_memory_battery_and_headroom_independently() {
        let mut a = sample();
        let out = process(&a, &[1.5]).unwrap();
        assert_eq!(out[3], 2.0);
        assert_eq!(out[5], 25.0);
        assert_eq!(out[11], 25.3);
        assert_eq!(out[15], -150.0);
        assert_eq!(out[19], 1.5);
        for (total, available) in [(0, 0), (2, 3), (-1, 0), (2, -1)] {
            a[5] = total;
            a[6] = available;
            assert_eq!(process(&a, &[0.0]).unwrap()[2], INVALID);
        }
        a = sample();
        a[5] = i64::MAX;
        a[6] = i64::MAX;
        assert_eq!(process(&a, &[0.0]).unwrap()[5], 100.0);
        for headroom in [f64::NAN, f64::INFINITY, -1.0] {
            let out = process(&sample(), &[headroom]).unwrap();
            assert_eq!(out[18], INVALID);
            assert_eq!(out[8], 0.0);
        }
    }
    #[test]
    fn bounded_summaries_missing_signed_and_extreme_values() {
        for count in [0, 1, 2, 120] {
            let mut input = vec![1, 2, count];
            input.extend(vec![1; count as usize]);
            let mut real = vec![0.0; count as usize * METRICS];
            for row in 0..count as usize {
                real[row * METRICS] = f64::MAX;
            }
            let out = process(&input, &real).unwrap();
            assert_eq!(out[4], count as f64);
            assert!(out.iter().all(|v| v.is_finite()));
            if count > 0 {
                assert_eq!(out[2], f64::MAX);
            }
            assert_eq!(out[9], 0.0);
        }
        let input = [1, 2, 3, 1, 0, 1];
        let mut real = vec![f64::NAN; 30];
        real[0] = -3.0;
        real[20] = 1.0;
        assert_eq!(
            &process(&input, &real).unwrap()[..5],
            &[-3.0, 1.0, -1.0, 1.0, 2.0]
        );
        real[0] = f64::INFINITY;
        assert_eq!(process(&input, &real), Err(6));
        assert_eq!(shape([1, 2, 121]), Err(3));
        assert_eq!(shape([1, 2, -1]), Err(3));
    }
    #[test]
    fn protocol_shapes_and_determinism() {
        assert_eq!(shape([2, 1, 1]), Err(8));
        assert_eq!(shape([1, 9, 1]), Err(7));
        assert_eq!(process(&[], &[]), Err(3));
        assert_eq!(process(&sample(), &[]), Err(3));
        assert_eq!(process(&sample(), &[0.0]), process(&sample(), &[0.0]));
    }
}
