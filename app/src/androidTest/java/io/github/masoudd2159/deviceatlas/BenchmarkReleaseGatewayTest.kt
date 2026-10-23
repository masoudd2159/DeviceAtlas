package io.github.masoudd2159.deviceatlas

import android.os.Bundle
import androidx.test.platform.app.InstrumentationRegistry
import java.lang.reflect.Modifier
import org.junit.Assert.*
import org.junit.Test

/** Exercises kept ABI names independent of Kotlin object field names. The
 * framework-only BenchmarkReleaseInstrumentation supports minified targets. */
class BenchmarkReleaseGatewayTest {
    @Test fun optimizedGatewayExecutesAllThreeWorkloadsAndLegacyMean() {
        System.loadLibrary("deviceatlas_core")
        // R8 preserves native ABI names, but may rename the Kotlin INSTANCE
        // field. Discover its receiver by type so this test needs no wider keep rule.
        val bridge=Class.forName("io.github.masoudd2159.deviceatlas.data.nativecore.NativeBridge")
        val field=bridge.declaredFields.first { Modifier.isStatic(it.modifiers) && it.type==bridge }
        field.isAccessible=true
        val receiver=field.get(null)
        val benchmark=bridge.getDeclaredMethod("processBenchmark",LongArray::class.java,DoubleArray::class.java,DoubleArray::class.java)
        val mean=bridge.getDeclaredMethod("mean",DoubleArray::class.java,DoubleArray::class.java)
        for (workload in 1L..3L) {
            val cap=if(workload==3L) 1L else 256L
            val bytes=if(workload==3L) 4096L else 0L
            val out=DoubleArray(10)
            assertEquals(0,benchmark.invoke(receiver,longArrayOf(1,1,workload,cap,20_000,bytes),doubleArrayOf(),out))
            assertTrue(out.all { it.isFinite() }); assertTrue(out[3]>0); assertTrue(out[4]>0); assertEquals(1.0,out[8],0.0)
            InstrumentationRegistry.getInstrumentation().sendStatus(0,Bundle().apply {
                putString("workload_v1",workload.toString()); putString("completed_work",out[3].toLong().toString())
                putString("active_nanos",out[4].toLong().toString()); putString("throughput",out[5].toString())
            })
        }
        val out=DoubleArray(1)
        assertEquals(0,mean.invoke(receiver,doubleArrayOf(2.0,4.0,6.0),out)); assertEquals(4.0,out[0],0.0)
    }
}
