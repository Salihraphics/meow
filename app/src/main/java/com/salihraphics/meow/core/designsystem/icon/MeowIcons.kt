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

    private fun icon(name: String, pathData: String) =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
            .addPath(addPathNodes(pathData), fill = SolidColor(Color.Black))
            .build()
}
