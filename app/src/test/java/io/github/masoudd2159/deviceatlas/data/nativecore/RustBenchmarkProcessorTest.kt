package io.github.masoudd2159.deviceatlas.data.nativecore

import io.github.masoudd2159.deviceatlas.foundation.benchmark.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RustBenchmarkProcessorTest {
    @Test fun boundedChunkWireAndVerifiedActualWork() = runTest {
        val processor=RustBenchmarkProcessor(StandardTestDispatcher(testScheduler),{}, { input,real,out ->
            assertArrayEquals(longArrayOf(1,1,3,64,5_000,65_536),input); assertTrue(real.isEmpty())
            doubleArrayOf(1.0,3.0,1.0,65_536.0,1_000_000.0,62.5,12.0,34.0,1.0,0.0).copyInto(out); 0
        })
        val result=processor.chunk(Workload.MEMORY,true)
        assertEquals(65_536L,result.run.work); assertEquals(1_000_000L,result.run.nanos)
        assertEquals(12L,result.checksumHigh)
    }
    @Test fun summaryUsesOnlyCompletedRunsAndValidatesProtocol() = runTest {
        val runs=listOf(BenchmarkRun(256,1_000_000))
        val processor=RustBenchmarkProcessor(StandardTestDispatcher(testScheduler),{}, { input,real,out ->
            assertArrayEquals(longArrayOf(1,2,1,1,0,0),input)
            assertArrayEquals(doubleArrayOf(256.0,1_000_000.0),real,0.0)
            doubleArrayOf(1.0,1.0,1.0,256.0,1_000_000.0,256_000.0,256_000.0,256_000.0,0.0,1.0).copyInto(out); 0
        })
        assertEquals(1,processor.summarize(Workload.INTEGER,runs).runs.size)
    }
    @Test fun errorNonfiniteUnwrittenAndImpossibleOutputsNeverAppearAsSuccess() = runTest {
        for (status in listOf(3,4,5,6,7,8)) {
            val p=RustBenchmarkProcessor(StandardTestDispatcher(testScheduler),{}, { _,_,_ -> status })
            try { p.chunk(Workload.INTEGER,false); fail() } catch (error: BenchmarkFailure) {
                assertEquals(if(status==6) BenchmarkError.INVALID_RESULT else BenchmarkError.NATIVE_FAILED,error.reason)
            }
        }
        for ((index,value) in listOf(0 to 2.0,1 to 9.0,3 to 0.0,3 to 255.0,4 to -1.0,5 to Double.NaN,6 to -1.0,8 to 0.0)) {
            val p=RustBenchmarkProcessor(StandardTestDispatcher(testScheduler),{}, { _,_,out ->
                doubleArrayOf(1.0,1.0,1.0,256.0,1_000_000.0,256_000.0,12.0,34.0,1.0,0.0).copyInto(out)
                out[index]=value; 0
            })
            try { p.chunk(Workload.INTEGER,false); fail() } catch (error: BenchmarkFailure) { assertEquals(BenchmarkError.INVALID_RESULT,error.reason) }
        }
    }
    @Test fun linkageAndCancellationRemainDistinct() = runTest {
        val p=RustBenchmarkProcessor(StandardTestDispatcher(testScheduler),{ throw UnsatisfiedLinkError() }, { _,_,_ -> 0 })
        try { p.chunk(Workload.INTEGER,false); fail() } catch (e: BenchmarkFailure) { assertEquals(BenchmarkError.NATIVE_UNAVAILABLE,e.reason) }
        val canceled=RustBenchmarkProcessor(StandardTestDispatcher(testScheduler),{ throw CancellationException() }, { _,_,_ -> 0 })
        try { canceled.chunk(Workload.INTEGER,false); fail() } catch (_: CancellationException) { }
    }
}
