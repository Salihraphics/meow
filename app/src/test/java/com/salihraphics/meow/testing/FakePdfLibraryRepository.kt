package com.salihraphics.meow.testing

import android.graphics.Bitmap
import android.net.Uri
import com.salihraphics.meow.data.library.PdfLibraryRepository
import com.salihraphics.meow.data.pdf.PdfPageSize
import com.salihraphics.meow.data.pdf.PdfRenderSession
import com.salihraphics.meow.data.pdf.PdfRenderSessionFactory
import com.salihraphics.meow.domain.model.PdfDocument
import com.salihraphics.meow.domain.model.PdfImportResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import java.io.File

fun sampleDocument(id: String = "doc-1", name: String = "TYT Deneme 1", pages: Int = 24, lastPage: Int = 0) = PdfDocument(
    id = id,
    displayName = name,
    originalFileName = "$name.pdf",
    sizeBytes = 2_400_000,
    pageCount = pages,
    importedAt = 1_760_000_000_000,
    lastOpenedAt = if (lastPage > 0) 1_760_000_100_000 else null,
    lastPageIndex = lastPage,
)

class FakePdfLibraryRepository(private val dir: File) : PdfLibraryRepository {
    val current = MutableStateFlow<List<PdfDocument>>(emptyList())
    override val documents = current
    val readings = mutableListOf<Pair<String, Int>>()
    var nextImport: PdfImportResult? = null
    /** When set, imports wait on it, so tests can observe the "importing" state. */
    var importGate: CompletableDeferred<Unit>? = null

    fun add(document: PdfDocument, withFile: Boolean = true) {
        current.update { it + document }
        if (withFile) pdfFile(document.id).apply { parentFile?.mkdirs(); writeText("%PDF-") }
    }

    override suspend fun get(id: String) = current.value.firstOrNull { it.id == id }

    override suspend fun import(uri: Uri, fallbackName: String): PdfImportResult {
        importGate?.await()
        val result = requireNotNull(nextImport)
        if (result is PdfImportResult.Imported) add(result.document)
        return result
    }

    override suspend fun rename(id: String, newName: String): Boolean {
        val name = PdfDocument.sanitizeName(newName) ?: return false
        current.update { list -> list.map { if (it.id == id) it.copy(displayName = name) else it } }
        return true
    }

    override suspend fun remove(id: String) = current.update { list -> list.filterNot { it.id == id } }

    override suspend fun recordReading(id: String, pageIndex: Int) {
        readings += id to pageIndex
    }

    override fun pdfFile(id: String) = File(dir, "$id.pdf")

    override fun thumbnailFile(id: String) = File(dir, "$id.jpg")
}

class FakeRenderSession(pages: Int) : PdfRenderSession {
    override val pageSizes = List(pages) { PdfPageSize(595, 842) }
    var closed = false

    override suspend fun render(pageIndex: Int, widthPx: Int): Bitmap =
        Bitmap.createBitmap(widthPx, widthPx * 842 / 595, Bitmap.Config.ARGB_8888)

    override fun close() {
        closed = true
    }
}

class FakeSessionFactory(private val pages: Int = 24, private val failure: Exception? = null) : PdfRenderSessionFactory {
    var opened: FakeRenderSession? = null
    var openCount = 0

    override suspend fun open(file: File): PdfRenderSession {
        failure?.let { throw it }
        openCount++
        return FakeRenderSession(pages).also { opened = it }
    }
}
