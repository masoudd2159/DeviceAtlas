package io.github.masoudd2159.deviceatlas.presentation.navigation

import androidx.compose.runtime.mutableStateOf
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import org.junit.Assert.*
import org.junit.Test

class AppNavigationTest {
    private fun state() = AppNavigationState(mutableStateOf(Destination.OVERVIEW),
        Destination.topLevel.associateWith { NavBackStack<NavKey>(it) })

    @Test fun startsAtOverviewAndRepeatedTabsNeverDuplicateEntries() {
        val state = state()
        assertEquals(Destination.OVERVIEW, state.current)
        assertFalse(state.canGoBack)
        repeat(10) { state.navigate(Destination.DEVICE) }
        assertEquals(listOf(Destination.DEVICE), state.stacks.getValue(Destination.DEVICE).toList())
        state.back()
        assertEquals(Destination.OVERVIEW, state.current)
    }

    @Test fun settingsSubsectionsHaveRealBackAndSurviveTabSwitches() {
        val state = state()
        state.navigate(Destination.LANGUAGE)
        repeat(5) { state.navigate(Destination.LANGUAGE) }
        assertEquals(2, state.stacks.getValue(Destination.SETTINGS).size)
        state.navigate(Destination.SENSORS)
        state.navigate(Destination.SETTINGS)
        assertEquals(Destination.LANGUAGE, state.current)
        state.back()
        assertEquals(Destination.SETTINGS, state.current)
        state.back()
        assertEquals(Destination.OVERVIEW, state.current)
        assertFalse(state.canGoBack)
    }

    @Test fun everyTopLevelDestinationAndSettingsSubsectionIsReachable() {
        val state = state()
        Destination.entries.forEach {
            state.navigate(it)
            assertEquals(it, state.current)
            if (it !in Destination.topLevel) state.back()
        }
    }
}
