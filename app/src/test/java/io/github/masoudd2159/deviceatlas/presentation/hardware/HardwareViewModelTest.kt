package io.github.masoudd2159.deviceatlas.presentation.hardware

import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.SavedStateHandle
import io.github.masoudd2159.deviceatlas.foundation.hardware.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HardwareViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val snapshot = HardwareSnapshot(emptyMap(), HardwareValue.Available(emptyList()), 123)
    @Before fun before() { Dispatchers.setMain(dispatcher) }
    @After fun after() { Dispatchers.resetMain() }

    @Test fun refreshIsExplicitAndConcurrentRequestsAreDeduplicated() = runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>(); var calls = 0
        val model = HardwareViewModel(SavedStateHandle()) { calls++; gate.await(); snapshot }
        assertNull(model.uiState.value.snapshot)
        model.refresh(); repeat(10) { model.refresh() }; runCurrent()
        assertEquals(1, calls); assertTrue(model.uiState.value.refreshing)
        gate.complete(Unit); advanceUntilIdle()
        assertEquals(HardwareUiState(snapshot, sensorInventoryRevision = 1), model.uiState.value)
    }

    @Test fun failedRefreshKeepsOriginalTimestampAndPreviousValues() = runTest(dispatcher) {
        var fail = false
        val model = HardwareViewModel(SavedStateHandle()) { if (fail) error("collector") else snapshot }
        model.refresh(); advanceUntilIdle(); fail = true
        model.refresh(); runCurrent()
        assertEquals(snapshot, model.uiState.value.snapshot)
        assertTrue(model.uiState.value.failed); assertFalse(model.uiState.value.refreshing)
        fail = false; model.refresh(); advanceUntilIdle()
        assertFalse(model.uiState.value.failed)
    }

    @Test fun clearingOwnerCancelsWorkAndCannotPublishLateSnapshot() = runTest(dispatcher) {
        var cancelled = false
        val model = HardwareViewModel(SavedStateHandle()) { try { awaitCancellation() } finally { cancelled = true } }
        val store = ViewModelStore().apply { put("hardware", model) }
        model.refresh(); runCurrent(); store.clear(); advanceUntilIdle()
        assertTrue(cancelled); assertNull(model.uiState.value.snapshot)
        assertFalse(model.uiState.value.failed)
    }

    @Test fun cancelledDependencyIsNotACollectionError() = runTest(dispatcher) {
        val model = HardwareViewModel(SavedStateHandle()) { throw CancellationException() }
        model.refresh(); advanceUntilIdle()
        assertFalse(model.uiState.value.failed); assertFalse(model.uiState.value.refreshing)
    }

    @Test fun everyAvailableInventoryReplacementAdvancesRevisionEvenForEqualSnapshots() = runTest(dispatcher) {
        val model = HardwareViewModel(SavedStateHandle()) { snapshot }
        repeat(3) { index ->
            model.refresh(); advanceUntilIdle()
            assertEquals((index + 1).toLong(), model.uiState.value.sensorInventoryRevision)
            assertEquals(123L, model.uiState.value.snapshot?.capturedAtMillis)
        }
    }

    @Test fun failedOrUnavailableInventoryDoesNotInventAReplacementRevision() = runTest(dispatcher) {
        var result = snapshot; var fail = false
        val model = HardwareViewModel(SavedStateHandle()) { if (fail) error("collection") else result }
        model.refresh(); advanceUntilIdle()
        assertEquals(1, model.uiState.value.sensorInventoryRevision)
        fail = true; model.refresh(); advanceUntilIdle()
        assertEquals(1, model.uiState.value.sensorInventoryRevision)
        result = snapshot.copy(sensors = HardwareValue.Unavailable(UnavailableReason.COLLECTION_FAILED))
        fail = false; model.refresh(); advanceUntilIdle()
        assertEquals(1, model.uiState.value.sensorInventoryRevision)
        result = snapshot; model.refresh(); advanceUntilIdle()
        assertEquals(2, model.uiState.value.sensorInventoryRevision)
    }

    @Test fun restoredOwnerCannotReuseTheSavedInventoryRevisionForNewData() = runTest(dispatcher) {
        val handle = SavedStateHandle(mapOf("sensorInventoryRevision" to 7L))
        val model = HardwareViewModel(handle) { snapshot }
        assertNull(model.uiState.value.snapshot) // Hardware observations are never saved.
        model.refresh(); advanceUntilIdle()
        assertEquals(8, model.uiState.value.sensorInventoryRevision)
        assertEquals(8L, handle.get<Long>("sensorInventoryRevision"))
    }
}
