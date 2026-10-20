package com.salihraphics.meow.data.library.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [PdfDocumentEntity::class], version = 1, exportSchema = true)
abstract class MeowDatabase : RoomDatabase() {
    abstract fun pdfDocuments(): PdfDocumentDao

    companion object {
        fun create(context: Context): MeowDatabase =
            Room.databaseBuilder(context.applicationContext, MeowDatabase::class.java, "meow.db").build()
    }
}
