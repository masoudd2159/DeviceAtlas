package io.github.masoudd2159.deviceatlas;

import android.app.Activity;
import android.app.Instrumentation;
import android.os.Bundle;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/** Tiny real release probe using only the Android framework and the kept JNI ABI.
 * Does not widen production R8 rules or require unminified Kotlin runtime names. */
public final class BenchmarkReleaseInstrumentation extends Instrumentation {
    @Override public void onCreate(Bundle arguments) { super.onCreate(arguments); start(); }

    @Override public void onStart() {
        Bundle result = new Bundle();
        try {
            System.loadLibrary("deviceatlas_core");
            Class<?> bridge = getTargetContext().getClassLoader().loadClass(
                    "io.github.masoudd2159.deviceatlas.data.nativecore.NativeBridge");
            Object receiver = null;
            for (Field field : bridge.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers()) && field.getType() == bridge) {
                    field.setAccessible(true);
                    receiver = field.get(null);
                    break;
                }
            }
            check(receiver != null, "Missing bridge receiver");
            Method chunk = bridge.getDeclaredMethod("processBenchmark", long[].class, double[].class, double[].class);
            for (long workload = 1; workload <= 3; workload++) {
                long cap = workload == 3 ? 1 : 256;
                long bytes = workload == 3 ? 4096 : 0;
                double[] output = new double[10];
                Object status = chunk.invoke(receiver, new long[]{1, 1, workload, cap, 20_000, bytes}, new double[0], output);
                check(Integer.valueOf(0).equals(status), "Native chunk failed");
                for (double value : output) check(Double.isFinite(value), "Nonfinite output");
                check(output[3] == (workload == 3 ? 4096 : 256) && output[4] > 0 && output[5] > 0 && output[8] == 1,
                        "Invalid measured output");
                Bundle observation = new Bundle();
                observation.putLong("workload_v1", workload);
                observation.putLong("completed_work", (long) output[3]);
                observation.putLong("active_nanos", (long) output[4]);
                observation.putDouble("throughput", output[5]);
                sendStatus(0, observation);
            }
            Method mean = bridge.getDeclaredMethod("mean", double[].class, double[].class);
            double[] output = new double[1];
            check(Integer.valueOf(0).equals(mean.invoke(receiver, new double[]{2, 4, 6}, output)) && output[0] == 4,
                    "Legacy mean failed");
            Method hardware = bridge.getDeclaredMethod("processHardware", long[].class, double[].class, double[].class);
            double[] capacity = new double[5];
            check(Integer.valueOf(0).equals(hardware.invoke(receiver, new long[]{1, 1, 2048, 1024}, new double[]{0}, capacity))
                    && capacity[3] == 50, "Legacy hardware failed");
            Method monitoring = bridge.getDeclaredMethod("processMonitoring", long[].class, double[].class, double[].class);
            double[] summaries = new double[50];
            check(Integer.valueOf(0).equals(monitoring.invoke(receiver, new long[]{1, 2, 0}, new double[0], summaries)),
                    "Legacy monitoring failed");
            Method sensor = bridge.getDeclaredMethod("processSensor", long[].class, double[].class, double[].class);
            double[] sensorSummary = new double[7];
            check(Integer.valueOf(0).equals(sensor.invoke(receiver, new long[]{1, 1, 3, 1},
                    new double[]{3, 4, 12}, sensorSummary)) && sensorSummary[5] == 13,
                    "Sensor vector analysis failed");
            check(Integer.valueOf(0).equals(sensor.invoke(receiver, new long[]{1, 1, 1, 2},
                    new double[]{0, 10}, sensorSummary)) && sensorSummary[4] == 5,
                    "Sensor scalar analysis failed");
            result.putInt("tests", 1);
            result.putInt("failures", 0);
            result.putString("stream", "OK (1 framework release probe): sensor analysis, three real workloads and all legacy JNI entry points\n");
            finish(Activity.RESULT_OK, result);
        } catch (Throwable failure) {
            result.putInt("tests", 1);
            result.putInt("failures", 1);
            result.putString("stream", android.util.Log.getStackTraceString(failure));
            finish(Activity.RESULT_CANCELED, result);
        }
    }
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
