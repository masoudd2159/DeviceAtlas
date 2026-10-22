//! JNI status protocol: 0 success, 1 empty, 2 non-finite, 3 invalid buffer,
//! 4 JNI/allocation failure, 5 caught Rust panic. Output is written only on success.
use crate::{MeanError, mean};
use jni_sys::{JNIEnv, jdoubleArray, jint, jobject};
use std::panic::{AssertUnwindSafe, catch_unwind};

const INVALID_BUFFER: jint = 3;
const NATIVE_FAILURE: jint = 4;
const PANIC: jint = 5;

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
    catch_unwind(AssertUnwindSafe(|| unsafe { invoke(env, input, output) })).unwrap_or(PANIC)
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
    if count < 0 {
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
