//! Pure mathematics and a small, synchronous JNI boundary.
mod bridge;
pub mod hardware;

#[derive(Debug, PartialEq, Eq)]
pub enum MeanError {
    EmptyInput,
    NonFiniteInput,
}

/// Arithmetic mean of a non-empty finite slice.
///
/// Prefer compensated summation in the original units to retain small residuals
/// after cancellation. If a partial sum overflows, divide terms by the count
/// before summing. A normalized fallback handles rounding overflow near f64::MAX.
/// As with other f64 arithmetic, results are subject to floating-point rounding.
pub fn mean(values: &[f64]) -> Result<f64, MeanError> {
    if values.is_empty() {
        return Err(MeanError::EmptyInput);
    }
    let mut scale = 0.0_f64;
    let mut lower = values[0];
    let mut upper = values[0];
    for &value in values {
        if !value.is_finite() {
            return Err(MeanError::NonFiniteInput);
        }
        scale = scale.max(value.abs());
        lower = lower.min(value);
        upper = upper.max(value);
    }
    if scale == 0.0 {
        return Ok(0.0);
    }
    let count = values.len() as f64;
    if let Some(sum) = compensated_sum(values, 1.0) {
        return Ok((sum / count).clamp(lower, upper));
    }
    if let Some(average) = compensated_sum(values, count) {
        return Ok(average.clamp(lower, upper));
    }

    // Rounding can overflow even a mathematically bounded average at f64::MAX.
    // Normalization and clamping keep that final fallback finite.
    let mut sum = 0.0_f64;
    let mut correction = 0.0_f64;
    for &value in values {
        let term = (value / scale) / count;
        let next = sum + term;
        correction += if sum.abs() >= term.abs() {
            (sum - next) + term
        } else {
            (term - next) + sum
        };
        sum = next;
    }
    Ok(((sum + correction).clamp(-1.0, 1.0) * scale).clamp(lower, upper))
}

fn compensated_sum(values: &[f64], divisor: f64) -> Option<f64> {
    let mut sum = 0.0_f64;
    let mut correction = 0.0_f64;
    for &value in values {
        let term = value / divisor;
        let next = sum + term;
        if !next.is_finite() {
            return None;
        }
        correction += if sum.abs() >= term.abs() {
            (sum - next) + term
        } else {
            (term - next) + sum
        };
        if !correction.is_finite() {
            return None;
        }
        sum = next;
    }
    let result = sum + correction;
    result.is_finite().then_some(result)
}

#[cfg(test)]
mod tests {
    use super::*;

    fn near(actual: f64, expected: f64) {
        assert!(
            (actual - expected).abs() <= 1e-12 * expected.abs().max(1.0),
            "{actual} != {expected}"
        );
    }

    #[test]
    fn positive_values() {
        near(mean(&[2.0, 4.0, 6.0]).unwrap(), 4.0);
    }
    #[test]
    fn negative_values() {
        near(mean(&[-2.0, -4.0, -6.0]).unwrap(), -4.0);
    }
    #[test]
    fn mixed_values() {
        near(mean(&[-4.0, 2.0, 8.0]).unwrap(), 2.0);
    }
    #[test]
    fn single_value() {
        assert_eq!(mean(&[7.25]), Ok(7.25));
    }
    #[test]
    fn empty_input() {
        assert_eq!(mean(&[]), Err(MeanError::EmptyInput));
    }
    #[test]
    fn non_finite_input() {
        for value in [f64::NAN, f64::INFINITY, f64::NEG_INFINITY] {
            assert_eq!(mean(&[1.0, value]), Err(MeanError::NonFiniteInput));
        }
    }
    #[test]
    fn maximum_values_do_not_overflow() {
        for count in 2..=64 {
            assert_eq!(mean(&vec![f64::MAX; count]), Ok(f64::MAX));
            assert_eq!(mean(&vec![-f64::MAX; count]), Ok(-f64::MAX));
        }
    }
    #[test]
    fn opposite_extremes_cancel() {
        assert_eq!(mean(&[f64::MAX, -f64::MAX]), Ok(0.0));
    }
    #[test]
    fn compensation_preserves_small_terms() {
        near(mean(&[1e16, 1.0, -1e16]).unwrap(), 1.0 / 3.0);
    }
    #[test]
    fn preserves_small_residual_between_extremes() {
        assert_eq!(mean(&[f64::MAX, 1e-15, -f64::MAX]), Ok(1e-15 / 3.0));
    }
    #[test]
    fn preserves_small_residual_when_partial_sum_overflows() {
        assert_eq!(
            mean(&[f64::MAX, f64::MAX, -f64::MAX, -f64::MAX, 1e-15]),
            Ok(1e-15 / 5.0)
        );
    }
    #[test]
    fn subnormal_and_zero() {
        let smallest = f64::from_bits(1);
        assert_eq!(mean(&[smallest]), Ok(smallest));
        assert_eq!(mean(&[0.0, -0.0]), Ok(0.0));
    }
}
