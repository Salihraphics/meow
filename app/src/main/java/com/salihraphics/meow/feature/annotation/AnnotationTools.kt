package com.salihraphics.meow.feature.annotation

import androidx.compose.animation.AnimatedVisibility
import com.salihraphics.meow.core.designsystem.theme.MeowTokens
import androidx.compose.runtime.getValue
import androidx.compose.animation.core.tween
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.Switch
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
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
private val highlightNames = listOf(R.string.annotation_highlight_yellow, R.string.annotation_highlight_green)
private val highlightWidthNames = listOf(R.string.annotation_highlight_narrow, R.string.annotation_highlight_medium, R.string.annotation_highlight_wide)

/** What the tool controls can do; one set for the tablet panel and the phone rail. */
class AnnotationActions(
    val onTool: (AnnotationTool?) -> Unit = {},
    val onColor: (Int) -> Unit = {},
    val onWidth: (Int) -> Unit = {},
    val onHighlightColor: (Int) -> Unit = {},
    val onHighlightWidth: (Int) -> Unit = {},
    val onUndo: () -> Unit = {},
    val onRedo: () -> Unit = {},
    val onFingerDraws: (Boolean) -> Unit = {},
    val onShapeCorrection: (Boolean) -> Unit = {},
) {
    companion object {
        fun of(session: AnnotationSession, onUndo: () -> Unit, onRedo: () -> Unit) = AnnotationActions(
            onTool = session::selectTool,
            onColor = session::selectColor,
            onWidth = session::selectWidth,
            onHighlightColor = session::selectHighlightColor,
            onHighlightWidth = session::selectHighlightWidth,
            onUndo = onUndo,
            onRedo = onRedo,
            onFingerDraws = session::setFingerDraws,
            onShapeCorrection = session::setShapeCorrection,
        )
    }
}

/**
 * The tools in the study panel: navigate / pen / highlighter / eraser, undo / redo, and the
 * settings of the selected tool. Part of the panel, never over the page.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AnnotationToolsSection(state: AnnotationToolState, actions: AnnotationActions, modifier: Modifier = Modifier) {
    val toolbarLabel = stringResource(R.string.annotation_toolbar)
    Column(modifier.semantics { contentDescription = toolbarLabel }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.selectableGroup()) { ToolButtons(state, actions) }
            Spacer(Modifier.weight(1f))
            UndoRedo(state, actions)
        }
        // The selected tool's settings, labelled: colour, then thickness.
        AnimatedVisibility(state.tool == AnnotationTool.PEN || state.tool == AnnotationTool.HIGHLIGHTER) {
            Column(Modifier.padding(top = 6.dp)) {
                OptionLabel(stringResource(R.string.annotation_option_color))
                FlowRow(Modifier.selectableGroup()) {
                    if (state.tool == AnnotationTool.HIGHLIGHTER) HighlightColors(state, actions) else PenColors(state, actions)
                }
                OptionLabel(stringResource(R.string.annotation_option_width))
                FlowRow(Modifier.selectableGroup()) {
                    if (state.tool == AnnotationTool.HIGHLIGHTER) HighlightWidths(state, actions) else PenWidths(state, actions)
                }
            }
        }
        if (state.tool == AnnotationTool.PEN || state.tool == AnnotationTool.HIGHLIGHTER) {
            ToggleRow(
                MeowIcons.Shapes, stringResource(R.string.annotation_shape), stringResource(R.string.annotation_shape_desc),
                state.shapeCorrection, actions.onShapeCorrection,
            )
        }
        if (state.tool != null) {
            ToggleRow(
                MeowIcons.Finger, stringResource(R.string.annotation_finger_draws), stringResource(R.string.annotation_finger_draws_desc),
                state.fingerDraws, actions.onFingerDraws,
            )
            Text(
                stringResource(if (state.fingerDraws) R.string.annotation_two_finger_hint else R.string.annotation_stylus_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

/**
 * Phones: the tools in a slim column docked at the right edge of the screen, beside the pages
 * (never over them). [onAddNote] and [onOpenPanel] lead to notes and the full study panel.
 */
@Composable
fun AnnotationToolRail(
    state: AnnotationToolState,
    actions: AnnotationActions,
    onAddNote: () -> Unit,
    onOpenPanel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val toolbarLabel = stringResource(R.string.annotation_toolbar)
    Column(
        modifier
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .verticalScroll(rememberScrollState())
            .padding(vertical = 4.dp)
            .semantics { contentDescription = toolbarLabel },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        IconButton(onClick = onOpenPanel) { Icon(Icons.Filled.Menu, contentDescription = stringResource(R.string.study_panel_open)) }
        Column(Modifier.selectableGroup(), horizontalAlignment = Alignment.CenterHorizontally) { ToolButtons(state, actions) }
        IconButton(onClick = onAddNote) { Icon(MeowIcons.StickyNote, contentDescription = stringResource(R.string.note_add)) }
        HorizontalDivider(Modifier.width(32.dp).padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant)
        IconButton(onClick = actions.onUndo, enabled = state.canUndo) { Icon(MeowIcons.Undo, contentDescription = stringResource(R.string.annotation_undo)) }
        IconButton(onClick = actions.onRedo, enabled = state.canRedo) { Icon(MeowIcons.Redo, contentDescription = stringResource(R.string.annotation_redo)) }
        if (state.tool != null) {
            HorizontalDivider(Modifier.width(32.dp).padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant)
            RailToggle(MeowIcons.Finger, stringResource(R.string.annotation_finger_draws), state.fingerDraws, actions.onFingerDraws)
            if (state.tool != AnnotationTool.ERASER) {
                RailToggle(MeowIcons.Shapes, stringResource(R.string.annotation_shape), state.shapeCorrection, actions.onShapeCorrection)
            }
        }
        when (state.tool) {
            AnnotationTool.PEN -> {
                HorizontalDivider(Modifier.width(32.dp).padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant)
                Column(Modifier.selectableGroup(), horizontalAlignment = Alignment.CenterHorizontally) { PenColors(state, actions) }
                // Colours above, thicknesses below.
                HorizontalDivider(Modifier.width(20.dp).padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant)
                Column(Modifier.selectableGroup(), horizontalAlignment = Alignment.CenterHorizontally) { PenWidths(state, actions) }
            }
            AnnotationTool.HIGHLIGHTER -> {
                HorizontalDivider(Modifier.width(32.dp).padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant)
                Column(Modifier.selectableGroup(), horizontalAlignment = Alignment.CenterHorizontally) { HighlightColors(state, actions) }
                // Colours above, thicknesses below.
                HorizontalDivider(Modifier.width(20.dp).padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant)
                Column(Modifier.selectableGroup(), horizontalAlignment = Alignment.CenterHorizontally) { HighlightWidths(state, actions) }
            }
            else -> Unit
        }
    }
}

/** An on/off setting of the drawing tools, with a one-line explanation under it. */
@Composable
private fun ToggleRow(icon: ImageVector, title: String, description: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = MeowTokens.TouchTarget)
            .padding(top = 4.dp)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}

/** Phones: an on/off setting as a small icon toggle on the rail. */
@Composable
private fun RailToggle(icon: ImageVector, label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    IconToggleButton(checked = checked, onCheckedChange = onChange, modifier = Modifier.semantics { contentDescription = label }) {
        Box(
            Modifier
                .size(36.dp)
                .background(if (checked) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent, MeowTokens.ControlShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = if (checked) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun OptionLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
}

@Composable
private fun ToolButtons(state: AnnotationToolState, actions: AnnotationActions) {
    ToolButton(MeowIcons.Hand, stringResource(R.string.annotation_tool_navigate), state.tool == null) { actions.onTool(null) }
    ToolButton(Icons.Filled.Create, stringResource(R.string.annotation_tool_pen), state.tool == AnnotationTool.PEN) { actions.onTool(AnnotationTool.PEN) }
    ToolButton(MeowIcons.Highlighter, stringResource(R.string.annotation_tool_highlighter), state.tool == AnnotationTool.HIGHLIGHTER) {
        actions.onTool(AnnotationTool.HIGHLIGHTER)
    }
    ToolButton(MeowIcons.Eraser, stringResource(R.string.annotation_tool_eraser), state.tool == AnnotationTool.ERASER) { actions.onTool(AnnotationTool.ERASER) }
}

@Composable
private fun UndoRedo(state: AnnotationToolState, actions: AnnotationActions) {
    IconButton(onClick = actions.onUndo, enabled = state.canUndo) { Icon(MeowIcons.Undo, contentDescription = stringResource(R.string.annotation_undo)) }
    IconButton(onClick = actions.onRedo, enabled = state.canRedo) { Icon(MeowIcons.Redo, contentDescription = stringResource(R.string.annotation_redo)) }
}

/**
 * A tool: unselected, a plain icon; selected, a filled tile in the primary colour with the icon
 * in its contrasting colour. The tile's shape, not only its colour, says which tool is on.
 */
@Composable
private fun ToolButton(icon: ImageVector, label: String, selected: Boolean, onSelect: () -> Unit) {
    val container by animateColorAsState(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent, tween(120), label = "tool")
    val content by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
        tween(120),
        label = "toolIcon",
    )
    Box(
        Modifier
            .size(MeowTokens.TouchTarget)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.size(MeowTokens.ToolTile).background(container, MeowTokens.ControlShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(MeowTokens.ToolIcon))
        }
    }
}

@Composable
private fun PenColors(state: AnnotationToolState, actions: AnnotationActions) {
    AnnotationToolState.PEN_COLORS.forEachIndexed { index, color ->
        Swatch(selected = index == state.colorIndex, label = stringResource(colorNames[index]), onClick = { actions.onColor(index) }) {
            Box(Modifier.size(22.dp).background(Color(color), CircleShape).border(1.dp, MaterialTheme.colorScheme.outline, CircleShape))
        }
    }
}

@Composable
private fun PenWidths(state: AnnotationToolState, actions: AnnotationActions) {
    AnnotationToolState.PEN_WIDTHS.forEachIndexed { index, _ ->
        Swatch(selected = index == state.widthIndex, label = stringResource(widthNames[index]), onClick = { actions.onWidth(index) }) {
            // Thickness, drawn in the text colour so a black pen still shows on a dark panel.
            Box(Modifier.size(listOf(6.dp, 10.dp, 15.dp)[index]).background(MaterialTheme.colorScheme.onSurface, CircleShape))
        }
    }
}

@Composable
private fun HighlightColors(state: AnnotationToolState, actions: AnnotationActions) {
    AnnotationToolState.HIGHLIGHT_COLORS.forEachIndexed { index, color ->
        Swatch(selected = index == state.highlightColorIndex, label = stringResource(highlightNames[index]), onClick = { actions.onHighlightColor(index) }) {
            Box(Modifier.size(24.dp, 14.dp).background(Color(color).copy(alpha = 0.9f), RoundedCornerShape(3.dp)))
        }
    }
}

@Composable
private fun HighlightWidths(state: AnnotationToolState, actions: AnnotationActions) {
    val sizes: List<Dp> = listOf(6.dp, 11.dp, 17.dp)
    AnnotationToolState.HIGHLIGHT_WIDTHS.forEachIndexed { index, _ ->
        Swatch(selected = index == state.highlightWidthIndex, label = stringResource(highlightWidthNames[index]), onClick = { actions.onHighlightWidth(index) }) {
            Box(Modifier.size(22.dp, sizes[index]).background(Color(state.highlightStyle.color).copy(alpha = 0.9f), RoundedCornerShape(2.dp)))
        }
    }
}

/** A colour or width choice; the selected one has a thick ring with a gap, visible on any background. */
@Composable
private fun Swatch(selected: Boolean, label: String, onClick: () -> Unit, content: @Composable () -> Unit) {
    val ring = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent
    Box(
        Modifier
            .size(44.dp)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(36.dp).border(3.dp, ring, CircleShape).padding(5.dp), contentAlignment = Alignment.Center) { content() }
    }
}
