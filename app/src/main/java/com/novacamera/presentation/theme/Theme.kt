package com.novacamera.presentation.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Brand accent: the warm amber of a camera's "armed" indicator. */
val NovaAmber = Color(0xFFFFC857)
val NovaRed = Color(0xFFFF4D4D)
val NovaScrim = Color(0xB3000000)

private val NovaDark = darkColorScheme(
    primary = NovaAmber,
    onPrimary = Color(0xFF1A1200),
    primaryContainer = Color(0xFF4A3A10),
    onPrimaryContainer = Color(0xFFFFE3A3),
    secondary = Color(0xFFB9C6D6),
    onSecondary = Color(0xFF101820),
    background = Color.Black,
    onBackground = Color(0xFFF2F2F2),
    surface = Color(0xFF111214),
    onSurface = Color(0xFFF2F2F2),
    surfaceVariant = Color(0xFF1E2024),
    onSurfaceVariant = Color(0xFFC4C7CC),
    surfaceContainer = Color(0xFF17181B),
    surfaceContainerHigh = Color(0xFF212328),
    outline = Color(0xFF5B5F66),
    error = NovaRed,
)

private val NovaType = Typography(
    titleLarge = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Medium),
    labelLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.4.sp),
    bodyMedium = TextStyle(fontSize = 14.sp),
)

/** Camera apps live in the dark: one consistent dark scheme, no dynamic-colour surprises. */
@Composable
fun NovaCameraTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = NovaDark, typography = NovaType, content = content)
}
