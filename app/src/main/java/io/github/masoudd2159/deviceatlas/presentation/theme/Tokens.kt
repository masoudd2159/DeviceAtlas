package io.github.masoudd2159.deviceatlas.presentation.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal val FoundationLightColors = lightColorScheme(
    primary = Color(0xFF315DA8),
    onPrimary = Color.White,
    background = Color(0xFFF9F9FF),
    onBackground = Color(0xFF191C20),
)
internal val FoundationDarkColors = darkColorScheme(
    primary = Color(0xFFABC7FF),
    onPrimary = Color(0xFF002F65),
    background = Color(0xFF111318),
    onBackground = Color(0xFFE2E2E9),
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
    val screen = 24.dp
    val content = 16.dp
}
