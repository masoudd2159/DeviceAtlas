package io.github.masoudd2159.deviceatlas

import io.github.masoudd2159.deviceatlas.data.nativecore.NativeBridge
import io.github.masoudd2159.deviceatlas.data.nativecore.RustBenchmarkProcessor
import io.github.masoudd2159.deviceatlas.foundation.benchmark.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class BenchmarkIntegrationTest {
    @Test fun tinyRealNativeWorkloadsAndSummaryPreserveLegacyJni() = runBlocking {
        System.loadLibrary("deviceatlas_core")
        for (workload in Workload.entries) {
            val output=DoubleArray(10)
            // One 256-step block or one 4 KiB copy: test budgets cannot raise production bounds.
            val count=if(workload==Workload.MEMORY) 1L else 256L
            val bytes=if(workload==Workload.MEMORY) 4096L else 0L
            assertEquals(0,NativeBridge.processBenchmark(longArrayOf(1,1,workload.wire,count,20_000,bytes),doubleArrayOf(),output))
            assertTrue(output.all { it.isFinite() }); assertTrue(output[3]>0); assertTrue(output[4]>0); assertTrue(output[5]>0)
            val summary=RustBenchmarkProcessor(Dispatchers.Default).summarize(workload,listOf(BenchmarkRun(output[3].toLong(),output[4].toLong())))
            assertEquals(1,summary.runs.size); assertTrue(summary.median>0); assertEquals(0.0,summary.variationPercent,0.0)
        }
        val mean=DoubleArray(1); assertEquals(0,NativeBridge.mean(doubleArrayOf(2.0,4.0,6.0),mean)); assertEquals(4.0,mean[0],0.0)
        val hardware=DoubleArray(5)
        assertEquals(0,NativeBridge.processHardware(longArrayOf(1,1,2048,1024),doubleArrayOf(0.0),hardware))
    }
    @Test fun malformedJniCallsLeaveOutputUntouched() {
        System.loadLibrary("deviceatlas_core")
        val out=DoubleArray(10) { 123.0 }; val valid=longArrayOf(1,1,1,256,1000,0)
        assertEquals(3,NativeBridge.processBenchmark(null,doubleArrayOf(),out))
        assertEquals(3,NativeBridge.processBenchmark(valid,null,out))
        assertEquals(3,NativeBridge.processBenchmark(valid,doubleArrayOf(),null))
        for(size in listOf(0,5,7,100)) assertEquals(3,NativeBridge.processBenchmark(LongArray(size),doubleArrayOf(),out))
        assertEquals(8,NativeBridge.processBenchmark(longArrayOf(2,1,1,256,1000,0),doubleArrayOf(),out))
        assertEquals(7,NativeBridge.processBenchmark(longArrayOf(1,9,1,256,1000,0),doubleArrayOf(),out))
        assertEquals(6,NativeBridge.processBenchmark(longArrayOf(1,1,3,1,1000,4_194_305),doubleArrayOf(),out))
        assertEquals(6,NativeBridge.processBenchmark(longArrayOf(1,1,1,Long.MAX_VALUE,1000,0),doubleArrayOf(),out))
        assertEquals(6,NativeBridge.processBenchmark(longArrayOf(1,1,1,256,20_001,0),doubleArrayOf(),out))
        assertEquals(3,NativeBridge.processBenchmark(valid,doubleArrayOf(0.0),out))
        assertEquals(3,NativeBridge.processBenchmark(valid,doubleArrayOf(),DoubleArray(11)))
        assertEquals(6,NativeBridge.processBenchmark(longArrayOf(1,2,1,1,0,0),doubleArrayOf(1.0,0.0),out))
        assertEquals(6,NativeBridge.processBenchmark(longArrayOf(1,2,1,1,0,0),doubleArrayOf(Double.NaN,1.0),out))
        assertTrue(out.all { it==123.0 })
    }
}
