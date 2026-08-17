package org.moodle.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

private val MoodleLightColors = lightColorScheme(
    primary = Color(0xFF006B5F),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFBCECE2),
    onPrimaryContainer = Color(0xFF00201C),
    secondary = Color(0xFF4B635E),
    tertiary = Color(0xFF526177),
    background = Color(0xFFF7F9F7),
    surface = Color(0xFFFFFBFE),
    surfaceVariant = Color(0xFFE0E7E4),
    outlineVariant = Color(0xFFC0C9C5),
    error = Color(0xFFBA1A1A),
)

private val MoodleDarkColors = darkColorScheme(
    primary = Color(0xFF9FD6CA),
    onPrimary = Color(0xFF00382F),
    primaryContainer = Color(0xFF005045),
    onPrimaryContainer = Color(0xFFBCECE2),
    secondary = Color(0xFFB2CCC5),
    tertiary = Color(0xFFBAC6E0),
    background = Color(0xFF101413),
    surface = Color(0xFF171C1A),
    surfaceVariant = Color(0xFF3F4946),
    outlineVariant = Color(0xFF3F4946),
)

private val MoodleShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
)

@Composable
fun MoodleTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    MaterialTheme(
        colorScheme = if (dark) MoodleDarkColors else MoodleLightColors,
        typography = Typography(),
        shapes = MoodleShapes,
        content = content,
    )
}
