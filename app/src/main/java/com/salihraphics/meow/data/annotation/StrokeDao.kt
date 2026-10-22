package com.salihraphics.meow.data.annotation

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface StrokeDao {
    @Query(
        "SELECT * FROM annotation_strokes WHERE documentId = :documentId AND pageIndex = :pageIndex " +
            "AND deletedAt IS NULL ORDER BY createdAt, rowid",
    )
    suspend fun page(documentId: String, pageIndex: Int): List<StrokeEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(stroke: StrokeEntity)

    @Query("UPDATE annotation_strokes SET deletedAt = :now, updatedAt = :now WHERE id IN (:ids) AND deletedAt IS NULL")
    suspend fun markDeleted(ids: List<String>, now: Long)

    @Query("UPDATE annotation_strokes SET deletedAt = NULL, updatedAt = :now WHERE id IN (:ids)")
    suspend fun restore(ids: List<String>, now: Long)

    @Query("UPDATE annotation_strokes SET deletedAt = :now, updatedAt = :now WHERE documentId = :documentId AND deletedAt IS NULL")
    suspend fun markDocumentDeleted(documentId: String, now: Long)

    @Query(
        "SELECT * FROM sticky_notes WHERE documentId = :documentId AND pageIndex = :pageIndex " +
            "AND deletedAt IS NULL ORDER BY createdAt, rowid",
    )
    suspend fun notesOnPage(documentId: String, pageIndex: Int): List<StickyNoteEntity>

    @Query("SELECT * FROM sticky_notes WHERE id = :id")
    suspend fun note(id: String): StickyNoteEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putNote(note: StickyNoteEntity)

    @Query("UPDATE sticky_notes SET deletedAt = :now, updatedAt = :now WHERE id = :id AND deletedAt IS NULL")
    suspend fun deleteNote(id: String, now: Long)

    @Query("UPDATE sticky_notes SET deletedAt = NULL, updatedAt = :now WHERE id = :id")
    suspend fun restoreNote(id: String, now: Long)

    @Query("UPDATE sticky_notes SET deletedAt = :now, updatedAt = :now WHERE documentId = :documentId AND deletedAt IS NULL")
    suspend fun markDocumentNotesDeleted(documentId: String, now: Long)

    /**
     * The newest notes across live documents, for the dashboard: ones with text or with ink on
     * them (an empty note placed and never used is left out). Edited = the later of the note's
     * own change and the newest ink on it.
     */
    @Query(
        "SELECT n.id, n.documentId, n.pageIndex, n.text, " +
            "(SELECT COUNT(*) FROM annotation_strokes s WHERE s.noteId = n.id AND s.deletedAt IS NULL) AS inkCount, " +
            "MAX(n.updatedAt, IFNULL((SELECT MAX(s.updatedAt) FROM annotation_strokes s WHERE s.noteId = n.id AND s.deletedAt IS NULL), 0)) AS editedAt " +
            "FROM sticky_notes n JOIN pdf_documents d ON d.id = n.documentId " +
            "WHERE n.deletedAt IS NULL AND d.deletedAt IS NULL AND (TRIM(n.text) != '' OR inkCount > 0) " +
            "ORDER BY editedAt DESC LIMIT :limit",
    )
    fun observeRecentNotes(limit: Int): Flow<List<RecentNoteRow>>
}

data class RecentNoteRow(
    val id: String,
    val documentId: String,
    val pageIndex: Int,
    val text: String,
    val inkCount: Int,
    val editedAt: Long,
)
