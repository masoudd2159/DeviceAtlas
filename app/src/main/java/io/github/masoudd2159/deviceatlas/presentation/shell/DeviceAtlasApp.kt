package io.github.masoudd2159.deviceatlas.presentation.shell

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import io.github.masoudd2159.deviceatlas.presentation.hardware.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import io.github.masoudd2159.deviceatlas.R
import io.github.masoudd2159.deviceatlas.foundation.AppLanguage
import io.github.masoudd2159.deviceatlas.foundation.ThemePreference
import io.github.masoudd2159.deviceatlas.presentation.components.*
import io.github.masoudd2159.deviceatlas.presentation.home.HomeScreen
import io.github.masoudd2159.deviceatlas.presentation.home.HomeViewModel
import io.github.masoudd2159.deviceatlas.presentation.navigation.*
import io.github.masoudd2159.deviceatlas.presentation.settings.SettingsUiState

@Composable
fun DeviceAtlasApp(
    settings: SettingsUiState,
    language: AppLanguage,
    selectLanguage: (AppLanguage) -> Unit,
    selectTheme: (ThemePreference) -> Unit,
    retryPreferences: () -> Unit,
    navigation: AppNavigationState = rememberAppNavigationState(),
    hardware: HardwareUiState = HardwareUiState(),
    refreshHardware: () -> Unit = {},
) {
    val informationPage = navigation.current in listOf(Destination.OVERVIEW, Destination.DEVICE, Destination.SENSORS, Destination.DIAGNOSTICS)
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { if (informationPage) refreshHardware() }
    LaunchedEffect(informationPage) { if (informationPage && hardware.snapshot == null) refreshHardware() }
    NavigationSuiteScaffold(navigationSuiteItems = {
        Destination.topLevel.forEach { destination ->
            item(
                selected = navigation.selected == destination,
                onClick = { navigation.navigate(destination) },
                icon = { Icon(painterResource(destination.icon), contentDescription = null) },
                label = { Text(stringResource(destination.title)) },
                modifier = Modifier.testTag("nav_${destination.name}"),
            )
        }
    }) {
        Scaffold(topBar = {
            AtlasTopBar(
                title = stringResource(if (navigation.current == Destination.OVERVIEW) R.string.app_name else navigation.current.title),
                onBack = if (navigation.current !in Destination.topLevel) ({ navigation.back() }) else null,
            )
        }) { padding ->
            NavDisplay(
                modifier = Modifier.padding(padding).consumeWindowInsets(padding).testTag("page_${navigation.current.name}"),
                entries = navigation.decoratedEntries(entryProvider {
                    entry<Destination> { destination ->
                        when (destination) {
                            Destination.OVERVIEW -> OverviewScreen(navigation::navigate, hardware, refreshHardware)
                            Destination.DEVICE -> HardwareDeviceScreen(hardware, refreshHardware)
                            Destination.SENSORS -> HardwareSensorsScreen(hardware, refreshHardware)
                            Destination.DIAGNOSTICS -> {
                                val model: HomeViewModel = hiltViewModel()
                                ResponsiveContent {
                                    Text(stringResource(R.string.diagnostics_description))
                                    HardwareRefresh(hardware, refreshHardware)
                                    HardwareIntegrity(hardware)
                                    HomeScreen(model.uiState.collectAsStateWithLifecycle().value)
                                }
                            }
                            Destination.SETTINGS -> SettingsScreen(settings, language, navigation::navigate, retryPreferences)
                            Destination.LANGUAGE -> LanguageScreen(language, selectLanguage)
                            Destination.APPEARANCE -> AppearanceScreen(settings, selectTheme, retryPreferences)
                            Destination.ABOUT -> AboutScreen()
                        }
                    }
                }),
                onBack = navigation::back,
            )
        }
    }
}
