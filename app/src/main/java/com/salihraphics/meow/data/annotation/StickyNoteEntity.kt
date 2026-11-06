package com.salihraphics.meow.data.annotation

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.salihraphics.meow.domain.model.StickyNote

/** Same rules as strokes: UUID keys, timestamps, and a tombstone instead of a hard delete. */
@Entity(tableName = "sticky_notes", indices = [Index(value = ["documentId", "pageIndex"])])
data class StickyNoteEntity(
    @PrimaryKey val id: String,
    val documentId: String,
    val pageIndex: Int,
    /** Position and size as fractions of the unrotated page. */
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val text: String,
    val color: Int,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long?,
) {
    /** Clamped on the way in too, so a damaged row can't put a note off its page (or crash it). */
    fun toDomain() = StickyNote(id, documentId, pageIndex, x, y, width, height, text, color).clamped()

    companion object {
        fun from(note: StickyNote, createdAt: Long, now: Long) = StickyNoteEntity(
            note.id, note.documentId, note.pageIndex, note.x, note.y, note.width, note.height, note.text, note.color,
            createdAt, now, null,
        )
    }
}
