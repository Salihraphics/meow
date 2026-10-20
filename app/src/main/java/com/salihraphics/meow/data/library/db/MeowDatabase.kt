package com.salihraphics.meow.data.library.db

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.salihraphics.meow.data.annotation.StrokeDao
import com.salihraphics.meow.data.answerkey.AnswerAttemptEntity
import com.salihraphics.meow.data.answerkey.AnswerKeyDao
import com.salihraphics.meow.data.answerkey.DocumentAnalysisEntity
import com.salihraphics.meow.data.answerkey.KeyAnswerEntity
import com.salihraphics.meow.data.answerkey.KeyTestEntity
import com.salihraphics.meow.data.annotation.StrokeEntity

@Database(
    entities = [
        PdfDocumentEntity::class,
        StrokeEntity::class,
        DocumentAnalysisEntity::class,
        KeyTestEntity::class,
        KeyAnswerEntity::class,
        AnswerAttemptEntity::class,
    ],
    version = 3,
    exportSchema = true,
    // v2 adds annotation_strokes; v3 adds answer keys and attempts. Existing data is kept.
    autoMigrations = [AutoMigration(from = 1, to = 2), AutoMigration(from = 2, to = 3)],
)
abstract class MeowDatabase : RoomDatabase() {
    abstract fun pdfDocuments(): PdfDocumentDao

    abstract fun strokes(): StrokeDao

    abstract fun answerKeys(): AnswerKeyDao

    companion object {
        fun create(context: Context): MeowDatabase =
            Room.databaseBuilder(context.applicationContext, MeowDatabase::class.java, "meow.db").build()
    }
}
