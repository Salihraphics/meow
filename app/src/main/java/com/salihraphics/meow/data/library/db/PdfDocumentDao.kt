package com.salihraphics.meow.data.library.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PdfDocumentDao {
    /** Most recently read (or imported) first. */
    @Query("SELECT * FROM pdf_documents WHERE deletedAt IS NULL ORDER BY COALESCE(lastOpenedAt, importedAt) DESC")
    fun observeAll(): Flow<List<PdfDocumentEntity>>

    @Query("SELECT * FROM pdf_documents WHERE id = :id AND deletedAt IS NULL")
    suspend fun get(id: String): PdfDocumentEntity?

    @Query("SELECT * FROM pdf_documents WHERE contentSha256 = :sha256 AND deletedAt IS NULL LIMIT 1")
    suspend fun findByContent(sha256: String): PdfDocumentEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: PdfDocumentEntity)

    @Query("UPDATE pdf_documents SET displayName = :name, updatedAt = :now WHERE id = :id AND deletedAt IS NULL")
    suspend fun rename(id: String, name: String, now: Long): Int

    @Query(
        "UPDATE pdf_documents SET lastPageIndex = :pageIndex, lastOpenedAt = :now, updatedAt = :now " +
            "WHERE id = :id AND deletedAt IS NULL",
    )
    suspend fun updateReading(id: String, pageIndex: Int, now: Long): Int

    @Query("UPDATE pdf_documents SET viewRotation = :degrees, updatedAt = :now WHERE id = :id AND deletedAt IS NULL")
    suspend fun updateViewRotation(id: String, degrees: Int, now: Long): Int

    @Query("UPDATE pdf_documents SET deletedAt = :now, updatedAt = :now WHERE id = :id AND deletedAt IS NULL")
    suspend fun markDeleted(id: String, now: Long): Int
}
