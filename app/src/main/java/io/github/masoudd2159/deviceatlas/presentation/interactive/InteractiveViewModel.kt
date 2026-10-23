package io.github.masoudd2159.deviceatlas.presentation.interactive

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.masoudd2159.deviceatlas.foundation.benchmark.DiagnosticsCoordinator
import io.github.masoudd2159.deviceatlas.foundation.interactive.*
import io.github.masoudd2159.deviceatlas.foundation.monitoring.MonotonicClock
import javax.inject.Inject

@HiltViewModel
class InteractiveViewModel @Inject constructor(sensors: InteractiveSensors, haptics: InteractiveHaptics,
    analyzer: SensorAnalyzer, clock: MonotonicClock, coordinator: DiagnosticsCoordinator) : ViewModel() {
    val session = InteractiveSession(sensors, haptics, analyzer, clock, coordinator, viewModelScope)
    override fun onCleared() { session.close() }
}
