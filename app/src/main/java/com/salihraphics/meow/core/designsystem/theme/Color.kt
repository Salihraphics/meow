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
    /** Warm taupe, as inside the logo's ears. */
    val InnerEar = Color(0xFF4D3D40)
    val EyeWhite = Color(0xFFFBF9F6)
    val Pupil = Color(0xFF0B0A0D)
    val Nose = Color(0xFF8A6874)
    val Line = Color(0xFF5B5562)
    val ClosedEye = Color(0xFFE6E1EC)
    /** Soft and light, so whiskers show on black fur and on cream or night backgrounds. */
    val Whisker = Color(0xFF8C8494)
}

/**
 * MEOW's palette. Light: cream paper, warm off-white cards (never stark white), a deep violet
 * that passes contrast with white text, charcoal ink. Dark: a night background, charcoal cards a
 * clear step lighter, raised surfaces (dialogs, bubbles, menus) another step up, lavender
 * accents and soft-white text (no pure white).
 */
object MeowPalette {
    val Lavender = Color(0xFFA78BFA)
    val LightLavender = Color(0xFFC4B5FD)
    val DeepViolet = Color(0xFF37305F)
    /** Text-safe violet: 5.1:1 on cream, 5.7:1 under white text. */
    val Violet = Color(0xFF6B46E8)
    val Night = Color(0xFF131217)
    val Charcoal = Color(0xFF1F1E24)
    val Cream = Color(0xFFF5F1EA)
    val SoftWhite = Color(0xFFF4F0F8)
    val Ink = Color(0xFF1B1A1F)
}

internal val LightColors = lightColorScheme(
    primary = MeowPalette.Violet,
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE7DFFF),
    onPrimaryContainer = Color(0xFF26106B),
    secondary = Color(0xFF5E576B),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFEAE3D8),
    onSecondaryContainer = Color(0xFF2C2832),
    tertiary = Color(0xFF8A5A2B),
    onTertiary = Color(0xFFFFFFFF),
    background = MeowPalette.Cream,
    onBackground = MeowPalette.Ink,
    surface = MeowPalette.Cream,
    onSurface = MeowPalette.Ink,
    surfaceVariant = Color(0xFFEAE4DA),
    // 7:1 on cream: secondary text that stays readable.
    onSurfaceVariant = Color(0xFF524D58),
    surfaceContainerLowest = Color(0xFFFFFDF9),
    surfaceContainerLow = Color(0xFFFAF7F1),
    surfaceContainer = Color(0xFFF0EBE3),
    surfaceContainerHigh = Color(0xFFEAE4DA),
    surfaceContainerHighest = Color(0xFFE3DCD1),
    // 3.2:1 or better on cream, cards and insets: field, switch and button borders show; dividers stay quiet.
    outline = Color(0xFF8A8277),
    outlineVariant = Color(0xFFDDD5C9),
    inverseSurface = Color(0xFF2A2830),
    inverseOnSurface = MeowPalette.SoftWhite,
    inversePrimary = MeowPalette.LightLavender,
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFF9DEDA),
    onErrorContainer = Color(0xFF5C1712),
    scrim = Color(0xFF000000),
)

internal val DarkColors = darkColorScheme(
    primary = MeowPalette.Lavender,
    onPrimary = Color(0xFF1E0F4A),
    primaryContainer = MeowPalette.DeepViolet,
    onPrimaryContainer = Color(0xFFE6DEFF),
    secondary = Color(0xFFCFC8DA),
    onSecondary = Color(0xFF332D3D),
    secondaryContainer = Color(0xFF2E2C35),
    onSecondaryContainer = Color(0xFFE8E3EE),
    tertiary = Color(0xFFE8B98A),
    onTertiary = Color(0xFF3A2410),
    background = MeowPalette.Night,
    onBackground = MeowPalette.SoftWhite,
    surface = MeowPalette.Night,
    onSurface = MeowPalette.SoftWhite,
    surfaceVariant = Color(0xFF2B2A31),
    // Brighter than before (was B7B2BE): secondary text reads at a glance on every card.
    onSurfaceVariant = Color(0xFFC9C3D2),
    surfaceContainerLowest = Color(0xFF0D0C10),
    surfaceContainerLow = Color(0xFF18171C),
    surfaceContainer = MeowPalette.Charcoal,
    surfaceContainerHigh = Color(0xFF28272E),
    surfaceContainerHighest = Color(0xFF323139),
    // 3:1 or better on the background, cards and dialogs.
    outline = Color(0xFF7A7587),
    outlineVariant = Color(0xFF3C3A44),
    inverseSurface = Color(0xFFE9E5EF),
    inverseOnSurface = Color(0xFF1B1A1F),
    inversePrimary = MeowPalette.Violet,
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410),
    errorContainer = Color(0xFF5C1E1A),
    onErrorContainer = Color(0xFFFFDAD6),
    scrim = Color(0xFF000000),
)
