package io.github.masoudd2159.deviceatlas.presentation.shell

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import io.github.masoudd2159.deviceatlas.BuildConfig
import io.github.masoudd2159.deviceatlas.R
import io.github.masoudd2159.deviceatlas.foundation.AppLanguage
import io.github.masoudd2159.deviceatlas.foundation.ThemePreference
import io.github.masoudd2159.deviceatlas.presentation.components.*
import io.github.masoudd2159.deviceatlas.presentation.navigation.Destination
import io.github.masoudd2159.deviceatlas.presentation.settings.SettingsUiState
import io.github.masoudd2159.deviceatlas.presentation.theme.*
import io.github.masoudd2159.deviceatlas.presentation.hardware.*

@Composable
fun OverviewScreen(navigate: (Destination) -> Unit, hardware: HardwareUiState = HardwareUiState(), refresh: () -> Unit = {}) {
    ResponsiveContent {
        Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.primaryContainer) {
            Column(Modifier.padding(FoundationSpacing.screen),
                verticalArrangement = Arrangement.spacedBy(FoundationSpacing.content)) {
                Text(stringResource(R.string.overview_title), style = MaterialTheme.typography.headlineLarge)
                Text(stringResource(R.string.overview_description), style = MaterialTheme.typography.bodyLarge)
            }
        }
        HardwareRefresh(hardware, refresh)
        HardwareSummary(hardware)
        SectionHeader(stringResource(R.string.explore))
        ActionRow(stringResource(R.string.device), stringResource(R.string.device_summary), { navigate(Destination.DEVICE) })
        ActionRow(stringResource(R.string.sensors), stringResource(R.string.sensors_summary), { navigate(Destination.SENSORS) })
        ActionRow(stringResource(R.string.diagnostics), stringResource(R.string.diagnostics_summary), { navigate(Destination.DIAGNOSTICS) })
        InformationCard {
            SectionHeader(stringResource(R.string.privacy_title))
            Text(stringResource(R.string.privacy_description))
        }
    }
}

@Composable
fun PlannedScreen(description: Int) {
    ResponsiveContent { AvailabilityMessage(stringResource(description)) }
}

@Composable
fun SettingsScreen(state: SettingsUiState, language: AppLanguage, navigate: (Destination) -> Unit, retry: () -> Unit) {
    ResponsiveContent {
        SectionHeader(stringResource(R.string.preferences))
        ActionRow(stringResource(R.string.language), languageLabel(language), { navigate(Destination.LANGUAGE) })
        ActionRow(stringResource(R.string.appearance), themeLabel(state.theme), { navigate(Destination.APPEARANCE) })
        ActionRow(stringResource(R.string.about), BuildConfig.VERSION_NAME, { navigate(Destination.ABOUT) })
        if (state.persistenceFailed) PreferenceError(retry)
    }
}

@Composable
fun LanguageScreen(language: AppLanguage, select: (AppLanguage) -> Unit) {
    ResponsiveContent {
        Text(stringResource(R.string.language_description))
        InformationCard {
            SelectionGroup {
                AppLanguage.entries.forEach { choice ->
                    SettingsChoice(languageLabel(choice), language == choice, { select(choice) })
                }
            }
        }
    }
}

@Composable
fun AppearanceScreen(state: SettingsUiState, select: (ThemePreference) -> Unit, retry: () -> Unit) {
    ResponsiveContent {
        Text(stringResource(R.string.appearance_description))
        InformationCard {
            SectionHeader(stringResource(R.string.theme))
            SelectionGroup {
                ThemePreference.entries.forEach { choice ->
                    SettingsChoice(themeLabel(choice), state.theme == choice, { select(choice) })
                }
            }
        }
        if (state.persistenceFailed) PreferenceError(retry)
    }
}

@Composable
fun AboutScreen() {
    ResponsiveContent {
        SectionHeader(stringResource(R.string.app_name))
        Text(stringResource(R.string.about_description))
        InformationCard {
            InformationRow(stringResource(R.string.version), BuildConfig.VERSION_NAME, technical = true)
            InformationRow(stringResource(R.string.phase), stringResource(R.string.phase_value))
            InformationRow(stringResource(R.string.computation_core), stringResource(R.string.rust), technical = true)
            InformationRow(stringResource(R.string.access), stringResource(R.string.none))
        }
        SectionHeader(stringResource(R.string.privacy_title))
        Text(stringResource(R.string.privacy_description))
    }
}

@Composable
fun languageLabel(language: AppLanguage): String = stringResource(when (language) {
    AppLanguage.SYSTEM -> R.string.follow_system
    AppLanguage.ENGLISH -> R.string.english
    AppLanguage.PERSIAN -> R.string.persian
})

@Composable
fun themeLabel(theme: ThemePreference): String = stringResource(when (theme) {
    ThemePreference.SYSTEM -> R.string.follow_system
    ThemePreference.LIGHT -> R.string.light
    ThemePreference.DARK -> R.string.dark
})

@Preview(name = "Overview phone", showBackground = true)
@Preview(name = "Overview Persian", locale = "fa", showBackground = true)
@Preview(name = "Overview tablet", widthDp = 1000, heightDp = 700, showBackground = true)
@Composable
private fun OverviewPreview() { DeviceAtlasTheme(dynamicColor = false) { OverviewScreen(navigate = {}) } }

@Preview(name = "Settings Persian large font", locale = "fa", fontScale = 2f, showBackground = true)
@Composable
private fun SettingsPreview() {
    DeviceAtlasTheme(darkTheme = true, dynamicColor = false) {
        SettingsScreen(SettingsUiState(loaded = true), AppLanguage.PERSIAN, {}, {})
    }
}
