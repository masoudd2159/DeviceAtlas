package io.github.masoudd2159.deviceatlas.presentation.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.Composable
import androidx.compose.material3.MaterialTheme

internal val FoundationLightColors = lightColorScheme(
    primary = Color(0xFF315DA8),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD8E5FF),
    onPrimaryContainer = Color(0xFF102F60),
    background = Color(0xFFF9F9FF),
    onBackground = Color(0xFF191C20),
    secondary = Color(0xFF526078),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDBE3F6),
    onSecondaryContainer = Color(0xFF152237),
    surface = Color(0xFFF9F9FF),
    surfaceContainer = Color(0xFFEEEFF7),
    surfaceContainerLow = Color(0xFFF2F3FA),
    onSurface = Color(0xFF191C20),
    onSurfaceVariant = Color(0xFF424751),
)
internal val FoundationDarkColors = darkColorScheme(
    primary = Color(0xFFABC7FF),
    onPrimary = Color(0xFF002F65),
    primaryContainer = Color(0xFF254777),
    onPrimaryContainer = Color(0xFFD8E5FF),
    background = Color(0xFF111318),
    onBackground = Color(0xFFE2E2E9),
    secondary = Color(0xFFBAC7E1),
    onSecondary = Color(0xFF233148),
    secondaryContainer = Color(0xFF39475F),
    onSecondaryContainer = Color(0xFFDBE3F6),
    surface = Color(0xFF111318),
    surfaceContainer = Color(0xFF1D2026),
    surfaceContainerLow = Color(0xFF191C22),
    onSurface = Color(0xFFE2E2E9),
    onSurfaceVariant = Color(0xFFC2C6D1),
)
internal val FoundationTypography = Typography(
    headlineLarge = TextStyle(
        fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold,
        fontSize = 32.sp, lineHeight = 40.sp,
    ),
    bodyLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 16.sp, lineHeight = 24.sp),
)
internal val FoundationShapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
)
internal object FoundationSpacing {
    val tiny = 4.dp
    val small = 8.dp
    val medium = 12.dp
    val screen = 24.dp
    val content = 16.dp
    val section = 32.dp
    val touchTarget = 48.dp
    val icon = 24.dp
    val featureIcon = 40.dp
    val maxContentWidth = 840.dp
    val cardElevation = 0.dp
}

enum class StatusTone { INFORMATION, SUCCESS, WARNING, ERROR, UNAVAILABLE }

/** Semantic on-container pairs keep status readable in either theme. */
@Composable
internal fun statusColors(tone: StatusTone): Pair<Color, Color> {
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    return when (tone) {
        StatusTone.SUCCESS -> if (dark) Color(0xFF103D32) to Color(0xFFA1F0CE)
            else Color(0xFFC3F0DC) to Color(0xFF073E2F)
        StatusTone.WARNING -> if (dark) Color(0xFF493514) to Color(0xFFFFDDA0)
            else Color(0xFFFFE4B4) to Color(0xFF4C3300)
        StatusTone.ERROR -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
        StatusTone.INFORMATION -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
        StatusTone.UNAVAILABLE -> MaterialTheme.colorScheme.surfaceContainerHighest to MaterialTheme.colorScheme.onSurfaceVariant
    }
}
