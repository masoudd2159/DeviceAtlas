//! Version 1 numeric protocol. Fixed stack buffers; no platform APIs or locale logic.
#[derive(Debug, PartialEq, Eq)]
pub enum MetricError {
    Invalid,
    Operation,
    Version,
}

pub fn process(input: [i64; 4], real: f64) -> Result<[f64; 5], MetricError> {
    let [version, operation, a, b] = input;
    if version != 1 {
        return Err(MetricError::Version);
    }
    if !real.is_finite() {
        return Err(MetricError::Invalid);
    }
    let mut out = [0.0; 5];
    let invalid = || MetricError::Invalid;
    match operation {
        1 => {
            if a <= 0 || b < 0 || b > a {
                return Err(invalid());
            }
            let occupied = a.checked_sub(b).ok_or_else(invalid)?;
            out = [
                a as f64 / 1_073_741_824.0,
                b as f64 / 1_073_741_824.0,
                occupied as f64 / 1_073_741_824.0,
                b as f64 / a as f64 * 100.0,
                occupied as f64 / a as f64 * 100.0,
            ];
        }
        2 => {
            if b <= 0 || a < 0 || a > b {
                return Err(invalid());
            }
            out[0] = a as f64 / b as f64 * 100.0;
        }
        3 => {
            // Tenths Celsius from the framework. Reject below absolute zero.
            if !(-2731..=i64::from(i32::MAX)).contains(&a) {
                return Err(invalid());
            }
            out[0] = a as f64 / 10.0;
        }
        4 => {
            if a <= 0 {
                return Err(invalid());
            }
            out[0] = a as f64 / 1000.0;
        }
        5 => {
            // Signed microamps: positive charging, negative discharging. Never abs().
            if a == i64::MIN || a == i64::from(i32::MIN) {
                return Err(invalid());
            }
            out[0] = a as f64 / 1000.0;
        }
        6 | 7 => {
            if a < 0 {
                return Err(invalid());
            }
            out[0] = a as f64
                / if operation == 6 {
                    1000.0
                } else {
                    1_000_000_000.0
                };
        }
        8 => {
            if a <= 0 || b <= 0 || a > i64::from(i32::MAX) || b > i64::from(i32::MAX) {
                return Err(invalid());
            }
            let (mut x, mut y) = (a, b);
            while y != 0 {
                (x, y) = (y, x % y);
            }
            out[0] = (a / x) as f64;
            out[1] = (b / x) as f64;
        }
        9 | 13 => {
            if real < 0.0 || (operation == 13 && real == 0.0) {
                return Err(invalid());
            }
            out[0] = real;
        }
        10 | 11 => {
            if a < 0 || (operation == 10 && a == 0) || a > i64::from(i32::MAX) {
                return Err(invalid());
            }
            out[0] = a as f64;
        }
        12 => {
            if a < 0 {
                return Err(invalid());
            }
            out[0] = a as f64 / 1_073_741_824.0;
        }
        _ => return Err(MetricError::Operation),
    }
    Ok(out)
}

#[cfg(test)]
mod tests {
    use super::*;
    fn run(op: i64, a: i64, b: i64) -> Result<[f64; 5], MetricError> {
        process([1, op, a, b], 0.0)
    }
    #[test]
    fn capacities_and_relationships() {
        assert_eq!(
            run(1, 8 << 30, 2 << 30).unwrap(),
            [8.0, 2.0, 6.0, 25.0, 75.0]
        );
        for (a, b) in [(0, 0), (-1, 0), (2, 3), (2, -1)] {
            assert_eq!(run(1, a, b), Err(MetricError::Invalid));
        }
    }
    #[test]
    fn capacity_extremes_and_invariants() {
        for total in [1, 1024, 1 << 40, i64::MAX] {
            for available in [0, total / 2, total] {
                let v = run(1, total, available).unwrap();
                assert!(v.iter().all(|x| x.is_finite()));
                assert!((0.0..=100.0).contains(&v[3]));
                assert!((v[3] + v[4] - 100.0).abs() < 1e-10);
                assert_eq!(run(1, total, available).unwrap(), v);
            }
        }
    }
    #[test]
    fn battery_level_boundaries() {
        assert_eq!(run(2, 1, 4).unwrap()[0], 25.0);
        assert_eq!(run(2, 0, 100).unwrap()[0], 0.0);
        assert_eq!(run(2, i64::MAX, i64::MAX).unwrap()[0], 100.0);
        for (a, b) in [(1, 0), (-1, 100), (101, 100)] {
            assert!(run(2, a, b).is_err());
        }
    }
    #[test]
    fn temperatures_and_electrical_units() {
        assert_eq!(run(3, 253, 0).unwrap()[0], 25.3);
        assert_eq!(run(3, -2731, 0).unwrap()[0], -273.1);
        assert!(run(3, -2732, 0).is_err());
        assert_eq!(run(4, 4200, 0).unwrap()[0], 4.2);
        assert!(run(4, 0, 0).is_err());
        assert_eq!(run(5, -150000, 0).unwrap()[0], -150.0);
        assert_eq!(run(5, 0, 0).unwrap()[0], 0.0);
        assert!(run(5, i64::MIN, 0).is_err());
        assert!(run(5, i64::from(i32::MIN), 0).is_err());
        assert_eq!(run(6, 2500000, 0).unwrap()[0], 2500.0);
        assert_eq!(run(7, 1500000000, 0).unwrap()[0], 1.5);
        assert!(run(6, -1, 0).is_err());
        assert!(run(7, i64::MIN, 0).is_err());
    }
    #[test]
    fn display_and_real_numbers() {
        assert_eq!(&run(8, 1080, 2400).unwrap()[..2], &[9.0, 20.0]);
        for (a, b) in [(0, 2), (-1, 3), (2, 0), (i64::MAX, 1)] {
            assert!(run(8, a, b).is_err());
        }
        for x in [f64::NAN, f64::INFINITY, f64::NEG_INFINITY, -1.0] {
            assert!(process([1, 9, 0, 0], x).is_err());
        }
        assert!(process([1, 13, 0, 0], 0.0).is_err());
        assert_eq!(process([1, 9, 0, 0], 0.0).unwrap()[0], 0.0);
    }
    #[test]
    fn protocol_and_integer_units() {
        assert_eq!(process([2, 1, 1, 1], 0.0), Err(MetricError::Version));
        assert_eq!(run(99, 0, 0), Err(MetricError::Operation));
        assert!(run(10, 0, 0).is_err());
        assert_eq!(run(11, 0, 0).unwrap()[0], 0.0);
        assert!(run(11, i64::MAX, 0).is_err());
        assert_eq!(run(12, 1 << 30, 0).unwrap()[0], 1.0);
    }
}
