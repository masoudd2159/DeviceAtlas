package io.github.masoudd2159.deviceatlas.presentation.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeContrastTest {
    @Test fun fallbackContentPairsMeetNormalTextContrast() {
        listOf(FoundationLightColors, FoundationDarkColors).forEach { colors ->
            listOf(
                colors.primary to colors.onPrimary,
                colors.primaryContainer to colors.onPrimaryContainer,
                colors.secondaryContainer to colors.onSecondaryContainer,
                colors.background to colors.onBackground,
                colors.surface to colors.onSurface,
                colors.surfaceContainerLow to colors.onSurfaceVariant,
                colors.errorContainer to colors.onErrorContainer,
            ).forEach { (background, content) ->
                assertTrue("Unreadable theme pair: $background / $content", contrast(background, content) >= 4.5f)
            }
        }
    }

    private fun contrast(first: Color, second: Color): Float {
        val a = first.luminance()
        val b = second.luminance()
        return (maxOf(a, b) + 0.05f) / (minOf(a, b) + 0.05f)
    }
}
