package io.github.masoudd2159.deviceatlas.presentation.settings

import androidx.lifecycle.ViewModelStore
import io.github.masoudd2159.deviceatlas.foundation.AppLanguage
import io.github.masoudd2159.deviceatlas.foundation.AppPreferences
import io.github.masoudd2159.deviceatlas.foundation.ThemePreference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.CompletableDeferred
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
class SettingsViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test fun restoresThemeAndPersistsSelectionBeforePublishing() = runTest(dispatcher) {
        val preferences = FakePreferences(ThemePreference.DARK)
        val model = SettingsViewModel(preferences)
        assertFalse(model.uiState.value.loaded)
        advanceUntilIdle()
        assertEquals(ThemePreference.DARK, model.uiState.value.theme)
        model.selectTheme(ThemePreference.LIGHT)
        advanceUntilIdle()
        assertEquals(ThemePreference.LIGHT, preferences.theme)
        assertEquals(ThemePreference.LIGHT, model.uiState.value.theme)
        val restored = SettingsViewModel(preferences)
        advanceUntilIdle()
        assertEquals(ThemePreference.LIGHT, restored.uiState.value.theme)
    }

    @Test fun failedWriteKeepsLastThemeAndExposesError() = runTest(dispatcher) {
        val model = SettingsViewModel(FakePreferences(succeeds = false))
        advanceUntilIdle()
        model.selectTheme(ThemePreference.DARK)
        advanceUntilIdle()
        assertEquals(ThemePreference.SYSTEM, model.uiState.value.theme)
        assertTrue(model.uiState.value.persistenceFailed)
    }

    @Test fun readFailureAllowsRetryAndDoesNotOverwriteStoredTheme() = runTest(dispatcher) {
        var failing = true
        val preferences = object : AppPreferences {
            override suspend fun readTheme(): ThemePreference {
                if (failing) error("unavailable")
                return ThemePreference.DARK
            }
            override suspend fun writeTheme(theme: ThemePreference) = error("unexpected write")
        }
        val model = SettingsViewModel(preferences)
        advanceUntilIdle()
        assertTrue(model.uiState.value.persistenceFailed)
        assertTrue(model.uiState.value.loaded)
        failing = false
        model.retryLoad()
        advanceUntilIdle()
        assertEquals(ThemePreference.DARK, model.uiState.value.theme)
        assertFalse(model.uiState.value.persistenceFailed)
    }

    @Test fun retryAfterFailedWritePersistsTheRequestedThemeInsteadOfReloadingOldValue() = runTest(dispatcher) {
        var stored = ThemePreference.SYSTEM
        var writesSucceed = false
        var writeCount = 0
        val model = SettingsViewModel(object : AppPreferences {
            override suspend fun readTheme() = stored
            override suspend fun writeTheme(theme: ThemePreference): Boolean {
                writeCount++
                if (writesSucceed) stored = theme
                return writesSucceed
            }
        })
        advanceUntilIdle()
        model.selectTheme(ThemePreference.DARK)
        advanceUntilIdle()
        assertEquals(ThemePreference.SYSTEM, model.uiState.value.theme)
        writesSucceed = true
        model.retry()
        advanceUntilIdle()
        assertEquals(2, writeCount)
        assertEquals(ThemePreference.DARK, stored)
        assertEquals(ThemePreference.DARK, model.uiState.value.theme)
        assertFalse(model.uiState.value.persistenceFailed)
    }

    @Test fun retryAfterRapidFailuresRetriesLatestChoice() = runTest(dispatcher) {
        val attempts = mutableListOf<ThemePreference>()
        val model = SettingsViewModel(object : AppPreferences {
            override suspend fun readTheme() = ThemePreference.SYSTEM
            override suspend fun writeTheme(theme: ThemePreference): Boolean {
                attempts += theme
                return attempts.size >= 3
            }
        })
        advanceUntilIdle()
        model.selectTheme(ThemePreference.DARK)
        model.selectTheme(ThemePreference.LIGHT)
        model.retry()
        advanceUntilIdle()
        assertEquals(listOf(ThemePreference.DARK, ThemePreference.LIGHT, ThemePreference.LIGHT), attempts)
        assertEquals(ThemePreference.LIGHT, model.uiState.value.theme)
        assertFalse(model.uiState.value.persistenceFailed)
    }

    @Test fun rapidSelectionsAndReloadWaitForDurableWriteAndLastSelectionWins() = runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>()
        val attempts = mutableListOf<ThemePreference>()
        var stored = ThemePreference.SYSTEM
        val model = SettingsViewModel(object : AppPreferences {
            override suspend fun readTheme() = stored
            override suspend fun writeTheme(theme: ThemePreference): Boolean {
                attempts += theme
                if (attempts.size == 1) gate.await()
                stored = theme
                return true
            }
        })
        advanceUntilIdle()
        model.selectTheme(ThemePreference.DARK)
        runCurrent()
        model.retryLoad()
        model.selectTheme(ThemePreference.LIGHT)
        runCurrent()
        assertEquals(ThemePreference.SYSTEM, model.uiState.value.theme)
        assertEquals(listOf(ThemePreference.DARK), attempts)
        gate.complete(Unit)
        advanceUntilIdle()
        assertEquals(listOf(ThemePreference.DARK, ThemePreference.LIGHT), attempts)
        assertEquals(ThemePreference.LIGHT, stored)
        assertEquals(ThemePreference.LIGHT, model.uiState.value.theme)
    }

    @Test fun cancellationDuringWriteDoesNotPublishSuccessOrAnError() = runTest(dispatcher) {
        val model = SettingsViewModel(object : AppPreferences {
            override suspend fun readTheme() = ThemePreference.SYSTEM
            override suspend fun writeTheme(theme: ThemePreference): Boolean = throw CancellationException()
        })
        advanceUntilIdle()
        model.selectTheme(ThemePreference.DARK)
        advanceUntilIdle()
        assertEquals(ThemePreference.SYSTEM, model.uiState.value.theme)
        assertFalse(model.uiState.value.persistenceFailed)
    }

    @Test fun clearingModelCancelsPreferenceLoading() = runTest(dispatcher) {
        var cancelled = false
        val model = SettingsViewModel(object : AppPreferences {
            override suspend fun readTheme(): ThemePreference = try {
                awaitCancellation()
            } finally { cancelled = true }
            override suspend fun writeTheme(theme: ThemePreference) = true
        })
        val store = ViewModelStore().apply { put("settings", model) }
        runCurrent()
        store.clear()
        runCurrent()
        assertTrue(cancelled)
        assertFalse(model.uiState.value.persistenceFailed)
    }

    @Test fun cancellationIsNeverConvertedToPreferenceError() = runTest(dispatcher) {
        val model = SettingsViewModel(object : AppPreferences {
            override suspend fun readTheme(): ThemePreference = throw CancellationException()
            override suspend fun writeTheme(theme: ThemePreference) = true
        })
        advanceUntilIdle()
        assertFalse(model.uiState.value.loaded)
        assertFalse(model.uiState.value.persistenceFailed)
    }

    @Test fun themeResolutionAndUnknownStoredValuesHavePredictableFallbacks() {
        assertEquals(ThemePreference.SYSTEM, ThemePreference.fromStorage("corrupt"))
        assertEquals(ThemePreference.SYSTEM, ThemePreference.fromStorage(null))
        assertTrue(ThemePreference.SYSTEM.isDark(true))
        assertFalse(ThemePreference.SYSTEM.isDark(false))
        assertTrue(ThemePreference.DARK.isDark(false))
        assertFalse(ThemePreference.LIGHT.isDark(true))
        ThemePreference.entries.forEach { assertEquals(it, ThemePreference.fromStorage(it.storageKey)) }
        assertEquals(AppLanguage.PERSIAN, AppLanguage.fromLanguageTag("fa-IR"))
        assertEquals(AppLanguage.PERSIAN, AppLanguage.fromLanguageTag("fa,en"))
        assertEquals(AppLanguage.ENGLISH, AppLanguage.fromLanguageTag("en-US"))
        assertEquals(AppLanguage.SYSTEM, AppLanguage.fromLanguageTag(""))
        assertEquals(AppLanguage.SYSTEM, AppLanguage.fromLanguageTag("de"))
    }

    private class FakePreferences(
        var theme: ThemePreference = ThemePreference.SYSTEM,
        val succeeds: Boolean = true,
    ) : AppPreferences {
        override suspend fun readTheme() = theme
        override suspend fun writeTheme(theme: ThemePreference): Boolean {
            if (succeeds) this.theme = theme
            return succeeds
        }
    }
}
