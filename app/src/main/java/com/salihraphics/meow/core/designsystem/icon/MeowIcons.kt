package com.salihraphics.meow.core.designsystem.icon

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * Icons missing from material-icons-core, defined here instead of pulling in the large
 * extended icon set. Undo, Redo and Hand are Material Icons paths; Eraser is the Material
 * Design Icons "eraser" path. Both sets are Apache License 2.0.
 */
object MeowIcons {
    val Undo: ImageVector by lazy {
        icon("Undo", "M12.5,8c-2.65,0 -5.05,0.99 -6.9,2.6L2,7v9h9l-3.62,-3.62c1.39,-1.16 3.16,-1.88 5.12,-1.88 3.54,0 6.55,2.31 7.6,5.5l2.37,-0.78C21.08,11.03 17.15,8 12.5,8z")
    }

    val Redo: ImageVector by lazy {
        icon("Redo", "M18.4,10.6C16.55,8.99 14.15,8 11.5,8c-4.65,0 -8.58,3.03 -9.96,7.22L3.9,16c1.05,-3.19 4.05,-5.5 7.6,-5.5 1.95,0 3.73,0.72 5.12,1.88L13,16h9V7l-3.6,3.6z")
    }

    val Hand: ImageVector by lazy {
        icon(
            "Hand",
            "M23,5.5V20c0,2.2 -1.8,4 -4,4h-7.3c-1.08,0 -2.1,-0.43 -2.85,-1.19L1,14.83c0,0 1.26,-1.23 1.3,-1.25 " +
                "0.22,-0.19 0.49,-0.29 0.79,-0.29 0.22,0 0.42,0.06 0.6,0.16 0.04,0.01 4.31,2.46 4.31,2.46V4c0,-0.83 " +
                "0.67,-1.5 1.5,-1.5S11,3.17 11,4v7h1V1.5C12,0.67 12.67,0 13.5,0S15,0.67 15,1.5V11h1V2.5C16,1.67 " +
                "16.67,1 17.5,1S19,1.67 19,2.5V11h1V5.5C20,4.67 20.67,4 21.5,4S23,4.67 23,5.5z",
        )
    }

    val Eraser: ImageVector by lazy {
        icon(
            "Eraser",
            "M16.24,3.56l4.95,4.94c0.78,0.79 0.78,2.05 0,2.84L12,20.53a4.008,4.008 0 0,1 -5.66,0L2.81,17" +
                "c-0.78,-0.79 -0.78,-2.05 0,-2.84l10.6,-10.6c0.79,-0.78 2.05,-0.78 2.83,0M4.22,15.58l3.54,3.53" +
                "c0.78,0.79 2.04,0.79 2.83,0l3.53,-3.53 -4.95,-4.95 -4.95,4.95z",
        )
    }

    /** Material "border_color": a marker over a bar, read as the highlighter. */
    val Highlighter: ImageVector by lazy {
        icon(
            "Highlighter",
            "M22,24H2v-4h20V24zM13.06,5.19l3.75,3.75L7.75,18H4v-3.75L13.06,5.19zM17.88,7.87l-3.75,-3.75l1.83,-1.83" +
                "c0.39,-0.39 1.02,-0.39 1.41,0l2.34,2.34c0.39,0.39 0.39,1.02 0,1.41L17.88,7.87z",
        )
    }

    /** Material "sticky_note_2". */
    val StickyNote: ImageVector by lazy {
        icon(
            "StickyNote",
            "M19,3H4.99C3.89,3 3,3.9 3,5l0.01,14c0,1.1 0.89,2 1.99,2h10l6,-6V5C21,3.9 20.1,3 19,3zM7,8h10v2H7V8z" +
                "M12,14H7v-2h5V14zM14,19.5V14h5.5L14,19.5z",
        )
    }

    val RotateLeft: ImageVector by lazy {
        icon(
            "RotateLeft",
            "M7.11,8.53L5.7,7.11C4.8,8.27 4.24,9.61 4.07,11h2.02c0.14,-0.87 0.49,-1.72 1.02,-2.47zM6.09,13L4.07,13" +
                "c0.17,1.39 0.72,2.73 1.62,3.89l1.41,-1.42c-0.52,-0.75 -0.87,-1.59 -1.01,-2.47zM7.1,18.32c1.16,0.9 2.51,1.44 3.9,1.61" +
                "L11,17.9c-0.87,-0.15 -1.71,-0.49 -2.46,-1.03L7.1,18.32zM13,4.07L13,1L8.45,5.55 13,10L13,6.09c2.84,0.48 5,2.94 5,5.91" +
                "s-2.16,5.43 -5,5.91v2.02c3.95,-0.49 7,-3.85 7,-7.93s-3.05,-7.44 -7,-7.93z",
        )
    }

    val RotateRight: ImageVector by lazy {
        icon(
            "RotateRight",
            "M15.55,5.55L11,1v3.07C7.06,4.56 4,7.92 4,12s3.05,7.44 7,7.93v-2.02c-2.84,-0.48 -5,-2.94 -5,-5.91" +
                "s2.16,-5.43 5,-5.91L11,10l4.55,-4.45zM19.93,11c-0.17,-1.39 -0.72,-2.73 -1.62,-3.89l-1.42,1.42c0.54,0.75 0.88,1.6 1.02,2.47h2.02z" +
                "M13,17.9v2.02c1.39,-0.17 2.74,-0.71 3.9,-1.61l-1.44,-1.44c-0.75,0.54 -1.59,0.89 -2.46,1.03zM16.89,15.48l1.42,1.41" +
                "c0.9,-1.16 1.45,-2.5 1.62,-3.89h-2.02c-0.14,0.87 -0.48,1.72 -1.02,2.48z",
        )
    }

    /** Material "open_with": four arrows, for moving a note. */
    val Move: ImageVector by lazy {
        icon("Move", "M10,9h4V6h3l-5,-5 -5,5h3v3zM9,10H6V7l-5,5 5,5v-3h3v-4zM23,12l-5,-5v3h-3v4h3v3l5,-5zM14,15h-4v3H7l5,5 5,-5h-3v-3z")
    }

    /** Material "folder". */
    val Folder: ImageVector by lazy {
        icon("Folder", "M10,4H4c-1.1,0 -1.99,0.9 -1.99,2L2,18c0,1.1 0.9,2 2,2h16c1.1,0 2,-0.9 2,-2V8c0,-1.1 -0.9,-2 -2,-2h-8l-2,-2z")
    }

    /** Material "sort". */
    val Sort: ImageVector by lazy { icon("Sort", "M3,18h6v-2H3v2zM3,6v2h18V6H3zM3,13h12v-2H3v2z") }

    /** Material "push_pin". */
    val Pin: ImageVector by lazy {
        icon("Pin", "M16,9V4l1,0c0.55,0 1,-0.45 1,-1v0c0,-0.55 -0.45,-1 -1,-1H7C6.45,2 6,2.45 6,3v0c0,0.55 0.45,1 1,1l1,0v5c0,1.66 -1.34,3 -3,3h0v2h5.97v7l1,1l1,-1v-7H19v-2h0C17.34,12 16,10.66 16,9z")
    }

    val Pause: ImageVector by lazy { icon("Pause", "M6,19h4V5H6v14zM14,5v14h4V5h-4z") }

    /** Material "touch_app": a finger. */
    val Finger: ImageVector by lazy {
        icon(
            "Finger",
            "M9,11.24V7.5C9,6.12 10.12,5 11.5,5S14,6.12 14,7.5v3.74c1.21,-0.81 2,-2.18 2,-3.74C16,5.01 13.99,3 11.5,3S7,5.01 7,7.5c0,1.56 0.79,2.93 2,3.74z" +
                "M18.84,15.87l-4.54,-2.26c-0.17,-0.07 -0.35,-0.11 -0.54,-0.11H13v-6C13,6.67 12.33,6 11.5,6S10,6.67 10,7.5v10.74l-3.43,-0.72c-0.08,-0.01 -0.15,-0.03 -0.24,-0.03 -0.31,0 -0.59,0.13 -0.79,0.33l-0.79,0.8 4.94,4.94c0.27,0.27 0.65,0.44 1.06,0.44h6.79c0.75,0 1.33,-0.55 1.44,-1.28l0.75,-5.27c0.01,-0.07 0.02,-0.14 0.02,-0.2 0,-0.62 -0.38,-1.16 -0.91,-1.38z",
        )
    }

    /** Material "category": a triangle, a circle and a square (shape correction). */
    val Shapes: ImageVector by lazy {
        icon("Shapes", "M12,2l-5.5,9h11L12,2zM17.5,13c-2.49,0 -4.5,2.01 -4.5,4.5s2.01,4.5 4.5,4.5 4.5,-2.01 4.5,-4.5 -2.01,-4.5 -4.5,-4.5zM3,21.5h8v-8H3v8z")
    }

    private fun icon(name: String, pathData: String) =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
            .addPath(addPathNodes(pathData), fill = SolidColor(Color.Black))
            .build()
}
