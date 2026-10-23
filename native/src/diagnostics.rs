//! Sensor v1: [version, operation=1, dimensions=1|3, count=1..64].
//! Output: [version, count, min, max, mean, latest, range]. Vector statistics
//! describe Euclidean magnitudes. Invalid input leaves caller output untouched.
pub fn shape(header: &[i64]) -> Result<usize, i32> {
    if header.len() != 4 || header[0] != 1 || header[1] != 1 {
        return Err(6);
    }
    if !matches!(header[2], 1 | 3) || !(1..=64).contains(&header[3]) {
        return Err(3);
    }
    Ok((header[2] * header[3]) as usize)
}

pub fn magnitude(vector: &[f64]) -> Result<f64, i32> {
    if vector.len() != 3 {
        return Err(3);
    }
    if vector.iter().any(|v| !v.is_finite()) {
        return Err(2);
    }
    let result = vector[0].hypot(vector[1]).hypot(vector[2]);
    result.is_finite().then_some(result).ok_or(2)
}

pub fn process(header: &[i64], samples: &[f64]) -> Result<[f64; 7], i32> {
    let expected = shape(header)?;
    if samples.len() != expected {
        return Err(3);
    }
    if samples.iter().any(|v| !v.is_finite()) {
        return Err(2);
    }
    let count = header[3] as usize;
    let mut values = [0.0_f64; 64];
    for (index, sample) in samples.chunks_exact(header[2] as usize).enumerate() {
        values[index] = if header[2] == 3 {
            magnitude(sample)?
        } else {
            sample[0]
        };
    }
    let values = &values[..count];
    let minimum = values.iter().copied().fold(f64::INFINITY, f64::min);
    let maximum = values.iter().copied().fold(f64::NEG_INFINITY, f64::max);
    let range = maximum - minimum;
    if !range.is_finite() {
        return Err(2);
    }
    let average = crate::mean(values).map_err(|_| 2)?;
    Ok([
        1.0,
        count as f64,
        minimum,
        maximum,
        average,
        values[count - 1],
        range,
    ])
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn scalar_statistics() {
        assert_eq!(
            process(&[1, 1, 1, 3], &[-2.0, 0.0, 5.0]),
            Ok([1.0, 3.0, -2.0, 5.0, 1.0, 5.0, 7.0])
        );
        assert_eq!(
            process(&[1, 1, 1, 1], &[0.0]),
            Ok([1.0, 1.0, 0.0, 0.0, 0.0, 0.0, 0.0])
        );
        assert_eq!(process(&[1, 1, 1, 1], &[-3.0]).unwrap()[5], -3.0);
    }
    #[test]
    fn bounds_and_missing() {
        assert_eq!(process(&[1, 1, 1, 0], &[]), Err(3));
        assert_eq!(process(&[1, 1, 1, 64], &[2.0; 64]).unwrap()[1], 64.0);
        assert_eq!(process(&[1, 1, 1, 65], &[2.0; 65]), Err(3));
        assert_eq!(process(&[1, 1, 1, 2], &[2.0]), Err(3));
        assert_eq!(process(&[1, 1, 1, 1], &[2.0, 3.0]), Err(3));
    }
    #[test]
    fn protocol() {
        for header in [&[2, 1, 1, 1][..], &[1, 2, 1, 1], &[], &[1, 1, 1]] {
            assert_eq!(shape(header), Err(6));
        }
        for dimensions in [-1, 0, 2, 4, i64::MAX] {
            assert_eq!(shape(&[1, 1, dimensions, 1]), Err(3));
        }
        assert_eq!(shape(&[1, 1, 3, i64::MAX]), Err(3));
    }
    #[test]
    fn nonfinite_never_becomes_zero() {
        for value in [f64::NAN, f64::INFINITY, f64::NEG_INFINITY] {
            assert_eq!(process(&[1, 1, 1, 1], &[value]), Err(2));
            assert_eq!(magnitude(&[1.0, value, 2.0]), Err(2));
        }
    }
    #[test]
    fn vectors() {
        assert_eq!(magnitude(&[0.0, 0.0, 0.0]), Ok(0.0));
        assert_eq!(magnitude(&[1.0, 0.0, 0.0]), Ok(1.0));
        assert_eq!(magnitude(&[-3.0, 4.0, 12.0]), Ok(13.0));
        assert_eq!(magnitude(&[1.0, 2.0]), Err(3));
        assert_eq!(process(&[1, 1, 3, 1], &[3.0, 4.0]), Err(3));
        assert_eq!(process(&[1, 1, 3, 1], &[3.0, 4.0, 12.0]).unwrap()[5], 13.0);
    }
    #[test]
    fn extremes_do_not_overflow_silently() {
        assert!(magnitude(&[1e300, 1e300, 1e300]).unwrap().is_finite());
        assert!(magnitude(&[1e-300, 1e-300, 0.0]).unwrap() > 0.0);
        assert_eq!(magnitude(&[f64::MAX; 3]), Err(2));
        assert_eq!(process(&[1, 1, 1, 2], &[-f64::MAX, f64::MAX]), Err(2));
        let result = process(&[1, 1, 1, 64], &[f64::MAX; 64]).unwrap();
        assert_eq!(result[4], f64::MAX);
        assert!(result.iter().all(|v| v.is_finite()));
    }
}
