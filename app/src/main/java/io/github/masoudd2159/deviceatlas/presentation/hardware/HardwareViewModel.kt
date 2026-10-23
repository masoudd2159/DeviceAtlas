package io.github.masoudd2159.deviceatlas.presentation.hardware

import androidx.lifecycle.ViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.masoudd2159.deviceatlas.foundation.hardware.HardwareReader
import io.github.masoudd2159.deviceatlas.foundation.hardware.HardwareSnapshot
import io.github.masoudd2159.deviceatlas.foundation.hardware.HardwareValue
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.coroutines.coroutineContext

data class HardwareUiState(
    val snapshot: HardwareSnapshot? = null, val refreshing: Boolean = false, val failed: Boolean = false,
    // Presentation-only revision. It is neither a timestamp nor a hardware identity.
    val sensorInventoryRevision: Long = 0,
)

/** Activity-owned snapshot. Only its UI revision counter enters saved state; observations never do. */
@HiltViewModel
class HardwareViewModel @Inject constructor(
    private val savedState: SavedStateHandle,
    private val reader: HardwareReader,
) : ViewModel() {
    private val state = MutableStateFlow(HardwareUiState(sensorInventoryRevision = savedState[INVENTORY_REVISION] ?: 0))
    val uiState = state.asStateFlow()
    private var refreshJob: Job? = null

    fun refresh() {
        if (refreshJob?.isActive == true) return
        refreshJob = viewModelScope.launch {
            state.value = state.value.copy(refreshing = true, failed = false)
            try {
                val snapshot = reader.read()
                coroutineContext.ensureActive()
                val revision = if (snapshot.sensors is HardwareValue.Available)
                    Math.incrementExact(state.value.sensorInventoryRevision) else state.value.sensorInventoryRevision
                savedState[INVENTORY_REVISION] = revision
                state.value = HardwareUiState(snapshot = snapshot, sensorInventoryRevision = revision)
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (_: RuntimeException) { state.value = state.value.copy(refreshing = false, failed = true)
            } catch (_: LinkageError) { state.value = state.value.copy(refreshing = false, failed = true) }
            finally { if (state.value.refreshing) state.value = state.value.copy(refreshing = false) }
        }
    }

    private companion object { const val INVENTORY_REVISION = "sensorInventoryRevision" }
}
