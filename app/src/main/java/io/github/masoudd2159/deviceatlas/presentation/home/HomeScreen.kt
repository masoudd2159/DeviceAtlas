package io.github.masoudd2159.deviceatlas.presentation.home

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import io.github.masoudd2159.deviceatlas.R
import io.github.masoudd2159.deviceatlas.foundation.MeanFailure
import androidx.compose.ui.tooling.preview.Preview
import io.github.masoudd2159.deviceatlas.presentation.theme.DeviceAtlasTheme
import io.github.masoudd2159.deviceatlas.presentation.components.InformationCard
import io.github.masoudd2159.deviceatlas.presentation.components.SectionHeader

@Composable
fun HomeScreen(state: HomeUiState, modifier: Modifier = Modifier) {
    InformationCard(modifier) {
        SectionHeader(stringResource(R.string.rust_core))
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleMedium)
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

@Preview(name = "Native verification English", showBackground = true)
@Preview(name = "Native verification Persian", locale = "fa", showBackground = true)
@Composable
private fun VerificationPreview() {
    DeviceAtlasTheme(dynamicColor = false) { HomeScreen(HomeUiState.Ready(4.0)) }
}
