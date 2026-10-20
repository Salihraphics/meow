package com.salihraphics.meow.core.designsystem.theme

import android.app.Activity
import android.provider.Settings
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

@Immutable
data class MeowMotion(
    /** Idle loops (blinking, tail, breathing). Off when the user or the system asks for less motion. */
    val ambientAnimations: Boolean = true,
)

val LocalMeowMotion = staticCompositionLocalOf { MeowMotion() }

/** True when the current theme is dark; lets components pick contrast-sensitive details. */
val LocalIsDarkTheme = staticCompositionLocalOf { false }

private val MeowShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

@Composable
fun MeowTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    mascotAnimationsEnabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    val systemAllowsMotion = rememberSystemAllowsMotion()
    val motion = MeowMotion(ambientAnimations = mascotAnimationsEnabled && systemAllowsMotion)

    StatusBarIconsEffect(darkIcons = !darkTheme)

    CompositionLocalProvider(
        LocalMeowMotion provides motion,
        LocalIsDarkTheme provides darkTheme,
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            typography = MeowTypography,
            shapes = MeowShapes,
            content = content,
        )
    }
}

@Composable
private fun rememberSystemAllowsMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f
    }
}

/** Keeps status/navigation bar icons readable on the current background. */
@Composable
fun StatusBarIconsEffect(darkIcons: Boolean) {
    if (LocalInspectionMode.current) return
    val view = LocalView.current
    val window = (view.context as? Activity)?.window ?: return
    SideEffect {
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = darkIcons
            isAppearanceLightNavigationBars = darkIcons
        }
    }
}

/** Temporarily forces light bar icons (e.g. on the purple welcome screen) and restores on exit. */
@Composable
fun LightBarIconsWhileVisible(restoreDarkIcons: Boolean) {
    if (LocalInspectionMode.current) return
    val view = LocalView.current
    val window = (view.context as? Activity)?.window ?: return
    DisposableEffect(window, restoreDarkIcons) {
        val controller = WindowCompat.getInsetsController(window, view)
        controller.isAppearanceLightStatusBars = false
        controller.isAppearanceLightNavigationBars = false
        onDispose {
            controller.isAppearanceLightStatusBars = restoreDarkIcons
            controller.isAppearanceLightNavigationBars = restoreDarkIcons
        }
    }
}
