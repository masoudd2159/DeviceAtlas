package io.github.masoudd2159.deviceatlas

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import io.github.masoudd2159.deviceatlas.presentation.home.HomeScreen
import io.github.masoudd2159.deviceatlas.presentation.home.HomeViewModel
import io.github.masoudd2159.deviceatlas.presentation.theme.DeviceAtlasTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val viewModel: HomeViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DeviceAtlasTheme {
                HomeScreen(viewModel.uiState.collectAsStateWithLifecycle().value)
            }
        }
    }
}
