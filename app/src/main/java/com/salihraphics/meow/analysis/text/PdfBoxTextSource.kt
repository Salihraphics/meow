package com.salihraphics.meow.analysis.text

import android.content.Context
import com.salihraphics.meow.analysis.PageText
import com.salihraphics.meow.analysis.TextBox
import com.salihraphics.meow.analysis.TextLine
import com.salihraphics.meow.analysis.TextSource
import com.salihraphics.meow.analysis.TextWord
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import java.io.File
import kotlin.math.abs

/**
 * PDF text with positions via PdfBox-Android, which works on every Android version (the
 * platform renderer only exposes text from Android 15). Positions are page fractions, matching
 * annotation strokes.
 */
class PdfBoxTextSource(context: Context) : PdfTextSource {
    private val appContext = context.applicationContext

    // Initialised on first use rather than at app start: launching the app shouldn't pay for it.
    private val initialized by lazy { PDFBoxResourceLoader.init(appContext) }

    override fun open(file: File): PdfTextDocument {
        initialized
        // Large books are buffered to a temp file rather than held in memory.
        val document = PDDocument.load(file, MemoryUsageSetting.setupMixed(32L * 1024 * 1024))
        return PdfBoxTextDocument(document)
    }
}

private class PdfBoxTextDocument(private val document: PDDocument) : PdfTextDocument {
    override val pageCount: Int get() = document.numberOfPages

    override fun page(pageIndex: Int): PageText {
        val page = document.getPage(pageIndex)
        val box = page.cropBox
        val rotated = page.rotation % 180 != 0
        val width = if (rotated) box.height else box.width
        val height = if (rotated) box.width else box.height
        val words = mutableListOf<TextWord>()
        val stripper = object : PDFTextStripper() {
            override fun writeString(text: String, textPositions: MutableList<TextPosition>) {
                // PdfBox hands over one word at a time; split further on any spaces it kept.
                var current = mutableListOf<TextPosition>()
                fun flush() {
                    if (current.isEmpty()) return
                    val left = current.minOf { it.xDirAdj }
                    val right = current.maxOf { it.xDirAdj + it.widthDirAdj }
                    val top = current.minOf { it.yDirAdj - it.heightDir }
                    val bottom = current.maxOf { it.yDirAdj }
                    val wordText = current.joinToString("") { it.unicode }.trim()
                    if (wordText.isNotEmpty()) {
                        words += TextWord(wordText, TextBox(left / width, top / height, right / width, bottom / height))
                    }
                    current = mutableListOf()
                }
                for (position in textPositions) {
                    if (position.unicode.isBlank()) flush() else current += position
                }
                flush()
            }
        }.apply {
            sortByPosition = true
            startPage = pageIndex + 1
            endPage = pageIndex + 1
        }
        stripper.getText(document)
        return PageText(pageIndex, groupIntoLines(words), TextSource.EMBEDDED)
    }

    override fun close() = document.close()
}

/**
 * Groups words into lines by vertical position, then splits a line where a wide horizontal gap
 * separates columns. Shared with OCR results that arrive as loose words.
 */
fun groupIntoLines(words: List<TextWord>, columnGap: Float = 0.08f): List<TextLine> {
    val rows = mutableListOf<MutableList<TextWord>>()
    for (word in words.sortedBy { it.box.centerY }) {
        val row = rows.lastOrNull()
        val reference = row?.let { r -> r.sumOf { it.box.centerY.toDouble() }.toFloat() / r.size }
        val tolerance = (word.box.height * 0.6f).coerceAtLeast(0.004f)
        if (row != null && reference != null && abs(word.box.centerY - reference) <= tolerance) row += word else rows += mutableListOf(word)
    }
    return rows.flatMap { row ->
        val sorted = row.sortedBy { it.box.left }
        val lines = mutableListOf(mutableListOf(sorted.first()))
        for ((previous, word) in sorted.zipWithNext()) {
            if (word.box.left - previous.box.right > columnGap) lines += mutableListOf(word) else lines.last() += word
        }
        lines.map { TextLine(it) }
    }.sortedWith(compareBy({ it.box.top }, { it.box.left }))
}
