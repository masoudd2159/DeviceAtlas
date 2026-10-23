package io.github.masoudd2159.deviceatlas.presentation.benchmark

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.masoudd2159.deviceatlas.foundation.benchmark.*
import io.github.masoudd2159.deviceatlas.foundation.monitoring.MonotonicClock
import javax.inject.Inject

@HiltViewModel
class BenchmarkViewModel @Inject constructor(safety: BenchmarkSafetySource, processor: BenchmarkProcessor,
    clock: MonotonicClock, coordinator: DiagnosticsCoordinator) : ViewModel() {
    val session = BenchmarkSession(safety, processor, clock, coordinator, viewModelScope)
    override fun onCleared() { session.close() }
}
