package com.salihraphics.meow.data.library.db

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.salihraphics.meow.data.annotation.StrokeDao
import com.salihraphics.meow.data.annotation.StrokeEntity

@Database(
    entities = [PdfDocumentEntity::class, StrokeEntity::class],
    version = 2,
    exportSchema = true,
    // v2 adds annotation_strokes; existing libraries are kept.
    autoMigrations = [AutoMigration(from = 1, to = 2)],
)
abstract class MeowDatabase : RoomDatabase() {
    abstract fun pdfDocuments(): PdfDocumentDao

    abstract fun strokes(): StrokeDao

    companion object {
        fun create(context: Context): MeowDatabase =
            Room.databaseBuilder(context.applicationContext, MeowDatabase::class.java, "meow.db").build()
    }
}
