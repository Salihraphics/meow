package com.salihraphics.meow.data.annotation

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.salihraphics.meow.domain.model.Stroke
import com.salihraphics.meow.domain.model.StrokeTool
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Same rules as the PDF library (docs/ACCOUNTS_AND_SYNC.md): UUID keys, timestamps, tombstones.
 * Erasing and undo only toggle [deletedAt], so nothing is ever rewritten.
 */
@Entity(tableName = "annotation_strokes", indices = [Index(value = ["documentId", "pageIndex"])])
data class StrokeEntity(
    @PrimaryKey val id: String,
    val documentId: String,
    val pageIndex: Int,
    val tool: String,
    val color: Int,
    val width: Float,
    /** Little-endian float32 pairs (x, y) in page fractions. */
    val points: ByteArray,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long?,
    /** The sticky note this ink was written on; its points are then fractions of the note. */
    @ColumnInfo(defaultValue = "NULL") val noteId: String? = null,
    /** Stylus pressure per point, little-endian float32; null for uniform-width strokes. */
    @ColumnInfo(defaultValue = "NULL") val pressures: ByteArray? = null,
) {
    fun toDomain() = Stroke(
        id = id,
        documentId = documentId,
        pageIndex = pageIndex,
        tool = StrokeTool.entries.firstOrNull { it.name == tool } ?: StrokeTool.PEN,
        color = color,
        width = width,
        points = decodePoints(points),
        noteId = noteId,
        pressures = pressures?.let { decodePoints(it) },
    )

    override fun equals(other: Any?) = other is StrokeEntity && other.id == id && other.updatedAt == updatedAt
    override fun hashCode() = id.hashCode()

    companion object {
        fun from(stroke: Stroke, now: Long) = StrokeEntity(
            id = stroke.id,
            documentId = stroke.documentId,
            pageIndex = stroke.pageIndex,
            tool = stroke.tool.name,
            color = stroke.color,
            width = stroke.width,
            points = encodePoints(stroke.points),
            createdAt = now,
            updatedAt = now,
            deletedAt = null,
            noteId = stroke.noteId,
            pressures = stroke.pressures?.let { encodePoints(it) },
        )

        fun encodePoints(points: FloatArray): ByteArray {
            val buffer = ByteBuffer.allocate(points.size * 4).order(ByteOrder.LITTLE_ENDIAN)
            points.forEach { buffer.putFloat(it) }
            return buffer.array()
        }

        fun decodePoints(bytes: ByteArray): FloatArray {
            val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            return FloatArray(bytes.size / 4) { buffer.getFloat() }
        }
    }
}
