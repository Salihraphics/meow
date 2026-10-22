package com.salihraphics.meow.core.designsystem.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertTrue
import org.junit.Test

/** Text and controls stay readable in both themes (WCAG: 4.5:1 text, 3:1 large text and UI parts). */
class ThemeContrastTest {
    private fun ratio(a: Color, b: Color): Double {
        val (hi, lo) = listOf(a.luminance(), b.luminance()).sortedDescending()
        return (hi + 0.05) / (lo + 0.05)
    }

    private fun check(name: String, fg: Color, bg: Color, min: Double) {
        val r = ratio(fg, bg)
        assertTrue("$name: %.2f < $min".format(r), r >= min)
    }

    @Test
    fun darkThemeTextAndControlsAreReadable() = with(DarkColors) {
        for ((bgName, bg) in listOf("background" to background, "card" to surfaceContainer, "raised" to surfaceContainerHigh)) {
            check("text on $bgName", onSurface, bg, 7.0)
            check("secondary text on $bgName", onSurfaceVariant, bg, 4.5)
            check("primary on $bgName", primary, bg, 4.5)
            check("outline on $bgName", outline, bg, 3.0)
        }
        check("on primary", onPrimary, primary, 4.5)
        check("on primary container", onPrimaryContainer, primaryContainer, 4.5)
        // Soft white, not pure white, for long reading.
        assertTrue(onSurface != Color.White)
    }

    @Test
    fun lightThemeTextAndControlsAreReadable() = with(LightColors) {
        for ((bgName, bg) in listOf("background" to background, "card" to surfaceContainerLowest, "inset" to surfaceContainer)) {
            check("text on $bgName", onSurface, bg, 7.0)
            check("secondary text on $bgName", onSurfaceVariant, bg, 4.5)
            check("primary on $bgName", primary, bg, 4.5)
            check("outline on $bgName", outline, bg, 3.0)
        }
        check("on primary", onPrimary, primary, 4.5)
        check("on primary container", onPrimaryContainer, primaryContainer, 4.5)
        // Cream paper, not a stark white screen.
        assertTrue(background != Color.White && surfaceContainerLowest != Color.White)
    }

    @Test
    fun surfacesStepUpInTheDarkTheme() = with(DarkColors) {
        val steps = listOf(background, surfaceContainer, surfaceContainerHigh, surfaceContainerHighest).map { it.luminance() }
        assertTrue(steps.zipWithNext().all { (a, b) -> b > a })
    }
}
