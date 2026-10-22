package io.github.masoudd2159.deviceatlas.presentation.home

import androidx.lifecycle.ViewModelStore
import io.github.masoudd2159.deviceatlas.foundation.MeanFailure
import io.github.masoudd2159.deviceatlas.foundation.MeanResult
import io.github.masoudd2159.deviceatlas.foundation.NativeMath
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test fun startsLoadingThenPublishesInjectedResult() = runTest(dispatcher) {
        var supplied: DoubleArray? = null
        val model = HomeViewModel(NativeMath { supplied = it; MeanResult.Success(4.0) })
        assertEquals(HomeUiState.Loading, model.uiState.value)
        advanceUntilIdle()
        assertArrayEquals(doubleArrayOf(2.0, 4.0, 6.0), supplied, 0.0)
        assertEquals(HomeUiState.Ready(4.0), model.uiState.value)
    }

    @Test fun mapsEveryTypedFailure() = runTest(dispatcher) {
        MeanFailure.entries.forEach { reason ->
            val model = HomeViewModel(NativeMath { MeanResult.Failure(reason) })
            advanceUntilIdle()
            assertEquals(HomeUiState.Unavailable(reason), model.uiState.value)
        }
    }

    @Test fun preservesDependencyResultInsteadOfHardcodingMean() = runTest(dispatcher) {
        val model = HomeViewModel(NativeMath { MeanResult.Success(7.0) })
        advanceUntilIdle()
        assertEquals(HomeUiState.Ready(7.0), model.uiState.value)
    }

    @Test fun mapsLinkageFailure() = runTest(dispatcher) {
        val model = HomeViewModel(NativeMath { throw UnsatisfiedLinkError("missing") })
        advanceUntilIdle()
        assertEquals(HomeUiState.Unavailable(MeanFailure.LIBRARY_UNAVAILABLE), model.uiState.value)
    }

    @Test fun mapsUnexpectedDependencyFailure() = runTest(dispatcher) {
        val model = HomeViewModel(NativeMath { error("failed") })
        advanceUntilIdle()
        assertEquals(HomeUiState.Unavailable(MeanFailure.NATIVE_FAILURE), model.uiState.value)
    }

    @Test fun cancellationIsNotAnErrorState() = runTest(dispatcher) {
        val model = HomeViewModel(NativeMath { throw CancellationException() })
        advanceUntilIdle()
        assertEquals(HomeUiState.Loading, model.uiState.value)
    }

    @Test fun clearingViewModelCancelsItsWork() = runTest(dispatcher) {
        val cancelled = CompletableDeferred<Unit>()
        val model = HomeViewModel(NativeMath {
            try { awaitCancellation() } finally { cancelled.complete(Unit) }
        })
        val store = ViewModelStore().apply { put("foundation", model) }
        runCurrent()
        store.clear()
        runCurrent()
        assertTrue(cancelled.isCompleted)
        assertEquals(HomeUiState.Loading, model.uiState.value)
    }
}
