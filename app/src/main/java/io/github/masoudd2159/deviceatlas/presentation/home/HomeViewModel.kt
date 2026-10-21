package io.github.masoudd2159.deviceatlas.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.masoudd2159.deviceatlas.foundation.MeanFailure
import io.github.masoudd2159.deviceatlas.foundation.MeanResult
import io.github.masoudd2159.deviceatlas.foundation.NativeMath
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface HomeUiState {
    data object Loading : HomeUiState
    data class Ready(val mean: Double) : HomeUiState
    data class Unavailable(val reason: MeanFailure) : HomeUiState
}

@HiltViewModel
class HomeViewModel @Inject constructor(math: NativeMath) : ViewModel() {
    private val mutableUiState = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val uiState: StateFlow<HomeUiState> = mutableUiState.asStateFlow()

    init {
        viewModelScope.launch {
            mutableUiState.value = try {
                // Fixed integration test data; never presented as a hardware measurement.
                when (val result = math.mean(doubleArrayOf(2.0, 4.0, 6.0))) {
                    is MeanResult.Success -> HomeUiState.Ready(result.value)
                    is MeanResult.Failure -> HomeUiState.Unavailable(result.reason)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: LinkageError) {
                HomeUiState.Unavailable(MeanFailure.LIBRARY_UNAVAILABLE)
            } catch (_: Exception) {
                HomeUiState.Unavailable(MeanFailure.NATIVE_FAILURE)
            }
        }
    }
}
