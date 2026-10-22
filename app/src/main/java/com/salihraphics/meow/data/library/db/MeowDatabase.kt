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
import com.salihraphics.meow.data.answerkey.KeySourceEntity
import com.salihraphics.meow.data.answerkey.SolvingSessionEntity
import com.salihraphics.meow.data.annotation.StickyNoteEntity
import com.salihraphics.meow.data.annotation.StrokeEntity
import com.salihraphics.meow.data.study.StudyDao
import com.salihraphics.meow.data.study.StudySessionEntity
import com.salihraphics.meow.data.study.StudyTimerEntity

@Database(
    entities = [
        PdfDocumentEntity::class,
        StrokeEntity::class,
        DocumentAnalysisEntity::class,
        KeyTestEntity::class,
        KeyAnswerEntity::class,
        AnswerAttemptEntity::class,
        SolvingSessionEntity::class,
        KeySourceEntity::class,
        StickyNoteEntity::class,
        StudyTimerEntity::class,
        StudySessionEntity::class,
    ],
    version = 7,
    exportSchema = true,
    // v2 adds annotation_strokes; v3 adds answer keys and attempts; v4 adds solving sessions,
    // separate key PDFs and search clues; v5 records where each found answer was read; v6 adds
    // sticky notes, the viewer rotation per PDF and the study timer; v7 lets ink be written on a
    // note. Existing data is kept.
    autoMigrations = [
        AutoMigration(from = 1, to = 2), AutoMigration(from = 2, to = 3), AutoMigration(from = 3, to = 4),
        AutoMigration(from = 4, to = 5), AutoMigration(from = 5, to = 6), AutoMigration(from = 6, to = 7),
    ],
)
abstract class MeowDatabase : RoomDatabase() {
    abstract fun pdfDocuments(): PdfDocumentDao

    abstract fun strokes(): StrokeDao

    abstract fun answerKeys(): AnswerKeyDao

    abstract fun study(): StudyDao

    companion object {
        fun create(context: Context): MeowDatabase =
            Room.databaseBuilder(context.applicationContext, MeowDatabase::class.java, "meow.db").build()
    }
}
