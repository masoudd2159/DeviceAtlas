package io.github.masoudd2159.deviceatlas.presentation.monitoring

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.masoudd2159.deviceatlas.foundation.monitoring.*
import io.github.masoudd2159.deviceatlas.foundation.benchmark.DiagnosticsCoordinator
import javax.inject.Inject

@HiltViewModel
class MonitoringViewModel @Inject constructor(source: MonitoringSource, processor: MonitoringProcessor, clock: MonotonicClock,
    coordinator: DiagnosticsCoordinator) : ViewModel() {
    val session = MonitoringSession(source, processor, clock, viewModelScope, coordinator = coordinator)
    val uiState = session.state
    override fun onCleared() { session.close() }
}
