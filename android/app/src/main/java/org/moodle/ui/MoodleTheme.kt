package org.moodle.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val PortalNavy = Color(0xFF0B2733)
val PortalTeal = Color(0xFF006A60)
val PortalTealDark = Color(0xFF004E47)
val PortalGold = Color(0xFFC5963F)
val PortalIvory = Color(0xFFF6F4ED)

private val MoodleLightColors = lightColorScheme(
    primary = PortalTeal,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD5EEE8),
    onPrimaryContainer = Color(0xFF063D38),
    secondary = PortalNavy,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDCE7EB),
    onSecondaryContainer = PortalNavy,
    tertiary = Color(0xFF8A6420),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFF5E3B9),
    onTertiaryContainer = Color(0xFF4D3709),
    background = PortalIvory,
    onBackground = Color(0xFF172220),
    surface = Color(0xFFFFFCF7),
    onSurface = Color(0xFF172220),
    surfaceVariant = Color(0xFFE8ECE8),
    onSurfaceVariant = Color(0xFF52605D),
    surfaceContainer = Color(0xFFF0EFE9),
    surfaceContainerHigh = Color(0xFFE9E9E3),
    outline = Color(0xFF74807D),
    outlineVariant = Color(0xFFD1D9D5),
    error = Color(0xFFB3261E),
    errorContainer = Color(0xFFF9DEDC),
)

private val MoodleDarkColors = darkColorScheme(
    primary = Color(0xFF82D8C9),
    onPrimary = Color(0xFF003730),
    primaryContainer = Color(0xFF0C5049),
    onPrimaryContainer = Color(0xFFB9F1E6),
    secondary = Color(0xFFB9D3DF),
    onSecondary = Color(0xFF17333F),
    secondaryContainer = Color(0xFF233F4B),
    onSecondaryContainer = Color(0xFFD5EFFA),
    tertiary = Color(0xFFE9C36F),
    onTertiary = Color(0xFF402D00),
    tertiaryContainer = Color(0xFF5B4311),
    onTertiaryContainer = Color(0xFFFFE8AD),
    background = Color(0xFF081513),
    onBackground = Color(0xFFE2E9E5),
    surface = Color(0xFF0E1E1B),
    onSurface = Color(0xFFE2E9E5),
    surfaceVariant = Color(0xFF253633),
    onSurfaceVariant = Color(0xFFBCC9C5),
    surfaceContainer = Color(0xFF142521),
    surfaceContainerHigh = Color(0xFF1B2D29),
    outline = Color(0xFF879590),
    outlineVariant = Color(0xFF344844),
    error = Color(0xFFFFB4AB),
    errorContainer = Color(0xFF8C1D18),
)

private val MoodleShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(34.dp),
)

private val MoodleTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 46.sp,
        lineHeight = 52.sp,
        letterSpacing = (-0.8).sp,
    ),
    displaySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 34.sp,
        lineHeight = 40.sp,
        letterSpacing = (-0.4).sp,
    ),
    headlineLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 30.sp,
        lineHeight = 37.sp,
        letterSpacing = (-0.25).sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 25.sp,
        lineHeight = 32.sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 29.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 19.sp,
        lineHeight = 26.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 23.sp,
        letterSpacing = 0.05.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 25.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 21.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 18.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.15.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 17.sp,
        letterSpacing = 0.2.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.25.sp,
    ),
)

@Composable
fun MoodleTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) MoodleDarkColors else MoodleLightColors,
        typography = MoodleTypography,
        shapes = MoodleShapes,
        content = content,
    )
}
