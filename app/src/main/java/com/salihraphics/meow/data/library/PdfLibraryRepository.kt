package com.salihraphics.meow.data.library

import android.net.Uri
import com.salihraphics.meow.domain.model.PdfDocument
import com.salihraphics.meow.domain.model.PdfImportResult
import kotlinx.coroutines.flow.Flow
import java.io.File

interface PdfLibraryRepository {
    val documents: Flow<List<PdfDocument>>

    suspend fun get(id: String): PdfDocument?

    /** SHA-256 of the stored copy; ties analysis results to this exact file. */
    suspend fun contentHash(id: String): String?

    /** Copies the picked PDF into the library after checking it. [fallbackName] is used if the file has no name. */
    suspend fun import(uri: Uri, fallbackName: String): PdfImportResult

    /** @return false if [newName] is blank or the document no longer exists. */
    suspend fun rename(id: String, newName: String): Boolean

    /** Removes MEOW's copy. The student's original file is not touched. */
    suspend fun remove(id: String)

    suspend fun recordReading(id: String, pageIndex: Int)

    /** How the student turned the pages in the viewer: 0, 90, 180 or 270 degrees clockwise. */
    suspend fun setViewRotation(id: String, degrees: Int)

    fun pdfFile(id: String): File

    fun thumbnailFile(id: String): File
}
