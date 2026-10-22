package com.salihraphics.meow.data.library

import com.salihraphics.meow.domain.model.LibraryFolder
import com.salihraphics.meow.domain.model.FolderResult
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

    /** The student's folders, by name. */
    val folders: Flow<List<LibraryFolder>>

    /** A new folder; names are unique, ignoring case. */
    suspend fun createFolder(name: String): FolderResult

    suspend fun renameFolder(id: String, name: String): FolderResult

    /** Removes the folder; its documents move to "Klasörsüz". No PDF is deleted. */
    suspend fun deleteFolder(id: String)

    /** Puts a document in a folder, or in none ([folderId] null). The file stays where it is. */
    suspend fun moveToFolder(documentId: String, folderId: String?)

    suspend fun setPinned(documentId: String, pinned: Boolean)

    fun pdfFile(id: String): File

    fun thumbnailFile(id: String): File
}
