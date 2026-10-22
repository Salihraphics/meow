package com.salihraphics.meow.data.library.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.salihraphics.meow.domain.model.PdfDocument

/**
 * Follows docs/ACCOUNTS_AND_SYNC.md: UUID keys, timestamps, and a tombstone instead of hard
 * deletes, so sync can be added later without a rewrite.
 */
@Entity(tableName = "pdf_documents", indices = [Index("contentSha256")])
data class PdfDocumentEntity(
    @PrimaryKey val id: String,
    val displayName: String,
    val originalFileName: String?,
    val sizeBytes: Long,
    val pageCount: Int,
    /** Used to notice when the same PDF is imported twice. */
    val contentSha256: String,
    val importedAt: Long,
    val lastOpenedAt: Long?,
    val lastPageIndex: Int,
    val updatedAt: Long,
    val deletedAt: Long?,
    /** Viewer rotation in degrees clockwise (0, 90, 180, 270); the PDF itself is never changed. */
    @ColumnInfo(defaultValue = "0") val viewRotation: Int = 0,
    /** The library folder; null (and for every document from before folders) is "Klasörsüz". */
    @ColumnInfo(defaultValue = "NULL") val folderId: String? = null,
    @ColumnInfo(defaultValue = "0") val pinned: Boolean = false,
) {
    fun toDomain() = PdfDocument(
        id = id,
        displayName = displayName,
        originalFileName = originalFileName,
        sizeBytes = sizeBytes,
        pageCount = pageCount,
        importedAt = importedAt,
        lastOpenedAt = lastOpenedAt,
        lastPageIndex = lastPageIndex,
        viewRotation = viewRotation,
        folderId = folderId,
        pinned = pinned,
    )
}
