package io.github.masoudd2159.deviceatlas.presentation.shell

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
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
import io.github.masoudd2159.deviceatlas.presentation.monitoring.*
import io.github.masoudd2159.deviceatlas.foundation.monitoring.MonitoringState
import io.github.masoudd2159.deviceatlas.foundation.monitoring.StopReason
import io.github.masoudd2159.deviceatlas.presentation.benchmark.*
import io.github.masoudd2159.deviceatlas.foundation.benchmark.*
import io.github.masoudd2159.deviceatlas.presentation.interactive.*
import io.github.masoudd2159.deviceatlas.foundation.interactive.*

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
    monitoring: MonitoringSession? = null,
    benchmark: BenchmarkSession? = null,
    interactive: InteractiveSession? = null,
) {
    var diagnosticsPanel by rememberSaveable { mutableIntStateOf(0) }
    if (monitoring != null) MonitoringVisibility(monitoring, navigation.current == Destination.DIAGNOSTICS && diagnosticsPanel == 0)
    if (benchmark != null) BenchmarkVisibility(benchmark, navigation.current == Destination.DIAGNOSTICS && diagnosticsPanel == 1)
    if (interactive != null) InteractiveVisibility(interactive, navigation.current == Destination.DIAGNOSTICS && diagnosticsPanel == 2)
    val interactiveState = interactive?.state?.collectAsStateWithLifecycle()?.value ?: InteractiveState()
    val benchmarkState = benchmark?.state?.collectAsStateWithLifecycle()?.value ?: BenchmarkState()
    val monitoringState = monitoring?.state?.collectAsStateWithLifecycle()?.value ?: MonitoringState()
    val informationPage = navigation.current in listOf(Destination.OVERVIEW, Destination.DEVICE, Destination.SENSORS, Destination.DIAGNOSTICS)
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { if (informationPage) refreshHardware() }
    LaunchedEffect(informationPage) { if (informationPage && hardware.snapshot == null) refreshHardware() }
    NavigationSuiteScaffold(navigationSuiteItems = {
        Destination.topLevel.forEach { destination ->
            item(
                selected = navigation.selected == destination,
                onClick = {
                    if (destination != Destination.DIAGNOSTICS) monitoring?.stop(StopReason.HIDDEN)
                    if (destination != Destination.DIAGNOSTICS) benchmark?.stop(BenchmarkStop.HIDDEN)
                    if (destination != Destination.DIAGNOSTICS) interactive?.stop(InteractiveStop.HIDDEN)
                    navigation.navigate(destination)
                },
                icon = { Icon(painterResource(destination.icon), contentDescription = null) },
                label = { Text(stringResource(destination.title)) },
                modifier = Modifier.testTag("nav_${destination.name}"),
            )
        }
    }) {
        Scaffold(topBar = {
            AtlasTopBar(
                title = stringResource(if (navigation.current == Destination.OVERVIEW) R.string.app_name else navigation.current.title),
                onBack = if (navigation.current !in Destination.topLevel) ({ monitoring?.stop(StopReason.HIDDEN); benchmark?.stop(BenchmarkStop.HIDDEN); interactive?.stop(InteractiveStop.HIDDEN); navigation.back() }) else null,
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
                                    FlowRow {
                                        FilterChip(selected = diagnosticsPanel == 0, onClick = { benchmark?.stop(BenchmarkStop.HIDDEN); interactive?.stop(InteractiveStop.HIDDEN); diagnosticsPanel = 0 },
                                            label = { Text(stringResource(R.string.monitor_title)) }, modifier = Modifier.testTag("diagnostics_monitor"))
                                        FilterChip(selected = diagnosticsPanel == 1, onClick = { monitoring?.stop(StopReason.HIDDEN); interactive?.stop(InteractiveStop.HIDDEN); diagnosticsPanel = 1 },
                                            label = { Text(stringResource(R.string.bench_title)) }, modifier = Modifier.testTag("diagnostics_benchmark"))
                                        FilterChip(selected = diagnosticsPanel == 2, onClick = { monitoring?.stop(StopReason.HIDDEN); benchmark?.stop(BenchmarkStop.HIDDEN); diagnosticsPanel = 2 },
                                            label = { Text(stringResource(R.string.interactive_title)) }, modifier = Modifier.testTag("diagnostics_interactive"))
                                    }
                                    if (diagnosticsPanel == 2) {
                                        InteractivePanel(interactiveState, interactive)
                                    } else if (diagnosticsPanel == 1) {
                                        BenchmarkPanel(benchmarkState, monitoringState.status is io.github.masoudd2159.deviceatlas.foundation.monitoring.MonitoringStatus.Running,
                                            { benchmark?.start() }, { benchmark?.stop() }, { benchmark?.reset() })
                                    } else {
                                        if (benchmarkState.status == BenchmarkStatus.Active || benchmarkState.retiring) Text(stringResource(R.string.bench_monitor_disabled))
                                        MonitoringPanel(monitoringState, { monitoring?.start() }, { monitoring?.stop() }, { monitoring?.reset() })
                                        Text(stringResource(R.string.diagnostics_description))
                                        HardwareRefresh(hardware, refreshHardware)
                                        HardwareIntegrity(hardware)
                                        HomeScreen(model.uiState.collectAsStateWithLifecycle().value)
                                    }
                                }
                            }
                            Destination.SETTINGS -> SettingsScreen(settings, language, navigation::navigate, retryPreferences)
                            Destination.LANGUAGE -> LanguageScreen(language, selectLanguage)
                            Destination.APPEARANCE -> AppearanceScreen(settings, selectTheme, retryPreferences)
                            Destination.ABOUT -> AboutScreen()
                        }
                    }
                }),
                onBack = { monitoring?.stop(StopReason.HIDDEN); benchmark?.stop(BenchmarkStop.HIDDEN); interactive?.stop(InteractiveStop.HIDDEN); navigation.back() },
            )
        }
    }
}
