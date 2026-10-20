package com.salihraphics.meow.core.designsystem.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/** Sampled from the logo. Used for brand moments (welcome, icon, splash), not for text. */
val BrandPurple = Color(0xFF8F6EFC)

/** The cat's own palette, shared by every mascot drawing. */
object CatPalette {
    val Fur = Color(0xFF121014)
    val FurShade = Color(0xFF1D1A21)
    val InnerEar = Color(0xFF473A3D)
    val EyeWhite = Color(0xFFFBF9F6)
    val Pupil = Color(0xFF0B0A0D)
    val Nose = Color(0xFF7A5E68)
    val Line = Color(0xFF5B5562)
    val ClosedEye = Color(0xFFE6E1EC)
}

internal val LightColors = lightColorScheme(
    primary = Color(0xFF7652F2),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE9E2FF),
    onPrimaryContainer = Color(0xFF26106B),
    secondary = Color(0xFF625B71),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFEDE7DD),
    onSecondaryContainer = Color(0xFF2C2832),
    background = Color(0xFFF7F3EC),
    onBackground = Color(0xFF1B1A1F),
    surface = Color(0xFFF7F3EC),
    onSurface = Color(0xFF1B1A1F),
    surfaceVariant = Color(0xFFEEE8DF),
    onSurfaceVariant = Color(0xFF5C5862),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFCFAF6),
    surfaceContainer = Color(0xFFF3EEE6),
    surfaceContainerHigh = Color(0xFFEEE8DF),
    surfaceContainerHighest = Color(0xFFE8E2D8),
    outline = Color(0xFFCFC8BD),
    outlineVariant = Color(0xFFE3DDD3),
    inverseSurface = Color(0xFF1B1A1F),
    inverseOnSurface = Color(0xFFF7F3EC),
    inversePrimary = Color(0xFFB9A4FF),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
)

internal val DarkColors = darkColorScheme(
    primary = Color(0xFFAE97FF),
    onPrimary = Color(0xFF210D57),
    primaryContainer = Color(0xFF362B5E),
    onPrimaryContainer = Color(0xFFE9E2FF),
    secondary = Color(0xFFCBC3D6),
    onSecondary = Color(0xFF332D3D),
    secondaryContainer = Color(0xFF2C2A31),
    onSecondaryContainer = Color(0xFFE6E1EC),
    background = Color(0xFF141317),
    onBackground = Color(0xFFECE9F1),
    surface = Color(0xFF141317),
    onSurface = Color(0xFFECE9F1),
    surfaceVariant = Color(0xFF26252B),
    onSurfaceVariant = Color(0xFFB7B2BE),
    surfaceContainerLowest = Color(0xFF0F0E11),
    surfaceContainerLow = Color(0xFF1A191D),
    surfaceContainer = Color(0xFF1F1E23),
    surfaceContainerHigh = Color(0xFF26252B),
    surfaceContainerHighest = Color(0xFF2E2D33),
    outline = Color(0xFF45424C),
    outlineVariant = Color(0xFF333139),
    inverseSurface = Color(0xFFECE9F1),
    inverseOnSurface = Color(0xFF141317),
    inversePrimary = Color(0xFF7652F2),
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410),
)
