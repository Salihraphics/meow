package com.salihraphics.meow.data.library.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.salihraphics.meow.domain.model.LibraryFolder

/**
 * A folder the student made to organise the library. Only a name: documents point to it with
 * [PdfDocumentEntity.folderId], so organising never moves or copies a PDF file. Same rules as
 * the other tables: UUID keys, timestamps, a tombstone instead of a hard delete.
 */
@Entity(tableName = "library_folders")
data class LibraryFolderEntity(
    @PrimaryKey val id: String,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long?,
) {
    fun toDomain() = LibraryFolder(id, name)
}
