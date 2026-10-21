package com.salihraphics.meow.data.answerkey

import android.net.Uri
import com.salihraphics.meow.data.library.DocumentSource
import com.salihraphics.meow.domain.model.PdfDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/**
 * Private copies of separate answer-key PDFs. They are read for answers and kept so the student
 * can look at them from the review screen; they don't appear in the library.
 */
class KeySourceFiles(private val dir: File, private val source: DocumentSource) {
    fun file(id: String) = File(dir, "$id.pdf")

    /**
     * Copies the picked file in under [id] and returns its display name, if it has one.
     * @throws IOException if it can't be read or isn't a PDF; SecurityException if access was lost.
     */
    suspend fun copyIn(uri: Uri, id: String): String? = withContext(Dispatchers.IO) {
        dir.mkdirs()
        val target = file(id)
        try {
            source.open(uri).use { input -> target.outputStream().use { input.copyTo(it) } }
            val head = target.inputStream().use { stream -> ByteArray(1024).let { String(it, 0, maxOf(stream.read(it), 0), Charsets.ISO_8859_1) } }
            if (!head.contains("%PDF-")) throw IOException("not a PDF")
        } catch (e: Exception) {
            target.delete()
            throw e
        }
        PdfDocument.nameFromFileName(source.displayName(uri))
    }

    fun delete(id: String) {
        file(id).delete()
    }
}
