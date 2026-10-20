package com.salihraphics.meow.data.annotation

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

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
}
