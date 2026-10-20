package com.salihraphics.meow.core.designsystem.component

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.salihraphics.meow.core.designsystem.theme.LocalIsDarkTheme

enum class BubbleTail { BottomStart, BottomEnd }

/** The cat's speech bubble. Text changes cross-fade and are announced politely to screen readers. */
@Composable
fun SpeechBubble(
    text: String,
    modifier: Modifier = Modifier,
    tail: BubbleTail = BubbleTail.BottomStart,
) {
    Surface(
        modifier = modifier
            .widthIn(max = 260.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
        shape = BubbleShape(tail),
        // Lifted above the background in both themes.
        color = if (LocalIsDarkTheme.current) MaterialTheme.colorScheme.surfaceContainerHighest
        else MaterialTheme.colorScheme.surfaceContainerLowest,
        shadowElevation = 2.dp,
    ) {
        AnimatedContent(
            targetState = text,
            transitionSpec = { (fadeIn() + scaleIn(initialScale = 0.96f)) togetherWith fadeOut() },
            label = "bubbleText",
        ) { value ->
            Text(
                value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 12.dp + TailHeight),
            )
        }
    }
}

private val TailHeight = 10.dp

private class BubbleShape(private val tail: BubbleTail) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val tailH = with(density) { TailHeight.toPx() }
        val r = with(density) { 18.dp.toPx() }
        val bodyBottom = size.height - tailH
        val atStart = (tail == BubbleTail.BottomStart) == (layoutDirection == LayoutDirection.Ltr)
        val tailBase = with(density) { 28.dp.toPx() }
        val tailX = if (atStart) tailBase else size.width - tailBase
        val path = Path().apply {
            addRoundRect(RoundRect(0f, 0f, size.width, bodyBottom, CornerRadius(r, r)))
            moveTo(tailX - tailH, bodyBottom - 1f)
            lineTo(if (atStart) tailX - tailH * 1.2f else tailX + tailH * 1.2f, size.height)
            lineTo(tailX + tailH, bodyBottom - 1f)
            close()
        }
        return Outline.Generic(path)
    }
}
