package com.salihraphics.meow.core.designsystem.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * MEOW's design tokens beyond Material's: one place for spacing, radii, sizes and the few
 * surface roles Material doesn't name. Colours live in [MeowPalette] and the colour schemes.
 */
object MeowTokens {
    object Space {
        val xs = 4.dp
        val s = 8.dp
        val m = 12.dp
        val l = 16.dp
        val xl = 24.dp
    }

    object Radius {
        val control = 12.dp
        val card = 16.dp
        val sheet = 24.dp
    }

    /** Minimum touch target; tool buttons on tablets get a little more for stylus users. */
    val TouchTarget = 48.dp
    val ToolIcon = 22.dp
    val ToolTile = 44.dp

    val CardShape = RoundedCornerShape(Radius.card)
    val ControlShape = RoundedCornerShape(Radius.control)
}

/** A card on the screen background: one clear step off it in either theme. */
val cardColor: Color
    @Composable @ReadOnlyComposable
    get() = if (LocalIsDarkTheme.current) MaterialTheme.colorScheme.surfaceContainer else MaterialTheme.colorScheme.surfaceContainerLowest

/** Something above a card: speech bubbles, dialogs, menus. */
val raisedColor: Color
    @Composable @ReadOnlyComposable
    get() = if (LocalIsDarkTheme.current) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surfaceContainerLowest

/** A field or nested panel inside a card. */
val insetColor: Color
    @Composable @ReadOnlyComposable
    get() = if (LocalIsDarkTheme.current) MaterialTheme.colorScheme.surfaceContainerHighest else MaterialTheme.colorScheme.surfaceContainer

/** The hairline edge every card has, so cards separate from the background in both themes. */
val cardBorder: BorderStroke
    @Composable @ReadOnlyComposable
    get() = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
