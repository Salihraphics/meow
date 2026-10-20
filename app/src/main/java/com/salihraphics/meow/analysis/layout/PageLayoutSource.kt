package com.salihraphics.meow.analysis.layout

import com.salihraphics.meow.analysis.AnswerKeyAnalyzer
import com.salihraphics.meow.analysis.text.OcrRegion
import com.salihraphics.meow.analysis.text.PageOcr
import com.salihraphics.meow.analysis.text.PdfTextDocument
import com.salihraphics.meow.analysis.text.PdfTextSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.io.File

/** Question positions of a document's pages, read on demand. */
interface PageLayoutSource : Closeable {
    suspend fun layout(pageIndex: Int): PageLayout?
}

/**
 * Uses the PDF's own text, or OCR for pages without any (scans). Each page is read once and
 * kept; nothing is stored on disk.
 */
class PdfPageLayoutSource(
    private val file: File,
    private val textSource: PdfTextSource,
    private val ocr: PageOcr?,
) : PageLayoutSource {
    private val mutex = Mutex()
    private var document: PdfTextDocument? = null
    private val cache = HashMap<Int, PageLayout?>()

    override suspend fun layout(pageIndex: Int): PageLayout? = mutex.withLock {
        if (cache.containsKey(pageIndex)) return@withLock cache[pageIndex]
        val text = withContext(Dispatchers.IO) {
            val doc = document ?: textSource.open(file).also { document = it }
            doc.page(pageIndex)
        }
        val page = if (text.characterCount < AnswerKeyAnalyzer.MIN_TEXT_CHARS && ocr != null) {
            ocr.read(pageIndex, OcrRegion.FULL_PAGE) ?: text
        } else {
            text
        }
        QuestionLayoutDetector.detect(page).also { cache[pageIndex] = it }
    }

    override fun close() {
        document?.close()
        document = null
    }
}
