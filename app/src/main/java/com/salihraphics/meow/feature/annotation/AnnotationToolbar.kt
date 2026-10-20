package com.salihraphics.meow.feature.annotation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Create
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.salihraphics.meow.R
import com.salihraphics.meow.core.designsystem.icon.MeowIcons

private val colorNames = listOf(
    R.string.annotation_color_black,
    R.string.annotation_color_blue,
    R.string.annotation_color_red,
    R.string.annotation_color_purple,
    R.string.annotation_color_green,
)
private val widthNames = listOf(R.string.annotation_width_thin, R.string.annotation_width_medium, R.string.annotation_width_thick)

/** Navigate / pen / eraser, undo / redo, and pen colour and width while the pen is selected. */
@Composable
fun AnnotationToolbar(
    state: AnnotationToolState,
    onTool: (AnnotationTool?) -> Unit,
    onColor: (Int) -> Unit,
    onWidth: (Int) -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val toolbarLabel = stringResource(R.string.annotation_toolbar)
    Surface(
        modifier = modifier.semantics { contentDescription = toolbarLabel },
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        shadowElevation = 4.dp,
    ) {
        Column(Modifier.padding(horizontal = 6.dp, vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            AnimatedVisibility(visible = state.tool == AnnotationTool.PEN) {
                PenOptions(state, onColor, onWidth)
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.selectableGroup()) {
                ToolButton(MeowIcons.Hand, stringResource(R.string.annotation_tool_navigate), state.tool == null) { onTool(null) }
                ToolButton(Icons.Filled.Create, stringResource(R.string.annotation_tool_pen), state.tool == AnnotationTool.PEN) {
                    onTool(AnnotationTool.PEN)
                }
                ToolButton(MeowIcons.Eraser, stringResource(R.string.annotation_tool_eraser), state.tool == AnnotationTool.ERASER) {
                    onTool(AnnotationTool.ERASER)
                }
                VerticalDivider(Modifier.height(28.dp).padding(horizontal = 4.dp), color = MaterialTheme.colorScheme.outlineVariant)
                IconButton(onClick = onUndo, enabled = state.canUndo) {
                    Icon(MeowIcons.Undo, contentDescription = stringResource(R.string.annotation_undo))
                }
                IconButton(onClick = onRedo, enabled = state.canRedo) {
                    Icon(MeowIcons.Redo, contentDescription = stringResource(R.string.annotation_redo))
                }
            }
        }
    }
}

@Composable
private fun ToolButton(icon: ImageVector, label: String, selected: Boolean, onSelect: () -> Unit) {
    IconToggleButton(
        checked = selected,
        onCheckedChange = { onSelect() },
        colors = IconButtonDefaults.iconToggleButtonColors(
            checkedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            checkedContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    ) {
        Icon(icon, contentDescription = label, modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun PenOptions(state: AnnotationToolState, onColor: (Int) -> Unit, onWidth: (Int) -> Unit) {
    Row(
        Modifier.padding(top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(Modifier.selectableGroup()) {
            AnnotationToolState.PEN_COLORS.forEachIndexed { index, color ->
                val name = stringResource(colorNames[index])
                Swatch(selected = index == state.colorIndex, label = name, onClick = { onColor(index) }) {
                    Box(Modifier.size(22.dp).background(Color(color), CircleShape))
                }
            }
        }
        Spacer(Modifier.width(6.dp))
        Row(Modifier.selectableGroup()) {
            AnnotationToolState.PEN_WIDTHS.forEachIndexed { index, _ ->
                Swatch(selected = index == state.widthIndex, label = stringResource(widthNames[index]), onClick = { onWidth(index) }) {
                    Box(
                        Modifier
                            .size(listOf(6.dp, 10.dp, 15.dp)[index])
                            .background(Color(state.penStyle.color), CircleShape),
                    )
                }
            }
        }
    }
}

@Composable
private fun Swatch(selected: Boolean, label: String, onClick: () -> Unit, content: @Composable () -> Unit) {
    val ring = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent
    Box(
        Modifier
            .size(40.dp)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(32.dp).border(2.dp, ring, CircleShape), contentAlignment = Alignment.Center) { content() }
    }
}
