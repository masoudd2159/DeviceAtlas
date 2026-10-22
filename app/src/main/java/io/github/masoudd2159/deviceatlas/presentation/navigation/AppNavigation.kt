/*
 * Multiple-stack decoration follows Android's nav3-recipes (Apache-2.0):
 * https://github.com/android/nav3-recipes/tree/main/app/src/main/java/com/example/nav3recipes/multiplestacks
 */
package io.github.masoudd2159.deviceatlas.presentation.navigation

import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.*
import io.github.masoudd2159.deviceatlas.R
import kotlinx.serialization.Serializable

@Serializable
enum class Destination : NavKey {
    OVERVIEW, DEVICE, SENSORS, DIAGNOSTICS, SETTINGS, LANGUAGE, APPEARANCE, ABOUT;

    val title: Int get() = when (this) {
        OVERVIEW -> R.string.overview
        DEVICE -> R.string.device
        SENSORS -> R.string.sensors
        DIAGNOSTICS -> R.string.diagnostics
        SETTINGS -> R.string.settings
        LANGUAGE -> R.string.language
        APPEARANCE -> R.string.appearance
        ABOUT -> R.string.about
    }

    val icon: Int get() = when (this) {
        OVERVIEW -> R.drawable.ic_overview
        DEVICE -> R.drawable.ic_device
        SENSORS -> R.drawable.ic_sensors
        DIAGNOSTICS -> R.drawable.ic_diagnostics
        else -> R.drawable.ic_settings
    }

    companion object {
        val topLevel = listOf(OVERVIEW, DEVICE, SENSORS, DIAGNOSTICS, SETTINGS)
    }
}

/** Thin event policy over official NavBackStacks: retain each tab, exit via Overview. */
class AppNavigationState(
    selected: MutableState<Destination>,
    val stacks: Map<Destination, NavBackStack<NavKey>>,
) {
    var selected by selected
        private set
    val current: Destination get() = stacks.getValue(selected).last() as Destination
    val canGoBack: Boolean get() = selected != Destination.OVERVIEW || stacks.getValue(selected).size > 1

    fun navigate(destination: Destination) {
        if (destination in Destination.topLevel) {
            selected = destination
        } else {
            // All Phase 1 secondary destinations belong to Settings.
            selected = Destination.SETTINGS
            val stack = stacks.getValue(selected)
            if (stack.last() != destination) stack.add(destination)
        }
    }

    fun back() {
        val stack = stacks.getValue(selected)
        if (stack.size > 1) stack.removeAt(stack.lastIndex)
        else selected = Destination.OVERVIEW
    }

    @Composable
    fun decoratedEntries(provider: (NavKey) -> NavEntry<NavKey>): List<NavEntry<NavKey>> {
        val decorated = stacks.mapValues { (destination, stack) ->
            key(destination) {
                rememberDecoratedNavEntries(
                    backStack = stack,
                    entryDecorators = listOf(
                        rememberSaveableStateHolderNavEntryDecorator(),
                        rememberViewModelStoreNavEntryDecorator(),
                    ),
                    entryProvider = provider,
                )
            }
        }
        return if (selected == Destination.OVERVIEW) decorated.getValue(selected)
        else decorated.getValue(Destination.OVERVIEW) + decorated.getValue(selected)
    }
}

@Composable
fun rememberAppNavigationState(): AppNavigationState {
    val selected = rememberSaveable { mutableStateOf(Destination.OVERVIEW) }
    val stacks = Destination.topLevel.associateWith { destination ->
        key(destination) { rememberNavBackStack(destination) }
    }
    return remember { AppNavigationState(selected, stacks) }
}
