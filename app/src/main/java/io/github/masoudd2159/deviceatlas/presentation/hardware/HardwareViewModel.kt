package io.github.masoudd2159.deviceatlas.presentation.hardware

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.masoudd2159.deviceatlas.foundation.hardware.HardwareReader
import io.github.masoudd2159.deviceatlas.foundation.hardware.HardwareSnapshot
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.coroutines.coroutineContext

data class HardwareUiState(val snapshot: HardwareSnapshot? = null, val refreshing: Boolean = false, val failed: Boolean = false)

/** Activity-owned shared foreground snapshot: survives tab changes and rotation, never persists. */
@HiltViewModel
class HardwareViewModel @Inject constructor(private val reader: HardwareReader) : ViewModel() {
    private val state = MutableStateFlow(HardwareUiState())
    val uiState = state.asStateFlow()
    private var refreshJob: Job? = null

    fun refresh() {
        if (refreshJob?.isActive == true) return
        refreshJob = viewModelScope.launch {
            state.value = state.value.copy(refreshing = true, failed = false)
            try {
                val snapshot = reader.read()
                coroutineContext.ensureActive()
                state.value = HardwareUiState(snapshot = snapshot)
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (_: RuntimeException) { state.value = state.value.copy(refreshing = false, failed = true)
            } catch (_: LinkageError) { state.value = state.value.copy(refreshing = false, failed = true) }
            finally { if (state.value.refreshing) state.value = state.value.copy(refreshing = false) }
        }
    }
}
