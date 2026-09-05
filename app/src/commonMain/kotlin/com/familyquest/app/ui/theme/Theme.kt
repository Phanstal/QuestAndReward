package com.familyquest.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val MagicPurple = Color(0xFF7C6FFF)
val QuestTeal = Color(0xFF4ECDC4)
val CoinGold = Color(0xFFF7B731)
val HealthRed = Color(0xFFFF6B6B)
val AppBackground = Color(0xFF0D0D1A)
val CardBackground = Color(0xFF16153A)
val BottomBarBackground = Color(0xFF12122A)

private val DarkColors = darkColorScheme(
    primary = MagicPurple,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF2A265C),
    onPrimaryContainer = Color(0xFFE8DEFF),
    secondary = CoinGold,
    onSecondary = Color(0xFF2A2100),
    secondaryContainer = Color(0xFF3A3016),
    onSecondaryContainer = Color(0xFFFFE39A),
    tertiary = QuestTeal,
    onTertiary = Color(0xFF002F27),
    tertiaryContainer = Color(0xFF123E38),
    onTertiaryContainer = Color(0xFF9DF2E2),
    error = HealthRed,
    errorContainer = Color(0xFF55242D),
    background = AppBackground,
    onBackground = Color(0xFFF2EFFB),
    surface = CardBackground,
    onSurface = Color(0xFFF2EFFB),
    surfaceVariant = Color(0xFF1E1B3A),
    onSurfaceVariant = Color(0xFFB8B3C8),
    outline = Color(0xFF817B93),
    outlineVariant = Color(0xFF39354A),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF6541C4),
    secondary = Color(0xFF8A6500),
    tertiary = Color(0xFF007A68),
    error = Color(0xFFB3263E),
    background = Color(0xFFF8F6FC),
    surface = Color.White,
)

private val AppTypography = Typography().run {
    val displayFamily = FontFamily.SansSerif
    copy(
        titleLarge = titleLarge.copy(fontFamily = displayFamily, letterSpacing = 0.sp),
        titleMedium = titleMedium.copy(fontFamily = displayFamily, letterSpacing = 0.sp),
        titleSmall = titleSmall.copy(letterSpacing = 0.sp),
        bodyLarge = bodyLarge.copy(letterSpacing = 0.sp),
        bodyMedium = bodyMedium.copy(letterSpacing = 0.sp),
        bodySmall = bodySmall.copy(letterSpacing = 0.sp),
        labelLarge = labelLarge.copy(fontFamily = displayFamily, letterSpacing = 0.sp),
        labelMedium = labelMedium.copy(letterSpacing = 0.sp),
        labelSmall = labelSmall.copy(letterSpacing = 0.sp),
    )
}

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(2.dp),
    small = RoundedCornerShape(4.dp),
    medium = RoundedCornerShape(6.dp),
    large = RoundedCornerShape(8.dp),
    extraLarge = RoundedCornerShape(8.dp),
)

@Composable
fun FamilyQuestTheme(darkTheme: Boolean, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}
