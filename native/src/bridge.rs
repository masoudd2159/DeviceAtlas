//! JNI status protocol: 0 success, 1 empty, 2 non-finite, 3 invalid buffer,
//! 4 JNI/allocation failure, 5 caught Rust panic. Output is written only on success.
use crate::{MeanError, mean};
use jni_sys::jlongArray;
use jni_sys::{JNIEnv, jdoubleArray, jint, jobject};
use std::panic::{AssertUnwindSafe, catch_unwind};

const INVALID_BUFFER: jint = 3;
const NATIVE_FAILURE: jint = 4;
const PANIC: jint = 5;

fn contain_panic(action: impl FnOnce() -> jint) -> jint {
    catch_unwind(AssertUnwindSafe(action)).unwrap_or(PANIC)
}

/// Bounded sensor v1. Four longs, up to 192 doubles, seven result doubles.
/// # Safety
/// JVM supplies live array handles on this thread and a valid JNI environment.
#[unsafe(no_mangle)]
pub unsafe extern "system" fn Java_io_github_masoudd2159_deviceatlas_data_nativecore_NativeBridge_processSensor(
    env: *mut JNIEnv,
    _receiver: jobject,
    input: jlongArray,
    real: jdoubleArray,
    output: jdoubleArray,
) -> jint {
    contain_panic(|| unsafe { invoke_sensor(env, input, real, output) })
}

unsafe fn invoke_sensor(
    env: *mut JNIEnv,
    input: jlongArray,
    real: jdoubleArray,
    output: jdoubleArray,
) -> jint {
    if env.is_null() || input.is_null() || real.is_null() || output.is_null() {
        return INVALID_BUFFER;
    }
    let table = unsafe { *env };
    if table.is_null() {
        return NATIVE_FAILURE;
    }
    let table = unsafe { &*table };
    let (
        Some(length),
        Some(read_long),
        Some(read_double),
        Some(write),
        Some(exception),
        Some(clear),
    ) = (
        table.GetArrayLength,
        table.GetLongArrayRegion,
        table.GetDoubleArrayRegion,
        table.SetDoubleArrayRegion,
        table.ExceptionCheck,
        table.ExceptionClear,
    )
    else {
        return NATIVE_FAILURE;
    };
    let failed = || unsafe {
        if exception(env) != 0 {
            clear(env);
            true
        } else {
            false
        }
    };
    // Preserve pending-exception semantics: never execute workload after failure.
    if failed() {
        return NATIVE_FAILURE;
    }
    for (array, expected) in [(input, 4), (output, 7)] {
        let size = unsafe { length(env, array) };
        if failed() {
            return NATIVE_FAILURE;
        }
        if size != expected {
            return INVALID_BUFFER;
        }
    }
    let mut header = [0_i64; 4];
    unsafe { read_long(env, input, 0, 4, header.as_mut_ptr()) };
    if failed() {
        return NATIVE_FAILURE;
    }
    let expected = match crate::diagnostics::shape(&header) {
        Ok(size) => size,
        Err(code) => return code,
    };
    let size = unsafe { length(env, real) };
    if failed() {
        return NATIVE_FAILURE;
    }
    if size != expected as i32 {
        return INVALID_BUFFER;
    }
    let mut values = [0.0_f64; 192];
    if expected > 0 {
        unsafe { read_double(env, real, 0, size, values.as_mut_ptr()) };
        if failed() {
            return NATIVE_FAILURE;
        }
    }
    let result = match crate::diagnostics::process(&header, &values[..expected]) {
        Ok(value) => value,
        Err(code) => return code,
    };
    if result.iter().any(|v| !v.is_finite()) {
        return NATIVE_FAILURE;
    }
    unsafe { write(env, output, 0, 7, result.as_ptr()) };
    if failed() { NATIVE_FAILURE } else { 0 }
}

/// Bounded benchmark v1. Six longs, zero to six doubles, ten result doubles.
/// # Safety
/// JVM supplies live array handles on this thread and a valid JNI environment.
#[unsafe(no_mangle)]
pub unsafe extern "system" fn Java_io_github_masoudd2159_deviceatlas_data_nativecore_NativeBridge_processBenchmark(
    env: *mut JNIEnv,
    _receiver: jobject,
    input: jlongArray,
    real: jdoubleArray,
    output: jdoubleArray,
) -> jint {
    contain_panic(|| unsafe { invoke_benchmark(env, input, real, output) })
}

unsafe fn invoke_benchmark(
    env: *mut JNIEnv,
    input: jlongArray,
    real: jdoubleArray,
    output: jdoubleArray,
) -> jint {
    if env.is_null() || input.is_null() || real.is_null() || output.is_null() {
        return INVALID_BUFFER;
    }
    let table = unsafe { *env };
    if table.is_null() {
        return NATIVE_FAILURE;
    }
    let table = unsafe { &*table };
    let (
        Some(length),
        Some(read_long),
        Some(read_double),
        Some(write),
        Some(exception),
        Some(clear),
    ) = (
        table.GetArrayLength,
        table.GetLongArrayRegion,
        table.GetDoubleArrayRegion,
        table.SetDoubleArrayRegion,
        table.ExceptionCheck,
        table.ExceptionClear,
    )
    else {
        return NATIVE_FAILURE;
    };
    let failed = || unsafe {
        if exception(env) != 0 {
            clear(env);
            true
        } else {
            false
        }
    };
    // Preserve pending-exception semantics: never execute workload after failure.
    if failed() {
        return NATIVE_FAILURE;
    }
    for (array, expected) in [(input, 6), (output, 10)] {
        let size = unsafe { length(env, array) };
        if failed() {
            return NATIVE_FAILURE;
        }
        if size != expected {
            return INVALID_BUFFER;
        }
    }
    let mut header = [0_i64; 6];
    unsafe { read_long(env, input, 0, 6, header.as_mut_ptr()) };
    if failed() {
        return NATIVE_FAILURE;
    }
    let expected = match crate::benchmark::shape(&header) {
        Ok(size) => size,
        Err(code) => return code,
    };
    let size = unsafe { length(env, real) };
    if failed() {
        return NATIVE_FAILURE;
    }
    if size != expected as i32 {
        return INVALID_BUFFER;
    }
    let mut values = [0.0_f64; 6];
    if expected > 0 {
        unsafe { read_double(env, real, 0, size, values.as_mut_ptr()) };
        if failed() {
            return NATIVE_FAILURE;
        }
    }
    let result = match crate::benchmark::process(&header, &values[..expected]) {
        Ok(value) => value,
        Err(code) => return code,
    };
    if result.iter().any(|v| !v.is_finite()) {
        return NATIVE_FAILURE;
    }
    unsafe { write(env, output, 0, 10, result.as_ptr()) };
    if failed() { NATIVE_FAILURE } else { 0 }
}

/// Monitoring v1. Shapes are validated from a three-long header before copying.
/// # Safety
/// JVM supplies live array handles and this thread's JNI environment.
#[unsafe(no_mangle)]
pub unsafe extern "system" fn Java_io_github_masoudd2159_deviceatlas_data_nativecore_NativeBridge_processMonitoring(
    env: *mut JNIEnv,
    _receiver: jobject,
    input: jlongArray,
    real: jdoubleArray,
    output: jdoubleArray,
) -> jint {
    contain_panic(|| unsafe { invoke_monitoring(env, input, real, output) })
}

unsafe fn invoke_monitoring(
    env: *mut JNIEnv,
    input: jlongArray,
    real: jdoubleArray,
    output: jdoubleArray,
) -> jint {
    if env.is_null() || input.is_null() || real.is_null() || output.is_null() {
        return INVALID_BUFFER;
    }
    let table = unsafe { *env };
    if table.is_null() {
        return NATIVE_FAILURE;
    }
    let table = unsafe { &*table };
    let (
        Some(length),
        Some(read_long),
        Some(read_double),
        Some(write),
        Some(exception),
        Some(clear),
    ) = (
        table.GetArrayLength,
        table.GetLongArrayRegion,
        table.GetDoubleArrayRegion,
        table.SetDoubleArrayRegion,
        table.ExceptionCheck,
        table.ExceptionClear,
    )
    else {
        return NATIVE_FAILURE;
    };
    let failed = || unsafe {
        if exception(env) != 0 {
            clear(env);
            true
        } else {
            false
        }
    };
    let size = unsafe { length(env, input) };
    if failed() {
        return NATIVE_FAILURE;
    }
    if size < 3 {
        return INVALID_BUFFER;
    }
    let mut header = [0_i64; 3];
    unsafe { read_long(env, input, 0, 3, header.as_mut_ptr()) };
    if failed() {
        return NATIVE_FAILURE;
    }
    let (longs, doubles, outputs) = match crate::monitoring::shape(header) {
        Ok(shape) => shape,
        Err(status) => return status,
    };
    for (array, expected) in [(input, longs), (real, doubles), (output, outputs)] {
        let actual = unsafe { length(env, array) };
        if failed() {
            return NATIVE_FAILURE;
        }
        if actual != expected as i32 {
            return INVALID_BUFFER;
        }
    }
    // Fixed maximum buffers; sizes/counts validated before reading any payload.
    let mut integers = [0_i64; 123];
    let mut floating = [0.0_f64; 1200];
    unsafe { read_long(env, input, 0, longs as i32, integers.as_mut_ptr()) };
    if failed() {
        return NATIVE_FAILURE;
    }
    if doubles > 0 {
        unsafe { read_double(env, real, 0, doubles as i32, floating.as_mut_ptr()) };
        if failed() {
            return NATIVE_FAILURE;
        }
    }
    let result = match crate::monitoring::process(&integers[..longs], &floating[..doubles]) {
        Ok(result) => result,
        Err(status) => return status,
    };
    if result.len() != outputs || result.iter().any(|v| !v.is_finite()) {
        return NATIVE_FAILURE;
    }
    unsafe { write(env, output, 0, outputs as i32, result.as_ptr()) };
    if failed() { NATIVE_FAILURE } else { 0 }
}

/// Versioned hardware ABI. long[4], double[1] -> double[5]. Statuses 6 invalid
/// numeric data, 7 unsupported operation, 8 protocol version; 3/4/5 as for mean.
/// # Safety
/// The JVM supplies valid array handles and an attached thread's environment.
#[unsafe(no_mangle)]
pub unsafe extern "system" fn Java_io_github_masoudd2159_deviceatlas_data_nativecore_NativeBridge_processHardware(
    env: *mut JNIEnv,
    _receiver: jobject,
    input: jlongArray,
    real: jdoubleArray,
    output: jdoubleArray,
) -> jint {
    contain_panic(|| unsafe { invoke_hardware(env, input, real, output) })
}

unsafe fn invoke_hardware(
    env: *mut JNIEnv,
    input: jlongArray,
    real: jdoubleArray,
    output: jdoubleArray,
) -> jint {
    if env.is_null() || input.is_null() || real.is_null() || output.is_null() {
        return INVALID_BUFFER;
    }
    let table = unsafe { *env };
    if table.is_null() {
        return NATIVE_FAILURE;
    }
    let table = unsafe { &*table };
    let (
        Some(length),
        Some(read_long),
        Some(read_double),
        Some(write),
        Some(exception),
        Some(clear),
    ) = (
        table.GetArrayLength,
        table.GetLongArrayRegion,
        table.GetDoubleArrayRegion,
        table.SetDoubleArrayRegion,
        table.ExceptionCheck,
        table.ExceptionClear,
    )
    else {
        return NATIVE_FAILURE;
    };
    let failed = || unsafe {
        if exception(env) != 0 {
            clear(env);
            true
        } else {
            false
        }
    };
    for (array, expected) in [(input, 4), (real, 1), (output, 5)] {
        let size = unsafe { length(env, array) };
        if failed() {
            return NATIVE_FAILURE;
        }
        if size != expected {
            return INVALID_BUFFER;
        }
    }
    let mut values = [0_i64; 4];
    let mut floating = [0.0_f64; 1];
    unsafe { read_long(env, input, 0, 4, values.as_mut_ptr()) };
    if failed() {
        return NATIVE_FAILURE;
    }
    unsafe { read_double(env, real, 0, 1, floating.as_mut_ptr()) };
    if failed() {
        return NATIVE_FAILURE;
    }
    let result = match crate::hardware::process(values, floating[0]) {
        Ok(value) => value,
        Err(crate::hardware::MetricError::Invalid) => return 6,
        Err(crate::hardware::MetricError::Operation) => return 7,
        Err(crate::hardware::MetricError::Version) => return 8,
    };
    unsafe { write(env, output, 0, 5, result.as_ptr()) };
    if failed() { NATIVE_FAILURE } else { 0 }
}

/// # Safety
/// Called by the JVM on its attached thread. Non-null JNI handles and the
/// environment must be valid for this call; no handle or pointer is retained.
#[unsafe(no_mangle)]
pub unsafe extern "system" fn Java_io_github_masoudd2159_deviceatlas_data_nativecore_NativeBridge_mean(
    env: *mut JNIEnv,
    _receiver: jobject,
    input: jdoubleArray,
    output: jdoubleArray,
) -> jint {
    // Both debug and release use unwinding, so Rust panics are contained here.
    // Caller memory is accessed only through checked JNI region operations.
    contain_panic(|| unsafe { invoke(env, input, output) })
}

unsafe fn invoke(env: *mut JNIEnv, input: jdoubleArray, output: jdoubleArray) -> jint {
    if env.is_null() || input.is_null() || output.is_null() {
        return INVALID_BUFFER;
    }
    // SAFETY: JNI supplies an environment with its function table for this thread.
    let table = unsafe { *env };
    if table.is_null() {
        return NATIVE_FAILURE;
    }
    let table = unsafe { &*table };
    let (Some(length), Some(read), Some(write), Some(exception), Some(clear)) = (
        table.GetArrayLength,
        table.GetDoubleArrayRegion,
        table.SetDoubleArrayRegion,
        table.ExceptionCheck,
        table.ExceptionClear,
    ) else {
        return NATIVE_FAILURE;
    };
    // Region APIs copy into Rust-owned memory and acquire no pinned JNI resources.
    // Check/clear JNI exceptions immediately and map them to an explicit status.
    let failed = || unsafe {
        if exception(env) != 0 {
            clear(env);
            true
        } else {
            false
        }
    };
    let output_length = unsafe { length(env, output) };
    if failed() {
        return NATIVE_FAILURE;
    }
    if output_length != 1 {
        return INVALID_BUFFER;
    }
    let count = unsafe { length(env, input) };
    if failed() {
        return NATIVE_FAILURE;
    }
    if count == 0 {
        return 1;
    }
    // Bound legacy mean allocation too (8 MiB maximum); status 3 remains invalid buffer.
    if !(0..=1_048_576).contains(&count) {
        return INVALID_BUFFER;
    }

    let mut values = Vec::<f64>::new();
    if values.try_reserve_exact(count as usize).is_err() {
        return NATIVE_FAILURE;
    }
    values.resize(count as usize, 0.0);
    // SAFETY: values owns count initialized doubles; JNI checks array bounds.
    unsafe { read(env, input, 0, count, values.as_mut_ptr()) };
    if failed() {
        return NATIVE_FAILURE;
    }
    let value = match mean(&values) {
        Ok(value) => value,
        Err(MeanError::EmptyInput) => return 1,
        Err(MeanError::NonFiniteInput) => return 2,
    };
    // SAFETY: output has exactly one element, and value is live for this copy.
    unsafe { write(env, output, 0, 1, &value) };
    if failed() { NATIVE_FAILURE } else { 0 }
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn panic_containment_and_null_environment() {
        assert_eq!(contain_panic(|| panic!("injected boundary panic")), PANIC);
        assert_eq!(contain_panic(|| 0), 0);
        // A null environment must be rejected before dereferencing any handles.
        let env = std::ptr::null_mut();
        let null = std::ptr::null_mut();
        assert_eq!(
            unsafe { invoke_hardware(env, null, null, null) },
            INVALID_BUFFER
        );
        assert_eq!(unsafe { invoke(env, null, null) }, INVALID_BUFFER);
        assert_eq!(
            unsafe { invoke_benchmark(env, null, null, null) },
            INVALID_BUFFER
        );
        assert_eq!(
            unsafe { invoke_monitoring(env, null, null, null) },
            INVALID_BUFFER
        );
        assert_eq!(
            unsafe { invoke_sensor(env, null, null, null) },
            INVALID_BUFFER
        );
    }
}
