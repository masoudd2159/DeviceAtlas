package io.github.masoudd2159.deviceatlas

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.activity.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.core.os.LocaleListCompat
import io.github.masoudd2159.deviceatlas.foundation.AppLanguage
import io.github.masoudd2159.deviceatlas.presentation.settings.SettingsViewModel
import io.github.masoudd2159.deviceatlas.presentation.shell.DeviceAtlasApp
import io.github.masoudd2159.deviceatlas.presentation.theme.DeviceAtlasTheme

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    private val viewModel: SettingsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val settings = viewModel.uiState.collectAsStateWithLifecycle().value
            val dark = settings.theme.isDark(isSystemInDarkTheme())
            // Configuration invalidation also refreshes selection after system per-app changes.
            LocalConfiguration.current
            val language = AppLanguage.fromLanguageTag(AppCompatDelegate.getApplicationLocales().toLanguageTags())
            DeviceAtlasTheme(darkTheme = dark) {
                SideEffect {
                    enableEdgeToEdge(
                        statusBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT) { dark },
                        navigationBarStyle = SystemBarStyle.auto(0xE6FFFFFF.toInt(), 0x801B1B1B.toInt()) { dark },
                    )
                }
                if (settings.loaded) {
                    DeviceAtlasApp(
                        settings = settings, language = language,
                        selectLanguage = { AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(it.languageTag)) },
                        selectTheme = viewModel::selectTheme,
                        retryPreferences = viewModel::retry,
                    )
                } else {
                    Surface(color = MaterialTheme.colorScheme.background) { Text(stringResource(R.string.app_starting)) }
                }
            }
        }
    }
}
