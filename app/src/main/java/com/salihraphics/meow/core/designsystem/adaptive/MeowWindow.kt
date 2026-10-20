package com.salihraphics.meow.core.designsystem.adaptive

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Material window width classes (breakpoints at 600dp and 840dp). */
enum class WidthClass { Compact, Medium, Expanded }

@Immutable
data class MeowWindow(
    val width: WidthClass,
    /** Under 480dp tall: typically a phone in landscape. */
    val shortHeight: Boolean,
) {
    /** Side-by-side panes: tablets in landscape, open foldables, phones in landscape. */
    val useTwoPanes: Boolean
        get() = width == WidthClass.Expanded || (width == WidthClass.Medium && shortHeight)

    /**
     * Tall and wide (tablets): keep actions right under their content and centre the group,
     * instead of pinning buttons to a far-away bottom edge.
     */
    val keepActionsNearContent: Boolean
        get() = width != WidthClass.Compact && !shortHeight

    /** Enough width for two content columns, e.g. on the home dashboard. */
    val useTwoColumns: Boolean
        get() = width != WidthClass.Compact

    companion object {
        fun fromDp(widthDp: Int, heightDp: Int) = MeowWindow(
            width = when {
                widthDp >= 840 -> WidthClass.Expanded
                widthDp >= 600 -> WidthClass.Medium
                else -> WidthClass.Compact
            },
            shortHeight = heightDp < 480,
        )
    }
}

val LocalMeowWindow = staticCompositionLocalOf { MeowWindow(WidthClass.Compact, shortHeight = false) }

/** Computed from the window's current size, so it follows rotation, resizing and multi-window. */
@Composable
fun rememberMeowWindow(): MeowWindow {
    val config = LocalConfiguration.current
    return remember(config.screenWidthDp, config.screenHeightDp) {
        MeowWindow.fromDp(config.screenWidthDp, config.screenHeightDp)
    }
}

/** Shared content widths, so text never runs edge to edge on large screens. */
object MeowWidths {
    /** Comfortable reading width for a single column of text and options. */
    val Readable: Dp = 560.dp
    /** Upper bound for a two-pane or two-column layout. */
    val Wide: Dp = 1120.dp
}

/** Centres [content] horizontally and caps its width. Height comes from [modifier]. */
@Composable
fun CenteredContent(maxWidth: Dp, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        Box(Modifier.widthIn(max = maxWidth).fillMaxWidth()) { content() }
    }
}

/** Scrolls when content is too tall, and centres it vertically when there is room to spare. */
@Composable
fun CenteredScrollColumn(
    modifier: Modifier = Modifier,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    content: @Composable ColumnScope.() -> Unit,
) {
    BoxWithConstraints(modifier) {
        val minHeight = maxHeight
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
            Column(
                Modifier.fillMaxWidth().heightIn(min = minHeight),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = horizontalAlignment,
                content = content,
            )
        }
    }
}

/** Two panes side by side, centred and capped at [MeowWidths.Wide]. */
@Composable
fun TwoPaneRow(
    modifier: Modifier = Modifier,
    gap: Dp = 48.dp,
    content: @Composable RowScope.() -> Unit,
) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Row(
            Modifier.widthIn(max = MeowWidths.Wide).fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(gap),
            content = content,
        )
    }
}
