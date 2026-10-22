package io.github.masoudd2159.deviceatlas.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.masoudd2159.deviceatlas.foundation.AppPreferences
import io.github.masoudd2159.deviceatlas.foundation.ThemePreference
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class SettingsUiState(
    val theme: ThemePreference = ThemePreference.SYSTEM,
    val loaded: Boolean = false,
    val persistenceFailed: Boolean = false,
)

/** Activity scope: one owner for the application's appearance. No Context retained. */
@HiltViewModel
class SettingsViewModel @Inject constructor(private val preferences: AppPreferences) : ViewModel() {
    private val mutableState = MutableStateFlow(SettingsUiState())
    val uiState = mutableState.asStateFlow()
    private val writes = Mutex()

    init { retryLoad() }

    fun retryLoad() {
        viewModelScope.launch {
            writes.withLock {
                try {
                    mutableState.value = SettingsUiState(preferences.readTheme(), loaded = true)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    mutableState.value = mutableState.value.copy(loaded = true, persistenceFailed = true)
                }
            }
        }
    }

    fun selectTheme(theme: ThemePreference) {
        viewModelScope.launch {
            writes.withLock {
                try {
                    val saved = preferences.writeTheme(theme)
                    mutableState.value = mutableState.value.copy(
                        theme = if (saved) theme else mutableState.value.theme,
                        persistenceFailed = !saved,
                    )
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    mutableState.value = mutableState.value.copy(persistenceFailed = true)
                }
            }
        }
    }
}
