package io.github.masoudd2159.deviceatlas.presentation.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import io.github.masoudd2159.deviceatlas.R
import io.github.masoudd2159.deviceatlas.foundation.MeanFailure
import io.github.masoudd2159.deviceatlas.presentation.theme.FoundationSpacing

@Composable
fun HomeScreen(state: HomeUiState, modifier: Modifier = Modifier) {
    Scaffold(modifier = modifier.fillMaxSize()) { contentPadding ->
        Column(
            modifier = Modifier.padding(contentPadding).verticalScroll(rememberScrollState())
                .padding(FoundationSpacing.screen),
            verticalArrangement = Arrangement.spacedBy(FoundationSpacing.content),
        ) {
            Text(
                stringResource(R.string.app_name),
                modifier = Modifier.semantics { heading() },
                style = MaterialTheme.typography.headlineLarge,
            )
            Text(stringResource(R.string.foundation_description))
            Text(
                when (state) {
                    HomeUiState.Loading -> stringResource(R.string.native_loading)
                    is HomeUiState.Ready -> stringResource(R.string.native_ready, state.mean)
                    is HomeUiState.Unavailable -> stringResource(when (state.reason) {
                        MeanFailure.EMPTY_INPUT -> R.string.native_empty
                        MeanFailure.NON_FINITE_INPUT -> R.string.native_non_finite
                        MeanFailure.LIBRARY_UNAVAILABLE -> R.string.native_unavailable
                        MeanFailure.NATIVE_FAILURE -> R.string.native_failure
                    })
                },
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
    }
}
