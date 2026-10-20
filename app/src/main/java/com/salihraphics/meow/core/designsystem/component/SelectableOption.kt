package com.salihraphics.meow.core.designsystem.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

enum class SelectionKind { Single, Multiple }

/**
 * A full-width answer card. Single choice behaves as a radio (selectable); multiple choice
 * behaves as a checkbox (toggleable), so screen readers announce the right semantics.
 */
@Composable
fun SelectableOptionCard(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
    kind: SelectionKind = SelectionKind.Single,
) {
    val scheme = MaterialTheme.colorScheme
    val container by animateColorAsState(
        if (selected) scheme.primaryContainer else scheme.surfaceContainerLow,
        label = "optionContainer",
    )
    val border by animateColorAsState(
        if (selected) scheme.primary else scheme.outlineVariant,
        label = "optionBorder",
    )
    val interaction = if (kind == SelectionKind.Single) {
        Modifier.selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
    } else {
        Modifier.toggleable(value = selected, role = Role.Checkbox, onValueChange = { onClick() })
    }
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = container,
        border = BorderStroke(if (selected) 1.5.dp else 1.dp, border),
    ) {
        Row(
            modifier = Modifier
                .then(interaction)
                .heightIn(min = 60.dp)
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (selected) scheme.onPrimaryContainer else scheme.onSurface,
                )
                if (description != null) {
                    Text(
                        description,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (selected) scheme.onPrimaryContainer.copy(alpha = 0.75f) else scheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            SelectionIndicator(selected = selected, kind = kind)
        }
    }
}

@Composable
fun SelectionIndicator(selected: Boolean, kind: SelectionKind, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val ring by animateColorAsState(if (selected) scheme.primary else scheme.outline, label = "indicatorRing")
    Box(
        modifier = modifier
            .size(24.dp)
            .border(2.dp, ring, CircleShape)
            .padding(if (kind == SelectionKind.Single) 5.dp else 0.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            when (kind) {
                SelectionKind.Single -> Box(Modifier.size(14.dp).background(scheme.primary, CircleShape))
                SelectionKind.Multiple -> Box(
                    Modifier.size(24.dp).background(scheme.primary, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Check, contentDescription = null, tint = scheme.onPrimary, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

/** Compact pill for multi-select lists such as subjects. */
@Composable
fun SelectableChip(
    label: String,
    selected: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val container by animateColorAsState(
        if (selected) scheme.primaryContainer else scheme.surfaceContainerLow,
        label = "chipContainer",
    )
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = container,
        border = BorderStroke(if (selected) 1.5.dp else 1.dp, if (selected) scheme.primary else scheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier
                .toggleable(value = selected, role = Role.Checkbox, onValueChange = { onToggle() })
                .heightIn(min = 48.dp)
                .padding(start = if (selected) 12.dp else 18.dp, end = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (selected) {
                Icon(Icons.Filled.Check, contentDescription = null, tint = scheme.primary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
            }
            Text(
                label,
                style = MaterialTheme.typography.titleSmall,
                color = if (selected) scheme.onPrimaryContainer else scheme.onSurface,
            )
        }
    }
}
