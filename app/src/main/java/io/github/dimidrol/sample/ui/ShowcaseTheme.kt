package io.github.dimidrol.sample.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val LightPalette: ColorScheme = lightColorScheme(
    primary = Color(0xFF006D8F),
    onPrimary = Color(0xFFFFFFFF),
    secondary = Color(0xFF775900),
    onSecondary = Color(0xFFFFFFFF),
    tertiary = Color(0xFF006A62),
    onTertiary = Color(0xFFFFFFFF),
    background = Color(0xFFF5FAFD),
    onBackground = Color(0xFF0C1B24),
    surface = Color(0xFFFDFEFF),
    onSurface = Color(0xFF0F1A22),
    error = Color(0xFFB3261E)
)

private val DarkPalette: ColorScheme = darkColorScheme(
    primary = Color(0xFF64D3FF),
    onPrimary = Color(0xFF003548),
    secondary = Color(0xFFF1C15D),
    onSecondary = Color(0xFF3F2E00),
    tertiary = Color(0xFF57DBC9),
    onTertiary = Color(0xFF003730),
    background = Color(0xFF08141B),
    onBackground = Color(0xFFD9EBF5),
    surface = Color(0xFF102028),
    onSurface = Color(0xFFD8E7EE),
    error = Color(0xFFFFB4AB)
)

private val ShowcaseTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Bold,
        fontSize = 36.sp,
        letterSpacing = 0.2.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp
    )
)

@Composable
fun DeviceMonitorShowcaseTheme(content: @Composable () -> Unit) {
    val colors = if (isSystemInDarkTheme()) DarkPalette else LightPalette

    MaterialTheme(
        colorScheme = colors,
        typography = ShowcaseTypography,
        content = content
    )
}
