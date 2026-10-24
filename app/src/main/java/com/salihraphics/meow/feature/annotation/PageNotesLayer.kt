package com.salihraphics.meow.feature.annotation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.salihraphics.meow.R
import com.salihraphics.meow.core.designsystem.icon.MeowIcons
import com.salihraphics.meow.domain.model.StickyNote
import com.salihraphics.meow.domain.model.Stroke
import com.salihraphics.meow.feature.viewer.turned
import kotlin.math.roundToInt

/**
 * The Post-it notes of one page, above its ink. A note takes every touch that lands on it, so
 * nothing is ever drawn on the page underneath it. A new note is empty: with the pen or
 * highlighter you write on the note itself (the ink is the note's and moves and resizes with
 * it), and "Yazı yaz" types text on it. With the hand tool, tap selects, a tap on a selected
 * note edits its text and dragging moves it. A selected note has handles to move, resize,
 * type and delete, whatever the tool. Positions are page fractions; the text is turned back
 * upright when the page is turned.
 */
@Composable
fun PageNotesLayer(pageIndex: Int, session: AnnotationSession, rotation: Int, tools: AnnotationToolState, modifier: Modifier = Modifier) {
    val notes by remember(pageIndex) { session.notes(pageIndex) }.collectAsState()
    val strokes by remember(pageIndex) { session.strokes(pageIndex) }.collectAsState()
    val selected by session.selectedNote.collectAsState()
    val list = notes ?: return
    BoxWithConstraints(modifier.fillMaxSize()) {
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        if (w <= 0f || h <= 0f) return@BoxWithConstraints
        val ink = strokes?.filter { it.noteId != null }?.groupBy { it.noteId }
        list.forEach { note ->
            key(note.id) { NoteView(note, ink?.get(note.id).orEmpty().takeIf { strokes != null }, w, h, selected == note.id, rotation, tools, session) }
        }
    }
}

@Composable
private fun NoteView(
    note: StickyNote,
    ink: List<Stroke>?,
    pageW: Float,
    pageH: Float,
    isSelected: Boolean,
    rotation: Int,
    tools: AnnotationToolState,
    session: AnnotationSession,
) {
    val density = LocalDensity.current
    // Gesture handlers outlive recompositions, so they read everything through state.
    val latest by rememberUpdatedState(note)
    val page by rememberUpdatedState(Size(pageW, pageH))
    // Movement not yet saved, in page pixels; saved when the finger lifts.
    var move by remember(note.id) { mutableStateOf(Offset.Zero) }
    var grow by remember(note.id) { mutableStateOf(Offset.Zero) }

    /** Where the note is shown now, in page pixels: saved place plus the unsaved drag, kept on the page. */
    fun placed(): Rect {
        val w = (latest.width * page.width + grow.x).coerceIn(StickyNote.MIN_SIZE * page.width, page.width)
        val h = (latest.height * page.height + grow.y).coerceIn(StickyNote.MIN_SIZE * page.height, page.height)
        val x = (latest.x * page.width + move.x).coerceIn(0f, page.width - w)
        val y = (latest.y * page.height + move.y).coerceIn(0f, page.height - h)
        return Rect(x, y, x + w, y + h)
    }

    fun commit() {
        if (move == Offset.Zero && grow == Offset.Zero) return
        val r = placed()
        session.updateNote(latest.copy(x = r.left / page.width, y = r.top / page.height, width = r.width / page.width, height = r.height / page.height))
        move = Offset.Zero
        grow = Offset.Zero
    }

    val shown = placed()
    val left = shown.left
    val top = shown.top
    val width = shown.width
    val height = shown.height
    val label = stringResource(
        R.string.note_cd,
        note.text.ifBlank { stringResource(if (ink.isNullOrEmpty()) R.string.note_empty else R.string.note_handwritten) },
    )
    val editLabel = stringResource(R.string.note_edit)

    Box(
        Modifier
            .offset { IntOffset(left.roundToInt(), top.roundToInt()) }
            .size(with(density) { width.toDp() }, with(density) { height.toDp() }),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .shadow(if (isSelected) 6.dp else 2.dp, RoundedCornerShape(2.dp))
                .background(Color(note.color), RoundedCornerShape(2.dp))
                .border(if (isSelected) 2.dp else 0.dp, if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent, RoundedCornerShape(2.dp))
                .then(
                    // With a drawing tool the note is written on (below); with the hand it moves.
                    if (tools.tool != null) {
                        Modifier
                    } else {
                        Modifier
                            .pointerInput(note.id) {
                                detectTapGestures(onTap = {
                                    if (session.selectedNote.value == latest.id) session.editNote(latest) else session.selectNote(latest.id)
                                })
                            }
                            .pointerInput(note.id) {
                                detectDragGestures(
                                    onDragStart = { session.selectNote(latest.id) },
                                    onDragEnd = { commit() },
                                    onDragCancel = { commit() },
                                ) { change, amount ->
                                    change.consume()
                                    move += amount
                                }
                            }
                    },
                )
                .semantics {
                    contentDescription = label
                    role = Role.Button
                    this.selected = isSelected
                    onClick(editLabel) { session.selectNote(latest.id); session.editNote(latest); true }
                },
        ) {
            // Upright on screen however the page is turned.
            Box(Modifier.fillMaxSize().turned(-rotation).padding(with(density) { (pageW * 0.008f).toDp() })) {
                val size = with(density) { (pageW * 0.022f).toSp() }.value.coerceAtLeast(6f).sp
                if (note.text.isNotBlank()) Text(note.text, fontSize = size, lineHeight = size * 1.2f, color = Color(0xFF2B2600))
            }
            // Handwriting on the note, in the note's own fractions: it moves and resizes with it.
            InkCanvas(
                strokes = ink,
                tools = tools,
                onDraw = { ink ->
                    val box = placed()
                    session.addStroke(
                        note.pageIndex, ink.points, ink.tool, latest.id,
                        widthScale = page.width / box.width,
                        pressures = ink.pressures,
                        aspect = ink.aspect,
                        // Measured against the page, so letters on a note stay letters.
                        boxScale = box.width / page.width,
                    )
                },
                onErase = { ids -> session.erase(note.pageIndex, ids) },
                widthScale = pageW / width,
                onStylus = session::onStylusSeen,
            )
        }
        if (isSelected) {
            val moveLabel = stringResource(R.string.note_move)
            Box(
                Modifier
                    .align(Alignment.TopStart)
                    .offset(-HandleOffset, -HandleOffset)
                    .size(HandleTouch)
                    .pointerInput(note.id) {
                        detectDragGestures(onDragEnd = { commit() }, onDragCancel = { commit() }) { change, amount ->
                            change.consume()
                            move += amount
                        }
                    }
                    .semantics { contentDescription = moveLabel },
                contentAlignment = Alignment.Center,
            ) {
                HandleDot(MaterialTheme.colorScheme.primary) {
                    Icon(MeowIcons.Move, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(13.dp))
                }
            }
            val writeLabel = stringResource(R.string.note_write_text)
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .offset(-HandleOffset, HandleOffset)
                    .size(HandleTouch)
                    .pointerInput(note.id) { detectTapGestures(onTap = { session.editNote(latest) }) }
                    .semantics { contentDescription = writeLabel; role = Role.Button; onClick { session.editNote(latest); true } },
                contentAlignment = Alignment.Center,
            ) {
                HandleDot(MaterialTheme.colorScheme.primary) {
                    Text("Aa", color = MaterialTheme.colorScheme.onPrimary, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                }
            }
            val deleteLabel = stringResource(R.string.note_delete)
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .offset(HandleOffset, -HandleOffset)
                    .size(HandleTouch)
                    .pointerInput(note.id) { detectTapGestures(onTap = { session.deleteNote(latest) }) }
                    .semantics { contentDescription = deleteLabel; role = Role.Button; onClick { session.deleteNote(latest); true } },
                contentAlignment = Alignment.Center,
            ) {
                HandleDot(MaterialTheme.colorScheme.error) {
                    Icon(Icons.Filled.Close, contentDescription = null, tint = MaterialTheme.colorScheme.onError, modifier = Modifier.size(13.dp))
                }
            }
            val resizeLabel = stringResource(R.string.note_resize)
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .offset(HandleOffset, HandleOffset)
                    .size(HandleTouch)
                    .pointerInput(note.id) {
                        detectDragGestures(onDragEnd = { commit() }, onDragCancel = { commit() }) { change, amount ->
                            change.consume()
                            grow += amount
                        }
                    }
                    .semantics { contentDescription = resizeLabel },
                contentAlignment = Alignment.Center,
            ) {
                HandleDot(MaterialTheme.colorScheme.primary) {}
            }
        }
    }
}

/** Handles: a 40dp touch area centred on a corner, showing a 20dp dot with a white edge. */
private val HandleTouch = 40.dp
private val HandleOffset = 20.dp

@Composable
private fun HandleDot(color: Color, content: @Composable () -> Unit) {
    Box(
        Modifier
            .size(20.dp)
            .shadow(2.dp, CircleShape)
            .background(color, CircleShape)
            .border(1.5.dp, Color.White, CircleShape),
        contentAlignment = Alignment.Center,
    ) { content() }
}
